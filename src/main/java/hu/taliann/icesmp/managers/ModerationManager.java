package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.moderation.LastKnownLocation;
import hu.taliann.icesmp.moderation.PunishmentLedger;
import hu.taliann.icesmp.moderation.PunishmentRecord;
import hu.taliann.icesmp.moderation.PunishmentState;
import hu.taliann.icesmp.moderation.PunishmentType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.CriticalPersistenceWriteError;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Authoritative native moderation state: one punishment ledger plus the persistent SocialSpy,
 * vanish and last-logout-location settings. Gameplay mutations run on Paper's async scheduler,
 * save atomically, and roll the in-memory snapshot back before reporting a failed commit.
 */
public final class ModerationManager implements PersistentStore, PlayerStateCleanup {

    public record MuteEntry(long untilMillis, String reason, String byName) {
        public boolean isPermanent() {
            return untilMillis <= 0L;
        }

        public boolean isExpired() {
            return !isPermanent() && System.currentTimeMillis() >= untilMillis;
        }
    }

    public record KnownPlayer(UUID id, String name) {
    }

    public record OperationResult<T>(T value, Throwable failure) {
        public boolean successful() {
            return failure == null;
        }
    }

    public enum PrivateMessageStatus {
        DELIVERED,
        BLOCKED_MUTED,
        BLOCKED_SPAM,
        BLOCKED_FILTER
    }

    public record PrivateMessageDecision(PrivateMessageStatus status, String message, MuteEntry mute) {
    }

    private enum FilterMode { CENSOR, BLOCK }

    private record RuntimeConfig(boolean filterEnabled, FilterMode filterMode, List<String> filterWords,
                                 boolean spamEnabled, long minIntervalMillis,
                                 long duplicateWindowMillis, boolean chatLogEnabled,
                                 List<Long> escalationMinutes) {
    }

    private record StateSnapshot(PunishmentLedger ledger, Set<UUID> socialSpy, Set<UUID> vanished,
                                 Map<UUID, LastKnownLocation> lastLocations,
                                 Map<String, KnownPlayer> knownPlayers) {
    }

    private static final int SCHEMA_VERSION = 1;
    private static final List<String> DEFAULT_FILTER_WORDS = List.of("példaszó1", "példaszó2");
    private static final List<Long> DEFAULT_ESCALATION_MINUTES = List.of(5L, 30L, 180L, 1440L);
    private static final long CHAT_LOG_MAX_BYTES = 5L * 1024L * 1024L;
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final File logDir;
    private final File chatLogFile;
    private final File auditLogFile;
    private final Object stateLock = new Object();
    private final Object mutationLifecycleLock = new Object();
    private int inFlightMutations;
    private boolean acceptingMutations = true;

    private PunishmentLedger ledger = new PunishmentLedger();
    private Set<UUID> socialSpy = new HashSet<>();
    private Set<UUID> vanished = new HashSet<>();
    private Map<UUID, LastKnownLocation> lastLocations = new HashMap<>();
    private Map<String, KnownPlayer> knownPlayers = new HashMap<>();
    private volatile RuntimeConfig runtimeConfig = new RuntimeConfig(true, FilterMode.CENSOR,
            DEFAULT_FILTER_WORDS, true, 1_500L, 20_000L, true, DEFAULT_ESCALATION_MINUTES);

    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();

    public ModerationManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "moderation-data.yml");
        this.logDir = new File(plugin.getDataFolder(), "logs");
        this.chatLogFile = new File(logDir, "chat-moderation.log");
        this.auditLogFile = new File(logDir, "moderation-audit.log");
        plugin.getDataFolder().mkdirs();
        YamlStore.registerCriticalWrite(storageFile);
    }

    @Override
    public void load() {
        reloadConfiguration();
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        try {
            if (storageFile.isFile() && yaml.getKeys(false).isEmpty()) {
                throw new IllegalArgumentException("existing authoritative file is empty");
            }
            final StateSnapshot loaded = decode(yaml);
            synchronized (stateLock) {
                restoreLocked(loaded);
                ledger.expireDue(System.currentTimeMillis());
            }
            plugin.getLogger().info("Moderation ledger loaded: " + punishmentCount()
                    + " records, " + activePunishments().size() + " active restrictions.");
        } catch (final RuntimeException invalid) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "A moderációs state szemantikailag érvénytelen: " + invalid.getMessage());
        }
    }

    @Override
    public void save() {
        synchronized (stateLock) {
            saveLocked();
        }
    }

    private void saveLocked() {
        final YamlConfiguration yaml = encodeLocked();
        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException failure) {
            throw new UncheckedIOException("Failed to save moderation-data.yml", failure);
        }
    }

    public void issueAsync(final PunishmentType type, final UUID targetId, final String targetName,
                           final UUID administratorId, final String administratorName,
                           final String reason, final Long durationMillis,
                           final Consumer<OperationResult<PunishmentRecord>> callback) {
        mutateAsync(() -> {
            final long now = System.currentTimeMillis();
            final Long expiry = type.isTemporary() ? checkedExpiry(now, durationMillis) : null;
            rememberKnownLocked(targetId, targetName);
            return ledger.issue(type, targetId, targetName, administratorId, administratorName,
                    reason, now, expiry);
        }, callback);
    }

    /**
     * Compatibility adapter for the existing command while the native command suite delegates to
     * the authoritative ledger. The mutation remains asynchronous and uses the same rollback path.
     */
    public void mute(final UUID targetId, final int minutes, final String reason, final String administratorName) {
        final PunishmentType type = minutes == 0 ? PunishmentType.MUTE : PunishmentType.TEMPORARY_MUTE;
        final Long duration = minutes == 0 ? null : Math.multiplyExact((long) minutes, 60_000L);
        final String targetName = findKnownNameOrUuid(targetId);
        issueAsync(type, targetId, targetName, null, administratorName, reason, duration, result -> {
            if (!result.successful()) {
                plugin.getLogger().severe("Mute commit failed for " + targetName + ": " + result.failure());
            }
        });
    }

    /** Compatibility adapter; all state still flows through the punishment ledger. */
    public void unmute(final UUID targetId) {
        final String targetName = findKnownNameOrUuid(targetId);
        revokeAsync(PunishmentType.Family.MUTE, targetId, targetName, null, "SYSTEM",
                "Némítás feloldva", result -> {
                    if (!result.successful()) {
                        plugin.getLogger().severe("Unmute commit failed for " + targetName + ": " + result.failure());
                    }
                });
    }

    private String findKnownNameOrUuid(final UUID targetId) {
        synchronized (stateLock) {
            return knownPlayers.values().stream()
                    .filter(player -> player.id().equals(targetId))
                    .map(KnownPlayer::name)
                    .findFirst()
                    .orElse(targetId.toString());
        }
    }

    public void revokeAsync(final PunishmentType.Family family, final UUID targetId,
                            final String targetName, final UUID administratorId,
                            final String administratorName, final String reason,
                            final Consumer<OperationResult<PunishmentLedger.RevocationResult>> callback) {
        mutateAsync(() -> {
            rememberKnownLocked(targetId, targetName);
            return ledger.revoke(targetId, family, administratorId, administratorName, reason,
                    System.currentTimeMillis());
        }, callback);
    }

    public void setSocialSpyAsync(final UUID playerId, final String playerName, final boolean enabled,
                                  final Consumer<OperationResult<Boolean>> callback) {
        mutateAsync(() -> {
            rememberKnownLocked(playerId, playerName);
            if (enabled) {
                socialSpy.add(playerId);
            } else {
                socialSpy.remove(playerId);
            }
            return enabled;
        }, callback);
    }

    public void setVanishedAsync(final UUID playerId, final String playerName, final boolean enabled,
                                 final Consumer<OperationResult<Boolean>> callback) {
        mutateAsync(() -> {
            rememberKnownLocked(playerId, playerName);
            if (enabled) {
                vanished.add(playerId);
            } else {
                vanished.remove(playerId);
            }
            return enabled;
        }, callback);
    }

    public void recordLastLocationAsync(final UUID playerId, final String playerName, final Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        final LastKnownLocation snapshot = new LastKnownLocation(playerId, playerName,
                location.getWorld().getUID(), location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch(), System.currentTimeMillis());
        mutateAsync(() -> {
            lastLocations.put(playerId, snapshot);
            rememberKnownLocked(playerId, playerName);
            return snapshot;
        }, result -> {
            if (!result.successful()) {
                plugin.getLogger().severe("Last-location commit failed for " + playerName + ": "
                        + result.failure());
            }
        });
    }

    public void expireDueAsync() {
        mutateAsync(() -> ledger.expireDue(System.currentTimeMillis()), result -> {
            if (!result.successful()) {
                plugin.getLogger().severe("Punishment expiry commit failed: " + result.failure());
            }
        }, changed -> changed != null && changed > 0);
    }

    private <T> void mutateAsync(final StateMutation<T> mutation,
                                 final Consumer<OperationResult<T>> callback) {
        mutateAsync(mutation, callback, ignored -> true);
    }

    private <T> void mutateAsync(final StateMutation<T> mutation,
                                 final Consumer<OperationResult<T>> callback,
                                 final java.util.function.Predicate<T> shouldPersist) {
        if (!reserveMutation()) {
            if (callback != null) {
                callback.accept(new OperationResult<>(null,
                        new IllegalStateException("moderation store is shutting down")));
            }
            return;
        }
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    OperationResult<T> result;
                    synchronized (stateLock) {
                        final StateSnapshot before = snapshotLocked();
                        try {
                            final T value = mutation.apply();
                            if (shouldPersist.test(value)) {
                                saveLocked();
                            }
                            result = new OperationResult<>(value, null);
                        } catch (final CriticalPersistenceWriteError failure) {
                            restoreLocked(before);
                            result = new OperationResult<>(null, failure);
                            tripPersistenceCircuit(failure);
                        } catch (final RuntimeException failure) {
                            restoreLocked(before);
                            result = new OperationResult<>(null, failure);
                        }
                    }
                    if (callback != null) {
                        callback.accept(result);
                    }
                } finally {
                    releaseMutation();
                }
            });
        } catch (final RuntimeException schedulingFailure) {
            releaseMutation();
            if (callback != null) {
                callback.accept(new OperationResult<>(null, schedulingFailure));
            }
        }
    }

    private boolean reserveMutation() {
        synchronized (mutationLifecycleLock) {
            if (!acceptingMutations) {
                return false;
            }
            inFlightMutations++;
            return true;
        }
    }

    private void releaseMutation() {
        synchronized (mutationLifecycleLock) {
            inFlightMutations--;
            if (inFlightMutations < 0) {
                throw new IllegalStateException("moderation mutation reservation underflow");
            }
            if (inFlightMutations == 0) {
                mutationLifecycleLock.notifyAll();
            }
        }
    }

    /** Closes the mutation gate and waits for already-reserved durable transactions before final save. */
    public boolean prepareShutdown(final long timeoutMillis) {
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException("timeoutMillis must be non-negative");
        }
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (mutationLifecycleLock) {
            acceptingMutations = false;
            while (inFlightMutations > 0) {
                final long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return false;
                }
                try {
                    java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(mutationLifecycleLock, remainingNanos);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    private void tripPersistenceCircuit(final Throwable failure) {
        plugin.getLogger().severe("A moderációs autoritatív mentés meghiúsult; a plugin leáll: " + failure);
        Bukkit.getGlobalRegionScheduler().run(plugin,
                task -> Bukkit.getPluginManager().disablePlugin(plugin));
    }

    private static Long checkedExpiry(final long now, final Long durationMillis) {
        if (durationMillis == null || durationMillis <= 0L) {
            throw new IllegalArgumentException("temporary punishment duration must be positive");
        }
        try {
            return Math.addExact(now, durationMillis);
        } catch (final ArithmeticException overflow) {
            throw new IllegalArgumentException("punishment duration overflow", overflow);
        }
    }

    public Optional<PunishmentRecord> activeMute(final UUID playerId) {
        synchronized (stateLock) {
            return ledger.active(playerId, PunishmentType.Family.MUTE, System.currentTimeMillis());
        }
    }

    public Optional<PunishmentRecord> activeBan(final UUID playerId) {
        synchronized (stateLock) {
            return ledger.active(playerId, PunishmentType.Family.BAN, System.currentTimeMillis());
        }
    }

    public List<PunishmentRecord> activePunishments() {
        synchronized (stateLock) {
            return ledger.activeAll(System.currentTimeMillis());
        }
    }

    public List<PunishmentRecord> history(final UUID playerId) {
        synchronized (stateLock) {
            return ledger.history(playerId);
        }
    }

    public int punishmentCount() {
        synchronized (stateLock) {
            return ledger.snapshot().size();
        }
    }

    public int muteHistoryCount(final UUID playerId) {
        synchronized (stateLock) {
            return Math.toIntExact(Math.min(Integer.MAX_VALUE,
                    ledger.countIssued(playerId, PunishmentType.Family.MUTE)));
        }
    }

    public long escalationMinutes(final UUID playerId) {
        final List<Long> steps = runtimeConfig.escalationMinutes();
        return steps.get(Math.min(muteHistoryCount(playerId), steps.size() - 1));
    }

    /** Rebuilds the immutable, validated chat/moderation config snapshot after ConfigManager reload. */
    public void reloadConfiguration() {
        final String rawMode = configManager.getString("moderation.chat-filter.mode", "CENSOR").trim();
        final FilterMode mode;
        try {
            mode = FilterMode.valueOf(rawMode.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            plugin.getLogger().warning("moderation.chat-filter.mode ismeretlen: '" + rawMode
                    + "' — biztonságos BLOCK mód lép életbe.");
            runtimeConfig = fallbackRuntimeConfig(FilterMode.BLOCK);
            return;
        }

        final List<String> words = new ArrayList<>();
        boolean wordsValid = true;
        for (final String raw : configManager.getStringList("moderation.chat-filter.words")) {
            final String word = raw == null ? "" : raw.trim();
            if (word.isEmpty() || word.length() > 64 || words.size() >= 256) {
                wordsValid = false;
                break;
            }
            words.add(word);
        }
        if (!wordsValid) {
            plugin.getLogger().warning("moderation.chat-filter.words hibás/üres vagy túl nagy; a szűrő "
                    + "biztonságos BLOCK móddal, üres tiltólistával indul.");
            runtimeConfig = fallbackRuntimeConfig(FilterMode.BLOCK);
            return;
        }

        final long minInterval = configManager.getLong("moderation.spam.min-interval-millis", 1_500L);
        final long duplicateSeconds = configManager.getLong("moderation.spam.duplicate-window-seconds", 20L);
        if (minInterval < 0L || minInterval > 60_000L || duplicateSeconds < 0L || duplicateSeconds > 86_400L) {
            plugin.getLogger().warning("A moderation.spam időértékei kívül esnek a biztonságos tartományon; "
                    + "az alapértékek lépnek életbe.");
            runtimeConfig = fallbackRuntimeConfig(mode);
            return;
        }

        final List<Long> escalation = parseEscalationStrict();
        runtimeConfig = new RuntimeConfig(
                configManager.getBoolean("moderation.chat-filter.enabled", true), mode, List.copyOf(words),
                configManager.getBoolean("moderation.spam.enabled", true), minInterval,
                Math.multiplyExact(duplicateSeconds, 1_000L),
                configManager.getBoolean("moderation.chat-log.enabled", true), escalation);
    }

    private RuntimeConfig fallbackRuntimeConfig(final FilterMode mode) {
        return new RuntimeConfig(configManager.getBoolean("moderation.chat-filter.enabled", true), mode,
                DEFAULT_FILTER_WORDS, configManager.getBoolean("moderation.spam.enabled", true), 1_500L, 20_000L,
                configManager.getBoolean("moderation.chat-log.enabled", true), DEFAULT_ESCALATION_MINUTES);
    }

    private List<Long> parseEscalationStrict() {
        final List<String> raw = configManager.getStringList("moderation.escalation-minutes");
        if (raw.isEmpty()) {
            return DEFAULT_ESCALATION_MINUTES;
        }
        final List<Long> parsed = new ArrayList<>(raw.size());
        try {
            for (final String value : raw) {
                final long step = Long.parseLong(value.trim());
                if (step <= 0L || step > 525_600L) {
                    throw new IllegalArgumentException("out of range");
                }
                parsed.add(step);
            }
        } catch (final RuntimeException invalid) {
            plugin.getLogger().warning("moderation.escalation-minutes hibás; a teljes lista elutasítva, "
                    + "az alapértékek lépnek életbe.");
            return DEFAULT_ESCALATION_MINUTES;
        }
        return List.copyOf(parsed);
    }

    public boolean isMuted(final UUID playerId) {
        return activeMute(playerId).isPresent();
    }

    public MuteEntry muteInfo(final UUID playerId) {
        return activeMute(playerId).map(record -> new MuteEntry(
                record.expiresAtMillis() == null ? 0L : record.expiresAtMillis(),
                record.reason(), record.administratorName())).orElse(null);
    }

    public Map<UUID, MuteEntry> listMutes() {
        final Map<UUID, MuteEntry> snapshot = new TreeMap<>();
        for (final PunishmentRecord record : activePunishments()) {
            if (record.type().family() == PunishmentType.Family.MUTE) {
                snapshot.put(record.targetId(), new MuteEntry(
                        record.expiresAtMillis() == null ? 0L : record.expiresAtMillis(),
                        record.reason(), record.administratorName()));
            }
        }
        return snapshot;
    }

    public boolean isSocialSpyEnabled(final UUID playerId) {
        synchronized (stateLock) {
            return socialSpy.contains(playerId);
        }
    }

    public Set<UUID> socialSpyRecipients() {
        synchronized (stateLock) {
            return Set.copyOf(socialSpy);
        }
    }

    public boolean isVanished(final UUID playerId) {
        synchronized (stateLock) {
            return vanished.contains(playerId);
        }
    }

    public Set<UUID> vanishedPlayers() {
        synchronized (stateLock) {
            return Set.copyOf(vanished);
        }
    }

    public Optional<LastKnownLocation> lastKnownLocation(final UUID playerId) {
        synchronized (stateLock) {
            return Optional.ofNullable(lastLocations.get(playerId));
        }
    }

    public Optional<KnownPlayer> findKnownPlayer(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        synchronized (stateLock) {
            return Optional.ofNullable(knownPlayers.get(name.toLowerCase(Locale.ROOT)));
        }
    }

    public void rememberOnlinePlayer(final UUID playerId, final String playerName) {
        synchronized (stateLock) {
            rememberKnownLocked(playerId, playerName);
        }
    }

    public void setReplyPartners(final UUID first, final UUID second) {
        replyTargets.put(first, second);
        replyTargets.put(second, first);
    }

    public Optional<UUID> replyTarget(final UUID playerId) {
        return Optional.ofNullable(replyTargets.get(playerId));
    }

    public PrivateMessageDecision evaluatePrivateMessage(final UUID senderId, final String rawMessage) {
        final MuteEntry mute = muteInfo(senderId);
        if (mute != null) {
            return new PrivateMessageDecision(PrivateMessageStatus.BLOCKED_MUTED, rawMessage, mute);
        }
        if (isSpam(senderId, rawMessage)) {
            return new PrivateMessageDecision(PrivateMessageStatus.BLOCKED_SPAM, rawMessage, null);
        }
        final String filtered = filter(rawMessage);
        if (filtered == null) {
            return new PrivateMessageDecision(PrivateMessageStatus.BLOCKED_FILTER, rawMessage, null);
        }
        return new PrivateMessageDecision(PrivateMessageStatus.DELIVERED, filtered, null);
    }

    public String filter(final String message) {
        final RuntimeConfig config = runtimeConfig;
        if (message == null || message.isEmpty() || !config.filterEnabled()) {
            return message;
        }
        final List<String> words = config.filterWords();
        if (words.isEmpty()) {
            return message;
        }
        final String lower = message.toLowerCase(Locale.ROOT);
        final List<String> hits = new ArrayList<>();
        for (final String word : words) {
            if (word != null && !word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT))) {
                hits.add(word);
            }
        }
        if (hits.isEmpty()) {
            return message;
        }
        if (config.filterMode() == FilterMode.BLOCK) {
            return null;
        }
        String result = message;
        for (final String word : hits) {
            result = replaceCaseInsensitive(result, word);
        }
        return result;
    }

    private static String replaceCaseInsensitive(final String text, final String word) {
        final String lowerText = text.toLowerCase(Locale.ROOT);
        final String lowerWord = word.toLowerCase(Locale.ROOT);
        final StringBuilder out = new StringBuilder(text.length());
        final String stars = "*".repeat(word.length());
        int cursor = 0;
        while (cursor < text.length()) {
            final int index = lowerText.indexOf(lowerWord, cursor);
            if (index < 0) {
                out.append(text, cursor, text.length());
                break;
            }
            out.append(text, cursor, index).append(stars);
            cursor = index + word.length();
        }
        return out.toString();
    }

    public boolean isSpam(final UUID playerId, final String message) {
        final RuntimeConfig config = runtimeConfig;
        if (!config.spamEnabled()) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final long minInterval = config.minIntervalMillis();
        final long duplicateWindow = config.duplicateWindowMillis();
        final Long previousAt = lastMessageAt.get(playerId);
        final boolean tooFast = previousAt != null && now - previousAt < minInterval;
        final String previous = lastMessage.get(playerId);
        final boolean duplicate = !tooFast && previous != null && previousAt != null
                && now - previousAt < duplicateWindow && previous.equalsIgnoreCase(message);
        if (tooFast || duplicate) {
            return true;
        }
        lastMessageAt.put(playerId, now);
        lastMessage.put(playerId, message);
        return false;
    }

    public void logChatEvent(final String type, final Player player, final String originalMessage) {
        final UUID playerId = player.getUniqueId();
        final String playerName = player.getName();
        logChatEvent(type, playerId, playerName, originalMessage);
    }

    public void logChatEvent(final String type, final UUID playerId, final String playerName,
                             final String originalMessage) {
        if (!runtimeConfig.chatLogEnabled()) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin,
                task -> appendChatLog(type, playerId, playerName, originalMessage));
    }

    public void logInventoryEditAsync(final UUID administratorId, final String administratorName,
                                      final UUID targetId, final String targetName,
                                      final String view, final int slot,
                                      final org.bukkit.inventory.ItemStack inserted,
                                      final org.bukkit.inventory.ItemStack displaced) {
        final String detail = "admin=" + administratorName + "(" + administratorId + ") target="
                + targetName + "(" + targetId + ") view=" + view + " slot=" + slot
                + " inserted=" + describeItem(inserted) + " displaced=" + describeItem(displaced);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> appendAuditLog("INVENTORY_EDIT", detail));
    }

    private static String describeItem(final org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "AIR";
        }
        return item.getType().name() + "x" + item.getAmount();
    }

    private synchronized void appendAuditLog(final String type, final String detail) {
        try {
            logDir.mkdirs();
            final String line = "[" + LOG_TIMESTAMP.format(LocalDateTime.now()) + "] " + type + " "
                    + detail + System.lineSeparator();
            Files.writeString(auditLogFile.toPath(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException failure) {
            plugin.getLogger().warning("Failed to write moderation-audit.log: " + failure.getMessage());
        }
    }

    private synchronized void appendChatLog(final String type, final UUID playerId,
                                            final String playerName, final String originalMessage) {
        try {
            logDir.mkdirs();
            rotateChatLogIfNeeded();
            final String line = "[" + LOG_TIMESTAMP.format(LocalDateTime.now()) + "] " + type + " "
                    + playerName + " (" + playerId + "): " + originalMessage + System.lineSeparator();
            Files.writeString(chatLogFile.toPath(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException failure) {
            plugin.getLogger().warning("Failed to write chat-moderation.log: " + failure.getMessage());
        }
    }

    private void rotateChatLogIfNeeded() throws IOException {
        if (chatLogFile.exists() && chatLogFile.length() > CHAT_LOG_MAX_BYTES) {
            final File rotated = new File(logDir, "chat-moderation.log.1");
            Files.deleteIfExists(rotated.toPath());
            Files.move(chatLogFile.toPath(), rotated.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String formatRemaining(final long untilMillis) {
        final long totalSeconds = Math.max(0L, untilMillis - System.currentTimeMillis()) / 1000L;
        final long days = totalSeconds / 86_400L;
        final long hours = (totalSeconds % 86_400L) / 3600L;
        final long minutes = (totalSeconds % 3600L) / 60L;
        final long seconds = totalSeconds % 60L;
        if (days > 0L) {
            return days + " nap " + hours + " óra";
        }
        if (hours > 0L) {
            return hours + " óra " + minutes + " perc";
        }
        if (minutes > 0L) {
            return minutes + " perc " + seconds + " mp";
        }
        return seconds + " mp";
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        lastMessageAt.remove(playerId);
        lastMessage.remove(playerId);
        replyTargets.remove(playerId);
        replyTargets.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
    }

    private YamlConfiguration encodeLocked() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        for (final PunishmentRecord record : ledger.snapshot()) {
            final String path = "punishments." + record.id();
            yaml.set(path + ".type", record.type().name());
            yaml.set(path + ".target-uuid", record.targetId().toString());
            yaml.set(path + ".target-name", record.targetName());
            yaml.set(path + ".administrator-uuid",
                    record.administratorId() == null ? null : record.administratorId().toString());
            yaml.set(path + ".administrator-name", record.administratorName());
            yaml.set(path + ".reason", record.reason());
            yaml.set(path + ".created-at", record.createdAtMillis());
            yaml.set(path + ".expires-at", record.expiresAtMillis());
            yaml.set(path + ".state", record.state().name());
            yaml.set(path + ".revoked-by-uuid",
                    record.revokedById() == null ? null : record.revokedById().toString());
            yaml.set(path + ".revoked-by-name", record.revokedByName());
            yaml.set(path + ".revoked-at", record.revokedAtMillis());
            yaml.set(path + ".revocation-reason", record.revocationReason());
            yaml.set(path + ".automatic-expiration", record.automaticExpiration());
            yaml.set(path + ".linked-punishment-uuid",
                    record.linkedPunishmentId() == null ? null : record.linkedPunishmentId().toString());
        }
        yaml.set("social-spy", socialSpy.stream().map(UUID::toString).sorted().toList());
        yaml.set("vanished", vanished.stream().map(UUID::toString).sorted().toList());
        for (final LastKnownLocation location : lastLocations.values().stream()
                .sorted(Comparator.comparing(entry -> entry.playerId().toString())).toList()) {
            final String path = "last-locations." + location.playerId();
            yaml.set(path + ".player-name", location.playerName());
            yaml.set(path + ".world-uuid", location.worldId().toString());
            yaml.set(path + ".world-name", location.worldName());
            yaml.set(path + ".x", location.x());
            yaml.set(path + ".y", location.y());
            yaml.set(path + ".z", location.z());
            yaml.set(path + ".yaw", location.yaw());
            yaml.set(path + ".pitch", location.pitch());
            yaml.set(path + ".captured-at", location.capturedAtMillis());
        }
        return yaml;
    }

    private StateSnapshot decode(final YamlConfiguration yaml) {
        if (yaml.getKeys(false).isEmpty()) {
            return emptySnapshot();
        }
        final Object schema = yaml.get("schema-version");
        if (!(schema instanceof Number number) || number.intValue() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported or missing schema-version");
        }
        final List<PunishmentRecord> records = new ArrayList<>();
        final ConfigurationSection punishments = yaml.getConfigurationSection("punishments");
        if (punishments != null) {
            for (final String idRaw : punishments.getKeys(false)) {
                final ConfigurationSection section = requireSection(punishments, idRaw);
                final UUID id = parseUuid(idRaw, "punishment id");
                final PunishmentType type = parseEnum(PunishmentType.class,
                        requireString(section, "type"), "punishment type");
                final UUID targetId = parseUuid(requireString(section, "target-uuid"), "target uuid");
                final String targetName = requireString(section, "target-name");
                final UUID administratorId = optionalUuid(section, "administrator-uuid");
                final String administratorName = requireString(section, "administrator-name");
                final String reason = optionalString(section, "reason", "");
                final long createdAt = requireLong(section, "created-at");
                final Long expiresAt = optionalLong(section, "expires-at");
                final PunishmentState state = parseEnum(PunishmentState.class,
                        requireString(section, "state"), "punishment state");
                final UUID revokedById = optionalUuid(section, "revoked-by-uuid");
                final String revokedByName = nullableString(section, "revoked-by-name");
                final Long revokedAt = optionalLong(section, "revoked-at");
                final String revocationReason = optionalString(section, "revocation-reason", "");
                final boolean automatic = optionalBoolean(section, "automatic-expiration", false);
                final UUID linked = optionalUuid(section, "linked-punishment-uuid");
                records.add(new PunishmentRecord(id, type, targetId, targetName, administratorId,
                        administratorName, reason, createdAt, expiresAt, state, revokedById,
                        revokedByName, revokedAt, revocationReason, automatic, linked));
            }
        }
        final Set<UUID> loadedSpy = parseUuidList(yaml, "social-spy");
        final Set<UUID> loadedVanish = parseUuidList(yaml, "vanished");
        final Map<UUID, LastKnownLocation> loadedLocations = new HashMap<>();
        final ConfigurationSection locations = yaml.getConfigurationSection("last-locations");
        if (locations != null) {
            for (final String idRaw : locations.getKeys(false)) {
                final UUID playerId = parseUuid(idRaw, "last-location player uuid");
                final ConfigurationSection section = requireSection(locations, idRaw);
                loadedLocations.put(playerId, new LastKnownLocation(playerId,
                        requireString(section, "player-name"),
                        parseUuid(requireString(section, "world-uuid"), "world uuid"),
                        requireString(section, "world-name"), requireFiniteDouble(section, "x"),
                        requireFiniteDouble(section, "y"), requireFiniteDouble(section, "z"),
                        (float) requireFiniteDouble(section, "yaw"),
                        (float) requireFiniteDouble(section, "pitch"),
                        requireLong(section, "captured-at")));
            }
        }
        final PunishmentLedger loadedLedger = new PunishmentLedger(records);
        final Map<String, KnownPlayer> loadedKnown = new HashMap<>();
        for (final PunishmentRecord record : records) {
            loadedKnown.put(record.targetName().toLowerCase(Locale.ROOT),
                    new KnownPlayer(record.targetId(), record.targetName()));
        }
        for (final LastKnownLocation location : loadedLocations.values()) {
            loadedKnown.put(location.playerName().toLowerCase(Locale.ROOT),
                    new KnownPlayer(location.playerId(), location.playerName()));
        }
        return new StateSnapshot(loadedLedger, loadedSpy, loadedVanish, loadedLocations, loadedKnown);
    }

    private StateSnapshot snapshotLocked() {
        return new StateSnapshot(ledger.copy(), new HashSet<>(socialSpy), new HashSet<>(vanished),
                new HashMap<>(lastLocations), new HashMap<>(knownPlayers));
    }

    private void restoreLocked(final StateSnapshot snapshot) {
        ledger = snapshot.ledger().copy();
        socialSpy = new HashSet<>(snapshot.socialSpy());
        vanished = new HashSet<>(snapshot.vanished());
        lastLocations = new HashMap<>(snapshot.lastLocations());
        knownPlayers = new HashMap<>(snapshot.knownPlayers());
    }

    private static StateSnapshot emptySnapshot() {
        return new StateSnapshot(new PunishmentLedger(), new HashSet<>(), new HashSet<>(),
                new HashMap<>(), new HashMap<>());
    }

    private void rememberKnownLocked(final UUID id, final String name) {
        if (id != null && name != null && !name.isBlank()) {
            knownPlayers.put(name.trim().toLowerCase(Locale.ROOT), new KnownPlayer(id, name.trim()));
        }
    }

    private static ConfigurationSection requireSection(final ConfigurationSection parent, final String key) {
        final ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("expected section at " + parent.getCurrentPath() + "." + key);
        }
        return section;
    }

    private static String requireString(final ConfigurationSection section, final String key) {
        final Object value = section.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("missing string: " + section.getCurrentPath() + "." + key);
        }
        return string;
    }

    private static String optionalString(final ConfigurationSection section, final String key,
                                         final String fallback) {
        final Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("expected string: " + section.getCurrentPath() + "." + key);
        }
        return string;
    }

    private static String nullableString(final ConfigurationSection section, final String key) {
        final Object value = section.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("expected nullable string: " + section.getCurrentPath() + "." + key);
        }
        return string;
    }

    private static long requireLong(final ConfigurationSection section, final String key) {
        final Object value = section.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("missing number: " + section.getCurrentPath() + "." + key);
        }
        return number.longValue();
    }

    private static Long optionalLong(final ConfigurationSection section, final String key) {
        final Object value = section.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("expected number: " + section.getCurrentPath() + "." + key);
        }
        return number.longValue();
    }

    private static double requireFiniteDouble(final ConfigurationSection section, final String key) {
        final Object value = section.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("missing number: " + section.getCurrentPath() + "." + key);
        }
        final double parsed = number.doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("non-finite number: " + section.getCurrentPath() + "." + key);
        }
        return parsed;
    }

    private static boolean optionalBoolean(final ConfigurationSection section, final String key,
                                           final boolean fallback) {
        final Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("expected boolean: " + section.getCurrentPath() + "." + key);
        }
        return bool;
    }

    private static UUID optionalUuid(final ConfigurationSection section, final String key) {
        final Object value = section.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("expected UUID string: " + section.getCurrentPath() + "." + key);
        }
        return parseUuid(string, section.getCurrentPath() + "." + key);
    }

    private static UUID parseUuid(final String raw, final String field) {
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid UUID for " + field + ": " + raw, invalid);
        }
    }

    private static <E extends Enum<E>> E parseEnum(final Class<E> type, final String raw,
                                                    final String field) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid " + field + ": " + raw, invalid);
        }
    }

    private static Set<UUID> parseUuidList(final YamlConfiguration yaml, final String path) {
        final Object value = yaml.get(path);
        if (value == null) {
            return new HashSet<>();
        }
        if (!(value instanceof Collection<?> values)) {
            throw new IllegalArgumentException(path + " must be a list");
        }
        final Set<UUID> result = new HashSet<>();
        for (final Object element : values) {
            if (!(element instanceof String raw) || !result.add(parseUuid(raw, path))) {
                throw new IllegalArgumentException(path + " contains a non-string or duplicate UUID");
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface StateMutation<T> {
        T apply();
    }
}
