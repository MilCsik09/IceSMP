package hu.taliann.icesmp.classspec.profile;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.MigrationState;
import hu.taliann.icesmp.classspec.domain.ProfileDiagnostics;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.domain.SealCause;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.classspec.domain.SoulbondState;
import hu.taliann.icesmp.classspec.persistence.ClassProfileCodec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;

/** Dependency-free executable regressions for the Profile v2 domain and ICS2 codec. */
public final class ClassProfileV2RegressionSuite {

    private ClassProfileV2RegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        domainInvariants();
        codecRoundTripAndDeterminism();
        codecIntegrityFailures();
        codecStructuralFailures();
        codecBounds();
        System.out.println("ClassProfile v2 domain/codec regression tests passed.");
    }

    private static void domainInvariants() {
        check(ClassSpecCatalog.classIds().size() == 13
                        && ClassSpecCatalog.specializationIds().size() == 35,
                "canonical catalogue must contain exactly 13 classes and 35 specializations");
        final ClassProfile empty = ClassProfile.empty(0L);
        check(empty.schemaVersion() == 2 && empty.revision() == 0L,
                "empty profile must start as schema 2/revision 0");
        check(empty.loadouts().size() == 2 && empty.activeSlot() == null,
                "empty profile must have exactly two inactive slots");

        final ClassProfile active = wizardProfile(activeLoadout("necromancer"), ClassLoadout.empty(),
                LoadoutSlot.FIRST, false);
        check(active.loadout(LoadoutSlot.FIRST).status() == LoadoutStatus.ACTIVE,
                "one active slot must be accepted");

        expectThrows(IllegalArgumentException.class, () -> wizardProfile(
                activeLoadout("necromancer"), activeLoadout("elementalist"),
                LoadoutSlot.FIRST, true));
        expectThrows(IllegalArgumentException.class, () -> wizardProfile(
                inactiveLoadout("necromancer"), inactiveLoadout("necromancer"), null, true));
        expectThrows(IllegalArgumentException.class, () -> wizardProfile(
                inactiveLoadout("guardian"), ClassLoadout.empty(), null, false));
        expectThrows(IllegalArgumentException.class, () -> wizardProfile(
                inactiveLoadout("necromancer"), inactiveLoadout("elementalist"), null, false));

        expectThrows(IllegalArgumentException.class, () -> new ClassLoadout("", LoadoutStatus.EMPTY,
                null, Map.of(), MasteryProgress.empty(), null, Set.of("hidden_spell"), "",
                CapstoneStatus.LOCKED, Map.of(), Map.of(), ""));

        final SealReason factionSeal = new SealReason(SealCause.FACTION_MISSING, "dark", "left faction");
        final ClassLoadout sealed = inactiveLoadout("necromancer")
                .withStatus(LoadoutStatus.SEALED, factionSeal);
        expectThrows(IllegalArgumentException.class, () -> wizardProfile(
                sealed, ClassLoadout.empty(), LoadoutSlot.FIRST, false));

        final MigrationState review = new MigrationState("legacy-v1", List.of("unknown spec"),
                Map.of("class_spec", "future_spec"));
        expectThrows(IllegalArgumentException.class, () -> ClassProfile.builder()
                .revision(0).status(ProfileStatus.MIGRATION_REVIEW)
                .primaryClassId("wizard").classLevel(25)
                .loadout(LoadoutSlot.FIRST, activeLoadout("necromancer"))
                .activeSlot(LoadoutSlot.FIRST).migrationState(review).build());

        final ClassProfile migrationReview = ClassProfile.builder().revision(0)
                .status(ProfileStatus.MIGRATION_REVIEW).migrationState(review).build();
        expectThrows(IllegalStateException.class, migrationReview::withoutClass);
        expectThrows(IllegalStateException.class, migrationReview::toBuilder);

        final ClassProfile corrupt = ClassProfile.builder().revision(0)
                .status(ProfileStatus.CORRUPT_QUARANTINE)
                .diagnostics(new ProfileDiagnostics("checksum mismatch", "decode blocked")).build();
        expectThrows(IllegalStateException.class, corrupt::withoutClass);
        expectThrows(IllegalStateException.class, corrupt::toBuilder);

        expectThrows(IllegalArgumentException.class, () -> ClassProfile.builder().revision(0)
                .primaryClassId("wizard").classLevel(25)
                .loadout(LoadoutSlot.FIRST, inactiveLoadout("necromancer")
                        .withStatus(LoadoutStatus.MIGRATION_REVIEW, null))
                .build());

        expectThrows(IllegalArgumentException.class, () -> new MasteryProgress(-1, 0));
        expectThrows(IllegalArgumentException.class, () -> new MasteryProgress(11, 0));
        expectThrows(IllegalArgumentException.class, () -> new MasteryProgress(0, -1));

        expectThrows(IllegalArgumentException.class, () -> new ClassLoadout("guardian",
                LoadoutStatus.INACTIVE, null, Map.of(), MasteryProgress.empty(), null, Set.of(), "",
                CapstoneStatus.LOCKED, Map.of(), Map.of("soulforge.health_rank", "1"), ""));

        final CompanionProfile leaked = companion("beast_master.stable");
        expectThrows(IllegalArgumentException.class, () -> new ClassLoadout("guardian",
                LoadoutStatus.INACTIVE, null, Map.of(), MasteryProgress.empty(), null, Set.of(), "",
                CapstoneStatus.LOCKED, Map.of(leaked.companionId(), leaked), Map.of(), ""));
        final CompanionProfile beast = companion("beast_master.stable");
        final ClassLoadout beastLoadout = new ClassLoadout("beast_master", LoadoutStatus.INACTIVE,
                null, Map.of(), MasteryProgress.empty(), null, Set.of(), "", CapstoneStatus.LOCKED,
                Map.of(beast.companionId(), beast), Map.of(), "");
        final ClassProfile archer = ClassProfile.builder().revision(0).primaryClassId("archer")
                .classLevel(25).loadout(LoadoutSlot.FIRST, beastLoadout).build();
        check(archer.loadout(LoadoutSlot.FIRST).companionRoster().size() == 1,
                "correctly namespaced Beast Master roster must survive");

        final ClassProfile migratedActive = active.toBuilder()
                .migrationState(new MigrationState("legacy-pdc-v1", List.of(),
                        Map.of("legacy.marker", "retained")))
                .build();
        final ClassProfile reset = migratedActive.withoutClass();
        check(reset.revision() == active.revision() + 1 && reset.primaryClassId().isEmpty(),
                "normal reset must increment revision and clear only a READY profile");
        check(reset.migrationState().equals(migratedActive.migrationState()),
                "normal reset must retain the migration idempotency marker");

        final Map<String, String> normalizedCollision = new LinkedHashMap<>();
        normalizedCollision.put("Legacy.Key", "one");
        normalizedCollision.put("legacy.key", "two");
        expectThrows(IllegalArgumentException.class,
                () -> new MigrationState("", List.of(), normalizedCollision));
    }

    private static void codecRoundTripAndDeterminism() throws Exception {
        final ClassProfileCodec codec = new ClassProfileCodec();
        final ClassProfile profile = complexProfile(false);
        final byte[] first = codec.encode(profile);
        check(profile.equals(codec.decode(first)), "full Profile v2 roundtrip must be exact");
        check(Arrays.equals(first, codec.encode(profile)), "same profile must encode deterministically");

        final ClassProfile reordered = complexProfile(true);
        check(profile.equals(reordered), "map/set insertion order must not affect domain equality");
        check(Arrays.equals(first, codec.encode(reordered)),
                "map/set insertion order must not affect encoded bytes");
    }

    private static void codecIntegrityFailures() {
        final ClassProfileCodec codec = new ClassProfileCodec();
        final byte[] valid = codec.encode(complexProfile(false));

        final byte[] badChecksum = valid.clone();
        badChecksum[badChecksum.length - 1] ^= 0x01;
        expectDecodeFailure(codec, badChecksum, "checksum");
        expectDecodeFailure(codec, Arrays.copyOf(valid, valid.length - 1), "truncated");

        final byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        expectDecodeFailure(codec, trailing, "trailing");

        final byte[] unknownEnum = replaceAscii(valid, "READY", "BOGUS");
        refreshChecksum(unknownEnum);
        expectDecodeFailure(codec, unknownEnum, "unknown enum");

        final byte[] badUtf8 = valid.clone();
        final int wizard = find(badUtf8, "wizard".getBytes(StandardCharsets.UTF_8), 0);
        check(wizard >= 0, "wizard fixture marker must exist");
        badUtf8[wizard] = (byte) 0xc3;
        badUtf8[wizard + 1] = 0x28;
        refreshChecksum(badUtf8);
        expectDecodeFailure(codec, badUtf8, "invalid UTF-8");
    }

    private static void codecStructuralFailures() {
        final ClassProfileCodec codec = new ClassProfileCodec();
        final byte[] valid = codec.encode(complexProfile(false));

        final byte[] repeated = replaceAscii(valid, "two", "one");
        refreshChecksum(repeated);
        expectDecodeFailure(codec, repeated, "repeated map key");

        final byte[] normalizedCollision = replaceAscii(valid, "two", "ONE");
        refreshChecksum(normalizedCollision);
        expectDecodeFailure(codec, normalizedCollision, "normalized map collision");

        final byte[] negativeString = valid.clone();
        putInt(negativeString, 24, -1);
        refreshChecksum(negativeString);
        expectDecodeFailure(codec, negativeString, "negative string length");

        final byte[] largeString = valid.clone();
        putInt(largeString, 24, ClassProfileCodec.Limits.defaults().maxStringBytes() + 1);
        refreshChecksum(largeString);
        expectDecodeFailure(codec, largeString, "oversized string length");

        final byte[] largePayload = valid.clone();
        putInt(largePayload, 8, ClassProfileCodec.Limits.defaults().maxPayloadBytes() + 1);
        expectDecodeFailure(codec, largePayload, "oversized payload length");

        assertOversizedCollectionRejected(codec, valid, "doctrine_alpha", "map");
        assertOversizedCollectionRejected(codec, valid, "module_alpha", "list");
        assertOversizedCollectionRejected(codec, valid, "favorite_alpha", "set");
    }

    private static void codecBounds() {
        final ClassProfileCodec constrained = new ClassProfileCodec(
                new ClassProfileCodec.Limits(4_096, 128, 2));
        final ClassProfile longDiagnostic = ClassProfile.builder().revision(0)
                .diagnostics(new ProfileDiagnostics("", "x".repeat(129))).build();
        expectThrows(IllegalArgumentException.class, () -> constrained.encode(longDiagnostic));

        final ClassLoadout tooManyDoctrines = loadoutWithCollections(
                Map.of("a", "1", "b", "2", "c", "3"), List.of("module"), Set.of("spell"));
        expectThrows(IllegalArgumentException.class,
                () -> constrained.encode(wizardProfile(tooManyDoctrines, ClassLoadout.empty(), null, false)));

        final ClassLoadout tooManyModules = loadoutWithCollections(Map.of("a", "1"),
                List.of("a", "b", "c"), Set.of("spell"));
        expectThrows(IllegalArgumentException.class,
                () -> constrained.encode(wizardProfile(tooManyModules, ClassLoadout.empty(), null, false)));

        final ClassLoadout tooManyFavorites = loadoutWithCollections(Map.of("a", "1"),
                List.of("module"), Set.of("a", "b", "c"));
        expectThrows(IllegalArgumentException.class,
                () -> constrained.encode(wizardProfile(tooManyFavorites, ClassLoadout.empty(), null, false)));
    }

    private static ClassProfile complexProfile(final boolean reverse) {
        final Map<String, String> doctrines = new LinkedHashMap<>();
        if (reverse) {
            doctrines.put("two", "value_b");
            doctrines.put("one", "value_a");
            doctrines.put("doctrine_alpha", "left");
        } else {
            doctrines.put("doctrine_alpha", "left");
            doctrines.put("one", "value_a");
            doctrines.put("two", "value_b");
        }
        final Set<String> favorites = new LinkedHashSet<>();
        if (reverse) {
            favorites.add("shadow_bolt");
            favorites.add("favorite_alpha");
        } else {
            favorites.add("favorite_alpha");
            favorites.add("shadow_bolt");
        }
        final CompanionProfile companion = companion("necromancer.court");
        final ClassLoadout loadout = new ClassLoadout("necromancer", LoadoutStatus.ACTIVE, null,
                doctrines, new MasteryProgress(7, 12_345L),
                new SoulbondState(UUID.fromString("10000000-0000-0000-0000-000000000001"), 3,
                        List.of("module_alpha", "soul_lens"), 9L, "rebound"),
                favorites, "shadow_bolt", CapstoneStatus.IN_PROGRESS,
                Map.of(companion.companionId(), companion),
                Map.of("soulforge.health_rank", "3", "necromancer.court.bond", "steady"), "");
        return ClassProfile.builder().revision(12L).primaryClassId("wizard").classLevel(37)
                .activeSlot(LoadoutSlot.FIRST).loadout(LoadoutSlot.FIRST, loadout)
                .migrationState(new MigrationState("legacy-pdc-v1", List.of(),
                        Map.of("legacy_note", "preserved"))).build();
    }

    private static ClassLoadout loadoutWithCollections(final Map<String, String> doctrines,
                                                       final List<String> modules,
                                                       final Set<String> favorites) {
        return new ClassLoadout("necromancer", LoadoutStatus.INACTIVE, null, doctrines,
                MasteryProgress.empty(),
                new SoulbondState(UUID.fromString("20000000-0000-0000-0000-000000000002"), 0,
                        modules, 0L, ""), favorites, "", CapstoneStatus.LOCKED,
                Map.of(), Map.of(), "");
    }

    private static ClassProfile wizardProfile(final ClassLoadout first, final ClassLoadout second,
                                              final LoadoutSlot activeSlot,
                                              final boolean secondUnlocked) {
        return ClassProfile.builder().revision(3L).primaryClassId("wizard").classLevel(25)
                .activeSlot(activeSlot).secondSpecUnlocked(secondUnlocked)
                .loadout(LoadoutSlot.FIRST, first).loadout(LoadoutSlot.SECOND, second).build();
    }

    private static ClassLoadout activeLoadout(final String specId) {
        return baseLoadout(specId, LoadoutStatus.ACTIVE);
    }

    private static ClassLoadout inactiveLoadout(final String specId) {
        return baseLoadout(specId, LoadoutStatus.INACTIVE);
    }

    private static ClassLoadout baseLoadout(final String specId, final LoadoutStatus status) {
        return new ClassLoadout(specId, status, null, Map.of(), MasteryProgress.empty(), null,
                Set.of(), "", CapstoneStatus.LOCKED, Map.of(), Map.of(), "");
    }

    private static CompanionProfile companion(final String namespace) {
        final UUID id = namespace.startsWith("beast")
                ? UUID.fromString("30000000-0000-0000-0000-000000000003")
                : UUID.fromString("40000000-0000-0000-0000-000000000004");
        return new CompanionProfile(id, namespace, "zombie", "Morzsa", 8, 420L,
                "sturdy", "ACTIVE", List.of("bone_armor"), 1_900_000_000_000L,
                Map.of("mutation", "none"));
    }

    private static void assertOversizedCollectionRejected(final ClassProfileCodec codec,
                                                           final byte[] valid, final String marker,
                                                           final String label) {
        final byte[] mutated = valid.clone();
        final int markerOffset = find(mutated, marker.getBytes(StandardCharsets.UTF_8), 0);
        check(markerOffset >= 8, label + " fixture marker must exist");
        putInt(mutated, markerOffset - 8,
                ClassProfileCodec.Limits.defaults().maxCollectionEntries() + 1);
        refreshChecksum(mutated);
        expectDecodeFailure(codec, mutated, "oversized " + label);
    }

    private static byte[] replaceAscii(final byte[] source, final String from, final String to) {
        final byte[] needle = from.getBytes(StandardCharsets.UTF_8);
        final byte[] replacement = to.getBytes(StandardCharsets.UTF_8);
        check(needle.length == replacement.length, "fixture replacement lengths must match");
        final byte[] result = source.clone();
        final int offset = find(result, needle, 0);
        check(offset >= 0, "fixture marker missing: " + from);
        System.arraycopy(replacement, 0, result, offset, replacement.length);
        return result;
    }

    private static int find(final byte[] haystack, final byte[] needle, final int start) {
        outer:
        for (int index = Math.max(0, start); index <= haystack.length - needle.length; index++) {
            for (int part = 0; part < needle.length; part++) {
                if (haystack[index + part] != needle[part]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }

    private static void refreshChecksum(final byte[] bytes) {
        final int payloadLength = readInt(bytes, 8);
        final int checksumOffset = 12 + payloadLength;
        check(checksumOffset + 4 == bytes.length, "fixture envelope length must remain valid");
        final CRC32 crc = new CRC32();
        crc.update(bytes, 0, checksumOffset);
        putInt(bytes, checksumOffset, (int) crc.getValue());
    }

    private static int readInt(final byte[] bytes, final int offset) {
        return ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
    }

    private static void putInt(final byte[] bytes, final int offset, final int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void expectDecodeFailure(final ClassProfileCodec codec, final byte[] payload,
                                            final String label) {
        expectThrows(ClassProfileCodec.DecodeException.class, () -> codec.decode(payload));
    }

    private static <T extends Throwable> T expectThrows(final Class<T> type,
                                                        final ThrowingRunnable action) {
        try {
            action.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError("Expected " + type.getName() + " but got " + thrown, thrown);
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
