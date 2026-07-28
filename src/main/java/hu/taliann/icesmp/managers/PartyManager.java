package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WoW-style party system: a small (max 5), ephemeral, cross-faction group formed
 * by invite so friends can adventure together. Party members share mob XP (split
 * per head — the more members nearby, the smaller each share), receive
 * <em>personal loot</em> from plugin loot events (each nearby member rolls their
 * own reward instead of racing for one ground drop), and cannot hurt each other.
 * Parties are pure in-memory session state: leaving the server leaves the party,
 * and a party below two members dissolves.
 *
 * <p>Threading: commands and combat events arrive on many region threads, so
 * every mutating/query method is {@code synchronized} on this manager (the state
 * is tiny). Messages/loot to other players hop to each member's own scheduler
 * (Folia-safe).
 */
public final class PartyManager implements PlayerStateCleanup {

    /** One party: the leader plus members in join order (leader included). */
    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        public UUID getLeader() {
            return leader;
        }

        /** Snapshot of the member ids in join order. */
        public List<UUID> getMembers() {
            return List.copyOf(members);
        }

        public int size() {
            return members.size();
        }
    }

    private record PendingInvite(UUID inviter, long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private final Map<UUID, Party> memberParty = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    public PartyManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    // ==================== queries ====================

    /** The player's party, or null if not in one. */
    public synchronized Party getParty(final UUID playerId) {
        return memberParty.get(playerId);
    }

    /** Whether the two players are members of the same party. */
    public synchronized boolean isSameParty(final UUID a, final UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        final Party party = memberParty.get(a);
        return party != null && party == memberParty.get(b);
    }

    /**
     * The source's online party members in the same world within the configured
     * share radius. Location reads may cross regions on Folia; a member whose
     * region is unavailable is simply skipped (defensive try/catch).
     *
     * @param source the acting member (their location is the centre)
     * @param includeSource whether the source itself is part of the result
     * @return the nearby members (empty if the source is not in a party)
     */
    public synchronized List<Player> getNearbyMembers(final Player source, final boolean includeSource) {
        final Party party = memberParty.get(source.getUniqueId());
        if (party == null) {
            return List.of();
        }
        final double radius = getShareRadius();
        final double radiusSq = radius * radius;
        // A pozíciókat a PositionCache tükréből olvassuk: a hívó gyakran idegen régió-szálon
        // fut (pl. Wild Hunt — a fenevad szálán), és a korábbi „élő olvasás + kihagyás
        // hibánál" védőháló régiófelosztás-függő jutalmat adott azonos geometriánál.
        final Location center = hu.taliann.icesmp.utils.PositionCache.get(source.getUniqueId());
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        final List<Player> nearby = new ArrayList<>();
        for (final UUID memberId : party.members) {
            if (memberId.equals(source.getUniqueId())) {
                if (includeSource) {
                    nearby.add(source);
                }
                continue;
            }
            final Player member = Bukkit.getPlayer(memberId);
            if (member == null || !member.isOnline()) {
                continue;
            }
            final Location memberPosition = hu.taliann.icesmp.utils.PositionCache.get(memberId);
            if (memberPosition != null && memberPosition.getWorld() != null
                    && memberPosition.getWorld().getUID().equals(center.getWorld().getUID())
                    && memberPosition.distanceSquared(center) <= radiusSq) {
                nearby.add(member);
            }
        }
        return nearby;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("party.enabled", true);
    }

    public int getMaxSize() {
        return Math.max(2, configManager.getInt("party.max-size", 5));
    }

    public double getShareRadius() {
        return Math.max(8.0D, configManager.getDouble("party.share-radius", 50.0D));
    }

    public boolean isXpShareEnabled() {
        return configManager.getBoolean("party.xp-share", true);
    }

    public boolean isPersonalLootEnabled() {
        return configManager.getBoolean("party.personal-loot", true);
    }

    public boolean isFriendlyFireBlocked() {
        return configManager.getBoolean("party.block-friendly-fire", true);
    }

    // ==================== command-facing API (null = success, else message key) ====================

    /** Invites the target; the party itself forms when the invite is accepted. */
    public synchronized String invite(final Player inviter, final Player target) {
        if (!isEnabled()) {
            return "party-disabled";
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            return "party-invite-self";
        }
        if (memberParty.containsKey(target.getUniqueId())) {
            return "party-target-in-party";
        }
        final Party party = memberParty.get(inviter.getUniqueId());
        if (party != null) {
            if (!party.leader.equals(inviter.getUniqueId())) {
                return "party-not-leader";
            }
            if (party.size() >= getMaxSize()) {
                return "party-full";
            }
        }

        final long expireMillis = Math.max(10L, configManager.getLong("party.invite-expire-seconds", 60L)) * 1000L;
        pendingInvites.put(target.getUniqueId(), new PendingInvite(inviter.getUniqueId(), System.currentTimeMillis() + expireMillis));

        tell(target, "party-invited",
                "&d[Party] &f{player}&7 meghívott a csapatába! Elfogadás: &e/party accept&7, elutasítás: &e/party decline",
                Map.of("player", inviter.getName()));
        return null;
    }

    /** Accepts the pending invite; creates or joins the inviter's party. */
    public synchronized String accept(final Player invitee) {
        final PendingInvite invite = pendingInvites.remove(invitee.getUniqueId());
        if (invite == null || System.currentTimeMillis() > invite.expiresAt()) {
            return "party-no-invite";
        }
        if (memberParty.containsKey(invitee.getUniqueId())) {
            return "party-already-in-party";
        }
        final Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter == null || !inviter.isOnline()) {
            return "party-no-invite";
        }

        Party party = memberParty.get(inviter.getUniqueId());
        if (party == null) {
            party = new Party();
            party.leader = inviter.getUniqueId();
            party.members.add(inviter.getUniqueId());
            memberParty.put(inviter.getUniqueId(), party);
        }
        if (party.size() >= getMaxSize()) {
            return "party-full";
        }

        party.members.add(invitee.getUniqueId());
        memberParty.put(invitee.getUniqueId(), party);
        broadcast(party, "party-joined", "&d[Party] &f{player}&a csatlakozott a csapathoz.",
                Map.of("player", invitee.getName()));
        return null;
    }

    /** Declines the pending invite. */
    public synchronized String decline(final Player invitee) {
        final PendingInvite invite = pendingInvites.remove(invitee.getUniqueId());
        if (invite == null) {
            return "party-no-invite";
        }
        final Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            tell(inviter, "party-declined", "&d[Party] &f{player}&7 elutasította a meghívást.",
                    Map.of("player", invitee.getName()));
        }
        return null;
    }

    /** Leaves the party (WoW: a departing leader passes lead; below 2 the party dissolves). */
    public synchronized String leave(final Player member) {
        final Party party = memberParty.get(member.getUniqueId());
        if (party == null) {
            return "party-not-in-party";
        }
        removeMember(party, member.getUniqueId(), "party-left", "&d[Party] &f{player}&7 kilépett a csapatból.",
                member.getName());
        return null;
    }

    /** Kicks the named member (leader only). */
    public synchronized String kick(final Player leader, final String targetName) {
        final Party party = memberParty.get(leader.getUniqueId());
        if (party == null) {
            return "party-not-in-party";
        }
        if (!party.leader.equals(leader.getUniqueId())) {
            return "party-not-leader";
        }
        final UUID targetId = findMemberByName(party, targetName);
        if (targetId == null) {
            return "party-member-not-found";
        }
        if (targetId.equals(leader.getUniqueId())) {
            return "party-kick-self";
        }
        final Player target = Bukkit.getPlayer(targetId);
        final String name = target == null ? targetName : target.getName();
        removeMember(party, targetId, "party-kicked", "&d[Party] &f{player}&c ki lett rúgva a csapatból.", name);
        return null;
    }

    /** Passes the lead to the named member (leader only). */
    public synchronized String promote(final Player leader, final String targetName) {
        final Party party = memberParty.get(leader.getUniqueId());
        if (party == null) {
            return "party-not-in-party";
        }
        if (!party.leader.equals(leader.getUniqueId())) {
            return "party-not-leader";
        }
        final UUID targetId = findMemberByName(party, targetName);
        if (targetId == null) {
            return "party-member-not-found";
        }
        if (targetId.equals(leader.getUniqueId())) {
            return "party-promote-self";
        }
        party.leader = targetId;
        final Player target = Bukkit.getPlayer(targetId);
        broadcast(party, "party-promoted", "&d[Party] &f{player}&6 lett az új csapatvezető. 👑",
                Map.of("player", target == null ? targetName : target.getName()));
        return null;
    }

    /** Disbands the whole party (leader only). */
    public synchronized String disband(final Player leader) {
        final Party party = memberParty.get(leader.getUniqueId());
        if (party == null) {
            return "party-not-in-party";
        }
        if (!party.leader.equals(leader.getUniqueId())) {
            return "party-not-leader";
        }
        broadcast(party, "party-disbanded", "&d[Party] &7A csapat feloszlott.", Map.of());
        for (final UUID memberId : List.copyOf(party.members)) {
            memberParty.remove(memberId);
        }
        party.members.clear();
        return null;
    }

    /** Sends a party-chat line to every member. */
    public synchronized String chat(final Player member, final String message) {
        final Party party = memberParty.get(member.getUniqueId());
        if (party == null) {
            return "party-not-in-party";
        }
        broadcast(party, "party-chat", "&d[Party] &f{player}&7: &f{message}",
                Map.of("player", member.getName(), "message", message));
        return null;
    }

    // ==================== event-facing helpers ====================

    /**
     * WoW-style personal loot: if the source is in a party (and the feature is on),
     * every nearby member — the source included — rolls their <em>own</em> reward
     * from the loot table, delivered straight to their inventory on their own
     * region thread. Returns false when the party path does not apply, so the
     * caller falls back to its usual single ground-drop behaviour.
     *
     * @param source the member who earned the loot (slayer/finder)
     * @param lootPath config path of the loot table
     * @param rolls rolls per member
     * @return true if the loot was distributed as personal loot
     */
    public boolean distributePersonalLoot(final Player source, final String lootPath, final int rolls) {
        if (!isEnabled() || !isPersonalLootEnabled()) {
            return false;
        }
        final List<Player> nearby = getNearbyMembers(source, true);
        if (nearby.size() < 2) {
            return false; // Alone (or party-less): the caller's normal reward path is fine.
        }
        for (final Player member : nearby) {
            member.getScheduler().run(plugin, task -> {
                for (final ItemStack loot : LootTable.roll(configManager, lootPath, rolls)) {
                    member.getInventory().addItem(loot).values()
                            .forEach(left -> member.getWorld().dropItemNaturally(member.getLocation(), left));
                }
                tell(member, "party-personal-loot", "&d[Party] &aSzemélyes zsákmányt kaptál a csapat sikeréből!", Map.of());
            }, null);
        }
        return true;
    }

    // ==================== internals ====================

    /** Removes a member, then settles leadership/dissolution and broadcasts the reason. */
    private void removeMember(final Party party, final UUID memberId, final String messageKey,
                              final String defaultMessage, final String memberName) {
        broadcast(party, messageKey, defaultMessage, Map.of("player", memberName));
        party.members.remove(memberId);
        memberParty.remove(memberId);

        if (party.size() < 2) {
            // A party of one dissolves (WoW behaviour).
            for (final UUID lastId : List.copyOf(party.members)) {
                memberParty.remove(lastId);
                final Player last = Bukkit.getPlayer(lastId);
                if (last != null) {
                    tell(last, "party-dissolved", "&d[Party] &7A csapat feloszlott (nem maradt elég tag).", Map.of());
                }
            }
            party.members.clear();
            return;
        }

        if (party.leader.equals(memberId)) {
            final UUID newLeader = party.members.iterator().next();
            party.leader = newLeader;
            final Player promoted = Bukkit.getPlayer(newLeader);
            broadcast(party, "party-promoted", "&d[Party] &f{player}&6 lett az új csapatvezető. 👑",
                    Map.of("player", promoted == null ? "?" : promoted.getName()));
        }
    }

    private UUID findMemberByName(final Party party, final String name) {
        for (final UUID memberId : party.members) {
            final Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.getName().equalsIgnoreCase(name)) {
                return memberId;
            }
        }
        return null;
    }

    /** Sends a formatted message to every online member on their own region thread. */
    private void broadcast(final Party party, final String key, final String fallback, final Map<String, String> placeholders) {
        for (final UUID memberId : party.members) {
            final Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                tell(member, key, fallback, placeholders);
            }
        }
    }

    private void tell(final Player player, final String key, final String fallback, final Map<String, String> placeholders) {
        player.getScheduler().run(plugin, task ->
                player.sendMessage(messageManager.getMessage(key, fallback, placeholders)), null);
    }

    /** Quit/kick cleanup: drop any pending invite and leave the party with a notice. */
    @Override
    public synchronized void clearPlayerState(final UUID playerId) {
        pendingInvites.remove(playerId);
        final Party party = memberParty.get(playerId);
        if (party != null) {
            final Player player = Bukkit.getPlayer(playerId);
            removeMember(party, playerId, "party-member-quit", "&d[Party] &f{player}&7 kilépett a szerverről — kikerült a csapatból.",
                    player == null ? "?" : player.getName());
        }
    }
}
