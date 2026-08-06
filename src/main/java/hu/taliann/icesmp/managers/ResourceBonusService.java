package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileClassMechanicStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Class-resource pool bonuses with PlayerProfile-backed warlock pact state. */
public final class ResourceBonusService implements Listener {

    private static final String PACT_KEY = "warlock.pact.resource-multiplier";
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final RelicManager relicManager;
    private final PlayerProfileClassMechanicStore mechanics =
            new PlayerProfileClassMechanicStore();
    private final Map<UUID, Double> pactCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> evokerCache = new ConcurrentHashMap<>();

    public ResourceBonusService(final JavaPlugin plugin, final ConfigManager configManager,
                                final JobManager jobManager, final RelicManager relicManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.relicManager = relicManager;
    }

    public double maxMultiplier(final UUID playerId) {
        double multiplier = pactCache.getOrDefault(playerId, 1.0D);
        final String relicId = configManager.getString("pakt.dragon-relic-id", "sarkany_tojas");
        final hu.taliann.icesmp.relics.RelicOwnership ownership =
                relicManager.getOwnership(relicId);
        if (ownership != null && playerId.equals(ownership.owner()) && isEvoker(playerId)) {
            multiplier *= 1.0D + Math.max(0.0D,
                    configManager.getDouble("pakt.dragon-essence-bonus-percent", 10.0D)) / 100.0D;
        }
        return multiplier;
    }

    private boolean isEvoker(final UUID playerId) {
        final Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null && org.bukkit.Bukkit.isOwnedByCurrentRegion(online)) {
            final boolean evoker = jobManager.getPrimaryJob(online) == JobType.EVOKER;
            evokerCache.put(playerId, evoker);
            return evoker;
        }
        return evokerCache.getOrDefault(playerId, Boolean.FALSE);
    }

    public boolean hasPakt(final Player player) {
        if (player == null) return false;
        if (pactCache.containsKey(player.getUniqueId())) return true;
        try {
            final boolean present = mechanics.read(player.getUniqueId(), PACT_KEY).isPresent();
            if (present) loadPact(player.getUniqueId());
            return present;
        } catch (final RuntimeException notReady) {
            return false;
        }
    }

    /** Optimistically updates the runtime mirror; the class-spec CAS proves durability. */
    public void sealPakt(final Player player) {
        if (player == null || hasPakt(player)) return;
        final double multiplier = 1.0D + Math.max(0.0D,
                configManager.getDouble("pakt.bonus-percent", 20.0D)) / 100.0D;
        pactCache.put(player.getUniqueId(), multiplier);
        mechanics.putIfAbsent(player.getUniqueId(), PACT_KEY,
                        Double.toString(multiplier))
                .whenComplete((created, failure) -> {
                    if (failure != null) {
                        pactCache.remove(player.getUniqueId());
                        plugin.getLogger().severe("PlayerProfile warlock pact commit failed for "
                                + player.getUniqueId() + ": " + rootMessage(failure));
                    } else if (!Boolean.TRUE.equals(created)) {
                        loadPact(player.getUniqueId());
                    }
                });
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        evokerCache.put(event.getPlayer().getUniqueId(),
                jobManager.getPrimaryJob(event.getPlayer()) == JobType.EVOKER);
        schedulePactLoad(event.getPlayer(), 0);
    }

    private void schedulePactLoad(final Player player, final int attempt) {
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            if (loadPact(player.getUniqueId()) || attempt >= 39) return;
            schedulePactLoad(player, attempt + 1);
        }, null, 5L);
    }

    private boolean loadPact(final UUID playerId) {
        try {
            final var stored = mechanics.read(playerId, PACT_KEY);
            if (stored.isEmpty()) {
                pactCache.remove(playerId);
                return true;
            }
            final double multiplier = Double.parseDouble(stored.orElseThrow());
            if (!Double.isFinite(multiplier) || multiplier < 1.0D || multiplier > 10.0D) {
                throw new IllegalStateException("invalid warlock pact multiplier");
            }
            pactCache.put(playerId, multiplier);
            return true;
        } catch (final hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.ProfileNotReadyException notReady) {
            return false;
        } catch (final RuntimeException corrupt) {
            plugin.getLogger().severe("PlayerProfile warlock pact read failed for "
                    + playerId + ": " + corrupt.getMessage());
            pactCache.remove(playerId);
            return true;
        }
    }

    @EventHandler
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        pactCache.remove(event.getPlayer().getUniqueId());
        evokerCache.remove(event.getPlayer().getUniqueId());
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
