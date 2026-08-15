package hu.taliann.icesmp.gui;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adds world-event placement controls without duplicating the transactional GUI. */
public final class EventSpawnConfigMenuExtension {
    private static final String PRIMARY_CATEGORY = "esemenyek";
    private static final String OVERFLOW_CATEGORY = "event-spawn";
    private static boolean installed;

    private EventSpawnConfigMenuExtension() { }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        final ConfigMenuGUI.Category current = ConfigMenuGUI.CATEGORIES.get(PRIMARY_CATEGORY);
        if (current == null) {
            return;
        }

        final Set<String> existingKeys = new HashSet<>();
        for (final ConfigMenuGUI.Entry entry : ConfigMenuGUI.allEntries()) {
            existingKeys.add(entry.key());
        }
        final List<ConfigMenuGUI.Entry> desired = desiredEntries();
        final List<ConfigMenuGUI.Entry> water = desired.stream()
                .filter(entry -> entry.key().startsWith("world-events.water-safety."))
                .filter(entry -> !existingKeys.contains(entry.key())).toList();
        final List<ConfigMenuGUI.Entry> placement = desired.stream()
                .filter(entry -> entry.key().startsWith("world-events.placement."))
                .filter(entry -> !existingKeys.contains(entry.key())).toList();
        if (water.isEmpty() && placement.isEmpty()) {
            installed = true;
            return;
        }

        final List<ConfigMenuGUI.Entry> primary = new ArrayList<>(current.entries());
        final List<ConfigMenuGUI.Entry> overflow = new ArrayList<>();
        for (final ConfigMenuGUI.Entry entry : water) {
            if (primary.size() < 45) {
                primary.add(entry);
            } else {
                overflow.add(entry);
            }
        }
        if (primary.size() + placement.size() <= 45) {
            primary.addAll(placement);
        } else {
            overflow.addAll(placement);
        }
        ConfigMenuGUI.CATEGORIES.put(PRIMARY_CATEGORY, new ConfigMenuGUI.Category(
                current.id(), current.title(), current.icon(), List.copyOf(primary)));

        if (!overflow.isEmpty()) {
            final ConfigMenuGUI.Category previous = ConfigMenuGUI.CATEGORIES.get(OVERFLOW_CATEGORY);
            final List<ConfigMenuGUI.Entry> overflowEntries = new ArrayList<>();
            if (previous != null) {
                overflowEntries.addAll(previous.entries());
            }
            final Set<String> overflowKeys = overflowEntries.stream()
                    .map(ConfigMenuGUI.Entry::key).collect(java.util.stream.Collectors.toSet());
            for (final ConfigMenuGUI.Entry entry : overflow) {
                if (overflowKeys.add(entry.key())) {
                    overflowEntries.add(entry);
                }
            }
            if (overflowEntries.size() > 45) {
                throw new IllegalStateException("Event-spawn config category exceeds 45 entries: "
                        + overflowEntries.size());
            }
            ConfigMenuGUI.CATEGORIES.put(OVERFLOW_CATEGORY, new ConfigMenuGUI.Category(
                    OVERFLOW_CATEGORY, "Event spawn-védelem", Material.RECOVERY_COMPASS,
                    List.copyOf(overflowEntries)));
        }
        installed = true;
    }

    private static List<ConfigMenuGUI.Entry> desiredEntries() {
        return List.of(
                ConfigMenuGUI.Entry.toggle(
                        "world-events.water-safety.enabled", "Víz- és partvédelem"),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.water-safety.enforce-all-events", "Vízvédelem minden eventre"),
                ConfigMenuGUI.Entry.integer(
                        "world-events.water-safety.buffer-blocks", "Víztől mért puffer (blokk)", 1, 0, 7),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.placement.dynamic-view-distance-enabled", "Dinamikus látótáv-védelem"),
                ConfigMenuGUI.Entry.number(
                        "world-events.placement.view-distance-margin-blocks", "Látótáv biztonsági margó", 8, 0, 512),
                ConfigMenuGUI.Entry.number(
                        "world-events.placement.search-clearance-margin-blocks", "Keresési extra távolság", 8, 0, 512),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.placement.visibility-cone.enabled", "Nézési kúp kerülése"),
                ConfigMenuGUI.Entry.number(
                        "world-events.placement.visibility-cone.max-distance-blocks", "Nézési kúp maximumtáv", 16, 0, 2048),
                ConfigMenuGUI.Entry.number(
                        "world-events.placement.visibility-cone.angle-degrees", "Nézési kúp szélessége (fok)", 5, 1, 179),
                ConfigMenuGUI.Entry.integer(
                        "world-events.placement.recent-location-cooldown-minutes", "Helyszín-újrahasználat cooldown", 5, 0, 1440),
                ConfigMenuGUI.Entry.number(
                        "world-events.placement.recent-location-distance-blocks", "Friss helyszínek minimumtávja", 16, 0, 4096),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.placement.recent-location-share-across-events", "Helymemória eventek között"),
                ConfigMenuGUI.Entry.integer(
                        "world-events.placement.max-concurrent-searches", "Párhuzamos helykeresések", 1, 1, 16),
                ConfigMenuGUI.Entry.integer(
                        "world-events.placement.search-timeout-millis", "Helykeresési timeout (ms)", 250, 250, 30000),
                ConfigMenuGUI.Entry.integer(
                        "world-events.placement.max-chunks-per-search", "Max. chunk keresésenként", 8, 1, 512),
                ConfigMenuGUI.Entry.integer(
                        "world-events.placement.search-backoff-seconds", "Sikertelen keresés pihenője", 5, 0, 3600),
                ConfigMenuGUI.Entry.integer(
                        "world-events.placement.route-attempts", "Útvonal-irány próbák", 1, 1, 32),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.placement.arrival.enabled", "Érkezési előjel"),
                ConfigMenuGUI.Entry.integer(
                        "world-events.placement.arrival.delay-seconds", "Érkezési késleltetés (mp)", 1, 0, 60),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.placement.arrival.particles", "Érkezési részecskék"),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.placement.arrival.sound", "Érkezési hang"),
                ConfigMenuGUI.Entry.toggle(
                        "world-events.placement.arrival.player-hint", "Távoli irányjelzés a játékosoknak")
        );
    }
}
