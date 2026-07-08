package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.integration.ProtectionBridge;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Native, chunk-based player claim system (replaces the external
 * SimpleClaimSystem plugin, integrated with the IceSMP economy and war rules):
 * standing in a chunk, {@code /claim} stakes it from bedrock to sky. The first
 * few chunks are free; every further chunk costs the player's own faction
 * currency at an escalating price that is BURNED (money sink — no minted money).
 * Owners can trust other players (full build/container access). Claims refuse to
 * overlap kingdom territories and WorldGuard regions, and the block-placing
 * world events (meteor, treasure) avoid them too.
 *
 * <p>War rule: claims are private property and stay protected during raids by
 * default; the {@code claims.raid-lootable} switch lets registered attackers
 * open (not break) containers in enemy-faction claims as war plunder.
 *
 * <p>Threading: the claim map is a ConcurrentHashMap keyed by
 * {@code world;chunkX;chunkZ}; protection checks are lock-free reads on the
 * acting region thread, mutations are synchronized (rare, command-driven).
 */
public final class ClaimManager implements PersistentStore {

    /** One claimed chunk: the owner plus the players they trust (full access). */
    public static final class Claim {
        private final UUID owner;
        private final String ownerName;
        private final Set<UUID> trusted = ConcurrentHashMap.newKeySet();
        private final long claimedAt;

        private Claim(final UUID owner, final String ownerName, final long claimedAt) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.claimedAt = claimedAt;
        }

        public UUID getOwner() {
            return owner;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public boolean isTrusted(final UUID playerId) {
            return owner.equals(playerId) || trusted.contains(playerId);
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final TerritoryManager territoryManager;
    private final File storageFile;

    /** world;chunkX;chunkZ → claim. */
    private final Map<String, Claim> claims = new ConcurrentHashMap<>();

    public ClaimManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final TerritoryManager territoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.territoryManager = territoryManager;
        this.storageFile = new File(plugin.getDataFolder(), "claims.yml");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("claims.enabled", true);
    }

    // ==================== queries (lock-free, hot path) ====================

    /** The claim covering the location, or null. */
    public Claim getClaimAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return claims.get(key(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    /**
     * Whether the player may build/interact at the location: true when unclaimed,
     * or when they own / are trusted in the covering claim. (The admin bypass
     * permission is the listener's concern, not the manager's.)
     */
    public boolean canUse(final UUID playerId, final Location location) {
        if (!isEnabled()) {
            return true;
        }
        final Claim claim = getClaimAt(location);
        return claim == null || claim.isTrusted(playerId);
    }

    /** How many chunks the player has claimed. */
    public int countClaims(final UUID playerId) {
        int count = 0;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The price of the player's NEXT chunk: 0 within the free allowance, then
     * {@code base-cost * cost-multiplier^(extra)} — always burned, never credited.
     */
    public double nextClaimCost(final UUID playerId) {
        final int owned = countClaims(playerId);
        final int free = Math.max(0, configManager.getInt("claims.free-chunks", 3));
        if (owned < free) {
            return 0.0D;
        }
        final double base = Math.max(0.0D, configManager.getDouble("claims.base-cost", 100.0D));
        final double multiplier = Math.max(1.0D, configManager.getDouble("claims.cost-multiplier", 1.5D));
        return Math.ceil(base * Math.pow(multiplier, owned - free) * 100.0D) / 100.0D;
    }

    /** The player's claims as "world (x, z)" chunk descriptors (for /claim list). */
    public List<String> describeClaims(final UUID playerId) {
        final List<String> entries = new ArrayList<>();
        for (final Map.Entry<String, Claim> entry : claims.entrySet()) {
            if (entry.getValue().owner.equals(playerId)) {
                final String[] parts = entry.getKey().split(";");
                entries.add(parts[0] + " (" + (Integer.parseInt(parts[1]) << 4) + ", " + (Integer.parseInt(parts[2]) << 4) + ")");
            }
        }
        return entries;
    }

    // ==================== command-facing API (null = success, else message key) ====================

    /** Claims the chunk the player stands in (free allowance, then burned faction currency). */
    public synchronized String claimHere(final Player player) {
        if (!isEnabled()) {
            return "claim-disabled";
        }
        final Location location = player.getLocation();
        final String claimKey = key(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        final Claim existing = claims.get(claimKey);
        if (existing != null) {
            return existing.owner.equals(player.getUniqueId()) ? "claim-already-yours" : "claim-already-taken";
        }

        // Kingdom land is claimed at faction level; player claims live in the wilderness.
        if (configManager.getBoolean("claims.block-in-territory", true)
                && territoryManager.getTerritoryAt(location) != null) {
            return "claim-in-territory";
        }
        // Admin-protected regions (WorldGuard) are off-limits too — fail-open without WG.
        if (configManager.getBoolean("claims.block-in-protected-region", true)
                && ProtectionBridge.isProtected(location)) {
            return "claim-in-protected-region";
        }

        final int max = Math.max(1, configManager.getInt("claims.max-chunks-per-player", 10));
        if (countClaims(player.getUniqueId()) >= max) {
            return "claim-limit-reached";
        }

        final double cost = nextClaimCost(player.getUniqueId());
        if (cost > 0.0D) {
            final CurrencyType currency = CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId()));
            // The price burns (never credited anywhere) — a pure money sink.
            if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
                return "claim-insufficient";
            }
        }

        claims.put(claimKey, new Claim(player.getUniqueId(), player.getName(), System.currentTimeMillis()));
        save();
        return null;
    }

    /** Releases the chunk the player stands in (owner only; no refund — the cost burned). */
    public synchronized String unclaimHere(final Player player) {
        final Location location = player.getLocation();
        final String claimKey = key(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        final Claim claim = claims.get(claimKey);
        if (claim == null) {
            return "claim-none-here";
        }
        if (!claim.owner.equals(player.getUniqueId())) {
            return "claim-not-owner";
        }
        claims.remove(claimKey);
        save();
        return null;
    }

    /** Admin: releases the chunk at the location regardless of owner. Returns false if unclaimed. */
    public synchronized boolean adminUnclaimAt(final Location location) {
        final boolean removed = claims.remove(
                key(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** Grants the named ONLINE player full access to every claim of the owner. */
    public synchronized String trust(final Player owner, final String targetName) {
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            return "target-player-offline";
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            return "claim-trust-self";
        }
        boolean any = false;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner.getUniqueId())) {
                claim.trusted.add(target.getUniqueId());
                any = true;
            }
        }
        if (!any) {
            return "claim-no-claims";
        }
        save();
        return null;
    }

    /** Revokes the named player's access from every claim of the owner (offline target OK). */
    public synchronized String untrust(final Player owner, final String targetName) {
        final UUID targetId = resolveIdByName(targetName);
        if (targetId == null) {
            return "target-player-offline";
        }
        boolean any = false;
        for (final Claim claim : claims.values()) {
            if (claim.owner.equals(owner.getUniqueId()) && claim.trusted.remove(targetId)) {
                any = true;
            }
        }
        if (!any) {
            return "claim-not-trusted";
        }
        save();
        return null;
    }

    /** Trusted-player names of the claim the player stands in (for /claim info). */
    public List<String> trustedNamesAt(final Location location) {
        final Claim claim = getClaimAt(location);
        if (claim == null) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        for (final UUID trustedId : claim.trusted) {
            final Player online = Bukkit.getPlayer(trustedId);
            names.add(online != null ? online.getName() : Bukkit.getOfflinePlayer(trustedId).getName());
        }
        names.removeIf(java.util.Objects::isNull);
        return names;
    }

    /** Draws a short-lived particle outline of the player's current chunk (claim borders). */
    public void showBorder(final Player player) {
        final Location location = player.getLocation();
        final World world = location.getWorld();
        final int baseX = (location.getBlockX() >> 4) << 4;
        final int baseZ = (location.getBlockZ() >> 4) << 4;
        final double y = location.getY() + 1.0D;
        final Claim claim = getClaimAt(location);
        final Particle particle = claim == null ? Particle.COMPOSTER
                : claim.isTrusted(player.getUniqueId()) ? Particle.HAPPY_VILLAGER : Particle.FLAME;
        for (int i = 0; i <= 16; i += 2) {
            player.spawnParticle(particle, new Location(world, baseX + i, y, baseZ), 1, 0, 0, 0, 0);
            player.spawnParticle(particle, new Location(world, baseX + i, y, baseZ + 16), 1, 0, 0, 0, 0);
            player.spawnParticle(particle, new Location(world, baseX, y, baseZ + i), 1, 0, 0, 0, 0);
            player.spawnParticle(particle, new Location(world, baseX + 16, y, baseZ + i), 1, 0, 0, 0, 0);
        }
    }

    // ==================== persistence ====================

    @Override
    public void load() {
        claims.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        final ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) {
            return;
        }
        for (final String claimKey : section.getKeys(false)) {
            try {
                final UUID owner = UUID.fromString(section.getString(claimKey + ".owner", ""));
                final Claim claim = new Claim(owner,
                        section.getString(claimKey + ".owner-name", "?"),
                        section.getLong(claimKey + ".claimed-at", System.currentTimeMillis()));
                for (final String trustedId : section.getStringList(claimKey + ".trusted")) {
                    claim.trusted.add(UUID.fromString(trustedId));
                }
                claims.put(claimKey, claim);
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Hibás claim-bejegyzés kihagyva: " + claimKey);
            }
        }
    }

    @Override
    public void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Claim> entry : claims.entrySet()) {
            final String basePath = "claims." + entry.getKey();
            final Claim claim = entry.getValue();
            yaml.set(basePath + ".owner", claim.owner.toString());
            yaml.set(basePath + ".owner-name", claim.ownerName);
            yaml.set(basePath + ".claimed-at", claim.claimedAt);
            if (!claim.trusted.isEmpty()) {
                yaml.set(basePath + ".trusted", claim.trusted.stream().map(UUID::toString).toList());
            }
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "A claims.yml mentése nem sikerült", exception);
        }
    }

    // ==================== internals ====================

    private UUID resolveIdByName(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        // Untrust must work for offline players; claims store the id, so look it up.
        for (final Claim claim : claims.values()) {
            for (final UUID trustedId : claim.trusted) {
                final String knownName = Bukkit.getOfflinePlayer(trustedId).getName();
                if (knownName != null && knownName.equalsIgnoreCase(name)) {
                    return trustedId;
                }
            }
        }
        return null;
    }

    private static String key(final String world, final int chunkX, final int chunkZ) {
        return world + ";" + chunkX + ";" + chunkZ;
    }
}
