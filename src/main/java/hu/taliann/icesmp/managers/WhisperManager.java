package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.WhisperEvidenceLedger;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileWhisperStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** PlayerProfile-backed hidden whisperer role with fixed, evidence-driven exposure stages. */
public final class WhisperManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;
    private final PlayerProfileWhisperStore whisperStore = new PlayerProfileWhisperStore();
    private final WhisperEvidenceLedger evidence = new WhisperEvidenceLedger();
    /** Online routing projection rebuilt from PlayerProfile. */
    private final java.util.Set<UUID> whispererCache = ConcurrentHashMap.newKeySet();

    public WhisperManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final FactionManager factionManager, final SinManager sinManager,
                          final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("factions.whisper.enabled", true);
    }

    /** A successful cultist event removes one exposure stage and still shares its loot. */
    public void rewardFaithful() {
        if (!isEnabled()) return;
        for (final Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!isWhispererCached(online.getUniqueId())) continue;
            whisperStore.applyCover(online.getUniqueId())
                    .whenComplete((result, failure) -> online.getScheduler().run(plugin, task -> {
                        if (failure != null || result == null || !result.state().whisperer()) return;
                        if (result.applied()) {
                            online.sendMessage(messageManager.getMessage("whisper-queen-favor",
                                    "<dark_gray>🕯 A Kapu érzi a hűséged — eggyel halványabb lett körülötted a gyanú.</dark_gray>"));
                        }
                        final int lootRolls = Math.max(0,
                                configManager.getInt("cultists.whisper-loot-rolls", 1));
                        boolean gaveAny = false;
                        for (final org.bukkit.inventory.ItemStack loot
                                : LootTable.roll(configManager, "cultists.rite-loot", lootRolls)) {
                            online.getInventory().addItem(loot).values().forEach(left ->
                                    online.getWorld().dropItemNaturally(online.getLocation(), left));
                            gaveAny = true;
                        }
                        if (gaveAny) {
                            online.sendMessage(messageManager.getMessage("whisper-queen-share",
                                    "<dark_gray>🕯 A hálózat osztozik a zsákmányon — csendben tedd el, ami a tiéd.</dark_gray>"));
                        }
                    }, null));
        }
    }

    public boolean isWhispererCached(final UUID playerId) {
        return whispererCache.contains(playerId)
                && factionManager.isEligibleForFactionBenefits(playerId)
                && !factionManager.isMember(playerId, FactionType.DARK);
    }

    public boolean canHearWhispersCached(final UUID playerId) {
        return isWhispererCached(playerId)
                || darkHears() && factionManager.isMember(playerId, FactionType.DARK);
    }

    public boolean canHearWhispers(final Player player) {
        return player != null && ((isWhisperer(player)
                && factionManager.isEligibleForFactionBenefits(player.getUniqueId())
                && !factionManager.isMember(player.getUniqueId(), FactionType.DARK))
                || darkHears() && factionManager.isMember(player.getUniqueId(), FactionType.DARK));
    }

    private boolean darkHears() {
        return configManager.getBoolean("factions.whisper.dark-hears-channel", true);
    }

    public boolean isWhisperer(final Player player) {
        if (player == null) return false;
        try { return whisperStore.read(player.getUniqueId()).whisperer(); }
        catch (final RuntimeException notReady) { return false; }
    }

    public PlayerProfileWhisperStore.Stage getStage(final Player player) {
        if (player == null) return PlayerProfileWhisperStore.Stage.CLEAN;
        try { return whisperStore.read(player.getUniqueId()).stage(); }
        catch (final RuntimeException notReady) { return PlayerProfileWhisperStore.Stage.CLEAN; }
    }

    public void makeWhisperer(final Player player) {
        if (player == null || !canBecomeWhisperer(player)) return;
        whisperStore.makeWhisperer(player.getUniqueId())
                .thenAccept(state -> whispererCache.add(player.getUniqueId()))
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile whisperer grant failed for "
                            + player.getUniqueId() + ": " + rootMessage(failure));
                    return null;
                });
    }

    public void handleJoin(final Player player) {
        reconcileMembership(player);
    }

    /** Guests and explicit DARK citizens cannot retain the hidden role. */
    public void reconcileMembership(final Player player) {
        if (player == null) return;
        final UUID playerId = player.getUniqueId();
        if (!factionManager.isEligibleForFactionBenefits(playerId)
                || factionManager.isMember(playerId, FactionType.DARK)) {
            whispererCache.remove(playerId);
            whisperStore.clear(playerId).exceptionally(failure -> {
                plugin.getLogger().severe("PlayerProfile whisper membership cleanup failed for "
                        + playerId + ": " + rootMessage(failure));
                return null;
            });
            return;
        }
        try {
            if (whisperStore.read(playerId).whisperer()) whispererCache.add(playerId);
            else whispererCache.remove(playerId);
        } catch (final RuntimeException notReady) {
            whispererCache.remove(playerId);
        }
    }

    public void recordAccusation(final Player suspect) {
        if (!isEnabled() || suspect == null || !isWhisperer(suspect)) return;
        whisperStore.advance(suspect.getUniqueId())
                .whenComplete((result, failure) -> suspect.getScheduler().run(plugin, task -> {
                    if (failure != null || result == null) {
                        plugin.getLogger().severe("PlayerProfile whisper accusation failed for "
                                + suspect.getUniqueId() + ": " + rootMessage(failure));
                        return;
                    }
                    suspect.sendMessage(messageManager.getMessage("whisper-stage-advanced",
                            "<red>A leleplezés közelebb ért: <white>{stage}</white>.</red>",
                            Map.of("stage", result.state().stage().displayName())));
                    if (result.exposed()) {
                        whispererCache.remove(suspect.getUniqueId());
                        applyExposure(suspect);
                    }
                }, null));
    }

    public void grantEvidence(final UUID witnessId, final UUID suspectId) {
        final long seconds = Math.max(10L,
                configManager.getLong("factions.whisper.witness-seconds", 120L));
        final long ttlMillis = seconds > Long.MAX_VALUE / 1_000L
                ? Long.MAX_VALUE : seconds * 1_000L;
        evidence.grant(witnessId, suspectId, ttlMillis);
    }

    public boolean hasEvidence(final UUID witnessId, final UUID suspectId) {
        return evidence.has(witnessId, suspectId);
    }

    public boolean consumeEvidence(final UUID witnessId, final UUID suspectId) {
        return evidence.consume(witnessId, suspectId);
    }

    /** Explicit exposure first commits the final stage, then runs owner-thread effects. */
    public void expose(final Player player) {
        if (player == null || !isWhisperer(player)) return;
        whispererCache.remove(player.getUniqueId());
        whisperStore.forceExpose(player.getUniqueId())
                .whenComplete((state, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure != null) {
                        reconcileMembership(player);
                        plugin.getLogger().severe("PlayerProfile whisper exposure failed for "
                                + player.getUniqueId() + ": " + rootMessage(failure));
                        return;
                    }
                    applyExposure(player);
                }, null));
    }

    private void applyExposure(final Player player) {
        player.getWorld().spawnParticle(Particle.SOUL,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                60, 0.6D, 1.0D, 0.6D, 0.05D);
        player.getWorld().spawnParticle(Particle.SQUID_INK,
                player.getLocation().add(0.0D, 1.5D, 0.0D),
                30, 0.5D, 0.8D, 0.5D, 0.03D);
        player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.2F, 0.5F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0F, 0.4F);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        sinManager.exile(player);
        if (configManager.getBoolean("factions.whisper.expose-broadcast", true)) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "whisper-exposed",
                    "<dark_purple>💀 A Suttogás lelepleződött: <white>{player}</white> a Néma Királynő szolgája!</dark_purple>",
                    Map.of("player", player.getName())));
        }
    }

    public void deliverWhisper(final Player sender, final String message) {
        final net.kyori.adventure.text.Component line = messageManager.getMessage(
                "whisper-chat-line",
                "<dark_purple>✧ Suttogás</dark_purple> <gray>{sender}:</gray> <light_purple>{message}</light_purple>",
                Map.of("sender", sender.getName(), "message", message));
        for (final Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!canHearWhispersCached(online.getUniqueId())
                    && !online.getUniqueId().equals(sender.getUniqueId())) continue;
            online.getScheduler().run(plugin, task -> {
                if (canHearWhispers(online)
                        || online.getUniqueId().equals(sender.getUniqueId())) {
                    online.sendMessage(line);
                    online.playSound(online.getLocation(),
                            Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.4F, 1.6F);
                }
            }, null);
        }
    }

    public boolean canBecomeWhisperer(final Player player) {
        return player != null
                && factionManager.isEligibleForFactionBenefits(player.getUniqueId())
                && !factionManager.isMember(player.getUniqueId(), FactionType.DARK)
                && !sinManager.isExiled(player)
                && !isWhisperer(player);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        evidence.clearPlayer(playerId);
        whispererCache.remove(playerId);
    }

    private static String rootMessage(final Throwable failure) {
        if (failure == null) return "unknown failure";
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
