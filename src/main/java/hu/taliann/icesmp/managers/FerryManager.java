package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Komp (építész-kérés): fix, két végpontú átkelő-teleport — pl. a Caldesterát a
 * szárazföldtől elválasztó óceánon át. A játékos a KÖZELEBBI végponttól a túlsó
 * partra utazik ({@code /komp <útvonal>}, tipikusan komp-NPC-re kötve:
 * {@code /npcbind <npc> command "komp <útvonal>"}). A viteldíj a banki egyenlegből
 * ég el (money sink, mint a boltok) — sosem keletkezik pénz. Útvonalak a configból
 * ({@code ferry.routes.<id>}), élőben olvasva; beszálláshoz a végpont közelében
 * kell állni (max-board-distance), a menet rövid cooldownnal jár.
 */
public final class FerryManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    /** UUID → az utolsó átkelés időbélyege (spam-fék; kilépéskor takarítandó méret-söpréssel). */
    private final Map<UUID, Long> lastRide = new ConcurrentHashMap<>();

    public FerryManager(final JavaPlugin plugin, final ConfigManager configManager,
                        final CurrencyManager currencyManager, final FactionManager factionManager,
                        final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    /** A configban felvett útvonal-azonosítók (tab-complete + lista). */
    public List<String> routeIds() {
        final ConfigurationSection routes = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("ferry.routes");
        return routes == null ? List.of() : List.copyOf(routes.getKeys(false));
    }

    /** Az útvonal kijelzett neve (lista/üzenetek). */
    public String routeName(final String routeId) {
        return configManager.getString("ferry.routes." + routeId + ".name", routeId);
    }

    /**
     * Átkelés: a játékost a route KÖZELEBBI végpontjától a túlsó végpontra viszi.
     * A hívó a játékos parancsa (saját régió-szál); a teleport teleportAsync.
     *
     * @return true, ha az utazás elindult
     */
    public boolean ride(final Player player, final String routeId) {
        if (!configManager.getBoolean("ferry.enabled", true)) {
            player.sendMessage(messageManager.getMessage("ferry-disabled", "<gray>⛴ A kompjárat most nem közlekedik.</gray>"));
            return false;
        }
        final Location a = parseLocation(configManager.getString("ferry.routes." + routeId + ".a", ""));
        final Location b = parseLocation(configManager.getString("ferry.routes." + routeId + ".b", ""));
        if (a == null || b == null) {
            player.sendMessage(messageManager.getMessage("ferry-unknown-route",
                    "<red>⛴ Ismeretlen vagy hiányosan beállított kompjárat.</red>"));
            return false;
        }

        // Beszállás-táv: a játékosnak valamelyik kikötő közelében kell állnia.
        final double boardDistance = Math.max(4.0D, configManager.getDouble("ferry.max-board-distance", 24.0D));
        final double distA = sameWorldDistance(player.getLocation(), a);
        final double distB = sameWorldDistance(player.getLocation(), b);
        if (Math.min(distA, distB) > boardDistance) {
            player.sendMessage(messageManager.getMessage("ferry-too-far",
                    "<gray>⛴ A kompra a kikötőben szállhatsz fel — menj közelebb a mólóhoz.</gray>"));
            return false;
        }
        final Location destination = distA <= distB ? b : a;

        // Spam-fék.
        final long cooldownMillis = Math.max(0L, configManager.getLong("ferry.cooldown-seconds", 10L)) * 1000L;
        final long now = System.currentTimeMillis();
        if (lastRide.size() > 512) {
            lastRide.values().removeIf(stamp -> now - stamp > cooldownMillis);
        }
        final Long last = lastRide.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            player.sendMessage(messageManager.getMessage("ferry-cooldown",
                    "<gray>⛴ A komp még fordul — várj egy pillanatot.</gray>"));
            return false;
        }

        // Viteldíj a banki egyenlegből (elég — money sink, mint a boltoknál).
        final double fee = Math.max(0.0D, configManager.getDouble("ferry.routes." + routeId + ".fee",
                configManager.getDouble("ferry.default-fee", 15.0D)));
        if (fee > 0.0D) {
            final CurrencyType currency = CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId()));
            if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, fee)) {
                player.sendMessage(messageManager.getMessage("ferry-no-funds",
                        "<red>⛴ A révész nem visz ingyen: <white>{fee}</white> a viteldíj (banki egyenleg).</red>",
                        Map.of("fee", currencyManager.formatBalance(fee))));
                return false;
            }
        }

        lastRide.put(player.getUniqueId(), now);
        player.playSound(player.getLocation(), Sound.ENTITY_BOAT_PADDLE_WATER, 1.0F, 0.9F);
        player.teleportAsync(destination).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                player.playSound(destination, Sound.ENTITY_BOAT_PADDLE_WATER, 1.0F, 1.1F);
                player.sendMessage(messageManager.getMessage("ferry-arrived",
                        "<aqua>⛴ A komp átért a túlpartra — {route}.</aqua>",
                        Map.of("route", routeName(routeId))));
            }
        });
        return true;
    }

    /** Világ-egyezés nélkül végtelen táv (más világban lévő kikötőbe nem szállhatsz be). */
    private static double sameWorldDistance(final Location from, final Location to) {
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return Double.MAX_VALUE;
        }
        return from.distance(to);
    }

    /** "world,x,y,z[,yaw,pitch]" formátum (mint az intro first-join-spawn). */
    private static Location parseLocation(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        final World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            final Location location = new Location(world,
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()));
            if (parts.length >= 6) {
                location.setYaw(Float.parseFloat(parts[4].trim()));
                location.setPitch(Float.parseFloat(parts[5].trim()));
            }
            return location;
        } catch (final NumberFormatException exception) {
            return null;
        }
    }
}
