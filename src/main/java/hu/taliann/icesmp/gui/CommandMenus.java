package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.accent;
import static hu.taliann.icesmp.gui.GuiUtil.grey;
import static hu.taliann.icesmp.gui.GuiUtil.label;

import hu.taliann.icesmp.commands.ClaimCommand;
import hu.taliann.icesmp.commands.EventsCommand;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.DailyQuestManager;
import hu.taliann.icesmp.managers.PartyManager;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.relics.RelicOwnership;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The /menu command hub and every per-system sub-menu. Each menu shows live
 * info read from the managers; the action buttons delegate to the existing
 * commands (RUN/OPEN) so no gameplay logic is duplicated here.
 *
 * <p>Structure rules kept consistent across the menus: the header sits in slot 4,
 * the back/close button in the middle of the bottom row, actionable tiles carry a
 * "» Kattints" hint, and systems with their own full GUI (profile, market, quest
 * log, spellbook) are navigated to instead of re-implemented here.</p>
 */
public final class CommandMenus {

    // The buttons in the admin menu delegate to commands with their own permission
    // nodes — the menu must gate on the SAME nodes, otherwise a button would show
    // but its command would be denied (or a permitted admin would not see it).
    private static final String ADMIN_EVENTS_PERMISSION = "icesmp.admin.events";
    private static final String ADMIN_RELOAD_PERMISSION = "icesmp.admin.reload";
    private static final String ADMIN_EXCHANGEBOARD_PERMISSION = "icesmp.admin.exchangeboard";
    private static final String ADMIN_NPC_PERMISSION = "icesmp.admin.npc";
    private static final String ADMIN_QUEST_PERMISSION = "icesmp.admin.quest";

    private CommandMenus() {
    }

    public static void open(final Player player, final CommandMenuHolder.Menu menu, final CommandMenuContext ctx) {
        switch (menu) {
            case MAIN -> openMain(player, ctx);
            case FACTION -> openFaction(player, ctx);
            case FACTION_SWITCH -> openFactionSwitch(player, ctx);
            case BANK -> openBank(player, ctx);
            case EXCHANGE -> openExchange(player, ctx);
            case EVENTS -> openEvents(player, ctx);
            case RELIC -> openRelic(player, ctx);
            case SOULS -> openSouls(player, ctx);
            case LEADERBOARD -> openLeaderboard(player, ctx, StatsManager.Category.LEVEL);
            case ACHIEVEMENTS -> openAchievements(player, ctx);
            case PARTY -> openParty(player, ctx);
            case CLAIM -> openClaim(player, ctx);
            case BOUNTY -> openBounty(player, ctx);
            case ADMIN -> openAdmin(player, ctx);
        }
    }

    // ===== MAIN HUB =====
    public static void openMain(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.MAIN, player.getUniqueId());
        final Inventory inv = create(holder, 45, "<dark_aqua>» Főmenü «</dark_aqua>", ctx);

        put(inv, holder, 4, GuiUtil.icon(Material.NETHER_STAR, accent("IceSMP — Főmenü"),
                List.of(grey("Karakter • Közösség • Gazdaság"))), null);

        // Row: karakter & haladás
        put(inv, holder, 10, GuiUtil.playerHead(player, title("Karakterlap"),
                List.of(grey("Kaszt, spec, szakma, talent, képesség-fa."), click())), "OPEN:profile");
        put(inv, holder, 11, GuiUtil.icon(Material.ENCHANTED_BOOK, title("Varázskönyv"),
                List.of(grey("Feloldott spelljeid böngészése"), grey("és kiválasztása a Katalizátorra."), click())), "OPEN:spellbook");
        // Dinamikus menü: a Társ csak azoknak látszik, akiknek lehet társa.
        final SpecializationType spec = ctx.specializationManager().getClassSpecialization(player);
        if (spec == SpecializationType.BEAST_MASTER || spec == SpecializationType.NECROMANCER) {
            put(inv, holder, 12, GuiUtil.icon(Material.BONE, title("Társ"),
                    List.of(grey("Vadmester / Nekromanta társ:"), grey("befogás, idézés, szint — infó chatben."), click())), "OPEN:pet");
        }
        put(inv, holder, 13, GuiUtil.icon(Material.WRITTEN_BOOK, title("Küldetésnapló"),
                List.of(grey("Aktív, felvehető és teljesített"), grey("küldetések — lapozható napló."), click())), "OPEN:quest log");
        put(inv, holder, 14, dailyTile(player, ctx), "OPEN:daily");
        put(inv, holder, 15, GuiUtil.icon(Material.EMERALD, title("Elérések"),
                List.of(grey("Mérföldkövek és jutalmaik."), click())), "MENU:ACHIEVEMENTS");
        put(inv, holder, 16, GuiUtil.icon(Material.GOLDEN_HELMET, title("Ranglisták"),
                List.of(grey("Leggazdagabb, legmagasabb szint, raid-kill."), click())), "MENU:LEADERBOARD");

        // Row: közösség & világ
        put(inv, holder, 19, GuiUtil.icon(Material.RED_BANNER, title("Frakció"),
                List.of(grey("Belépés, kassza, király, raid."), click())), "MENU:FACTION");
        put(inv, holder, 20, GuiUtil.icon(Material.CAMPFIRE, title("Csapat (party)"),
                List.of(grey("Tagok, meghívás, közös XP és loot."), click())), "MENU:PARTY");
        put(inv, holder, 21, GuiUtil.icon(Material.GRASS_BLOCK, title("Birtok (claim)"),
                List.of(grey("Saját chunkok foglalása és védelme."), click())), "MENU:CLAIM");
        put(inv, holder, 22, GuiUtil.icon(Material.CLOCK, title("Események"),
                List.of(grey("Szezon, vérhold, világboss, karaván…"), click())), "MENU:EVENTS");
        put(inv, holder, 23, GuiUtil.icon(Material.CROSSBOW, title("Körözési lista"),
                List.of(grey("Bűnös játékosok fejpénzzel."), click())), "MENU:BOUNTY");
        put(inv, holder, 24, GuiUtil.icon(Material.TOTEM_OF_UNDYING, title("Relikviák"),
                List.of(grey("Legendás tárgyak és tulajdonosaik."), click())), "MENU:RELIC");
        // Dinamikus menü: a Lélekszilánk a Nekromanta specé — másnak nem jelenik meg.
        if (spec == SpecializationType.NECROMANCER) {
            put(inv, holder, 25, GuiUtil.icon(Material.SOUL_LANTERN, title("Lélekszilánk"),
                    List.of(grey("Nekromanta erőforrás és bajnok-idézés."), click())), "MENU:SOULS");
        }

        // Row: gazdaság & egyéb
        put(inv, holder, 28, GuiUtil.icon(Material.GOLD_INGOT, title("Bank & Pénz"),
                List.of(grey("Egyenleg, befizetés, kivét, árfolyam."), click())), "MENU:BANK");
        put(inv, holder, 29, GuiUtil.icon(Material.CHEST, title("Piac"),
                List.of(grey("Vásárlás és eladás más játékosokkal."), click())), "OPEN:market");
        put(inv, holder, 30, GuiUtil.icon(Material.HOPPER, title("Adomány-láda"),
                List.of(grey("Ingyenes, közösségi ajándéktár —"),
                        grey("tegyél bele vagy vegyél ki bármit."), click())), "OPEN:adomany");
        put(inv, holder, 31, GuiUtil.icon(Material.CRAFTING_TABLE, title("Recept-könyv"),
                List.of(grey("Szakma-receptek: tanult/zárolt lista,"),
                        grey("egyedi (rolled) craftok egy kattintással."), click())), "OPEN:profession recipes");
        if (hasAnyAdminAccess(player)) {
            put(inv, holder, 34, GuiUtil.icon(Material.COMMAND_BLOCK, title("Admin"),
                    List.of(grey("Admin gyors-parancsok."), click())), "MENU:ADMIN");
        }

        put(inv, holder, 40, closeButton(), "CLOSE");

        player.openInventory(inv);
    }

    /** The daily/weekly quest tile with live progress (main hub). */
    private static ItemStack dailyTile(final Player player, final CommandMenuContext ctx) {
        if (!ctx.dailyQuestManager().isEnabled()) {
            return GuiUtil.icon(Material.GRAY_DYE, title("Napi küldetés"), List.of(grey("A napi küldetések ki vannak kapcsolva.")));
        }

        final List<Component> lore = new ArrayList<>();
        final DailyQuestManager.Daily daily = ctx.dailyQuestManager().getActive();
        if (daily != null) {
            lore.add(label("Napi", Component.text(daily.name(), NamedTextColor.WHITE)));
            lore.add(label("Állás", ctx.dailyQuestManager().isDone(player)
                    ? Component.text("teljesítve ✔", NamedTextColor.GREEN)
                    : Component.text(ctx.dailyQuestManager().getProgress(player) + "/" + daily.amount(), NamedTextColor.WHITE)));
        }
        final DailyQuestManager.Daily weekly = ctx.dailyQuestManager().getActiveWeekly();
        if (weekly != null) {
            lore.add(label("Heti", Component.text(weekly.name(), NamedTextColor.WHITE)));
            lore.add(label("Állás", ctx.dailyQuestManager().isWeeklyDone(player)
                    ? Component.text("teljesítve ✔", NamedTextColor.GREEN)
                    : Component.text(ctx.dailyQuestManager().getWeeklyProgress(player) + "/" + weekly.amount(), NamedTextColor.WHITE)));
        }
        lore.add(label("Sorozat", Component.text(ctx.dailyQuestManager().getStreak(player) + " nap", NamedTextColor.WHITE)));
        lore.add(click());
        return GuiUtil.icon(Material.SUNFLOWER, title("Napi küldetés"), lore);
    }

    // ===== FACTION =====
    public static void openFaction(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.FACTION, player.getUniqueId());
        final Inventory inv = create(holder, 36, "<dark_aqua>» Frakció «</dark_aqua>", ctx);
        final FactionType faction = ctx.factionManager().getFaction(player.getUniqueId());

        final List<Component> headerLore = new ArrayList<>();
        headerLore.add(label("Frakciód", Component.text(faction == null ? "nincs" : faction.getDisplayName(), NamedTextColor.WHITE)));
        final UUID kingId = faction == null ? null : ctx.kingManager().getKing(faction);
        if (faction != null) {
            headerLore.add(label("Kassza", Component.text(ctx.currencyManager().formatBalance(ctx.treasuryManager().getBalance(faction)), NamedTextColor.WHITE)));
            headerLore.add(label("Adókulcs", Component.text(ctx.treasuryManager().getTaxRate(faction) + "%", NamedTextColor.WHITE)));
            headerLore.add(label("Király", Component.text(kingId == null ? "nincs" : nameOf(kingId), NamedTextColor.WHITE)));
            headerLore.add(label("Raid", Component.text(ctx.raidManager().isRaidActive() ? "folyamatban" : "nincs", NamedTextColor.WHITE)));
        }
        put(inv, holder, 4, GuiUtil.icon(Material.RED_BANNER, accent("Frakció"), headerLore), null);

        if (faction == null) {
            putJoinButtons(inv, holder, player, ctx, null, "Csatlakozás");
        } else {
            put(inv, holder, 10, GuiUtil.icon(Material.GOLD_NUGGET, title("Adomány: 10"),
                    List.of(grey("10 token a frakciókasszába."), click())), "RUN:faction donate 10");
            put(inv, holder, 11, GuiUtil.icon(Material.GOLD_INGOT, title("Adomány: 50"),
                    List.of(grey("50 token a frakciókasszába."), click())), "RUN:faction donate 50");
            put(inv, holder, 12, GuiUtil.icon(Material.GOLD_BLOCK, title("Adomány: 100"),
                    List.of(grey("100 token a frakciókasszába."), click())), "RUN:faction donate 100");
            final List<Component> kingLore = List.of(grey("Aktuális király és szavazatok."), click());
            put(inv, holder, 14, kingId == null
                    ? GuiUtil.icon(Material.PAPER, title("Király & szavazás"), kingLore)
                    : GuiUtil.playerHead(Bukkit.getOfflinePlayer(kingId), title("Király: " + nameOf(kingId)), kingLore),
                    "RUN:faction king");
            put(inv, holder, 16, GuiUtil.icon(Material.COMPASS, title("Frakcióváltás"),
                    List.of(grey("Átlépés egy másik frakcióba."), click())), "MENU:FACTION_SWITCH");
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

    // ===== FACTION SWITCH =====
    public static void openFactionSwitch(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.FACTION_SWITCH, player.getUniqueId());
        final Inventory inv = create(holder, 36, "<dark_aqua>» Frakcióváltás «</dark_aqua>", ctx);
        final FactionType current = ctx.factionManager().getFaction(player.getUniqueId());

        put(inv, holder, 4, GuiUtil.icon(Material.COMPASS, accent("Frakcióváltás"),
                List.of(label("Jelenlegi", Component.text(current == null ? "nincs" : current.getDisplayName(), NamedTextColor.WHITE)),
                        label("Váltás ára", Component.text(ctx.currencyManager().formatBalance(ctx.factionManager().getSwitchCost())
                                + " (jelenlegi valutádban)", NamedTextColor.GOLD)),
                        label("Várakozás két váltás közt", Component.text(
                                (long) ctx.factionManager().getSwitchCooldownHours() + " óra", NamedTextColor.WHITE)),
                        grey("Semlegesből bárhová, és a Sötétbe"),
                        grey("bárhonnan ingyenes a váltás."),
                        Component.text("A passzívák azonnal cserélődnek,", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("a Sötét paktum bűne viszont veled marad.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))), null);

        putJoinButtons(inv, holder, player, ctx, current, "Váltás");

        put(inv, holder, 31, backButton(), "MENU:FACTION");
        player.openInventory(inv);
    }

    /**
     * The four faction join/switch tiles (slots 10/12/14/16), skipping {@code exclude}.
     * Shared by the no-faction join view and the switch sub-menu; the join command
     * itself enforces the Dark faction's sinner requirement.
     */
    private static void putJoinButtons(final Inventory inv, final CommandMenuHolder holder, final Player player,
                                       final CommandMenuContext ctx, final FactionType exclude, final String prefix) {
        int slot = 10;
        for (final FactionType type : FactionType.values()) {
            if (type == exclude) {
                continue;
            }
            final ItemStack icon = switch (type) {
                case RED -> GuiUtil.icon(Material.RED_WOOL, Component.text(prefix + ": Piros", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        List.of(grey("Tűz-immunitás."), click()));
                case BLUE -> GuiUtil.icon(Material.BLUE_WOOL, Component.text(prefix + ": Kék", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                        List.of(grey("Fagy-immunitás, lassabb éhség."), click()));
                case NEUTRAL -> GuiUtil.icon(Material.WHITE_WOOL, Component.text(prefix + ": Semleges", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        List.of(grey("Esés-immunitás, adómentesség."), click()));
                case DARK -> GuiUtil.icon(Material.BLACK_WOOL, Component.text(prefix + ": Sötét", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                        List.of(grey("Wither-immunitás, élőholtak békéje."),
                                ctx.sinManager() != null && ctx.sinManager().isSinner(player)
                                        ? Component.text("A sötét paktum vár rád…", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)
                                        : Component.text("Feltétel: bűnös jelölés", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                                click()));
            };
            put(inv, holder, slot, icon, "RUN:faction join " + type.name().toLowerCase(Locale.ROOT));
            slot += 2;
        }
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
        final boolean capitalOnly = ctx.configManager().getBoolean("banking.capital-only", true);
        if (capitalOnly) {
            balances.add(Component.text("⚑ Banki ügyintézés csak fővárosban!", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        put(inv, holder, 4, GuiUtil.icon(Material.GOLD_INGOT, accent("Egyenlegeid"), balances), null);

        final Component capitalNote = grey(capitalOnly ? "Csak fővárosban működik!" : " ");
        put(inv, holder, 11, GuiUtil.icon(Material.HOPPER, title("Befizetés (összes)"),
                List.of(grey("A nálad lévő tokeneket bankba teszi."), capitalNote, click())), "RUN:bank deposit");
        final FactionType own = ctx.factionManager().getFaction(player.getUniqueId());
        final String cur = (own == null ? FactionType.NEUTRAL : own).name().toLowerCase();
        put(inv, holder, 13, GuiUtil.icon(Material.DROPPER, title("Kivét: 64 (saját valuta)"),
                List.of(grey("64 token kivétele itemként (KP)."), capitalNote, click())), "RUN:bank withdraw " + cur + " 64");
        put(inv, holder, 15, GuiUtil.icon(Material.PAPER, title("Árfolyamok"),
                List.of(grey("Aktuális valuta-értékek és váltási arány."), click())), "RUN:currency rates");

        // Valutaváltó: külön, interaktív almenü (honnan/hová választó + élő árfolyam + gyors összegek).
        put(inv, holder, 22, GuiUtil.icon(Material.EMERALD, title("Valutaváltó"),
                List.of(grey("Interaktív váltó: válaszd ki a forrás- és cél-"),
                        grey("valutát, és az élő árfolyamon válts (díjjal)."), capitalNote, click())),
                "MENU:EXCHANGE");

        put(inv, holder, 31, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== VALUTAVÁLTÓ =====
    /** Opens the interactive currency exchanger with the caller's own currency preselected as source. */
    public static void openExchange(final Player player, final CommandMenuContext ctx) {
        final FactionType own = ctx.factionManager().getFaction(player.getUniqueId());
        openExchange(player, ctx, own == null ? FactionType.NEUTRAL : own, null);
    }

    /**
     * Renders the currency exchanger for a chosen source/target pair. The selectors set the
     * pair; the quick-amount tiles delegate to {@code /currency exchange}. The pair is stored on
     * the holder so the listener can preserve it across the post-command refresh.
     */
    public static void openExchange(final Player player, final CommandMenuContext ctx,
                                    final FactionType from, final FactionType to) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.EXCHANGE, player.getUniqueId());
        holder.setExchangeSelection(from == null ? null : from.name(), to == null ? null : to.name());
        final Inventory inv = create(holder, 45, "<dark_aqua>» Valutaváltó «</dark_aqua>", ctx);

        final boolean capitalOnly = ctx.configManager().getBoolean("banking.capital-only", true);
        final List<Component> header = new ArrayList<>();
        for (final FactionType type : FactionType.values()) {
            header.add(label(type.getDisplayName(), Component.text(
                    ctx.currencyManager().formatBalance(ctx.currencyManager().getBalance(player, type)), NamedTextColor.WHITE)));
        }
        header.add(grey("Váltási díj: " + trimPercent(ctx.exchangeRateService().getFeePercent()) + "%"));
        if (capitalOnly) {
            header.add(Component.text("⚑ Váltás csak fővárosban!", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        put(inv, holder, 4, GuiUtil.icon(Material.GOLD_INGOT, accent("Egyenlegeid"), header), null);

        // Forrás-választók (felső sor): a kiválasztott ragyog.
        put(inv, holder, 9, GuiUtil.icon(Material.NAME_TAG, title("⬅ Honnan (miből)"),
                List.of(grey("Válaszd ki a forrás-valutát."))), null);
        final int[] fromSlots = {11, 12, 14, 15};
        final FactionType[] all = FactionType.values();
        for (int i = 0; i < all.length; i++) {
            final FactionType type = all[i];
            final boolean selected = type == from;
            put(inv, holder, fromSlots[i], GuiUtil.icon(woolFor(type),
                    title((selected ? "✔ " : "") + type.getDisplayName()),
                    List.of(grey("Forrás-valuta: " + type.getDisplayName()),
                            selected ? grey("Kiválasztva.") : click()), selected),
                    "XFROM:" + type.name());
        }

        // Cél-választók (alsó sor): a forrással azonos szürke és nem választható.
        put(inv, holder, 27, GuiUtil.icon(Material.NAME_TAG, title("➡ Hová (mire)"),
                List.of(grey("Válaszd ki a cél-valutát."))), null);
        final int[] toSlots = {29, 30, 32, 33};
        for (int i = 0; i < all.length; i++) {
            final FactionType type = all[i];
            if (type == from) {
                put(inv, holder, toSlots[i], GuiUtil.icon(Material.GRAY_STAINED_GLASS_PANE,
                        grey(type.getDisplayName() + " (a forrás)"), List.of(grey("Ez a forrás-valuta."))), null);
                continue;
            }
            final boolean selected = type == to;
            put(inv, holder, toSlots[i], GuiUtil.icon(woolFor(type),
                    title((selected ? "✔ " : "") + type.getDisplayName()),
                    List.of(grey("Cél-valuta: " + type.getDisplayName()),
                            selected ? grey("Kiválasztva.") : click()), selected),
                    "XTO:" + type.name());
        }

        // Árfolyam-kártya + gyors összegek (a középső/alsó sorban), csak ha van érvényes pár.
        if (from != null && to != null && from != to) {
            final CurrencyType cf = CurrencyType.fromFactionType(from);
            final CurrencyType ctto = CurrencyType.fromFactionType(to);
            final double rate = ctx.exchangeRateService().getRate(cf, ctto);
            final double fee = ctx.exchangeRateService().getFeePercent();
            final long balance = (long) ctx.currencyManager().getBalance(player, from);
            final List<Component> rateLore = new ArrayList<>();
            rateLore.add(label("1 " + from.getDisplayName(), Component.text(trimRate(rate) + " " + to.getDisplayName(), NamedTextColor.WHITE)));
            rateLore.add(grey("64 " + shortName(from) + " ≈ " + Math.round(64 * rate * (1.0D - fee / 100.0D)) + " " + shortName(to) + " (díj után)"));
            rateLore.add(grey("Egyenleged: " + balance + " " + shortName(from)));
            put(inv, holder, 22, GuiUtil.icon(Material.SUNFLOWER, accent(from.getDisplayName() + " → " + to.getDisplayName()), rateLore, true), null);

            final long[] amounts = {16, 32, 64};
            final int[] amountSlots = {38, 39, 40};
            final String fromId = from.name().toLowerCase(Locale.ROOT);
            final String toId = to.name().toLowerCase(Locale.ROOT);
            for (int i = 0; i < amounts.length; i++) {
                final long amount = amounts[i];
                put(inv, holder, amountSlots[i], GuiUtil.icon(Material.GOLD_NUGGET, title("Váltás: " + amount),
                        List.of(grey(amount + " " + shortName(from) + " → " + shortName(to)),
                                grey("az élő árfolyamon (díjjal)."), click())),
                        "RUN:currency exchange " + amount + " " + fromId + " " + toId);
            }
            if (balance > 0L) {
                put(inv, holder, 41, GuiUtil.icon(Material.GOLD_BLOCK, title("Váltás: mind (" + balance + ")"),
                        List.of(grey("A teljes " + shortName(from) + "-egyenleged váltása."), click())),
                        "RUN:currency exchange " + balance + " " + fromId + " " + toId);
            }
        } else {
            put(inv, holder, 22, GuiUtil.icon(Material.BARRIER, grey("Válassz forrás- és cél-valutát!"),
                    List.of(grey("Fent a forrás, lent a cél."))), null);
        }

        put(inv, holder, 36, GuiUtil.icon(Material.ARROW,
                Component.text("Vissza a bankba", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()), "MENU:BANK");
        player.openInventory(inv);
    }

    private static String shortName(final FactionType faction) {
        return switch (faction) {
            case RED -> "Piros";
            case BLUE -> "Kék";
            case NEUTRAL -> "Semleges";
            case DARK -> "Sötét";
        };
    }

    private static String trimRate(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String trimPercent(final double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static Material woolFor(final FactionType faction) {
        return switch (faction) {
            case RED -> Material.RED_WOOL;
            case BLUE -> Material.BLUE_WOOL;
            case NEUTRAL -> Material.WHITE_WOOL;
            case DARK -> Material.BLACK_WOOL;
        };
    }

    // ===== EVENTS =====
    public static void openEvents(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.EVENTS, player.getUniqueId());
        final Inventory inv = create(holder, 27, "<dark_aqua>» Események «</dark_aqua>", ctx);

        put(inv, holder, 0, whatsHappeningNowTile(ctx), "RUN:events status");

        final List<Component> standings = new ArrayList<>();
        for (final FactionType type : FactionType.values()) {
            standings.add(label(type.getDisplayName(), Component.text(ctx.seasonManager().getPoints(type) + " pont", NamedTextColor.WHITE)));
        }
        put(inv, holder, 4, GuiUtil.icon(Material.GOLDEN_HELMET, accent("Szezonális liga"), standings), "RUN:events season");

        final boolean bloodMoon = ctx.bloodMoonManager().isActive();
        put(inv, holder, 10, GuiUtil.icon(bloodMoon ? Material.RED_DYE : Material.GRAY_DYE, accent("Vérhold"),
                List.of(statusLine(bloodMoon, NamedTextColor.RED),
                        grey("Vérholdkor a szörnyek erősebbek.")), bloodMoon), "RUN:events blood-moon");

        final boolean boss = ctx.worldBossManager().isBossActive();
        put(inv, holder, 11, GuiUtil.icon(Material.WITHER_SKELETON_SKULL, accent("Világboss"),
                List.of(statusLine(boss, NamedTextColor.DARK_RED),
                        grey("Időnként boss spawnol; a legyőző"),
                        grey("frakciója kasszát és liga-pontot kap.")), boss), null);

        final boolean caravan = ctx.caravanManager().isActive();
        put(inv, holder, 12, GuiUtil.icon(caravan ? Material.EMERALD : Material.CHEST_MINECART, accent("Kereskedő-karaván"),
                List.of(statusLine(caravan, NamedTextColor.GOLD),
                        grey("Amíg a városban van, különleges"),
                        grey("áruit a karaván-boltban veheted.")), caravan), "RUN:events caravan");

        final boolean escort = ctx.escortManager().isActive();
        final List<Component> escortLore = new ArrayList<>();
        escortLore.add(statusLine(escort, NamedTextColor.YELLOW));
        escortLore.add(grey("Kísérd el a konvojt a célig —"));
        escortLore.add(grey("siker esetén bónusz-készlet nyílik."));
        if (ctx.escortManager().isBonusStockActive()) {
            escortLore.add(label("Bónusz-készlet", Component.text("elérhető!", NamedTextColor.GREEN)));
        }
        put(inv, holder, 13, GuiUtil.icon(Material.LEAD, accent("Karaván-kíséret"), escortLore, escort), null);

        final boolean abundance = ctx.abundanceManager().isActive();
        put(inv, holder, 14, GuiUtil.icon(Material.HAY_BLOCK, accent("Bőség-idő"),
                List.of(statusLine(abundance, NamedTextColor.GREEN),
                        grey("Amíg tart, a gyűjtögetés"),
                        grey("extra hozamot ad.")), abundance), null);

        final boolean challenge = ctx.serverChallengeManager().isActive();
        final List<Component> challengeLore = new ArrayList<>();
        challengeLore.add(statusLine(challenge, NamedTextColor.GOLD));
        if (challenge) {
            challengeLore.add(label("Cél", Component.text(String.valueOf(ctx.serverChallengeManager().describeGoal()), NamedTextColor.WHITE)));
            challengeLore.add(label("Állás", Component.text(ctx.serverChallengeManager().getProgress()
                    + " / " + ctx.serverChallengeManager().getTarget(), NamedTextColor.GOLD)));
        } else {
            challengeLore.add(grey("Közös cél az egész szervernek,"));
            challengeLore.add(grey("jutalommal a résztvevőknek."));
        }
        put(inv, holder, 15, GuiUtil.icon(Material.TARGET, accent("Szerver-kihívás"), challengeLore, challenge), null);

        final boolean meteor = ctx.meteorEventManager().isActive();
        put(inv, holder, 16, GuiUtil.icon(meteor ? Material.MAGMA_BLOCK : Material.COBBLESTONE, accent("Meteor"),
                List.of(statusLine(meteor, NamedTextColor.RED),
                        grey("Becsapódás után a kráter ritka"),
                        grey("ércekben gazdag — amíg ki nem hűl.")), meteor), null);

        final String buff = ctx.gatheringBuffManager().describeActive();
        put(inv, holder, 19, GuiUtil.icon(buff != null ? Material.GLOWSTONE_DUST : Material.GRAY_DYE,
                accent("Buff-óra"),
                List.of(label("Állapot", buff != null
                                ? Component.text(buff + " — AKTÍV", NamedTextColor.YELLOW)
                                : Component.text("nyugalom", NamedTextColor.GRAY)),
                        grey("Időnként bányász-/termés-/horgász-/"),
                        grey("XP-bónusz ablak nyílik mindenkinek.")), buff != null), null);

        put(inv, holder, 22, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    /** "Állapot: AKTÍV/nyugalom" lore line for the event tiles. */
    private static Component statusLine(final boolean active, final NamedTextColor activeColor) {
        return label("Állapot", Component.text(active ? "AKTÍV" : "nyugalom", active ? activeColor : NamedTextColor.GRAY));
    }

    /**
     * "Mi történik most?" info-ikon az Események almenü tetején: a lore élőben az
     * aktív világeseményeket sorolja fel — ugyanazt a formázót hívja, mint az
     * {@code /events status} parancs ({@link EventsCommand#activeEventLines}), hogy a
     * két felület sose térjen el egymástól. A {@code CommandMenuContext} nem hordoz
     * invasion/treasure/wild-hunt managert, ezért azokra null-t adunk át — a formázó
     * ezeket egyszerűen kihagyja, a többi eseményt viszont ugyanúgy mutatja.
     */
    private static ItemStack whatsHappeningNowTile(final CommandMenuContext ctx) {
        final List<String> lines = EventsCommand.activeEventLines(ctx.messageManager(),
                ctx.bloodMoonManager(), ctx.worldBossManager(), null, ctx.caravanManager(),
                ctx.gatheringBuffManager(), null, null, ctx.abundanceManager(),
                ctx.serverChallengeManager(), ctx.escortManager(), ctx.meteorEventManager());

        final List<Component> lore = new ArrayList<>();
        if (lines.isEmpty()) {
            lore.add(grey("Most éppen nyugalom van a vidéken."));
        } else {
            for (final String line : lines) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
        }
        lore.add(click());
        return GuiUtil.icon(Material.CLOCK, accent("Mi történik most?"), lore, !lines.isEmpty());
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

    // ===== PARTY =====
    public static void openParty(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.PARTY, player.getUniqueId());
        final Inventory inv = create(holder, 27, "<dark_aqua>» Csapat «</dark_aqua>", ctx);

        if (!ctx.partyManager().isEnabled()) {
            put(inv, holder, 13, GuiUtil.icon(Material.BARRIER,
                    Component.text("A party-rendszer ki van kapcsolva", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()), null);
            put(inv, holder, 22, backButton(), "MENU:MAIN");
            player.openInventory(inv);
            return;
        }

        put(inv, holder, 4, GuiUtil.icon(Material.CAMPFIRE, accent("Csapat"),
                List.of(label("Max létszám", Component.text(ctx.partyManager().getMaxSize() + " fő", NamedTextColor.WHITE)),
                        grey("Meghívás: /party invite <név>"),
                        grey("Csapat-chat: /p <üzenet>"))), null);

        final PartyManager.Party party = ctx.partyManager().getParty(player.getUniqueId());
        if (party == null) {
            put(inv, holder, 13, GuiUtil.icon(Material.GRAY_DYE,
                    Component.text("Nem vagy csapatban", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    List.of(grey("Hívj meg valakit: /party invite <név>"))), null);
        } else {
            int slot = 10;
            for (final UUID memberId : party.getMembers()) {
                if (slot > 16) {
                    break;
                }
                final boolean leader = memberId.equals(party.getLeader());
                final OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
                final boolean online = member.isOnline();
                put(inv, holder, slot++, GuiUtil.playerHead(member,
                        Component.text((leader ? "👑 " : "") + nameOf(memberId),
                                leader ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                        List.of(label("Állapot", Component.text(online ? "online" : "offline",
                                online ? NamedTextColor.GREEN : NamedTextColor.GRAY)))), null);
            }
            put(inv, holder, 19, GuiUtil.icon(Material.OAK_DOOR,
                    Component.text("Kilépés a csapatból", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(click())), "RUN:party leave");
            if (player.getUniqueId().equals(party.getLeader())) {
                put(inv, holder, 25, GuiUtil.icon(Material.TNT,
                        Component.text("Csapat feloszlatása", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false),
                        List.of(grey("Csak vezetőként."), click())), "RUN:party disband");
            }
        }

        put(inv, holder, 22, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== CLAIM =====
    public static void openClaim(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.CLAIM, player.getUniqueId());
        final Inventory inv = create(holder, 27, "<dark_aqua>» Birtok «</dark_aqua>", ctx);

        if (!ctx.claimManager().isEnabled()) {
            put(inv, holder, 13, GuiUtil.icon(Material.BARRIER,
                    Component.text("A claim-rendszer ki van kapcsolva", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()), null);
            put(inv, holder, 22, backButton(), "MENU:MAIN");
            player.openInventory(inv);
            return;
        }

        final double nextCost = ctx.claimManager().nextClaimCost(player.getUniqueId());
        final int usedColumns = ctx.claimManager().countColumns(player.getUniqueId());
        final List<Component> headerLore = new ArrayList<>();
        headerLore.add(label("Birtokaid", Component.text(ctx.claimManager().countClaims(player.getUniqueId())
                + " terület (" + usedColumns + " oszlop)", NamedTextColor.WHITE)));
        headerLore.add(label("Ingyenes keret", Component.text(
                Math.max(0, ctx.claimManager().freeColumns() - usedColumns) + " oszlop", NamedTextColor.WHITE)));
        headerLore.add(label("Köv. gyorsfoglalás ára", nextCost <= 0.0D
                ? Component.text("ingyenes", NamedTextColor.GREEN)
                : Component.text(ctx.currencyManager().formatBalance(nextCost) + " (saját valuta)", NamedTextColor.WHITE)));
        put(inv, holder, 4, GuiUtil.icon(Material.GRASS_BLOCK, accent("Birtok (claim)"), headerLore), null);

        put(inv, holder, 10, GuiUtil.icon(Material.GOLDEN_SHOVEL, title("Gyorsfoglalás"),
                List.of(grey("16×16 blokk lefoglalása körülötted"),
                        grey("(±20 blokk magasságban)."), click())), "RUN:claim");
        put(inv, holder, 11, GuiUtil.icon(Material.IRON_SHOVEL, title("Birtok feloldása"),
                List.of(grey("Feloldja azt a claimet, amiben állsz."), click())), "RUN:claim unclaim");
        put(inv, holder, 12, GuiUtil.icon(Material.MAP, title("Határok mutatása"),
                List.of(grey("Kirajzolja a környező claimek pereméit."), click())), "RUN:claim show");
        put(inv, holder, 13, GuiUtil.icon(Material.PAPER, title("Claimjeid listája"),
                List.of(grey("Chatben listázza a chunkjaidat."),
                        grey("Megbízott: /claim trust <név>"), click())), "OPEN:claim list");
        put(inv, holder, 14, GuiUtil.icon(Material.STICK, title("Terület: 1. sarok"),
                List.of(grey("A blokk, amin állsz, a kijelölés"), grey("első sarka."), click())), "RUN:claim pos1");
        put(inv, holder, 15, GuiUtil.icon(Material.BLAZE_ROD, title("Terület: 2. sarok"),
                List.of(grey("A blokk, amin állsz, a kijelölés"), grey("második sarka."), click())), "RUN:claim pos2");
        put(inv, holder, 16, GuiUtil.icon(Material.EMERALD, title("Terület foglalása"),
                List.of(grey("Blokk-pontos téglalap a két sarok"),
                        grey("között (az ár előre kiírva, elég)."), click())), "RUN:claim area");

        final double extendCost = ctx.claimManager().extendCostAt(player);
        final Component extendPrice = extendCost < 0.0D
                ? grey("Állj a saját claimedbe hozzá.")
                : label("Ár", Component.text(ctx.currencyManager().formatBalance(extendCost) + " / +5 blokk", NamedTextColor.GOLD));
        put(inv, holder, 19, GuiUtil.icon(Material.SCAFFOLDING, title("Magasítás (+5 blokk)"),
                List.of(grey("A claim tetejét emeli meg."), extendPrice, click())), "RUN:claim extend up");
        put(inv, holder, 20, GuiUtil.icon(Material.POINTED_DRIPSTONE, title("Mélyítés (+5 blokk)"),
                List.of(grey("A claim alját viszi lejjebb."), extendPrice, click())), "RUN:claim extend down");
        put(inv, holder, 21, GuiUtil.icon(Material.PLAYER_HEAD, title("Megbízottak kezelése"),
                List.of(grey("Megbízottak listája + megbízás."), click())), "OPEN:claim trustgui");

        put(inv, holder, 22, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== BOUNTY =====
    public static void openBounty(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.BOUNTY, player.getUniqueId());
        final Inventory inv = create(holder, 27, "<dark_aqua>» Körözési lista «</dark_aqua>", ctx);

        if (!ctx.configManager().getBoolean("factions.sins.bounty.enabled", true)) {
            put(inv, holder, 13, GuiUtil.icon(Material.BARRIER,
                    Component.text("A fejvadász-rendszer ki van kapcsolva", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()), null);
            put(inv, holder, 22, backButton(), "MENU:MAIN");
            player.openInventory(inv);
            return;
        }

        final int minSins = Math.max(1, ctx.configManager().getInt("factions.sins.bounty.min-sins", 3));
        final double perSin = Math.max(0.0D, ctx.configManager().getDouble("factions.sins.bounty.reward-per-sin", 25.0D));
        final CurrencyType configured = CurrencyType.fromInput(ctx.configManager().getString("factions.sins.bounty.currency", "NEUTRAL"));
        final CurrencyType currency = configured == null ? CurrencyType.fromFactionType(FactionType.NEUTRAL) : configured;

        put(inv, holder, 4, GuiUtil.icon(Material.CROSSBOW, accent("Körözési lista"),
                List.of(grey("Bűnös játékosok fejpénzzel —"),
                        grey("aki levadássza őket, jutalmat kap."))), null);

        // Same discovery rule as /bounty: online players at/above the sin threshold.
        final List<Player> wanted = new ArrayList<>();
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (ctx.sinManager().getSinCount(online) >= minSins) {
                wanted.add(online);
            }
        }
        wanted.sort(Comparator.comparingInt((Player target) -> ctx.sinManager().getSinCount(target)).reversed());

        if (wanted.isEmpty()) {
            put(inv, holder, 13, GuiUtil.icon(Material.GRAY_DYE,
                    Component.text("Jelenleg nincs körözött játékos", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), List.of()), null);
        } else {
            int slot = 9;
            for (final Player target : wanted) {
                if (slot > 17) {
                    break;
                }
                final int sins = ctx.sinManager().getSinCount(target);
                put(inv, holder, slot++, GuiUtil.playerHead(target,
                        Component.text(target.getName(), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        List.of(label("Bűnök", Component.text(String.valueOf(sins), NamedTextColor.WHITE)),
                                label("Fejpénz", Component.text(ctx.currencyManager().formatBalance(sins * perSin)
                                        + " " + currency.getDisplayName(), NamedTextColor.GOLD)))), null);
            }
        }

        put(inv, holder, 22, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    // ===== ADMIN =====
    public static void openAdmin(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.ADMIN, player.getUniqueId());
        final Inventory inv = create(holder, 54, "<dark_aqua>» Admin «</dark_aqua>", ctx);

        if (!hasAnyAdminAccess(player)) {
            put(inv, holder, 22, GuiUtil.icon(Material.BARRIER, Component.text("Nincs jogosultságod", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()), null);
            put(inv, holder, 49, backButton(), "MENU:MAIN");
            player.openInventory(inv);
            return;
        }

        put(inv, holder, 4, GuiUtil.icon(Material.COMMAND_BLOCK, accent("Admin panel"),
                List.of(grey("Felül: rendszer • középen: esemény-"),
                        grey("triggerek • alul: kezelő-listák."))), null);

        // Each button is gated on the permission node of the command it runs.
        if (player.hasPermission(ADMIN_RELOAD_PERMISSION)) {
            put(inv, holder, 10, GuiUtil.icon(Material.COMMAND_BLOCK, title("Config újratöltés"),
                    List.of(grey("/icesmp reload"), click())), "RUN:icesmp reload");
        }
        if (player.hasPermission(ADMIN_EXCHANGEBOARD_PERMISSION)) {
            put(inv, holder, 12, GuiUtil.icon(Material.ITEM_FRAME, title("Árfolyamtábla lerakása"),
                    List.of(grey("/exchangeboard place"), click())), "RUN:exchangeboard place");
            put(inv, holder, 13, GuiUtil.icon(Material.SHEARS, title("Árfolyamtábla törlése"),
                    List.of(grey("/exchangeboard remove"), click())), "RUN:exchangeboard remove");
        }
        if (player.hasPermission(ADMIN_EVENTS_PERMISSION)) {
            put(inv, holder, 16, GuiUtil.icon(Material.ENDER_EYE, title("Intro újrajátszása"),
                    List.of(grey("/events intro"), click())), "RUN:events intro");
            // Esemény-triggerek (két sor): minden világesemény kézzel indítható.
            put(inv, holder, 19, GuiUtil.icon(Material.RED_DYE, title("Vérhold: indítás"),
                    List.of(grey("/events bloodmoon start"), click())), "RUN:events bloodmoon start");
            put(inv, holder, 20, GuiUtil.icon(Material.GRAY_DYE, title("Vérhold: leállítás"),
                    List.of(grey("/events bloodmoon stop"), click())), "RUN:events bloodmoon stop");
            put(inv, holder, 21, GuiUtil.icon(Material.WITHER_SKELETON_SKULL, title("Világboss idézése"),
                    List.of(grey("/events worldboss"), click())), "RUN:events worldboss");
            put(inv, holder, 22, GuiUtil.icon(Material.ZOMBIE_HEAD, title("Invázió indítása"),
                    List.of(grey("/events invasion"), click())), "RUN:events invasion");
            put(inv, holder, 23, GuiUtil.icon(Material.EMERALD, title("Karaván: érkezés"),
                    List.of(grey("/events caravan arrive"), click())), "RUN:events caravan arrive");
            put(inv, holder, 24, GuiUtil.icon(Material.MINECART, title("Karaván: távozás"),
                    List.of(grey("/events caravan depart"), click())), "RUN:events caravan depart");
            put(inv, holder, 25, GuiUtil.icon(Material.BONE, title("Vad Hajsza indítása"),
                    List.of(grey("/events wild-hunt"), click())), "RUN:events wild-hunt");
            put(inv, holder, 28, GuiUtil.icon(Material.MAGMA_BLOCK, title("Meteor idézése"),
                    List.of(grey("/events meteor"), click())), "RUN:events meteor");
            put(inv, holder, 29, GuiUtil.icon(Material.CHEST, title("Kincs elrejtése"),
                    List.of(grey("/events treasure"), click())), "RUN:events treasure");
            put(inv, holder, 30, GuiUtil.icon(Material.GLOWSTONE_DUST, title("Buff-óra nyitása"),
                    List.of(grey("/events gathering"), click())), "RUN:events gathering");
            put(inv, holder, 31, GuiUtil.icon(Material.HAY_BLOCK, title("Bőség-idő indítása"),
                    List.of(grey("/events abundance"), click())), "RUN:events abundance");
            put(inv, holder, 32, GuiUtil.icon(Material.TARGET, title("Szerver-kihívás indítása"),
                    List.of(grey("/events challenge"), click())), "RUN:events challenge");
            put(inv, holder, 33, GuiUtil.icon(Material.LEAD, title("Kíséret indítása"),
                    List.of(grey("/events escort"), click())), "RUN:events escort");
            put(inv, holder, 34, GuiUtil.icon(Material.FIREWORK_STAR, title("Hangulat-esemény"),
                    List.of(grey("/events ambient"), click())), "RUN:events ambient");
        }
        // Kezelő-sor: pozíció-/lista-alapú admin műveletek, saját node-jaikra kapuzva.
        if (player.hasPermission(ClaimCommand.ADMIN_PERMISSION)) {
            put(inv, holder, 37, GuiUtil.icon(Material.IRON_SHOVEL, title("Claim törlése (itt)"),
                    List.of(grey("A claim törlése, amiben állsz."),
                            grey("/claim admin unclaim"), click())), "RUN:claim admin unclaim");
        }
        if (player.hasPermission(ADMIN_NPC_PERMISSION)) {
            put(inv, holder, 38, GuiUtil.icon(Material.PLAYER_HEAD, title("NPC-kötések listája"),
                    List.of(grey("/npcbind list — chatben."), click())), "OPEN:npcbind list");
        }
        if (player.hasPermission(ADMIN_QUEST_PERMISSION)) {
            put(inv, holder, 39, GuiUtil.icon(Material.WRITABLE_BOOK, title("Admin-questek listája"),
                    List.of(grey("/quest admin list — chatben."), click())), "OPEN:quest admin list");
        }

        put(inv, holder, 49, backButton(), "MENU:MAIN");
        player.openInventory(inv);
    }

    /** True when the player can use at least one admin menu button (per-command nodes). */
    private static boolean hasAnyAdminAccess(final Player player) {
        return player.hasPermission(ADMIN_RELOAD_PERMISSION)
                || player.hasPermission(ADMIN_EXCHANGEBOARD_PERMISSION)
                || player.hasPermission(ADMIN_EVENTS_PERMISSION)
                || player.hasPermission(ClaimCommand.ADMIN_PERMISSION)
                || player.hasPermission(ADMIN_NPC_PERMISSION)
                || player.hasPermission(ADMIN_QUEST_PERMISSION);
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
            put(inv, holder, slot++, GuiUtil.playerHead(Bukkit.getOfflinePlayer(row.uuid()),
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

    // ===== ACHIEVEMENTS =====
    public static void openAchievements(final Player player, final CommandMenuContext ctx) {
        final CommandMenuHolder holder = new CommandMenuHolder(CommandMenuHolder.Menu.ACHIEVEMENTS, player.getUniqueId());
        final Inventory inv = create(holder, 54, "<dark_aqua>» Elérések «</dark_aqua>", ctx);
        put(inv, holder, 4, GuiUtil.icon(Material.NETHER_STAR, accent("Elérések"),
                List.of(grey("Mérföldkövek — automatikusan teljesülnek, jutalommal."))), null);

        int slot = 9;
        for (final hu.taliann.icesmp.managers.AchievementManager.Achievement achievement : ctx.achievementManager().getAchievements()) {
            if (slot > 44) {
                break;
            }
            final boolean earned = ctx.achievementManager().isEarned(player, achievement.id());
            final double value = ctx.achievementManager().metricValue(player, achievement.metric());
            final List<Component> lore = new ArrayList<>();
            lore.add(grey(achievement.description()));
            lore.add(label("Haladás", Component.text(
                    Math.min((long) value, (long) achievement.threshold()) + "/" + (long) achievement.threshold(),
                    earned ? NamedTextColor.GREEN : NamedTextColor.WHITE)));
            lore.add(label("Jutalom", Component.text(achievement.reward() + " valuta", NamedTextColor.GOLD)));
            lore.add(earned
                    ? Component.text("✔ Teljesítve", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    : Component.text("Folyamatban…", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            put(inv, holder, slot++, GuiUtil.icon(earned ? Material.LIME_DYE : Material.GRAY_DYE,
                    Component.text(achievement.name(), earned ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    lore, earned), null);
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

    private static Component click() {
        return Component.text("» Kattints", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }
}
