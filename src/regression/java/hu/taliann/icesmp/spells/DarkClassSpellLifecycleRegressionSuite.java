package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.SealCause;
import hu.taliann.icesmp.classspec.domain.SealReason;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cross-system DARK lifecycle regression for all five gated specializations. */
public final class DarkClassSpellLifecycleRegressionSuite {

    private static final Map<String, String> DARK_SPELLS = Map.of(
            "necromancer", "life_drain",
            "plaguebringer", "plague_cut",
            "unholy", "festering_strike",
            "bone_priest", "bone_mend",
            "demonologist", "fel_bolt");
    private static int assertions;

    private DarkClassSpellLifecycleRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        allDarkLoadoutsSealAndUnsealDeterministically();
        runtimeProjectionCleanupAndRegrantOrdering();
        activeKitFavoritesAndSelectedSpellAreFailClosed();
        System.out.println("DARK class/spell lifecycle regression suite passed. assertions=" + assertions);
    }

    private static void allDarkLoadoutsSealAndUnsealDeterministically() {
        for (final Map.Entry<String, String> entry : DARK_SPELLS.entrySet()) {
            final String spec = entry.getKey();
            final String spell = entry.getValue();
            final String namespace = ClassSpecCatalog.companionNamespace(spec);
            final Map<UUID, CompanionProfile> roster = durableRoster(spec);
            final Map<String, String> mechanics = "necromancer".equals(spec)
                    ? Map.of("necromancer.soulforge.shards", "5")
                    : Map.of(spec + ".audit.marker", "preserved");
            final ClassLoadout active = new ClassLoadout(
                    spec, LoadoutStatus.ACTIVE, null,
                    Map.of("level_30", "audit_choice"), new MasteryProgress(2, 210), null,
                    Set.of(spell), spell, CapstoneStatus.AVAILABLE,
                    roster, mechanics, "");

            check(active.isActivatable(), spec + " starts ACTIVE");
            if (namespace != null) {
                check(ClassSpecCatalog.companionProjection(active, namespace).size() == roster.size(),
                        spec + " ACTIVE projects exactly its durable companions");
            }

            final SealReason reason = new SealReason(
                    SealCause.FACTION_MISSING, "dark", "audit gate closed");
            final ClassLoadout sealed = active.withStatus(LoadoutStatus.SEALED, reason);
            check(!sealed.isActivatable(), spec + " SEALED is not activatable/castable");
            check(sealed.favoriteSpells().equals(active.favoriteSpells()),
                    spec + " durable favorites survive sealing without becoming runtime grants");
            check(sealed.selectedSpell().equals(active.selectedSpell()),
                    spec + " slot-local durable selected metadata survives for deterministic rebuild");
            check(sealed.mechanicState().equals(active.mechanicState()),
                    spec + " durable mechanic state survives sealing");
            check(sealed.companionRoster().equals(active.companionRoster()),
                    spec + " durable companion roster survives sealing");
            if (namespace != null) {
                check(ClassSpecCatalog.companionProjection(sealed, namespace).isEmpty(),
                        spec + " SEALED projects zero runtime companions");
            }

            final ClassLoadout unsealed = sealed.withStatus(LoadoutStatus.INACTIVE, null);
            check(unsealed.isActivatable(), spec + " becomes activatable after gate recovery");
            check(unsealed.companionRoster().size() == roster.size(),
                    spec + " unseal does not duplicate durable companions");
            if (namespace != null) {
                check(ClassSpecCatalog.companionProjection(unsealed, namespace).isEmpty(),
                        spec + " INACTIVE unsealed loadout still projects zero companions");
            }
            final ClassLoadout rebuilt = unsealed.withStatus(LoadoutStatus.ACTIVE, null);
            check(rebuilt.companionRoster().equals(roster),
                    spec + " deterministic reactivation restores exactly the same roster");
            check(rebuilt.mechanicState().equals(mechanics),
                    spec + " deterministic reactivation restores mechanic state");
            if (namespace != null) {
                check(ClassSpecCatalog.companionProjection(rebuilt, namespace).size() == roster.size(),
                        spec + " reactivation projects the roster exactly once");
            }
        }
    }

    private static Map<UUID, CompanionProfile> durableRoster(final String spec) {
        final String namespace = ClassSpecCatalog.companionNamespace(spec);
        if (namespace == null) return Map.of();
        final UUID id = UUID.nameUUIDFromBytes(("audit:" + spec).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final CompanionProfile companion = new CompanionProfile(
                id, namespace, "ZOMBIE", "Audit", 1, 0, "", "ACTIVE", List.of(), 0, Map.of());
        final Map<UUID, CompanionProfile> roster = new LinkedHashMap<>();
        roster.put(id, companion);
        return Map.copyOf(roster);
    }

    private static void runtimeProjectionCleanupAndRegrantOrdering() throws Exception {
        final String adapter = normalized(
                "src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java");
        final int revoke = adapter.indexOf("jobs.revokeGrantsFromV2(player, revoke)");
        final int cleanup = adapter.indexOf("() -> clearUuidOnly(id, kind, true)", revoke);
        final int clearSelection = adapter.indexOf(
                "spellbookStateStore.selectWhile(id, \"\", () -> current(id, token))", cleanup);
        final int baseRegrant = adapter.indexOf("jobs.applyAutoUnlocksV2(player, durable)", cleanup);
        final int specRegrant = adapter.indexOf("specs.applyClassSpecializationUnlocksV2(player, durable)", baseRegrant);
        final int post = adapter.indexOf("() -> postReconcile.accept(player)", specRegrant);
        check(revoke >= 0 && cleanup > revoke, "old BASE/SPEC grants are revoked before runtime cleanup");
        check(baseRegrant > cleanup && specRegrant > baseRegrant && post > specRegrant,
                "runtime cleanup precedes deterministic BASE/SPEC rebuild and post-reconcile");
        check(clearSelection > cleanup && clearSelection < baseRegrant,
                "sealed/fail-closed profile clears durable selected spell before any possible regrant");
        check(adapter.contains("minions.removeAllOwned(id);"),
                "seal/loadout reconciliation despawns transient minion entities");
        check(adapter.indexOf("minions.removeAllOwned(id);") < adapter.indexOf("for (final Spell spell : spells.getAll())"),
                "minions are removed before spell transient-state cleanup completes");
        check(adapter.contains("catalyst.getSelectedSpellId(player);")
                        && adapter.indexOf("catalyst.getSelectedSpellId(player);")
                        < adapter.indexOf("catalyst.refreshSoulbond(player);"),
                "successful rebuild reconciles selected spell before Soulbond presentation refresh");
    }

    private static void activeKitFavoritesAndSelectedSpellAreFailClosed() throws Exception {
        final String listener = normalized(
                "src/main/java/hu/taliann/icesmp/listeners/AbilityCatalystListener.java");
        check(listener.contains("final int limit = activeKitLimit(player);"),
                "active kit has one final maximum gate");
        check(listener.contains("if (unlocked.contains(id) && spellRegistry.getById(id) != null) valid.add(id);"),
                "favorite/default proposals cannot bypass current grants or registry existence");
        check(listener.contains("if (valid.size() >= limit) break;"),
                "favorites/default/admin grants cannot exceed active-kit maximum");
        check(listener.contains("if (!active.contains(selected))")
                        && listener.contains("selected = active.getFirst();")
                        && listener.contains("persistSelectedSpell(player, selected);"),
                "stale selected spell deterministically moves to first valid active-kit member");
        check(listener.contains("if (active.isEmpty()) return null;"),
                "empty/sealed kit exposes no selected spell to cast");

        final String adapter = normalized(
                "src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java");
        check(adapter.contains("source.startsWith(JobManager.SOURCE_BASE_PREFIX)")
                        && adapter.contains("source.startsWith(JobManager.SOURCE_SPEC_PREFIX)"),
                "seal/spec transition revokes derived BASE/SPEC grants only");
        final String grantSuite = normalized(
                "src/regression/java/hu/taliann/icesmp/classspec/domain/SpellGrantLedgerRegressionSuite.java");
        check(grantSuite.contains("admin retained") && grantSuite.contains("quest retained")
                        && grantSuite.contains("talent retained"),
                "ADMIN/QUEST/TALENT provenance survives scoped SPEC revocation");

        final String favorites = normalized(
                "src/main/java/hu/taliann/icesmp/managers/SpellFavoritesManager.java");
        check(favorites.contains("if (favorites.size() >= maximum)")
                        && favorites.contains("ToggleResult.LIMIT_REACHED"),
                "favorite maximum is enforced atomically inside PlayerProfile CAS");
    }

    private static String normalized(final String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
