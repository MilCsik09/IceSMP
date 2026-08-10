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
        runtimeRolesSeparateStartupBlockingFromOptionalCapabilities();
        roleParserAcceptsCanonicalAndEnumStyleValues();
        System.out.println("Class/spec compatibility regression suite passed.");
    }

    private static void exactVersionsAcceptDecoratedRuntimeStrings() {
        final VersionRequirement requirement = new VersionRequirement(
                "BetterHud", VersionRequirement.RuntimeRole.OPTIONAL_INTEGRATION,
                List.of("1.14.1"), "official-release");
        check(requirement.accepts("1.14.1"), "exact version rejected");
        check(requirement.accepts("v1.14.1"), "v-prefixed version rejected");
        check(requirement.accepts("1.14.1+build.7"), "build metadata rejected");
        check(requirement.accepts("1.14.1-bukkit"), "platform suffix rejected");
        check(!requirement.accepts("1.14.0"), "older version accepted");
        check(!requirement.accepts("2.0.0"), "future major version accepted without review");
    }

    private static void wildcardsStayWithinTheDeclaredMinorLine() {
        final VersionRequirement requirement = new VersionRequirement(
                "LibsDisguises", VersionRequirement.RuntimeRole.OPTIONAL_INTEGRATION,
                List.of("11.0.*"), "staging-required");
        check(requirement.accepts("11.0.18"), "declared wildcard rejected");
        check(requirement.accepts("v11.0.99-SNAPSHOT"), "decorated wildcard rejected");
        check(!requirement.accepts("11.1.0"), "wildcard escaped minor line");
        check(!requirement.accepts("10.0.44"), "wildcard accepted old major");
    }

    private static void invalidOrBlankVersionsAreRejected() {
        final VersionRequirement requirement = new VersionRequirement(
                "CraftEngine", VersionRequirement.RuntimeRole.OPTIONAL_INTEGRATION,
                List.of("26.7.4"), "official-beta-pin");
        check(!requirement.accepts(null), "null version accepted");
        check(!requirement.accepts(""), "blank version accepted");
        check(!requirement.accepts("26.7"), "prefix-only version accepted");
    }

    private static void runtimeRolesSeparateStartupBlockingFromOptionalCapabilities() {
        final VersionRequirement required = requirement(VersionRequirement.RuntimeRole.REQUIRED_RUNTIME);
        final VersionRequirement optional = requirement(VersionRequirement.RuntimeRole.OPTIONAL_INTEGRATION);
        final VersionRequirement devOnly = requirement(VersionRequirement.RuntimeRole.DEV_ONLY);
        final VersionRequirement validationOnly = requirement(VersionRequirement.RuntimeRole.VALIDATION_ONLY);
        check(required.blocksStartup(), "required-runtime did not block startup");
        check(!required.acceptsMissingDependency(), "required-runtime may be absent");
        check(required.participatesInRuntimeCheck(), "required-runtime was skipped at runtime");
        check(!optional.blocksStartup() && optional.acceptsMissingDependency(),
                "optional integration became startup-fatal");
        check(optional.participatesInRuntimeCheck(), "optional integration is not inspected");
        check(!devOnly.blocksStartup() && !devOnly.participatesInRuntimeCheck(),
                "dev-only dependency leaked into runtime enforcement");
        check(!validationOnly.blocksStartup() && !validationOnly.participatesInRuntimeCheck(),
                "validation-only dependency leaked into runtime enforcement");
    }

    private static void roleParserAcceptsCanonicalAndEnumStyleValues() {
        check(VersionRequirement.RuntimeRole.parse("required-runtime")
                        == VersionRequirement.RuntimeRole.REQUIRED_RUNTIME,
                "canonical required role rejected");
        check(VersionRequirement.RuntimeRole.parse("OPTIONAL_INTEGRATION")
                        == VersionRequirement.RuntimeRole.OPTIONAL_INTEGRATION,
                "enum-style optional role rejected");
        boolean rejected = false;
        try {
            VersionRequirement.RuntimeRole.parse("sometimes-required");
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "unknown runtime role accepted");
    }

    private static VersionRequirement requirement(final VersionRequirement.RuntimeRole role) {
        return new VersionRequirement("Example", role, List.of("1.0.0"), "test");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
