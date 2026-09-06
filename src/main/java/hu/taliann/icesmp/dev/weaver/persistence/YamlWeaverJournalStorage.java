package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/** Both files use the shared fsync/atomic replacement primitive, without claiming cross-file atomicity. */
public final class YamlWeaverJournalStorage implements WeaverJournalStorage {
    private static final int MAX_BYTES = 2_000_000;
    private final File stateFile;
    private final File auditFile;
    private final WeaverJournalCodec codec;
    private final Logger logger;
    public YamlWeaverJournalStorage(final File directory, final WeaverJournalCodec codec, final Logger destination) {
        this.codec = Objects.requireNonNull(codec); Objects.requireNonNull(destination);
        stateFile = new File(directory, "world-weaver-state.yml"); auditFile = new File(directory, "world-weaver-audit.yml");
        logger = new Logger("IceSMP-internal-state", null) {
            @Override public void log(final java.util.logging.LogRecord ignored) { destination.severe("Internal developer state unavailable; recovery required."); }
        };
        YamlStore.registerCriticalWrite(stateFile); YamlStore.registerCriticalWrite(auditFile);
    }
    @Override public WeaverJournalState readState() throws Exception {
        if (!stateFile.exists()) return WeaverJournalState.empty();
        try { return codec.decodeState(read(stateFile)); }
        catch (final IllegalArgumentException invalid) { YamlStore.failCorrupt(stateFile, logger, "Invalid internal schema"); throw invalid; }
    }
    @Override public Map<String, WeaverAuditEntry> readAudit() throws Exception {
        if (!auditFile.exists()) return Map.of();
        try { return codec.decodeAudit(read(auditFile)); }
        catch (final IllegalArgumentException invalid) { YamlStore.failCorrupt(auditFile, logger, "Invalid internal schema"); throw invalid; }
    }
    @Override public void writeState(final WeaverJournalState state) throws Exception { write(stateFile, codec.encodeState(state)); }
    @Override public void writeAudit(final Map<String, WeaverAuditEntry> audit) throws Exception { write(auditFile, codec.encodeAudit(audit)); }
    private Map<String, Object> read(final File file) throws Exception {
        if (Files.size(file.toPath()) > MAX_BYTES) throw new IllegalArgumentException("Internal state byte cap");
        return WeaverJournalCodec.map(detach(YamlStore.loadTracked(file, logger), 0, new int[]{0}));
    }
    private static Object detach(final Object value, final int depth, final int[] count) {
        if (depth > 32 || ++count[0] > 250_000) throw new IllegalArgumentException("Internal state nesting cap");
        final Object detached = value instanceof ConfigurationSection section ? section.getValues(false) : value;
        if (detached instanceof Map<?, ?> map) {
            final Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("Invalid YAML key");
                result.put(text, detach(item, depth + 1, count));
            }); return Collections.unmodifiableMap(result);
        }
        if (detached instanceof List<?> list) return list.stream().map(item -> detach(item, depth + 1, count)).toList();
        if (detached instanceof String || detached instanceof Boolean || detached instanceof Integer || detached instanceof Long
                || detached instanceof Double number && Double.isFinite(number)) return detached;
        throw new IllegalArgumentException("Invalid YAML value");
    }
    private static void write(final File file, final Map<String, Object> data) throws Exception {
        final YamlConfiguration yaml = new YamlConfiguration(); data.forEach(yaml::set);
        if (yaml.saveToString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Internal state byte cap");
        YamlStore.saveAtomic(file, yaml);
    }
}
