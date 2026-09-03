package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileSinStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** PlayerProfile-backed infamy, wanted, exile and DARK-oath domain. */
public final class SinManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final PlayerProfileSinStore sinStore = new PlayerProfileSinStore();
    private volatile SpecializationManager specializationManager;

    public SinManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public void setSpecializationManager(final SpecializationManager specializationManager) {
        this.specializationManager = specializationManager;
    }

    public int getSinCount(final Player player) {
        if (player == null) return 0;
        try { return sinStore.read(player.getUniqueId()).count(); }
        catch (final RuntimeException notReady) { return 0; }
    }

    public int getInfamy(final Player player) {
        return getSinCount(player);
    }

    public int addSin(final Player player, final int amount) {
        if (player == null || amount <= 0) return getSinCount(player);
        final int exileThreshold = Math.max(0,
                configManager.getInt("factions.sins.exile-threshold", 4));
        final int wantedThreshold = Math.max(0,
                configManager.getInt("factions.sins.bounty.min-sins", 3));
        try {
            final PlayerProfileSinStore.AddResult result = sinStore.add(
                    player.getUniqueId(), amount, wantedThreshold,
                    exileThreshold).toCompletableFuture().join();
            publishResult(player.getUniqueId(), result);
            return result.state().count();
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile sin mutation failed", unwrap(failure));
        }
    }

    /** Restart-safe exact-once sin mutation for durable outbox consumers. */
    public CompletionStage<Boolean> addSinOnce(final UUID playerId, final int amount,
                                               final String operationId) {
        final int exileThreshold = Math.max(0,
                configManager.getInt("factions.sins.exile-threshold", 4));
        final int wantedThreshold = Math.max(0,
                configManager.getInt("factions.sins.bounty.min-sins", 3));
        return sinStore.addOnce(playerId, amount, wantedThreshold,
                        exileThreshold, operationId)
                .thenApply(result -> {
                    if (result.applied()) publishResult(playerId, result.result());
                    return result.applied();
                });
    }

    private void publishResult(final UUID playerId,
                               final PlayerProfileSinStore.AddResult result) {
        final Player online = Bukkit.getPlayer(playerId);
        if (online == null) return;
        online.getScheduler().run(plugin, task -> {
            if (result.exiled()) applyExileEffects(online);
            reconcileProfileGates(online);
        }, null);
    }

    private void applyExileEffects(final Player player) {
        AdvancementService.award(player, "exiled");
        player.sendMessage(messageManager.getMessage(
                "sinner.exiled",
                "<dark_purple>Bűneid súlya alatt összeroskadt a becsületed: száműzött lettél. A DARK esküt külön kell letenned.</dark_purple>"));
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "sinner.exile-broadcast",
                "<dark_purple>{player} bűnei elérték a tűréshatárt — száműzött lett!</dark_purple>",
                Map.of("player", player.getName())));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6F, 0.7F);
        player.getWorld().spawnParticle(Particle.SQUID_INK,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                40, 0.4D, 0.6D, 0.4D, 0.03D);
    }

    public boolean isSinner(final Player player) {
        if (player == null) return false;
        try { return sinStore.read(player.getUniqueId()).sinner(); }
        catch (final RuntimeException notReady) { return false; }
    }

    public boolean isWanted(final Player player) {
        if (player == null) return false;
        try { return sinStore.read(player.getUniqueId()).wanted(); }
        catch (final RuntimeException notReady) { return false; }
    }

    public boolean isExiled(final Player player) {
        if (player == null) return false;
        try { return sinStore.read(player.getUniqueId()).exiled(); }
        catch (final RuntimeException notReady) { return false; }
    }

    public void markAsSinner(final Player player) {
        if (player == null) return;
        try {
            sinStore.markSinner(player.getUniqueId()).toCompletableFuture().join();
            reconcileProfileGates(player);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile sinner mark failed", unwrap(failure));
        }
    }

    public int reduceSin(final Player player, final int amount) {
        if (player == null) return 0;
        try {
            return sinStore.reduce(player.getUniqueId(), Math.max(0, amount))
                    .toCompletableFuture().join().count();
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile sin reduction failed", unwrap(failure));
        }
    }

    public void resetSinCount(final Player player) {
        if (player == null) return;
        try { sinStore.resetCount(player.getUniqueId()).toCompletableFuture().join(); }
        catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile sin reset failed", unwrap(failure));
        }
    }

    public void sealDarkPact(final Player player) {
        if (player == null || !isExiled(player)) return;
        try {
            sinStore.sealDarkPact(player.getUniqueId()).toCompletableFuture().join();
            reconcileProfileGates(player);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile DARK pact seal failed", unwrap(failure));
        }
    }

    public boolean hasDarkPact(final Player player) {
        if (player == null) return false;
        try { return sinStore.read(player.getUniqueId()).darkPact(); }
        catch (final RuntimeException notReady) { return false; }
    }

    public boolean hasOath(final Player player) {
        return hasDarkPact(player);
    }

    public void exile(final Player player) {
        if (player == null) return;
        final boolean alreadyExiled = isExiled(player);
        try {
            sinStore.exile(player.getUniqueId()).toCompletableFuture().join();
            if (!alreadyExiled) applyExileEffects(player);
            reconcileProfileGates(player);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile exile failed", unwrap(failure));
        }
    }

    public void sealDarkForFactionOverride(final Player player) {
        if (player == null) return;
        try {
            sinStore.sealDarkForFactionOverride(player.getUniqueId())
                    .toCompletableFuture().join();
            reconcileProfileGates(player);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile DARK override failed", unwrap(failure));
        }
    }

    public void clearDarkPactForFactionOverride(final Player player) {
        if (player == null) return;
        try {
            sinStore.clearDarkPactForFactionOverride(player.getUniqueId())
                    .toCompletableFuture().join();
            reconcileProfileGates(player);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile DARK pact override failed", unwrap(failure));
        }
    }

    public void breakDarkPact(final Player player) {
        if (player == null) return;
        try {
            sinStore.breakDarkPact(player.getUniqueId()).toCompletableFuture().join();
            reconcileProfileGates(player);
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile DARK pact break failed", unwrap(failure));
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
        player.getWorld().spawnParticle(Particle.END_ROD,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                60, 0.5D, 0.8D, 0.5D, 0.05D);
        AdvancementService.award(player, "redeemed");
        player.sendMessage(messageManager.getMessage(
                "sinner.pact-broken",
                "<gold>A vezeklésed teljes: a sötét paktum megtört, bűneid feloldozást nyertek.</gold>"));
    }

    public boolean clearSinner(final Player player) {
        if (player == null) return true;
        final boolean cleared;
        try {
            cleared = sinStore.clearSinner(player.getUniqueId()).toCompletableFuture().join();
        } catch (final CompletionException failure) {
            throw new IllegalStateException("PlayerProfile sinner cleanse failed", unwrap(failure));
        }
        if (!cleared) return false;
        reconcileProfileGates(player);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7F, 1.8F);
        player.getWorld().spawnParticle(Particle.END_ROD,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                24, 0.35D, 0.5D, 0.35D, 0.02D);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                16, 0.25D, 0.4D, 0.25D, 0.01D);
        player.sendMessage(messageManager.getMessage(
                "sinner.cleansed", "<green><i>Megtisztultal a buneidtol...</i></green>"));
        return true;
    }

    private void reconcileProfileGates(final Player player) {
        final SpecializationManager specs = specializationManager;
        if (specs != null) specs.reconcileDarkGates(player);
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }
}
