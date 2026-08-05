package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hidden-spot world aggregate plus PlayerProfile-backed per-player discovery state.
 * The global first discoverer is shared world state and intentionally remains separate.
 */
public final class HiddenSpotManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final PlayerProfileAchievementStore achievementStore =
            new PlayerProfileAchievementStore();
    /** spot-id → first discoverer (shared world aggregate). */
    private final Map<String, UUID> discoveredBy = new ConcurrentHashMap<>();
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

    @Override public void load() {
        discoveredBy.clear();
        discovererName.clear();
        if (!storageFile.exists()) return;
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection section = yaml.getConfigurationSection("discovered");
        if (section == null) return;
        for (final String spotId : section.getKeys(false)) {
            try {
                discoveredBy.put(spotId,
                        UUID.fromString(section.getString(spotId + ".by", "")));
                discovererName.put(spotId,
                        section.getString(spotId + ".name", "?"));
            } catch (final IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in hidden-spots.yml for spot '"
                        + spotId + "'.");
            }
        }
    }

    @Override public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final Map.Entry<String, UUID> entry : discoveredBy.entrySet()) {
                yaml.set("discovered." + entry.getKey() + ".by",
                        entry.getValue().toString());
                yaml.set("discovered." + entry.getKey() + ".name",
                        discovererName.getOrDefault(entry.getKey(), "?"));
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save hidden-spots.yml: "
                    + exception.getMessage());
            throw new java.io.UncheckedIOException(
                    "Failed to save hidden-spots.yml", exception);
        }
    }

    public void tick() {
        if (!configManager.getBoolean("hidden-spots.enabled", true)) return;
        final long now = System.currentTimeMillis();
        if (now < nextCheckAt) return;
        nextCheckAt = now + Math.max(5L,
                configManager.getLong("hidden-spots.check-seconds", 30L)) * 1000L;
        final ConfigurationSection spots = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration()
                        .getConfigurationSection("hidden-spots.spots");
        if (spots == null || spots.getKeys(false).isEmpty()) return;
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> checkPlayer(player, spots), null);
        }
    }

    private void checkPlayer(final Player player, final ConfigurationSection spots) {
        for (final String spotId : spots.getKeys(false)) {
            final ConfigurationSection spot = spots.getConfigurationSection(spotId);
            if (spot == null) continue;
            final String[] parts = spot.getString("location", "").split(",");
            if (parts.length < 4
                    || !player.getWorld().getName().equals(parts[0].trim())) continue;
            final double radius = Math.max(1.0D, spot.getDouble("radius", 4.0D));
            try {
                final double dx = player.getLocation().getX()
                        - Double.parseDouble(parts[1].trim());
                final double dy = player.getLocation().getY()
                        - Double.parseDouble(parts[2].trim());
                final double dz = player.getLocation().getZ()
                        - Double.parseDouble(parts[3].trim());
                if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
            } catch (final NumberFormatException exception) {
                continue;
            }
            handleEntry(player, spotId, spot);
        }
    }

    private void handleEntry(final Player player, final String spotId,
                             final ConfigurationSection spot) {
        if (achievementStore.hasVisitedHiddenSpot(player.getUniqueId(), spotId)) return;
        achievementStore.markHiddenSpotVisited(player.getUniqueId(), spotId)
                .whenComplete((created, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("PlayerProfile hidden-spot commit failed for "
                                + player.getUniqueId() + '/' + spotId + ": "
                                + failure.getMessage());
                        return;
                    }
                    if (!Boolean.TRUE.equals(created)) return;
                    player.getScheduler().run(plugin,
                            task -> deliverEntry(player, spotId, spot), null);
                });
    }

    private void deliverEntry(final Player player, final String spotId,
                              final ConfigurationSection spot) {
        if (!player.isOnline()) {
            plugin.getLogger().warning("Hidden-spot visit committed while player went offline: "
                    + player.getUniqueId() + '/' + spotId);
            return;
        }
        AdvancementService.award(player, "hidden_spot");
        final String name = spot.getString("name", spotId);
        final boolean first = discoveredBy.putIfAbsent(spotId,
                player.getUniqueId()) == null;
        if (first) {
            discovererName.put(spotId, player.getName());
            save();
            giveRewards(player, spot, 1.0D);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(),
                    org.bukkit.Particle.END_ROD,
                    player.getLocation().add(0.0D, 1.0D, 0.0D),
                    24, 0.8D, 0.8D, 0.8D, 0.02D);
            Bukkit.getServer().broadcast(messageManager.getMessage("hidden-spot-first",
                    "<gold>🧭 {player} ELSŐKÉNT fedezte fel: <white>{name}</white> — a neve bekerül a térképekbe!</gold>",
                    Map.of("player", player.getName(), "name", name)));
            return;
        }
        if (configManager.getBoolean("hidden-spots.first-finder-only", false)) {
            player.sendActionBar(messageManager.getMessage("hidden-spot-already",
                    "<gray>🧭 {name} — {finder} fedezte fel elsőként.</gray>",
                    Map.of("name", name, "finder",
                            discovererName.getOrDefault(spotId, "valaki"))));
            return;
        }
        giveRewards(player, spot, Math.max(0.0D, Math.min(1.0D,
                configManager.getDouble("hidden-spots.repeat-reward-ratio", 0.5D))));
        player.playSound(player.getLocation(),
                org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.3F);
        player.sendMessage(messageManager.getMessage("hidden-spot-found",
                "<aqua>🧭 Felfedezted: <white>{name}</white> <gray>(elsőként {finder} járt itt)</gray></aqua>",
                Map.of("name", name, "finder",
                        discovererName.getOrDefault(spotId, "valaki"))));
    }

    private void giveRewards(final Player player, final ConfigurationSection spot,
                             final double ratio) {
        if (ratio <= 0.0D) return;
        for (final String entry : spot.getStringList("rewards")) {
            final ItemStack stack = LootTable.parseEntry(entry);
            if (stack != null) {
                stack.setAmount(Math.max(1,
                        (int) Math.round(stack.getAmount() * ratio)));
                player.getInventory().addItem(stack).values()
                        .forEach(left -> player.getWorld()
                                .dropItemNaturally(player.getLocation(), left));
            }
        }
        final int xp = (int) Math.round(
                Math.max(0, spot.getInt("xp", 20)) * ratio);
        if (xp > 0) player.giveExp(xp);
    }
}
