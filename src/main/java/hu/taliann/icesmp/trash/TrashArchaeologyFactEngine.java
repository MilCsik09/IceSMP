package hu.taliann.icesmp.trash;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Bounded evidence-to-fact engine; it never exposes hidden kind or behavior identifiers. */
public final class TrashArchaeologyFactEngine {

    private final TrashCatalog catalog;
    private final TrashItemFactory items;
    private final TrashHistoryService history;

    public TrashArchaeologyFactEngine(final TrashCatalog catalog, final TrashItemFactory items,
                                      final TrashHistoryService history) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.items = Objects.requireNonNull(items, "items");
        this.history = Objects.requireNonNull(history, "history");
    }

    public Optional<Evaluation> evaluate(final ItemStack item, final int archaeologyLevel) {
        if (archaeologyLevel < 0 || archaeologyLevel > 50 || !items.isKnownItem(item)) {
            return Optional.empty();
        }
        final String id = items.idOf(item).orElse(null);
        if (id == null) return Optional.empty();
        final TrashDefinition definition = catalog.require(id);
        final TrashHistoryStore.Snapshot snapshot;
        try {
            snapshot = history.historyOf(item).orElse(null);
        } catch (final RuntimeException malformed) {
            return Optional.empty();
        }
        final long revision = snapshot == null ? 0L : snapshot.revision();
        final String family = family(definition.material());
        final String domain = domain(definition);
        final ArrayList<Fact> candidates = new ArrayList<>();
        candidates.add(new Fact("material", Category.MATERIAL, 0, 1, false,
                materialObservation(definition.material(), family)));

        if (definition.internalKind() == TrashKind.STORY) {
            candidates.add(new Fact("cultural_trace", Category.ORIGIN, 0, 3, true,
                    archaeologyLevel < 12
                            ? "Talán ugyanabból a régi használati körből származik, mint néhány más lelet."
                            : domainObservation(domain)));
        } else if (archaeologyLevel >= 6 && !definition.sourceBias().affinities().isEmpty()) {
            candidates.add(new Fact("wear_domain", Category.ORIGIN, 6, 2, false,
                    domainObservation(domain)));
        }

        if (snapshot != null) addHistoryFacts(snapshot, candidates);
        if (archaeologyLevel >= 30 && isUnnaturalClueCandidate(definition)) {
            candidates.add(new Fact("material_discrepancy", Category.MATERIAL, 30, 4, true,
                    "Az anyag öregedése több ponton nem egyezik a becsült használati korral."));
        }

        final List<Fact> visible = candidates.stream()
                .filter(fact -> fact.minLevel() <= archaeologyLevel)
                .sorted(Comparator.comparingInt(Fact::minLevel).thenComparing(Fact::id))
                .limit(8).toList();
        if (visible.isEmpty()) return Optional.empty();
        final boolean historical = definition.internalKind() == TrashKind.STORY
                || snapshot != null && snapshot.events().stream().anyMatch(event ->
                event.type() != TrashHistoryEvent.ACTIVATED
                        && event.type() != TrashHistoryEvent.TRANSFORMED);
        return Optional.of(new Evaluation(id, revision, family, domain, historical, visible));
    }

    private static void addHistoryFacts(final TrashHistoryStore.Snapshot snapshot,
                                        final List<Fact> facts) {
        if (snapshot.events().stream().anyMatch(event -> event.type() == TrashHistoryEvent.REPAIRED)) {
            facts.add(new Fact("repaired", Category.HISTORY, 8, 3, true,
                    "A tárgyon legalább egy későbbi, eltérő technikájú javítás nyoma látszik."));
        }
        if (snapshot.events().stream().anyMatch(event -> event.type() == TrashHistoryEvent.VENDOR_SOLD
                || event.type() == TrashHistoryEvent.VENDOR_RECYCLED)) {
            facts.add(new Fact("vendor_cycle", Category.PROVENANCE, 10, 3, true,
                    "A felületi szennyeződés alapján hosszabb ideig vegyes raktári készletben állhatott."));
        }
        if (snapshot.events().stream().anyMatch(event -> event.type() == TrashHistoryEvent.HELD_BY_KING)) {
            facts.add(new Fact("royal_contact", Category.PROVENANCE, 15, 5, true,
                    "Egy korábbi használóhoz udvari leltárjelhez hasonló nyom köthető."));
        }
        if (snapshot.events().stream().anyMatch(event -> event.type()
                == TrashHistoryEvent.PRESENT_AT_PLAYER_DEATH)) {
            facts.add(new Fact("death_presence", Category.PROVENANCE, 18, 4, true,
                    "A tárgy szennyeződési rétege egy erőszakos esemény helyszínére utal."));
        }
        if (snapshot.events().stream().anyMatch(event -> event.type() == TrashHistoryEvent.NETHER_TRANSIT)) {
            facts.add(new Fact("nether_transit", Category.PROVENANCE, 20, 4, true,
                    "A felületen rövid, szélsőséges hő- és hamuterhelés nyoma maradt."));
        }
        if (snapshot.events().stream().anyMatch(event -> event.type() == TrashHistoryEvent.TRANSFORMED)) {
            facts.add(new Fact("transformed", Category.HISTORY, 24, 4, true,
                    "A jelenlegi alak nem teljesen egyezik az eredeti anyageloszlással."));
        }
        if (snapshot.owners().size() >= 2) {
            facts.add(new Fact("multiple_owners", Category.PROVENANCE, 25, 3, true,
                    "Az elmúlt időszakban több, egymástól eltérő használati minta rakódott rá."));
        }
    }

    private static boolean isUnnaturalClueCandidate(final TrashDefinition definition) {
        if (definition.internalKind() != TrashKind.ANOMALY
                && definition.internalKind() != TrashKind.TRASH_RELIC) return false;
        return Math.floorMod(definition.id().hashCode(), 3) == 0;
    }

    private static String materialObservation(final Material material, final String family) {
        return switch (family) {
            case "metal" -> "A korrózió és a felületi kopás több, eltérő használati időszakot jelez.";
            case "wood" -> "A rostok kiszáradása alapján a tárgy hosszú ideig fedetlen helyen állhatott.";
            case "textile" -> "A szálak közt többféle por- és koromréteg rakódott le.";
            case "glass" -> "Az üveg apró zárványai kézi, egyenetlen hőkezelésre utalnak.";
            case "stone" -> "Az élek lekerekedése ismételt szállításra és nedvességre utal.";
            case "paper" -> "A rostok és a hajtásnyomok többszöri használatot mutatnak.";
            default -> "Az anyag állapota hosszú és változatos használati múltra utal.";
        };
    }

    private static String domainObservation(final String domain) {
        return switch (domain) {
            case "fish", "wet" -> "A lerakódások tartós vízközeli használatra utalnak.";
            case "nether", "hot" -> "A felület rövid, ismétlődő szélsőséges hőterhelést kapott.";
            case "deep", "underground" -> "A pórusokban mélyből származó ásványi por maradt.";
            case "dark" -> "A felületi viasz és korom fénytől védett tárolásra utal.";
            case "undead" -> "A szerves maradványok temetkezési környezetből származhatnak.";
            case "humanoid" -> "A kopás rendszeres, kézben végzett használatra utal.";
            case "ambient", "open_sky" -> "Az időjárási kopás hosszú, szabadtéri hányódást jelez.";
            default -> "A készítési és használati nyomok nem köthetők biztosan egyetlen tájhoz.";
        };
    }

    private static String family(final Material material) {
        final String name = material.name();
        if (containsAny(name, "IRON", "GOLD", "COPPER", "NETHERITE", "CHAIN", "BUCKET")) {
            return "metal";
        }
        if (containsAny(name, "WOOD", "LOG", "PLANK", "STICK", "BAMBOO")) return "wood";
        if (containsAny(name, "WOOL", "LEATHER", "STRING", "CARPET")) return "textile";
        if (containsAny(name, "GLASS", "BOTTLE")) return "glass";
        if (containsAny(name, "STONE", "BRICK", "DEEPSLATE", "COBBLE", "FLINT")) return "stone";
        if (containsAny(name, "PAPER", "MAP", "BOOK")) return "paper";
        return "organic";
    }

    private static boolean containsAny(final String value, final String... tokens) {
        for (final String token : tokens) if (value.contains(token)) return true;
        return false;
    }

    private static String domain(final TrashDefinition definition) {
        return definition.sourceBias().affinities().stream().sorted().findFirst()
                .orElse("global").toLowerCase(Locale.ROOT);
    }

    public enum Category { MATERIAL, ORIGIN, HISTORY, PROVENANCE }

    public record Fact(String id, Category category, int minLevel, int insight,
                       boolean higherOrder, String text) {
        public Fact {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(text, "text");
            if (minLevel < 0 || minLevel > 50 || insight < 0 || insight > 5) {
                throw new IllegalArgumentException("invalid Archaeology fact");
            }
        }
    }

    public record Evaluation(String trashId, long historyRevision, String family, String domain,
                             boolean historical, List<Fact> facts) {
        public Evaluation {
            facts = List.copyOf(facts);
        }

        public String signature(final Fact fact) {
            return trashId + "@" + historyRevision + ":" + fact.id();
        }

        public TrashArchaeologyProfileStore.Evidence evidence() {
            return new TrashArchaeologyProfileStore.Evidence(family, domain, historical,
                    facts.stream().map(fact -> new TrashArchaeologyProfileStore.Discovery(
                            signature(fact), fact.insight(), fact.higherOrder())).toList());
        }
    }
}
