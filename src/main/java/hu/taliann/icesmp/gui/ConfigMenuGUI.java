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

/**
 * Ingame admin config-menü (jog: {@code icesmp.admin.config}): kategóriákra osztott,
 * kattintható felület a legfontosabb, élőben olvasott config-kulcsokhoz. A menü a
 * meglévő override-mechanizmusra épül (a data-folder config.yml-be ír, amit a
 * ConfigManager UTOLSÓKÉNT fésül be — így minden kulcsot felülír), tehát minden
 * módosítás restart nélkül él. A teljes kulcskészlethez továbbra is a
 * {@code /icesmp config set|get|find} a felület; ez a menü a kurátort listát adja.
 *
 * <p>Kezelés: BOOLEAN — katt = váltás; SZÁM — bal katt +lépés, jobb katt −lépés,
 * SHIFT = ötszörös lépés; CYCLE — katt = következő opció.
 */
public final class ConfigMenuGUI {

    /** Egy szerkeszthető kulcs a menüben. */
    public record Entry(String key, String label, EntryType type, double step, double min, double max,
                        List<String> options) {
        static Entry toggle(final String key, final String label) {
            return new Entry(key, label, EntryType.TOGGLE, 0, 0, 0, List.of());
        }

        static Entry number(final String key, final String label, final double step, final double min, final double max) {
            return new Entry(key, label, EntryType.NUMBER, step, min, max, List.of());
        }

        static Entry integer(final String key, final String label, final int step, final int min, final int max) {
            return new Entry(key, label, EntryType.INTEGER, step, min, max, List.of());
        }

        static Entry cycle(final String key, final String label, final List<String> options) {
            return new Entry(key, label, EntryType.CYCLE, 0, 0, 0, options);
        }
    }

    public enum EntryType { TOGGLE, NUMBER, INTEGER, CYCLE }

    /** Egy kategória: cím, ikon és a kurátort kulcs-lista. */
    public record Category(String id, String title, Material icon, List<Entry> entries) {
    }

    /** A kurátort katalógus — kategóriánként a leggyakrabban hangolt, élőben olvasott kulcsok. */
    public static final Map<String, Category> CATEGORIES = buildCatalog();

    private static Map<String, Category> buildCatalog() {
        final Map<String, Category> categories = new LinkedHashMap<>();
        categories.put("hp", new Category("hp", "HP-rendszer", Material.GOLDEN_APPLE, List.of(
                Entry.toggle("health.enabled", "Kaszt-HP-profilok bekapcsolva"),
                Entry.toggle("health.display.normalize", "Szívsor-normalizálás (10 szív)"),
                Entry.toggle("health.ooc-regen.enabled", "Harcon kívüli regen"),
                Entry.number("health.ooc-regen.delay-seconds", "Regen-késleltetés (mp)", 1, 0, 60),
                Entry.number("health.ooc-regen.percent-per-tick", "Regen üteme (%/2mp)", 1, 0, 25),
                Entry.number("health.ooc-regen.min-food", "Regen étel-küszöb", 1, 0, 20),
                Entry.toggle("health.scale-heals", "Gyógyítás-skálázás (maxHP/20)"),
                Entry.number("health.scale-heals-cap", "Gyógyítás-skála plafon", 0.25, 1, 5)
        )));
        categories.put("ado", new Category("ado", "Adó és gazdaság", Material.GOLD_INGOT, List.of(
                Entry.toggle("factions.tax.enabled", "Adó bekapcsolva"),
                Entry.number("factions.tax.rate-percent", "Adókulcs (%)", 0.5, 0, 100),
                Entry.number("factions.tax.minimum-amount", "Fejadó (min. összeg)", 0.5, 0, 1000),
                Entry.number("factions.tax.max-arrears", "Hátralék-plafon", 5, 0, 100000),
                Entry.integer("factions.tax.evasion-strikes", "Adócsalás-strike küszöb", 1, 0, 50),
                Entry.integer("factions.tax.interval-minutes", "Beszedés (perc)", 5, 1, 100000),
                Entry.toggle("ferry.enabled", "Kompjáratok"),
                Entry.number("ferry.default-fee", "Komp-viteldíj (alap)", 5, 0, 10000),
                Entry.toggle("factions.council.enabled", "Vének Tanácsa (NEUTRAL)"),
                Entry.number("factions.council.withdraw-daily-cap", "Tanácsi kassza-keret/nap", 50, 0, 100000),
                Entry.integer("factions.council.market-week-minutes", "Vásár-hét hossza (perc)", 15, 5, 10080))));
        categories.put("motd", new Category("motd", "Szerverlista és MOTD", Material.OAK_SIGN, List.of(
                Entry.toggle("motd.enabled", "Natív MOTD bekapcsolva"),
                Entry.cycle("motd.selection-mode", "Variáns-választás", List.of("time", "random")),
                Entry.integer("motd.rotation-seconds", "Rotációs ablak (mp)", 5, 2, 86400),
                Entry.toggle("motd.exclude-vanished-from-online-count", "Vanish játékosok kihagyása"),
                Entry.cycle("motd.icons.mode", "Ikonmód", List.of("none", "default", "variant", "random"))
        )));
        categories.put("esemenyek", new Category("esemenyek", "Világesemények", Material.DRAGON_HEAD, List.of(
                Entry.toggle("world-events.spawn-rules-enabled", "Spawn-védelem mester-kapcsoló"),
                Entry.toggle("world-events.orchestration.enabled", "Esemény-orchestráció (1 nagy esemény egyszerre)"),
                Entry.toggle("world-events.blood-moon.enabled", "Vérhold"),
                Entry.number("world-events.blood-moon.chance-percent", "Vérhold esély (%)", 5, 0, 100),
                Entry.toggle("world-events.world-boss.enabled", "Világboss"),
                Entry.number("world-events.world-boss.chance-percent", "Világboss esély (%)", 5, 0, 100),
                Entry.toggle("world-events.invasion.enabled", "Invázió"),
                Entry.toggle("wild-hunt.enabled", "Vad Hajsza"),
                Entry.toggle("meteor.enabled", "Meteor"),
                Entry.toggle("treasure-events.enabled", "Elrejtett kincs"),
                Entry.toggle("ambient-events.enabled", "Hangulat-események"))));
        categories.put("karhozat", new Category("karhozat", "Kárhozat-zóna és mob-szabályok", Material.WITHER_SKELETON_SKULL, List.of(
                Entry.toggle("territory.doom-gate.sin-exempt", "Ölés nem bűn a zónában"),
                Entry.integer("territory.doom-gate.entry-grace-seconds", "Belépő-védelem (mp)", 1, 0, 120),
                Entry.integer("territory.mob-rules.doom-gate.bonus-levels", "Zóna mob-bónusz szint", 1, 0, 20),
                Entry.toggle("territory.mob-rules.doom-gate.no-daylight-burn", "Mob nappal sem ég"),
                Entry.toggle("territory.mob-rules.doom-gate.no-zombification", "Mob nem zombisodik"),
                Entry.toggle("nether-portal.allow-creation", "Nether-portál gyújtás engedett"),
                Entry.toggle("mob-scaling.zone-ramp.enabled", "Zóna-rámpás mob-szint"),
                Entry.integer("mob-scaling.zone-ramp.blocks-per-level", "Rámpa (blokk/szint)", 50, 1, 5000))));
        categories.put("killjutalom", new Category("killjutalom", "Kill-jutalom szűrők", Material.ROTTEN_FLESH, List.of(
                Entry.toggle("kill-rewards.afk-block", "AFK-jelölt ölése nem fizet"),
                Entry.toggle("kill-rewards.exclude-spawner-mobs", "Spawner-mob nem dob lootot/pénzt"),
                Entry.toggle("kill-rewards.exclude-minions", "Saját minion ölése nem fizet"),
                Entry.toggle("kill-rewards.require-survival", "Csak survival gyilkos kap jutalmat"))));
        categories.put("hadiablak", new Category("hadiablak", "Hadi-ablak", Material.IRON_SWORD, List.of(
                Entry.toggle("factions.war-window.enabled", "Hadi-ablak (RED↔BLUE ölés nem bűn)"),
                Entry.integer("factions.war-window.points-per-kill", "Liga-pont ölésenként", 1, 0, 100),
                Entry.integer("factions.war-window.daily-point-cap", "Napi pont-plafon/fő", 1, 0, 100),
                Entry.integer("factions.war-window.per-victim-cooldown-minutes", "Per-áldozat cooldown (perc)", 5, 0, 1440))));
        categories.put("suttogok", new Category("suttogok", "Suttogók", Material.ECHO_SHARD, List.of(
                Entry.toggle("factions.whisper.enabled", "Suttogó-rendszer"),
                Entry.number("factions.whisper.suspicion-threshold", "Leleplezés-küszöb", 5, 1, 10000),
                Entry.number("factions.whisper.betrayal-suspicion", "Árulás-gyanú", 5, 0, 1000),
                Entry.number("factions.whisper.accuse-suspicion", "Tanú-vád gyanú", 5, 0, 1000),
                Entry.integer("factions.whisper.decay-minutes", "Csillapodás (perc)", 1, 1, 100000),
                Entry.integer("factions.whisper.exposure-sins", "Leleplezés bűn-terhe", 1, 1, 20),
                Entry.toggle("factions.whisper.expose-broadcast", "Leleplezés-broadcast"),
                Entry.toggle("factions.whisper.night-undead-truce", "Éjszakai élőhalott-békesség"),
                Entry.integer("cultists.whisper-loot-rolls", "Kult-loot részesedés (guríts)", 1, 0, 10),
                Entry.number("factions.whisper.blackmarket-discount-percent", "Feketepiac-kedvezmény (%)", 5, 0, 90))));
        categories.put("etelek", new Category("etelek", "Frakció-ételek (honvágy)", Material.COOKED_SALMON, List.of(
                Entry.toggle("factions.food-duty.enabled", "Honvágy-kötelezettség"),
                Entry.integer("factions.food-duty.grace-hours", "Türelmi idő (óra)", 1, 1, 100000),
                Entry.integer("factions.food-duty.check-minutes", "Ellenőrzés (perc)", 1, 1, 100000),
                Entry.integer("factions.food-duty.debuff-seconds", "Debuff hossza (mp)", 1, 1, 600))));
        categories.put("signature", new Category("signature", "Signature-perkek", Material.DIAMOND_PICKAXE, List.of(
                Entry.number("signature.csakany.bonus-drop-chance", "Csákány bónusz-esély", 0.05, 0, 1),
                Entry.number("signature.horgaszbot.bonus-drop-chance", "Horgászbot bónusz-esély", 0.05, 0, 1),
                Entry.number("signature.bankbetet.value", "Bankbetét értéke", 5, 0, 100000),
                Entry.integer("signature.szarvas.cooldown-seconds", "Szellemszarvas cooldown (mp)", 10, 0, 100000),
                Entry.number("signature.agyar.damage-mult", "Agyar sebzés-szorzó", 0.05, 1, 5),
                Entry.number("signature.jegvert.damage-mult", "Jégvért bejövő-szorzó", 0.05, 0, 1))));
        categories.put("relikviak", new Category("relikviak", "Relikviák", Material.ELYTRA, List.of(
                Entry.toggle("relics.enabled", "Relikvia-rendszer"),
                Entry.integer("relics.inactivity.expiry-days", "Inaktivitás-lejárat (nap)", 1, 0, 3650),
                Entry.integer("relics.inactivity.lost-expiry-days", "Elveszett-lejárat (nap)", 1, 0, 3650),
                Entry.cycle("relics.passive-death.mode", "Passzív relikvia halálkor",
                        List.of("reclaim", "keep", "drop")),
                Entry.toggle("relics.wings.faction-locked-pickup", "Szárny frakció-zár (felvétel)"),
                Entry.toggle("relics.pvp-transfer.enabled", "Fegyver-relikvia PvP-átvétel"))));
        categories.put("devitemek", new Category("devitemek", "DEV itemek", Material.HEART_OF_THE_SEA, List.of(
                Entry.toggle("dev-items.csodalatos_bingulus.auto-restore", "Bingulus automatikus visszaállítása"),
                Entry.integer("dev-items.csodalatos_bingulus.reward-interval-seconds", "Jutalom-időköz (mp)", 60, 60, 86400),
                Entry.number("dev-items.csodalatos_bingulus.rarity-weights.kozonseges", "Közönséges súly", 1, 0, 10000),
                Entry.number("dev-items.csodalatos_bingulus.rarity-weights.nem_mindennapi", "Nem mindennapi súly", 1, 0, 10000),
                Entry.number("dev-items.csodalatos_bingulus.rarity-weights.ritka", "Ritka súly", 0.5, 0, 10000),
                Entry.number("dev-items.csodalatos_bingulus.rarity-weights.epikus", "Epikus súly", 0.1, 0, 10000),
                Entry.number("dev-items.csodalatos_bingulus.rarity-weights.legendas", "Legendás súly", 0.1, 0, 10000),
                Entry.number("dev-items.csodalatos_bingulus.rarity-weights.ereklye", "Ereklye súly", 0.1, 0, 10000),
                Entry.integer("dev-items.csodalatos_bingulus.pity.ritka.after-rolls", "Ritka pity (sorsolás)", 5, 1, 100000),
                Entry.integer("dev-items.csodalatos_bingulus.pity.epikus.after-rolls", "Epikus pity (sorsolás)", 10, 1, 100000),
                Entry.integer("dev-items.csodalatos_bingulus.pity.legendas.after-rolls", "Legendás pity (sorsolás)", 50, 1, 100000))));
        categories.put("emlek", new Category("emlek", "Emlékszilánkok", Material.AMETHYST_SHARD, List.of(
                Entry.integer("memory-shards.xp-amount", "XP-csomag mérete", 50, 1, 1000000),
                Entry.integer("memory-shards.costs.xp", "XP-beváltás ára (szilánk)", 1, 1, 100),
                Entry.integer("memory-shards.costs.talent", "Talentpont ára", 1, 1, 100),
                Entry.integer("memory-shards.costs.spec", "Spec-kapu ára", 1, 1, 100))));
        categories.put("liga", new Category("liga", "Szezon-liga (aszimmetrikus)", Material.GOLDEN_HELMET, List.of(
                Entry.toggle("world-events.season.enabled", "Szezon-liga"),
                Entry.integer("world-events.season.length-days", "Szezon hossza (nap)", 5, 1, 3650),
                Entry.integer("world-events.season-finale.top2-window-hours", "Nagydöntő-ablak (óra)", 6, 1, 720),
                Entry.number("world-events.season-finale.top2-point-multiplier", "Nagydöntő pont-szorzó", 0.25, 1, 10),
                Entry.integer("community-goals.season-points", "Közösségi cél pontja", 1, 0, 1000),
                Entry.integer("corruption.season-points", "Rontás-tisztítás pontja", 1, 0, 1000),
                Entry.integer("honor-duel.season-points", "Párbaj-győzelem pontja", 1, 0, 1000),
                Entry.integer("spy.season-points", "Kém-küldetés pontja", 1, 0, 1000))));
        categories.put("rontas", new Category("rontas", "Rontás-zóna", Material.SCULK_CATALYST, List.of(
                Entry.toggle("corruption.enabled", "Rontás-góc"),
                Entry.integer("corruption.interval-minutes", "Sorsolás-időköz (perc)", 10, 1, 100000),
                Entry.number("corruption.chance-percent", "Nyílás-esély (%)", 5, 0, 100),
                Entry.integer("corruption.mob-cap", "Korrupt mob-plafon", 1, 1, 100),
                Entry.integer("corruption.purge-kills-required", "Tisztításhoz kell (kill)", 1, 1, 1000),
                Entry.number("corruption.dark-bias.chance-percent", "DARK-perem esély (%)", 5, 0, 100),
                Entry.integer("corruption.dark-bias.min-edge-distance", "DARK-perem min. táv", 4, 4, 1000),
                Entry.integer("corruption.dark-bias.max-edge-distance", "DARK-perem max. táv", 8, 4, 2000))));
        categories.put("darknep", new Category("darknep", "DARK-népesség és ritka variánsok", Material.ZOMBIE_HEAD, List.of(
                Entry.toggle("dark-undead.enabled", "DARK undead-népesség"),
                Entry.cycle("dark-undead.scope", "Hatókör", List.of("capital", "all")),
                Entry.integer("dark-undead.max-population", "Populáció-plafon", 2, 1, 200),
                Entry.integer("dark-undead.spawn-interval-seconds", "Pótlás-időköz (mp)", 5, 5, 3600),
                Entry.integer("dark-undead.min-level", "Min. szint", 1, 1, 50),
                Entry.integer("dark-undead.max-level", "Max. szint", 1, 1, 50),
                Entry.integer("dark-undead.lifespan-seconds", "Élettartam (mp)", 60, 60, 86400),
                Entry.number("rare-variant.chance-percent", "Ritka variáns esély (%)", 0.25, 0, 100),
                Entry.number("rare-variant.xp-multiplier", "Variáns XP-szorzó", 0.25, 1, 10),
                Entry.number("rare-variant.soul-chance-multiplier", "Variáns lélekkő-szorzó", 0.25, 1, 10))));
        categories.put("cehek", new Category("cehek", "Céhek és szakma-hét", Material.WHITE_BANNER, List.of(
                Entry.toggle("guilds.enabled", "Céh-rendszer"),
                Entry.number("guilds.create-cost", "Alapítás ára", 25, 0, 100000),
                Entry.integer("guilds.base-max-members", "Alap-taglétszám", 1, 1, 100),
                Entry.integer("guilds.max-members-cap", "Taglétszám-plafon", 1, 1, 200),
                Entry.integer("guilds.xp-per-quest", "Céh-XP questenként", 1, 0, 1000),
                Entry.toggle("profession-weekly.enabled", "Szakma-céh heti cél"),
                Entry.integer("profession-weekly.reward-xp", "Heti cél jutalom-XP", 25, 0, 100000),
                Entry.integer("profession-weekly.min-contribution", "Jutalom-küszöb (egység)", 25, 1, 1000000))));
        categories.put("parbajkem", new Category("parbajkem", "Párbaj és kém-álca", Material.IRON_SWORD, List.of(
                Entry.toggle("honor-duel.enabled", "Becsület-párbaj"),
                Entry.integer("honor-duel.window-seconds", "Párbaj-ablak (mp)", 30, 30, 3600),
                Entry.integer("honor-duel.weekly-limit", "Heti párbaj-limit", 1, 1, 50),
                Entry.toggle("spy.enabled", "Kém-álca"),
                Entry.integer("spy.duration-seconds", "Álca hossza (mp)", 10, 10, 3600),
                Entry.integer("spy.cooldown-minutes", "Álca cooldown (perc)", 1, 1, 100000))));
        categories.put("ules", new Category("ules", "Ülés", Material.OAK_STAIRS, List.of(
                Entry.toggle("sit.enabled", "Natív ülés"),
                Entry.toggle("sit.click-to-sit", "Kattintásos ülés"),
                Entry.toggle("sit.empty-hand-only", "Csak üres főkézzel"),
                Entry.number("sit.max-click-distance", "Max. kattintási távolság", 0.5, 1, 16),
                Entry.toggle("sit.allow-unsafe-locations", "Veszélyes hely engedélyezése"),
                Entry.toggle("sit.stand-up.damage", "Sebzésre feláll"),
                Entry.toggle("sit.stand-up.sneak", "Lopakodásra feláll"),
                Entry.toggle("sit.stand-up.block-break", "Blokktörésre feláll"))));
        categories.put("borze", new Category("borze", "Börze és városi őrség", Material.EMERALD, List.of(
                Entry.toggle("market.allow-relic-listing", "Relikvia listázható (börze)"),
                Entry.number("market.relic-auction.recommended-min-bid", "Börze ajánlott minimuma", 25, 0, 1000000),
                Entry.toggle("city-guards.enabled", "Városi őrség"),
                Entry.integer("city-guards.step-seconds", "Őr-léptetés (mp)", 1, 1, 60),
                Entry.number("city-guards.day-step-blocks", "Nappali lépés (blokk)", 0.5, 0.5, 16),
                Entry.number("city-guards.night-step-blocks", "Éjjeli lépés (blokk)", 0.5, 0.5, 16))));
        return categories;
    }

    private ConfigMenuGUI() {
    }

    /** A főmenü (kategória-választó) megnyitása. */
    public static void openRoot(final Player player) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), null);
        // 36 slot: 3 sor kategória-rács (7/sor, szélek üresen) — 21 kategóriáig elég.
        final Inventory inventory = Bukkit.createInventory(holder, 36,
                Component.text("⚙ IceSMP Config", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        int slot = 10;
        for (final Category category : CATEGORIES.values()) {
            inventory.setItem(slot, tile(category.icon(), "&b" + category.title(),
                    List.of("&7" + category.entries().size() + " kulcs", "&eKattints a megnyitáshoz")));
            holder.bind(slot, "CAT:" + category.id());
            slot++;
            if (slot == 17) {
                slot = 19;
            } else if (slot == 26) {
                slot = 28;
            }
        }
        inventory.setItem(35, tile(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(35, "CLOSE");
        player.openInventory(inventory);
    }

    /** Egy kategória-lap megnyitása (a kulcsok aktuális értékével). */
    public static void openCategory(final Player player, final String categoryId, final ConfigManager configManager) {
        final Category category = CATEGORIES.get(categoryId);
        if (category == null) {
            openRoot(player);
            return;
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), categoryId);
        final Inventory inventory = Bukkit.createInventory(holder, 36,
                Component.text("⚙ " + category.title(), NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        int slot = 0;
        for (final Entry entry : category.entries()) {
            inventory.setItem(slot, entryTile(entry, configManager));
            holder.bind(slot, switch (entry.type()) {
                case TOGGLE -> "TOGGLE:" + entry.key();
                case CYCLE -> "CYCLE:" + entry.key();
                default -> "NUM:" + entry.key();
            });
            slot++;
        }
        inventory.setItem(31, tile(Material.ARROW, "&7Vissza", List.of()));
        holder.bind(31, "BACK");
        inventory.setItem(35, tile(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(35, "CLOSE");
        player.openInventory(inventory);
    }

    private static ItemStack entryTile(final Entry entry, final ConfigManager configManager) {
        final List<String> lore = new ArrayList<>();
        lore.add("&8" + entry.key());
        switch (entry.type()) {
            case TOGGLE -> {
                final boolean value = configManager.getBoolean(entry.key(), false);
                lore.add(value ? "&aBekapcsolva" : "&cKikapcsolva");
                lore.add("&eKattints a váltáshoz");
                return tile(value ? Material.LIME_DYE : Material.GRAY_DYE,
                        (value ? "&a" : "&c") + entry.label(), lore);
            }
            case CYCLE -> {
                final String value = configManager.getString(entry.key(), entry.options().isEmpty() ? "?" : entry.options().get(0));
                lore.add("&fJelenleg: &b" + value);
                lore.add("&7Opciók: &f" + String.join(" / ", entry.options()));
                lore.add("&eKattints a következőhöz");
                return tile(Material.COMPARATOR, "&b" + entry.label(), lore);
            }
            default -> {
                final double value = configManager.getDouble(entry.key(), 0.0D);
                lore.add("&fJelenleg: &b" + formatNumber(entry, value));
                lore.add("&7Bal katt: &f+" + formatStep(entry) + " &7| Jobb katt: &f−" + formatStep(entry));
                lore.add("&7SHIFT = ötszörös lépés");
                return tile(Material.PAPER, "&b" + entry.label(), lore);
            }
        }
    }

    private static String formatNumber(final Entry entry, final double value) {
        return entry.type() == EntryType.INTEGER
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatStep(final Entry entry) {
        return entry.type() == EntryType.INTEGER
                ? String.valueOf((long) entry.step())
                : String.format(Locale.ROOT, "%.2f", entry.step());
    }

    private static ItemStack tile(final Material material, final String name, final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(name).decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            for (final String line : loreLines) {
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(line).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** A kulcshoz tartozó katalógus-bejegyzés (a listener érték-léptetéséhez). */
    public static Entry findEntry(final String key) {
        for (final Category category : CATEGORIES.values()) {
            for (final Entry entry : category.entries()) {
                if (entry.key().equals(key)) {
                    return entry;
                }
            }
        }
        return null;
    }
}
