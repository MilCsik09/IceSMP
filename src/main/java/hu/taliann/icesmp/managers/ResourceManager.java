package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
 * Per-class "power" resource: a 0–max meter shown as an Adventure boss bar (the screen-top bar),
 * named and coloured per class (Mana / Düh / Energia / Runikus Erő / Csi …). Casting a spell BUILDS
 * the resource; once full it DISCHARGES into a short empowerment (strength + speed) and resets to 0.
 *
 * <p>This is an <b>additive reward layer</b> on top of the existing spell costs — it does not replace
 * the cost/cooldown pipeline, so it can never block or break a cast. It gives every class a visible,
 * build-to-payoff rotation feel and a clear resource HUD (the original missing class-identity layer).
 *
 * <p>Folia: every bar/effect call runs on the owning player's region thread — {@link #onSpellCast}
 * is invoked from the cast event (player thread), and cleanup from the quit event (player thread),
 * mirroring {@code HudManager}'s boss-bar handling.
 */
public final class ResourceManager implements PlayerStateCleanup {

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final Map<UUID, Integer> resource = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

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
        final int maxValue = max();
        int value = resource.merge(player.getUniqueId(), gain(), Integer::sum);
        if (value >= maxValue) {
            value = 0;
            resource.put(player.getUniqueId(), 0);
            discharge(player);
        }
        updateBar(player, value, maxValue);
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

    private void updateBar(final Player player, final int value, final int maxValue) {
        final BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(Component.empty(), 0.0F, colorFor(player), BossBar.Overlay.NOTCHED_10));
        bar.color(colorFor(player));
        bar.name(Component.text(nameFor(player) + ": " + value + "/" + maxValue, NamedTextColor.AQUA));
        bar.progress(Math.max(0.0F, Math.min(1.0F, value / (float) maxValue)));
        if (value > 0) {
            player.showBossBar(bar);
        } else {
            player.hideBossBar(bar);
        }
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

    private BossBar.Color colorFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return BossBar.Color.PURPLE;
        }
        return switch (job) {
            case WARRIOR, DEATH_KNIGHT -> BossBar.Color.RED;
            case ARCHER, DRUID -> BossBar.Color.GREEN;
            case ASSASSIN, MONK, PALADIN -> BossBar.Color.YELLOW;
            case WARLOCK, DEMON_HUNTER -> BossBar.Color.PURPLE;
            case EVOKER -> BossBar.Color.PINK;
            default -> BossBar.Color.BLUE; // Varázsló, Sámán, Pap
        };
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        resource.remove(playerId);
        final BossBar bar = bars.remove(playerId);
        final Player player = Bukkit.getPlayer(playerId);
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
    }
}
