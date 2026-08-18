#!/usr/bin/env python3
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
paths=[ROOT/'docs/development/professions-2-recipe-migration.json',
       ROOT/'docs/development/professions-2-economy-graph.json',
       ROOT/'docs/development/professions-2-rp-handoff.json']
for p in paths:
    assert json.loads(p.read_text(encoding='utf-8')).get('schema')==2, p
mig=json.loads(paths[0].read_text(encoding='utf-8'))
assert mig['baseline_recipe_count']==392
assert mig['effective_recipe_count']==407
assert mig['canonical_recipe_count']==18
assert mig['category_summary']['EQUIPMENT']==18
assert len(mig['recipes'])==407
assert all('economy_category' in row for row in mig['recipes'])
g=json.loads(paths[1].read_text(encoding='utf-8'))
assert not g['dead_managed_materials'] and not g['cycles']
assert g['mail_mixed_dependency_verified']
assert set(g['family_distribution'])=={'CLOTH','LEATHER','MAIL','PLATE'}
assert all(g['family_distribution'][f]>0 for f in g['family_distribution'])
assert set(g['salvage_reclamation_sinks_verified'])=={'szovet_foszlany','bor_hulladek','lanc_toredek','femhulladek'}
nodes={n['id']:n for n in g['material_nodes']}
for scrap in g['salvage_reclamation_sinks_verified']:
    assert nodes[scrap]['consumer_recipes'], scrap
listener=(ROOT/'src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java').read_text(encoding='utf-8')
tx=listener.index('craftTransaction.apply(player, recipe, batches, outputs)')
award=listener.index('AdvancementService.award(player, "masterwork")')
assert award>tx
assert 'dropItemNaturally(player.getLocation(), overflow)' not in listener
print('Professions 2.0 reports/hardening: OK')
