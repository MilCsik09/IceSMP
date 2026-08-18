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
assert all(graph['family_distribution'][family] > 0
           for family in graph['family_distribution'])
expected_scraps = {'szovet_foszlany', 'bor_hulladek', 'lanc_toredek', 'femhulladek'}
assert set(graph['salvage_reclamation_sinks_verified']) == expected_scraps
nodes = {node['id']: node for node in graph['material_nodes']}
for scrap in expected_scraps:
    assert nodes[scrap]['consumer_recipes'], scrap

listener = (ROOT / 'src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java').read_text(encoding='utf-8')
tx_index = listener.index('craftTransaction.apply(player, recipe, batches, outputs)')
award_index = listener.index('AdvancementService.award(player, "masterwork")')
assert award_index > tx_index
assert 'dropItemNaturally(player.getLocation(), overflow)' not in listener

transaction = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionCraftTransaction.java').read_text(encoding='utf-8')
assert 'player.saveData();' in transaction
assert 'PERSISTENCE_FAILED' in transaction
assert 'inventory.setStorageContents(cloneContents(before));' in transaction
assert 'ProfessionSpecializationEconomyPolicy.effectFor(player, recipe)' in transaction
assert 'dropItemNaturally' not in transaction

salvage = (ROOT / 'src/main/java/hu/taliann/icesmp/itemization/ItemSalvageService.java').read_text(encoding='utf-8')
assert 'familyScrapId' in salvage
assert 'ProfessionEconomyTelemetry.global().recordSalvage' not in salvage

root_config = yaml.safe_load((ROOT / 'src/main/resources/config.yml').read_text(encoding='utf-8')) or {}
delivery = (((root_config.get('itemization') or {}).get('salvage') or {}).get('output-map') or {})
for scrap in expected_scraps:
    assert delivery.get(scrap) == scrap, (scrap, delivery.get(scrap))

specializations = json.loads((ROOT / 'docs/development/professions-2-specializations.json').read_text(encoding='utf-8'))
assert specializations['schema'] == 1
assert specializations['authority'].startswith('PlayerProfile')
assert len(specializations['specializations']) == 16
assert len({row['role'] for row in specializations['specializations']}) >= 6
assert not specializations['policy']['random_conservation_proc']
policy_source = (ROOT / 'src/main/java/hu/taliann/icesmp/professions/ProfessionSpecializationEconomyPolicy.java').read_text(encoding='utf-8')
assert 'PlayerProfileSpecializationProgressStore' in policy_source
assert 'roleOf' in policy_source

print('Professions 2.0 reports/hardening: OK')
