package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Natív moderációs modul állapota (a SModeration plugin chat-részének kiváltása, IDEAS C17):
 * némítás-tár + chat-szűrő + spam-fék. Némítások {@code moderation-data.yml}-be perzisztálnak
 * ({@link YamlStore#saveAtomic}), restart-állóak és kilépéskor NEM törlődnek — csak a spam-fék
 * volatilis (per-session) állapota takarítódik {@link #clearPlayerState}-ben.
 *
 * <p>Szálbiztonság: minden térkép {@link ConcurrentHashMap}, minden metódus lock-free vagy a
 * {@code mutes} map-en {@code synchronized} az összetett (olvasás+írás) módosításokhoz — ezt a
 * {@link ChatModerationListener} az {@code AsyncChatEvent} ASYNC szálán hívja, ahol csak
 * konkurens állapot-olvasás/írás megengedett, entitás-módosítás nem (ld. CLAUDE.md).</p>
 */
public final class ModerationManager implements PersistentStore, PlayerStateCleanup {

    /** Egy némítás bejegyzés: lejárat (0/negatív = végtelen), ok, kiadó neve. */
    public record MuteEntry(long untilMillis, String reason, String byName) {

        public boolean isPermanent() {
            return untilMillis <= 0;
        }

        public boolean isExpired() {
            return !isPermanent() && System.currentTimeMillis() >= untilMillis;
        }
    }

    /** IDEAS A52 eszkalációs alapértelmezés, ha a {@code moderation.escalation-minutes} config-lista üres/hibás. */
    private static final List<Long> DEFAULT_ESCALATION_MINUTES = List.of(5L, 30L, 180L, 1440L);
    private static final long CHAT_LOG_MAX_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final File logDir;
    private final File chatLogFile;

    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();
    /** IDEAS A52: hányszor volt korábban némítva egy játékos — az eszkalációs lépcső ez alapján dönt. */
    private final Map<UUID, Integer> muteHistory = new ConcurrentHashMap<>();

    // Spam-fék: kizárólag session-állapot (nem perzisztens) — az utolsó ENGEDÉLYEZETT üzenet
    // ideje/szövege játékosonként.
    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

    public ModerationManager(final JavaPlugin plugin, final ConfigManager configManager,
                              final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "moderation-data.yml");
        plugin.getDataFolder().mkdirs();
        this.logDir = new File(plugin.getDataFolder(), "logs");
        this.chatLogFile = new File(logDir, "chat-moderation.log");
    }

    @Override
    public void load() {
        mutes.clear();
        muteHistory.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            final ConfigurationSection section = yaml.getConfigurationSection("mutes");
            if (section != null) {
                for (final String uuidRaw : section.getKeys(false)) {
                    try {
                        final UUID playerId = UUID.fromString(uuidRaw);
                        final long until = section.getLong(uuidRaw + ".until", 0L);
                        final String reason = section.getString(uuidRaw + ".reason", "");
                        final String byName = section.getString(uuidRaw + ".by", "");
                        mutes.put(playerId, new MuteEntry(until, reason == null ? "" : reason,
                                byName == null ? "" : byName));
                    } catch (final IllegalArgumentException invalidUuid) {
                        plugin.getLogger().warning("moderation-data.yml: érvénytelen UUID kihagyva: " + uuidRaw);
                    }
                }
            }

            final ConfigurationSection historySection = yaml.getConfigurationSection("history");
            if (historySection != null) {
                for (final String uuidRaw : historySection.getKeys(false)) {
                    try {
                        final UUID playerId = UUID.fromString(uuidRaw);
                        final int count = historySection.getInt(uuidRaw, 0);
                        if (count > 0) {
                            muteHistory.put(playerId, count);
                        }
                    } catch (final IllegalArgumentException invalidUuid) {
                        plugin.getLogger().warning("moderation-data.yml: érvénytelen UUID (history) kihagyva: " + uuidRaw);
                    }
                }
            }
            plugin.getLogger().info("Loaded " + mutes.size() + " mute entry/entries.");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load moderation-data.yml: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<UUID, MuteEntry> entry : mutes.entrySet()) {
            final String basePath = "mutes." + entry.getKey();
            yaml.set(basePath + ".until", entry.getValue().untilMillis());
            yaml.set(basePath + ".reason", entry.getValue().reason());
            yaml.set(basePath + ".by", entry.getValue().byName());
        }
        for (final Map.Entry<UUID, Integer> entry : muteHistory.entrySet()) {
            yaml.set("history." + entry.getKey(), entry.getValue());
        }

        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save moderation-data.yml: " + exception.getMessage());
        }
    }

    // ===== Némítás API =====

    /**
     * Némít egy játékost.
     *
     * @param playerId a némítandó játékos
     * @param minutes hátralévő percek; 0 vagy kevesebb = végtelen
     * @param reason indoklás (lehet üres)
     * @param byName a némítást kiadó admin neve
     */
    public synchronized void mute(final UUID playerId, final int minutes, final String reason, final String byName) {
        final long until = minutes <= 0 ? 0L : System.currentTimeMillis() + minutes * 60_000L;
        mutes.put(playerId, new MuteEntry(until, reason == null ? "" : reason, byName == null ? "" : byName));
        muteHistory.merge(playerId, 1, Integer::sum);
        save();
    }

    // ===== IDEAS A52: eszkalációs lépcső =====

    /** Hányszor volt már némítva a játékos korábban (a folyamatban lévő mute-ot még NEM számolva). */
    public int muteHistoryCount(final UUID playerId) {
        return muteHistory.getOrDefault(playerId, 0);
    }

    /**
     * A következő automatikus némítás hossza percben, a némítás-történet alapján: a
     * {@code moderation.escalation-minutes} config-lépcső {@code muteHistoryCount(playerId)}.
     * indexű eleme (a listán túl az utolsó elem ismétlődik). Config hiányában/hibás bejegyzés esetén
     * az 5 → 30 → 180 → 1440 perces alapértelmezett lépcsőt használja.
     */
    public long escalationMinutes(final UUID playerId) {
        final List<Long> steps = escalationSteps();
        final int index = Math.min(muteHistoryCount(playerId), steps.size() - 1);
        return steps.get(index);
    }

    private List<Long> escalationSteps() {
        final List<String> raw = configManager.getStringList("moderation.escalation-minutes");
        if (raw.isEmpty()) {
            return DEFAULT_ESCALATION_MINUTES;
        }
        final List<Long> parsed = new ArrayList<>(raw.size());
        for (final String value : raw) {
            try {
                parsed.add(Long.parseLong(value.trim()));
            } catch (final NumberFormatException invalidStep) {
                plugin.getLogger().warning("moderation.escalation-minutes: érvénytelen érték kihagyva: " + value);
            }
        }
        return parsed.isEmpty() ? DEFAULT_ESCALATION_MINUTES : parsed;
    }

    public synchronized void unmute(final UUID playerId) {
        if (mutes.remove(playerId) != null) {
            save();
        }
    }

    /** Igaz, ha a némítás érvényes — lejárt bejegyzést lustán töröl (nem perzisztál azonnal). */
    public boolean isMuted(final UUID playerId) {
        return muteInfo(playerId) != null;
    }

    /**
     * A hátralévő némítás adatai, vagy {@code null} ha nincs (érvényes) némítás.
     * Lejárt bejegyzést lustán, a memória-térképből eltávolítja (a diszk csak a következő
     * {@link #save()}-nél frissül — ez elfogadható, {@link #load()} úgyis kiszűri újraindításkor).
     *
     * <p>A lejárat-észlelést több szál is elindíthatja egyidejűleg (ld. {@link #isMuted}/
     * {@link #muteInfo} async chat-szálú hívása) — a {@link Map#remove(Object, Object)} atomi
     * compare-and-remove, tehát legfeljebb EGY hívó kapja vissza {@code true}-t ugyanarra a
     * bejegyzésre; ez az "egyszeri jog" dönti el, ki küldi a lejárat-értesítést (A57), így az
     * sosem duplikálódik.</p>
     */
    public MuteEntry muteInfo(final UUID playerId) {
        final MuteEntry entry = mutes.get(playerId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            if (mutes.remove(playerId, entry)) {
                notifyMuteExpired(playerId);
            }
            return null;
        }
        return entry;
    }

    /**
     * A57: ha a lejárt némítást töröljük és a játékos ONLINE, értesítjük — a küldés a JÁTÉKOS
     * saját entitás-régió-szálán történik (Folia — ez a hívó szálról, akár az async chat-szálról
     * is legális hop, maga a hop thread-safe; ld. CLAUDE.md Folia-szabályok). Ha a játékos nincs
     * online, nincs teendő.
     */
    private void notifyMuteExpired(final UUID playerId) {
        final Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, task ->
                player.sendMessage(messageManager.get("moderation.mute-expired",
                        "&aA némításod lejárt — újra beszélhetsz.")), null);
    }

    /** Minden érvényes (le nem járt) némítás, UUID szerint rendezve — snapshot olvasás. */
    public Map<UUID, MuteEntry> listMutes() {
        final Map<UUID, MuteEntry> snapshot = new TreeMap<>();
        for (final Map.Entry<UUID, MuteEntry> entry : mutes.entrySet()) {
            if (!entry.getValue().isExpired()) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }
        return snapshot;
    }

    /** Emberi-olvasható hátralévő idő szöveg (pl. "12 perc 3 mp múlva jár le"). */
    public static String formatRemaining(final long untilMillis) {
        final long remainingMs = Math.max(0L, untilMillis - System.currentTimeMillis());
        final long totalSeconds = remainingMs / 1000L;
        final long minutes = totalSeconds / 60L;
        final long seconds = totalSeconds % 60L;
        return minutes > 0
                ? minutes + " perc " + seconds + " mp múlva jár le"
                : seconds + " mp múlva jár le";
    }

    // ===== Chat-szűrő =====

    /**
     * Alkalmazza a tiltott-szó szűrőt egy üzenetre.
     *
     * @param message a nyers (plain-text) üzenet
     * @return CENSOR módban a csillagozott szöveg (vagy változatlan, ha nincs találat);
     *         BLOCK módban {@code null}, ha volt találat (= az üzenetet el kell nyelni)
     */
    public String filter(final String message) {
        if (message == null || message.isEmpty()
                || !configManager.getBoolean("moderation.chat-filter.enabled", true)) {
            return message;
        }

        final List<String> words = configManager.getStringList("moderation.chat-filter.words");
        if (words.isEmpty()) {
            return message;
        }

        final String lower = message.toLowerCase(Locale.ROOT);
        List<String> hits = null;
        for (final String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                if (hits == null) {
                    hits = new ArrayList<>(2);
                }
                hits.add(word);
            }
        }
        if (hits == null) {
            return message;
        }

        final String mode = configManager.getString("moderation.chat-filter.mode", "CENSOR").toUpperCase(Locale.ROOT);
        if ("BLOCK".equals(mode)) {
            return null;
        }

        String result = message;
        for (final String word : hits) {
            result = replaceCaseInsensitive(result, word);
        }
        return result;
    }

    /** Az összes {@code word} előfordulását azonos hosszúságú '*'-sorra cseréli, kis/nagybetű-függetlenül. */
    private static String replaceCaseInsensitive(final String text, final String word) {
        final String lowerText = text.toLowerCase(Locale.ROOT);
        final String lowerWord = word.toLowerCase(Locale.ROOT);
        final StringBuilder out = new StringBuilder(text.length());
        final String stars = "*".repeat(word.length());

        int i = 0;
        while (i < text.length()) {
            final int idx = lowerText.indexOf(lowerWord, i);
            if (idx < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, idx).append(stars);
            i = idx + word.length();
        }
        return out.toString();
    }

    // ===== Spam-fék =====

    /**
     * Spam-e az üzenet: túl gyorsan érkezett az előző (engedélyezett) üzenet után, vagy
     * ugyanaz az üzenet ismétlődik a duplikátum-ablakon belül. Blokkolt üzenet NEM frissíti
     * az utolsó-üzenet állapotot — így egy gyors, ismételt küldés-sorozat mindvégig blokkolva
     * marad, nem "reseteli magát" minden próbálkozással.
     */
    public boolean isSpam(final UUID playerId, final String message) {
        if (!configManager.getBoolean("moderation.spam.enabled", true)) {
            return false;
        }

        final long now = System.currentTimeMillis();
        final long minIntervalMillis = configManager.getLong("moderation.spam.min-interval-millis", 1500L);
        final long duplicateWindowMillis = configManager.getLong("moderation.spam.duplicate-window-seconds", 20L) * 1000L;

        final Long lastAt = lastMessageAt.get(playerId);
        final boolean tooFast = lastAt != null && now - lastAt < minIntervalMillis;

        final String last = lastMessage.get(playerId);
        final boolean duplicate = !tooFast && last != null && lastAt != null
                && now - lastAt < duplicateWindowMillis && last.equalsIgnoreCase(message);

        if (tooFast || duplicate) {
            return true;
        }

        lastMessageAt.put(playerId, now);
        lastMessage.put(playerId, message);
        return false;
    }

    // ===== IDEAS A52: chat-napló =====

    /**
     * Naplózza egy blokkolt/cenzúrázott/némított üzenetet a {@code logs/chat-moderation.log}-ba
     * (időbélyeg + típus + játékos + EREDETI — nem cenzúrázott — szöveg). {@code type}: {@code MUTED},
     * {@code SPAM}, {@code CENSOR} vagy {@code BLOCK}.
     *
     * <p><b>Szálválasztás:</b> a hívó {@link hu.taliann.icesmp.listeners.ChatModerationListener} a
     * Paper ASYNC chat-szálán fut, ami — a Folia régió-szálakkal ellentétben — több szálon,
     * párhuzamosan tüzelhet több egyidejű chat-üzenetre. Egy külön {@code getAsyncScheduler().runNow}
     * hop csak plusz late­nciát adna anélkül, hogy bármilyen konkurencia-problémát megoldana (a hop
     * célja egy MÁSIK, előre nem ismert szál lenne — de itt már eleve egy tetszőleges worker-szálon
     * vagyunk, a fájlírás maga nem Bukkit-API-hívás, tehát nincs Folia-régió-tulajdon, amit be
     * kellene tartani). Az egyetlen valódi kockázat a PÁRHUZAMOS írás (két chat-üzenet egyszerre) —
     * ezt egy {@code synchronized} metódussal zárjuk ki, ugyanúgy, ahogy a {@link #mute}/{@link #save}
     * is teszi ebben az osztályban.
     *
     * @param type az esemény típusa
     * @param player az érintett játékos (csak {@code getName()}/{@code getUniqueId()} olvasva — ezek
     *               egyszerű mezőolvasások, nem PDC/inventory-érintés, tehát biztonságosak bármely
     *               szálról, ld. CLAUDE.md Folia-szabályok)
     * @param originalMessage az eredeti (nem cenzúrázott) üzenetszöveg
     */
    public synchronized void logChatEvent(final String type, final Player player, final String originalMessage) {
        if (!configManager.getBoolean("moderation.chat-log.enabled", true)) {
            return;
        }
        try {
            logDir.mkdirs();
            rotateChatLogIfNeeded();
            final String line = "[" + LOG_TIMESTAMP.format(LocalDateTime.now()) + "] " + type + " "
                    + player.getName() + " (" + player.getUniqueId() + "): " + originalMessage + System.lineSeparator();
            Files.writeString(chatLogFile.toPath(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException exception) {
            plugin.getLogger().warning("Failed to write chat-moderation.log: " + exception.getMessage());
        }
    }

    /** Méret-fék: 5 MB felett a fájlt {@code .1} utótagra forgatja, a napló üresről folytatódik. */
    private void rotateChatLogIfNeeded() throws IOException {
        if (chatLogFile.exists() && chatLogFile.length() > CHAT_LOG_MAX_BYTES) {
            final File rotated = new File(logDir, "chat-moderation.log.1");
            Files.deleteIfExists(rotated.toPath());
            Files.move(chatLogFile.toPath(), rotated.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        lastMessageAt.remove(playerId);
        lastMessage.remove(playerId);
        // A némítások SZÁNDÉKOSAN nem törlődnek kilépéskor — restart-állóak (ld. osztály-Javadoc).
    }
}
