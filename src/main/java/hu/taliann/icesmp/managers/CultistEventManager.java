package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.TransientEntities;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Cultist world event with bounded lifecycle and distant, guarded placement. */
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
    private volatile EventSpawnPointManager spawnPointManager;
    private volatile MajorEventGate eventGate;

    public CultistEventManager(final JavaPlugin plugin,
                               final ConfigManager configManager,
                               final MobScalingManager mobScalingManager,
                               final EventSpawnGuard spawnGuard,
                               final TerritoryManager territoryManager,
                               final CorruptionManager corruptionManager,
                               final MessageManager messageManager,
                               final WhisperManager whisperManager,
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

    public void setEventGate(final MajorEventGate eventGate) {
        this.eventGate = eventGate;
    }

    public void setSpawnPointManager(final EventSpawnPointManager spawnPointManager) {
        this.spawnPointManager = spawnPointManager;
    }

    public boolean isActive() {
        if (!active) {
            return false;
        }
        cultists.removeIf(id -> !TransientEntities.isAlive(id));
        if (cultists.isEmpty()) {
            synchronized (this) {
                if (active && cultists.isEmpty()) {
                    active = false;
                    riteEndsAt = 0L;
                    riteSite = null;
                }
            }
        }
        return active;
    }

    public boolean isCultist(final Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(
                markKey, PersistentDataType.BYTE);
    }

    private synchronized boolean claimClose() {
        if (!active) {
            return false;
        }
        active = false;
        return true;
    }

    public void onDeath(final UUID entityId) {
        if (entityId == null) {
            return;
        }
        final boolean removed = cultists.remove(entityId);
        TransientEntities.markGone(entityId);
        if (!removed || !active || !cultists.isEmpty() || !claimClose()) {
            return;
        }
        final String current = variant;
        final String key = "rite".equals(current) ? "cultists-rite-broken"
                : "courier".equals(current)
                ? "cultists-courier-slain" : "cultists-routed";
        final String fallback = switch (current) {
            case "rite" -> "<green>🕯 A rítus megszakadt — a kántálás elhal, és a kör közepén ott marad, amit a hívek a Királynőnek szántak…</green>";
            case "courier" -> "<green>🕯 A hírvivő elesett — az üzenete sosem ér a Kapuhoz.</green>";
            default -> "<green>🕯 A kultista portyát szétszórtátok — a suttogás ma elhallgat.</green>";
        };
        if ("rite".equals(current)) {
            dropRiteLoot();
        }
        broadcast(key, fallback);
        resetTransientState();
    }

    private void dropRiteLoot() {
        final Location site = riteSite == null ? null : riteSite.clone();
        if (site == null || site.getWorld() == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().run(plugin, site, task -> {
            for (final org.bukkit.inventory.ItemStack loot : LootTable.roll(
                    configManager, "cultists.rite-loot",
                    Math.max(1, configManager.getInt("cultists.rite-loot-rolls", 3)))) {
                site.getWorld().dropItemNaturally(site, loot);
            }
            site.getWorld().playSound(site,
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
        });
    }

    public void tick() {
        if (!configManager.getBoolean("cultists.enabled", true)) {
            if (active) {
                shutdown();
            }
            return;
        }
        final long now = System.currentTimeMillis();
        if (active) {
            cultists.removeIf(id -> !TransientEntities.isAlive(id));
            if (cultists.isEmpty()) {
                if (claimClose()) {
                    resetTransientState();
                }
                return;
            }
            if ("rite".equals(variant) && riteEndsAt > 0L && now >= riteEndsAt) {
                completeRite();
            }
            return;
        }
        if (now < nextAttemptAt || now < spawnGraceUntil) {
            return;
        }
        nextAttemptAt = now + Math.max(1L,
                configManager.getLong("cultists.interval-minutes", 100L)) * 60_000L;
        final MajorEventGate gate = eventGate;
        if (gate != null && !gate.mayStartNaturally("cultists")) {
            return;
        }
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("cultists.chance-percent", 30.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
            forceStart(null);
        }
    }

    public synchronized boolean forceStart(final Player preferredAnchor) {
        if (active || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + 60_000L;
        final String picked = pickVariant();
        final long seed = System.nanoTime() ^ picked.hashCode();
        final EventSpawnPointManager points = spawnPointManager;
        final Location fixed = preferredAnchor != null || points == null
                ? null : points.resolveAnchorLocation("cultists");
        if (fixed != null) {
            spawnGuard.findSafeAtOrNear("cultists", fixed, seed,
                    site -> spawnVariant(picked, site), () -> spawnGraceUntil = 0L);
            return true;
        }

        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                spawnGraceUntil = 0L;
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        final Player target = anchor;
        target.getScheduler().run(plugin, task -> {
            final Location origin = target.getLocation().clone();
            final long playerSeed = seed ^ target.getUniqueId().getMostSignificantBits()
                    ^ target.getUniqueId().getLeastSignificantBits();
            spawnGuard.findSafeNear("cultists", origin, playerSeed,
                    site -> spawnVariant(picked, site), () -> spawnGraceUntil = 0L);
        }, () -> spawnGraceUntil = 0L);
        return true;
    }

    private String pickVariant() {
        final int attack = Math.max(0,
                configManager.getInt("cultists.variant-weights.attack", 40));
        final int rite = Math.max(0,
                configManager.getInt("cultists.variant-weights.rite", 35));
        final int courier = Math.max(0,
                configManager.getInt("cultists.variant-weights.courier", 25));
        final int total = Math.max(1, attack + rite + courier);
        final int roll = ThreadLocalRandom.current().nextInt(total);
        return roll < attack ? "attack"
                : roll < attack + rite ? "rite" : "courier";
    }

    /** Called on the region thread owning an already guarded event center. */
    private synchronized void spawnVariant(final String picked, final Location site) {
        final World world = site.getWorld();
        if (world == null || active || spawnGuard.isBlocked("cultists", site)) {
            spawnGraceUntil = 0L;
            return;
        }
        final int x = site.getBlockX();
        final int z = site.getBlockZ();
        cultists.clear();
        variant = picked;
        riteSite = "rite".equals(picked) ? site.clone() : null;
        riteEndsAt = "rite".equals(picked)
                ? System.currentTimeMillis() + Math.max(1,
                configManager.getInt("cultists.rite-minutes", 6)) * 60_000L : 0L;

        if ("rite".equals(picked)) {
            final int count = Math.max(2,
                    configManager.getInt("cultists.rite-count", 3));
            for (int index = 0; index < count; index++) {
                final double angle = Math.PI * 2.0D * index / count;
                spawnCultist(world, site.clone().add(
                                Math.cos(angle) * 3.0D, 0.0D,
                                Math.sin(angle) * 3.0D),
                        index == 0 ? "cultist_ritualist" : "cultist_blade");
            }
            world.playSound(site, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD,
                    1.5F, 0.6F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(
                    world, Particle.SOUL, site, 30,
                    2.0D, 1.0D, 2.0D, 0.02D);
            broadcast("cultists-rite-started",
                    "<dark_purple>🕯 A Néma Királynő hívei RÍTUSBA kezdtek ({world}: {x}, {z})! Szakítsd meg, mielőtt beteljesül — különben a rontás gyökeret ver…</dark_purple>",
                    Map.of("world", world.getName(), "x", String.valueOf(x),
                            "z", String.valueOf(z)));
        } else if ("courier".equals(picked)) {
            spawnCourier(world, site);
            broadcast("cultists-courier-seen",
                    "<dark_purple>🕯 Csuklyás HÍRVIVŐT láttak ({world}: {x}, {z} felől) — a Kitaszítottak földje felé oson. Kövesd vagy állítsd meg, mielőtt eltűnik!</dark_purple>",
                    Map.of("world", world.getName(), "x", String.valueOf(x),
                            "z", String.valueOf(z)));
        } else {
            final int count = Math.max(2,
                    configManager.getInt("cultists.attack-count", 4));
            for (int index = 0; index < count; index++) {
                spawnCultist(world, site.clone().add(
                                ThreadLocalRandom.current().nextDouble(-3.0D, 3.0D),
                                0.0D,
                                ThreadLocalRandom.current().nextDouble(-3.0D, 3.0D)),
                        index == 0 ? "cultist_ritualist" : "cultist_blade");
            }
            world.playSound(site, Sound.ENTITY_VINDICATOR_CELEBRATE,
                    1.2F, 0.7F);
            broadcast("cultists-attack-started",
                    "<dark_purple>🕯 Kultista PORTYA tört elő a homályból ({world}: {x}, {z})! A Királynő hívei nem kegyelmeznek — szórd szét őket!</dark_purple>",
                    Map.of("world", world.getName(), "x", String.valueOf(x),
                            "z", String.valueOf(z)));
        }
        active = !cultists.isEmpty();
        spawnGraceUntil = 0L;
        if (!active) {
            resetTransientState();
        }
    }

    private void spawnCultist(final World world, final Location requested,
                              final String templateId) {
        final Location where = spawnGuard.resolveSafeStandingLocation(
                "cultists", world, requested.getBlockX(), requested.getBlockZ());
        if (where == null || spawnGuard.isBlocked("cultists", where)) {
            return;
        }
        final Mob mob = spawnCultistMob(where, templateId);
        if (mob == null) return;
        prepareCultist(mob);
    }

    private Mob spawnCultistMob(final Location where, final String templateId) {
        final hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns =
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();
        return spawns == null ? null : spawns.spawn(where,
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.Request.template(
                        "cultists", "cultists:active", "cultist", templateId,
                        Math.max(1, configManager.getInt("cultists.mob-level", 5)),
                        hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.RewardOwner.GENERIC,
                        true, 1.0D, 1.0D, 0L));
    }

    private void prepareCultist(final Mob mob) {
        EventSpawnGuard.prepare(mob);
        mob.getPersistentDataContainer().set(
                markKey, PersistentDataType.BYTE, (byte) 1);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCustomNameVisible(true);
        TransientEntities.register(plugin, mob);
        cultists.add(mob.getUniqueId());
    }

    private void spawnCourier(final World world, final Location site) {
        final Mob courier = spawnCultistMob(site, "cultist_courier");
        if (courier == null) return;
        prepareCultist(courier);
        Location goal = null;
        double best = Double.MAX_VALUE;
        for (final hu.taliann.icesmp.data.Territory territory : territoryManager.all()) {
            if (territory.faction() == hu.taliann.icesmp.data.FactionType.DARK
                    && territory.world().equals(world.getName())) {
                final double distance = Math.pow(territory.x() - site.getX(), 2)
                        + Math.pow(territory.z() - site.getZ(), 2);
                if (distance < best) {
                    best = distance;
                    goal = new Location(world, territory.x(), site.getY(), territory.z());
                }
            }
        }
        if (goal == null) {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            goal = site.clone().add(
                    Math.cos(angle) * 300.0D, 0.0D,
                    Math.sin(angle) * 300.0D);
        }
        final Location target = goal;
        final long lifetimeMillis = Math.max(1,
                configManager.getInt("cultists.courier-lifetime-minutes", 8)) * 60_000L;
        final long spawnedAt = System.currentTimeMillis();
        courier.getScheduler().runAtFixedRate(plugin, task -> {
            if (!courier.isValid()) {
                task.cancel();
                return;
            }
            if (System.currentTimeMillis() - spawnedAt >= lifetimeMillis
                    || courier.getLocation().distanceSquared(target) < 25.0D) {
                final UUID id = courier.getUniqueId();
                cultists.remove(id);
                claimClose();
                courier.getWorld().playSound(courier.getLocation(),
                        Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.6F);
                hu.taliann.icesmp.utils.ParticleUtil.spawn(
                        courier.getWorld(), Particle.SQUID_INK,
                        courier.getLocation(), 20,
                        0.5D, 1.0D, 0.5D, 0.02D);
                courier.remove();
                TransientEntities.markGone(id);
                broadcast("cultists-courier-escaped",
                        "<dark_purple>🕯 A hírvivő eltűnt a homályban — az üzenete célba ért. A Suttogók ma elégedettek…</dark_purple>");
                rewardCultSuccess();
                resetTransientState();
                task.cancel();
                return;
            }
            courier.getPathfinder().moveTo(target, 1.15D);
        }, () -> {
            cultists.remove(courier.getUniqueId());
            TransientEntities.markGone(courier.getUniqueId());
        }, 20L, 60L);
    }

    private synchronized void completeRite() {
        if (!"rite".equals(variant) || !claimClose()) {
            return;
        }
        riteEndsAt = 0L;
        for (final UUID id : List.copyOf(cultists)) {
            TransientEntities.removeById(plugin, id);
        }
        cultists.clear();
        broadcast("cultists-rite-complete",
                "<dark_purple>🕯 A rítus BETELJESÜLT — a kántálás elhal, és a föld megremeg a hívek lába alatt…</dark_purple>");
        final Location site = riteSite == null ? null : riteSite.clone();
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("cultists.rite-corruption-chance", 60.0D)));
        if (site != null && ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
            corruptionManager.forceSpawnAt(site);
        }
        rewardCultSuccess();
        resetTransientState();
    }

    private void rewardCultSuccess() {
        whisperManager.rewardFaithful(Math.max(0.0D,
                configManager.getDouble("cultists.whisper-suspicion-relief", 15.0D)));
        seasonManager.addPoints(hu.taliann.icesmp.data.FactionType.DARK,
                Math.max(0, configManager.getInt("cultists.success-season-points", 3)), "cult");
    }

    public void shutdown() {
        TransientEntities.removeAllOnShutdown(cultists);
        active = false;
        resetTransientState();
    }

    private void resetTransientState() {
        riteEndsAt = 0L;
        riteSite = null;
        variant = "";
        spawnGraceUntil = 0L;
    }

    private void broadcast(final String key, final String fallback) {
        broadcast(key, fallback, Map.of());
    }

    private void broadcast(final String key, final String fallback,
                           final Map<String, String> placeholders) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        key, fallback, placeholders)));
    }
}
