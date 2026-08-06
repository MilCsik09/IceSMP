package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileIntroStore;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** First-join intro sequence backed exclusively by PlayerProfile onboarding state. */
public final class IntroManager {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerProfileIntroStore introStore = new PlayerProfileIntroStore();

    public IntroManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public CompletionStage<Boolean> hasSeenIntro(final Player player) {
        return introStore.hasSeen(player.getUniqueId());
    }

    /** Restores interrupted cinematic state before deciding whether first-join intro should run. */
    public CompletionStage<Void> playOnFirstJoin(final Player player) {
        if (!configManager.getBoolean("world-events.intro.enabled", true)) {
            return runOnOwner(player, () -> playReturningWelcome(player));
        }
        return introStore.hasSeen(player.getUniqueId()).thenCompose(seen -> {
            if (Boolean.TRUE.equals(seen)) {
                return runOnOwner(player, () -> playReturningWelcome(player));
            }
            return introStore.markSeen(player.getUniqueId()).thenCompose(marked -> {
                if (!Boolean.TRUE.equals(marked)) {
                    return runOnOwner(player, () -> playReturningWelcome(player));
                }
                final Location firstSpawn = parseFirstJoinSpawn();
                if (firstSpawn == null) {
                    return runOnOwner(player, () -> play(player));
                }
                return player.teleportAsync(firstSpawn).thenCompose(success ->
                        runOnOwner(player, () -> play(player)));
            });
        }).exceptionally(failure -> {
            plugin.getLogger().severe("PlayerProfile intro initialization failed for "
                    + player.getUniqueId() + ": " + failure.getMessage());
            return null;
        });
    }

    private Location parseFirstJoinSpawn() {
        final String raw = configManager.getString("world-events.intro.first-join-spawn", "");
        if (raw.isBlank()) return null;
        final String[] parts = raw.split(",");
        if (parts.length < 4) return null;
        final World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) return null;
        try {
            final Location location = new Location(world, Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
            if (parts.length >= 6) {
                location.setYaw(Float.parseFloat(parts[4].trim()));
                location.setPitch(Float.parseFloat(parts[5].trim()));
            }
            return location;
        } catch (final NumberFormatException exception) {
            plugin.getLogger().warning("Hibás intro.first-join-spawn formátum: " + raw);
            return null;
        }
    }

    private void playReturningWelcome(final Player player) {
        if (!configManager.getBoolean("world-events.intro.join-welcome.enabled", true)) return;
        final Title title = Title.title(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        configManager.getString("world-events.intro.join-welcome.title", "<aqua>❄ IceSMP</aqua>")),
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        configManager.getString("world-events.intro.join-welcome.subtitle",
                                "<gray>Üdv újra a fagyott királyságok földjén!</gray>")),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(1600),
                        Duration.ofMillis(600)));
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.2F);
    }

    public void play(final Player player) {
        final List<String> lines = configManager.getStringList("world-events.intro.lines");
        if (lines.isEmpty()) return;
        final long staggerTicks = Math.max(1L,
                configManager.getLong("world-events.intro.stagger-seconds", 4L)) * 20L;
        for (int index = 0; index < lines.size(); index++) {
            final String rawLine = lines.get(index);
            final long delay = Math.max(1L, staggerTicks * index);
            player.getScheduler().runDelayed(plugin, task -> showCard(player, rawLine), null, delay);
        }
        playCinematic(player);
    }

    public void playCinematic(final Player player) {
        if (!configManager.getBoolean("world-events.intro.cinematic.enabled", false)) return;
        final List<String> waypoints = configManager.getStringList(
                "world-events.intro.cinematic.waypoints");
        if (waypoints.isEmpty()) return;
        introStore.beginCinematic(player.getUniqueId(), player.getGameMode().name())
                .thenCompose(started -> {
                    if (!Boolean.TRUE.equals(started)) return CompletableFuture.completedFuture(null);
                    return runOnOwner(player, () -> startCinematicRuntime(player, waypoints));
                }).exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile cinematic start failed for "
                            + player.getUniqueId() + ": " + failure.getMessage());
                    return null;
                });
    }

    private void startCinematicRuntime(final Player player, final List<String> waypoints) {
        player.setGameMode(GameMode.SPECTATOR);
        final long perTicks = Math.max(1L,
                configManager.getLong("world-events.intro.cinematic.point-seconds", 3L)) * 20L;
        for (int index = 0; index < waypoints.size(); index++) {
            final Location target = parseWaypoint(waypoints.get(index), player.getWorld());
            if (target == null) continue;
            player.getScheduler().runDelayed(plugin, task -> player.teleportAsync(target), null,
                    Math.max(1L, perTicks * index));
        }
        player.getScheduler().runDelayed(plugin,
                task -> restoreCinematicIfNeeded(player), null,
                Math.max(2L, perTicks * waypoints.size()));
    }

    public CompletionStage<Void> restoreCinematicIfNeeded(final Player player) {
        return introStore.cinematicState(player.getUniqueId()).<Void>thenCompose(state -> {
            if (!state.active()) return CompletableFuture.<Void>completedFuture(null);
            return runOnOwner(player, () -> restoreGamemode(player, state.previousGamemode()))
                    .thenCompose(ignored -> introStore.completeCinematic(player.getUniqueId()))
                    .thenApply(ignored -> (Void) null);
        }).exceptionally(failure -> {
            plugin.getLogger().severe("PlayerProfile cinematic recovery failed for "
                    + player.getUniqueId() + ": " + failure.getMessage());
            return null;
        });
    }

    private static void restoreGamemode(final Player player, final String previous) {
        GameMode mode = GameMode.SURVIVAL;
        try { mode = GameMode.valueOf(previous); }
        catch (final IllegalArgumentException ignored) { mode = GameMode.SURVIVAL; }
        if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(mode);
    }

    private Location parseWaypoint(final String raw, final World defaultWorld) {
        final String[] parts = raw.split(",");
        try {
            if (parts.length >= 6) {
                final World world = Bukkit.getWorld(parts[0].trim());
                final World resolved = world == null ? defaultWorld : world;
                return new Location(resolved, Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()),
                        Float.parseFloat(parts[4].trim()), Float.parseFloat(parts[5].trim()));
            }
            if (parts.length == 5) {
                return new Location(defaultWorld, Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()),
                        Float.parseFloat(parts[3].trim()), Float.parseFloat(parts[4].trim()));
            }
            if (parts.length >= 3) {
                return new Location(defaultWorld, Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
            }
        } catch (final NumberFormatException exception) {
            return null;
        }
        return null;
    }

    private void showCard(final Player player, final String rawLine) {
        final String[] parts = rawLine.split("\\|\\|", 2);
        final Component titleLine = SECTION.deserialize(TextUtil.color(parts[0].trim()));
        final Component subtitleLine = parts.length > 1
                ? SECTION.deserialize(TextUtil.color(parts[1].trim())) : Component.empty();
        player.showTitle(Title.title(titleLine, subtitleLine,
                Title.Times.times(Duration.ofMillis(400L), Duration.ofMillis(3200L),
                        Duration.ofMillis(400L))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7F, 1.2F);
    }

    private CompletionStage<Void> runOnOwner(final Player player, final Runnable action) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            try {
                action.run();
                result.complete(null);
            } catch (final Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, () -> result.completeExceptionally(
                new IllegalStateException("Player scheduler rejected intro operation")));
        return result;
    }
}
