package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
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

/** PlayerProfile-backed hidden whisperer role and suspicion domain. */
public final class WhisperManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;
    private final PlayerProfileWhisperStore whisperStore = new PlayerProfileWhisperStore();

    /** Short-lived witness tokens are runtime-only and intentionally not persisted. */
    private final Map<UUID, Long> witnessUntil = new ConcurrentHashMap<>();
    /** Online routing projection rebuilt from PlayerProfile. */
    private final java.util.Set<UUID> whispererCache = ConcurrentHashMap.newKeySet();
    private volatile long nextDecayAt;

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

    /** Cultist success reduces suspicion only after the durable section-CAS commits. */
    public void rewardFaithful(final double relief) {
        if (!isEnabled() || !Double.isFinite(relief) || relief <= 0.0D) return;
        final double threshold = suspicionThreshold();
        for (final Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!isWhispererCached(online.getUniqueId())) continue;
            whisperStore.adjust(online.getUniqueId(), -relief, threshold)
                    .whenComplete((result, failure) -> online.getScheduler().run(plugin, task -> {
                        if (failure != null || result == null || !result.state().whisperer()) return;
                        online.sendMessage(messageManager.getMessage("whisper-queen-favor",
                                "<dark_gray>🕯 A Kapu érzi a hűséged — a gyanú árnyéka halványul körülötted. <gray>(−{relief} gyanú)</gray></dark_gray>",
                                Map.of("relief", String.valueOf((int) relief))));
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

    public double getSuspicion(final Player player) {
        if (player == null) return 0.0D;
        try { return whisperStore.read(player.getUniqueId()).suspicion(); }
        catch (final RuntimeException notReady) { return 0.0D; }
    }

    public void addSuspicion(final Player player, final double amount) {
        if (!isEnabled() || player == null || !isWhisperer(player)
                || !Double.isFinite(amount) || amount <= 0.0D) return;
        whisperStore.adjust(player.getUniqueId(), amount, suspicionThreshold())
                .whenComplete((result, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure != null || result == null) {
                        plugin.getLogger().severe("PlayerProfile suspicion mutation failed for "
                                + player.getUniqueId() + ": " + rootMessage(failure));
                        return;
                    }
                    if (result.exposed()) {
                        whispererCache.remove(player.getUniqueId());
                        applyExposure(player);
                    }
                }, null));
    }

    public void grantWitnessToken(final UUID playerId) {
        final long seconds = Math.max(10L,
                configManager.getLong("factions.whisper.witness-seconds", 120L));
        witnessUntil.put(playerId, System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean hasWitnessToken(final UUID playerId) {
        final Long until = witnessUntil.get(playerId);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            witnessUntil.remove(playerId);
            return false;
        }
        return true;
    }

    public void consumeWitnessToken(final UUID playerId) {
        witnessUntil.remove(playerId);
    }

    /** Explicit exposure first clears PlayerProfile, then runs owner-thread effects. */
    public void expose(final Player player) {
        if (player == null || !isWhisperer(player)) return;
        whispererCache.remove(player.getUniqueId());
        whisperStore.clear(player.getUniqueId())
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
        final int sins = Math.max(1,
                configManager.getInt("factions.whisper.exposure-sins", 4));
        sinManager.addSin(player, sins);
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

    public void tick() {
        if (!isEnabled()) return;
        final long now = System.currentTimeMillis();
        if (now < nextDecayAt) return;
        nextDecayAt = now + Math.max(1L,
                configManager.getLong("factions.whisper.decay-minutes", 10L)) * 60_000L;
        final double decay = Math.max(0.0D,
                configManager.getDouble("factions.whisper.decay-amount", 5.0D));
        if (decay <= 0.0D) return;
        for (final UUID playerId : List.copyOf(whispererCache)) {
            final Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            whisperStore.adjust(playerId, -decay, suspicionThreshold())
                    .exceptionally(failure -> {
                        plugin.getLogger().severe("PlayerProfile whisper decay failed for "
                                + playerId + ": " + rootMessage(failure));
                        return null;
                    });
        }
    }

    public boolean canBecomeWhisperer(final Player player) {
        return player != null
                && factionManager.isEligibleForFactionBenefits(player.getUniqueId())
                && !factionManager.isMember(player.getUniqueId(), FactionType.DARK)
                && !isWhisperer(player);
    }

    private double suspicionThreshold() {
        return Math.max(1.0D,
                configManager.getDouble("factions.whisper.suspicion-threshold", 100.0D));
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        witnessUntil.remove(playerId);
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
