package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * N25b — Kultista esemény (teszter-ötlet, lore: a Néma Királynő kósza hívei a Kapu
 * körül gyülekeznek — kódex V./VII.). Három változat, súlyozott sorsolással:
 * <ul>
 *   <li><b>TÁMADÁS:</b> kis kultista portya (akolitusok + pengék) ront a horgony-pontra
 *       — az utolsó leölése zárja, broadcast dicséri az irtókat.</li>
 *   <li><b>RÍTUS:</b> a hívek kört állnak és kántálnak; ha rite-minutes alatt nem
 *       irtják ki MINDET, a rítus beteljesül és eséllyel RONTÁS-GÓC nyílik a helyszínen
 *       (CorruptionManager.forceSpawnAt) — megszakítva viszont loot hullik.</li>
 *   <li><b>HÍRVIVŐ:</b> magányos csuklyás alak indul a Kitaszítottak földje felé —
 *       követhető; leölve elejti az üzenetét (loot), különben eltűnik a homályban.</li>
 * </ul>
 * Horgony: az N25 spawnpont-rendszer ("cultists" kulcs) → játékos-fallback. Minden
 * spawn a spawn-rules mátrixon és EventSpawnGuard.prepare-en megy át. Folia: tick a
 * globális schedulerről, spawn a helyszín régió-szálán, a hírvivő léptetése a saját
 * entity-schedulerén (halálkor magától nyugdíjazódik). Élő kulcsok: cultists.*.
 */
public final class CultistEventManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final EventSpawnGuard spawnGuard;
    private final TerritoryManager territoryManager;
    private final CorruptionManager corruptionManager;
    private final MessageManager messageManager;
    private final WhisperManager whisperManager;
    private final SeasonManager seasonManager;
    private final org.bukkit.NamespacedKey markKey;

    private final Set<UUID> cultists = ConcurrentHashMap.newKeySet();
    private volatile boolean active;
    private volatile String variant = "";
    private volatile Location riteSite;
    private volatile long riteEndsAt;
    private volatile long nextAttemptAt;
    private volatile long spawnGraceUntil;
    /** N25 — setterrel kötve. */
    private volatile EventSpawnPointManager spawnPointManager;


    /** Orchestráció-kapu (setterrel kötve; null = nincs kapuzás). */
    private volatile MajorEventGate eventGate;

    public void setEventGate(final MajorEventGate eventGate) {
        this.eventGate = eventGate;
    }

    public CultistEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final MobScalingManager mobScalingManager, final EventSpawnGuard spawnGuard,
                               final TerritoryManager territoryManager, final CorruptionManager corruptionManager,
                               final MessageManager messageManager, final WhisperManager whisperManager,
                               final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.spawnGuard = spawnGuard;
        this.territoryManager = territoryManager;
        this.corruptionManager = corruptionManager;
        this.messageManager = messageManager;
        this.whisperManager = whisperManager;
        this.seasonManager = seasonManager;
        this.markKey = new org.bukkit.NamespacedKey(plugin, "cultist_mob");
    }

    public void setSpawnPointManager(final EventSpawnPointManager spawnPointManager) {
        this.spawnPointManager = spawnPointManager;
    }

    /** Fut-e éppen kultista esemény (orchestráció-kapu / menü). */
    public boolean isActive() {
        return active;
    }

    public boolean isCultist(final Entity entity) {
        return entity.getPersistentDataContainer().has(markKey,
                org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** A közös halál-listener hívja (a mob régió-szálán). */
    public void onDeath(final UUID entityId) {
        if (!cultists.remove(entityId) || !active) {
            return;
        }
        if (cultists.isEmpty()) {
            final String key = "rite".equals(variant) ? "cultists-rite-broken"
                    : "courier".equals(variant) ? "cultists-courier-slain" : "cultists-routed";
            final String def = switch (variant) {
                case "rite" -> "<green>🕯 A rítus megszakadt — a kántálás elhal, és a kör közepén ott marad, amit a hívek a Királynőnek szántak…</green>";
                case "courier" -> "<green>🕯 A hírvivő elesett — az üzenete sosem ér a Kapuhoz.</green>";
                default -> "<green>🕯 A kultista portyát szétszórtátok — a suttogás ma elhallgat.</green>";
            };
            if ("rite".equals(variant)) {
                dropRiteLoot();
            }
            active = false;
            Bukkit.getServer().broadcast(messageManager.getMessage(key, def));
        }
    }

    private void dropRiteLoot() {
        final Location site = riteSite;
        if (site == null || site.getWorld() == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().run(plugin, site, task -> {
            for (final org.bukkit.inventory.ItemStack loot
                    : LootTable.roll(configManager, "cultists.rite-loot",
                            Math.max(1, configManager.getInt("cultists.rite-loot-rolls", 3)))) {
                site.getWorld().dropItemNaturally(site, loot);
            }
            site.getWorld().playSound(site, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
        });
    }

    /** Periodikus driver a world-events tickről (global scheduler). */
    public void tick() {
        if (!configManager.getBoolean("cultists.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (active) {
            // Rítus-határidő: ha él még hív, a rítus beteljesül.
            if ("rite".equals(variant) && riteEndsAt > 0L && now >= riteEndsAt && !cultists.isEmpty()) {
                completeRite();
            }
            return;
        }
        if (now < nextAttemptAt || now < spawnGraceUntil) {
            return;
        }
        nextAttemptAt = now + Math.max(1L, configManager.getLong("cultists.interval-minutes", 100L)) * 60_000L;
        // Orchestráció: ha másik nagy PvE-esemény fut, ez a természetes sorsolás kimarad.
        final MajorEventGate gateRef = eventGate;
        if (gateRef != null && !gateRef.mayStartNaturally("cultists")) {
            return;
        }
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("cultists.chance-percent", 30.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        forceStart(null);
    }

    /** Admin/tick indítás; false = már fut vagy nincs horgony. */
    public synchronized boolean forceStart(final Player preferredAnchor) {
        if (active || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        final String picked = pickVariant();
        // Hely-horgony, aztán játékos-fallback.
        final EventSpawnPointManager pointsRef = spawnPointManager;
        final Location fixedAnchor = preferredAnchor != null || pointsRef == null
                ? null : pointsRef.resolveAnchorLocation("cultists");
        if (fixedAnchor != null) {
            plugin.getServer().getRegionScheduler().run(plugin, fixedAnchor,
                    task -> spawnVariant(picked, fixedAnchor));
            return true;
        }
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        final Player target = anchor;
        final int offset = Math.max(16, configManager.getInt("cultists.spawn-offset", 48));
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-offset, offset), 0.0D,
                    ThreadLocalRandom.current().nextDouble(-offset, offset));
            plugin.getServer().getRegionScheduler().run(plugin, base,
                    spawn -> spawnVariant(picked, base));
        }, null);
        return true;
    }

    private String pickVariant() {
        final int attack = Math.max(0, configManager.getInt("cultists.variant-weights.attack", 40));
        final int rite = Math.max(0, configManager.getInt("cultists.variant-weights.rite", 35));
        final int courier = Math.max(0, configManager.getInt("cultists.variant-weights.courier", 25));
        final int total = Math.max(1, attack + rite + courier);
        final int roll = ThreadLocalRandom.current().nextInt(total);
        return roll < attack ? "attack" : roll < attack + rite ? "rite" : "courier";
    }

    /** A helyszín régió-szálán fut: guard-ellenőrzés + a változat felállítása. */
    private void spawnVariant(final String picked, final Location base) {
        final World world = base.getWorld();
        if (world == null) {
            return;
        }
        final int x = base.getBlockX();
        final int z = base.getBlockZ();
        final int y = world.getHighestBlockYAt(x, z) + 1;
        final Location site = new Location(world, x + 0.5D, y, z + 0.5D);
        if (spawnGuard.isBlocked("cultists", site) || spawnGuard.isUnsafeSurface("cultists", world, x, z)) {
            return; // következő intervallum, máshol
        }
        cultists.clear();
        variant = picked;
        riteSite = "rite".equals(picked) ? site : null;
        riteEndsAt = "rite".equals(picked)
                ? System.currentTimeMillis() + Math.max(1, configManager.getInt("cultists.rite-minutes", 6)) * 60_000L : 0L;
        active = true;

        switch (picked) {
            case "rite" -> {
                final int count = Math.max(2, configManager.getInt("cultists.rite-count", 3));
                for (int i = 0; i < count; i++) {
                    final double angle = (Math.PI * 2.0D / count) * i;
                    spawnCultist(world, site.clone().add(Math.cos(angle) * 3.0D, 0.0D, Math.sin(angle) * 3.0D),
                            i == 0 ? EntityType.WITCH : EntityType.VINDICATOR,
                            i == 0 ? "Kultista főpap" : "Kultista őrző");
                }
                world.playSound(site, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.5F, 0.6F);
                hu.taliann.icesmp.utils.ParticleUtil.spawn(world, Particle.SOUL, site, 30, 2.0D, 1.0D, 2.0D, 0.02D);
                Bukkit.getServer().broadcast(messageManager.getMessage("cultists-rite-started",
                        "<dark_purple>🕯 A Néma Királynő hívei RÍTUSBA kezdtek ({world}: {x}, {z})! Szakítsd meg, mielőtt beteljesül — különben a rontás gyökeret ver…</dark_purple>",
                        Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z))));
            }
            case "courier" -> {
                spawnCourier(world, site);
                Bukkit.getServer().broadcast(messageManager.getMessage("cultists-courier-seen",
                        "<dark_purple>🕯 Csuklyás HÍRVIVŐT láttak ({world}: {x}, {z} felől) — a Kitaszítottak földje felé oson. Kövesd vagy állítsd meg, mielőtt eltűnik!</dark_purple>",
                        Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z))));
            }
            default -> {
                final int count = Math.max(2, configManager.getInt("cultists.attack-count", 4));
                for (int i = 0; i < count; i++) {
                    spawnCultist(world, site.clone().add(
                                    ThreadLocalRandom.current().nextDouble(-3.0D, 3.0D), 0.0D,
                                    ThreadLocalRandom.current().nextDouble(-3.0D, 3.0D)),
                            i == 0 ? EntityType.WITCH : EntityType.VINDICATOR,
                            i == 0 ? "Kultista akolitus" : "Kultista penge");
                }
                world.playSound(site, Sound.ENTITY_VINDICATOR_CELEBRATE, 1.2F, 0.7F);
                Bukkit.getServer().broadcast(messageManager.getMessage("cultists-attack-started",
                        "<dark_purple>🕯 Kultista PORTYA tört elő a homályból ({world}: {x}, {z})! A Királynő hívei nem kegyelmeznek — szórd szét őket!</dark_purple>",
                        Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z))));
            }
        }
    }

    private void spawnCultist(final World world, final Location where, final EntityType type, final String name) {
        final int floorY = world.getHighestBlockYAt(where.getBlockX(), where.getBlockZ()) + 1;
        where.setY(floorY);
        final Entity spawned = world.spawnEntity(where, type);
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            return;
        }
        EventSpawnGuard.prepare(mob);
        mob.getPersistentDataContainer().set(markKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.customName(Component.text("🕯 " + name, NamedTextColor.DARK_PURPLE));
        mob.setCustomNameVisible(true);
        mobScalingManager.forceLevel(mob, Math.max(1, configManager.getInt("cultists.mob-level", 5)));
        cultists.add(mob.getUniqueId());
    }

    /** Hírvivő: a legközelebbi DARK territórium (vagy random irány) felé lépked. */
    private void spawnCourier(final World world, final Location site) {
        final Entity spawned = world.spawnEntity(site, EntityType.VINDICATOR);
        if (!(spawned instanceof Mob courier)) {
            spawned.remove();
            return;
        }
        EventSpawnGuard.prepare(courier);
        courier.getPersistentDataContainer().set(markKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        courier.setPersistent(false);
        courier.setRemoveWhenFarAway(false);
        courier.customName(Component.text("🕯 Kultista hírvivő", NamedTextColor.DARK_PURPLE));
        courier.setCustomNameVisible(true);
        mobScalingManager.forceLevel(courier, Math.max(1, configManager.getInt("cultists.mob-level", 5)));
        cultists.add(courier.getUniqueId());

        // Cél: a legközelebbi DARK territórium középpontja; ha nincs, random irány messze.
        Location goal = null;
        double best = Double.MAX_VALUE;
        for (final hu.taliann.icesmp.data.Territory territory : territoryManager.all()) {
            if (territory.faction() == hu.taliann.icesmp.data.FactionType.DARK
                    && territory.world().equals(world.getName())) {
                final double dist = Math.pow(territory.x() - site.getX(), 2) + Math.pow(territory.z() - site.getZ(), 2);
                if (dist < best) {
                    best = dist;
                    goal = new Location(world, territory.x(), site.getY(), territory.z());
                }
            }
        }
        if (goal == null) {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            goal = site.clone().add(Math.cos(angle) * 300.0D, 0.0D, Math.sin(angle) * 300.0D);
        }
        final Location target = goal;
        final long lifetimeTicks = Math.max(1, configManager.getInt("cultists.courier-lifetime-minutes", 8)) * 60L * 20L;
        final long spawnedAt = System.currentTimeMillis();
        // Léptetés + élettartam a hírvivő SAJÁT schedulerén (halálkor magától áll le).
        courier.getScheduler().runAtFixedRate(plugin, task -> {
            if (!courier.isValid()) {
                task.cancel();
                return;
            }
            if ((System.currentTimeMillis() - spawnedAt) / 50L >= lifetimeTicks
                    || courier.getLocation().distanceSquared(target) < 25.0D) {
                // Elért/eltűnt: köddé válik — az üzenet a Kapuhoz jutott.
                cultists.remove(courier.getUniqueId());
                active = false;
                courier.getWorld().playSound(courier.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.6F);
                hu.taliann.icesmp.utils.ParticleUtil.spawn(courier.getWorld(), Particle.SQUID_INK,
                        courier.getLocation(), 20, 0.5D, 1.0D, 0.5D, 0.02D);
                courier.remove();
                Bukkit.getServer().broadcast(messageManager.getMessage("cultists-courier-escaped",
                        "<dark_purple>🕯 A hírvivő eltűnt a homályban — az üzenete célba ért. A Suttogók ma elégedettek…</dark_purple>"));
                rewardCultSuccess();
                task.cancel();
                return;
            }
            courier.getPathfinder().moveTo(target, 1.15D);
        }, null, 20L, 60L);
    }

    /** A rítus beteljesül: hívek köddé válnak, eséllyel rontás-góc nyílik a helyszínen. */
    private synchronized void completeRite() {
        if (!active || !"rite".equals(variant)) {
            return;
        }
        active = false;
        riteEndsAt = 0L;
        for (final UUID id : cultists) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.getScheduler().run(plugin, task -> {
                    hu.taliann.icesmp.utils.ParticleUtil.spawn(entity.getWorld(), Particle.SQUID_INK,
                            entity.getLocation(), 15, 0.4D, 1.0D, 0.4D, 0.02D);
                    entity.remove();
                }, null);
            }
        }
        cultists.clear();
        Bukkit.getServer().broadcast(messageManager.getMessage("cultists-rite-complete",
                "<dark_purple>🕯 A rítus BETELJESÜLT — a kántálás elhal, és a föld megremeg a hívek lába alatt…</dark_purple>"));
        final Location site = riteSite;
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("cultists.rite-corruption-chance", 60.0D)));
        if (site != null && ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
            corruptionManager.forceSpawnAt(site);
        }
        rewardCultSuccess();
    }

    /**
     * Suttogó-crossover: a BETELJESÜLT kultista esemény (rítus / célba érő hírvivő)
     * a rejtett hálózatnak dolgozik — minden felesküdött Suttogó gyanúja csillapodik
     * (privát üzenettel), a DARK frakció pedig liga-pontot kap ("cult" forrás). Így a
     * védelem is valódi játék-cél: a Suttogóknak és a Kitaszítottaknak MEGÉRI kiállni
     * a hívek mellé, az irtóknak pedig megakadályozni. Nem farmolható: az esemény
     * természetes sorsolású, játékos nem tudja kiváltani.
     */
    private void rewardCultSuccess() {
        whisperManager.rewardFaithful(Math.max(0.0D,
                configManager.getDouble("cultists.whisper-suspicion-relief", 15.0D)));
        seasonManager.addPoints(hu.taliann.icesmp.data.FactionType.DARK,
                Math.max(0, configManager.getInt("cultists.success-season-points", 3)), "cult");
    }

    /** Leállításkor: a hívek despawnja (best effort). */
    public void shutdown() {
        for (final UUID id : cultists) {
            try {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            } catch (final Exception ignored) {
                // shutdown közben a régió már nem elérhető
            }
        }
        cultists.clear();
        active = false;
    }
}
