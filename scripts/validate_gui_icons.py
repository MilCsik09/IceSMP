#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re, sys
ROOT = pathlib.Path(__file__).resolve().parents[1]
MODEL_RE = re.compile(r'^(?:icesmp:)?([a-z0-9_]+)$')
used: dict[str,set[str]] = {}
authority_paths = list((ROOT/'src/main/resources/config').glob('*.yml'))
authority_paths += list((ROOT/'src/main/resources/content').rglob('*.yml'))
for path in authority_paths:
    text=path.read_text(encoding='utf-8')
    for match in re.finditer(r'(?:key-)?item-model:\s*["\']?([^"\'\s#}]+)', text):
        raw=match.group(1); parsed=MODEL_RE.match(raw)
        if parsed: used.setdefault(parsed.group(1),set()).add(str(path.relative_to(ROOT)))
for path in (ROOT/'src/main/java').rglob('*.java'):
    text=path.read_text(encoding='utf-8')
    for match in re.finditer(r'applyItemModel\([^;]*?"icesmp:([a-z0-9_]+)"', text, re.S):
        model=match.group(1)
        if not model.endswith('_'): used.setdefault(model,set()).add(str(path.relative_to(ROOT)))
manifest=(ROOT/'docs/RESOURCE_PACK_CMD.md').read_text(encoding='utf-8')
manifest_models=set(re.findall(r'^### `([a-z0-9_]+)`',manifest,re.M))|set(re.findall(r'\| `([a-z0-9_]+)` \|',manifest))
pack_models={p.stem for p in (ROOT/'resource-pack/assets/icesmp/items').glob('*.json')}
missing_manifest=sorted(set(used)-manifest_models)
missing_pack=sorted(set(used)-pack_models)
fallback='missing_icon'
if fallback not in pack_models:
    fallback='PAPER'
errors=[]
if missing_manifest: errors.append('missing manifest: '+', '.join(missing_manifest))
if missing_pack: errors.append('missing pack: '+', '.join(missing_pack))
print(f'GUI_ICON_COVERAGE used={len(used)} manifest={len(manifest_models)} pack={len(pack_models)} '
      f'missing_manifest={len(missing_manifest)} missing_pack={len(missing_pack)} fallback={fallback}')
if errors:
    print('\n'.join(errors),file=sys.stderr); raise SystemExit(1)
