package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingame admin config-menü (jog: {@code icesmp.admin.config}): kategóriákra osztott,
 * kattintható felület a legfontosabb, admin által hangolható config-kulcsokhoz.
 *
 * <p>Kezelés: BOOLEAN — bal/jobb katt = váltás; SZÁM — bal katt +lépés, jobb katt
 * −lépés, SHIFT = ötszörös lépés; CYCLE — katt = következő opció; GÖRGŐKATT —
 * a config.yml override törlése és visszatérés a subsystem alapértékhez.
 */
public final class ConfigMenuGUI {

    /** Egy szerkeszthető kulcs a menüben. */
    public record Entry(String key, String label, EntryType type, double step,
                        double min, double max, List<String> options) {
        static Entry toggle(final String key, final String label) {
            return new Entry(key, label, EntryType.TOGGLE, 0, 0, 0, List.of());
        }

        static Entry number(final String key, final String label, final double step,
                            final double min, final double max) {
            return new Entry(key, label, EntryType.NUMBER, step, min, max, List.of());
        }

        static Entry integer(final String key, final String label, final int step,
                             final int min, final int max) {
            return new Entry(key, label, EntryType.INTEGER, step, min, max, List.of());
        }

        static Entry cycle(final String key, final String label,
                           final List<String> options) {
            return new Entry(key, label, EntryType.CYCLE, 0, 0, 0, options);
        }

        public ReloadMode reloadMode() {
            if (key.equals("factions.tax.enabled") || key.equals("factions.tax.interval-minutes")) {
                return ReloadMode.RESTART_REQUIRED;
            }
            if (key.startsWith("motd.") || key.startsWith("sit.") || key.startsWith("crates.")
                    || key.startsWith("resource-pack.") || key.startsWith("factions.passives.")
                    || key.startsWith("factions.whisper.") || key.startsWith("professions.recipes.")) {
                return ReloadMode.RELOAD_HOOK;
            }
            return ReloadMode.LIVE;
        }
    }

    public enum EntryType { TOGGLE, NUMBER, INTEGER, CYCLE }
    public enum ReloadMode { LIVE, RELOAD_HOOK, RESTART_REQUIRED }


    /** Egy kategória: cím, ikon és a kurátort kulcs-lista. */
    public record Category(String id, String title, Material icon, List<Entry> entries) {
    }

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
                Entry.toggle("world-events.safety.enabled", "Játékos-/border spawn-biztonság"),
                Entry.toggle("world-events.safety.ignore-spectators", "Spectatorok kihagyása"),
                Entry.toggle("world-events.safety.ignore-vanished", "Vanish adminok kihagyása"),
                Entry.toggle("world-events.safety.ignore-admins", "Adminok kihagyása"),
                Entry.number("world-events.safety.min-horizontal-distance-blocks", "Minimum játékostávolság (blokk)", 8, 0, 2048),
                Entry.number("world-events.safety.min-3d-distance-blocks", "Minimum 3D távolság (0=kikapcsolva)", 8, 0, 2048),
                Entry.number("world-events.safety.min-world-spawn-distance-blocks", "Világspawn minimumtáv", 8, 0, 4096),
                Entry.toggle("world-events.safety.require-loaded-chunk", "Csak betöltött chunk"),
                Entry.integer("world-events.safety.search-attempts", "Biztonságos hely keresési próbák", 1, 1, 128),
                Entry.number("world-events.safety.search-min-radius-blocks", "Keresési sugár minimum", 16, 0, 4096),
                Entry.number("world-events.safety.search-max-radius-blocks", "Keresési sugár maximum", 16, 16, 4096),
                Entry.number("world-events.safety.world-border-margin-blocks", "World border biztonsági margó", 8, 0, 1024),
                Entry.number("world-events.safety.reservation-distance-blocks", "Párhuzamos események minimumtávja", 8, 0, 2048),
                Entry.integer("world-events.safety.reservation-seconds", "Spawn-foglalás ideje (mp)", 10, 1, 3600),
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
                Entry.toggle("kill-rewards.exclude-spawner-mobs", "Spawner-mob kizárása"),
                Entry.toggle("kill-rewards.exclude-minions", "Minionok kizárása"),
                Entry.toggle("kill-rewards.require-survival", "Csak survival gyilkos kap jutalmat"))));
        categories.put("passzivok", new Category("passzivok", "Frakciópasszívok", Material.TOTEM_OF_UNDYING, List.of(
                Entry.toggle("factions.passives.enabled", "Passzívok mesterkapcsoló"),
                Entry.toggle("factions.passives.red.enabled", "RED passzív"),
                Entry.number("factions.passives.red.fire-damage-multiplier", "RED tűz szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.number("factions.passives.red.fire-tick-damage-multiplier", "RED égés szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.number("factions.passives.red.entity-fire-damage-multiplier", "RED entity-tűz szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.number("factions.passives.red.lava-damage-multiplier", "RED láva szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.number("factions.passives.red.hot-floor-damage-multiplier", "RED magma szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.toggle("factions.passives.red.affect-icesmp-fire-magic", "RED érinti a TUZ mágiát"),
                Entry.number("factions.passives.red.fire-magic-damage-multiplier", "RED TUZ szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.toggle("factions.passives.red.affect-scripted-combat-fire", "RED scripted/event tűzre is hat"),
                Entry.toggle("factions.passives.blue.enabled", "BLUE passzív"),
                Entry.number("factions.passives.blue.freeze-damage-multiplier", "BLUE fagyás szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.number("factions.passives.blue.drowning-damage-multiplier", "BLUE fulladás szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.number("factions.passives.blue.natural-exhaustion-save-chance", "BLUE exhaustion mentési esély", 0.05, 0, 1),
                Entry.toggle("factions.passives.neutral.enabled", "NEUTRAL passzív"),
                Entry.number("factions.passives.neutral.fall-damage-multiplier", "NEUTRAL zuhanás szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.toggle("factions.passives.neutral.passive-mob-truce.enabled", "NEUTRAL spontán mob-béke"),
                Entry.toggle("factions.passives.neutral.passive-mob-truce.break-on-damage", "NEUTRAL provokáció megtöri"),
                Entry.integer("factions.passives.neutral.passive-mob-truce.retaliation-seconds", "NEUTRAL megtorlás (mp)", 5, 0, Integer.MAX_VALUE),
                Entry.toggle("factions.passives.neutral.enderman.ignore-stare-aggro", "Enderman szemkontaktus ignorálása"),
                Entry.toggle("factions.passives.dark.enabled", "DARK passzív"),
                Entry.toggle("factions.passives.dark.wither.damage-enabled", "DARK Wither-sebzés ellenállás"),
                Entry.number("factions.passives.dark.wither.damage-multiplier", "DARK Wither-sebzés szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.toggle("factions.passives.dark.wither.duration-enabled", "DARK Wither-idő ellenállás"),
                Entry.number("factions.passives.dark.wither.duration-multiplier", "DARK Wither-idő szorzó", 0.05, 0, Double.MAX_VALUE),
                Entry.toggle("factions.passives.dark.ambient-undead.enabled", "DARK ambient undead-béke"),
                Entry.toggle("factions.passives.dark.ambient-undead.break-on-damage", "DARK provokáció megtöri"),
                Entry.integer("factions.passives.dark.ambient-undead.retaliation-seconds", "DARK megtorlás (mp)", 5, 0, Integer.MAX_VALUE),
                Entry.number("factions.passives.dark.ambient-undead.alert-nearby-radius", "DARK riasztási sugár", 1, 0, Double.MAX_VALUE),
                Entry.number("factions.passives.dark.wild-undead.target-cancel-chance", "DARK vad éji béke esély", 0.05, 0, 1),
                Entry.toggle("factions.passives.dark.wild-undead.disabled-during-blood-moon", "Vérhold felülírja a vad békét"))));
        categories.put("hadiablak", new Category("hadiablak", "Hadi-ablak", Material.IRON_SWORD, List.of(
                Entry.toggle("factions.war-window.enabled", "Hadi-ablak"),
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
                Entry.integer("cultists.whisper-loot-rolls", "Kult-loot részesedés", 1, 0, 10),
                Entry.number("factions.whisper.blackmarket-discount-percent", "Feketepiac-kedvezmény (%)", 5, 0, 90))));
        categories.put("etelek", new Category("etelek", "Frakció-ételek", Material.COOKED_SALMON, List.of(
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
                Entry.cycle("relics.passive-death.mode", "Passzív relikvia halálkor", List.of("reclaim", "keep", "drop")),
                Entry.toggle("relics.wings.faction-locked-pickup", "Szárny frakció-zár"),
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
                Entry.integer("dev-items.csodalatos_bingulus.pity.ritka.after-rolls", "Ritka pity", 5, 1, 100000),
                Entry.integer("dev-items.csodalatos_bingulus.pity.epikus.after-rolls", "Epikus pity", 10, 1, 100000),
                Entry.integer("dev-items.csodalatos_bingulus.pity.legendas.after-rolls", "Legendás pity", 50, 1, 100000))));
        categories.put("emlek", new Category("emlek", "Emlékszilánkok", Material.AMETHYST_SHARD, List.of(
                Entry.integer("memory-shards.xp-amount", "XP-csomag mérete", 50, 1, 1000000),
                Entry.integer("memory-shards.costs.xp", "XP-beváltás ára", 1, 1, 100),
                Entry.integer("memory-shards.costs.talent", "Talentpont ára", 1, 1, 100),
                Entry.integer("memory-shards.costs.spec", "Spec-kapu ára", 1, 1, 100))));
        categories.put("liga", new Category("liga", "Szezon-liga", Material.GOLDEN_HELMET, List.of(
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
        categories.put("darknep", new Category("darknep", "DARK-népesség és variánsok", Material.ZOMBIE_HEAD, List.of(
                Entry.toggle("dark-undead.enabled", "DARK undead-népesség"),
                Entry.cycle("dark-undead.scope", "Hatókör", List.of("capital", "all")),
                Entry.integer("dark-undead.max-population", "Populáció-plafon", 2, 1, 200),
                Entry.integer("dark-undead.spawn-interval-seconds", "Pótlás-időköz (mp)", 5, 5, 3600),
                Entry.integer("dark-undead.min-level", "Min. szint", 1, 1, 50),
                Entry.integer("dark-undead.max-level", "Max. szint", 1, 1, 50),
                Entry.integer("dark-undead.lifespan-seconds", "Élettartam (mp)", 60, 60, 86400),
                Entry.number("rare-variant.chance-percent", "Ritka variáns esély (%)", 0.25, 0, 100),
                Entry.number("rare-variant.xp-multiplier", "Variáns XP-szorzó", 0.25, 1, 10),
                Entry.number("rare-variant.soul-chance-multiplier", "Variáns lélek-szorzó", 0.25, 1, 10))));
        categories.put("cehek", new Category("cehek", "Céhek és szakma-hét", Material.WHITE_BANNER, List.of(
                Entry.toggle("guilds.enabled", "Céh-rendszer"),
                Entry.number("guilds.create-cost", "Alapítás ára", 25, 0, 100000),
                Entry.integer("guilds.base-max-members", "Alap-taglétszám", 1, 1, 100),
                Entry.integer("guilds.max-members-cap", "Taglétszám-plafon", 1, 1, 200),
                Entry.integer("guilds.xp-per-quest", "Céh-XP questenként", 1, 0, 1000),
                Entry.toggle("profession-weekly.enabled", "Szakma-céh heti cél"),
                Entry.integer("profession-weekly.reward-xp", "Heti cél jutalom-XP", 25, 0, 100000),
                Entry.integer("profession-weekly.min-contribution", "Jutalom-küszöb", 25, 1, 1000000))));
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
        categories.put("moderacio", new Category("moderacio", "Moderáció és vanish", Material.ENDER_EYE, List.of(
                Entry.toggle("moderation.enabled", "Natív moderáció"),
                Entry.toggle("moderation.chat-filter.enabled", "Chat-szűrő"),
                Entry.cycle("moderation.chat-filter.mode", "Chat-szűrő mód", List.of("CENSOR", "BLOCK")),
                Entry.toggle("moderation.spam.enabled", "Spam-védelem"),
                Entry.integer("moderation.spam.min-interval-millis", "Üzenetköz minimum (ms)", 100, 0, 60000),
                Entry.integer("moderation.spam.duplicate-window-seconds", "Duplikált üzenet ablak (mp)", 1, 0, 3600),
                Entry.toggle("moderation.chat-log.enabled", "Moderációs chat-log"),
                Entry.toggle("moderation.vanish.exclude-from-online-count", "Vanish kihagyása online számból"),
                Entry.toggle("moderation.vanish.allow-item-pickup", "Vanish tárgyfelvétel"),
                Entry.toggle("moderation.vanish.allow-damage", "Vanish sebzés/sebezhetőség"),
                Entry.toggle("moderation.vanish.allow-interaction", "Vanish interakció"),
                Entry.toggle("moderation.vanish.allow-chat", "Vanish chat"))));
        categories.put("borze", new Category("borze", "Börze és városi őrség", Material.EMERALD, List.of(
                Entry.toggle("market.allow-relic-listing", "Relikvia listázható"),
                Entry.number("market.relic-auction.recommended-min-bid", "Relikvia minimum licit", 25, 0, 1000000),
                Entry.toggle("city-guards.enabled", "Városi őrség"),
                Entry.integer("city-guards.step-seconds", "Őr-léptetés (mp)", 1, 1, 60),
                Entry.number("city-guards.day-step-blocks", "Nappali lépés (blokk)", 0.5, 0.5, 16),
                Entry.number("city-guards.night-step-blocks", "Éjjeli lépés (blokk)", 0.5, 0.5, 16))));

        categories.put("party", new Category("party", "Party és megosztás", Material.PLAYER_HEAD, List.of(
                Entry.toggle("party.enabled", "Party-rendszer"),
                Entry.integer("party.max-size", "Maximális party-méret", 1, 2, 20),
                Entry.integer("party.invite-expire-seconds", "Meghívó lejárata (mp)", 5, 5, 600),
                Entry.number("party.share-radius", "Megosztási sugár (blokk)", 5, 1, 256),
                Entry.toggle("party.xp-share", "Party XP-megosztás"),
                Entry.toggle("party.personal-loot", "Személyes eseményloot"),
                Entry.toggle("party.block-friendly-fire", "Party friendly fire tiltása"),
                Entry.toggle("party.hud-enabled", "Party HUD-frame"))));
        categories.put("claimek", new Category("claimek", "3D claimek", Material.GOLDEN_SHOVEL, List.of(
                Entry.toggle("claims.enabled", "Claim-rendszer"),
                Entry.integer("claims.quick-size", "Gyorsclaim oldalhossz", 1, 1, 128),
                Entry.integer("claims.default-height", "Alap magasság", 5, 0, 384),
                Entry.integer("claims.default-depth", "Alap mélység", 5, 0, 384),
                Entry.integer("claims.free-columns", "Ingyenes oszlopok", 64, 0, 1000000),
                Entry.number("claims.column-cost", "Oszloponkénti ár", 0.1, 0, 100000),
                Entry.integer("claims.max-columns-per-player", "Játékos claim-plafon", 256, 1, 10000000),
                Entry.integer("claims.area-max-columns", "Egy kijelölés plafonja", 100, 1, 10000000),
                Entry.integer("claims.y-extend-step", "Y-bővítés lépése", 1, 1, 64),
                Entry.number("claims.y-extend-cost-per-column", "Y-bővítés oszlopára", 0.05, 0, 100000),
                Entry.toggle("claims.protect-containers", "Konténervédelem"),
                Entry.toggle("claims.protect-explosions", "Robbanásvédelem"),
                Entry.toggle("claims.protect-fire", "Tűzvédelem"),
                Entry.toggle("claims.protect-terrain", "Folyadék- és dugattyúvédelem"),
                Entry.toggle("claims.block-in-protected-zone", "Claim tiltása védett zónában"),
                Entry.toggle("claims.block-in-territory", "Claim tiltása normál territoryban"),
                Entry.toggle("claims.block-in-protected-region", "Claim tiltása WorldGuard-régióban"),
                Entry.toggle("claims.raid-lootable", "Raid alatti konténerloot"),
                Entry.integer("claims.border.show-seconds", "Határrajz ideje (mp)", 1, 1, 120),
                Entry.integer("claims.border.radius", "Határrajz chunk-sugara", 1, 0, 16),
                Entry.toggle("claims.border.enter-notice", "Claimhatár értesítés"))));
        categories.put("megjelenes", new Category("megjelenes", "Chat és spell-VFX", Material.GLOW_INK_SAC, List.of(
                Entry.toggle("chat.format-enabled", "Natív chat-formázás"),
                Entry.toggle("chat.name-faction-color", "Frakciószínű chatnév"),
                Entry.toggle("spell-vfx.enabled", "Formázott spell-VFX"),
                Entry.integer("spell-vfx.max-points", "VFX pontplafon", 4, 4, 256))));
        categories.put("adomany", new Category("adomany", "Adományláda", Material.CHEST, List.of(
                Entry.toggle("donation-chest.enabled", "Adományláda"),
                Entry.integer("donation-chest.max-items", "Teljes tételkapacitás", 45, 45, 2700),
                Entry.integer("donation-chest.max-per-player", "Tételek játékosonként", 1, 0, 2700))));
        categories.put("kliens", new Category("kliens", "Kliens-bridge", Material.SPYGLASS, List.of(
                Entry.toggle("client.enabled", "Kliens-bridge (rollback-kapcsoló)"),
                Entry.toggle("client.debug", "Bridge debug-napló"),
                Entry.integer("client.limits.control-messages-per-second", "Control-üzenet plafon (db/mp)", 5, 1, 200),
                Entry.integer("client.limits.resync-cooldown-ms", "Resync-szünet (ms)", 500, 500, 60000),
                Entry.integer("client.limits.cast-messages-per-second", "CAST_SLOT plafon (db/mp)", 1, 1, 40))));
        return categories;
    }

    private ConfigMenuGUI() {
    }

    public static void openRoot(final Player player) {
        ConfigMenuRootGUI.openRoot(player);
    }

    /** Egy 54 slotos kategória-lap; 45 szerkeszthető helyet hagy a vezérlősor fölött. */
    public static void openCategory(final Player player, final String categoryId,
                                    final ConfigManager configManager) {
        final Category category = CATEGORIES.get(categoryId);
        if (category == null) {
            openRoot(player);
            return;
        }
        if (category.entries().size() > 45) {
            throw new IllegalStateException("A config-kategória túl nagy egy oldalhoz: "
                    + category.id() + " (" + category.entries().size() + "/45)");
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), categoryId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ " + category.title(), NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final Entry entry : category.entries()) {
            inventory.setItem(slot, ConfigMenuEntryRenderer.render(entry, configManager));
            holder.bind(slot, actionFor(entry));
            slot++;
        }
        inventory.setItem(49, GuiUtil.item(Material.ARROW, "&7Vissza", List.of()));
        holder.bind(49, "BACK");
        inventory.setItem(53, GuiUtil.item(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(53, "CLOSE");
        player.openInventory(inventory);
    }

    private static String actionFor(final Entry entry) {
        return switch (entry.type()) {
            case TOGGLE -> "TOGGLE:" + entry.key();
            case CYCLE -> "CYCLE:" + entry.key();
            default -> "NUM:" + entry.key();
        };
    }

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

    public static List<Entry> allEntries() {
        final List<Entry> entries = new ArrayList<>();
        for (final Category category : CATEGORIES.values()) {
            entries.addAll(category.entries());
        }
        return List.copyOf(entries);
    }
}
