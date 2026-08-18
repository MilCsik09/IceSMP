#!/usr/bin/env python3
from __future__ import annotations
import json, random
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
CFG=ROOT/'src/main/resources/config'
base=yaml.safe_load((CFG/'profession-recipes.yml').read_text(encoding='utf-8'))['profession-recipes']
overlay=yaml.safe_load((CFG/'professions-2.yml').read_text(encoding='utf-8'))['profession-recipes']
templates=yaml.safe_load((CFG/'item-templates.yml').read_text(encoding='utf-8'))['item-templates']
effective={k:dict(v) for k,v in base.items()}
for rid,patch in overlay.items(): effective.setdefault(rid,{}).update(patch)
processing=[(rid,r) for rid,r in effective.items() if str(r.get('economy-category','')).upper()=='PROCESSING']
services=[(rid,r) for rid,r in effective.items() if str(r.get('economy-category','')).upper()=='UPGRADE_SERVICE']
gear=[(rid,r) for rid,r in effective.items() if (r.get('result') or {}).get('template')]
families={str(templates[(r.get('result') or {})['template']].get('armor-family','')).upper()
          for _,r in gear if templates.get((r.get('result') or {})['template'],{}).get('armor-family')}
assert families=={'CLOTH','LEATHER','MAIL','PLATE'}
assert len(gear)==18 and len(processing)==8 and len(services)>=4
rng=random.Random(0x1CE5A2)
p=yaml.safe_load((CFG/'professions-2.yml').read_text(encoding='utf-8'))['professions']['masterwork']
chance=min(float(p['maximum-chance']),float(p['base-chance'])+50*float(p['chance-per-level']))
hits=sum(1 for _ in range(100000) if rng.random()<chance)
rate=hits/100000
assert 0.0<chance<0.5 and abs(rate-chance)<0.01
for rid,r in services:
    if rid.startswith('p2_') and rid.endswith('_reclamation'):
        units=sum(int(str(x).rsplit(':',1)[1]) for x in r['ingredients'])
        assert units>=7 and int(r['result'].get('amount',1))==1
report={'seed':0x1CE5A2,'processing_recipe_count':len(processing),
        'upgrade_service_recipe_count':len(services),'canonical_gear_recipe_count':len(gear),
        'family_coverage':sorted(families),'masterwork_level50_configured_chance':chance,
        'masterwork_seeded_rate':rate,'production_balance_proven':False,
        'staging_required':['50-60 player material supply','real GUI latency',
                            'disconnect during craft','market price equilibrium']}
out=ROOT/'build/reports/professions-2/economy-harness.json'; out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
print(f"Professions 2.0 economy harness: {len(processing)} processing, {len(services)} services, {len(gear)} canonical gear, masterwork@50={rate:.3f}")
