package hu.taliann.icesmp.spells;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Active-kit/selection transition specification pinned to AbilityCatalystListener's final gate. */
public final class ActiveKitLifecycleRegressionSuite {

    private static int assertions;

    private ActiveKitLifecycleRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        defaultFavoritesAndMaximum();
        loadoutSwitchAndStaleSelection();
        darkSealAndUnseal();
        adminGrantIsStillGrantBound();
        productionSourceMatchesSpecification();
        System.out.println("Active-kit lifecycle regression suite passed. assertions=" + assertions);
    }

    private static void defaultFavoritesAndMaximum() {
        final List<String> unlocked = List.of("base_a", "base_b", "spec_a", "spec_b", "spec_c", "admin_x");
        final Set<String> registry = Set.copyOf(unlocked);
        final List<String> defaults = List.of("base_a", "spec_a", "spec_b", "base_b", "spec_c");
        check(reconcile(unlocked, defaults, registry, 4).equals(List.of("base_a", "spec_a", "spec_b", "base_b")),
                "default kit preserves order and obeys the maximum");

        final List<String> favorites = List.of("spec_c", "base_b");
        check(reconcile(unlocked, favorites, registry, 4).equals(favorites),
                "non-empty favorites override the default proposal without changing order");
        check(reconcile(unlocked, defaults, registry, 4).equals(List.of("base_a", "spec_a", "spec_b", "base_b")),
                "empty favorites deterministically falls back to the default proposal");

        final List<String> invalidFavorite = List.of("missing", "spec_c", "missing", "base_a");
        check(reconcile(unlocked, invalidFavorite, registry, 4).equals(List.of("spec_c", "base_a")),
                "invalid/duplicate favorites cannot enter the active kit");
        check(reconcile(unlocked, List.of("base_a", "spec_a"), registry, 7)
                        .equals(List.of("base_a", "spec_a")),
                "base and current-spec spells coexist in one valid kit");
    }

    private static void loadoutSwitchAndStaleSelection() {
        final Set<String> registry = Set.of("base", "first_a", "first_b", "second_a", "second_b");
        final List<String> firstUnlocked = List.of("base", "first_a", "first_b");
        final List<String> first = reconcile(firstUnlocked, List.of("first_a", "base", "first_b"), registry, 3);
        check(selected("first_b", first).equals("first_b"), "first loadout keeps a valid selected spell");

        final List<String> secondUnlocked = List.of("base", "second_a", "second_b");
        final List<String> second = reconcile(secondUnlocked, List.of("second_a", "base", "second_b"), registry, 3);
        check(!second.contains("first_a") && !second.contains("first_b"),
                "second loadout cannot inherit first-loadout spec spells");
        check(selected("first_b", second).equals("second_a"),
                "spec/loadout switch replaces stale selected spell with first valid member");
    }

    private static void darkSealAndUnseal() {
        final Set<String> registry = Set.of("base", "dark_a", "dark_b");
        final List<String> active = reconcile(List.of("base", "dark_a", "dark_b"),
                List.of("dark_a", "base", "dark_b"), registry, 3);
        check(active.equals(List.of("dark_a", "base", "dark_b")), "ACTIVE DARK loadout exposes its granted spec spells");

        final List<String> sealed = reconcile(List.of("base"),
                List.of("dark_a", "base", "dark_b"), registry, 3);
        check(sealed.equals(List.of("base")), "SEALED spec grants are removed even if the proposed/favorite kit still names them");
        check(selected("dark_a", sealed).equals("base"), "sealed stale selection falls back to a surviving base spell");
        check(selected("dark_a", List.of()).isEmpty(), "fully empty sealed kit has no castable selected spell");

        final List<String> unsealed = reconcile(List.of("base", "dark_a", "dark_b"),
                List.of("dark_a", "base", "dark_b"), registry, 3);
        check(unsealed.equals(active), "unseal deterministically rebuilds the same kit without duplicates");
    }

    private static void adminGrantIsStillGrantBound() {
        final Set<String> registry = Set.of("base", "spec", "admin_spell");
        check(reconcile(List.of("base", "spec", "admin_spell"),
                        List.of("admin_spell", "spec", "base"), registry, 3).getFirst().equals("admin_spell"),
                "an explicitly granted admin spell may enter the kit");
        check(!reconcile(List.of("base", "spec"),
                        List.of("admin_spell", "spec", "base"), registry, 3).contains("admin_spell"),
                "an admin id without a current grant cannot bypass the authority gate");
    }

    /** Mirrors the listener's intentionally tiny final gate; source checks below prevent drift. */
    private static List<String> reconcile(final List<String> unlocked, final List<String> proposed,
                                          final Set<String> registry, final int maximum) {
        final Set<String> grants = new HashSet<>();
        for (final String raw : unlocked) grants.add(normalize(raw));
        final LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (final String raw : proposed) {
            final String id = normalize(raw);
            if (grants.contains(id) && registry.contains(id)) valid.add(id);
            if (valid.size() >= maximum) break;
        }
        return List.copyOf(new ArrayList<>(valid));
    }

    private static String selected(final String selected, final List<String> active) {
        if (active.isEmpty()) return "";
        final String normalized = normalize(selected);
        return active.contains(normalized) ? normalized : active.getFirst();
    }

    private static void productionSourceMatchesSpecification() throws Exception {
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/AbilityCatalystListener.java"))
                .replace("\r\n", "\n");
        check(listener.contains("if (unlocked.contains(id) && spellRegistry.getById(id) != null) valid.add(id);"),
                "production final gate requires both current grant and registry existence");
        check(listener.contains("if (valid.size() >= limit) break;"),
                "production final gate enforces maximum after de-duplication");
        check(listener.contains("final LinkedHashSet<String> valid = new LinkedHashSet<>();"),
                "production final gate de-duplicates while preserving order");
        check(listener.contains("if (!active.contains(selected))")
                        && listener.contains("selected = active.getFirst();")
                        && listener.contains("persistSelectedSpell(player, selected);"),
                "production selected reconciliation uses the same deterministic first fallback");
        check(listener.contains("if (active.isEmpty()) return null;"),
                "production exposes no selected spell for an empty/sealed active kit");

        final String adapter = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java"));
        check(adapter.contains("spellbookStateStore.select(id, \"\")"),
                "seal/fail-closed reconciliation clears durable stale selection when no kit may be active");
        check(adapter.contains("catalyst.getSelectedSpellId(player);"),
                "successful spec/loadout rebuild eagerly reconciles selected spell");

        final String favorites = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/SpellFavoritesManager.java"));
        check(favorites.contains("if (favorites.size() >= maximum)")
                        && favorites.contains("ToggleResult.LIMIT_REACHED"),
                "favorite maximum is enforced by PlayerProfile CAS mutation");
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
