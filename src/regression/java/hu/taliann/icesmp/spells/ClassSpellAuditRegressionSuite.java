package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.monk.MonkStaggerLifecycleRegressionSuite;
import hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite;
import hu.taliann.icesmp.wizard.WizardProfileRegressionSuite;

/** Check-wired aggregate for the cross-system class/spell hardening contracts. */
public final class ClassSpellAuditRegressionSuite {

    private ClassSpellAuditRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        SpellRegistryRegressionSuite.main(args);
        SpellCastArchitectureRegressionSuite.main(args);
        ActiveKitLifecycleRegressionSuite.main(args);
        DarkClassSpellLifecycleRegressionSuite.main(args);
        MonkStaggerLifecycleRegressionSuite.main(args);
        WizardGameplayRegressionSuite.main(args);
        WizardProfileRegressionSuite.main(args);
        System.out.println("Class/spell audit regression aggregate passed.");
    }
}
