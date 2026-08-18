#!/usr/bin/env python3
"""Idempotently restores the specialization economy closure after base generators."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]

POLICY = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionSpecializationEconomyPolicy.java').read_text(encoding='utf-8')


def patch_transaction() -> None:
    path = ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java'
    text = path.read_text(encoding='utf-8')
    marker = 'ProfessionSpecializationEconomyPolicy.effectFor(player, recipe)'
    if marker in text:
        return
    anchor = '        final ItemStack[] working = cloneContents(before);\n'
    if anchor not in text:
        raise RuntimeError('profession transaction specialization anchor missing')
    text = text.replace(anchor, anchor +
        '        final ProfessionSpecializationEconomyPolicy.Effect specialization =\n'
        '                ProfessionSpecializationEconomyPolicy.effectFor(player, recipe);\n', 1)
    text = text.replace('final long requested = (long) entry.getValue() * batches;',
                        'final long requested = specialization.adjustInput((long) entry.getValue() * batches);')
    old = '        for (final ItemStack raw : outputs) {\n'
    new = '        for (final ItemStack raw : specialization.adjustOutputs(recipe, outputs)) {\n'
    if old not in text:
        raise RuntimeError('profession transaction output specialization anchor missing')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


def patch_regression() -> None:
    path = ROOT / 'src/regression/java/hu/taliann/icesmp/professions/Professions2RegressionSuite.java'
    text = path.read_text(encoding='utf-8')
    if 'professionSpecializationRolesAreCompleteAndDiverse' in text:
        return
    text = text.replace('import hu.taliann.icesmp.itemization.ArmorFamily;\n',
                        'import hu.taliann.icesmp.data.ProfessionSpecializationType;\n'
                        'import hu.taliann.icesmp.itemization.ArmorFamily;\n', 1)
    text = text.replace('import java.util.Map;\n', 'import java.util.Arrays;\nimport java.util.Map;\n', 1)
    text = text.replace('        familySalvageMaterialIdsAreStableAndDistinct();\n',
                        '        familySalvageMaterialIdsAreStableAndDistinct();\n'
                        '        professionSpecializationRolesAreCompleteAndDiverse();\n', 1)
    method = '''    private static void professionSpecializationRolesAreCompleteAndDiverse() {
        require(ProfessionSpecializationType.values().length == 16,
                "unexpected profession specialization roster drift");
        for (final ProfessionSpecializationType specialization
                : ProfessionSpecializationType.values()) {
            require(ProfessionSpecializationEconomyPolicy.roleOf(specialization)
                            != ProfessionSpecializationEconomyPolicy.Role.NONE,
                    "profession specialization has no economic role: " + specialization);
        }
        final long roles = Arrays.stream(ProfessionSpecializationType.values())
                .map(ProfessionSpecializationEconomyPolicy::roleOf).distinct().count();
        require(roles >= 6, "profession specializations collapsed into one mandatory role");
        require(ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.PROSPECTOR)
                        != ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.EXCAVATOR),
                "miner specializations must offer different economic roles");
        require(ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.POTION_MASTER)
                        != ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.TRANSMUTER),
                "alchemist specializations must offer different economic roles");
        require(ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.RUNEKEEPER)
                        != ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.ARCANIST),
                "enchanter specializations must offer different economic roles");
    }

'''
    anchor = '    private static void require(final boolean condition, final String message) {'
    if anchor not in text:
        raise RuntimeError('profession regression specialization anchor missing')
    path.write_text(text.replace(anchor, method + anchor, 1), encoding='utf-8')


def patch_checker() -> None:
    path = ROOT / 'scripts/check_professions_2_reports.py'
    text = path.read_text(encoding='utf-8')
    if 'professions-2-specializations.json' in text:
        return
    block = '''specializations = json.loads((ROOT / 'docs/development/professions-2-specializations.json').read_text(encoding='utf-8'))
assert specializations['schema'] == 1
assert specializations['authority'].startswith('PlayerProfile')
assert len(specializations['specializations']) == 16
assert len({row['role'] for row in specializations['specializations']}) >= 6
assert not specializations['policy']['random_conservation_proc']
policy_source = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionSpecializationEconomyPolicy.java').read_text(encoding='utf-8')
assert 'PlayerProfileSpecializationProgressStore' in policy_source
assert 'roleOf' in policy_source

'''
    text = text.replace("assert 'PERSISTENCE_FAILED' in transaction\n",
                        "assert 'PERSISTENCE_FAILED' in transaction\n"
                        "assert 'ProfessionSpecializationEconomyPolicy.effectFor(player, recipe)' in transaction\n", 1)
    marker = "print('Professions 2.0 reports/hardening: OK')"
    if marker not in text:
        raise RuntimeError('profession checker specialization anchor missing')
    path.write_text(text.replace(marker, block + marker, 1), encoding='utf-8')


def patch_config() -> None:
    path = ROOT / 'src/main/resources/config.yml'
    text = path.read_text(encoding='utf-8')
    if 'economy-efficiency-percent:' in text:
        return
    block = '''# A meglévő Profile v2 profession specializációk gazdasági hatása bounded és determinisztikus.
# Efficiency inputot takarít meg; yield csak stackelhető processing/consumable outputot növel.
professions:
  specialization:
    economy-efficiency-percent: 0.10
    economy-yield-percent: 0.10

'''
    anchor = '# ===== Season 0 / Prologue — Olethropyla ====='
    if anchor not in text:
        raise RuntimeError('specialization config insertion anchor missing')
    path.write_text(text.replace(anchor, block + anchor, 1), encoding='utf-8')


def write_report() -> None:
    rows = [
        ('prospector','miner','PROCESSING_EFFICIENCY','processing inputs'),
        ('excavator','miner','PROCESSING_YIELD','stackable processing outputs'),
        ('botanist','herbalist','PROCESSING_YIELD','stackable processing outputs'),
        ('naturalist','herbalist','PROCESSING_EFFICIENCY','processing inputs'),
        ('forester','lumberjack','PROCESSING_EFFICIENCY','processing inputs'),
        ('carpenter','lumberjack','PROCESSING_YIELD','stackable processing outputs'),
        ('weaponsmith','armorer','EQUIPMENT_EXPERTISE','non-armor canonical equipment inputs'),
        ('armorsmith','armorer','EQUIPMENT_EXPERTISE','canonical armor inputs'),
        ('potion_master','alchemist','CONSUMABLE_YIELD','stackable potion/consumable outputs'),
        ('transmuter','alchemist','PROCESSING_EFFICIENCY','processing inputs'),
        ('runekeeper','enchanter','SERVICE_EXPERTISE','rune/service inputs'),
        ('arcanist','enchanter','EQUIPMENT_EXPERTISE','canonical enchanter equipment inputs'),
        ('angler','fisherman','PROCESSING_YIELD','stackable processing outputs'),
        ('treasure_hunter','fisherman','BLUEPRINT_EFFICIENCY','blueprint recipe inputs'),
        ('chef','cook','CONSUMABLE_YIELD','stackable food outputs'),
        ('butcher','cook','CONSUMABLE_EFFICIENCY','food recipe inputs'),
    ]
    report = {
        'schema': 1,
        'authority': 'PlayerProfile ProfessionSection specializations.active',
        'policy': {
            'deterministic': True,
            'random_conservation_proc': False,
            'efficiency_percent': 0.1,
            'yield_percent': 0.1,
            'maximum_configured_effect': 0.25,
            'crafting_expertise_is_not_equipment_proficiency': True,
        },
        'specializations': [
            {'id': i, 'profession': p, 'role': r, 'target': t}
            for i, p, r, t in rows
        ],
    }
    path = ROOT / 'docs/development/professions-2-specializations.json'
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def main() -> None:
    # POLICY is read intentionally: the base generator never owns this new file, and exact-head
    # verification confirms that it remains present while restoring generated consumers below.
    if 'PlayerProfileSpecializationProgressStore' not in POLICY:
        raise RuntimeError('specialization policy source is incomplete')
    patch_transaction()
    patch_regression()
    patch_checker()
    patch_config()
    write_report()
    print('Professions 2.0 specialization economy closure applied')


if __name__ == '__main__':
    main()
