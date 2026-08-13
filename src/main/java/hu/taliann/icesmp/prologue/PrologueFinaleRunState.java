package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/** Small durable companion for the gathering participant-count scaling baseline. */
public final class PrologueFinaleRunState {
    private final JavaPlugin plugin;
    private final File file;
    private UUID finaleId;
    private int scalingBaseline;

    public PrologueFinaleRunState(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "prologue-finale.yml");
        YamlStore.registerCriticalWrite(file);
        load();
    }

    public synchronized void load() {
        finaleId = null;
        scalingBaseline = 0;
        if (!file.exists()) return;
        final YamlConfiguration yaml = YamlStore.loadTracked(file, plugin.getLogger());
        final String raw = yaml.getString("finale-id", "");
        if (!raw.isBlank()) finaleId = UUID.fromString(raw);
        scalingBaseline = Math.max(0, yaml.getInt("scaling-baseline", 0));
    }

    public synchronized int baselineFor(final UUID id) {
        return id != null && id.equals(finaleId) ? scalingBaseline : 0;
    }

    public synchronized void begin(final UUID id) {
        finaleId = id;
        scalingBaseline = 0;
        save();
    }

    public synchronized void setBaseline(final UUID id, final int value) {
        if (id == null || !id.equals(finaleId)) throw new IllegalStateException("finale-id mismatch");
        scalingBaseline = Math.max(0, value);
        save();
    }

    public synchronized void clear(final UUID id) {
        if (id != null && finaleId != null && !id.equals(finaleId)) return;
        finaleId = null;
        scalingBaseline = 0;
        save();
    }

    private void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("finale-id", finaleId == null ? "" : finaleId.toString());
            yaml.set("scaling-baseline", scalingBaseline);
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException exception) {
            throw new UncheckedIOException("prologue-finale.yml mentése sikertelen", exception);
        }
    }
}
