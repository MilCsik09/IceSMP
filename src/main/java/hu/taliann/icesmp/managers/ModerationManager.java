package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;

    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();

    // Spam-fék: kizárólag session-állapot (nem perzisztens) — az utolsó ENGEDÉLYEZETT üzenet
    // ideje/szövege játékosonként.
    private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

    public ModerationManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "moderation-data.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        mutes.clear();

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
        save();
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
     */
    public MuteEntry muteInfo(final UUID playerId) {
        final MuteEntry entry = mutes.get(playerId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            mutes.remove(playerId, entry);
            return null;
        }
        return entry;
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

    @Override
    public void clearPlayerState(final UUID playerId) {
        lastMessageAt.remove(playerId);
        lastMessage.remove(playerId);
        // A némítások SZÁNDÉKOSAN nem törlődnek kilépéskor — restart-állóak (ld. osztály-Javadoc).
    }
}
