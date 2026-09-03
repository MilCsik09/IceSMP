package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * P5a/P5b — natív, szerver-oldali IceSMP haladás-fül (advancement-fa). A bejegyzéseket a JAR
 * SAJÁT DATAPACKJE szállítja ({@code src/main/resources/datapack}), amit a bootstrap a
 * {@code DatapackRegistrar} ({@code LifecycleEvents.DATAPACK_DISCOVERY}) horgán derít fel — ez a
 * támogatott, nem deprecated út, és resource pack sem kell hozzá.
 *
 * <p><b>A JSON-ok forrása a lenti {@code NODES} lista</b> — a két oldal nem drifthet szét: a
 * {@code scripts/check_consistency.py} FAIL-el, ha egy csomóponthoz nincs JSON, ha egy JSON
 * árva, vagy ha egy bejegyzéshez nincs valódi {@link #award} hívás (nincs holt bejegyzés).
 *
 * <p><b>Tartalék út:</b> a {@link #load} azokra a csomópontokra, amiket a datapack nem hozott be,
 * még megpróbálja a régi, {@code @Deprecated Bukkit.getUnsafe().loadAdvancement} hívást (az a
 * világ automatikusan generált {@code <world>/datapacks/bukkit/} packjébe ír), és WARNING-ot
 * logol. Így a modern útra migrálás nem tud néma funkció-veszteséget okozni. Az egész út
 * fail-soft: ha mindkettő elbukik, a haladás-fül nem jelenik meg, a játékmenet érintetlen.
 *
 * <p>Minden bejegyzés {@code minecraft:impossible} triggerű: KIZÁRÓLAG kódból kapja meg a
 * játékos ({@link #award}), a meglévő rendszerek grant-pontjain.
 *
 * <p>Szabály: NINCS holt bejegyzés — minden advancementhez tartozik valódi grant-hívás.
 *
 * <p>Statikus facade ({@link #award(Player, String)}): a keresztmetsző „adj advancementet"
 * hívás mezőinjektálás nélkül elérhető bármely managerből (SpellDamageUtil-minta). Ha a
 * rendszer kikapcsolt ({@code advancements.enabled=false}) vagy a service még nem állt fel,
 * a hívás no-op.
 *
 * <p>Folia: a {@link #award} a JÁTÉKOS régió-szálára hopol (a progress a játékos objektumát
 * írja). A {@link #load} a plugin-enable (globál) szálon fut — a registry-mutáció ott biztonságos.
 */
public final class AdvancementService {

    /** Egy advancement leíró: a JSON ebből épül. A parent null = gyökér (háttérrel). */
    private record Node(String id, String parent, String title, String description,
                        String icon, String frame, boolean hidden, String background) {
    }

    private static final String NS = "icesmp";

    // A fa root-tól lefelé rendezve — a loadAdvancement szülőt csak akkor fogad el, ha az
    // MÁR betöltött, ezért a gyökér az első.
    private static final List<Node> NODES = List.of(
            new Node("root", null, "IceSMP", "A Fa árnyékában írt legendád.",
                    "minecraft:beacon", "task", false, "minecraft:gui/advancements/backgrounds/stone"),

            // --- Kaszt-ág ---
            new Node("first_class", "root", "Elhivatás", "Kasztot választottál — az utad elkezdődött.",
                    "minecraft:iron_sword", "task", false, null),
            new Node("first_spec", "first_class", "Az út elágazik", "Specializációt választottál: a mesterséged elmélyült.",
                    "minecraft:enchanted_book", "goal", false, null),
            new Node("class_max", "first_spec", "A hivatás csúcsa", "Elérted a kasztod legmagasabb szintjét.",
                    "minecraft:netherite_sword", "challenge", false, null),
            new Node("capstone", "first_spec", "Zárókő", "Megvásároltad egy talent-fa capstone-jét.",
                    "minecraft:amethyst_shard", "goal", false, null),

            // --- Frakció-ág ---
            // A világban NÉGY hatalom van, de a játékos csak HÁROM közül választhat: a
            // Menedékben kezd (alapértelmezés), onnan a Lánghoz vagy a Fagyhoz állhat. A
            // Kitaszított nem lehet első választás: az útját a bűnküszöb vagy a
            // Suttogó-lelepleződés nyitja meg ugyanazzal az „exiled" bejegyzéssel; az
            // eskü és a tagság ezután külön döntés. A „whisperer" azért REJTETT és
            // toast/chat-mentes, mert a Suttogó-státusz titkos: az álca a mechanika lényege.
            new Node("faction_join", "root", "Hovatartozás", "Kikötöttél az egyik hatalom mellett.",
                    "minecraft:white_banner", "task", false, null),
            new Node("whisperer", "faction_join", "Akit a csend befogadott",
                    "Éjjel, sculkon — a Suttogás megszólalt hozzád. Őrizd a titkot.",
                    "minecraft:sculk_catalyst", "goal", true, null),
            new Node("exiled", "faction_join", "Kitaszítva",
                    "Bűneid miatt száműzött lettél — a DARK eskü és tagság még külön döntés.",
                    "minecraft:wither_skeleton_skull", "challenge", true, null),
            new Node("crowned", "faction_join", "A korona súlya", "Megválasztottak a frakciód királyává.",
                    "minecraft:golden_helmet", "challenge", false, null),
            new Node("cursed_crown", "crowned", "Amit a Királynő számol", "Kitartottál a koronán a Néma Királynő teljes figyelméig.",
                    "minecraft:soul_lantern", "challenge", true, null),
            new Node("raid_win", "faction_join", "Hadizsákmány", "A frakciód megnyert egy raidet, és te ott voltál.",
                    "minecraft:iron_axe", "goal", false, null),
            new Node("redeemed", "exiled", "Vezeklés", "Vezeklésed lezárta a száműzetést és a DARK esküt — visszatértél.",
                    "minecraft:totem_of_undying", "challenge", true, null),

            // --- Szakma-ág ---
            new Node("profession_pick", "root", "Mesterség kezdete", "Beálltál egy szakma tanoncának.",
                    "minecraft:crafting_table", "task", false, null),
            new Node("profession_master", "profession_pick", "A mester keze", "Egy szakmát a legmagasabb szintre vittél.",
                    "minecraft:smithing_table", "challenge", false, null),
            new Node("masterwork", "profession_pick", "Mestermű", "Olyan tárgyat készítettél, amit a Vasművek Akadémiája is elismerne.",
                    "minecraft:anvil", "goal", false, null),

            // --- Világ-ág ---
            new Node("cleanse", "root", "A rontás megtörve", "Megtörted egy rontás-góc magját — a Fa fellélegzik.",
                    "minecraft:echo_shard", "challenge", true, null),
            new Node("hidden_spot", "root", "Rejtett zug", "Rábukkantál a világ egyik titkos helyére.",
                    "minecraft:spyglass", "goal", true, null),
            new Node("world_boss", "root", "Világfaló", "Részt vettél egy világboss elejtésében.",
                    "minecraft:dragon_head", "challenge", false, null),
            new Node("first_relic", "root", "Ereklye a kezedben", "Megszereztél egy relikviát — a régi világ egy darabját.",
                    "minecraft:heart_of_the_sea", "goal", false, null),
            new Node("first_ritual", "first_relic", "Az oltár válaszol", "Végigvittél egy rituálét egy oltárnál.",
                    "minecraft:enchanting_table", "goal", false, null),
            new Node("pet_bond", "root", "Hű Társ", "Társad a te oldalán érte el a legmagasabb szintet.",
                    "minecraft:bone", "goal", false, null),
            new Node("parkour", "root", "Akrobata", "Teljesítettél egy ügyességi pályát.",
                    "minecraft:feather", "task", false, null));

    private static volatile AdvancementService instance;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private volatile boolean loaded;

    public AdvancementService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        instance = this;
    }

    /**
     * Enable-időben: a fa MEGLÉTÉNEK ellenőrzése.
     *
     * <p>Az elsődleges út a jarból szállított datapack (a bootstrap {@code DATAPACK_DISCOVERY}
     * horgán) — ilyenkor itt nincs mit tenni, csak megszámoljuk a bejegyzéseket. A tartalék út
     * a régi, {@code @Deprecated} {@code loadAdvancement} hívás: CSAK azokra a csomópontokra
     * fut, amiket a datapack nem hozott be (pl. ha a jar-URI felderítés elbukott). Így a
     * modern útra migrálás nem tud néma funkció-veszteséget okozni.
     */
    @SuppressWarnings("deprecation")
    public void load() {
        if (!configManager.getBoolean("advancements.enabled", true)) {
            return;
        }
        int fromDatapack = 0;
        int fromFallback = 0;
        final List<String> missing = new java.util.ArrayList<>();
        for (final Node node : NODES) {
            final NamespacedKey key = new NamespacedKey(NS, node.id());
            if (Bukkit.getAdvancement(key) != null) {
                fromDatapack++;
                continue;
            }
            try {
                if (Bukkit.getUnsafe().loadAdvancement(key, buildJson(node)) != null) {
                    fromFallback++;
                    continue;
                }
            } catch (final Throwable throwable) {
                plugin.getLogger().warning("Advancement tartalék-betöltés hiba (" + node.id() + "): "
                        + throwable.getMessage());
            }
            missing.add(node.id());
        }
        // TÉTELES állapot: egyetlen betöltött bejegyzés is „loaded"-nak számított, közben a
        // hiányzó node-ok award-jai NÉMÁN no-opoltak — a részleges pack sikeres indulásnak
        // látszott. Hiányos fa = a rendszer KIKAPCSOL, és a log megnevezi a hiányzókat.
        loaded = missing.isEmpty() && fromDatapack + fromFallback == NODES.size();
        if (!missing.isEmpty()) {
            plugin.getLogger().severe("IceSMP advancement-fa HIÁNYOS (" + (NODES.size() - missing.size())
                    + "/" + NODES.size() + ") — a rendszer KIKAPCSOLT, hogy ne némán vesszenek el a "
                    + "bejegyzések. Hiányzó: " + String.join(", ", missing)
                    + ". A datapack a jar-ból telepítődik a világ datapack-könyvtárába.");
            return;
        }
        if (fromFallback > 0) {
            plugin.getLogger().warning("IceSMP advancement-fa: " + fromDatapack + " datapackből, "
                    + fromFallback + " a DEPRECATED tartalék úton (" + NODES.size() + " összesen). "
                    + "A datapack-felderítés nem hozta be mindet — érdemes a szerver-logot megnézni.");
        } else {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "IceSMP advancement-fa: " + fromDatapack + "/" + NODES.size()
                    + " bejegyzés a jar datapackjéből él.");
        }
    }

    /** A statikus facade célpontja: a bejegyzés-kritérium teljesítése a játékos szálán. */
    public static void award(final Player player, final String id) {
        final AdvancementService service = instance;
        if (service == null || !service.loaded || player == null) {
            return;
        }
        // Élő kulcs: a kikapcsolás azonnal hasson (a loaded flag csak az indulási állapot;
        // a visszakapcsoláshoz viszont /icesmp reload kell, mert a fa regisztrációja indulási).
        if (!service.configManager.getBoolean("advancements.enabled", true)) {
            return;
        }
        service.grant(player, id);
    }

    private void grant(final Player player, final String id) {
        final NamespacedKey key = new NamespacedKey(NS, id);
        player.getScheduler().run(plugin, task -> {
            final Advancement advancement = Bukkit.getAdvancement(key);
            if (advancement == null) {
                return;
            }
            final AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (progress.isDone()) {
                return;
            }
            for (final String criterion : progress.getRemainingCriteria()) {
                progress.awardCriteria(criterion);
            }
        }, null);
    }

    /**
     * A JSON az 1.21.11 advancement-formátumot követi: icon={id,count}, cím/leírás
     * text-komponens, impossible-trigger + requirements. A gyökér háttér-textúrát kap.
     *
     * <p>A {@code show_toast:false} SZÁNDÉKOS és a datapack-JSON-okkal egyező: a fa-bejegyzések
     * nem ugranak fel, a toast-réteg külön, célzott ({@code ToastUtil}) — e nélkül a tartalék
     * úton betöltött csomópont máshogy viselkedne, mint a datapackből jövő ugyanaz.</p>
     */
    private static String buildJson(final Node node) {
        final StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        if (node.parent() != null) {
            sb.append("\"parent\":\"").append(NS).append(':').append(node.parent()).append("\",");
        }
        sb.append("\"criteria\":{\"granted\":{\"trigger\":\"minecraft:impossible\"}},");
        sb.append("\"requirements\":[[\"granted\"]],");
        sb.append("\"display\":{");
        sb.append("\"icon\":{\"id\":\"").append(node.icon()).append("\",\"count\":1},");
        sb.append("\"title\":{\"text\":\"").append(escape(node.title())).append("\"},");
        sb.append("\"description\":{\"text\":\"").append(escape(node.description())).append("\"},");
        sb.append("\"frame\":\"").append(node.frame()).append("\",");
        sb.append("\"show_toast\":false,\"announce_to_chat\":false,");
        sb.append("\"hidden\":").append(node.hidden());
        if (node.background() != null) {
            sb.append(",\"background\":\"").append(node.background()).append('"');
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String escape(final String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
