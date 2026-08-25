package hu.taliann.icesmp.hardening;

import hu.taliann.icesmp.data.SpellSchool;
import hu.taliann.icesmp.itemization.ItemTemplateRegistry;
import hu.taliann.icesmp.managers.AdvancementService;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.progression.ItemAcquisitionPolicy;
import hu.taliann.icesmp.utils.SpellDamageUtil;
import org.bukkit.GameMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Behavioral/domain regressions for the bounded gameplay/bootstrap finding closure. */
public final class GameplayBootstrapIntegrityRegressionSuite {

    private static int assertions;

    private GameplayBootstrapIntegrityRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        acquisitionPolicyRejectsReplayAndNonPlayerTransfers();
        projectileSnapshotScalesExactlyOnce();
        projectileCanonicalPathIsSingular();
        identitySafeFacadeTeardown(ConfigManager.class, "active", "current");
        identitySafeFacadeTeardown(AdvancementService.class, "instance", null);
        identitySafeFacadeTeardown(ItemTemplateRegistry.class, "activeInstance", "current");
        executableDependencyContractIsBounded();
        advancementInventoryHasNoOrphans();
        System.out.println("Gameplay/bootstrap integrity regression suite passed. assertions=" + assertions);
    }

    private static void acquisitionPolicyRejectsReplayAndNonPlayerTransfers() {
        check(ItemAcquisitionPolicy.acceptedPickupAmount(
                GameMode.SURVIVAL, false, false, 32, 0) == 32,
                "full survival pickup must count its transferred stack once");
        check(ItemAcquisitionPolicy.acceptedPickupAmount(
                GameMode.ADVENTURE, false, false, 32, 17) == 15,
                "partial stack merge must count only the transferred amount");
        check(ItemAcquisitionPolicy.acceptedPickupAmount(
                GameMode.SURVIVAL, true, false, 32, 0) == 0,
                "cancelled pickup advanced acquisition");
        check(ItemAcquisitionPolicy.acceptedPickupAmount(
                GameMode.SURVIVAL, false, true, 32, 0) == 0,
                "player/death drop re-pickup advanced acquisition");
        check(ItemAcquisitionPolicy.acceptedPickupAmount(
                GameMode.CREATIVE, false, false, 32, 0) == 0,
                "creative/admin injection advanced acquisition");
        check(ItemAcquisitionPolicy.acceptedPickupAmount(
                GameMode.SPECTATOR, false, false, 32, 0) == 0,
                "spectator pickup advanced acquisition");
        check(ItemAcquisitionPolicy.acceptedPickupAmount(
                GameMode.SURVIVAL, false, false, 32, 33) == 0,
                "invalid remaining amount advanced acquisition");
        final ItemAcquisitionPolicy.ReceiptWindow receipts =
                new ItemAcquisitionPolicy.ReceiptWindow(2);
        final java.util.UUID first = java.util.UUID.randomUUID();
        final java.util.UUID second = java.util.UUID.randomUUID();
        final java.util.UUID third = java.util.UUID.randomUUID();
        check(receipts.claim(first) && !receipts.claim(first),
                "same logical acquisition event was not exactly-once");
        check(receipts.claim(second) && receipts.claim(third) && receipts.size() == 2,
                "acquisition receipt window is not bounded");
        check(receipts.claim(first), "evicted receipt could not represent a later independent event");
    }

    private static void projectileSnapshotScalesExactlyOnce() {
        final SpellDamageUtil.ProjectileSnapshot snapshot = new SpellDamageUtil.ProjectileSnapshot(
                "fireball", SpellSchool.TUZ, java.util.UUID.randomUUID(), 5.0D, 1.4D);
        check(Math.abs(snapshot.scaledDamage() - 7.0D) < 0.000_001D,
                "projectile base and cast multiplier were not applied exactly once");
        final SpellDamageUtil.ProjectileSnapshot zero = new SpellDamageUtil.ProjectileSnapshot(
                "gale_burst", SpellSchool.VIHAR, null, 1.0D, 0.0D);
        check(zero.scaledDamage() == 0.0D, "zero cast multiplier manufactured damage");
    }

    private static void projectileCanonicalPathIsSingular() throws Exception {
        final String utility = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/utils/SpellDamageUtil.java"));
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/SpellDamageListener.java"));
        check(utility.contains("CANONICAL_PROJECTILE_DAMAGE")
                        && utility.contains("withDirectEntity(projectile)")
                        && utility.contains("withCausingEntity(causing)"),
                "projectile custom DamageSource attribution/context guard is incomplete");
        check(listener.contains("onVanillaSpellProjectileDamage")
                        && listener.contains("event.setCancelled(true)")
                        && !listener.contains("projectileDamageMultiplier"),
                "vanilla projectile damage is not suppressed behind the one custom hit");
    }

    private static void identitySafeFacadeTeardown(final Class<?> type, final String fieldName,
                                                    final String currentMethod) throws Exception {
        final Object unsafe = unsafe();
        final Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        final Object a = allocate.invoke(unsafe, type);
        final Object b = allocate.invoke(unsafe, type);
        final Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        final Method clear = type.getMethod("clearIfCurrent", type);

        field.set(null, a);
        check(readCurrent(type, field, currentMethod) == a, type.getSimpleName() + " did not install A");
        clear.invoke(null, a);
        check(readCurrent(type, field, currentMethod) == null, type.getSimpleName() + " did not uninstall A");
        field.set(null, b);
        check(readCurrent(type, field, currentMethod) == b, type.getSimpleName() + " did not install B");
        clear.invoke(null, a);
        check(readCurrent(type, field, currentMethod) == b,
                type.getSimpleName() + " stale A cleanup cleared B");
        clear.invoke(null, b);
        check(readCurrent(type, field, currentMethod) == null, type.getSimpleName() + " did not uninstall B");
    }

    private static Object readCurrent(final Class<?> type, final Field field,
                                      final String methodName) throws Exception {
        return methodName == null ? field.get(null) : type.getMethod(methodName).invoke(null);
    }

    private static Object unsafe() throws Exception {
        final Class<?> type = Class.forName("sun.misc.Unsafe");
        final Field field = type.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return field.get(null);
    }

    private static void executableDependencyContractIsBounded() throws Exception {
        final String paper = Files.readString(Path.of("src/main/resources/paper-plugin.yml"));
        final String lock = Files.readString(Path.of("src/main/resources/class-spec-dependencies.lock.yml"));
        check(paper.contains("FancyNpcs:") && paper.contains("required: true"),
                "FancyNpcs is not a required Paper dependency");
        check(lock.contains("server-name: FancyNpcs") && lock.contains("runtime-role: required-runtime"),
                "FancyNpcs lock role is not startup-fatal");
        for (final String unused : List.of("MythicMobs:", "FancyDialogs:", "PacketEvents:", "packetevents:")) {
            check(!paper.contains(unused) && !lock.contains(unused),
                    unused + " leaked into current runtime dependency metadata");
        }
    }

    private static void advancementInventoryHasNoOrphans() throws Exception {
        final Path root = Path.of("src/main/resources/datapack/data/icesmp/advancement");
        final List<String> names;
        try (var files = Files.list(root)) {
            names = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString()).sorted().toList();
        }
        check(names.size() == 22, "authored advancement total must be 21 persistent + 1 toast: " + names);
        check(names.contains("toast_quest.json"), "live quest toast missing");
        check(!names.contains("toast_milestone.json") && !names.contains("toast_discovery.json")
                        && !names.contains("class_max.json"),
                "orphan/redundant advancement survived cleanup");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
