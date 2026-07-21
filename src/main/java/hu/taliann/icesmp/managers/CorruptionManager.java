package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * H2 — Terjedő korrupt zóna (lore: a Kárhozat Kapujából szivárgó rontás; a tisztítás
 * a Fát gyógyítja — kódex V./VII.). Ritkán a vadonban egy rontás-góc születik: közepén
 * egy SCULK_CATALYST "mag" (az egyetlen blokk-változás — visszaállítjuk!), körülötte
 * LOGIKAI sugarú zóna, amely éjszakánként nő a plafonig, és korrupt mobokat szül
 * (kemény darabszám-cappal). Tisztítás: elég korrupt lényt kell leölni, MAJD a magot
 * SHIFT+jobb kattal megtörni — jutalom: loot + rövid regeneráció, és „a Fa fellélegzik".
 *
 * <p>A góc előszeretettel a DARK territóriumok pereme felől nyílik (corruption.dark-bias.*
 * — a rontás a Kitaszítottak földjéből szivárog; a zóna BELSEJÉT a spawn-rules védi).
 *
 * <p>Terep-barát: a zóna nem ír át blokkokat (csak az 1 mag-blokk, snapshot-tal);
 * a fenyegetés a mob-nyomás. Folia: a tick a globális schedulerről fut, minden
 * blokk/entitás-művelet a hely/entitás saját régió-schedulerére hopol; a mob-készlet
 * concurrent set. Minden kulcs élőben olvasódik (corruption.*).
 */
public final class CorruptionManager implements PersistentStore {

    /** A rontás fajzatai (mind kap EventSpawnGuard.prepare keményítést). */
    private static final EntityType[] POOL = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.PHANTOM
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final EventSpawnGuard spawnGuard;
    private final MessageManager messageManager;
    private final TerritoryManager territoryManager;
    private final FactionManager factionManager;
    private final SeasonManager seasonManager;
    private final File storageFile;

    private volatile String worldName;
    private volatile int centerX;
    private volatile int centerY;
    private volatile int centerZ;
    private volatile double radius;
    private volatile boolean active;
    private final AtomicInteger purgeKills = new AtomicInteger();
    private volatile long nextAttemptAt;
    private volatile long spawnGraceUntil;
    private final Set<UUID> corruptMobs = ConcurrentHashMap.newKeySet();
    /** Az utolsó terjedés napja (world full-day), hogy éjszakánként csak egyszer nőjön. */
    private volatile long lastSpreadDay = -1L;
    /** Mentésre váró változás (purge-kill számláló) — a globális tick írja ki, nem a kill-szál. */
    private volatile boolean dirty;

    public CorruptionManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final MobScalingManager mobScalingManager, final EventSpawnGuard spawnGuard,
                             final MessageManager messageManager, final TerritoryManager territoryManager,
                             final FactionManager factionManager, final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.spawnGuard = spawnGuard;
        this.messageManager = messageManager;
        this.territoryManager = territoryManager;
        this.factionManager = factionManager;
        this.seasonManager = seasonManager;
        this.storageFile = new File(plugin.getDataFolder(), "corruption.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        active = false;
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        if (!yaml.getBoolean("active", false)) {
            return;
        }
        worldName = yaml.getString("world", "");
        centerX = yaml.getInt("x");
        centerY = yaml.getInt("y");
        centerZ = yaml.getInt("z");
        radius = yaml.getDouble("radius", 16.0D);
        purgeKills.set(yaml.getInt("purge-kills", 0));
        active = true;
        plugin.getLogger().info("Resumed corruption zone at " + centerX + "," + centerZ + " r=" + radius);
    }

    @Override
    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("active", active);
            if (active) {
                yaml.set("world", worldName);
                yaml.set("x", centerX);
                yaml.set("y", centerY);
                yaml.set("z", centerZ);
                yaml.set("radius", radius);
                yaml.set("purge-kills", purgeKills.get());
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save corruption.yml: " + exception.getMessage());
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCorruptMob(final UUID entityId) {
        return entityId != null && corruptMobs.contains(entityId);
    }

    /** A tisztítás-számláló növelése (a korrupt mob halál-listenere hívja).
     * A lemezírás DEBOUNCE-olt: a régió-szálon nem blokkolunk minden killnél
     * (tömeges farmolásnál tick-lassulást okozna) — a következő globális tick menti.
     * Restart-vesztés legfeljebb pár kill, nem kritikus. */
    public void recordPurgeKill() {
        purgeKills.incrementAndGet();
        dirty = true;
    }

    public int getPurgeKills() {
        return purgeKills.get();
    }

    public int getRequiredPurgeKills() {
        return Math.max(1, configManager.getInt("corruption.purge-kills-required", 15));
    }

    /** A mag-blokk-e (a tisztítás-interakció szűrője; régió-lokális hívás). */
    public boolean isCoreBlock(final org.bukkit.block.Block block) {
        return active && block != null && block.getType() == Material.SCULK_CATALYST
                && block.getWorld().getName().equals(worldName)
                && block.getX() == centerX && block.getY() == centerY && block.getZ() == centerZ;
    }

    /** Periodikus driver a world-events tickről. */
    public void tick() {
        if (!configManager.getBoolean("corruption.enabled", true)) {
            // Kikapcsoláskor az aktív zóna nem maradhat "befagyva" (mag + mobok örökre):
            // a magot eltüntetjük, a fajzatokat despawnoljuk (ArcheologyManager-minta).
            if (active) {
                deactivate();
            }
            return;
        }
        if (dirty) {
            dirty = false;
            save();
        }
        final long now = System.currentTimeMillis();
        if (active) {
            spreadAndSpawn();
            return;
        }
        if (now < spawnGraceUntil || now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + Math.max(1L, configManager.getLong("corruption.interval-minutes", 120L)) * 60_000L;
        final double chance = Math.max(0.0D, Math.min(100.0D, configManager.getDouble("corruption.chance-percent", 25.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        trySpawn(null);
    }

    /** N25b — góc-nyitás ADOTT helyen (a kultista rítus beteljesülése hívja). */
    public synchronized boolean forceSpawnAt(final Location where) {
        if (active || System.currentTimeMillis() < spawnGraceUntil || where.getWorld() == null) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        final World world = where.getWorld();
        final int x = where.getBlockX();
        final int z = where.getBlockZ();
        plugin.getServer().getRegionScheduler().run(plugin, where,
                place -> placeCore(world, x, z, false));
        return true;
    }

    /** Admin override: rontás-góc most (a horgony közelébe). */
    public synchronized boolean forceSpawn(final Player anchor) {
        if (active || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return trySpawn(anchor);
    }

    /**
     * Admin/kapcsoló-oldali leállítás: a mag-blokk visszaállítása (régió-hoppal), a
     * fajzatok despawnja, a zóna-állapot törlése. A tick hívja, ha corruption.enabled
     * kikapcsolt állapotban aktív zónát talál.
     */
    private synchronized void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        final World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        if (world != null) {
            final int x = centerX;
            final int y = centerY;
            final int z = centerZ;
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, y, z), task -> {
                if (world.getBlockAt(x, y, z).getType() == Material.SCULK_CATALYST) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            });
        }
        for (final UUID id : corruptMobs) {
            try {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.getScheduler().run(plugin, task -> entity.remove(), null);
                }
            } catch (final Exception ignored) {
                // Régió nem elérhető — kósza mob marad, a takarítás-szabály kezeli.
            }
        }
        corruptMobs.clear();
        save();
        plugin.getLogger().info("Corruption zone deactivated (corruption.enabled=false).");
    }

    private synchronized boolean trySpawn(final Player preferredAnchor) {
        // Zárt check-then-act: a synchronized belépés UTÁN is újraellenőrzünk — a tick és
        // egy egyidejű admin-hívás közül csak az első juthat át.
        if (active || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            // Elfogadott irány: a rontás előszeretettel a DARK territóriumok PEREME
            // felől szivárog ("Mortengrad lehelete") — configolható eséllyel a góc egy
            // DARK zóna szélén túl nyílik, a horgony-játékos helyett.
            if (tryDarkEdgeSpawn()) {
                return true;
            }
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        final Player target = anchor;
        final int offset = Math.max(32, configManager.getInt("corruption.spawn-offset", 96));
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone();
            final int x = base.getBlockX() + ThreadLocalRandom.current().nextInt(-offset, offset + 1);
            final int z = base.getBlockZ() + ThreadLocalRandom.current().nextInt(-offset, offset + 1);
            final World world = base.getWorld();
            if (world == null) {
                return;
            }
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z),
                    place -> placeCore(world, x, z, false));
        }, null);
        return true;
    }

    /**
     * DARK-perem sorsolás: configolt eséllyel egy véletlen DARK territórium szélén TÚL
     * (min/max perem-távolságra, tehát a zónán KÍVÜL — a spawn-rules mátrix a territórium
     * belsejét amúgy is tiltja) nyílik a góc. false = essen vissza a horgony-játékos útra.
     */
    private boolean tryDarkEdgeSpawn() {
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("corruption.dark-bias.chance-percent", 50.0D)));
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return false;
        }
        final java.util.List<hu.taliann.icesmp.data.Territory> darks = new java.util.ArrayList<>();
        for (final hu.taliann.icesmp.data.Territory territory : territoryManager.all()) {
            if (territory.faction() == hu.taliann.icesmp.data.FactionType.DARK) {
                darks.add(territory);
            }
        }
        if (darks.isEmpty()) {
            return false;
        }
        final hu.taliann.icesmp.data.Territory source =
                darks.get(ThreadLocalRandom.current().nextInt(darks.size()));
        final World world = Bukkit.getWorld(source.world());
        if (world == null) {
            return false;
        }
        final int minEdge = Math.max(4, configManager.getInt("corruption.dark-bias.min-edge-distance", 24));
        final int maxEdge = Math.max(minEdge, configManager.getInt("corruption.dark-bias.max-edge-distance", 96));
        final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
        final double dist = source.radius()
                + ThreadLocalRandom.current().nextDouble(minEdge, maxEdge + 1.0D);
        final int x = source.x() + (int) Math.round(Math.cos(angle) * dist);
        final int z = source.z() + (int) Math.round(Math.sin(angle) * dist);
        plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z),
                place -> placeCore(world, x, z, true));
        return true;
    }

    /** A mag lehelyezése (a cél régió-szálán): spawn-rules + víz-ellenőrzés, 1 blokk-csere. */
    private void placeCore(final World world, final int x, final int z, final boolean fromDarkEdge) {
        final int y = world.getHighestBlockYAt(x, z);
        final Location core = new Location(world, x, y + 1, z);
        if (spawnGuard.isBlocked("corruption", core) || spawnGuard.isUnsafeSurface("corruption", world, x, z)) {
            return; // Következő intervallum, máshol.
        }
        world.getBlockAt(x, y + 1, z).setType(Material.SCULK_CATALYST, false);
        worldName = world.getName();
        centerX = x;
        centerY = y + 1;
        centerZ = z;
        radius = Math.max(4.0D, configManager.getDouble("corruption.initial-radius", 16.0D));
        purgeKills.set(0);
        lastSpreadDay = -1L;
        active = true;
        spawnGraceUntil = 0L;
        save();
        world.playSound(core, org.bukkit.Sound.ENTITY_WARDEN_EMERGE, 1.5F, 0.5F);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(world, org.bukkit.Particle.SCULK_SOUL, core, 40, 2.0D, 1.0D, 2.0D, 0.05D);
        if (fromDarkEdge) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "corruption-spawned-dark",
                    "<dark_purple>🕸 RONTÁS-GÓC szivárgott ki a Kitaszítottak földjének pereméről ({world}: {x}, {z})! A rontás éjszakánként terjed — irtsd a fajzatait ({kills} kell), majd törd meg a magját (SHIFT+jobb katt), különben nő tovább!</dark_purple>",
                    Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z),
                            "kills", String.valueOf(getRequiredPurgeKills()))));
            return;
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "corruption-spawned",
                "<dark_purple>🕸 RONTÁS-GÓC nyílt a vadonban ({world}: {x}, {z})! A Kapu rontása éjszakánként terjed — irtsd a fajzatait ({kills} kell), majd törd meg a magját (SHIFT+jobb katt), különben nő tovább!</dark_purple>",
                Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z),
                        "kills", String.valueOf(getRequiredPurgeKills()))));
    }

    /** Éjszakánkénti terjedés + korrupt mob-utánpótlás (cap-pal). */
    private void spreadAndSpawn() {
        final World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        if (world == null) {
            return;
        }
        // Terjedés: éjszakánként egyszer, a plafonig.
        final long day = world.getFullTime() / 24000L;
        if (!world.isDayTime() && day != lastSpreadDay) {
            lastSpreadDay = day;
            final double cap = Math.max(8.0D, configManager.getDouble("corruption.radius-cap", 64.0D));
            final double next = Math.min(cap, radius + Math.max(0.0D, configManager.getDouble("corruption.spread-per-night", 4.0D)));
            if (next > radius) {
                radius = next;
                save();
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "corruption-spread",
                        "<dark_purple>🕸 A rontás terjed… (sugár: {radius} blokk) — a Fa fájdalma nő.</dark_purple>",
                        Map.of("radius", String.valueOf((int) radius))));
            }
        }

        // Mob-utánpótlás: kemény cap, régió-hoppal a spawn-pont felé.
        corruptMobs.removeIf(id -> {
            try {
                final Entity existing = Bukkit.getEntity(id);
                return existing == null || !existing.isValid();
            } catch (final Exception exception) {
                return false;
            }
        });
        final int cap = Math.max(1, configManager.getInt("corruption.mob-cap", 12));
        final int batch = Math.max(1, configManager.getInt("corruption.spawn-batch", 2));
        if (corruptMobs.size() >= cap) {
            return;
        }
        for (int i = 0; i < batch && corruptMobs.size() + i < cap; i++) {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            final double dist = ThreadLocalRandom.current().nextDouble(radius);
            final int x = centerX + (int) Math.round(Math.cos(angle) * dist);
            final int z = centerZ + (int) Math.round(Math.sin(angle) * dist);
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z), task -> {
                final int y = world.getHighestBlockYAt(x, z) + 1;
                final Location spot = new Location(world, x + 0.5D, y, z + 0.5D);
                // A mob-utánpótlás is a spawn-rules mátrixot követi (territory/claim/region/víz):
                // a góc mellé később épült claim/város belsejébe sem szivárog fajzat.
                if (spawnGuard.isBlocked("corruption", spot)
                        || spawnGuard.isUnsafeSurface("corruption", world, x, z)) {
                    return;
                }
                final EntityType type = POOL[ThreadLocalRandom.current().nextInt(POOL.length)];
                final Class<? extends Entity> entityClass = type.getEntityClass();
                if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
                    return;
                }
                final Mob mob = (Mob) world.spawn(spot, entityClass.asSubclass(Mob.class));
                EventSpawnGuard.prepare(mob);
                mob.setGlowing(true);
                mob.setRemoveWhenFarAway(false);
                mobScalingManager.forceLevel(mob, Math.max(1, configManager.getInt("corruption.mob-level", 6)));
                corruptMobs.add(mob.getUniqueId());
            });
        }
    }

    /**
     * A mag megtörése (a mag régió-szálán, a tisztító játékos hívásából): csak elég
     * purge-kill után enged; siker = zóna vége, jutalom + gyógyulás-üzenet.
     *
     * @return true, ha a tisztítás megtörtént (false: még kevés a leölt fajzat)
     */
    public synchronized boolean tryCleanse(final Player cleanser) {
        if (!active) {
            return false;
        }
        if (purgeKills.get() < getRequiredPurgeKills()) {
            cleanser.sendMessage(messageManager.getMessage(
                    "corruption-core-strong",
                    "<dark_purple>🕸 A mag még erős — irts több korrupt fajzatot! ({kills}/{required})</dark_purple>",
                    Map.of("kills", String.valueOf(purgeKills.get()),
                            "required", String.valueOf(getRequiredPurgeKills()))));
            return false;
        }
        final World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        active = false;
        save();
        if (world != null) {
            final Location core = new Location(world, centerX, centerY, centerZ);
            // A mag régió-szálán vagyunk (az interakció ott futott) — a blokk-csere biztonságos.
            world.getBlockAt(centerX, centerY, centerZ).setType(Material.AIR, false);
            world.playSound(core, org.bukkit.Sound.BLOCK_SCULK_CATALYST_BREAK, 1.5F, 0.6F);
            world.playSound(core, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(world, org.bukkit.Particle.TOTEM_OF_UNDYING, core, 40, 2.0D, 1.5D, 2.0D, 0.1D);
            final int rolls = Math.max(1, configManager.getInt("corruption.reward-rolls", 4));
            for (final org.bukkit.inventory.ItemStack loot : LootTable.roll(configManager, "corruption.loot", rolls)) {
                world.dropItemNaturally(core, loot);
            }
            final int buffTicks = Math.max(1, configManager.getInt("corruption.reward-buff-minutes", 5)) * 60 * 20;
            cleanser.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.REGENERATION, buffTicks, 0, false, true, true));
        }
        // A fajzatok szétszélednek (despawn a saját régió-szálukon).
        for (final UUID id : corruptMobs) {
            try {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.getScheduler().run(plugin, task -> entity.remove(), null);
                }
            } catch (final Exception ignored) {
                // Régió nem elérhető — kósza mob marad, a takarítás-szabály kezeli.
            }
        }
        corruptMobs.clear();
        // Aszimmetrikus liga: a tisztítás liga-pontot ér a tisztító frakciójának
        // ("cleanse" forrás — a Fa gyógyítása a NEUTRAL identitás-útja a súlymátrixban).
        seasonManager.addPoints(factionManager.getFaction(cleanser.getUniqueId()),
                Math.max(0, configManager.getInt("corruption.season-points", 6)), "cleanse");
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "corruption-cleansed",
                "<green>🌿 {player} megtörte a rontás magját — a góc szertefoszlik, és valahol messze a Fa fellélegzik.</green>",
                Map.of("player", cleanser.getName())));
        return true;
    }

    /** Despawn a plugin leállásakor (a zóna-állapot perzisztens, a mobok nem). */
    public void shutdown() {
        for (final UUID id : corruptMobs) {
            try {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            } catch (final Exception ignored) {
                // Shutdown közben a régió már nem elérhető — kósza mob.
            }
        }
        corruptMobs.clear();
    }
}
