package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Explicit NPC-binding store: detaches NPC integration from NPC-name semantics.
 * Instead of a shop/quest recognising itself by the NPC's own name (the legacy
 * {@code faction-shops.<name>} / {@code giver-npc: <name>} conventions), an admin
 * can point ANY FancyNpcs NPC at a quest, a shop, a banker, or a currency
 * exchanger with {@code /npcbind}. Bindings persist to {@code npc-bindings.yml}.
 *
 * <p>Thread-safety: {@link #bindings} is a {@link ConcurrentHashMap} so {@link #get}
 * is lock-free and safe from any region thread (the FancyNpcs interact event fires
 * on the interacting player's own region thread); every compound mutation
 * ({@link #bind}/{@link #unbind}) is {@code synchronized} so a concurrent
 * bind-then-save from the command thread never races (mirrors
 * {@link DonationChestManager}'s pattern).</p>
 *
 * <p>Works standalone — without FancyNpcs installed, binding/listing/persistence
 * all function normally; the bindings simply have no runtime effect until the
 * reflective {@link hu.taliann.icesmp.integration.FancyNpcsQuestBridge} is active.</p>
 */
public final class NpcBindingManager implements PersistentStore {

    /**
     * What an NPC is bound to. QUEST/SHOP/COMMAND carry a value (quest id / shop name / the
     * command line the interacting player runs); BANK/EXCHANGE/FACTION don't.
     */
    public enum BindingType {
        QUEST, SHOP, BANK, EXCHANGE, FACTION, COMMAND
    }

    /** A single binding: its type, and the type-specific value (empty for BANK/EXCHANGE/FACTION). */
    public record Binding(BindingType type, String value) {
    }

    private final JavaPlugin plugin;
    private final File storageFile;
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();

    public NpcBindingManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "npc-bindings.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        bindings.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
            final ConfigurationSection section = yaml.getConfigurationSection("bindings");
            if (section != null) {
                for (final String npcName : section.getKeys(false)) {
                    final String typeRaw = section.getString(npcName + ".type", "");
                    final BindingType type = parseType(typeRaw);
                    if (type == null) {
                        plugin.getLogger().warning("npc-bindings.yml: ismeretlen kötés-típus '" + typeRaw
                                + "' az NPC-nél '" + npcName + "' — kihagyva.");
                        continue;
                    }
                    final String value = section.getString(npcName + ".value", "");
                    bindings.put(npcName.toLowerCase(Locale.ROOT), new Binding(type, value == null ? "" : value));
                }
            }
            plugin.getLogger().info("Loaded " + bindings.size() + " NPC binding(s).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load npc-bindings.yml: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Binding> entry : bindings.entrySet()) {
            final String basePath = "bindings." + entry.getKey();
            yaml.set(basePath + ".type", entry.getValue().type().name());
            yaml.set(basePath + ".value", entry.getValue().value());
        }

        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save npc-bindings.yml: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save npc-bindings.yml", exception);
        }
    }

    private static BindingType parseType(final String raw) {
        try {
            return BindingType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    /**
     * Binds an NPC (by its FancyNpcs internal name) to a quest/shop/bank/exchange.
     *
     * @param npcName the NPC's internal name (case-insensitive)
     * @param type the binding type
     * @param value the type-specific value (quest id / shop name); ignored for BANK/EXCHANGE
     */
    public synchronized void bind(final String npcName, final BindingType type, final String value) {
        bindings.put(npcName.toLowerCase(Locale.ROOT), new Binding(type, value == null ? "" : value));
        save();
    }

    /**
     * Removes an NPC's binding.
     *
     * @param npcName the NPC's internal name (case-insensitive)
     * @return true if a binding existed and was removed
     */
    public synchronized boolean unbind(final String npcName) {
        final boolean removed = bindings.remove(npcName.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Looks up an NPC's binding — lock-free, safe from any region thread.
     *
     * @param npcName the NPC's internal name (case-insensitive)
     * @return the binding, or null if the NPC has none
     */
    public Binding get(final String npcName) {
        return npcName == null ? null : bindings.get(npcName.toLowerCase(Locale.ROOT));
    }

    /** Every binding, NPC-name sorted, for the {@code /npcbind list} listing — snapshot read. */
    public Map<String, Binding> describeAll() {
        return new TreeMap<>(bindings);
    }
}
