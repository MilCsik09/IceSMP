package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B35 — Céhek: frakción belüli 5-15 fős kisközösségek — a párt (5 fő) és a frakció
 * (50+) közti tartós szervezeti réteg. Saját név, kassza (bank-infra: a befizetés a
 * tag SZÁMLÁJÁRÓL megy — bankon belüli átvezetés), és céh-szint: a tagok aktivitása
 * (quest-teljesítések) céh-XP-t termel, a szint a taglétszám-plafont emeli.
 *
 * <p>Perzisztencia: guilds.yml (YamlStore.saveAtomic). Szál-biztonság: minden
 * mutáció synchronized (parancsból, a hívó régió-szálán fut), a lookup-mapek
 * ConcurrentHashMap-ek. A függő meghívások volatilisak (session-cleanup takarítja).
 */
public final class GuildManager implements PersistentStore, PlayerStateCleanup {

    public static final class Guild {
        public String id;
        public String name;
        public FactionType faction;
        public UUID leader;
        // COW-lista: a mutáció synchronized manager-metódusokban fut, de a /ceh info|list
        // bármely régió-szálról iterál rajta — a sima ArrayList CME-t kockáztatna.
        public final List<UUID> members = new java.util.concurrent.CopyOnWriteArrayList<>();
        public double bank;
        public long xp;
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Map<String, Guild> guilds = new ConcurrentHashMap<>();
    private final Map<UUID, String> memberGuild = new ConcurrentHashMap<>();
    /** meghívott játékos -> céh-id (volatilis; kilépéskor takarítva). */
    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();

    public GuildManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "guilds.yml");
    }

    @Override
    public synchronized void load() {
        guilds.clear();
        memberGuild.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection root = yaml.getConfigurationSection("guilds");
        if (root == null) {
            return;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection g = root.getConfigurationSection(id);
            if (g == null) {
                continue;
            }
            final Guild guild = new Guild();
            guild.id = id;
            guild.name = g.getString("name", id);
            guild.faction = FactionType.fromInput(g.getString("faction", ""));
            guild.bank = Math.max(0.0D, g.getDouble("bank", 0.0D));
            guild.xp = Math.max(0L, g.getLong("xp", 0L));
            try {
                guild.leader = UUID.fromString(g.getString("leader", ""));
            } catch (final IllegalArgumentException exception) {
                continue;
            }
            for (final String raw : g.getStringList("members")) {
                try {
                    guild.members.add(UUID.fromString(raw));
                } catch (final IllegalArgumentException ignored) {
                    // sérült sor — kihagyjuk
                }
            }
            if (!guild.members.contains(guild.leader)) {
                guild.members.add(guild.leader);
            }
            guilds.put(id, guild);
            for (final UUID member : guild.members) {
                memberGuild.put(member, id);
            }
        }
    }

    @Override
    public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final Guild guild : guilds.values()) {
                final String base = "guilds." + guild.id + ".";
                yaml.set(base + "name", guild.name);
                yaml.set(base + "faction", guild.faction == null ? "" : guild.faction.name());
                yaml.set(base + "leader", guild.leader.toString());
                yaml.set(base + "members", guild.members.stream().map(UUID::toString).toList());
                yaml.set(base + "bank", guild.bank);
                yaml.set(base + "xp", guild.xp);
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save guilds.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return configManager.getBoolean("guilds.enabled", true);
    }

    public Guild getGuild(final UUID playerId) {
        final String id = memberGuild.get(playerId);
        return id == null ? null : guilds.get(id);
    }

    public Guild getById(final String id) {
        return id == null ? null : guilds.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Guild> allGuilds() {
        return List.copyOf(guilds.values());
    }

    /** Céh-szint az XP-ből: n. szint ára base + (n-1)×increment, kumulatívan. */
    public int levelOf(final Guild guild) {
        final long base = Math.max(1, configManager.getInt("guilds.level-base-xp", 500));
        final long increment = Math.max(0, configManager.getInt("guilds.level-increment", 250));
        long remaining = guild.xp;
        int level = 1;
        while (true) {
            final long cost = base + (level - 1) * increment;
            if (remaining < cost) {
                return level;
            }
            remaining -= cost;
            level++;
        }
    }

    /** Aktuális taglétszám-plafon: alap + szint-bónusz, kemény felső korláttal. */
    public int maxMembers(final Guild guild) {
        final int base = Math.max(2, configManager.getInt("guilds.base-max-members", 10));
        final int per = Math.max(1, configManager.getInt("guilds.levels-per-extra-member", 3));
        final int cap = Math.max(base, configManager.getInt("guilds.max-members-cap", 15));
        return Math.min(cap, base + (levelOf(guild) - 1) / per);
    }

    /** Céh-alapítás; hibaüzenet-kulcsot ad vissza, siker esetén null-t. */
    public synchronized String create(final Player founder, final String name) {
        if (!isEnabled()) {
            return "guild-disabled";
        }
        if (memberGuild.containsKey(founder.getUniqueId())) {
            return "guild-already-member";
        }
        final FactionType faction = factionManager.getFaction(founder.getUniqueId());
        if (faction == null) {
            return "guild-needs-faction";
        }
        final String trimmed = name.trim();
        if (trimmed.length() < 3 || trimmed.length() > 24) {
            return "guild-bad-name";
        }
        final String id = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9áéíóöőúüű_-]", "");
        if (id.length() < 3 || guilds.containsKey(id)) {
            return "guild-name-taken";
        }
        final double cost = Math.max(0.0D, configManager.getDouble("guilds.create-cost", 250.0D));
        final CurrencyType currency = CurrencyType.fromFactionType(faction);
        if (cost > 0.0D && !currencyManager.deductFromBalance(founder.getUniqueId(), currency, cost)) {
            return "guild-cant-afford";
        }
        // Az alapítási díj elég a gazdaságból (money sink) — a céh a hűség jele, nem befektetés.
        final Guild guild = new Guild();
        guild.id = id;
        guild.name = trimmed;
        guild.faction = faction;
        guild.leader = founder.getUniqueId();
        guild.members.add(founder.getUniqueId());
        guilds.put(id, guild);
        memberGuild.put(founder.getUniqueId(), id);
        save();
        return null;
    }

    public synchronized String invite(final Player leader, final Player target) {
        final Guild guild = getGuild(leader.getUniqueId());
        if (guild == null || !guild.leader.equals(leader.getUniqueId())) {
            return "guild-not-leader";
        }
        if (memberGuild.containsKey(target.getUniqueId())) {
            return "guild-target-in-guild";
        }
        if (factionManager.getFaction(target.getUniqueId()) != guild.faction) {
            return "guild-wrong-faction";
        }
        if (guild.members.size() >= maxMembers(guild)) {
            return "guild-full";
        }
        pendingInvites.put(target.getUniqueId(), guild.id);
        return null;
    }

    public synchronized String accept(final Player invitee) {
        final String guildId = pendingInvites.remove(invitee.getUniqueId());
        final Guild guild = guildId == null ? null : guilds.get(guildId);
        if (guild == null) {
            return "guild-no-invite";
        }
        if (memberGuild.containsKey(invitee.getUniqueId())) {
            return "guild-already-member";
        }
        if (factionManager.getFaction(invitee.getUniqueId()) != guild.faction
                || guild.members.size() >= maxMembers(guild)) {
            return "guild-full";
        }
        guild.members.add(invitee.getUniqueId());
        memberGuild.put(invitee.getUniqueId(), guild.id);
        save();
        return null;
    }

    /** Kilépés; vezető távozásakor átadás az első tagnak, egyedül = feloszlás. */
    public synchronized String leave(final Player player) {
        final Guild guild = getGuild(player.getUniqueId());
        if (guild == null) {
            return "guild-not-member";
        }
        guild.members.remove(player.getUniqueId());
        memberGuild.remove(player.getUniqueId());
        if (guild.members.isEmpty()) {
            guilds.remove(guild.id);
        } else if (guild.leader.equals(player.getUniqueId())) {
            guild.leader = guild.members.get(0);
        }
        save();
        return null;
    }

    /**
     * Frakcióváltás utáni egyeztetés: a céh definíció szerint EGY frakción belüli szervezet, ezért
     * más frakcióba lépve a tagság megszűnik.
     *
     * <p>Enélkül a váltó játékos az ellenséges oldalról vezethette tovább a régi frakció céhét, az
     * ott teljesített questjei a régi céh XP-jét növelték, és a céhkasszát a RÉGI frakció
     * valutájában kezelhette — a tagsági plafon és a céh politikai szerepe így megkerülhető volt.
     *
     * <p>Vezető esetén az irányítás a legrégebbi másik tagra száll; ha nincs másik tag, a céh
     * megszűnik (a kasszája vele együtt — üres céhet nem tartunk fenn).
     *
     * @return a céh neve, amiből a játékos kilépett, vagy {@code null}, ha nem volt mit egyeztetni
     */
    public synchronized String reconcileFaction(final UUID playerId, final FactionType newFaction) {
        final Guild guild = getGuild(playerId);
        if (guild == null || guild.faction == newFaction) {
            return null;
        }
        final String name = guild.name;
        guild.members.remove(playerId);
        memberGuild.remove(playerId);
        if (playerId.equals(guild.leader)) {
            if (guild.members.isEmpty()) {
                guilds.remove(guild.id);
            } else {
                guild.leader = guild.members.get(0);
            }
        }
        save();
        // A játékos SAJÁT szálán értesítünk (Folia): a váltást kiváltó parancs futhat máshol.
        final org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null) {
            online.getScheduler().run(plugin, task -> online.sendMessage(messageManager.getMessage(
                    "guild-left-on-faction-switch",
                    "<gold>⚜ A céh a frakcióval együtt marad: kiléptél a(z) <white>{guild}</white> "
                            + "céhből, mert az egy másik hatalom szervezete.</gold>",
                    java.util.Map.of("guild", name))), null);
        }
        return name;
    }

    public synchronized String kick(final Player leader, final UUID targetId) {
        final Guild guild = getGuild(leader.getUniqueId());
        if (guild == null || !guild.leader.equals(leader.getUniqueId())) {
            return "guild-not-leader";
        }
        if (targetId.equals(guild.leader) || !guild.members.remove(targetId)) {
            return "guild-target-not-member";
        }
        memberGuild.remove(targetId);
        save();
        return null;
    }

    /** Befizetés a céh-kasszába a tag SZÁMLÁJÁRÓL (bankon belüli átvezetés). */
    public synchronized String deposit(final Player member, final double amount) {
        final Guild guild = getGuild(member.getUniqueId());
        if (guild == null) {
            return "guild-not-member";
        }
        if (amount <= 0.0D) {
            return "guild-bad-amount";
        }
        final CurrencyType currency = CurrencyType.fromFactionType(guild.faction);
        if (!currencyManager.deductFromBalance(member.getUniqueId(), currency, amount)) {
            return "guild-cant-afford";
        }
        guild.bank += amount;
        save();
        return null;
    }

    /**
     * Tag-aktivitás céh-XP-je (quest-teljesítés hívja). Szintlépéskor a céh online
     * tagjai értesülnek — mindenki a SAJÁT régió-szálán (Folia).
     */
    public synchronized void addActivityXp(final Player member, final int amount) {
        final Guild guild = getGuild(member.getUniqueId());
        if (guild == null || amount <= 0) {
            return;
        }
        final int before = levelOf(guild);
        guild.xp += amount;
        final int after = levelOf(guild);
        save();
        if (after > before) {
            for (final UUID memberId : guild.members) {
                final Player online = Bukkit.getPlayer(memberId);
                if (online != null) {
                    online.getScheduler().run(plugin, task -> online.sendMessage(messageManager.getMessage(
                            "guild-level-up",
                            "<gold>⚜ A céhed szintet lépett: <white>{guild}</white> — <white>{level}. szint</white>! (Taglétszám-plafon: {max})</gold>",
                            Map.of("guild", guild.name, "level", String.valueOf(after),
                                    "max", String.valueOf(maxMembers(guild))))), null);
                }
            }
        }
    }


    @Override
    public void clearPlayerState(final UUID playerId) {
        pendingInvites.remove(playerId);
    }
}
