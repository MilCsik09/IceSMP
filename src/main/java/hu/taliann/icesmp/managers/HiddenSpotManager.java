package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D8 — Felfedezhető titkos helyek (lore: a kézzel épített world-building játékmechanikai
 * súlya). Az admin config-listában rejtett pontokat jelöl ki ({@code hidden-spots.spots.<id>}:
 * location/radius/name/rewards/xp); aki ELSŐKÉNT ér oda, felfedezés-jutalmat kap és a nevét
 * broadcast hirdeti ("beírta magát a térképekbe"). A további látogatók configtól függően
 * (first-finder-only) kisebb, egyszeri jutalmat kapnak, vagy csak a hely már-felfedezett
 * üzenetét.
 *
 * <p>Teljesítmény: a proximity-check a globális tick THROTTLE-olt ütemében fut
 * (check-seconds), és per-játékos régió-hoppal CSAK a távolság-négyzetet számolja —
 * a spot-lista kicsi (admin-kézzel karbantartott). Az első-felfedező YAML-ban
 * (hidden-spots.yml), a per-játékos "már járt itt" jelölés player-PDC-ben él (nem
 * szivárgó map). Minden kulcs élőben olvasódik.
 */
public final class HiddenSpotManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final File storageFile;
    /** spot-id → első felfedező (UUID). */
    private final Map<String, UUID> discoveredBy = new ConcurrentHashMap<>();
    /** spot-id → első felfedező NÉV (a kiíráshoz; a UUID-ből nem hívunk profil-lookupot). */
    private final Map<String, String> discovererName = new ConcurrentHashMap<>();

    private volatile long nextCheckAt;

    public HiddenSpotManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "hidden-spots.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        discoveredBy.clear();
        discovererName.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection section = yaml.getConfigurationSection("discovered");
        if (section == null) {
            return;
        }
        for (final String spotId : section.getKeys(false)) {
            try {
                discoveredBy.put(spotId, UUID.fromString(section.getString(spotId + ".by", "")));
                discovererName.put(spotId, section.getString(spotId + ".name", "?"));
            } catch (final IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in hidden-spots.yml for spot '" + spotId + "'.");
            }
        }
    }

    @Override
    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final Map.Entry<String, UUID> entry : discoveredBy.entrySet()) {
                yaml.set("discovered." + entry.getKey() + ".by", entry.getValue().toString());
                yaml.set("discovered." + entry.getKey() + ".name",
                        discovererName.getOrDefault(entry.getKey(), "?"));
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save hidden-spots.yml: " + exception.getMessage());
        }
    }

    /** Throttle-olt proximity-check a globális world-events tickről. */
    public void tick() {
        if (!configManager.getBoolean("hidden-spots.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextCheckAt) {
            return;
        }
        nextCheckAt = now + Math.max(5L, configManager.getLong("hidden-spots.check-seconds", 30L)) * 1000L;
        final ConfigurationSection spots = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection("hidden-spots.spots");
        if (spots == null || spots.getKeys(false).isEmpty()) {
            return;
        }
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            // A pozíció-olvasás és a PDC a játékos SAJÁT régió-szálán (Folia).
            player.getScheduler().run(plugin, task -> checkPlayer(player, spots), null);
        }
    }

    /** A játékos szálán: minden spot távolság-négyzete (olcsó — a spot-lista kicsi). */
    private void checkPlayer(final Player player, final ConfigurationSection spots) {
        for (final String spotId : spots.getKeys(false)) {
            final ConfigurationSection spot = spots.getConfigurationSection(spotId);
            if (spot == null) {
                continue;
            }
            final String[] parts = spot.getString("location", "").split(",");
            if (parts.length < 4 || !player.getWorld().getName().equals(parts[0].trim())) {
                continue;
            }
            final double radius = Math.max(1.0D, spot.getDouble("radius", 4.0D));
            try {
                final double dx = player.getLocation().getX() - Double.parseDouble(parts[1].trim());
                final double dy = player.getLocation().getY() - Double.parseDouble(parts[2].trim());
                final double dz = player.getLocation().getZ() - Double.parseDouble(parts[3].trim());
                if (dx * dx + dy * dy + dz * dz > radius * radius) {
                    continue;
                }
            } catch (final NumberFormatException exception) {
                continue;
            }
            handleEntry(player, spotId, spot);
        }
    }

    /** A spot-ba lépés kezelése (a játékos szálán): első-felfedező vs. későbbi látogató. */
    private void handleEntry(final Player player, final String spotId, final ConfigurationSection spot) {
        final NamespacedKey visitedKey = new NamespacedKey(plugin, "hidden_spot_" + spotId.toLowerCase(java.util.Locale.ROOT));
        if (player.getPersistentDataContainer().has(visitedKey, PersistentDataType.BYTE)) {
            return; // Már járt itt — csend (nem spammelünk minden checknél).
        }
        player.getPersistentDataContainer().set(visitedKey, PersistentDataType.BYTE, (byte) 1);
        AdvancementService.award(player, "hidden_spot");

        final String name = spot.getString("name", spotId);
        final boolean first = discoveredBy.putIfAbsent(spotId, player.getUniqueId()) == null;
        if (first) {
            discovererName.put(spotId, player.getName());
            save();
            giveRewards(player, spot, 1.0D);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), org.bukkit.Particle.END_ROD,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.8D, 0.8D, 0.8D, 0.02D);
            Bukkit.getServer().broadcast(messageManager.getMessage("hidden-spot-first",
                    "<gold>🧭 {player} ELSŐKÉNT fedezte fel: <white>{name}</white> — a neve bekerül a térképekbe!</gold>",
                    Map.of("player", player.getName(), "name", name)));
            return;
        }
        if (configManager.getBoolean("hidden-spots.first-finder-only", false)) {
            player.sendActionBar(messageManager.getMessage("hidden-spot-already",
                    "<gray>🧭 {name} — {finder} fedezte fel elsőként.</gray>",
                    Map.of("name", name, "finder", discovererName.getOrDefault(spotId, "valaki"))));
            return;
        }
        // Későbbi látogató: kisebb (fél-értékű) jutalom, egyszer.
        giveRewards(player, spot, Math.max(0.0D, Math.min(1.0D,
                configManager.getDouble("hidden-spots.repeat-reward-ratio", 0.5D))));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.3F);
        player.sendMessage(messageManager.getMessage("hidden-spot-found",
                "<aqua>🧭 Felfedezted: <white>{name}</white> <gray>(elsőként {finder} járt itt)</gray></aqua>",
                Map.of("name", name, "finder", discovererName.getOrDefault(spotId, "valaki"))));
    }

    /** Jutalom-kiosztás (a játékos szálán): item-sorok (LootTable-formátum) + vanília-XP. */
    private void giveRewards(final Player player, final ConfigurationSection spot, final double ratio) {
        if (ratio <= 0.0D) {
            return;
        }
        for (final String entry : spot.getStringList("rewards")) {
            final ItemStack stack = LootTable.parseEntry(entry);
            if (stack != null) {
                stack.setAmount(Math.max(1, (int) Math.round(stack.getAmount() * ratio)));
                player.getInventory().addItem(stack).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }
        final int xp = (int) Math.round(Math.max(0, spot.getInt("xp", 20)) * ratio);
        if (xp > 0) {
            player.giveExp(xp);
        }
    }
}
