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

    public boolean isCatalogued(final ItemStack item) { return items.isKnownItem(item); }

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
        return evaluate(definition, Optional.ofNullable(snapshot), archaeologyLevel);
    }

    /** Pure read of already detached native evidence; grants neither discoveries nor progression. */
    public static Optional<Evaluation> evaluate(final TrashDefinition definition,
                                               final Optional<TrashHistoryStore.Snapshot> history,
                                               final int archaeologyLevel) {
        Objects.requireNonNull(definition); Objects.requireNonNull(history);
        if (archaeologyLevel < 0 || archaeologyLevel > 50) return Optional.empty();
        final String id = definition.id();
        final TrashHistoryStore.Snapshot snapshot = history.orElse(null);
        if (snapshot != null && !snapshot.baseId().equals(id)) return Optional.empty();
        final long revision = snapshot == null ? 0L : snapshot.revision();
        final TrashArchaeologyEvidence authored = definition.archaeology();
        if (authored == null) return Optional.empty();
        final String family = authored.family();
        final String domain = authored.domain();
        final ArrayList<Fact> candidates = new ArrayList<>();
        candidates.add(new Fact("material", Category.MATERIAL, 0, 1, false, 0L,
                snapshot != null && !"base".equals(snapshot.phase())
                        ? "A megváltozott alak mellett az eredeti tárgy megmunkálásának nyomai is felismerhetők."
                        : authored.material()));
        for (int index = 0; index < authored.facts().size(); index++) {
            candidates.add(new Fact("authored_" + index, index == 0 ? Category.MATERIAL : Category.ORIGIN,
                    index < 2 ? 0 : 8 + (index - 2) * 6, index == 0 ? 2 : 3, index > 0, 0L,
                    authored.facts().get(index)));
        }
        if (snapshot != null) addHistoryFacts(snapshot, candidates);
        if (archaeologyLevel >= 30 && !authored.discrepancy().isBlank()) {
            candidates.add(new Fact("material_discrepancy", Category.MATERIAL, 30, 4, true, 0L,
                    authored.discrepancy()));
        }

        final List<Fact> visible = candidates.stream()
                .filter(fact -> fact.minLevel() <= archaeologyLevel)
                .sorted(Comparator.comparingInt(Fact::minLevel).thenComparing(Fact::id))
                .limit(8).toList();
        if (visible.isEmpty()) return Optional.empty();
        final boolean historical = definition.internalKind() == TrashKind.STORY
                || snapshot != null && snapshot.events().stream().anyMatch(event ->
                !event.type().developer() && event.type() != TrashHistoryEvent.ACTIVATED
                        && event.type() != TrashHistoryEvent.TRANSFORMED);
        return Optional.of(new Evaluation(id, revision, family, domain, historical, visible));
    }

    private static void addHistoryFacts(final TrashHistoryStore.Snapshot snapshot,
                                        final List<Fact> facts) {
        final long repaired = firstRevision(snapshot, TrashHistoryEvent.REPAIRED);
        if (repaired > 0L) {
            facts.add(new Fact("repaired", Category.HISTORY, 8, 3, true, repaired,
                    "A tárgyon legalább egy későbbi, eltérő technikájú javítás nyoma látszik."));
        }
        final long vendorCycle = firstRevision(snapshot, TrashHistoryEvent.VENDOR_SOLD,
                TrashHistoryEvent.VENDOR_RECYCLED);
        if (vendorCycle > 0L) {
            facts.add(new Fact("vendor_cycle", Category.PROVENANCE, 10, 3, true, vendorCycle,
                    "A felületi szennyeződés alapján hosszabb ideig vegyes raktári készletben állhatott."));
        }
        final long royalContact = firstRevision(snapshot, TrashHistoryEvent.HELD_BY_KING);
        if (royalContact > 0L) {
            facts.add(new Fact("royal_contact", Category.PROVENANCE, 15, 5, true, royalContact,
                    "Egy korábbi használóhoz udvari leltárjelhez hasonló nyom köthető."));
        }
        final long deathPresence = firstRevision(snapshot,
                TrashHistoryEvent.PRESENT_AT_PLAYER_DEATH);
        if (deathPresence > 0L) {
            facts.add(new Fact("death_presence", Category.PROVENANCE, 18, 4, true,
                    deathPresence,
                    "A tárgy szennyeződési rétege egy erőszakos esemény helyszínére utal."));
        }
        final long netherTransit = firstRevision(snapshot, TrashHistoryEvent.NETHER_TRANSIT);
        if (netherTransit > 0L) {
            facts.add(new Fact("nether_transit", Category.PROVENANCE, 20, 4, true,
                    netherTransit,
                    "A felületen rövid, szélsőséges hő- és hamuterhelés nyoma maradt."));
        }
        final long transformed = firstRevision(snapshot, TrashHistoryEvent.TRANSFORMED);
        if (transformed > 0L) {
            facts.add(new Fact("transformed", Category.HISTORY, 24, 4, true, transformed,
                    "A jelenlegi alak nem teljesen egyezik az eredeti anyageloszlással."));
        }
        if (snapshot.owners().size() >= 2) {
            facts.add(new Fact("multiple_owners", Category.PROVENANCE, 25, 3, true, 0L,
                    "Az elmúlt időszakban több, egymástól eltérő használati minta rakódott rá."));
        }
    }

    private static long firstRevision(final TrashHistoryStore.Snapshot snapshot,
                                      final TrashHistoryEvent... types) {
        final java.util.Set<TrashHistoryEvent> accepted = java.util.Set.of(types);
        return snapshot.events().stream().filter(event -> accepted.contains(event.type()))
                .mapToLong(TrashHistoryStore.HistoryEntry::revision).min().orElse(0L);
    }

    public enum Category { MATERIAL, ORIGIN, HISTORY, PROVENANCE }

    public record Fact(String id, Category category, int minLevel, int insight,
                       boolean higherOrder, long evidenceRevision, String text) {
        public Fact {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(text, "text");
            if (minLevel < 0 || minLevel > 50 || insight < 0 || insight > 5
                    || evidenceRevision < 0L) {
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
            return trashId + "@" + fact.evidenceRevision() + ":" + fact.id();
        }

        public TrashArchaeologyProfileStore.Evidence evidence() {
            return new TrashArchaeologyProfileStore.Evidence(
                    trashId, family, domain, historical,
                    facts.stream().map(fact -> new TrashArchaeologyProfileStore.Discovery(
                            signature(fact), fact.insight(), fact.higherOrder())).toList());
        }
    }
}
