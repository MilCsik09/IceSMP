package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Server/world operations plus the safe staged text and string-list editors. */
public final class ServerWorldConfigMenuGUI {

    public static final String ROOT_ACTION = "SERVER_WORLD_CONFIG";
    public static final String CATEGORY_ID = "server-world";
    public static final String ENTRY_ACTION_PREFIX = "ADV:";

    private static final List<AdvancedConfigEntry> ENTRIES = List.of(
            AdvancedConfigEntry.toggle("settings.disable-locator-bar", "Vanilla Locator Bar tiltása",
                    "Minden betöltött világban ki- vagy bekapcsolja a vanilla játékosirány-jelző Locator Bart. A módosítás mentés után azonnal végigfut a világokon."),
            AdvancedConfigEntry.toggle("world-tweaks.warden-death-xp.enabled", "Warden XP felülírás",
                    "Bekapcsolva a Warden halálakor a vanilla érték helyett a megadott minimum és maximum közötti XP esik."),
            AdvancedConfigEntry.integer("world-tweaks.warden-death-xp.min", "Warden minimum XP", 5, 0, 100000,
                    "A Warden halálakor sorsolható XP alsó határa. Nem lehet nagyobb a maximum XP értékénél."),
            AdvancedConfigEntry.integer("world-tweaks.warden-death-xp.max", "Warden maximum XP", 5, 0, 100000,
                    "A Warden halálakor sorsolható XP felső határa. Nem lehet kisebb a minimum XP értékénél."),
            AdvancedConfigEntry.toggle("world-tweaks.crop-trample-protection.players", "Játékos terméstaposás-védelem",
                    "Megakadályozza, hogy játékos ugrása vagy esése a szántóföldet földdé tapossa."),
            AdvancedConfigEntry.toggle("world-tweaks.crop-trample-protection.mobs", "Mob terméstaposás-védelem",
                    "Megakadályozza, hogy mobok mozgása vagy esése a szántóföldet földdé tapossa."),
            AdvancedConfigEntry.integer("world-events.check-interval-seconds", "Világesemény-driver üteme (mp)", 5, 1, 3600,
                    "A közös világesemény-driver periódusa. Mentéskor a global scheduler taskja élőben újraütemeződik."),
            AdvancedConfigEntry.stringList("world-events.orchestration.major-events", "Egymást kizáró nagy események",
                    16, 32, false, "[a-z0-9_-]+",
                    "Az itt felsorolt nagy események nem futhatnak egymással párhuzamosan. A chatben ;; jellel elválasztott eseményazonosítókat adj meg."),
            AdvancedConfigEntry.text("mob-scaling.name.prefix", "Skálázott mob névelőtag", 64, true, "",
                    "A skálázott mob neve elé kerülő legacy szöveg. A %level% token az aktuális mobszintre cserélődik, az & színkódok használhatók."),
            AdvancedConfigEntry.text("hud.sidebar.title", "HUD-oldalsáv címe", 64, false, "",
                    "A scoreboard oldalsáv felső címe. Legacy & színkód és resource-pack glyph is megadható; a HUD mentés után újrarajzolódik."),
            AdvancedConfigEntry.stringList("hud.dynamic.combat-visible-sections", "Harcban látható HUD-szekciók",
                    6, 24, true, "[a-z]+",
                    "Harc-fókusz alatt csak ezek a HUD-szekciók maradnak láthatók. Példák: eroforras, csapat, kaszt, valuta."),
            AdvancedConfigEntry.stringList("moderation.chat-filter.words", "Chatszűrő tiltott szavai",
                    128, 48, true, "[^\\r\\n]+",
                    "A natív chatszűrő által keresett szavak és kifejezések. Kis- és nagybetűtől függetlenül működik; az elemeket ;; jellel válaszd el."),
            AdvancedConfigEntry.stringList("moderation.muted-blocked-commands", "Némítás alatt tiltott parancsok",
                    32, 32, true, "[A-Za-z0-9:_-]+",
                    "A némított játékos által nem használható privát üzenet- és chatjellegű parancsnevek, kezdő perjel nélkül."),
            AdvancedConfigEntry.text("world-events.intro.join-welcome.title", "Belépési üdvözlő cím", 128, true, "",
                    "Az első belépési és visszatérési üdvözlő title MiniMessage szövege. Üres értékkel a cím elhagyható."),
            AdvancedConfigEntry.text("world-events.intro.join-welcome.subtitle", "Belépési üdvözlő alcím", 192, true, "",
                    "Az üdvözlő title alatt megjelenő MiniMessage alcím. Üres értékkel az alcím elhagyható."),
            AdvancedConfigEntry.stringList("world-events.intro.lines", "Első belépési történetsorok",
                    16, 192, false, "[^\\r\\n]+",
                    "Az intro egymás után megjelenő címsorai. Minden elem formátuma pontosan cím||alcím; az egyes elemeket a chatben ;; jellel válaszd el.")
    );

    private ServerWorldConfigMenuGUI() { }

    public static int entryCount() { return ENTRIES.size(); }
    public static List<AdvancedConfigEntry> entries() { return ENTRIES; }

    public static AdvancedConfigEntry findEntry(final String key) {
        return ENTRIES.stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
    }

    public static void open(final Player player, final ConfigManager configManager) {
        open(player, configManager, null);
    }

    public static void open(final Player player, final ConfigManager configManager,
                            final ConfigEditSession session) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), CATEGORY_ID);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Szerver, világ és szövegek", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final AdvancedConfigEntry entry : ENTRIES) {
            inventory.setItem(slot, AdvancedConfigEntryRenderer.render(entry, configManager, session));
            holder.bind(slot, ENTRY_ACTION_PREFIX + entry.key());
            slot++;
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }
}
