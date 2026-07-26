package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.PartyRewardResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Wild Hunt world event with Folia-safe reward aggregation. */
public final class WildHuntManager {

    private enum Beast {
        ANCIENT_RAVAGER("Ősi Fenevad", EntityType.RAVAGER),
        BONE_HUNTER("Csontvadász", EntityType.WITHER_SKELETON),
        ELDER_MAGE("Vén Mágus", EntityType.EVOKER),
        INFERNAL_BRUTE("Pokoli Behemót", EntityType.PIGLIN_BRUTE);

        private final String displayName;
        private final EntityType type;

        Beast(final String displayName, final EntityType type) {
            this.displayName = displayName;
            this.type = type;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final PartyManager partyManager;
    private final MessageManager messageManager;
    private final Set<UUID> damagers = ConcurrentHashMap.newKeySet();

    private volatile UUID beastId;
    private volatile long expiresAt;
    private volatile long nextAttemptAt;
    private volatile long spawnGraceUntil;
    private volatile EventSpawnGuard spawnGuard;
    private volatile MajorEventGate eventGate;
    private volatile SeasonalModifierService seasonalModifiers;

    public WildHuntManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final MobScalingManager mobScalingManager,
                           final PartyManager partyManager,
                           final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.partyManager = partyManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    public void setEventGate(final MajorEventGate eventGate) {
        this.eventGate = eventGate;
    }

    public void setSpawnGuard(final EventSpawnGuard spawnGuard) {
        this.spawnGuard = spawnGuard;
    }

    public void setSeasonalModifiers(final SeasonalModifierService seasonalModifiers) {
        this.seasonalModifiers = seasonalModifiers;
    }

    public boolean isWildHunt(final UUID entityId) {
        return entityId != null && entityId.equals(beastId);
    }

    public boolean isActive() {
        return beastId != null;
    }

    public long getRemainingMillis() {
        return beastId != null
                ? Math.max(0L, expiresAt - System.currentTimeMillis()) : -1L;
    }

    private boolean isActiveOrSpawning() {
        return beastId != null || System.currentTimeMillis() < spawnGraceUntil;
    }

    private synchronized boolean claimSettlement() {
        if (beastId == null) {
            return false;
        }
        beastId = null;
        return true;
    }

    public void tick() {
        if (!configManager.getBoolean("wild-hunt.enabled", true)) {
            if (beastId != null) {
                escape();
            }
            return;
        }
        final long now = System.currentTimeMillis();
        if (beastId != null) {
            if (now >= expiresAt
                    || !hu.taliann.icesmp.utils.TransientEntities.isAlive(beastId)) {
                escape();
            }
            return;
        }
        if (now < spawnGraceUntil || now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + intervalMillis();
        final MajorEventGate gateRef = eventGate;
        if (gateRef != null && !gateRef.mayStartNaturally("wild-hunt")) {
            return;
        }
        final SeasonalModifierService seasonalRef = seasonalModifiers;
        final double seasonalMult = seasonalRef == null
                ? 1.0D : seasonalRef.chanceMultiplier("wild-hunt");
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("wild-hunt.chance-percent", 30.0D)
                        * seasonalMult));
        if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
            spawn(null);
        }
    }

    public synchronized boolean forceStart(final Player anchor) {
        if (isActiveOrSpawning()) {
            return false;
        }
        return spawn(anchor);
    }

    public void recordDamager(final UUID playerId) {
        if (beastId != null && playerId != null) {
            damagers.add(playerId);
        }
    }

    public void onSlain(final UUID slayerId, final Location where) {
        if (!claimSettlement()) {
            damagers.clear();
            return;
        }
        final Set<UUID> participantSnapshot = Set.copyOf(damagers);
        damagers.clear();
        final Location death = where == null ? null : where.clone();
        final World world = death == null ? null : death.getWorld();
        if (world == null) {
            broadcastSlain(slayerId);
            return;
        }
        final int rolls = Math.max(1,
                configManager.getInt("wild-hunt.rolls", 4));
        if (slayerId != null && partyManager.isEnabled()
                && partyManager.isPersonalLootEnabled()
                && PartyRewardResolver.hasPartyPair(partyManager, slayerId)) {
            PartyRewardResolver.resolveNearby(plugin, partyManager, slayerId,
                    world.getUID(), death.getX(), death.getY(), death.getZ(),
                    partyManager.getShareRadius(), nearby ->
                            plugin.getServer().getRegionScheduler().run(
                                    plugin, death, task -> settleAtRegion(
                                            slayerId, death, rolls,
                                            participantSnapshot, nearby)));
            return;
        }
        settleAtRegion(slayerId, death, rolls, participantSnapshot, List.of());
    }

    private void settleAtRegion(final UUID slayerId, final Location where, final int rolls,
                                final Set<UUID> participants,
                                final List<UUID> nearbyParty) {
        final World world = where.getWorld();
        if (world == null) {
            broadcastSlain(slayerId);
            return;
        }
        if (nearbyParty.size() >= 2) {
            for (final UUID memberId : nearbyParty) {
                grantPersonalLoot(memberId, rolls, "party-personal-loot",
                        "&d[Party] &aSzemélyes zsákmányt kaptál a csapat sikeréből!");
            }
        } else {
            for (final ItemStack loot : LootTable.roll(
                    configManager, "wild-hunt.loot", rolls)) {
                world.dropItemNaturally(where, loot);
            }
        }

        world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                where.clone().add(0.0D, 1.0D, 0.0D),
                16, 0.5D, 0.7D, 0.5D, 0.1D);
        world.playSound(where, Sound.ENTITY_ENDER_DRAGON_DEATH,
                0.5F, 1.5F);

        final double ratio = Math.max(0.0D, Math.min(1.0D,
                configManager.getDouble(
                        "wild-hunt.participant-loot-ratio", 0.5D)));
        final int participantRolls = (int) Math.round(rolls * ratio);
        if (participantRolls > 0) {
            for (final UUID id : participants) {
                if (id.equals(slayerId)
                        || (slayerId != null
                        && partyManager.isSameParty(slayerId, id))) {
                    continue;
                }
                grantPersonalLoot(id, participantRolls,
                        "wild-hunt-participant-loot",
                        "<green>🏹 Részt vettél a Hajszában — a zsákmányból neked is jut.</green>");
            }
        }
        broadcastSlain(slayerId);
    }

    private void grantPersonalLoot(final UUID playerId, final int rolls,
                                   final String messageKey, final String fallback) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            for (final ItemStack loot : LootTable.roll(
                    configManager, "wild-hunt.loot", rolls)) {
                player.getInventory().addItem(loot).values().forEach(left ->
                        player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            player.sendMessage(messageManager.getMessage(messageKey, fallback));
        }, null);
    }

    private void broadcastSlain(final UUID slayerId) {
        if (slayerId == null) {
            broadcastSlainName("?");
            return;
        }
        final Player slayer = Bukkit.getPlayer(slayerId);
        if (slayer == null) {
            broadcastSlainName("?");
            return;
        }
        slayer.getScheduler().run(plugin,
                task -> broadcastSlainName(slayer.getName()), null);
    }

    private void broadcastSlainName(final String name) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "wild-hunt-slain",
                        "&a🏹 {player} leterítette a Vad Hajsza fenevadját — a ritka zsákmány az övé!",
                        Map.of("player", name == null ? "?" : name))));
    }

    public void shutdown() {
        hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, beastId);
        beastId = null;
        damagers.clear();
    }

    private synchronized boolean spawn(final Player preferredAnchor) {
        if (System.currentTimeMillis() < spawnGraceUntil || beastId != null) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online =
                    List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                spawnGraceUntil = 0L;
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        final Player target = anchor;
        final Beast beast = Beast.values()[ThreadLocalRandom.current()
                .nextInt(Beast.values().length)];
        target.getScheduler().run(plugin, task -> {
            final Location center = target.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(
                    plugin, center, spawnTask -> spawnBeast(center, beast));
        }, null);
        return true;
    }

    private void spawnBeast(final Location center, final Beast beast) {
        final World world = center.getWorld();
        if (world == null) {
            spawnGraceUntil = 0L;
            return;
        }
        final int x = center.getBlockX();
        final int z = center.getBlockZ();
        final Location spot = new Location(world, x + 0.5D,
                world.getHighestBlockYAt(x, z) + 1, z + 0.5D);
        final EventSpawnGuard guard = spawnGuard;
        if (guard != null && (guard.isBlocked("wild-hunt", spot)
                || guard.isUnsafeSurface("wild-hunt", world, x, z))) {
            spawnGraceUntil = 0L;
            return;
        }
        final Class<? extends Entity> entityClass = beast.type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            spawnGraceUntil = 0L;
            return;
        }
        final Mob mob = (Mob) world.spawn(
                spot, entityClass.asSubclass(Mob.class));
        EventSpawnGuard.prepare(mob);
        mob.setGlowing(true);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(false);
        mob.customName(net.kyori.adventure.text.Component.text(
                "🏹 " + beast.displayName,
                net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
        mob.setCustomNameVisible(true);
        mobScalingManager.forceLevel(mob, Math.max(1,
                configManager.getInt("wild-hunt.beast-level", 8)));
        hu.taliann.icesmp.utils.TransientEntities.register(plugin, mob);
        beastId = mob.getUniqueId();
        expiresAt = System.currentTimeMillis() + expireMillis();
        spawnGraceUntil = 0L;
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "wild-hunt-started",
                "&4🐺 VAD HAJSZA — egy {beast} kóborol a vidéken ({world}: {x}, {z}); ritka zsákmányt őriz — a tiéd, ha {minutes} percen belül le tudod teríteni!",
                Map.of(
                        "beast", beast.displayName,
                        "world", world.getName(),
                        "x", String.valueOf(x),
                        "z", String.valueOf(z),
                        "minutes", String.valueOf(Math.max(1L,
                                expireMillis() / 60_000L)))));
    }

    private void escape() {
        final UUID id = beastId;
        damagers.clear();
        if (!claimSettlement()) {
            return;
        }
        hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id);
        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "wild-hunt-escaped",
                        "&7🐺 A Vad Hajsza fenevadja eltűnt a vadonban — a zsákmány veszve.")));
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong(
                "wild-hunt.interval-minutes", 70L)) * 60_000L;
    }

    private long expireMillis() {
        return Math.max(1L, configManager.getLong(
                "wild-hunt.expire-minutes", 20L)) * 60_000L;
    }
}
