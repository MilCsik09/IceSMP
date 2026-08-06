package hu.taliann.icesmp.classspec.compat;

import java.util.List;

/** Dependency-free regressions for the 1.21.11 class/spec dependency lock matcher. */
public final class ClassSpecCompatibilityRegressionSuite {

    private ClassSpecCompatibilityRegressionSuite() {
    }

    public static void main(final String[] args) {
        exactVersionsAcceptDecoratedRuntimeStrings();
        wildcardsStayWithinTheDeclaredMinorLine();
        invalidOrBlankVersionsAreRejected();
        onlyOptionalDependenciesMayBeAbsent();
        System.out.println("Class/spec compatibility regression suite passed.");
    }

    private static void exactVersionsAcceptDecoratedRuntimeStrings() {
        final VersionRequirement requirement = new VersionRequirement(
                "BetterHud", true, List.of("1.14.1"), "official-release");
        check(requirement.accepts("1.14.1"), "exact version rejected");
        check(requirement.accepts("v1.14.1"), "v-prefixed version rejected");
        check(requirement.accepts("1.14.1+build.7"), "build metadata rejected");
        check(requirement.accepts("1.14.1-bukkit"), "platform suffix rejected");
        check(!requirement.accepts("1.14.0"), "older version accepted");
        check(!requirement.accepts("2.0.0"), "future major version accepted without review");
    }

    private static void wildcardsStayWithinTheDeclaredMinorLine() {
        final VersionRequirement requirement = new VersionRequirement(
                "LibsDisguises", false, List.of("11.0.*"), "staging-required");
        check(requirement.accepts("11.0.18"), "declared wildcard rejected");
        check(requirement.accepts("v11.0.99-SNAPSHOT"), "decorated wildcard rejected");
        check(!requirement.accepts("11.1.0"), "wildcard escaped minor line");
        check(!requirement.accepts("10.0.44"), "wildcard accepted old major");
    }

    private static void invalidOrBlankVersionsAreRejected() {
        final VersionRequirement requirement = new VersionRequirement(
                "CraftEngine", true, List.of("26.7.4"), "official-beta-pin");
        check(!requirement.accepts(null), "null version accepted");
        check(!requirement.accepts(""), "blank version accepted");
        check(!requirement.accepts("26.7"), "prefix-only version accepted");
    }

    private static void onlyOptionalDependenciesMayBeAbsent() {
        final VersionRequirement required = new VersionRequirement(
                "CraftEngine", true, List.of("26.7.4"), "official-beta-pin");
        final VersionRequirement optional = new VersionRequirement(
                "FancyDialogs", false, List.of("1.3.0"), "official-release");
        check(!required.acceptsMissingDependency(), "required dependency may be absent");
        check(optional.acceptsMissingDependency(), "optional dependency was treated as required");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
