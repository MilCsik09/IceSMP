#!/usr/bin/env python3
"""Fail-closed audit for IceSMP-owned persistent player state.

Every finding must be owned by PlayerProfile or explicitly documented in the
allowlist as runtime/item/entity/derived mirror, global aggregate reference, or
short-lived transition. The scanner intentionally uses stable file+kind+symbol
keys rather than line numbers so harmless formatting does not invalidate it.
"""
from __future__ import annotations
import argparse, hashlib, json, re, sys
from pathlib import Path

RULES = {
    "PLAYER_PDC": re.compile(r"(?:getPersistentDataContainer\s*\(|PersistentDataContainer|NamespacedKey\s*\()"),
    "UUID_MAP": re.compile(r"(?:Map|ConcurrentMap|LoadingCache)\s*<\s*UUID\b"),
    "PLAYER_YAML": re.compile(r"(?:YamlConfiguration|YamlStore|PersistentStore|resolve\([^\n]*(?:player|uuid|profile)|player[-_ ]?(?:data|profile)[^\n]*\.ya?ml)", re.I),
    "DIRECT_FILE_IO": re.compile(r"(?:Files\.(?:read|write|move|delete|create)|FileChannel\.open|new\s+File\s*\()[^\n]*(?:player|uuid|profile)", re.I),
    "LEGACY_NOOP": re.compile(r"(?:ClassProfile|ICS2|ProfileV2|resetClassProfileV2|(?:legacy|deprecated)[^\n]*(?:class|spec|pet|soul|shard))", re.I),
}
SOURCE_SUFFIXES={".java", ".kt", ".kts"}
IGNORE_DIRS={"build",".gradle","resource-pack","node_modules",".git"}

def stable_symbol(line: str) -> str:
    line=re.sub(r"//.*$", "", line).strip()
    line=re.sub(r"\s+", " ", line)
    return line[:240]

def scan(root: Path):
    findings=[]
    for path in sorted((root/'src').rglob('*')):
        if not path.is_file() or path.suffix not in SOURCE_SUFFIXES or any(part in IGNORE_DIRS for part in path.parts):
            continue
        rel=path.relative_to(root).as_posix()
        text=path.read_text(encoding='utf-8', errors='replace')
        for number,line in enumerate(text.splitlines(),1):
            for kind,pattern in RULES.items():
                if pattern.search(line):
                    symbol=stable_symbol(line)
                    key=f"{kind}|{rel}|{symbol}"
                    findings.append({"key":key,"kind":kind,"path":rel,"line":number,"symbol":symbol})
    return findings

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--root',default='.')
    ap.add_argument('--allowlist',default='scripts/player_profile_authority_allowlist.json')
    ap.add_argument('--write-report')
    ap.add_argument('--update-allowlist',action='store_true')
    args=ap.parse_args()
    root=Path(args.root).resolve(); allow_path=root/args.allowlist
    findings=scan(root)
    if args.update_allowlist:
        existing={}
        if allow_path.exists():
            existing={x['key']:x for x in json.loads(allow_path.read_text()).get('entries',[])}
        entries=[]
        for f in findings:
            old=existing.get(f['key'])
            if old: entries.append(old)
            else:
                path=f['path']; symbol=f['symbol']
                if '/playerprofile/' in path:
                    category='PLAYER_PROFILE_AUTHORITY'; reason='Canonical PlayerProfile domain, repository, transaction or API implementation.'
                elif any(token in symbol.lower() for token in ('itemmeta','itemstack','persistentdatatype')) and 'player' not in symbol.lower():
                    category='ITEM_METADATA'; reason='Persistent item identity or item-owned metadata, not player progression.'
                elif any(token in path.lower() for token in ('entity','mob','pet')) and 'player' not in symbol.lower():
                    category='ENTITY_METADATA'; reason='Entity/runtime marker; durable player state belongs to PlayerProfile.'
                elif f['kind']=='UUID_MAP':
                    category='RUNTIME'; reason='In-memory runtime/session map; must not be a durable authority.'
                elif any(token in path.lower() for token in ('guild','party','auction','market','claim','season','raid','territory')):
                    category='GLOBAL_AGGREGATE_REFERENCE'; reason='Shared aggregate remains separate; only player-owned references may enter PlayerProfile.'
                else:
                    category='TRANSITION'; reason='Existing player-state authority scheduled for removal by the stacked PlayerProfile integration PRs.'
                entries.append({'key':f['key'],'category':category,'reason':reason})
        payload={'version':1,'allowed_categories':['PLAYER_PROFILE_AUTHORITY','RUNTIME','DERIVED_MIRROR','ITEM_METADATA','ENTITY_METADATA','GLOBAL_AGGREGATE_REFERENCE','TRANSITION'],'entries':sorted(entries,key=lambda x:x['key'])}
        allow_path.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n')
    data=json.loads(allow_path.read_text()) if allow_path.exists() else {'entries':[]}
    allowed={e['key']:e for e in data.get('entries',[])}
    current={f['key']:f for f in findings}
    unknown=[f for k,f in current.items() if k not in allowed]
    stale=[e for k,e in allowed.items() if k not in current]
    invalid=[e for e in allowed.values() if not e.get('reason') or e.get('category') not in data.get('allowed_categories',[])]
    transitions=[e for e in allowed.values() if e.get('category')=='TRANSITION']
    report={'finding_count':len(findings),'unknown':unknown,'stale':stale,'invalid':invalid,'transition_count':len(transitions),'fingerprint':hashlib.sha256('\n'.join(sorted(current)).encode()).hexdigest()}
    if args.write_report:
        out=root/args.write_report; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(f"PlayerProfile authority guard: {len(findings)} findings, {len(unknown)} unknown, {len(stale)} stale, {len(invalid)} invalid, {len(transitions)} transition")
    for label,items in [('UNKNOWN',unknown),('STALE',stale),('INVALID',invalid)]:
        for item in items[:50]: print(f"{label}: {item.get('key')}")
    return 1 if unknown or stale or invalid else 0
if __name__=='__main__': raise SystemExit(main())
