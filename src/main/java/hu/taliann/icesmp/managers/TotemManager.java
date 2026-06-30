package hu.taliann.icesmp.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shaman totems: a placed, short-lived {@link ArmorStand} that pulses a themed effect to nearby
 * entities (heal/buff allies, or damage/debuff hostiles) on its OWN region scheduler, so the whole
 * thing is Folia-correct — the totem ticks and despawns on its region thread, and the pulse only
 * touches region-local nearby entities. Active totems are tracked and removed on plugin disable so
 * none survive a reload as orphans.
 */
public final class TotemManager {

    /** The totem roster: who it affects, the effect/damage it pulses, and its look. */
    public enum TotemType {
        HEALING_STREAM("healing_stream_totem", "Gyógyár-totem",
                "Periodikusan gyógyítja a közeli szövetségeseket (regeneráció).",
                true, PotionEffectType.REGENERATION, 0, 0.0D, 0,
                Material.GLOWSTONE, NamedTextColor.GREEN, Particle.HEART),
        SEARING("searing_totem", "Perzselő Totem",
                "Periodikusan sebzi és felgyújtja a közeli ellenségeket.",
                false, null, 0, 2.0D, 40,
                Material.MAGMA_BLOCK, NamedTextColor.GOLD, Particle.FLAME),
        WINDFURY("windfury_totem", "Szélharag-totem",
                "Sebességgel tölti fel a közeli szövetségeseket.",
                true, PotionEffectType.SPEED, 0, 0.0D, 0,
                Material.QUARTZ_BLOCK, NamedTextColor.AQUA, Particle.CLOUD),
        EARTHBIND("earthbind_totem", "Földbéklyó-totem",
                "Lelassítja a közeli ellenségeket.",
                false, PotionEffectType.SLOWNESS, 1, 0.0D, 0,
                Material.PODZOL, NamedTextColor.DARK_GREEN, Particle.ASH);

        private final String id;
        private final String displayName;
        private final String description;
        private final boolean targetPlayers;
        private final PotionEffectType effect;
        private final int amplifier;
        private final double damage;
        private final int fireTicks;
        private final Material head;
        private final NamedTextColor color;
        private final Particle particle;

        TotemType(final String id, final String displayName, final String description, final boolean targetPlayers,
                  final PotionEffectType effect, final int amplifier, final double damage, final int fireTicks,
                  final Material head, final NamedTextColor color, final Particle particle) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.targetPlayers = targetPlayers;
            this.effect = effect;
            this.amplifier = amplifier;
            this.damage = damage;
            this.fireTicks = fireTicks;
            this.head = head;
            this.color = color;
            this.particle = particle;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        /** Applies this totem's pulse to a single nearby entity (allies buff/heal, hostiles damage/debuff). */
        private void affect(final Entity entity, final int durationTicks) {
            if (targetPlayers) {
                if (entity instanceof Player player
                        && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                        && effect != null) {
                    player.addPotionEffect(new PotionEffect(effect, durationTicks, amplifier, true, false, true));
                }
                return;
            }
            if (entity instanceof Monster monster) {
                if (effect != null) {
                    monster.addPotionEffect(new PotionEffect(effect, durationTicks, amplifier, true, false, true));
                }
                if (damage > 0.0D) {
                    monster.damage(damage);
                }
                if (fireTicks > 0) {
                    monster.setFireTicks(Math.max(monster.getFireTicks(), fireTicks));
                }
            }
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey totemKey;
    private final Set<UUID> activeTotems = ConcurrentHashMap.newKeySet();

    public TotemManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.totemKey = new NamespacedKey(plugin, "shaman_totem");
    }

    /**
     * Places a totem of the given type at the caster's feet. Must be called on the caster's region
     * thread (the spell system already runs spell execution there), so the spawn is region-local.
     *
     * @param owner the casting shaman
     * @param type the totem type
     */
    public void placeTotem(final Player owner, final TotemType type) {
        final Location loc = owner.getLocation().clone();
        if (loc.getWorld() == null) {
            return;
        }

        final ArmorStand totem = loc.getWorld().spawn(loc, ArmorStand.class);
        totem.setInvulnerable(true);
        totem.setGravity(false);
        totem.setBasePlate(false);
        totem.setArms(false);
        totem.setSmall(true);
        totem.setCanPickupItems(false);
        totem.setPersistent(true);
        totem.customName(Component.text(type.displayName, type.color));
        totem.setCustomNameVisible(true);
        final EntityEquipment equipment = totem.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(type.head));
        }
        totem.getPersistentDataContainer().set(totemKey, PersistentDataType.BYTE, (byte) 1);

        activeTotems.add(totem.getUniqueId());
        startPulse(totem, type);

        final int lifeSeconds = Math.max(3, configManager.getInt("spells.totem.lifetime-seconds", 12));
        totem.getScheduler().runDelayed(plugin, task -> removeTotem(totem), null, lifeSeconds * 20L);
    }

    private void startPulse(final ArmorStand totem, final TotemType type) {
        final double radius = Math.max(2.0D, configManager.getDouble("spells.totem.radius", 6.0D));
        final int period = Math.max(10, configManager.getInt("spells.totem.pulse-ticks", 30));
        // Refresh effects for a little longer than the pulse interval so they never lapse between pulses.
        final int durationTicks = period + 20;
        totem.getScheduler().runAtFixedRate(plugin, task -> {
            if (!totem.isValid()) {
                // Removed externally before the lifetime timer — prune the tracking set so it can't leak.
                activeTotems.remove(totem.getUniqueId());
                task.cancel();
                return;
            }
            for (final Entity nearby : totem.getNearbyEntities(radius, radius, radius)) {
                type.affect(nearby, durationTicks);
            }
            totem.getWorld().spawnParticle(type.particle, totem.getLocation().add(0.0D, 1.0D, 0.0D),
                    10, 0.5D, 0.5D, 0.5D, 0.01D);
        }, null, period, period);
    }

    private void removeTotem(final ArmorStand totem) {
        activeTotems.remove(totem.getUniqueId());
        if (totem.isValid()) {
            totem.remove();
        }
    }

    public boolean isTotem(final Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().getOrDefault(totemKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /** Despawns every active totem on plugin disable (best-effort), so none orphan across a reload. */
    public void shutdown() {
        for (final UUID id : activeTotems) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                try {
                    entity.remove();
                } catch (final Exception ignored) {
                    // Region/thread unavailable during shutdown — leave it; at worst a stray armor stand.
                }
            }
        }
        activeTotems.clear();
    }
}
