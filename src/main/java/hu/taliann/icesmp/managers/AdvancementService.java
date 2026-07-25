package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * P5a/P5b — natív, szerver-oldali IceSMP haladás-fül (advancement-fa). A bejegyzéseket
 * data-driven advancement JSON-ként töltjük be a {@code Bukkit.getUnsafe().loadAdvancement(kulcs, json)}
 * úton (a szerver a klienssel szinkronizálja — resource pack NEM kell).
 *
 * <p><b>Hova kerülnek valójában:</b> a Bukkit a plugin-oldali advancementeket a VILÁG
 * automatikusan generált datapackjébe teszi ({@code <world>/datapacks/bukkit/}, leírása:
 * „Data pack for resources provided by Bukkit plugins"). Tehát a fa datapack-formában és
 * -mechanizmussal él, csak nem a jar szállítja: futásidőben íródik.
 *
 * <p><b>FIGYELEM — nem stabil API:</b> a {@code Bukkit.getUnsafe()} az API-ban
 * {@code @Deprecated}. Ma működik, de MC/Paper-bumpnál ez az első törési pont; a modern,
 * támogatott alternatíva a {@code io.papermc.paper.datapack.DatapackRegistrar} (a jar szállít
 * saját datapacket). A {@link #load} ezért teljesen fail-soft: ha az út elbukik, a haladás-fül
 * egyszerűen nem jelenik meg, a játékmenet érintetlen. Minden bejegyzés {@code minecraft:impossible} triggerű: KIZÁRÓLAG kódból
 * kapja meg a játékos ({@link #award}), a meglévő rendszerek grant-pontjain.
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
            new Node("first_class", "root", "Elhivatás", "Kasztot választottál — az utad elkezdődött.",
                    "minecraft:iron_sword", "task", false, null),
            new Node("first_spec", "first_class", "Az út elágazik", "Specializációt választottál: a mesterséged elmélyült.",
                    "minecraft:enchanted_book", "goal", false, null),
            new Node("faction_join", "root", "Hovatartozás", "Csatlakoztál a négy hatalom egyikéhez.",
                    "minecraft:white_banner", "task", false, null),
            new Node("profession_pick", "root", "Mesterség kezdete", "Beálltál egy szakma tanoncának.",
                    "minecraft:crafting_table", "task", false, null),
            new Node("cleanse", "root", "A rontás megtörve", "Megtörted egy rontás-góc magját — a Fa fellélegzik.",
                    "minecraft:echo_shard", "challenge", true, null),
            new Node("hidden_spot", "root", "Rejtett zug", "Rábukkantál a világ egyik titkos helyére.",
                    "minecraft:spyglass", "goal", true, null));

    private static volatile AdvancementService instance;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private volatile boolean loaded;

    public AdvancementService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        instance = this;
    }

    /** Enable-időben: a fa betöltése a szerver-registrybe (idempotens, a már meglévőt átugorja). */
    @SuppressWarnings("deprecation")
    public void load() {
        if (!configManager.getBoolean("advancements.enabled", true)) {
            return;
        }
        int count = 0;
        for (final Node node : NODES) {
            final NamespacedKey key = new NamespacedKey(NS, node.id());
            if (Bukkit.getAdvancement(key) != null) {
                count++;
                continue;
            }
            try {
                if (Bukkit.getUnsafe().loadAdvancement(key, buildJson(node)) != null) {
                    count++;
                }
            } catch (final Throwable throwable) {
                plugin.getLogger().warning("Advancement betöltés hiba (" + node.id() + "): " + throwable.getMessage());
            }
        }
        loaded = count > 0;
        plugin.getLogger().info("IceSMP advancement-fa: " + count + "/" + NODES.size() + " bejegyzés él.");
    }

    /** A statikus facade célpontja: a bejegyzés-kritérium teljesítése a játékos szálán. */
    public static void award(final Player player, final String id) {
        final AdvancementService service = instance;
        if (service == null || !service.loaded || player == null) {
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
        sb.append("\"show_toast\":true,\"announce_to_chat\":false,");
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
