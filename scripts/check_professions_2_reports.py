#!/usr/bin/env python3
from pathlib import Path
import json, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
paths=[ROOT/'docs/development/professions-2-recipe-migration.json',ROOT/'docs/development/professions-2-economy-graph.json',ROOT/'docs/development/professions-2-rp-handoff.json']
for p in paths:
    data=json.loads(p.read_text(encoding='utf-8'))
    assert data.get('schema')==2, p
mig=json.loads(paths[0].read_text(encoding='utf-8'))
assert mig['baseline_recipe_count']==392 and mig['canonical_recipe_count']==15
assert len(mig['recipes'])==mig['effective_recipe_count']
g=json.loads(paths[1].read_text(encoding='utf-8'))
assert not g['dead_managed_materials'] and not g['cycles']
assert set(g['family_distribution']).issubset({'CLOTH','LEATHER','MAIL','PLATE'})
print('Professions 2.0 reports: OK')
