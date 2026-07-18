package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wild Hunt world event (ROADMAP "élőbb világ", harci variáns): periodically a
 * single named, level-scaled elite beast roams in near a random adventurer — a
 * roaming mini-threat between the invasion swarms and the world bosses. Slaying it
 * drops a rare loot table (raw items, never currency) and announces the hunter; if
 * nobody brings it down in time it slips away.
 *
 * <p>The spawn runs on the target region's thread (Folia-safe); the slay reward is
 * handed out from the {@link hu.taliann.icesmp.listeners.WildHuntListener} on the
 * dying entity's region thread.
 */
public final class WildHuntManager {

    /** The elite beast roster; each firing picks one at random. */
    private enum Beast {
        ANCIENT_RAVAGER("Ősi Fenevad", EntityType.RAVAGER),
        BONE_HUNTER("Csont Vadász", EntityType.WITHER_SKELETON),
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

    private volatile UUID beastId;
    private volatile long expiresAt;
    private volatile long nextAttemptAt;
    /** Reserves the "active" state while a spawn is still hopping region threads. */
    private volatile long spawnGraceUntil;

    public WildHuntManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final MobScalingManager mobScalingManager, final PartyManager partyManager,
                           final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.partyManager = partyManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether the given entity is the active wild-hunt beast (for the death listener). */
    public boolean isWildHunt(final UUID entityId) {
        return entityId != null && entityId.equals(beastId);
    }

    /** Whether the wild-hunt beast is currently roaming (for the "what's happening now" summary). */
    public boolean isActive() {
        return beastId != null;
    }

    /** Milliseconds left before the beast escapes, or -1 when none is active. */
    public long getRemainingMillis() {
        return beastId != null ? Math.max(0L, expiresAt - System.currentTimeMillis()) : -1L;
    }

    /** Active (or a spawn is in flight): guards double-starts during the hop window. */
    private boolean isActiveOrSpawning() {
        return beastId != null || System.currentTimeMillis() < spawnGraceUntil;
    }

    /**
     * Atomically claims the right to settle the hunt (slain OR escaped): only the
     * first caller wins, so the expiry tick and the death listener can never both
     * settle the same beast (global vs region thread race).
     */
    private synchronized boolean claimSettlement() {
        if (beastId == null) {
            return false;
        }
        beastId = null;
        return true;
    }

    /** Periodic driver on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("wild-hunt.enabled", true)) {
            if (beastId != null) {
                escape();
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (beastId != null) {
            if (now >= expiresAt || !beastValid()) {
                escape();
            }
            return;
        }
        if (now < spawnGraceUntil) {
            return; // A spawn is still hopping region threads.
        }

        if (now >= nextAttemptAt) {
            nextAttemptAt = now + intervalMillis();
            final double chance = Math.max(0.0D, Math.min(100.0D,
                    configManager.getDouble("wild-hunt.chance-percent", 30.0D)));
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                spawn(null);
            }
        }
    }

    /** Admin override: unleashes the hunt now near the anchor (or a random player). */
    public synchronized boolean forceStart(final Player anchor) {
        if (isActiveOrSpawning()) {
            return false;
        }
        return spawn(anchor);
    }

    /**
     * Rewards the slayer and clears the hunt. Called on the beast's region thread
     * from the death listener.
     *
     * @param slayer the killing player (may be null if it died some other way)
     * @param where the death location
     */
    public void onSlain(final Player slayer, final Location where) {
        if (!claimSettlement()) {
            return; // Expiry won the race — the escape path already settled it.
        }

        final World world = where.getWorld();
        if (world != null) {
            final int rolls = Math.max(1, configManager.getInt("wild-hunt.rolls", 4));
            // WoW-style personal loot: a slayer in a party shares the kill — every nearby
            // member rolls their own reward; otherwise the loot drops at the carcass.
            final boolean personal = slayer != null
                    && partyManager.distributePersonalLoot(slayer, "wild-hunt.loot", rolls);
            if (!personal) {
                for (final ItemStack loot : LootTable.roll(configManager, "wild-hunt.loot", rolls)) {
                    world.dropItemNaturally(where, loot);
                }
            }
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, where.clone().add(0.0D, 1.0D, 0.0D), 16, 0.5D, 0.7D, 0.5D, 0.1D);
            world.playSound(where, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5F, 1.5F);
        }

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "wild-hunt-slain",
                "&a🏹 {player} leterítette a Vad Hajsza fenevadját — a ritka zsákmány az övé!",
                Map.of("player", slayer == null ? "?" : slayer.getName())
        ));
    }

    /** Removes the beast on plugin disable / expiry. */
    public void shutdown() {
        removeEntityById(beastId);
        beastId = null;
    }

    private boolean spawn(final Player preferredAnchor) {
        // Reserve the active state while the spawn hops threads (self-healing after 10s
        // if the chain dies, e.g. the world unloads), so no second beast can start.
        spawnGraceUntil = System.currentTimeMillis() + 10_000L;
        Player anchor = preferredAnchor;
        if (anchor == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        final Player target = anchor;
        final Beast beast = Beast.values()[ThreadLocalRandom.current().nextInt(Beast.values().length)];
        target.getScheduler().run(plugin, task -> {
            final Location center = target.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(plugin, center, spawnTask -> spawnBeast(center, beast));
        }, null);
        return true;
    }

    private void spawnBeast(final Location center, final Beast beast) {
        final World world = center.getWorld();
        if (world == null) {
            return;
        }
        final int x = center.getBlockX();
        final int z = center.getBlockZ();
        final Location spot = new Location(world, x + 0.5D, world.getHighestBlockYAt(x, z) + 1, z + 0.5D);

        final Class<? extends Entity> entityClass = beast.type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            return;
        }
        final Mob mob = (Mob) world.spawn(spot, entityClass.asSubclass(Mob.class));
        mob.setGlowing(true);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(false);
        mob.customName(net.kyori.adventure.text.Component.text(
                "🏹 " + beast.displayName, net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
        mob.setCustomNameVisible(true);

        final int level = Math.max(1, configManager.getInt("wild-hunt.beast-level", 8));
        mobScalingManager.forceLevel(mob, level);

        beastId = mob.getUniqueId();
        expiresAt = System.currentTimeMillis() + expireMillis();
        spawnGraceUntil = 0L;

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "wild-hunt-started",
                "&4🐺 VAD HAJSZA — egy {beast} kóborol a vidéken ({world}: {x}, {z}); ritka zsákmányt őriz, ha {minutes} percen belül le tudod teríteni!",
                Map.of(
                        "beast", beast.displayName,
                        "world", world.getName(),
                        "x", String.valueOf(x),
                        "z", String.valueOf(z),
                        "minutes", String.valueOf(Math.max(1L, expireMillis() / 60_000L))
                )
        ));
    }

    private void escape() {
        final UUID id = beastId;
        if (!claimSettlement()) {
            return; // The death listener won the race — the slain path already settled it.
        }
        removeEntityById(id);
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "wild-hunt-escaped", "&7🐺 A Vad Hajsza fenevadja eltűnt a vadonban — a zsákmány veszve."));
    }

    private void removeEntityById(final UUID id) {
        if (id == null) {
            return;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                // Folia: entity removal must run on its own region thread.
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
            }
        } catch (final Exception ignored) {
            // Region/scheduler unavailable (shutdown) — the non-persistent beast won't survive a restart.
        }
    }

    private boolean beastValid() {
        final UUID id = beastId;
        if (id == null) {
            return false;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            return entity != null && entity.isValid();
        } catch (final Exception exception) {
            return true; // Region unavailable — assume alive rather than despawning it.
        }
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("wild-hunt.interval-minutes", 70L)) * 60_000L;
    }

    private long expireMillis() {
        return Math.max(1L, configManager.getLong("wild-hunt.expire-minutes", 20L)) * 60_000L;
    }
}
