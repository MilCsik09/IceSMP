#!/usr/bin/env python3
"""Register the PlayerProfile extension-copy regression in Gradle check."""
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

'''
if block not in text:
    if text.count(anchor) != 1:
        raise SystemExit("PlayerProfile YAML task anchor not found exactly once")
    text = text.replace(anchor, block + anchor, 1)
old = '''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
'''
new = '''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileSectionExtensionsRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
'''
if new not in text:
    if text.count(old) != 1:
        raise SystemExit("Gradle check dependency anchor not found exactly once")
    text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("PlayerProfile extension regression task registered.")
