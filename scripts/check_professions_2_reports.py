#!/usr/bin/env python3
from pathlib import Path
import json
import yaml

ROOT = Path(__file__).resolve().parents[1]
paths = [
    ROOT / 'docs/development/professions-2-recipe-migration.json',
    ROOT / 'docs/development/professions-2-economy-graph.json',
    ROOT / 'docs/development/professions-2-rp-handoff.json',
]
for path in paths:
    assert json.loads(path.read_text(encoding='utf-8')).get('schema') == 2, path

migration = json.loads(paths[0].read_text(encoding='utf-8'))
assert migration['baseline_recipe_count'] == 392
assert migration['effective_recipe_count'] == 407
assert migration['canonical_recipe_count'] == 18
assert migration['category_summary']['EQUIPMENT'] == 18
assert len(migration['recipes']) == 407
assert all('economy_category' in row for row in migration['recipes'])

graph = json.loads(paths[1].read_text(encoding='utf-8'))
assert not graph['dead_managed_materials'] and not graph['cycles']
assert graph['mail_mixed_dependency_verified']
assert set(graph['family_distribution']) == {'CLOTH', 'LEATHER', 'MAIL', 'PLATE'}
assert all(graph['family_distribution'][family] > 0 for family in graph['family_distribution'])
expected_scraps = {'szovet_foszlany', 'bor_hulladek', 'lanc_toredek', 'femhulladek'}
assert set(graph['salvage_reclamation_sinks_verified']) == expected_scraps
nodes = {node['id']: node for node in graph['material_nodes']}
for scrap in expected_scraps:
    assert nodes[scrap]['consumer_recipes'], scrap

plan = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionEffectiveCraftPlan.java').read_text(encoding='utf-8')
assert 'Math.multiplyExact(oneCraft, batches)' in plan
assert 'effectiveOutputAmount' in plan and 'effectiveOutputs' in plan

listener = (ROOT / 'src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java').read_text(encoding='utf-8')
assert 'ProfessionEffectiveCraftPlan.of(' in listener
assert 'craftTransaction.preflight(player, plan, outputs)' in listener
assert 'craftTransaction.apply(player, plan, outputs)' in listener
assert 'craftTransaction.apply(player, recipe, batches, outputs)' not in listener
assert 'hasIngredients(player, recipe)' not in listener
assert 'dropItemNaturally(player.getLocation(), overflow)' not in listener

gui = (ROOT / 'src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java').read_text(encoding='utf-8')
assert 'ProfessionEffectiveCraftPlan.of(recipe, specialization, 1)' in gui
assert 'transaction.preflight(player, plan, previewOutputs)' in gui
assert 'plan.materialInputs()' in gui and 'plan.uniqueInputs()' in gui
assert 'effectiveOutputAmount' in gui and 'maxCraftableBatches' in gui
assert 'hasIngredients(player, recipe' not in gui

transaction = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java').read_text(encoding='utf-8')
assert 'ProfessionEffectiveCraftPlan plan' in transaction
assert 'preflight(final Player player' in transaction
assert 'preflightStorage' in transaction
assert 'player.saveData();' in transaction and 'PERSISTENCE_FAILED' in transaction
assert 'inventory.setStorageContents(cloneContents(before));' in transaction
assert 'ProfessionSpecializationEconomyPolicy.effectFor(player, recipe)' not in transaction
assert 'dropItemNaturally' not in transaction

blueprint = (ROOT / 'src/main/java/hu/taliann/icesmp/listeners/BlueprintUseListener.java').read_text(encoding='utf-8')
for token in ('PlayerProfileOperationStore', 'profession-blueprint-learn-v1',
              'blueprint_reservation_operation', 'recoverPrepared'):
    assert token in blueprint, token
recovery = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/BlueprintRecoveryPolicy.java').read_text(encoding='utf-8')
for token in ('ROLLBACK_UNTOUCHED', 'RELEASE_AND_ROLLBACK', 'CONSUME_AND_COMMIT', 'COMMIT_CONSUMED'):
    assert token in recovery, token

salvage = (ROOT / 'src/main/java/hu/taliann/icesmp/itemization/ItemSalvageService.java').read_text(encoding='utf-8')
assert 'familyScrapId' in salvage
assert 'ProfessionEconomyTelemetry.global().recordSalvage' not in salvage

equipment_content = yaml.safe_load((ROOT / 'src/main/resources/content/equipment/equipment.yml').read_text(encoding='utf-8')) or {}
delivery = (((equipment_content.get('itemization') or {}).get('salvage') or {}).get('output-map') or {})
for scrap in expected_scraps:
    assert delivery.get(scrap) == scrap, (scrap, delivery.get(scrap))

specializations = json.loads((ROOT / 'docs/development/professions-2-specializations.json').read_text(encoding='utf-8'))
assert specializations['schema'] == 1
assert specializations['authority'].startswith('PlayerProfile')
assert len(specializations['specializations']) == 16
assert len({row['role'] for row in specializations['specializations']}) >= 6
assert not specializations['policy']['random_conservation_proc']
policy_source = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionSpecializationEconomyPolicy.java').read_text(encoding='utf-8')
assert 'PlayerProfileSpecializationProgressStore' in policy_source and 'roleOf' in policy_source

print('Professions 2.0 reports/hardening: OK')
