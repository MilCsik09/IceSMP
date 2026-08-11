package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite;
import hu.taliann.icesmp.wizard.WizardProfileRegressionSuite;

/**
 * Check-wired aggregate for class/spell regressions that previously existed as
 * orphan main classes. It is invoked by the mandatory spell-grant ledger gate.
 */
public final class ClassSpellAuditRegressionSuite {

    private ClassSpellAuditRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        SpellCastArchitectureRegressionSuite.main(args);
        WizardGameplayRegressionSuite.main(args);
        WizardProfileRegressionSuite.main(args);
        System.out.println("Class/spell audit regression aggregate passed.");
    }
}
