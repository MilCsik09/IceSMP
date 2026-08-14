package hu.taliann.icesmp.spells;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Pure-Java/source contract regression for the hardened shared cast foundation. */
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
        require(power.harmfulDurationMultiplier() == 1.0D,
                "standard power must not lengthen harmful duration");
        require(power.beneficialDurationMultiplier() == 1.0D,
                "standard power must not lengthen beneficial duration");
        require(power.cooldownMultiplier() == 1.0D, "standard power must not alter cooldowns");
        require(power.costMultiplier() == 1.0D, "standard power must not alter costs");
        require(power.resourceGenerationMultiplier() == 1.0D,
                "standard power must not alter resource generation");

        final CastModifiers explicitCc = new CastModifiers(
                1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.25D, 1.0D, 1.0D, 1.0D);
        require(power.combine(explicitCc).ccDurationMultiplier() == 1.25D,
                "only an explicit CC modifier may lengthen hard CC");
        require(power.combine(explicitCc).damageMultiplier() == 1.75D,
                "explicit CC modifier must not erase damage scaling");
        expectFailure(() -> CastModifiers.standardPower(Double.NaN), "NaN multiplier must fail fast");
        expectFailure(() -> CastModifiers.standardPower(-0.1D), "negative multiplier must fail fast");
    }

    private static void outcomesAreTransactional() {
        for (final CastOutcome outcome : CastOutcome.values()) {
            final boolean commits = outcome == CastOutcome.SUCCESS;
            require(outcome.commitsCast() == commits, outcome + " commit mismatch");
            require(outcome.consumesCost() == commits, outcome + " cost mismatch");
            require(outcome.startsCooldown() == commits, outcome + " cooldown mismatch");
            require(outcome.effectApplied() == commits, outcome + " effect flag mismatch");
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
                "registry must fail fast instead of silently replacing ids");

        final String spell = read(root, "src/main/java/hu/taliann/icesmp/spells/Spell.java");
        require(spell.contains("try (SpellExecutionContext.Scope ignored = SpellExecutionContext.open(modifiers))"),
                "typed cast must open exactly one modifier scope");
        require(spell.contains("final CastOutcome outcome = executeCast(player);"),
                "executeCast is the canonical typed execution path");
        require(spell.contains("return cast(player, CastModifiers.standardPower(powerMultiplier)).effectApplied();"),
                "legacy scalar compatibility must delegate one-way into typed cast");
        require(!spell.contains("cast(player, modifiers) ?"),
                "typed cast may not recurse through a boolean compatibility path");

        final String configured = read(root, "src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java");
        require(configured.contains("return cast(player, CastModifiers.standardPower(power)).effectApplied();"),
                "ConfiguredSpell scalar overload must delegate one-way to typed cast");
        require(configured.contains("CastOutcome.NO_TARGET"), "configured no-target casts must be typed");
        require(configured.contains("SpellExecutionContext.current().ccDurationMultiplier()"),
                "configured hard CC must have an explicit modifier channel");
        require(!configured.contains("effect.getDuration() * power"),
                "generic spell power must not implicitly lengthen potion effects");
        require(configured.contains("Double ccFactor = null")
                        && occurrences(configured, "CcDiminish.nextFactor(target)") == 2,
                "freeze+Slowness must share one lazy DR factor instead of unconditional double consumption");
        require(configured.contains("if (ccFactor == null)"),
                "the same DR factor must be reused by potion CC and freeze within one spell");
        require(configured.contains("withBalanceOverrides") && configured.contains("return spell;"),
                "live balance reporting must preserve the original registered instance");

        final String damage = read(root, "src/main/java/hu/taliann/icesmp/utils/SpellDamageUtil.java");
        final String healing = read(root, "src/main/java/hu/taliann/icesmp/utils/SpellHealingUtil.java");
        require(damage.contains("SpellExecutionContext.current()"), "damage primitive must inherit modifiers");
        require(healing.contains("SpellExecutionContext.current()"), "healing primitive must inherit modifiers");
        require(damage.contains("baseAmount * effective.damageMultiplier()"),
                "damage must apply the semantic damage multiplier exactly in the shared primitive");
        require(healing.contains("baseAmount * effective.healingMultiplier()"),
                "healing must apply the semantic healing multiplier exactly in the shared primitive");
        require(damage.contains("markProjectile") && damage.contains("projectileDamageMultiplier"),
                "projectiles must carry an immutable cast snapshot");

        final String projectile = read(root, "src/main/java/hu/taliann/icesmp/spells/ProjectileBurstSpell.java");
        require(projectile.contains("SpellExecutionContext.capture()")
                        && projectile.contains("SpellDamageUtil.markProjectile"),
                "projectile volleys must capture and carry modifiers to hit time");

        assertDelayedDamageSnapshot(root, "DeepBreathSpell.java");
        assertDelayedDamageSnapshot(root, "WhirlwindSpell.java");
        assertDelayedDamageSnapshot(root, "FlyingSerpentKickSpell.java");
        assertDelayedDamageSnapshot(root, "GlaiveThrowSpell.java");

        final String shamanTotem = read(root,
                "src/main/java/hu/taliann/icesmp/spells/ShamanTotemSpell.java");
        final String totemManager = read(root,
                "src/main/java/hu/taliann/icesmp/managers/TotemManager.java");
        require(shamanTotem.contains("SpellExecutionContext.capture()"),
                "totem creation must snapshot modifiers inside the synchronous cast scope");
        require(totemManager.contains("final CastModifiers snapshot")
                        && totemManager.contains("SpellDamageUtil.scaledDamage(damage, modifiers)"),
                "totem pulse damage must retain the immutable cast multiplier");
        require(totemManager.contains("type.affect(nearby, durationTicks, modifiers)"),
                "cross-region totem pulse must carry the same snapshot");
        require(!totemManager.contains("monster.damage(damage);"),
                "totem delayed damage may not bypass shared scaling");

        final String listener = read(root, "src/main/java/hu/taliann/icesmp/listeners/AbilityCatalystListener.java");
        require(listener.contains("CastOutcome") && listener.contains("!outcome.commitsCast()"),
                "non-committing typed outcomes must stop state/cooldown bookkeeping");
        require(listener.contains("prepareClassCast"), "class preparation must be a named lifecycle phase");
        require(listener.contains("authorizedByPersonalSoulbond"),
                "casting authorization must be separate from the held item");
        require(occurrences(listener, "masteryManager.getCooldownMultiplier(player, spell.getId())") == 1,
                "mastery cooldown reduction must have one formula authority");
        require(listener.contains("private long effectiveCooldownMillis(final Player player, final Spell spell)"),
                "effective cooldown must have a shared helper");
        require(listener.contains("lastCast + delayMs + effectiveCooldownMillis(player, spell) - now"),
                "cast gate must use shared effective cooldown");
        require(listener.contains("effectiveCooldownMillis(player, spell) - comboRefundMs"),
                "item overlay must use shared effective cooldown");
        final int activeCooldowns = listener.indexOf("public Map<String, Long> activeCooldowns(final UUID playerId)");
        final int comboBoost = listener.indexOf("public boolean hasComboBoost", activeCooldowns);
        require(activeCooldowns >= 0 && comboBoost > activeCooldowns
                        && listener.substring(activeCooldowns, comboBoost)
                        .contains("effectiveCooldownMillis(online, spell)"),
                "active cooldown HUD snapshot must use shared effective cooldown");
        require(listener.contains("final long effectiveCooldownMs = effectiveCooldownMillis(player, spell);"),
                "combo refund must use shared effective cooldown");
        require(listener.contains("spellbookStateStore.recordLastCast(playerId, spell.getId(), timestamp)"),
                "persistent cooldown stores the same cast timestamp consumed by the shared formula");
        require(listener.contains("secondary.value() < secondary.maximum()"),
                "Assassin detection gating must use the canonical ClassHudMetric value accessor");
        require(!listener.contains("secondary.current()"),
                "Assassin detection gating must not call a non-existent HUD metric accessor");

        final String combos = read(root, "src/main/resources/config/spells.yml");
        require(!combos.contains("soul-collapse:"),
                "cross-spec Affliction/Destruction chain must stay removed");
        require(!combos.contains("way-of-hundred-fists:"),
                "Monk native rotation must not be double-rewarded globally");

        final String aggregate = read(root,
                "src/regression/java/hu/taliann/icesmp/spells/ClassSpellAuditRegressionSuite.java");
        require(aggregate.contains("SpellRegistryRegressionSuite.main")
                        && aggregate.contains("SpellCastArchitectureRegressionSuite.main")
                        && aggregate.contains("ActiveKitLifecycleRegressionSuite.main")
                        && aggregate.contains("DarkClassSpellLifecycleRegressionSuite.main")
                        && aggregate.contains("MonkStaggerLifecycleRegressionSuite.main")
                        && aggregate.contains("WizardGameplayRegressionSuite.main")
                        && aggregate.contains("WizardProfileRegressionSuite.main"),
                "hardening aggregate must execute registry, cast, active-kit, DARK, Monk and Wizard regressions");
        final String grantGate = read(root,
                "src/regression/java/hu/taliann/icesmp/classspec/domain/SpellGrantLedgerRegressionSuite.java");
        require(grantGate.contains("ClassSpellAuditRegressionSuite.main"),
                "hardening aggregate must execute through the check-wired grant ledger gate");
        final String build = read(root, "build.gradle.kts");
        require(build.contains("val wizardGameplayRegressionTest = registerRegression(")
                        && build.contains("val wizardProfileRegressionTest = registerRegression("),
                "Wizard suites must be explicit Gradle tasks");
        final int check = build.indexOf("tasks.check");
        require(check >= 0 && build.indexOf("wizardGameplayRegressionTest", check) > check
                        && build.indexOf("wizardProfileRegressionTest", check) > check,
                "Wizard regression tasks must be dependencies of check");
    }

    private static void assertDelayedDamageSnapshot(final Path root, final String file) throws IOException {
        final String source = read(root, "src/main/java/hu/taliann/icesmp/spells/" + file);
        require(source.contains("SpellExecutionContext.capture()"),
                file + " must snapshot modifiers before scheduler hops");
        require(source.contains("CastModifiers modifiers"),
                file + " must explicitly carry the immutable snapshot");
        require(source.contains("SpellDamageUtil.damageBySpell")
                        || source.contains("SpellDamageUtil.scaledDamage"),
                file + " delayed output must use the shared damage scaling primitive");
    }

    private static int occurrences(final String text, final String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Repository root not found from working directory");
        return current;
    }

    private static String read(final Path root, final String relative) throws IOException {
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8).replace("\r\n", "\n");
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
        if (!condition) throw new AssertionError(message);
    }
}
