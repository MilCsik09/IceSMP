package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Az admin menüből állítható robbanás- és blokkregenerációs kulcsok. */
public final class BlockRegenConfigMenuGUI {

    public static final String CATEGORY_ID = "blockregen";
    public static final String ROOT_ACTION = "BLOCK_REGEN";

    private static final Set<String> RESTART_REQUIRED_KEYS = Set.of(
            "territory.protection.regen.restore-interval-ticks"
    );

    private static final List<ConfigMenuGUI.Entry> ENTRIES = List.of(
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.enabled",
                    "Világregeneráció bekapcsolva"),
            ConfigMenuGUI.Entry.toggle("claims.protect-explosions",
                    "Claim-robbanások védelme"),

            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.capital",
                    "Főváros visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.protected-city",
                    "Védett város visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.protected-faction",
                    "Védett frakciózóna visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.dungeon",
                    "Kazamata visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.doom-gate",
                    "Kárhozat Kapuja visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.faction",
                    "Normál frakcióterület visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.wilderness",
                    "Vadon visszagyógyuljon"),

            ConfigMenuGUI.Entry.toggle("territory.protection.rules.capital.allow-explosions",
                    "Fővárosi robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.protected-city.allow-explosions",
                    "Védett városi robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.protected-faction.allow-explosions",
                    "Védett frakciózónás robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.dungeon.allow-explosions",
                    "Kazamatai robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.doom-gate.allow-explosions",
                    "Kapu-robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.faction.allow-explosions",
                    "Frakcióföldi robbanás engedett regen nélkül"),

            ConfigMenuGUI.Entry.integer("territory.protection.regen.delay-seconds",
                    "Robbanás utáni várakozás (mp)", 15, 5, 3600),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.restore-interval-ticks",
                    "Visszaépítő ütem (tick, restart)", 1, 1, 120),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.blocks-per-pass",
                    "Blokkok menetenként", 1, 1, 128),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.support-grace-seconds",
                    "Támasz-várakozás (mp)", 10, 5, 3600),

            ConfigMenuGUI.Entry.integer("territory.protection.regen.max-recaptures",
                    "Újrarombolási próbák plafonja", 1, 1, 100),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.recapture-window-seconds",
                    "Újrarombolási ablak (mp)", 30, 30, 86400),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.physics-shield-enabled",
                    "Fizika-pajzs bekapcsolva"),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.physics-shield-seconds",
                    "Visszaépített blokk pajzsa (mp)", 30, 0, 86400),

            ConfigMenuGUI.Entry.toggle("territory.protection.regen.player-break.siege-enabled",
                    "Ostrom alatti kézi rombolás"),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.player-break.siege-delay-seconds",
                    "Ostromrombolás visszaépülése (mp)", 15, 5, 3600),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.player-break.always-enabled",
                    "Állandó kézi rombolás védett zónában"),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.player-break.always-delay-seconds",
                    "Állandó rombolás visszaépülése (mp)", 15, 5, 3600),

            ConfigMenuGUI.Entry.toggle("territory.protection.regen.restore-effects-enabled",
                    "Visszaépítési hang és részecske"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.tile-entity-explode",
                    "Láda/tábla/spawner is kirobbanhat"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.debris-enabled",
                    "Repülő törmelék-effekt"),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-percent",
                    "Törmelékké váló blokkok (%)", 5, 0, 100),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.debris-lifetime-seconds",
                    "Törmelék élettartama (mp)", 1, 1, 60),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-launch-power",
                    "Törmelék kilövési ereje", 0.1, 0, 5)
    );

    private BlockRegenConfigMenuGUI() {
    }

    public static int entryCount() {
        return ENTRIES.size();
    }

    public static boolean requiresRestart(final String key) {
        return RESTART_REQUIRED_KEYS.contains(key);
    }

    public static ConfigMenuGUI.Entry findEntry(final String key) {
        for (final ConfigMenuGUI.Entry entry : ENTRIES) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        return null;
    }

    public static void open(final Player player, final ConfigManager configManager) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), CATEGORY_ID);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Robbanás és regeneráció", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        int slot = 0;
        for (final ConfigMenuGUI.Entry entry : ENTRIES) {
            inventory.setItem(slot, entryTile(entry, configManager));
            holder.bind(slot, switch (entry.type()) {
                case TOGGLE -> "TOGGLE:" + entry.key();
                case CYCLE -> "CYCLE:" + entry.key();
                default -> "NUM:" + entry.key();
            });
            slot++;
        }

        inventory.setItem(49, tile(Material.ARROW, "&7Vissza", List.of()));
        holder.bind(49, "BACK");
        inventory.setItem(53, tile(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(53, "CLOSE");
        player.openInventory(inventory);
    }

    private static ItemStack entryTile(final ConfigMenuGUI.Entry entry,
                                       final ConfigManager configManager) {
        final List<String> lore = new ArrayList<>();
        lore.add("&8" + entry.key());
        if (requiresRestart(entry.key())) {
            lore.add("&cA szerver újraindítása után lép életbe");
        }
        switch (entry.type()) {
            case TOGGLE -> {
                final boolean value = configManager.getBoolean(entry.key(), false);
                lore.add(value ? "&aBekapcsolva" : "&cKikapcsolva");
                lore.add("&eKattints a váltáshoz");
                return tile(value ? Material.LIME_DYE : Material.GRAY_DYE,
                        (value ? "&a" : "&c") + entry.label(), lore);
            }
            case CYCLE -> {
                final String value = configManager.getString(entry.key(),
                        entry.options().isEmpty() ? "?" : entry.options().get(0));
                lore.add("&fJelenleg: &b" + value);
                lore.add("&7Opciók: &f" + String.join(" / ", entry.options()));
                lore.add("&eKattints a következőhöz");
                return tile(Material.COMPARATOR, "&b" + entry.label(), lore);
            }
            default -> {
                final double value = configManager.getDouble(entry.key(), 0.0D);
                lore.add("&fJelenleg: &b" + formatNumber(entry, value));
                lore.add("&7Bal katt: &f+" + formatStep(entry)
                        + " &7| Jobb katt: &f−" + formatStep(entry));
                lore.add("&7SHIFT = ötszörös lépés");
                return tile(Material.PAPER, "&b" + entry.label(), lore);
            }
        }
    }

    private static String formatNumber(final ConfigMenuGUI.Entry entry, final double value) {
        return entry.type() == ConfigMenuGUI.EntryType.INTEGER
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatStep(final ConfigMenuGUI.Entry entry) {
        return entry.type() == ConfigMenuGUI.EntryType.INTEGER
                ? String.valueOf((long) entry.step())
                : String.format(Locale.ROOT, "%.2f", entry.step());
    }

    private static ItemStack tile(final Material material, final String name,
                                  final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            for (final String line : loreLines) {
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(line)
                        .colorIfAbsent(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
