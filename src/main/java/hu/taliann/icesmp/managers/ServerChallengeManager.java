package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collective server challenge (ROADMAP "élőbb világ"): periodically the whole
 * server is handed one timed, shared goal — slay, mine or harvest a target count
 * <em>together</em> — tracked on a boss bar everyone sees. Meet it in time and
 * every online player is rewarded (XP + a raw-item pack, never currency); miss it
 * and it simply lapses. A purely additive, cooperative event that touches no
 * terrain and mints no money.
 *
 * <p>Counting comes from {@link hu.taliann.icesmp.listeners.ServerChallengeListener}
 * on each acting region thread (an atomic counter); the manager flips the window
 * and settles success/failure exactly once.
 */
public final class ServerChallengeManager {

    /** The shared goal kinds; each is individually toggleable in config. */
    public enum ChallengeType {
        SLAY, MINE, HARVEST;

        String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private final BossBar bar = BossBar.bossBar(Component.empty(), 0.0F, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);

    private volatile boolean active;
    private volatile long activeUntil;
    private volatile long nextAttemptAt;
    private volatile ChallengeType type;
    private volatile long target;
    private final AtomicLong progress = new AtomicLong();
    private final AtomicBoolean settled = new AtomicBoolean(true);

    public ServerChallengeManager(final JavaPlugin plugin, final ConfigManager configManager,
                                  final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether a challenge is currently running (for the join listener). */
    public boolean isActive() {
        return active;
    }

    /** Milliseconds left in the current challenge window, or -1 when none is running. */
    public long getRemainingMillis() {
        return active ? Math.max(0L, activeUntil - System.currentTimeMillis()) : -1L;
    }

    /** Shows the boss bar to a (joining) player if a challenge is live. */
    public void showTo(final Player player) {
        if (active) {
            player.showBossBar(bar);
        }
    }

    /**
     * Records one unit of progress of the given kind; if it meets the target it
     * settles the challenge as a success (once). Called on the acting region thread.
     *
     * @param kind the progress kind that just happened
     */
    public void record(final ChallengeType kind) {
        if (!active || kind != type) {
            return;
        }
        final long now = progress.incrementAndGet();
        updateBar(now);
        if (now >= target && settled.compareAndSet(false, true)) {
            succeed();
        }
    }

    /** Periodic driver on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("server-challenge.enabled", true)) {
            if (active) {
                stop();
            }
            return;
        }

        final long nowMillis = System.currentTimeMillis();
        if (active) {
            if (nowMillis >= activeUntil && settled.compareAndSet(false, true)) {
                fail();
            }
            return;
        }

        if (nowMillis >= nextAttemptAt) {
            nextAttemptAt = nowMillis + intervalMillis();
            // Kevés online játékosnál a közös cél értelmetlen (0/1000 lejáratok) — kihagyjuk.
            if (org.bukkit.Bukkit.getOnlinePlayers().size()
                    < Math.max(0, configManager.getInt("server-challenge.min-online-players", 2))) {
                return;
            }
            final double chance = Math.max(0.0D, Math.min(100.0D,
                    configManager.getDouble("server-challenge.chance-percent", 30.0D)));
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                start(pickRandomEnabled());
            }
        }
    }

    /** Live progress of the running challenge (menu display). */
    public long getProgress() {
        return progress.get();
    }

    /** The running challenge's goal count (menu display; 0 when inactive). */
    public long getTarget() {
        return active ? target : 0L;
    }

    /** Hungarian goal text of the running challenge (menu display), or null when inactive. */
    public String describeGoal() {
        return active ? goalText(type, target) : null;
    }

    /** Admin override: starts a random enabled challenge now. Returns false if one runs or none enabled. */
    public boolean forceStart() {
        if (active) {
            return false;
        }
        final ChallengeType picked = pickRandomEnabled();
        if (picked == null) {
            return false;
        }
        start(picked);
        return true;
    }

    private ChallengeType pickRandomEnabled() {
        final List<ChallengeType> enabled = new java.util.ArrayList<>();
        for (final ChallengeType kind : ChallengeType.values()) {
            if (configManager.getBoolean("server-challenge.types." + kind.configKey(), true)) {
                enabled.add(kind);
            }
        }
        return enabled.isEmpty() ? null : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
    }

    private void start(final ChallengeType picked) {
        if (picked == null) {
            return;
        }
        type = picked;
        // Népesség-skálázás: a cél = per-player érték × online létszám,
        // ha a per-player mód él — így 3 és 30 fősen is elérhető marad a közös cél.
        final long baseTarget = Math.max(1L, configManager.getLong(
                "server-challenge.targets." + picked.configKey(), defaultTarget(picked)));
        target = configManager.getBoolean("server-challenge.per-player-targets", true)
                ? Math.max(1L, configManager.getLong("server-challenge.targets-per-player." + picked.configKey(),
                        Math.max(1L, baseTarget / 10L)) * Bukkit.getOnlinePlayers().size())
                : baseTarget;
        progress.set(0L);
        active = true;
        settled.set(false);
        activeUntil = System.currentTimeMillis() + durationMillis();

        updateBar(0L);
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> player.showBossBar(bar), null);
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "server-challenge-start",
                "&6⚔ SZERVER-KIHÍVÁS: {goal} — {minutes} perc! Teljesítsétek együtt, és mindenki jutalmat kap!",
                Map.of(
                        "goal", goalText(type, target),
                        "minutes", String.valueOf(Math.max(1L, durationMillis() / 60_000L))
                )
        ));
    }

    private void succeed() {
        active = false;
        hideBarFromAll();
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "server-challenge-success",
                "&a✔ SZERVER-KIHÍVÁS teljesítve! Mindenki jutalmat kap — szép munka!"));
        rewardAll();
    }

    private void fail() {
        active = false;
        hideBarFromAll();
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "server-challenge-fail",
                "&7✘ A szerver-kihívás lejárt — {progress}/{target}. Legközelebb összekapjuk magunkat!",
                Map.of("progress", String.valueOf(progress.get()), "target", String.valueOf(target))
        ));
    }

    /** Hides the live boss bar on plugin disable so no frozen bar lingers on clients. */
    public void shutdown() {
        if (active) {
            stop();
        }
    }

    /** Silent stop (feature disabled): just clear the window and bar. */
    private void stop() {
        active = false;
        settled.set(true);
        hideBarFromAll();
    }

    private void rewardAll() {
        final int rolls = Math.max(1, configManager.getInt("server-challenge.reward-rolls", 2));
        final int xp = Math.max(0, configManager.getInt("server-challenge.reward-xp", 300));
        final int hasteSeconds = Math.max(0, configManager.getInt("server-challenge.reward-haste-seconds", 45));
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                for (final ItemStack loot : LootTable.roll(configManager, "server-challenge.reward-loot", rolls)) {
                    player.getInventory().addItem(loot).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
                if (xp > 0) {
                    player.giveExp(xp);
                }
                if (hasteSeconds > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, hasteSeconds * 20, 0, true, false, true));
                }
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }, null);
        }
    }

    // synchronized: a record() bármely régió-szálról hívja — a megosztott BossBar
    // progress/name mutációja verseny nélkül fusson.
    private synchronized void updateBar(final long current) {
        final float fraction = target <= 0L ? 0.0F : (float) Math.max(0.0D, Math.min(1.0D, (double) current / target));
        bar.progress(fraction);
        bar.name(Component.text("⚔ " + goalText(type, target) + " — " + current + " / " + target, NamedTextColor.GOLD));
    }

    private void hideBarFromAll() {
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> player.hideBossBar(bar), null);
        }
    }

    private String goalText(final ChallengeType kind, final long amount) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case SLAY -> amount + " szörny leölése";
            case MINE -> amount + " érc kibányászása";
            case HARVEST -> amount + " termés betakarítása";
        };
    }

    private long defaultTarget(final ChallengeType kind) {
        return switch (kind) {
            case SLAY -> 500L;
            case MINE -> 800L;
            case HARVEST -> 1000L;
        };
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("server-challenge.interval-minutes", 100L)) * 60_000L;
    }

    private long durationMillis() {
        return Math.max(1L, configManager.getLong("server-challenge.duration-minutes", 30L)) * 60_000L;
    }
}
