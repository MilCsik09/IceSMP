package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.relics.RelicOwnership;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The /menu command hub and every per-system sub-menu. Each menu shows live
 * info read from the managers; the action buttons delegate to the existing
 * commands (RUN/OPEN) so no gameplay logic is duplicated here.
 */
public final class CommandMenus {

    private static final String ADMIN_PERMISSION = "icesmp.admin";

    private CommandMenus() {
    }

    public static void open(final Player player, final CommandMenuHolder.Menu menu, final CommandMenuContext ctx) {
        switch (menu) {
            case MAIN -> openMain(player, ctx);
            case FACTION -> openFaction(player, ctx);
            case BANK -> openBank(player, ctx);
            case QUEST -> openQuest(player, ctx);
            case EVENTS -> openEvents(player, ctx);
            case RELIC -> openRelic(player, ctx);
            case SOULS -> openSouls(player, ctx);
            case LEADERBOARD -> openLeaderboard(player, ctx, StatsManager.Category.LEVEL);
            case ADMIN -> openAdmin(player, ctx);
        }
    }

    // ===== MAIN HUB =====
    public static void openMain(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.MAIN, player.getUniqueId());
        final Inventory inv = create(holder, 36, "<dark_aqua>» Főmenü «</dark_aqua>", ctx);

        put(inv, holder, 4, GuiUtil.icon(Material.NETHER_STAR, accent("IceSMP — Főmenü"),
                List.of(grey("Válassz egy rendszert."))), null);
        put(inv, holder, 10, GuiUtil.icon(Material.PLAYER_HEAD, title("Karakterlap"),
                List.of(grey("Kaszt, spec, szakma, talent, képesség-fa."), click())), "OPEN:profile");
        put(inv, holder, 11, GuiUtil.icon(Material.RED_BANNER, title("Frakció"),
                List.of(grey("Belépés, kassza, király, raid."), click())), "MENU:FACTION");
        put(inv, holder, 12, GuiUtil.icon(Material.GOLD_INGOT, title("Bank & Pénz"),
                List.of(grey("Egyenleg, befizetés, kivét, árfolyam."), click())), "MENU:BANK");
        put(inv, holder, 13, GuiUtil.icon(Material.CHEST, title("Piac"),
                List.of(grey("Vásárlás és eladás más játékosokkal."), click())), "OPEN:market");
        put(inv, holder, 14, GuiUtil.icon(Material.WRITTEN_BOOK, title("Küldetések"),
                List.of(grey("Feladatok felvétele és követése."), click())), "MENU:QUEST");
        put(inv, holder, 15, GuiUtil.icon(Material.CLOCK, title("Események"),
                List.of(grey("Szezon, vérhold, világboss."), click())), "MENU:EVENTS");
        put(inv, holder, 16, GuiUtil.icon(Material.TOTEM_OF_UNDYING, title("Relikviák"),
                List.of(grey("Legendás tárgyak és tulajdonosaik."), click())), "MENU:RELIC");
        put(inv, holder, 20, GuiUtil.icon(Material.SOUL_LANTERN, title("Lélekszilánk"),
                List.of(grey("Nekromanta erőforrás és bajnok-idézés."), click())), "MENU:SOULS");
        put(inv, holder, 22, GuiUtil.icon(Material.GOLDEN_HELMET, title("Ranglisták"),
                List.of(grey("Leggazdagabb, legmagasabb szint, raid-kill."), click())), "MENU:LEADERBOARD");
        if (player.hasPermission(ADMIN_PERMISSION)) {
            put(inv, holder, 23, GuiUtil.icon(Material.COMMAND_BLOCK, title("Admin"),
                    List.of(grey("Admin gyors-parancsok."), click())), "MENU:ADMIN");
        }
        put(inv, holder, 31, closeButton(), "CLOSE");

        player.openInventory(inv);
    }

    // ===== FACTION =====
    public static void openFaction(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.FACTION, player.getUniqueId());
        final Inventory inv = create(holder, 36, "<dark_aqua>» Frakció «</dark_aqua>", ctx);
        final FactionType faction = ctx.factionManager().getFaction(player.getUniqueId());

        final List<Component> headerLore = new ArrayList<>();
        headerLore.add(label("Frakciód", Component.text(faction == null ? "nincs" : faction.getDisplayName(), NamedTextColor.WHITE)));
        if (faction != null) {
            headerLore.add(label("Kassza", Component.text(ctx.currencyManager().formatBalance(ctx.treasuryManager().getBalance(faction)), NamedTextColor.WHITE)));
            headerLore.add(label("Adókulcs", Component.text(ctx.treasuryManager().getTaxRate(faction) + "%", NamedTextColor.WHITE)));
            final UUID kingId = ctx.kingManager().getKing(faction);
            headerLore.add(label("Király", Component.text(kingId == null ? "nincs" : nameOf(kingId), NamedTextColor.WHITE)));
            headerLore.add(label("Raid", Component.text(ctx.raidManager().isRaidActive() ? "folyamatban" : "nincs", NamedTextColor.WHITE)));
        }
        put(inv, holder, 4, GuiUtil.icon(Material.RED_BANNER, accent("Frakció"), headerLore), null);

        if (faction == null) {
            put(inv, holder, 11, GuiUtil.icon(Material.RED_WOOL, Component.text("Csatlakozás: Piros", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(grey("Tűz-immunitás."), click())), "RUN:faction join red");
            put(inv, holder, 13, GuiUtil.icon(Material.BLUE_WOOL, Component.text("Csatlakozás: Kék", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                    List.of(grey("Fagy-immunitás, lassabb éhség."), click())), "RUN:faction join blue");
            put(inv, holder, 15, GuiUtil.icon(Material.WHITE_WOOL, Component.text("Csatlakozás: Semleges", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    List.of(grey("Lopakodva láthatatlan."), click())), "RUN:faction join neutral");
        } else {
            put(inv, holder, 10, GuiUtil.icon(Material.GOLD_NUGGET, title("Adomány: 10"),
                    List.of(grey("10 token a frakciókasszába."), click())), "RUN:faction donate 10");
            put(inv, holder, 11, GuiUtil.icon(Material.GOLD_INGOT, title("Adomány: 50"),
                    List.of(grey("50 token a frakciókasszába."), click())), "RUN:faction donate 50");
            put(inv, holder, 12, GuiUtil.icon(Material.GOLD_BLOCK, title("Adomány: 100"),
                    List.of(grey("100 token a frakciókasszába."), click())), "RUN:faction donate 100");
            put(inv, holder, 14, GuiUtil.icon(Material.PAPER, title("Király & szavazás"),
                    List.of(grey("Aktuális király és szavazatok."), click())), "RUN:faction king");
            if (ctx.kingManager().isKing(player)) {
                put(inv, holder, 15, GuiUtil.icon(Material.DIAMOND, title("Kassza-kivét: 100"),
                        List.of(grey("Király: 100 token kivétele."), click())), "RUN:faction treasury withdraw 100");
                int raidSlot = 20;
                for (final FactionType target : FactionType.values()) {
                    if (target == faction || target == FactionType.NEUTRAL) {
                        continue;
                    }
                    put(inv, holder, raidSlot++, GuiUtil.icon(Material.NETHERITE_SWORD,
                            Component.text("Raid: " + target.getDisplayName(), NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
                            List.of(grey("Király: háború hirdetése."), click())), "RUN:faction raid " + target.name().toLowerCase());
                }
            }
            put(inv, holder, 30, GuiUtil.icon(Material.BARRIER, Component.text("Kilépés a frakcióból", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(grey("Elhagyod a jelenlegi frakciót."), click())), "RUN:faction leave");
        }

        put(inv, holder, 31, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== BANK =====
    public static void openBank(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.BANK, player.getUniqueId());
        final Inventory inv = create(holder, 36, "<dark_aqua>» Bank & Pénz «</dark_aqua>", ctx);

        final List<Component> balances = new ArrayList<>();
        for (final FactionType type : FactionType.values()) {
            balances.add(label(type.getDisplayName(), Component.text(
                    ctx.currencyManager().formatBalance(ctx.currencyManager().getBalance(player, type)), NamedTextColor.WHITE)));
        }
        put(inv, holder, 4, GuiUtil.icon(Material.GOLD_INGOT, accent("Egyenlegeid"), balances), null);

        put(inv, holder, 11, GuiUtil.icon(Material.HOPPER, title("Befizetés (összes)"),
                List.of(grey("A nálad lévő tokeneket bankba teszi."), click())), "RUN:bank deposit");
        final FactionType own = ctx.factionManager().getFaction(player.getUniqueId());
        final String cur = (own == null ? FactionType.NEUTRAL : own).name().toLowerCase();
        put(inv, holder, 13, GuiUtil.icon(Material.DROPPER, title("Kivét: 64 (saját valuta)"),
                List.of(grey("64 token kivétele itemként."), click())), "RUN:bank withdraw " + cur + " 64");
        put(inv, holder, 15, GuiUtil.icon(Material.PAPER, title("Árfolyamok"),
                List.of(grey("Aktuális valuta-értékek és váltási arány."), click())), "RUN:currency rates");
        put(inv, holder, 22, GuiUtil.icon(Material.COMPARATOR, title("Váltás (chat)"),
                List.of(grey("Használat: /currency exchange <összeg> <honnan> <hová>"))), null);

        put(inv, holder, 31, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== QUEST =====
    public static void openQuest(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.QUEST, player.getUniqueId());
        final Inventory inv = create(holder, 54, "<dark_aqua>» Küldetések «</dark_aqua>", ctx);
        put(inv, holder, 4, GuiUtil.icon(Material.WRITTEN_BOOK, accent("Küldetések"),
                List.of(grey("Felül: aktív • alul: felvehető."))), null);

        int activeSlot = 9;
        int availableSlot = 27;
        for (final String questId : ctx.questManager().getQuestIds()) {
            if (ctx.questManager().hasCompleted(player, questId)) {
                continue;
            }
            final String name = ctx.questManager().getDisplayName(questId);
            if (ctx.questManager().isActive(player, questId) && activeSlot < 18) {
                put(inv, holder, activeSlot++, GuiUtil.icon(Material.WRITABLE_BOOK,
                        Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                        List.of(
                                label("Haladás", Component.text(ctx.questManager().getProgress(player, questId)
                                        + "/" + ctx.questManager().getObjectiveCount(questId), NamedTextColor.WHITE)),
                                Component.empty(),
                                Component.text("» Kattints a feladáshoz", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                        ), true), "RUN:quest abandon " + questId);
            } else if (!ctx.questManager().isActive(player, questId)
                    && ctx.questManager().getAcceptBlocker(player, questId) == null && availableSlot < 45) {
                put(inv, holder, availableSlot++, GuiUtil.icon(Material.BOOK,
                        Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                        List.of(grey("Feladatok: " + ctx.questManager().getObjectiveCount(questId)), click())),
                        "RUN:quest accept " + questId);
            }
        }

        put(inv, holder, 49, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== EVENTS =====
    public static void openEvents(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.EVENTS, player.getUniqueId());
        final Inventory inv = create(holder, 27, "<dark_aqua>» Események «</dark_aqua>", ctx);

        final List<Component> standings = new ArrayList<>();
        for (final FactionType type : FactionType.values()) {
            standings.add(label(type.getDisplayName(), Component.text(ctx.seasonManager().getPoints(type) + " pont", NamedTextColor.WHITE)));
        }
        put(inv, holder, 11, GuiUtil.icon(Material.GOLDEN_HELMET, accent("Szezonális liga"), standings), "RUN:events season");
        put(inv, holder, 13, GuiUtil.icon(ctx.bloodMoonManager().isActive() ? Material.RED_DYE : Material.GRAY_DYE,
                accent("Vérhold"),
                List.of(label("Állapot", Component.text(ctx.bloodMoonManager().isActive() ? "AKTÍV" : "nyugalom",
                        ctx.bloodMoonManager().isActive() ? NamedTextColor.RED : NamedTextColor.GRAY)),
                        grey("Vérholdkor a szörnyek erősebbek."))), "RUN:events blood-moon");
        put(inv, holder, 15, GuiUtil.icon(Material.WITHER_SKELETON_SKULL, accent("Világboss"),
                List.of(grey("Időnként boss spawnol; a legyőző"), grey("frakciója kasszát és liga-pontot kap."))), null);

        put(inv, holder, 22, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== RELIC =====
    public static void openRelic(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.RELIC, player.getUniqueId());
        final Inventory inv = create(holder, 36, "<dark_aqua>» Relikviák «</dark_aqua>", ctx);
        put(inv, holder, 4, GuiUtil.icon(Material.TOTEM_OF_UNDYING, accent("Relikviák"),
                List.of(grey("Legendás tárgyak és állapotuk."))), null);

        int slot = 10;
        for (final RelicDefinition def : ctx.relicManager().getDefinitions()) {
            if (slot > 16 && slot < 19) {
                slot = 19;
            }
            if (slot > 25) {
                break;
            }
            final RelicOwnership ownership = ctx.relicManager().getOwnership(def.id());
            final Component status = ownership == null
                    ? Component.text("szabad — megszerezhető", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    : Component.text("tulajdonosa: " + nameOf(ownership.owner()), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
            put(inv, holder, slot++, GuiUtil.icon(def.material() == null ? Material.PAPER : def.material(),
                    Component.text(def.displayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                    List.of(status)), null);
        }

        put(inv, holder, 31, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== SOULS =====
    public static void openSouls(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.SOULS, player.getUniqueId());
        final Inventory inv = create(holder, 27, "<dark_aqua>» Lélekszilánk «</dark_aqua>", ctx);

        final boolean necromancer = ctx.specializationManager().getClassSpecialization(player) == SpecializationType.NECROMANCER;
        if (necromancer) {
            put(inv, holder, 11, GuiUtil.icon(Material.SOUL_LANTERN, accent("Lélekszilánkjaid"),
                    List.of(label("Mennyiség", Component.text(String.valueOf(ctx.soulShardManager().getShards(player)), NamedTextColor.WHITE)),
                            grey("Ellenség-ölésenként gyűlik."))), null);
            put(inv, holder, 15, GuiUtil.icon(Material.WITHER_SKELETON_SKULL, title("Bajnok idézése"),
                    List.of(grey("Megerősített Wither-csontváz bajnok."), click())), "RUN:souls champion");
        } else {
            put(inv, holder, 13, GuiUtil.icon(Material.BARRIER, Component.text("Csak Nekromantáknak", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(grey("Ez a rendszer a Nekromanta specé."))), null);
        }

        put(inv, holder, 22, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== ADMIN =====
    public static void openAdmin(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.ADMIN, player.getUniqueId());
        final Inventory inv = create(holder, 27, "<dark_aqua>» Admin «</dark_aqua>", ctx);

        if (!player.hasPermission(ADMIN_PERMISSION)) {
            put(inv, holder, 13, GuiUtil.icon(Material.BARRIER, Component.text("Nincs jogosultságod", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()), null);
        } else {
            put(inv, holder, 10, GuiUtil.icon(Material.COMMAND_BLOCK, title("Config újratöltés"),
                    List.of(grey("/icesmp reload"), click())), "RUN:icesmp reload");
            put(inv, holder, 12, GuiUtil.icon(Material.ITEM_FRAME, title("Árfolyamtábla lerakása"),
                    List.of(grey("/exchangeboard place"), click())), "RUN:exchangeboard place");
            put(inv, holder, 14, GuiUtil.icon(Material.SHEARS, title("Árfolyamtábla törlése"),
                    List.of(grey("/exchangeboard remove"), click())), "RUN:exchangeboard remove");
            put(inv, holder, 16, GuiUtil.icon(Material.ENDER_EYE, title("Intro újrajátszása"),
                    List.of(grey("/events intro"), click())), "RUN:events intro");
            put(inv, holder, 19, GuiUtil.icon(Material.RED_DYE, title("Vérhold: indítás"),
                    List.of(grey("/events bloodmoon start"), click())), "RUN:events bloodmoon start");
            put(inv, holder, 20, GuiUtil.icon(Material.GRAY_DYE, title("Vérhold: leállítás"),
                    List.of(grey("/events bloodmoon stop"), click())), "RUN:events bloodmoon stop");
            put(inv, holder, 22, GuiUtil.icon(Material.WITHER_SKELETON_SKULL, title("Világboss idézése"),
                    List.of(grey("/events worldboss"), click())), "RUN:events worldboss");
        }

        put(inv, holder, 25, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== LEADERBOARD =====
    public static void openLeaderboard(final Player player, final CommandMenuContext ctx, final StatsManager.Category category) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.LEADERBOARD, player.getUniqueId());
        final Inventory inv = create(holder, 54, "<dark_aqua>» Ranglisták «</dark_aqua>", ctx);

        put(inv, holder, 4, GuiUtil.icon(Material.GOLDEN_HELMET, accent("Ranglista: " + categoryName(category)),
                List.of(grey("Válts kategóriát a felső gombokkal."))), null);
        put(inv, holder, 0, categoryButton("Szint", category == StatsManager.Category.LEVEL), "LB:level");
        put(inv, holder, 1, categoryButton("Vagyon", category == StatsManager.Category.WEALTH), "LB:wealth");
        put(inv, holder, 2, categoryButton("Raid-kill", category == StatsManager.Category.RAID_KILLS), "LB:raidkills");

        final List<StatsManager.Entry> rows = ctx.statsManager().top(category, 10);
        int slot = 9;
        int rank = 1;
        for (final StatsManager.Entry row : rows) {
            final Component value = switch (category) {
                case LEVEL -> Component.text("Szint " + row.level(), NamedTextColor.WHITE);
                case WEALTH -> Component.text(ctx.currencyManager().formatBalance(row.wealth()), NamedTextColor.GOLD);
                case RAID_KILLS -> Component.text(row.raidKills() + " raid-kill", NamedTextColor.RED);
            };
            put(inv, holder, slot++, GuiUtil.icon(Material.PLAYER_HEAD,
                    Component.text("#" + rank + " " + row.name(), rank == 1 ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    List.of(value)), null);
            rank++;
        }
        if (rows.isEmpty()) {
            put(inv, holder, 22, GuiUtil.icon(Material.BARRIER, Component.text("Még nincs adat", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), List.of()), null);
        }

        put(inv, holder, 49, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    private static ItemStack categoryButton(final String name, final boolean selected) {
        return GuiUtil.icon(selected ? Material.NETHER_STAR : Material.PAPER,
                Component.text(name, selected ? NamedTextColor.AQUA : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                List.of(grey(selected ? "Kiválasztva" : "Kattints a váltáshoz")), selected);
    }

    private static String categoryName(final StatsManager.Category category) {
        return switch (category) {
            case LEVEL -> "Legmagasabb szint";
            case WEALTH -> "Leggazdagabb";
            case RAID_KILLS -> "Legtöbb raid-kill";
        };
    }

    // ===== helpers =====
    private static Inventory create(final CommandMenuHolder holder, final int size, final String title, final CommandMenuContext ctx) {
        final Inventory inv = Bukkit.createInventory(holder, size, MiniMessage.miniMessage().deserialize(title));
        holder.setInventory(inv);
        GuiUtil.fill(inv);
        return inv;
    }

    private static void put(final Inventory inv, final CommandMenuHolder holder, final int slot, final ItemStack item, final String action) {
        inv.setItem(slot, item);
        if (action != null) {
            holder.bind(slot, action);
        }
    }

    private static String nameOf(final UUID uuid) {
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
    }

    private static ItemStack backButton() {
        return GuiUtil.icon(Material.ARROW, Component.text("Vissza a főmenübe", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of());
    }

    private static ItemStack closeButton() {
        return GuiUtil.icon(Material.BARRIER, Component.text("Bezárás", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of());
    }

    private static Component title(final String text) {
        return Component.text(text, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false);
    }

    private static Component accent(final String text) {
        return Component.text(text, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
    }

    private static Component grey(final String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static Component label(final String key, final Component value) {
        return Component.text(key + ": ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(value);
    }

    private static Component click() {
        return Component.text("» Kattints", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }
}
