package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-class "power" resource — the WoW-style cost pool shown as a coloured bar <b>in the HUD
 * sidebar</b> (a scoreboard line, deliberately NOT a boss bar so it never stacks with the world-boss
 * bar). It is a 0–max meter, named and coloured per class (Mana / Düh / Energia / Runikus Erő / Csi …):
 * <b>casting a spell SPENDS it</b>, and it <b>regenerates over time</b>. If a spell costs more than the
 * player currently has, the cast is blocked (the legacy hunger/XP/HP costs only apply when this system
 * is disabled in config).
 *
 * <p>Regeneration is computed lazily on access (no scheduler needed): every read first credits the
 * time elapsed since the last update, so the pool stays accurate whether it's polled by the HUD tick
 * or touched by a cast. All state is UUID-keyed in concurrent maps, so reads/writes are thread-safe and
 * Folia-correct without touching any entity off its region thread.
 */
public final class ResourceManager implements PlayerStateCleanup {

    private final ConfigManager configManager;
    private final JobManager jobManager;
    /** Current resource value at {@link #lastRegen} time (lazily regenerated on access). */
    private final Map<UUID, Double> resource = new ConcurrentHashMap<>();
    /** Epoch millis of the last time a player's resource was regenerated. */
    private final Map<UUID, Long> lastRegen = new ConcurrentHashMap<>();

    public ResourceManager(final JavaPlugin plugin, final ConfigManager configManager, final JobManager jobManager,
                           final SpecializationManager specializationManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    /** Whether the resource-cost system is active. When false, spells fall back to hunger/XP/HP costs. */
    public boolean isEnabled() {
        return configManager.getBoolean("spells.resource.enabled", true);
    }

    /**
     * Whether this spell is paid for with the class resource (hybrid model). Each spell keeps its
     * thematic cost where one fits, and uses the class pool otherwise:
     * <ul>
     *   <li><b>HEALTH</b> (blood magic / self-sacrifice) — always kept on HP.</li>
     *   <li><b>XP</b> ≥ {@code xp-ritual-threshold} (great arcane rituals, summons, weather, signature
     *       ultis) — kept on XP.</li>
     *   <li><b>HUNGER</b> ≥ {@code hunger-heavy-threshold} (heavy physical effort: stances, second-wind,
     *       big totem/beast summons) — kept on hunger.</li>
     *   <li>everything else (light arcane / light stamina) — the class resource, so every class's bar is
     *       used by its bread-and-butter spells.</li>
     * </ul>
     * Always false when the system is disabled (then every spell uses its declared hunger/XP/HP cost).
     *
     * @param spell the spell being cast
     * @return true if it should be paid from the class resource pool
     */
    public boolean usesResource(final Spell spell) {
        if (!isEnabled()) {
            return false;
        }
        return switch (spell.getCostType()) {
            case HEALTH -> false;
            case XP -> spell.getCostAmount() < configManager.getInt("spells.resource.xp-ritual-threshold", 80);
            case HUNGER -> spell.getCostAmount() < configManager.getInt("spells.resource.hunger-heavy-threshold", 8);
        };
    }

    private double max() {
        return Math.max(10.0D, configManager.getDouble("spells.resource.max", 100.0D));
    }

    private double regenPerSecond() {
        return Math.max(0.0D, configManager.getDouble("spells.resource.regen-per-second", 8.0D));
    }

    /**
     * The player's current resource, after crediting the time elapsed since the last access.
     * Players start (and rejoin) at full so they can cast immediately.
     */
    private double current(final UUID id) {
        final long now = System.currentTimeMillis();
        final double maxValue = max();
        double value = resource.getOrDefault(id, maxValue);
        final Long last = lastRegen.get(id);
        if (last != null && value < maxValue) {
            value = Math.min(maxValue, value + ((now - last) / 1000.0D) * regenPerSecond());
        }
        resource.put(id, value);
        lastRegen.put(id, now);
        return value;
    }

    /** True if the player has enough resource to cast the spell (always true when the system is off). */
    public boolean canAfford(final Player player, final Spell spell) {
        if (!isEnabled()) {
            return true;
        }
        return current(player.getUniqueId()) >= spell.getResourceCost();
    }

    /** Spends the spell's resource cost. No-op when the system is off. */
    public void consume(final Player player, final Spell spell) {
        if (!isEnabled()) {
            return;
        }
        final double after = Math.max(0.0D, current(player.getUniqueId()) - spell.getResourceCost());
        resource.put(player.getUniqueId(), after);
    }

    /** Refunds the spell's resource cost (used when a cast no-ops). No-op when the system is off. */
    public void refund(final Player player, final Spell spell) {
        if (!isEnabled()) {
            return;
        }
        final double after = Math.min(max(), current(player.getUniqueId()) + spell.getResourceCost());
        resource.put(player.getUniqueId(), after);
    }

    /**
     * The HUD sidebar line for this player's resource: a coloured 10-segment bar plus the value, or
     * {@code null} when the system is disabled. Read by {@code HudManager} on its scoreboard tick.
     *
     * @param player the viewer
     * @return the rendered line, or null
     */
    public Component hudLine(final Player player) {
        if (!isEnabled()) {
            return null;
        }
        final int maxValue = (int) Math.round(max());
        final int value = Math.max(0, Math.min(maxValue, (int) Math.round(current(player.getUniqueId()))));
        final int filled = Math.round(value / (float) maxValue * 10.0F);
        final NamedTextColor color = colorFor(player);
        Component bar = Component.text(nameFor(player) + " ", NamedTextColor.GRAY);
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text("▰", i < filled ? color : NamedTextColor.DARK_GRAY));
        }
        return bar.append(Component.text(" " + value, NamedTextColor.WHITE));
    }

    /** The player's class-resource display name (Mana / Düh / Energia …) for cast/cost messages. */
    public String resourceName(final Player player) {
        return nameFor(player);
    }

    private String nameFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return "Erő";
        }
        return switch (job) {
            case WARRIOR -> "Düh";
            case ARCHER -> "Fókusz";
            case ASSASSIN -> "Energia";
            case DEATH_KNIGHT -> "Runikus Erő";
            case MONK -> "Csi";
            case WARLOCK -> "Lélekerő";
            case DEMON_HUNTER -> "Fúria";
            case PALADIN -> "Szent Erő";
            case DRUID -> "Természeti Erő";
            case EVOKER -> "Eszencia";
            default -> "Mana"; // Varázsló, Sámán, Pap
        };
    }

    private NamedTextColor colorFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        return switch (job) {
            case WARRIOR, DEATH_KNIGHT -> NamedTextColor.RED;
            case ARCHER, DRUID -> NamedTextColor.GREEN;
            case ASSASSIN, MONK, PALADIN -> NamedTextColor.YELLOW;
            case WARLOCK, DEMON_HUNTER, EVOKER -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.AQUA; // Varázsló, Sámán, Pap
        };
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        resource.remove(playerId);
        lastRegen.remove(playerId);
    }
}
