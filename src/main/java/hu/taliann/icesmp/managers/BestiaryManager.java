package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * B21 — Bestiárium / gyűjtő-album: a krónikás-lajstrom játékos-oldala. Négy
 * kategória pipálódik (első alkalmak): megölt mob-FAJOK, elkészített receptek,
 * bejárt territóriumok, legyőzött boss-archetípusok. A haladás player-PDC-ben
 * él (CSV-halmazok — nincs külön store), a mérföldkövek fizikai veretben
 * fizetnek (a „számlára csak a bankból” szabály szerint), a nagy mérföldkő
 * broadcastol. Folia: minden record-hívás a játékos saját régió-szálán fut
 * (kill/craft/enter eventek), a PDC-írás ott biztonságos.
 */
public final class BestiaryManager {

    public enum Category { MOBS, RECIPES, TERRITORIES, BOSSES }

    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final Map<Category, NamespacedKey> keys = new java.util.EnumMap<>(Category.class);

    public BestiaryManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final CurrencyManager currencyManager, final FactionManager factionManager,
                           final MessageManager messageManager) {
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        for (final Category category : Category.values()) {
            keys.put(category, new NamespacedKey(plugin, "bestiary_" + category.name().toLowerCase(Locale.ROOT)));
        }
    }

    public boolean isEnabled() {
        return configManager.getBoolean("bestiary.enabled", true);
    }

    public Set<String> entries(final Player player, final Category category) {
        final String raw = player.getPersistentDataContainer()
                .getOrDefault(keys.get(category), PersistentDataType.STRING, "");
        final Set<String> out = new LinkedHashSet<>();
        if (!raw.isBlank()) {
            out.addAll(Arrays.asList(raw.split(",")));
        }
        return out;
    }

    public int count(final Player player, final Category category) {
        return entries(player, category).size();
    }

    /** Első-alkalom rögzítés; mérföldkő-ellenőrzéssel. Igaz, ha ÚJ bejegyzés volt. */
    public boolean record(final Player player, final Category category, final String id) {
        if (!isEnabled() || id == null || id.isBlank()) {
            return false;
        }
        final Set<String> set = entries(player, category);
        if (!set.add(id.toLowerCase(Locale.ROOT))) {
            return false;
        }
        player.getPersistentDataContainer().set(keys.get(category), PersistentDataType.STRING,
                String.join(",", set));
        checkMilestone(player, category, set.size());
        return true;
    }

    /** Mérföldkövek: config-lista (`bestiary.milestones.<kategória>`), veret-jutalommal. */
    private void checkMilestone(final Player player, final Category category, final int size) {
        final String base = "bestiary.milestones." + category.name().toLowerCase(Locale.ROOT);
        for (final String row : configManager.getStringList(base)) {
            // formátum: "<darab>:<veret-jutalom>[:broadcast]" — hibás sor kihagyva,
            // különben minden killnél kivétel dőlne a listener-láncra.
            final String[] parts = row.split(":");
            final int threshold;
            final long reward;
            try {
                if (parts.length < 2) {
                    continue;
                }
                threshold = Integer.parseInt(parts[0].trim());
                reward = Long.parseLong(parts[1].trim());
            } catch (final NumberFormatException ignored) {
                continue;
            }
            if (threshold != size) {
                continue;
            }
            if (reward > 0) {
                currencyManager.payOutTokens(player,
                        CurrencyType.fromFactionType(factionManager.getFaction(player.getUniqueId())), reward);
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.1F);
            player.sendMessage(messageManager.getMessage("bestiary-milestone",
                    "<gold>📜 Bestiárium-mérföldkő: <white>{count}</white> bejegyzés a(z) <white>{category}</white> lajstromban! Jutalom: <white>{reward} veret</white> a kezedbe.</gold>",
                    Map.of("count", String.valueOf(size), "category", categoryName(category),
                            "reward", String.valueOf(reward))));
            if (parts.length >= 3 && "broadcast".equalsIgnoreCase(parts[2].trim())) {
                Bukkit.getServer().broadcast(messageManager.getMessage("bestiary-milestone-broadcast",
                        "<gold>📜 <white>{player}</white> lajstroma <white>{count}</white> bejegyzésre hízott a(z) <white>{category}</white> fejezetben — a krónikások főt hajtanak!</gold>",
                        Map.of("player", player.getName(), "count", String.valueOf(size),
                                "category", categoryName(category))));
            }
        }
    }

    public static String categoryName(final Category category) {
        return switch (category) {
            case MOBS -> "Szörnyek";
            case RECIPES -> "Receptek";
            case TERRITORIES -> "Territóriumok";
            case BOSSES -> "Világbossok";
        };
    }
}
