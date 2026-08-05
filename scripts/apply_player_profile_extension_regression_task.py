#!/usr/bin/env python3
"""Register full-authority PlayerProfile regressions in Gradle check."""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "build.gradle.kts"
text = path.read_text(encoding="utf-8")
anchor = '''val playerProfileYamlRegressionTest by tasks.registering(JavaExec::class) {
'''
block = '''val playerProfileSectionExtensionsRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs immutable extension-copy regressions across every PlayerProfile section."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensionsRegressionSuite")
}

val spellMasteryTransactionRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs exact-once spell mastery wallet/receipt recovery regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.SpellMasteryTransactionRegressionSuite")
}

'''
if block not in text:
    existing_extension = '''val playerProfileSectionExtensionsRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs immutable extension-copy regressions across every PlayerProfile section."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensionsRegressionSuite")
}

'''
    mastery_only = block.removeprefix(existing_extension)
    if existing_extension in text and mastery_only not in text:
        text = text.replace(existing_extension, existing_extension + mastery_only, 1)
    else:
        if text.count(anchor) != 1:
            raise SystemExit("PlayerProfile YAML task anchor not found exactly once")
        text = text.replace(anchor, block + anchor, 1)
old = '''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileSectionExtensionsRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
'''
new = '''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileSectionExtensionsRegressionTest, spellMasteryTransactionRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
'''
if new not in text:
    if old not in text:
        old = '''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
'''
    if text.count(old) != 1:
        raise SystemExit("Gradle check dependency anchor not found exactly once")
    text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("PlayerProfile full-authority regression tasks registered.")
