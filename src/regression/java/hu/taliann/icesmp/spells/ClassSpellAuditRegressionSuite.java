package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite;
import hu.taliann.icesmp.wizard.WizardProfileRegressionSuite;

/**
 * Check-wired cross-system class/spell aggregate. It runs from the mandatory
 * SpellGrantLedger gate and owns the hardening-specific behavior/contracts that
 * span more than one class package.
 */
public final class ClassSpellAuditRegressionSuite {

    private ClassSpellAuditRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        SpellRegistryRegressionSuite.main(args);
        SpellCastArchitectureRegressionSuite.main(args);
        DarkClassSpellLifecycleRegressionSuite.main(args);
        WizardGameplayRegressionSuite.main(args);
        WizardProfileRegressionSuite.main(args);
        System.out.println("Class/spell audit regression aggregate passed.");
    }
}
