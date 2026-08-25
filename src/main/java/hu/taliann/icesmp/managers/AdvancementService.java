package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * P5a/P5b — natív, szerver-oldali IceSMP haladás-fül (advancement-fa). A bejegyzéseket a JAR
 * SAJÁT DATAPACKJE szállítja ({@code src/main/resources/datapack}), amit a bootstrap a
 * {@code DatapackRegistrar} ({@code LifecycleEvents.DATAPACK_DISCOVERY}) horgán derít fel — ez a
 * támogatott, nem deprecated út, és resource pack sem kell hozzá.
 *
 * <p><b>A kézzel authorolt datapack JSON-ok a gameplay source of truth.</b> A lenti ID-lista
 * kizárólag a runtime teljesség-ellenőrzésének bounded indexe. A repository consistency gate
 * FAIL-el hiányzó/árva JSON, hibás parent vagy valódi {@link #award} hívás nélküli node esetén.
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

    private static final String NS = "icesmp";

    /** Runtime verification order; parent JSONs precede their children for deprecated fallback load. */
    private static final List<String> ADVANCEMENT_IDS = List.of(
            "root",
            "first_class", "first_spec", "capstone",
            "faction_join", "whisperer", "exiled", "crowned", "cursed_crown", "raid_win", "redeemed",
            "profession_pick", "profession_master", "masterwork",
            "cleanse", "hidden_spot", "world_boss", "first_relic", "first_ritual", "pet_bond", "parkour");
    private static final List<String> TOAST_IDS = List.of("toast_quest");

    private static volatile AdvancementService instance;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private volatile boolean loaded;

    public AdvancementService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        synchronized (AdvancementService.class) {
            instance = this;
        }
    }

    /** Identity-safe lifecycle teardown: a late disable of A cannot clear active B. */
    public static void clearIfCurrent(final AdvancementService candidate) {
        synchronized (AdvancementService.class) {
            if (instance == candidate) instance = null;
        }
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
        for (final String id : ADVANCEMENT_IDS) {
            final NamespacedKey key = new NamespacedKey(NS, id);
            if (Bukkit.getAdvancement(key) != null) {
                fromDatapack++;
                continue;
            }
            try {
                if (Bukkit.getUnsafe().loadAdvancement(key, authoredJson(id)) != null) {
                    fromFallback++;
                    continue;
                }
            } catch (final Throwable throwable) {
                plugin.getLogger().warning("Advancement tartalék-betöltés hiba (" + id + "): "
                        + throwable.getMessage());
            }
            missing.add(id);
        }
        // TÉTELES állapot: egyetlen betöltött bejegyzés is „loaded"-nak számított, közben a
        // hiányzó node-ok award-jai NÉMÁN no-opoltak — a részleges pack sikeres indulásnak
        // látszott. Hiányos fa = a rendszer KIKAPCSOL, és a log megnevezi a hiányzókat.
        loaded = missing.isEmpty() && fromDatapack + fromFallback == ADVANCEMENT_IDS.size();
        if (!missing.isEmpty()) {
            plugin.getLogger().severe("IceSMP advancement-fa HIÁNYOS (" + (ADVANCEMENT_IDS.size() - missing.size())
                    + "/" + ADVANCEMENT_IDS.size() + ") — a rendszer KIKAPCSOLT, hogy ne némán vesszenek el a "
                    + "bejegyzések. Hiányzó: " + String.join(", ", missing)
                    + ". A datapack a jar-ból telepítődik a világ datapack-könyvtárába.");
            return;
        }
        final List<String> missingToasts = new java.util.ArrayList<>();
        int toastFromDatapack = 0;
        int toastFromFallback = 0;
        for (final String id : TOAST_IDS) {
            final NamespacedKey key = new NamespacedKey(NS, id);
            if (Bukkit.getAdvancement(key) != null) {
                toastFromDatapack++;
                continue;
            }
            try {
                if (Bukkit.getUnsafe().loadAdvancement(key, authoredJson(id)) != null) {
                    toastFromFallback++;
                    continue;
                }
            } catch (final Throwable throwable) {
                plugin.getLogger().warning("Quest-toast tartalék-betöltés hiba: " + throwable.getMessage());
            }
            missingToasts.add(id);
        }
        if (fromFallback > 0) {
            plugin.getLogger().warning("IceSMP persistent advancement-fa: " + fromDatapack + " datapackből, "
                    + fromFallback + " a DEPRECATED tartalék úton (" + ADVANCEMENT_IDS.size() + " persistent). "
                    + "A datapack-felderítés nem hozta be mindet — érdemes a szerver-logot megnézni.");
        } else {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager,
                    "IceSMP persistent advancement-fa: " + fromDatapack + "/" + ADVANCEMENT_IDS.size()
                            + " bejegyzés a jar datapackjéből él.");
        }
        if (!missingToasts.isEmpty()) {
            plugin.getLogger().warning("IceSMP presentation DEGRADED: a persistent advancement-fa teljes, "
                    + "de a használt quest-toast hiányzik: " + String.join(", ", missingToasts)
                    + ". A quest chat-visszajelzés működik.");
        } else {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager,
                    "IceSMP reusable toast: " + toastFromDatapack + " datapackből, "
                            + toastFromFallback + " deprecated fallbackból (1 authored).");
        }
    }

    private String authoredJson(final String id) throws java.io.IOException {
        final String path = "datapack/data/icesmp/advancement/" + id + ".json";
        try (InputStream input = plugin.getResource(path)) {
            if (input == null) throw new java.io.IOException("missing authored resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
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

}
