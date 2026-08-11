package hu.taliann.icesmp.spells;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Pure-Java/source regression for shared modifiers, cast transactions and graph wiring. */
public final class SpellCastArchitectureRegressionSuite {

    private SpellCastArchitectureRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        modifiersAreSemantic();
        outcomesAreTransactional();
        executionContextIsNestedAndThreadLocal();
        sourceContractsAreHardened(repoRoot());
        System.out.println("Spell cast architecture regression suite passed.");
    }

    private static void modifiersAreSemantic() {
        final CastModifiers power = CastModifiers.standardPower(1.75D);
        require(power.damageMultiplier() == 1.75D, "damage must inherit standard power");
        require(power.healingMultiplier() == 1.75D, "healing must inherit standard power");
        require(power.shieldingMultiplier() == 1.75D, "shielding must inherit standard power");
        require(power.ccDurationMultiplier() == 1.0D, "standard power must not lengthen hard CC");
        require(power.harmfulDurationMultiplier() == 1.0D, "standard power must not lengthen harmful duration");
        require(power.beneficialDurationMultiplier() == 1.0D, "standard power must not lengthen beneficial duration");
        require(power.cooldownMultiplier() == 1.0D, "standard power must not alter cooldowns");
        require(power.costMultiplier() == 1.0D, "standard power must not alter costs");
        final CastModifiers explicitCc = new CastModifiers(
                1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.25D, 1.0D, 1.0D, 1.0D);
        require(power.combine(explicitCc).ccDurationMultiplier() == 1.25D,
                "only an explicit CC modifier may lengthen hard CC");
        expectFailure(() -> CastModifiers.standardPower(Double.NaN), "NaN multiplier must fail fast");
        expectFailure(() -> CastModifiers.standardPower(-0.1D), "negative multiplier must fail fast");
    }

    private static void outcomesAreTransactional() {
        for (final CastOutcome outcome : CastOutcome.values()) {
            final boolean commits = outcome == CastOutcome.SUCCESS;
            require(outcome.commitsCast() == commits, outcome + " commit mismatch");
            require(outcome.consumesCost() == commits, outcome + " cost mismatch");
            require(outcome.startsCooldown() == commits, outcome + " cooldown mismatch");
        }
    }

    private static void executionContextIsNestedAndThreadLocal() throws InterruptedException {
        require(SpellExecutionContext.current().equals(CastModifiers.IDENTITY), "context must start at identity");
        final CastModifiers outer = CastModifiers.standardPower(1.2D);
        final CastModifiers inner = CastModifiers.standardPower(1.8D);
        try (SpellExecutionContext.Scope ignored = SpellExecutionContext.open(outer)) {
            require(SpellExecutionContext.current().equals(outer), "outer context missing");
            try (SpellExecutionContext.Scope nested = SpellExecutionContext.open(inner)) {
                require(SpellExecutionContext.current().equals(inner), "nested context missing");
            }
            require(SpellExecutionContext.current().equals(outer), "nested close did not restore outer context");
            final AtomicReference<CastModifiers> otherThread = new AtomicReference<>();
            final CountDownLatch done = new CountDownLatch(1);
            final Thread thread = Thread.ofPlatform().start(() -> {
                otherThread.set(SpellExecutionContext.current());
                done.countDown();
            });
            done.await();
            thread.join();
            require(otherThread.get().equals(CastModifiers.IDENTITY), "context leaked across region threads");
        }
        require(SpellExecutionContext.current().equals(CastModifiers.IDENTITY), "context did not close cleanly");
    }

    private static void sourceContractsAreHardened(final Path root) throws IOException {
        final String registry = read(root, "src/main/java/hu/taliann/icesmp/managers/SpellRegistry.java");
        require(registry.contains("putIfAbsent") && registry.contains("Duplicate spell id"),
                "spell registry must fail fast instead of silently replacing ids");
        final String spell = read(root, "src/main/java/hu/taliann/icesmp/spells/Spell.java");
        require(spell.contains("SpellExecutionContext.open") && spell.contains("CastModifiers.standardPower"),
                "bespoke spells must inherit the shared modifier context");
        final String configured = read(root, "src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java");
        require(configured.contains("CastOutcome.NO_TARGET"), "configured no-target casts must be typed");
        require(configured.contains("ccDurationMultiplier"), "configured CC needs an explicit modifier channel");
        require(!configured.contains("effect.getDuration() * power"),
                "generic power must not implicitly lengthen potion effects");
        require(configured.contains("rejects an empty cast"), "instant AoE empty policy must be explicit");
        final String damage = read(root, "src/main/java/hu/taliann/icesmp/utils/SpellDamageUtil.java");
        final String healing = read(root, "src/main/java/hu/taliann/icesmp/utils/SpellHealingUtil.java");
        require(damage.contains("SpellExecutionContext.current()"), "damage primitive must inherit modifiers");
        require(healing.contains("SpellExecutionContext.current()"), "healing primitive must inherit modifiers");
        require(damage.contains("markProjectile") && damage.contains("projectileDamageMultiplier"),
                "projectiles must carry an immutable cast snapshot");
        final String projectile = read(root, "src/main/java/hu/taliann/icesmp/spells/ProjectileBurstSpell.java");
        require(projectile.contains("SpellDamageUtil.markProjectile"),
                "projectile volleys must propagate the cast snapshot");
        final String combos = read(root, "src/main/resources/config/spells.yml");
        require(!combos.contains("soul-collapse:"), "cross-spec Affliction/Destruction chain must be removed");
        require(!combos.contains("way-of-hundred-fists:"), "Monk native rotation must not be double rewarded");
        final String druid = read(root, "src/main/java/hu/taliann/icesmp/druid/DruidGameplayService.java");
        require(druid.contains("Harmónia"), "Druid secondary harmony needs a distinct player-facing name");
        require(!druid.contains("\"harmony\", \"Természeti Erő\""),
                "Druid primary and secondary resources may not share a label");
        final String build = read(root, "build.gradle.kts");
        require(build.contains("wizardGameplayRegressionTest") && build.contains("WizardGameplayRegressionSuite"),
                "Wizard gameplay regression must have a Gradle task");
        require(build.contains("wizardProfileRegressionTest") && build.contains("WizardProfileRegressionSuite"),
                "Wizard profile regression must have a Gradle task");
        require(build.contains("spellCastArchitectureRegressionTest"),
                "cast architecture regression must be wired into check");
        final String listener = read(root, "src/main/java/hu/taliann/icesmp/listeners/AbilityCatalystListener.java");
        require(listener.contains("CastOutcome") && listener.contains("commitsCast()"),
                "cast listener must commit from typed outcomes");
        require(listener.contains("prepareClassCast"), "class preparation must be a named lifecycle phase");
        require(listener.contains("authorizedByPersonalSoulbond"),
                "casting authorization must be separated from the held item");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Repository root not found from working directory");
        }
        return current;
    }

    private static String read(final Path root, final String relative) throws IOException {
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }

    private static void expectFailure(final Runnable action, final String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (final IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
