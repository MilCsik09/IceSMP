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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Frequently adjusted operational domains kept behind one compact submenu. */
public final class OperationalConfigMenuGUI {

    public static final String ROOT_ACTION = "OPERATIONAL_CONFIG";
    public static final String CATEGORY_ACTION_PREFIX = "OPS_CAT:";
    public static final String ROOT_CATEGORY_ID = "ops-root";
    private static final String HOLDER_PREFIX = "ops:";

    public record Category(String id, String title, Material icon,
                           List<ConfigMenuGUI.Entry> entries) {
    }

    private static final Map<String, Category> CATEGORIES = buildCatalog();

    private OperationalConfigMenuGUI() {
    }

    private static Map<String, Category> buildCatalog() {
        final Map<String, Category> categories = new LinkedHashMap<>();

        categories.put("afk", new Category("afk", "AFK és jutalomvédelem",
                Material.CLOCK, List.of(
                ConfigMenuGUI.Entry.integer("afk.afk-after-seconds",
                        "Automatikus AFK-idő (mp)", 15, 1, 31_536_000),
                ConfigMenuGUI.Entry.toggle("afk.block-rewards",
                        "AFK jutalmak globális tiltása")
        )));

        categories.put("hud", new Category("hud", "HUD és tablista",
                Material.COMPASS, List.of(
                ConfigMenuGUI.Entry.toggle("hud.enabled", "IceSMP HUD"),
                ConfigMenuGUI.Entry.toggle("hud.sidebar-enabled", "Scoreboard-oldalsáv"),
                ConfigMenuGUI.Entry.toggle("hud.tablist-enabled", "Egyszerű tabnév fallback"),
                ConfigMenuGUI.Entry.toggle("hud.low-hp-vignette.enabled", "Alacsony HP-vignetta"),
                ConfigMenuGUI.Entry.number("hud.low-hp-vignette.threshold-percent",
                        "Vignetta HP-küszöb (%)", 2.5D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.integer("hud.refresh-ticks",
                        "HUD frissítési idő (tick)", 5, 5, 200),
                ConfigMenuGUI.Entry.toggle("hud.dynamic.combat-focus", "Harci fókusznézet"),
                ConfigMenuGUI.Entry.integer("hud.dynamic.combat-grace-seconds",
                        "Harci HUD türelmi idő (mp)", 1, 0, 120),
                ConfigMenuGUI.Entry.toggle("hud.dynamic.rotating-line", "Forgó információs sor"),
                ConfigMenuGUI.Entry.integer("hud.dynamic.rotation-seconds",
                        "Információs sor váltása (mp)", 1, 1, 120),
                ConfigMenuGUI.Entry.toggle("hud.profile.enabled", "Közeli profil-kijelzés"),
                ConfigMenuGUI.Entry.integer("hud.profile.update-interval-ticks",
                        "Profil frissítése (tick)", 1, 1, 200),
                ConfigMenuGUI.Entry.integer("hud.profile.lifetime-ticks",
                        "Profil élettartama (tick)", 20, 20, 6000),
                ConfigMenuGUI.Entry.number("hud.profile.distance",
                        "Profil megjelenési táv", 0.5D, 0.5D, 32.0D),
                ConfigMenuGUI.Entry.number("hud.profile.height-offset",
                        "Profil magassági eltolás", 0.05D, -3.0D, 3.0D),
                ConfigMenuGUI.Entry.toggle("tablist.enabled", "Natív tablista"),
                ConfigMenuGUI.Entry.integer("tablist.refresh-ticks",
                        "Tablista frissítése (tick)", 5, 5, 200),
                ConfigMenuGUI.Entry.integer("tablist.sweep-every-refresh",
                        "Teljes takarítás gyakorisága", 1, 1, 200),
                ConfigMenuGUI.Entry.toggle("tablist.header-footer.enabled", "Tab header és footer"),
                ConfigMenuGUI.Entry.toggle("tablist.tab-names.enabled", "Formázott tabnevek"),
                ConfigMenuGUI.Entry.toggle("tablist.nametags.enabled", "Fej fölötti nametagek"),
                ConfigMenuGUI.Entry.toggle("tablist.nametags.war-colors", "Raid alatti hadi színek"),
                ConfigMenuGUI.Entry.toggle("tablist.playerlist-ping.enabled", "Ping oszlop"),
                ConfigMenuGUI.Entry.integer("tablist.ping-colors.good",
                        "Zöld ping küszöb (ms)", 10, 0, 5000),
                ConfigMenuGUI.Entry.integer("tablist.ping-colors.ok",
                        "Sárga ping küszöb (ms)", 10, 0, 5000)
        )));

        categories.put("pets", new Category("pets", "Petek és minionok",
                Material.BONE, List.of(
                ConfigMenuGUI.Entry.toggle("pets.summon.night-only", "Rituális idézés csak éjjel"),
                ConfigMenuGUI.Entry.integer("pets.summon.bonus-levels", "Rituális társszint-bónusz", 1, 0, 100),
                ConfigMenuGUI.Entry.integer("pets.summon.tier2-level", "Második forma szintje", 1, 1, 100),
                ConfigMenuGUI.Entry.integer("pets.summon.tier3-level", "Harmadik forma szintje", 1, 1, 100),
                ConfigMenuGUI.Entry.number("pets.summon.heart-drop-chance", "Nyughatatlan Szív esélye", 0.01D, 0.0D, 1.0D),
                ConfigMenuGUI.Entry.number("pets.summon.seal-drop-chance", "Démon-pecsét esélye", 0.01D, 0.0D, 1.0D),
                ConfigMenuGUI.Entry.integer("pets.max-active", "Aktív társak plafonja", 1, 1, 64),
                ConfigMenuGUI.Entry.number("pets.talent-health-share", "Gazda HP-talent részesedése", 0.05D, 0.0D, 1.0D),
                ConfigMenuGUI.Entry.number("pets.equipment.drop-chance", "Társvért dropesély", 0.005D, 0.0D, 1.0D),
                ConfigMenuGUI.Entry.number("pets.equipment.armor-bonus", "Társvért armor-bónusz", 0.5D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("pets.equipment.health-bonus", "Társvért HP-bónusz", 0.5D, 0.0D, 1000.0D),
                ConfigMenuGUI.Entry.integer("pets.companion.max-level", "Companion max. szint", 1, 1, 100),
                ConfigMenuGUI.Entry.integer("pets.companion.death-respawn-seconds", "Halál utáni várakozás (mp)", 15, 0, 86_400),
                ConfigMenuGUI.Entry.integer("pets.companion.base-xp", "Első szintlépés XP-je", 1, 1, 100_000),
                ConfigMenuGUI.Entry.integer("pets.companion.increment-per-level", "XP-növekmény szintenként", 1, 0, 100_000),
                ConfigMenuGUI.Entry.integer("pets.companion.xp-per-kill", "Companion XP ölésenként", 1, 0, 10_000),
                ConfigMenuGUI.Entry.number("pets.companion.health-per-level", "HP szintenként", 0.25D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("pets.companion.damage-per-level", "Sebzés szintenként", 0.1D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("pets.companion.follow-distance", "Visszateleportálási táv", 1.0D, 1.0D, 256.0D),
                ConfigMenuGUI.Entry.number("pets.companion.follow-start-distance", "Követés kezdőtávja", 0.5D, 0.5D, 128.0D),
                ConfigMenuGUI.Entry.integer("pets.companion.tick-ticks", "Companion driver üteme", 1, 2, 200),
                ConfigMenuGUI.Entry.number("pets.companion.attack-damage-base", "Alap támadási sebzés", 0.25D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("pets.companion.attack-reach", "Támadási elérés", 0.1D, 0.5D, 16.0D),
                ConfigMenuGUI.Entry.integer("pets.companion.attack-cooldown-ticks", "Támadási cooldown (tick)", 1, 1, 200),
                ConfigMenuGUI.Entry.number("pets.companion.chase-speed", "Üldözési sebesség", 0.1D, 0.1D, 5.0D),
                ConfigMenuGUI.Entry.number("pets.companion.aggro-range", "Automatikus aggro-táv", 1.0D, 0.0D, 128.0D),
                ConfigMenuGUI.Entry.number("pets.companion.leash-range", "Célpont elengedési táv", 1.0D, 1.0D, 256.0D)
        )));

        categories.put("economy", new Category("economy", "Piac és árfolyam",
                Material.EMERALD, List.of(
                ConfigMenuGUI.Entry.number("currency.exchange-rate", "Fix árfolyam", 0.05D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("currency.exchange-fee-percent", "Valutaváltási díj (%)", 0.5D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.toggle("currency.soul-drop.enabled", "Lélekkő-drop"),
                ConfigMenuGUI.Entry.integer("currency.soul-drop.min-mob-level", "Lélekkő minimum mobszint", 1, 1, 100),
                ConfigMenuGUI.Entry.number("currency.soul-drop.chance-percent", "Lélekkő esély (%)", 2.5D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.integer("currency.soul-drop.max-amount", "Lélekkő max. mennyiség", 1, 1, 1000),
                ConfigMenuGUI.Entry.integer("currency.soul-drop.daily-cap", "Napi lélekkő-plafon", 5, 0, 100_000),
                ConfigMenuGUI.Entry.toggle("currency.soul-drop.dark-undead-drops", "DARK élőhalott-drop"),
                ConfigMenuGUI.Entry.toggle("currency.economy-event.enabled", "Valutakeresleti sokkok"),
                ConfigMenuGUI.Entry.integer("currency.economy-event.check-interval-minutes", "Gazdasági ellenőrzés (perc)", 5, 1, 10_080),
                ConfigMenuGUI.Entry.number("currency.economy-event.chance-percent", "Gazdasági esemény esélye (%)", 2.5D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.integer("currency.economy-event.duration-hours", "Sokk időtartama (óra)", 1, 1, 720),
                ConfigMenuGUI.Entry.number("currency.economy-event.min-multiplier", "Sokk minimum szorzó", 0.05D, 0.0D, 10.0D),
                ConfigMenuGUI.Entry.number("currency.economy-event.max-multiplier", "Sokk maximum szorzó", 0.05D, 0.0D, 10.0D),
                ConfigMenuGUI.Entry.number("currency.economy-event.panic-chance", "Piaci pánik esélye", 0.05D, 0.0D, 1.0D),
                ConfigMenuGUI.Entry.number("currency.economy-event.panic-min-multiplier", "Pánik minimum szorzó", 0.05D, 0.0D, 2.0D),
                ConfigMenuGUI.Entry.number("currency.economy-event.panic-max-multiplier", "Pánik maximum szorzó", 0.05D, 0.0D, 2.0D),
                ConfigMenuGUI.Entry.toggle("currency.market-boom.enabled", "Konjunktúra"),
                ConfigMenuGUI.Entry.number("currency.market-boom.chance-percent", "Konjunktúra esélye (%)", 1.0D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.integer("currency.market-boom.duration-minutes", "Konjunktúra hossza (perc)", 5, 1, 10_080),
                ConfigMenuGUI.Entry.number("currency.market-boom.fee-percent", "Konjunktúra piaci díja (%)", 0.5D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.toggle("currency.dynamic-exchange.enabled", "Dinamikus árfolyam"),
                ConfigMenuGUI.Entry.number("currency.dynamic-exchange.reference-supply", "Referencia-kínálat", 100.0D, 1.0D, 1_000_000_000.0D),
                ConfigMenuGUI.Entry.number("currency.dynamic-exchange.elasticity", "Árfolyam rugalmassága", 0.05D, 0.0D, 10.0D),
                ConfigMenuGUI.Entry.number("currency.dynamic-exchange.min-multiplier", "Árfolyam alsó korlát", 0.05D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("currency.dynamic-exchange.max-multiplier", "Árfolyam felső korlát", 0.05D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("currency.dynamic-exchange.daily-limit", "Napi váltási limit", 25.0D, 0.0D, 1_000_000_000.0D),
                ConfigMenuGUI.Entry.integer("market.max-listings-per-player", "Hirdetések játékosonként", 1, 1, 1000),
                ConfigMenuGUI.Entry.number("market.fee-percent", "Piactéri díj (%)", 0.5D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.integer("market.auction.default-duration-hours", "Aukció alap időtartama (óra)", 1, 1, 720),
                ConfigMenuGUI.Entry.integer("market.auction.max-duration-hours", "Aukció max. időtartama (óra)", 1, 1, 720),
                ConfigMenuGUI.Entry.number("market.auction.min-increment-percent", "Minimum licitlépcső (%)", 1.0D, 0.0D, 100.0D),
                ConfigMenuGUI.Entry.number("market.auction.big-increment-percent", "Gyors licitlépcső (%)", 1.0D, 0.0D, 1000.0D)
        )));

        categories.put("moderation", new Category("moderation", "Moderáció és vanish",
                Material.SHIELD, List.of(
                ConfigMenuGUI.Entry.toggle("moderation.enabled", "Natív moderáció"),
                ConfigMenuGUI.Entry.toggle("moderation.chat-filter.enabled", "Chatszűrő"),
                ConfigMenuGUI.Entry.cycle("moderation.chat-filter.mode", "Chatszűrő mód", List.of("censor", "block")),
                ConfigMenuGUI.Entry.toggle("moderation.spam.enabled", "Spamvédelem"),
                ConfigMenuGUI.Entry.integer("moderation.spam.min-interval-millis", "Minimum üzenetköz (ms)", 100, 0, 60_000),
                ConfigMenuGUI.Entry.integer("moderation.spam.duplicate-window-seconds", "Duplikált üzenet ablaka (mp)", 1, 0, 3600),
                ConfigMenuGUI.Entry.toggle("moderation.chat-log.enabled", "Moderációs chatnapló"),
                ConfigMenuGUI.Entry.toggle("moderation.vanish.exclude-from-online-count", "Vanish kihagyása az online számból"),
                ConfigMenuGUI.Entry.toggle("moderation.vanish.allow-item-pickup", "Vanish tárgyfelvétel"),
                ConfigMenuGUI.Entry.toggle("moderation.vanish.allow-damage", "Vanish sebzés"),
                ConfigMenuGUI.Entry.toggle("moderation.vanish.allow-interaction", "Vanish interakció")
        )));

        validateCatalog(categories);
        return Map.copyOf(categories);
    }

    private static void validateCatalog(final Map<String, Category> categories) {
        final Set<String> keys = new java.util.HashSet<>();
        for (final Category category : categories.values()) {
            if (category.entries().size() > 45) {
                throw new IllegalStateException("Túl nagy üzemeltetési config-kategória: "
                        + category.id() + " (" + category.entries().size() + "/45)");
            }
            for (final ConfigMenuGUI.Entry entry : category.entries()) {
                if (!keys.add(entry.key())) {
                    throw new IllegalStateException("Duplikált üzemeltetési config-kulcs: "
                            + entry.key());
                }
                if (OperationalConfigHelp.describe(entry.key(), entry.label()).length() < 40) {
                    throw new IllegalStateException("Túl rövid üzemeltetési config-leírás: "
                            + entry.key());
                }
            }
        }
    }

    public static int categoryCount() {
        return CATEGORIES.size();
    }

    public static int entryCount() {
        return CATEGORIES.values().stream().mapToInt(category -> category.entries().size()).sum();
    }

    public static ConfigMenuGUI.Entry findEntry(final String key) {
        for (final Category category : CATEGORIES.values()) {
            for (final ConfigMenuGUI.Entry entry : category.entries()) {
                if (entry.key().equals(key)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static boolean isOperationalCategory(final String holderCategory) {
        return holderCategory != null && holderCategory.startsWith(HOLDER_PREFIX);
    }

    public static String categoryIdFromHolder(final String holderCategory) {
        return isOperationalCategory(holderCategory)
                ? holderCategory.substring(HOLDER_PREFIX.length()) : null;
    }

    public static void openRoot(final Player player) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), ROOT_CATEGORY_ID);
        final Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("⚙ Üzemeltetés és finomhangolás", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        final int[] slots = {10, 11, 12, 13, 14};
        int index = 0;
        for (final Category category : CATEGORIES.values()) {
            final int slot = slots[index++];
            inventory.setItem(slot, tile(category.icon(), "&b" + category.title(),
                    List.of("&7" + category.entries().size() + " élő kulcs",
                            "&eKattints a megnyitáshoz")));
            holder.bind(slot, CATEGORY_ACTION_PREFIX + category.id());
        }
        inventory.setItem(22, tile(Material.ARROW, "&7Vissza a főmenübe", List.of()));
        holder.bind(22, "BACK");
        inventory.setItem(26, tile(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(26, "CLOSE");
        player.openInventory(inventory);
    }

    public static void openCategory(final Player player, final String categoryId,
                                    final ConfigManager configManager) {
        final Category category = CATEGORIES.get(categoryId);
        if (category == null) {
            openRoot(player);
            return;
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(
                player.getUniqueId(), HOLDER_PREFIX + categoryId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ " + category.title(), NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        int slot = 0;
        for (final ConfigMenuGUI.Entry entry : category.entries()) {
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
        final Object defaultValue = ConfigMenuEntryRenderer.defaultValue(entry, configManager);
        final Object currentValue = ConfigMenuEntryRenderer.currentValue(entry, configManager);
        final List<String> lore = new ArrayList<>();
        lore.add("&8" + entry.key());
        lore.add("");
        for (final String line : wrap(
                OperationalConfigHelp.describe(entry.key(), entry.label()), 43)) {
            lore.add("&7" + line);
        }
        lore.add("");
        lore.add("&fJelenleg: &b" + formatValue(entry, currentValue));
        lore.add("&fAlapérték: &a" + formatValue(entry, defaultValue));
        lore.add(configManager.hasOverride(entry.key())
                ? "&eForrás: config.yml felülbírálás"
                : "&aForrás: subsystem alapkonfiguráció");
        lore.add("&aAzonnal, restart nélkül alkalmazódik");

        final Material material;
        final String name;
        switch (entry.type()) {
            case TOGGLE -> {
                final boolean enabled = Boolean.TRUE.equals(currentValue);
                material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
                name = (enabled ? "&a" : "&c") + entry.label();
                lore.add("");
                lore.add("&eBal/jobb katt: be- vagy kikapcsolás");
            }
            case CYCLE -> {
                material = Material.COMPARATOR;
                name = "&b" + entry.label();
                lore.add("&7Választható: &f" + String.join(" / ", entry.options()));
                lore.add("");
                lore.add("&eBal/jobb katt: következő lehetőség");
            }
            default -> {
                material = Material.PAPER;
                name = "&b" + entry.label();
                lore.add("&7Tartomány: &f" + formatBound(entry, entry.min())
                        + " &7– &f" + formatBound(entry, entry.max()));
                lore.add("");
                lore.add("&eBal katt: &f+" + formatStep(entry)
                        + " &7| &eJobb katt: &f−" + formatStep(entry));
                lore.add("&7SHIFT = ötszörös lépés");
            }
        }
        lore.add("&dGörgőkatt/Q: visszaállítás az alapértékre");
        return tile(material, name, lore);
    }

    private static String formatValue(final ConfigMenuGUI.Entry entry, final Object value) {
        return switch (entry.type()) {
            case TOGGLE -> Boolean.TRUE.equals(value) ? "bekapcsolva" : "kikapcsolva";
            case CYCLE -> String.valueOf(value);
            case INTEGER -> String.valueOf(((Number) value).longValue());
            case NUMBER -> String.format(Locale.ROOT, "%.3f", ((Number) value).doubleValue());
        };
    }

    private static String formatBound(final ConfigMenuGUI.Entry entry, final double value) {
        return entry.type() == ConfigMenuGUI.EntryType.INTEGER
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatStep(final ConfigMenuGUI.Entry entry) {
        return entry.type() == ConfigMenuGUI.EntryType.INTEGER
                ? String.valueOf((long) entry.step())
                : String.format(Locale.ROOT, "%.3f", entry.step());
    }

    private static List<String> wrap(final String text, final int width) {
        final List<String> lines = new ArrayList<>();
        final StringBuilder line = new StringBuilder();
        for (final String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
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
