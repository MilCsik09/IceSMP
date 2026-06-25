package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-class "power" resource shown as a coloured bar <b>in the HUD sidebar</b> (a scoreboard line —
 * deliberately NOT a separate boss bar, so it never stacks with the world-boss bar). A 0–max meter,
 * named and coloured per class (Mana / Düh / Energia / Runikus Erő / Csi …): casting a spell BUILDS
 * it, and once full it DISCHARGES into a short empowerment (strength + speed) and resets to 0.
 *
 * <p>This is an <b>additive reward layer</b> on top of the existing spell costs — it never blocks or
 * breaks a cast. {@link #onSpellCast} runs on the caster's region thread (cast event); the HUD reads
 * {@link #hudLine} on its own scoreboard tick (≤1s lag, and the discharge also flashes the action bar).
 */
public final class ResourceManager implements PlayerStateCleanup {

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final Map<UUID, Integer> resource = new ConcurrentHashMap<>();

    public ResourceManager(final JavaPlugin plugin, final ConfigManager configManager, final JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    private boolean enabled() {
        return configManager.getBoolean("spells.resource.enabled", true);
    }

    private int max() {
        return Math.max(10, configManager.getInt("spells.resource.max", 100));
    }

    private int gain() {
        return Math.max(1, configManager.getInt("spells.resource.gain-per-cast", 25));
    }

    /** Builds the resource after a successful cast; discharges into an empowerment at full. */
    public void onSpellCast(final Player player) {
        if (!enabled()) {
            return;
        }
        final int value = resource.merge(player.getUniqueId(), gain(), Integer::sum);
        if (value >= max()) {
            resource.put(player.getUniqueId(), 0);
            discharge(player);
        }
    }

    private void discharge(final Player player) {
        final int duration = 6 * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, false, false, true));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D),
                30, 0.4D, 0.6D, 0.4D, 0.1D);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.4F);
        player.sendActionBar(Component.text("⚡ Feltöltődve — erőd kirobban!", NamedTextColor.GOLD));
    }

    /**
     * The HUD sidebar line for this player's resource: a coloured 10-segment bar plus the value, or
     * {@code null} when the system is disabled. Read by {@code HudManager} on its scoreboard tick.
     *
     * @param player the viewer
     * @return the rendered line, or null
     */
    public Component hudLine(final Player player) {
        if (!enabled()) {
            return null;
        }
        final int maxValue = max();
        final int value = Math.max(0, Math.min(maxValue, resource.getOrDefault(player.getUniqueId(), 0)));
        final int filled = Math.round(value / (float) maxValue * 10.0F);
        final NamedTextColor color = colorFor(player);
        Component bar = Component.text(nameFor(player) + " ", NamedTextColor.GRAY);
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text("▰", i < filled ? color : NamedTextColor.DARK_GRAY));
        }
        return bar.append(Component.text(" " + value, NamedTextColor.WHITE));
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
    }
}
