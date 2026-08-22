#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter, defaultdict
from pathlib import Path
import json
import yaml

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "src/main/resources/config"
DOC = ROOT / "docs/development"
FAMILIES = {"CLOTH", "LEATHER", "MAIL", "PLATE"}

NEW_RECIPES = {
    "p2_fonixpihe_kopeny": {
        "profession": "armorer", "kind": "equipment", "economy-category": "EQUIPMENT",
        "material-tier": "SPECIAL", "economy-managed": True, "batchable": False, "batch-limit": 1,
        "processing-dependencies": ["runaszott_poszto", "fonixpihe"], "level": 40, "learn": "level",
        "display-name": "Főnixpihe Köpeny", "category": "Equipment 2.0 • CLOTH",
        "result": {"template": "fonixpihe_kopeny", "amount": 1, "masterwork": True},
        "ingredients": ["unique:runaszott_poszto:3", "unique:fonixpihe:2", "GOLD_INGOT:1"],
    },
    "p2_vadorzo_csizma": {
        "profession": "armorer", "kind": "equipment", "economy-category": "EQUIPMENT",
        "material-tier": "SPECIAL", "economy-managed": True, "batchable": False, "batch-limit": 1,
        "processing-dependencies": ["erositett_bor"], "level": 24, "learn": "level",
        "display-name": "Vadőrző Csizma", "category": "Equipment 2.0 • LEATHER",
        "result": {"template": "vadorzo_csizma", "amount": 1, "masterwork": True},
        "ingredients": ["unique:erositett_bor:2", "STRING:2"],
    },
    "p2_csontenyv_pancel": {
        "profession": "armorer", "kind": "equipment", "economy-category": "EQUIPMENT",
        "material-tier": "SPECIAL", "economy-managed": True, "batchable": False, "batch-limit": 1,
        "processing-dependencies": ["cserzett_bor", "sodrott_lancszem"], "level": 28, "learn": "level",
        "display-name": "Csontenyv Páncél", "category": "Equipment 2.0 • MAIL",
        "result": {"template": "csontenyv_pancel", "amount": 1, "masterwork": True},
        "ingredients": ["unique:cserzett_bor:1", "unique:sodrott_lancszem:3", "BONE:4"],
    },
    "p2_szovet_reclamation": {
        "profession": "enchanter", "kind": "service", "economy-category": "UPGRADE_SERVICE",
        "material-tier": "COMMON", "economy-managed": True, "batchable": True, "batch-limit": 8,
        "processing-dependencies": ["szovet_foszlany"], "level": 20, "learn": "level",
        "display-name": "Szövetfoszlány visszanyerés", "category": "Salvage • Visszanyerés",
        "result": {"unique": "runapor", "amount": 1},
        "ingredients": ["unique:szovet_foszlany:6", "AMETHYST_SHARD:1"],
    },
    "p2_bor_reclamation": {
        "profession": "alchemist", "kind": "service", "economy-category": "UPGRADE_SERVICE",
        "material-tier": "COMMON", "economy-managed": True, "batchable": True, "batch-limit": 8,
        "processing-dependencies": ["bor_hulladek"], "level": 20, "learn": "level",
        "display-name": "Bőrhulladék visszanyerés", "category": "Salvage • Visszanyerés",
        "result": {"unique": "cserzett_bor", "amount": 1},
        "ingredients": ["unique:bor_hulladek:6", "SUGAR:1"],
    },
    "p2_lanc_reclamation": {
        "profession": "armorer", "kind": "service", "economy-category": "UPGRADE_SERVICE",
        "material-tier": "COMMON", "economy-managed": True, "batchable": True, "batch-limit": 8,
        "processing-dependencies": ["lanc_toredek"], "level": 22, "learn": "level",
        "display-name": "Lánctöredék visszanyerés", "category": "Salvage • Visszanyerés",
        "result": {"unique": "finom_huzal", "amount": 1},
        "ingredients": ["unique:lanc_toredek:8", "COPPER_INGOT:1"],
    },
    "p2_fem_reclamation": {
        "profession": "armorer", "kind": "service", "economy-category": "UPGRADE_SERVICE",
        "material-tier": "COMMON", "economy-managed": True, "batchable": True, "batch-limit": 8,
        "processing-dependencies": ["femhulladek"], "level": 24, "learn": "level",
        "display-name": "Fémhulladék visszanyerés", "category": "Salvage • Visszanyerés",
        "result": {"unique": "edzett_otvozet", "amount": 1},
        "ingredients": ["unique:femhulladek:10", "COPPER_INGOT:1", "COAL:2"],
    },
}
SCRAPS = ("szovet_foszlany", "bor_hulladek", "lanc_toredek", "femhulladek")


def load_yaml(path: Path):
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def dump_yaml(path: Path, data):
    path.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=120), encoding="utf-8")


def patch_overlay():
    path = CFG / "professions-2.yml"
    doc = load_yaml(path)
    materials = doc.setdefault("profession-materials", {})
    for scrap in SCRAPS:
        if scrap not in materials:
            raise RuntimeError(f"missing family salvage material {scrap}")
        materials[scrap]["sink-types"] = ["reclamation", "market"]
    recipes = doc.setdefault("profession-recipes", {})
    for rid, recipe in NEW_RECIPES.items():
        recipes[rid] = recipe
    dump_yaml(path, doc)


def patch_listener():
    path = ROOT / "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java"
    text = path.read_text(encoding="utf-8")
    pre = '''                if (professionCraft && instance.origin().masterwork()) {
                    hu.taliann.icesmp.managers.AdvancementService.award(player, "masterwork");
                }
'''
    if pre in text:
        text = text.replace(pre, "", 1)
    marker = '''        EquippedCombatPowerService.refreshAfterMutation(player);
'''
    post = '''        EquippedCombatPowerService.refreshAfterMutation(player);
        // Masterwork achievement is post-commit: failed inventory/material preflight grants nothing.
        if (masterworkCount > 0) {
            hu.taliann.icesmp.managers.AdvancementService.award(player, "masterwork");
        }
'''
    if post not in text:
        if marker not in text:
            raise RuntimeError("craft transaction post-commit marker missing")
        text = text.replace(marker, post, 1)
    award_pos = text.find('AdvancementService.award(player, "masterwork")')
    tx_pos = text.find("craftTransaction.apply(player, recipe, batches, outputs)")
    if award_pos < 0 or tx_pos < 0 or award_pos < tx_pos:
        raise RuntimeError("Masterwork advancement is not post-transaction")
    path.write_text(text, encoding="utf-8")


def effective_state():
    base = load_yaml(CFG / "profession-recipes.yml").get("profession-recipes", {})
    overlay = load_yaml(CFG / "professions-2.yml")
    patches = overlay.get("profession-recipes", {})
    effective = {rid: dict(recipe) for rid, recipe in base.items()}
    for rid, patch in patches.items():
        merged = dict(effective.get(rid, {}))
        merged.update(patch)
        effective[rid] = merged
    templates = load_yaml(CFG / "item-templates.yml").get("item-templates", {})
    materials = load_yaml(CFG / "profession-materials.yml").get("profession-materials", {})
    materials = {**materials, **overlay.get("profession-materials", {})}
    return base, patches, effective, templates, materials


def category_for(recipe):
    explicit = str(recipe.get("economy-category", "")).upper()
    if explicit:
        return explicit
    result = recipe.get("result") or {}
    if result.get("template"):
        return "EQUIPMENT"
    kind = str(recipe.get("kind", "")).lower()
    cat = str(recipe.get("category", "")).lower()
    if kind == "hozam" or "alapanyag" in cat:
        return "PROCESSING"
    if result.get("affix-tier") or any(token in cat for token in ("fegyver", "páncél", "szerszám")):
        return "RETUNE"
    if any(token in cat for token in ("étel", "ital", "főzet", "consum")):
        return "CONSUMABLE"
    if kind == "gyakorlo":
        return "KEEP"
    return "UTILITY"


def unique_ingredients(recipe):
    out = []
    for spec in recipe.get("ingredients", []):
        value = str(spec)
        if value.startswith("unique:"):
            parts = value.split(":")
            if len(parts) >= 2:
                out.append(parts[1])
    return out


def rebuild_reports():
    base, patches, effective, templates, materials = effective_state()
    if len(base) != 392:
        raise RuntimeError(f"baseline recipe drift: {len(base)}")

    categories = Counter()
    rows = []
    for rid, recipe in effective.items():
        result = recipe.get("result") or {}
        template_id = result.get("template")
        template = templates.get(template_id, {}) if template_id else {}
        family = str(template.get("armor-family", "")).upper() or None
        econ = category_for(recipe)
        categories[econ] += 1
        if rid not in base:
            action, old_cat, status = "ADD", None, "ADDED"
        elif template_id:
            action = "MIGRATE" if family else "RETUNE"
            old_cat, status = base[rid].get("category", ""), "VERIFIED"
        else:
            action = econ if econ in {"KEEP", "RETUNE", "PROCESSING", "CONSUMABLE", "UTILITY", "UPGRADE_SERVICE"} else "RETUNE"
            old_cat, status = base[rid].get("category", ""), "VERIFIED"
        rows.append({
            "recipe_id": rid, "old_category": old_cat, "new_category": recipe.get("category", ""),
            "economy_category": econ, "owner_profession": recipe.get("profession"),
            "output": template_id or result.get("unique") or result.get("material"),
            "armor_family": family, "processing_dependency": list(recipe.get("processing-dependencies", [])),
            "migration_action": action, "status": status,
        })

    canonical_rows = []
    family_counts = Counter()
    for rid, recipe in effective.items():
        result = recipe.get("result") or {}
        template_id = result.get("template")
        if not template_id:
            continue
        template = templates.get(template_id)
        if not template:
            raise RuntimeError(f"recipe {rid} references missing ItemTemplate {template_id}")
        family = str(template.get("armor-family", "")).upper() or None
        if family:
            if family not in FAMILIES:
                raise RuntimeError(f"invalid ArmorFamily {family} on {template_id}")
            family_counts[family] += 1
        canonical_rows.append({
            "recipe_id": rid, "template_id": template_id, "slot": template.get("slot"),
            "armor_family": family, "old_owner": base.get(rid, {}).get("profession"),
            "new_owner": recipe.get("profession"),
            "processing_dependency": list(recipe.get("processing-dependencies", [])),
            "migration_action": "ADD" if rid not in base else ("MIGRATE" if family else "RETUNE"),
            "status": "VERIFIED",
        })
    if len(canonical_rows) != 18:
        raise RuntimeError(f"expected 18 canonical profession recipes after family closure, got {len(canonical_rows)}")
    if set(family_counts) != FAMILIES or any(family_counts[f] <= 0 for f in FAMILIES):
        raise RuntimeError(f"incomplete family endpoints: {dict(family_counts)}")
    if not any({"cserzett_bor", "sodrott_lancszem"}.issubset(set(r["processing_dependency"]))
               for r in canonical_rows if r["armor_family"] == "MAIL"):
        raise RuntimeError("MAIL endpoint is not a mixed leather/metal dependency")

    produces, consumes = defaultdict(list), defaultdict(list)
    prof_produces, prof_consumes = defaultdict(set), defaultdict(set)
    for rid, recipe in effective.items():
        result = recipe.get("result") or {}
        if result.get("unique"):
            mid = result["unique"]
            produces[mid].append(rid)
            prof_produces[str(recipe.get("profession"))].add(mid)
        for mid in unique_ingredients(recipe):
            consumes[mid].append(rid)
            prof_consumes[str(recipe.get("profession"))].add(mid)

    nodes, dead = [], []
    for mid, definition in sorted(materials.items()):
        sources = list(definition.get("source-types", []))
        sinks = list(definition.get("sink-types", []))
        producer_ids = sorted(produces.get(mid, []))
        consumer_ids = sorted(consumes.get(mid, []))
        if not sources and producer_ids:
            sources = [f"recipe:{rid}" for rid in producer_ids]
        if not sinks and consumer_ids:
            sinks = [f"recipe:{rid}" for rid in consumer_ids]
        managed = bool(definition.get("economy-managed", False))
        if managed and (not sources or not sinks):
            dead.append(mid)
        nodes.append({
            "id": mid, "tier": definition.get("tier", "LEGACY"),
            "processing_state": definition.get("processing-state", "LEGACY"),
            "sources": sources, "sinks": sinks, "producer_recipes": producer_ids,
            "consumer_recipes": consumer_ids, "economy_managed": managed,
        })
    if dead:
        raise RuntimeError(f"managed material dead ends: {dead}")
    for scrap in SCRAPS:
        if not consumes.get(scrap):
            raise RuntimeError(f"salvage material has no real recipe sink: {scrap}")

    edges, graph = [], defaultdict(set)
    for rid, recipe in patches.items():
        result = recipe.get("result") or {}
        output = result.get("unique") or result.get("template")
        if output:
            for dep in recipe.get("processing-dependencies", []):
                graph[output].add(dep)
                edges.append({"from": dep, "to": output, "recipe": rid})
    visiting, done = set(), set()
    def dfs(node):
        if node in done:
            return
        if node in visiting:
            raise RuntimeError(f"processing cycle at {node}")
        visiting.add(node)
        for nxt in graph.get(node, ()):
            if nxt in graph:
                dfs(nxt)
        visiting.remove(node)
        done.add(node)
    for node in list(graph):
        dfs(node)

    migration = {
        "schema": 2, "baseline_recipe_count": len(base), "effective_recipe_count": len(effective),
        "canonical_recipe_count": len(canonical_rows), "category_summary": dict(sorted(categories.items())),
        "recipes": sorted(rows, key=lambda r: r["recipe_id"]),
    }
    economy = {
        "schema": 2,
        "north_star": "survival_gathering -> processing -> crafting -> equipment/utility -> use/trade/salvage -> upgrade -> market",
        "profession_nodes": [
            {"profession": p, "produces": sorted(prof_produces[p]), "consumes": sorted(prof_consumes[p])}
            for p in sorted(set(prof_produces) | set(prof_consumes))
        ],
        "material_nodes": nodes, "new_processing_edges": sorted(edges, key=lambda e: (e["from"], e["to"], e["recipe"])),
        "canonical_equipment": canonical_rows, "family_distribution": dict(sorted(family_counts.items())),
        "dead_managed_materials": dead, "cycles": [], "mail_mixed_dependency_verified": True,
        "salvage_reclamation_sinks_verified": list(SCRAPS),
    }
    theme = {"CLOTH": "woven arcane textile", "LEATHER": "treated reinforced hide",
             "MAIL": "hybrid leather and light rings", "PLATE": "forged alloy plate"}
    handoff = {
        "schema": 2, "authority": "Equipment 2.0 ArmorFamily remains canonical",
        "items": [{
            "recipe_id": row["recipe_id"], "template_id": row["template_id"],
            "crafting_source": "profession:craft", "profession": row["new_owner"],
            "material_theme": theme.get(row["armor_family"], "authored utility/weapon"),
            "armor_family": row["armor_family"],
            "visual_theme_hint": "preserve authored template silhouette; Professions 2.0 adds no new equipment asset",
        } for row in canonical_rows],
    }
    DOC.mkdir(parents=True, exist_ok=True)
    (DOC / "professions-2-recipe-migration.json").write_text(json.dumps(migration, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (DOC / "professions-2-economy-graph.json").write_text(json.dumps(economy, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (DOC / "professions-2-rp-handoff.json").write_text(json.dumps(handoff, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_report_checker():
    (ROOT / "scripts/check_professions_2_reports.py").write_text(r'''#!/usr/bin/env python3
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
''', encoding="utf-8")


def write_balance_harness():
    (ROOT / "scripts/test_professions_2_economy.py").write_text(r'''#!/usr/bin/env python3
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
''', encoding="utf-8")


def patch_docs():
    additions = {
        ROOT/"docs/ARCHITECTURE.md": "### Professions 2.0 family closure\nA végső canonical páncél-összeállítás az Armorer gazdasági szerepe. CLOTH-hoz az Enchanter textil-feldolgozása, LEATHER-höz az Alchemist bőrkezelése kell; MAIL explicit bőr + könnyű fém dependency. Ez crafting expertise, nem class proficiency. A family scrap csak veszteséges reclamation útvonalon kerül vissza köztes anyagba.",
        ROOT/"docs/FEATURES.md": "### Professions 2.0 family crafting\nA négy Equipment 2.0 family mind rendelkezik profession craft végponttal: CLOTH, LEATHER, MAIL és PLATE. A salvage-family maradékoknak valós, veszteséges visszanyerési sinkjük van; boss-komponens nem állítható vissza salvage-ből.",
        ROOT/"docs/PLAYER_GUIDE.md": "### Hogyan lesz a feldolgozott anyagból páncél?\nA Kovács (Armorer) rakja össze a végső canonical páncélt, de nem önellátó: a CLOTH textilhez Bűvölő, a LEATHER kezelt bőrhöz Alkimista munka kell, a MAIL pedig kezelt bőrt és sodronyt is kér. A salvage maradék visszaforgatható, de mindig veszteséggel.",
        ROOT/"docs/ADMIN_GUIDE.md": "### Professions 2.0 hardening gate\nA `scripts/check_professions_2_reports.py` ellenőrzi a 392 baseline recept teljes kategorizálását, a 18 canonical recipe-t, a négy ArmorFamily craft-végpontot, a MAIL mixed dependencyt, a salvage scrap valódi sinkeket és a post-commit Masterwork advancement sorrendet.",
        ROOT/"docs/LATEST_CHANGES.md": "- Professions 2.0 adversarial closure: meglévő Equipment 2.0 template-ekkel létrejött CLOTH/LEATHER/MAIL craft-végpont, family-salvage reclamation sink, és a Masterwork achievement csak sikeres inventory commit után jár.",
    }
    for path, block in additions.items():
        text = path.read_text(encoding="utf-8")
        marker = block.splitlines()[0]
        if marker not in text:
            path.write_text(text.rstrip() + "\n\n" + block + "\n", encoding="utf-8")


def validate():
    base, patches, effective, templates, materials = effective_state()
    assert len(base)==392 and len(effective)==407
    gear=[r for r in effective.values() if (r.get("result") or {}).get("template")]
    assert len(gear)==18
    fams={str(templates[r["result"]["template"]].get("armor-family","")).upper()
          for r in gear if templates.get(r["result"]["template"],{}).get("armor-family")}
    assert fams==FAMILIES
    consumers=defaultdict(list)
    for rid,r in effective.items():
        for mid in unique_ingredients(r): consumers[mid].append(rid)
    assert all(consumers[s] for s in SCRAPS)


def main():
    patch_overlay()
    patch_listener()
    rebuild_reports()
    write_report_checker()
    write_balance_harness()
    patch_docs()
    validate()
    print("Professions 2.0 adversarial hardening applied: 392 baseline / 407 effective / 18 canonical / 4 families")


if __name__ == "__main__":
    main()
