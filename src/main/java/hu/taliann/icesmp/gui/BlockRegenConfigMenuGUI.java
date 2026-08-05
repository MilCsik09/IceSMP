package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Az admin menüből állítható robbanás- és blokkregenerációs kulcsok. */
public final class BlockRegenConfigMenuGUI {

    public static final String CATEGORY_ID = "blockregen";
    public static final String ROOT_ACTION = "BLOCK_REGEN";

    private static final List<ConfigMenuGUI.Entry> ENTRIES = List.of(
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.enabled", "Világregeneráció bekapcsolva"),
            ConfigMenuGUI.Entry.toggle("claims.protect-explosions", "Claim-robbanások védelme"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.capital", "Főváros visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.protected-city", "Védett város visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.protected-faction", "Védett frakciózóna visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.dungeon", "Kazamata visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.doom-gate", "Kárhozat Kapuja visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.faction", "Normál frakcióterület visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.zones.wilderness", "Vadon visszagyógyuljon"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.capital.allow-explosions", "Fővárosi robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.protected-city.allow-explosions", "Védett városi robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.protected-faction.allow-explosions", "Védett frakciózónás robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.dungeon.allow-explosions", "Kazamatai robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.doom-gate.allow-explosions", "Kapu-robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.toggle("territory.protection.rules.faction.allow-explosions", "Frakcióföldi robbanás engedett regen nélkül"),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.delay-seconds", "Robbanás utáni várakozás (mp)", 15, 5, 3600),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.restore-interval-ticks", "Visszaépítő ütem (tick)", 1, 1, 120),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.blocks-per-pass", "Blokkok menetenként", 1, 1, 128),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.support-grace-seconds", "Támasz-várakozás (mp)", 10, 5, 3600),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.max-recaptures", "Újrarombolási próbák plafonja", 1, 1, 100),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.recapture-window-seconds", "Újrarombolási ablak (mp)", 30, 30, 86400),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.physics-shield-enabled", "Fizika-pajzs bekapcsolva"),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.physics-shield-seconds", "Visszaépített blokk pajzsa (mp)", 30, 0, 86400),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.player-break.siege-enabled", "Ostrom alatti kézi rombolás"),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.player-break.siege-delay-seconds", "Ostromrombolás visszaépülése (mp)", 15, 5, 3600),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.player-break.always-enabled", "Állandó kézi rombolás védett zónában"),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.player-break.always-delay-seconds", "Állandó rombolás visszaépülése (mp)", 15, 5, 3600),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.restore-effects-enabled", "Visszaépítési hang és részecske"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.tile-entity-explode", "Láda/tábla/spawner is kirobbanhat"),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.debris-enabled", "Repülő törmelék-effekt"),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-percent", "Törmelékké váló blokkok (%)", 5, 0, 100),
            ConfigMenuGUI.Entry.integer("territory.protection.regen.debris-lifetime-seconds", "Törmelék élettartama (mp)", 1, 1, 60),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-launch-power", "Alap radiális kilövési erő", 0.1, 0, 5),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-horizontal-multiplier", "Vízszintes röppálya-szorzó", 0.1, 0, 5),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-vertical-multiplier", "Függőleges röppálya-szorzó", 0.1, 0, 5),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-horizontal-spread", "Véletlen oldalirányú szórás", 0.05, 0, 3),
            ConfigMenuGUI.Entry.number("territory.protection.regen.debris-extra-upward-velocity", "Extra felfelé sebesség", 0.05, 0, 3),
            ConfigMenuGUI.Entry.toggle("territory.protection.regen.debris-gravity-enabled", "Törmelék gravitációja")
    );

    private BlockRegenConfigMenuGUI() { }

    public static int entryCount() { return ENTRIES.size(); }
    public static List<ConfigMenuGUI.Entry> entries() { return ENTRIES; }

    public static boolean requiresRestart(final String key) {
        return "territory.protection.regen.restore-interval-ticks".equals(key);
    }

    public static ConfigMenuGUI.Entry findEntry(final String key) {
        return ENTRIES.stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
    }

    public static void open(final Player player, final ConfigManager configManager) {
        open(player, configManager, null);
    }

    public static void open(final Player player, final ConfigManager configManager,
                            final ConfigEditSession session) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), CATEGORY_ID);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Robbanás és regeneráció", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final ConfigMenuGUI.Entry entry : ENTRIES) {
            inventory.setItem(slot, ConfigMenuEntryRenderer.render(entry, configManager, session));
            holder.bind(slot, switch (entry.type()) {
                case TOGGLE -> "TOGGLE:" + entry.key();
                case CYCLE -> "CYCLE:" + entry.key();
                default -> "NUM:" + entry.key();
            });
            slot++;
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }
}
