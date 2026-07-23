package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.CurrencyType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Exchange-rate hologram boards: floating
 * TextDisplay panels that show the live, supply-driven currency values from the
 * {@link ExchangeRateService} and refresh on the world-events tick. Admins place
 * and remove boards with /exchangeboard. Board locations are persisted so the
 * holograms can be refreshed in place across restarts (the TextDisplay entities
 * themselves are persistent world entities).
 *
 * <p>Folia note: the world-events tick runs on the global region scheduler, so
 * every entity access hops onto the board's own region thread first.
 */
public final class ExchangeBoardManager implements PersistentStore {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** A tracked board: the display entity's id plus the location it lives at. */
    private record Board(UUID entityId, Location location) { }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ExchangeRateService exchangeRateService;
    private final NamespacedKey boardKey;
    private final File storageFile;
    private final List<Board> boards = new CopyOnWriteArrayList<>();

    public ExchangeBoardManager(final JavaPlugin plugin, final ConfigManager configManager,
                                final ExchangeRateService exchangeRateService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.exchangeRateService = exchangeRateService;
        this.boardKey = new NamespacedKey(plugin, "is_exchange_board");
        this.storageFile = new File(plugin.getDataFolder(), "exchange-boards.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        boards.clear();
        if (!storageFile.exists()) {
            return;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        final ConfigurationSection section = yaml.getConfigurationSection("boards");
        if (section == null) {
            return;
        }

        for (final String key : section.getKeys(false)) {
            final ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            final Location location = entry.getLocation("location");
            if (location == null || location.getWorld() == null) {
                continue;
            }
            try {
                boards.add(new Board(UUID.fromString(key), location));
            } catch (final IllegalArgumentException ignored) {
                // Skip malformed entries.
            }
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final Board board : boards) {
                yaml.set("boards." + board.entityId() + ".location", board.location());
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save exchange-boards.yml: " + exception.getMessage());
        }
    }

    /**
     * Places a new exchange board hologram above the player. Runs on the
     * location's region so it is Folia-safe.
     *
     * @param player the placing admin
     */
    public void placeBoard(final Player player) {
        final Location at = player.getLocation().clone().add(0.0D, 2.5D, 0.0D);
        plugin.getServer().getRegionScheduler().run(plugin, at, task -> {
            final TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setPersistent(true);
                spawned.setTransformation(new Transformation(
                        new Vector3f(), new AxisAngle4f(), new Vector3f(1.2F, 1.2F, 1.2F), new AxisAngle4f()));
                spawned.getPersistentDataContainer().set(boardKey, PersistentDataType.BOOLEAN, true);
                spawned.text(buildText());
            });
            boards.add(new Board(display.getUniqueId(), at));
            save();
        });
    }

    /**
     * Removes the nearest exchange board within range of the player.
     *
     * @param player the admin
     * @param range search radius
     * @return true if a board was removed
     */
    public boolean removeNearest(final Player player, final double range) {
        Entity nearest = null;
        double nearestSq = range * range;
        for (final Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!isBoard(entity)) {
                continue;
            }
            final double sq = entity.getLocation().distanceSquared(player.getLocation());
            if (sq <= nearestSq) {
                nearestSq = sq;
                nearest = entity;
            }
        }

        if (nearest == null) {
            return false;
        }

        final UUID removedId = nearest.getUniqueId();
        boards.removeIf(board -> board.entityId().equals(removedId));
        // Folia: the found display can sit in a neighbouring region — remove it on its own scheduler.
        final Entity found = nearest;
        if (org.bukkit.Bukkit.isOwnedByCurrentRegion(found)) {
            found.remove();
        } else {
            found.getScheduler().run(plugin, task -> found.remove(), null);
        }
        save();
        return true;
    }

    public boolean isBoard(final Entity entity) {
        return entity instanceof TextDisplay
                && Boolean.TRUE.equals(entity.getPersistentDataContainer().get(boardKey, PersistentDataType.BOOLEAN));
    }

    /**
     * Refreshes the text of every tracked board. Each refresh hops onto the
     * board's region thread (Folia-safe). Boards whose entity no longer exists
     * are pruned lazily.
     */
    public void tick() {
        if (boards.isEmpty() || !configManager.getBoolean("exchange-board.enabled", true)) {
            return;
        }

        final Component text = buildText();
        for (final Board board : boards) {
            if (board.location().getWorld() == null) {
                continue;
            }
            plugin.getServer().getRegionScheduler().run(plugin, board.location(), task -> {
                final Entity entity = board.location().getWorld().getEntity(board.entityId());
                if (entity instanceof TextDisplay display) {
                    display.text(text);
                } else if (entity == null) {
                    boards.remove(board);
                }
            });
        }
    }

    private Component buildText() {
        final StringBuilder builder = new StringBuilder("<gradient:#5cb3ff:#ffffff><bold>Árfolyamok</bold></gradient>\n");
        for (final CurrencyType currency : CurrencyType.values()) {
            builder.append(String.format(Locale.ROOT,
                    "<white>%s:</white> <yellow>%.2f</yellow>\n",
                    currency.getDisplayName(), exchangeRateService.getValue(currency)));
        }
        builder.append("<gray>(érték = ritkaság szerint)</gray>");
        return MINI_MESSAGE.deserialize(builder.toString());
    }
}
