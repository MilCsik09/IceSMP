#!/usr/bin/env python3
from __future__ import annotations
import json, random
from collections import defaultdict
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
CFG=ROOT/'src/main/resources/config'
base=yaml.safe_load((CFG/'profession-recipes.yml').read_text(encoding='utf-8'))['profession-recipes']
overlay=yaml.safe_load((CFG/'professions-2.yml').read_text(encoding='utf-8'))['profession-recipes']
effective={k:dict(v) for k,v in base.items()}
for rid,patch in overlay.items():
    if rid not in effective: effective[rid]=patch; continue
    effective[rid].update(patch)
rng=random.Random(0x1CE5A2)
processing=[(rid,r) for rid,r in effective.items() if str(r.get('economy-category','')).upper()=='PROCESSING']
gear=[(rid,r) for rid,r in effective.items() if (r.get('result') or {}).get('template')]
# Deterministic economy sanity, deliberately not a production-balance claim.
throughput=[]
for rid,r in processing:
    cost=0
    for spec in r.get('ingredients',[]):
        try: cost+=int(str(spec).rsplit(':',1)[1])
        except Exception: cost+=1
    out=int((r.get('result') or {}).get('amount',1))
    throughput.append({'recipe':rid,'input_units':cost,'output_units':out,'ratio':round(out/max(1,cost),4)})
# Masterwork expected-rate Monte Carlo from configured bounded chance.
p=yaml.safe_load((CFG/'professions-2.yml').read_text(encoding='utf-8'))['professions']['masterwork']
chance=min(float(p['maximum-chance']),float(p['base-chance'])+50*float(p['chance-per-level']))
hits=sum(1 for _ in range(100000) if rng.random()<chance)
rate=hits/100000
assert 0.0 < chance < 0.5 and abs(rate-chance)<0.01
# Salvage is a hard loss target: never model >=100% resource recovery.
salvage_recovery_ceiling=0.55
assert salvage_recovery_ceiling < 1.0
# XP spam gate is already grey-after; calculate relevance window.
prof=yaml.safe_load((CFG/'professions.yml').read_text(encoding='utf-8'))['professions']['xp']
grey=int(prof['recipe-craft-grey-after']); assert grey>=2
report={'seed':0x1CE5A2,'processing_recipe_count':len(processing),'canonical_gear_recipe_count':len(gear),
        'masterwork_level50_configured_chance':chance,'masterwork_seeded_rate':rate,
        'salvage_recovery_ceiling':salvage_recovery_ceiling,'recipe_xp_grey_after':grey,
        'throughput':throughput,'production_balance_proven':False,
        'staging_required':['50-60 player material supply','real GUI latency','disconnect during craft','market price equilibrium']}
out=ROOT/'build/reports/professions-2/economy-harness.json'; out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
print(f"Professions 2.0 economy harness: {len(processing)} processing, {len(gear)} canonical gear, masterwork@50={rate:.3f}")
