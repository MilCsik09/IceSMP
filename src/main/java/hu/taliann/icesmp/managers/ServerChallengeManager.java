package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.integrity.*;
import java.util.UUID;
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

/**
 * Collective server challenge: periodically the whole
 * server is handed one timed, shared goal — slay, mine or harvest a target count
 * <em>together</em> — tracked on a boss bar everyone sees. Meet it in time and
 * every online player is rewarded (XP + a raw-item pack, never currency); miss it
 * and it simply lapses. A purely additive, cooperative event that touches no
 * terrain and mints no money.
 *
 * <p>Counting comes from {@link hu.taliann.icesmp.listeners.ServerChallengeListener}
 * on each acting region thread; one monitor guards admission, counting and window changes. The manager
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

    private volatile ServerChallengeRun run;
    private volatile long nextAttemptAt;
    private volatile boolean closed;

    public ServerChallengeManager(final JavaPlugin plugin, final ConfigManager configManager,
                                  final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether a challenge is currently running (for the join listener). */
    public boolean isActive() {
        final var current = run;
        return !closed && current != null && current.snapshot().status() == ServerChallengeRun.Status.ACTIVE;
    }

    /** Milliseconds left in the current challenge window, or -1 when none is running. */
    public long getRemainingMillis() {
        final var current = run;
        if (closed || current == null) return -1L;
        final var snapshot = current.snapshot();
        return snapshot.status() == ServerChallengeRun.Status.ACTIVE ? Math.max(0L, snapshot.activeUntil() - System.currentTimeMillis()) : -1L;
    }

    /** Shows the boss bar to a (joining) player if a challenge is live. */
    public void showTo(final Player player) {
        if (isActive()) {
            player.showBossBar(bar);
        }
    }

    /**
     * Records one unit of progress of the given kind; if it meets the target it
     * settles the challenge as a success (once). Called on the acting region thread.
     *
     * @param kind the progress kind that just happened
     */
    public synchronized void record(final ChallengeType kind, final RewardContext contribution) {
        if (closed || run == null || !run.record(kind, contribution, System.currentTimeMillis())) return;
        final var current = run.snapshot();
        updateBar(current);
        if (current.status() == ServerChallengeRun.Status.SUCCEEDED) succeed(current);
    }

    /** Periodic driver on the global world-events tick. */
    public synchronized void tick() {
        if (closed) return;
        if (!configManager.getBoolean("server-challenge.enabled", true)) {
            if (isActive()) {
                stop();
            }
            return;
        }

        final long nowMillis = System.currentTimeMillis();
        if (isActive()) {
            if (run.expire(nowMillis)) {
                fail(run.snapshot());
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
        final var current = run;
        return current == null ? 0L : current.snapshot().progress();
    }

    /** The running challenge's goal count (menu display; 0 when inactive). */
    public long getTarget() {
        final var current = run;
        if (closed || current == null) return 0L;
        final var snapshot = current.snapshot();
        return snapshot.status() == ServerChallengeRun.Status.ACTIVE ? snapshot.target() : 0L;
    }

    /** Hungarian goal text of the running challenge (menu display), or null when inactive. */
    public String describeGoal() {
        final var current = run;
        if (closed || current == null) return null;
        final var snapshot = current.snapshot();
        return snapshot.status() == ServerChallengeRun.Status.ACTIVE ? goalText(snapshot.type(), snapshot.target()) : null;
    }

    /** Admin override: starts a random enabled challenge now. Returns false if one runs or none enabled. */
    public synchronized boolean forceStart() {
        if (closed || isActive()) {
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
        // Népesség-skálázás: a cél = per-player érték × online létszám,
        // ha a per-player mód él — így 3 és 30 fősen is elérhető marad a közös cél.
        final long baseTarget = Math.max(1L, configManager.getLong(
                "server-challenge.targets." + picked.configKey(), defaultTarget(picked)));
        final long target = configManager.getBoolean("server-challenge.per-player-targets", true)
                ? Math.max(1L, configManager.getLong("server-challenge.targets-per-player." + picked.configKey(),
                        Math.max(1L, baseTarget / 10L)) * Bukkit.getOnlinePlayers().size())
                : baseTarget;
        run = new ServerChallengeRun(picked, target, System.currentTimeMillis(), durationMillis());
        updateBar(run.snapshot());
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            final UUID id = player.getUniqueId();
            player.getScheduler().run(plugin, task -> {
                final Player current = Bukkit.getPlayer(id);
                if (current != null && Bukkit.isOwnedByCurrentRegion(current) && current.isOnline() && isActive()) current.showBossBar(bar);
            }, null);
        }
        announce(messageManager.getMessage(
                "server-challenge-start",
                "&6⚔ SZERVER-KIHÍVÁS: {goal} — {minutes} perc! Teljesítsétek együtt, és mindenki jutalmat kap!",
                Map.of(
                        "goal", goalText(picked, target),
                        "minutes", String.valueOf(Math.max(1L, durationMillis() / 60_000L))
                )
        ));
    }

    private void succeed(final ServerChallengeRun.Snapshot completed) {
        hideBarFromAll();
        announce(messageManager.getMessage(
                "server-challenge-success",
                "&a✔ SZERVER-KIHÍVÁS teljesítve! Mindenki jutalmat kap — szép munka!"));
        rewardAll(completed);
    }

    private void fail(final ServerChallengeRun.Snapshot completed) {
        hideBarFromAll();
        announce(messageManager.getMessage(
                "server-challenge-fail",
                "&7✘ A szerver-kihívás lejárt — {progress}/{target}. Legközelebb összekapjuk magunkat!",
                Map.of("progress", String.valueOf(completed.progress()), "target", String.valueOf(completed.target()))
        ));
    }

    /** Hides the live boss bar on plugin disable so no frozen bar lingers on clients. */
    public synchronized void shutdown() {
        if (isActive()) stop();
        closed = true;
    }

    /** Silent stop (feature disabled): just clear the window and bar. */
    private void stop() {
        if (run != null) run.stop();
        hideBarFromAll();
    }

    private void rewardAll(final ServerChallengeRun.Snapshot completed) {
        final int rolls = Math.max(1, configManager.getInt("server-challenge.reward-rolls", 2));
        final int xp = Math.max(0, configManager.getInt("server-challenge.reward-xp", 300));
        final int hasteSeconds = Math.max(0, configManager.getInt("server-challenge.reward-haste-seconds", 45));
        for (final Player candidate : List.copyOf(Bukkit.getOnlinePlayers())) {
            final UUID recipient = candidate.getUniqueId();
            candidate.getScheduler().run(plugin, task -> {
                final Player player = Bukkit.getPlayer(recipient);
                if (closed || player == null || !Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline()) return;
                if (!rewardAllowed(completed, player, RewardChannel.SERVER_CHALLENGE)) return;
                if (rewardAllowed(completed, player, RewardChannel.ITEM_ACQUISITION)) {
                    for (final ItemStack loot : LootTable.roll(configManager, "server-challenge.reward-loot", rolls)) {
                        if (!rewardAllowed(completed, player, RewardChannel.ITEM_ACQUISITION)) return;
                        player.getInventory().addItem(loot).values()
                                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    }
                }
                if (xp > 0 && rewardAllowed(completed, player, RewardChannel.VANILLA_XP)) player.giveExp(xp);
                if (hasteSeconds > 0 && rewardAllowed(completed, player, RewardChannel.EVENT_REWARD)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, hasteSeconds * 20, 0, true, false, true));
                }
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }, null);
        }
    }

    private boolean rewardAllowed(final ServerChallengeRun.Snapshot completed, final Player player, final RewardChannel channel) {
        try { return !closed && ServerChallengeRun.rewardAllowed(completed,
                new RewardContext(channel, player.getUniqueId(), BukkitRewardSources.causal(player))); }
        catch (final RuntimeException | LinkageError unavailable) { return false; }
    }

    private synchronized void updateBar(final ServerChallengeRun.Snapshot current) {
        final float fraction = (float) Math.max(0.0D, Math.min(1.0D, (double) current.progress() / current.target()));
        bar.progress(fraction);
        bar.name(Component.text("⚔ " + goalText(current.type(), current.target()) + " — " + current.progress() + " / " + current.target(), NamedTextColor.GOLD));
    }

    private void hideBarFromAll() {
        for (final Player candidate : List.copyOf(Bukkit.getOnlinePlayers())) {
            final UUID id = candidate.getUniqueId();
            candidate.getScheduler().run(plugin, task -> {
                final Player player = Bukkit.getPlayer(id);
                if (player != null && Bukkit.isOwnedByCurrentRegion(player) && player.isOnline() && !isActive()) player.hideBossBar(bar);
            }, null);
        }
    }

    private void announce(final Component message) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            if (!closed) Bukkit.getServer().broadcast(message);
        });
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
