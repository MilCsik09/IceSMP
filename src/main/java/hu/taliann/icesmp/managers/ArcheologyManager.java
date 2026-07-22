package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * B42 — Régészet (lore: a Mélység Népe romjainak és az elveszett emlékeknek a
 * kiásása — kódex I./V.). Időnként egy GYANÚS HOMOK/KAVICS lelőhely bukkan fel a
 * vadonban (a kincs-esemény elhelyezés-mintáján): a vanília ecset-mechanikával
 * ásható ki, de a lelet SZERVER-SAJÁT (a BrushableBlock item-je felülírva a
 * config finds-táblájából — Emlékszilánk, ritka anyagok; sosem vanília cserép).
 * A ki nem ásott lelőhely lejáratkor nyomtalanul visszaáll (terep-barát).
 *
 * <p>Folia: a blokk-műveletek a lelőhely régió-schedulerén futnak; a tick a
 * globális world-events tickről jön. Minden kulcs élőben olvasódik (archeology.*).
 */
public final class ArcheologyManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final EventSpawnGuard spawnGuard;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;
    private final MessageManager messageManager;

    private volatile Location site;
    private volatile BlockState originalState;
    private volatile long expiresAt;
    private volatile long nextAttemptAt;
    private volatile long spawnGraceUntil;

    public ArcheologyManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final EventSpawnGuard spawnGuard,
                             final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
                             final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spawnGuard = spawnGuard;
        this.uniqueMaterials = uniqueMaterials;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    public boolean isActive() {
        return site != null;
    }

    /** Periodikus driver a world-events tickről. */
    public void tick() {
        if (!configManager.getBoolean("archeology.enabled", true)) {
            if (site != null) {
                restoreViaRegion(false);
            }
            return;
        }
        final long now = System.currentTimeMillis();
        if (site != null) {
            if (now >= expiresAt) {
                restoreViaRegion(true);
            }
            return;
        }
        if (now < spawnGraceUntil || now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + intervalMillis();
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("archeology.chance-percent", 35.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
            spawn(null);
        }
    }

    /** Admin override: lelőhely most, a horgony közelébe. */
    public synchronized boolean forceSpawn(final Player anchor) {
        if (site != null || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return spawn(anchor);
    }

    private synchronized boolean spawn(final Player preferredAnchor) {
        // Zárt check-then-act: a synchronized belépés UTÁN is újraellenőrzünk — a tick
        // és egy egyidejű admin-hívás közül csak az első juthat át.
        if (System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        final int radius = Math.max(16, configManager.getInt("archeology.spawn-radius", 80));
        final Player target = anchor;
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone();
            final int x = base.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int z = base.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final World world = base.getWorld();
            if (world == null) {
                return;
            }
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z),
                    place -> placeSite(world, x, z));
        }, null);
        return true;
    }

    /** A lelőhely lehelyezése (régió-szálon): a felszíni blokk cseréje gyanús homokra/kavicsra. */
    private void placeSite(final World world, final int x, final int z) {
        final int y = world.getHighestBlockYAt(x, z);
        final Location spot = new Location(world, x, y, z);
        if (spawnGuard.isBlocked("archeology", spot) || spawnGuard.isUnsafeSurface("archeology", world, x, z)) {
            return;
        }
        final Block block = world.getBlockAt(x, y, z);
        originalState = block.getState();
        block.setType(ThreadLocalRandom.current().nextBoolean()
                ? Material.SUSPICIOUS_SAND : Material.SUSPICIOUS_GRAVEL, false);
        // A vanília cserép-loot helyett SZERVER-SAJÁT lelet a finds-táblából.
        final BlockState state = block.getState();
        if (state instanceof org.bukkit.block.BrushableBlock brushable) {
            brushable.setItem(rollFind());
            brushable.update(true, false);
        }
        site = new Location(world, x, y, z);
        expiresAt = System.currentTimeMillis() + Math.max(1L,
                configManager.getLong("archeology.expire-minutes", 20L)) * 60_000L;
        spawnGraceUntil = 0L;
        // Broadcast-diéta: a lelőhely személyes léptékű — csak a környéken állók
        // értesülnek róla (a globális chat a nagy eseményeké marad).
        hu.taliann.icesmp.utils.LocalAnnounce.nearby(plugin, site,
                configManager.getDouble("archeology.announce-radius", 160.0D),
                messageManager.getMessage(
                        "archeology-spawned",
                        "<gold>🏺 A szél régi cserepeket fújt elő a közelben ({x}, {z}) — hozz ecsetet, mielőtt a homok visszaveszi ({minutes} perc)!</gold>",
                        Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z),
                                "minutes", String.valueOf(Math.max(1L, configManager.getLong("archeology.expire-minutes", 20L))))));
        return;
    }

    /**
     * A kiásás megosztott jutalma: a lelőhely körül álló többi játékos is kap esélyt
     * egy leletre — az "első kattintó visz mindent" fék. A hívó a LELŐHELY régió-szálán
     * fut (BlockDropItemEvent); az item-átadás a részesülő saját szálán történik.
     */
    public void handleExcavated(final Player digger, final Location diggedAt) {
        final Location current = this.site;
        if (current == null || diggedAt.getWorld() == null
                || !current.getWorld().equals(diggedAt.getWorld())
                || current.distanceSquared(diggedAt) > 4.0D) {
            return;
        }
        this.site = null;
        this.originalState = null;
        final double shareRadius = Math.max(0.0D, configManager.getDouble("archeology.share-radius", 24.0D));
        final double shareChance = Math.max(0.0D, configManager.getDouble("archeology.share-chance", 0.5D));
        final int maxPlayers = Math.max(0, configManager.getInt("archeology.share-max-players", 3));
        if (shareRadius <= 0.0D || shareChance <= 0.0D || maxPlayers <= 0) {
            return;
        }
        int shared = 0;
        for (final Player nearby : diggedAt.getWorld().getNearbyPlayers(diggedAt, shareRadius)) {
            if (shared >= maxPlayers || nearby.getUniqueId().equals(digger.getUniqueId())) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() >= shareChance) {
                continue;
            }
            shared++;
            final ItemStack share = rollFind();
            nearby.getScheduler().run(plugin, task -> {
                nearby.getInventory().addItem(share).values()
                        .forEach(left -> nearby.getWorld().dropItemNaturally(nearby.getLocation(), left));
                nearby.sendMessage(messageManager.getMessage("archeology-share",
                        "<gold>🏺 A régész melletted dolgozott — egy kisebb lelet neked is jutott a törmelékből.</gold>"));
            }, null);
        }
    }

    /**
     * Egy lelet sorsolása a finds-táblából. Formátum soronként:
     * {@code "unique:<id>:<db>"} (unique material) vagy {@code "MATERIAL[:MIN[:MAX]]"}.
     */
    private ItemStack rollFind() {
        final List<String> finds = configManager.getStringList("archeology.finds");
        if (finds.isEmpty()) {
            return new ItemStack(Material.EMERALD);
        }
        final String entry = finds.get(ThreadLocalRandom.current().nextInt(finds.size())).trim();
        if (entry.toLowerCase(java.util.Locale.ROOT).startsWith("unique:")) {
            final String[] parts = entry.split(":");
            final int amount = parts.length >= 3 ? parseIntSafe(parts[2], 1) : 1;
            final ItemStack unique = uniqueMaterials.create(parts[1], Math.max(1, amount));
            return unique != null ? unique : new ItemStack(Material.EMERALD);
        }
        return parseMaterialEntry(entry);
    }

    /** "MATERIAL" | "MATERIAL:DB" | "MATERIAL:MIN:MAX" — a LootTable-szintaxis helyi mása. */
    private static ItemStack parseMaterialEntry(final String entry) {
        final String[] parts = entry.split(":");
        final Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || material.isAir()) {
            return new ItemStack(Material.EMERALD);
        }
        int amount = 1;
        if (parts.length == 2) {
            amount = parseIntSafe(parts[1], 1);
        } else if (parts.length >= 3) {
            final int min = parseIntSafe(parts[1], 1);
            final int max = Math.max(min, parseIntSafe(parts[2], min));
            amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        }
        return new ItemStack(material, Math.max(1, amount));
    }

    private static int parseIntSafe(final String raw, final int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    /** Lejárat/leállás: az eredeti blokk visszaáll (ha már kiásták, akkor is takarítunk). */
    private void restoreViaRegion(final boolean announce) {
        final Location active = site;
        final BlockState original = originalState;
        site = null;
        originalState = null;
        if (active == null) {
            return;
        }
        try {
            plugin.getServer().getRegionScheduler().run(plugin, active, task -> {
                if (original != null) {
                    original.update(true, false);
                }
            });
        } catch (final Exception ignored) {
            // Scheduler nem elérhető (leállás) — a blokk marad; kozmetikai.
        }
        if (announce) {
            // Broadcast-diéta: a lejárat is csak a környékbelieknek szól.
            hu.taliann.icesmp.utils.LocalAnnounce.nearby(plugin, active,
                    configManager.getDouble("archeology.announce-radius", 160.0D),
                    messageManager.getMessage(
                            "archeology-expired", "<gray>🏺 A homok visszavette, amit őrzött — a lelőhely eltűnt.</gray>"));
        }
    }

    public void shutdown() {
        restoreViaRegion(false);
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("archeology.interval-minutes", 50L)) * 60_000L;
    }
}
