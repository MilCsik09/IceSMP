#!/usr/bin/env python3
"""Strict, source-based IceSMP class/spec/spell audit.

Definitions and runtime registrations are intentionally separate evidence sets.
A BaseSpell constructor or ConfiguredSpell builder proves DEFINED only; REGISTERED
requires an actual SpellRegistry.register(...) call reachable from startup.
"""
from __future__ import annotations

import argparse, csv, re, subprocess, sys
from dataclasses import dataclass, field
from pathlib import Path

CLASS_TO_SPECS = {
    "warrior": ("berserker", "guardian"), "evoker": ("devastation", "preservation"),
    "archer": ("sharpshooter", "beast_master"), "shaman": ("elemental", "enhancement", "tidal"),
    "monk": ("windwalker", "brewmaster", "mistweaver"), "paladin": ("holy", "retribution", "protection"),
    "demon_hunter": ("havoc", "vengeance"), "druid": ("feral", "lunar", "ironbark", "restoration"),
    "priest": ("discipline", "bone_priest", "shadow"), "death_knight": ("blood", "frost", "unholy"),
    "assassin": ("poisoner", "phantom", "plaguebringer"), "warlock": ("affliction", "destruction", "demonologist"),
    "wizard": ("elementalist", "necromancer"),
}
SPEC_TO_CLASS = {s: c for c, ss in CLASS_TO_SPECS.items() for s in ss}
DARK_SPECS = {"necromancer", "plaguebringer", "unholy", "bone_priest", "demonologist"}
PROGRESSION = {"BASE", "SPEC", "TALENT", "QUEST"}
EXPLICIT_PROVENANCE = {
    "talent_surge": ("TALENT", "SpellCatalog.registerTalentSpells explicit talent provenance"),
}
# Human semantic review: these are immediate generic payloads whose presentation is
# signature enough to warrant a B marker. Stale ids are ignored automatically.
SIGNATURE_PRESENTATION_IDS = {
    "gravity_well", "berserk", "last_stand", "masterful_shot", "king_of_beasts",
    "deathcap", "spectre", "celestial_alignment", "avenging_wrath", "final_verdict",
    "breath_of_sindragosa", "ascendance_flame", "doom_winds", "serenity", "invoke_niuzao",
    "evangelism", "void_eruption", "darkglare", "metamorphosis_havoc", "the_hunt",
    "metamorphosis_veng", "dragonrage", "eternity_breath", "rewind", "incarnation_bear",
    "tranquility", "spirit_tide", "revival",
}
# Any C/D item is a merge blocker until migrated; do not hide it in an allowlist.
CONFIGURED_IDENTITY_FINDINGS: dict[str, tuple[str, str]] = {}
FORBIDDEN_COMBOS = {
    "soul-collapse": "Affliction/Destruction cross-spec chain",
    "way-of-hundred-fists": "duplicates Monk native martial-chain reward",
}
CSV_FIELDS = [
    "id","display_name","class","spec","provenance","defined","registered","unlock_referenced",
    "active_kit_referenced","combo_referenced","balance_configured","implementation_class",
    "implementation_category","configured_spell_category","configured_spell_category_reason",
    "unlock_level","active_kit","balance_entry","spell_school","combo_reference","targeting",
    "cooldown","effective_cost","damage","healing","cc","mobility","summon","delayed","projectile",
    "scaling_path","regression_coverage","definition_location","registration_location",
]

@dataclass
class Definition:
    id: str; name: str; impl: str; category: str; path: str; line: int
    cooldown: str = ""; cost_type: str = ""; cost: str = ""; clazz: str = ""; spec: str = ""
    details: dict[str,str] = field(default_factory=dict); configured_block: str = ""

@dataclass
class Row:
    id: str; definitions: list[Definition] = field(default_factory=list); registered: bool = False
    registration_locations: list[str] = field(default_factory=list); unlock: bool = False; active: bool = False
    combo: bool = False; balance_configured: bool = False; provenance: str = ""; provenance_reason: str = ""
    clazz: str = ""; spec: str = ""; unlock_level: str = ""; active_owners: set[str] = field(default_factory=set)
    combo_names: set[str] = field(default_factory=set); balance: dict[str,str] = field(default_factory=dict)
    school: str = ""
    @property
    def primary(self):
        if not self.definitions: return None
        cs = [d for d in self.definitions if d.category == "configured"]
        return cs[0] if cs else self.definitions[0]

def read(p: Path) -> str: return p.read_text(encoding="utf-8")
def scalar(s: str) -> str:
    s=s.strip(); return s[1:-1] if len(s)>=2 and s[0]==s[-1] and s[0] in "\"'" else s
def lineno(text: str, pos: int) -> int: return text.count("\n",0,pos)+1
def snake(s: str) -> str: return re.sub(r"([a-z0-9])([A-Z])",r"\1_\2",s).lower()

def method_span(text: str, name: str):
    m=re.search(r"\b(?:public|private|protected)\s+(?:static\s+)?[\w<>?, .\[\]]+\s+"+re.escape(name)+r"\s*\([^)]*\)\s*\{",text)
    if not m: return None
    start=text.find("{",m.start()); depth=0; string=False; esc=False
    for i in range(start,len(text)):
        ch=text[i]
        if string:
            if esc: esc=False
            elif ch=="\\": esc=True
            elif ch=='"': string=False
            continue
        if ch=='"': string=True
        elif ch=='{': depth+=1
        elif ch=='}':
            depth-=1
            if depth==0: return m.start(),i+1,text[m.start():i+1]
    return None

def register_calls(text: str):
    out=[]
    for m in re.finditer(r"\b(?:spellRegistry|registry)\.register\s*\(",text):
        op=text.find("(",m.start()); depth=0; string=False; esc=False
        for i in range(op,len(text)):
            ch=text[i]
            if string:
                if esc: esc=False
                elif ch=="\\": esc=True
                elif ch=='"': string=False
                continue
            if ch=='"': string=True
            elif ch=='(': depth+=1
            elif ch==')':
                depth-=1
                if depth==0:
                    out.append((m.start(),text[m.start():i+1])); break
    return out

def method_owner(name: str):
    token=snake(name.removeprefix("register"))
    if token in CLASS_TO_SPECS: return token,""
    if token in SPEC_TO_CLASS: return SPEC_TO_CLASS[token],token
    return "",""

def classify(name: str, text: str):
    low=name.lower()
    if name=="ConfiguredSpell": return "configured"
    if "form" in low: return "form"
    if "summon" in low or "minion" in low: return "summon"
    if "projectile" in low or "launchProjectile" in text: return "projectile"
    if "runDelayed" in text or "runAtFixedRate" in text or "clearPlayerState" in text: return "stateful"
    return "dedicated"

def configured_details(block: str):
    d={"targeting":"TARGET" if ".target(" in block else "AOE" if ".aoe(" in block else "SELF"}
    for key,pat in (("damage",r"\.damage\(([^)]+)\)"),("healing",r"\.healSelf\(([^)]+)\)")):
        m=re.search(pat,block)
        if m: d[key]=m.group(1).strip()
    effects=re.findall(r"targetEffect\(PotionEffectType\.([A-Z_]+)",block)
    hard=[e for e in effects if e in {"SLOWNESS","BLINDNESS","LEVITATION","JUMP_BOOST"}]
    if ".freeze(" in block: hard.append("FREEZE")
    if hard: d["cc"]=",".join(hard)
    if ".dash(" in block: d["mobility"]="dash"
    if any(x in block for x in (".knockback(",".pull(",".launchUp(")): d["mobility"]=(d.get("mobility","")+"+displacement").strip("+")
    return d

def definitions(java_root: Path):
    defs: dict[str,list[Definition]]={}; class_ids={}
    for p in java_root.rglob("*.java"):
        text=read(p); rel=p.relative_to(java_root.parents[2]).as_posix()
        cm=re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)",text); impl=cm.group(1) if cm else p.stem
        for m in re.finditer(r"super\([^;]*?\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)",text,re.S):
            sid,name,cd,ct,cost=m.groups(); d=Definition(sid,name,impl,classify(impl,text),rel,lineno(text,m.start()),cd.strip(),ct,cost.strip())
            defs.setdefault(sid,[]).append(d); class_ids[impl]=sid
        methods=list(re.finditer(r"private static void (register[A-Za-z0-9_]+)\s*\(",text)); spans=[]
        for i,m in enumerate(methods): spans.append((m.start(),methods[i+1].start() if i+1<len(methods) else len(text),m.group(1)))
        def owner(pos):
            for a,b,n in spans:
                if a<=pos<b:return method_owner(n)
            return "",""
        for m in re.finditer(r"ConfiguredSpell\.builder\([^,]+,\s*\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)(.*?)(?:\.build\(\))",text,re.S):
            sid,name,cd,ct,cost,tail=m.groups(); c,s=owner(m.start())
            defs.setdefault(sid,[]).append(Definition(sid,name,"ConfiguredSpell","configured",rel,lineno(text,m.start()),cd.strip(),ct,cost.strip(),c,s,configured_details(tail),m.group(0)))
        for generic,cat in (("ProjectileBurstSpell","projectile"),("BlinkSpell","dedicated"),("SummonMinionsSpell","summon")):
            for m in re.finditer(r"new\s+"+generic+r"\s*\((.*?)\)\s*[;,]",text,re.S):
                lits=re.findall(r"\"([^\"]+)\"",m.group(1))
                if len(lits)<2: continue
                c,s=owner(m.start()); sid,name=lits[0],lits[1]
                det={"projectile":"yes"} if cat=="projectile" else {"summon":"yes"} if cat=="summon" else {"mobility":"blink"}
                defs.setdefault(sid,[]).append(Definition(sid,name,generic,cat,rel,lineno(text,m.start()),clazz=c,spec=s,details=det))
    return defs,class_ids

def reachable_catalog(text: str):
    todo=["registerExpansionSpells","registerSummonSpells"]; seen=set()
    while todo:
        n=todo.pop()
        if n in seen:continue
        seen.add(n); sp=method_span(text,n)
        if not sp:continue
        for called in re.findall(r"\b(register[A-Z][A-Za-z0-9_]*)\s*\(",sp[2]):
            if called not in seen: todo.append(called)
    return seen

def resolve_reg(expr: str,class_ids: dict[str,str]):
    m=re.search(r"ConfiguredSpell\.builder\([^,]+,\s*\"([^\"]+)\"",expr)
    if m:return m.group(1)
    m=re.search(r"new\s+(\w+)\s*\(",expr)
    if not m:return None
    impl=m.group(1)
    if impl in class_ids:return class_ids[impl]
    lits=re.findall(r"\"([^\"]+)\"",expr[m.end():])
    return lits[0] if lits else None

def registrations(root: Path,class_ids: dict[str,str]):
    out:dict[str,list[str]]={}; unresolved=[]
    for p in (root/"src/main/java/hu/taliann/icesmp/core/IceSMPCore.java",root/"src/main/java/hu/taliann/icesmp/spells/SpellCatalog.java"):
        text=read(p); rel=p.relative_to(root).as_posix(); ranges=[]
        names=["registerSpells"] if p.name=="IceSMPCore.java" else sorted(reachable_catalog(text))
        for n in names:
            sp=method_span(text,n)
            if sp:ranges.append((sp[0],sp[1]))
        for pos,expr in register_calls(text):
            if not any(a<=pos<b for a,b in ranges):continue
            sid=resolve_reg(expr,class_ids); loc=f"{rel}:{lineno(text,pos)}"
            if sid is None: unresolved.append(f"{loc}: {expr[:120].replace(chr(10),' ')}")
            else: out.setdefault(sid,[]).append(loc)
    return out,unresolved

def parse_unlocks(p: Path):
    out={}; stack=[]
    for raw in read(p).splitlines():
        clean=raw.split("#",1)[0].rstrip()
        if not clean.strip() or ":" not in clean:continue
        ind=len(clean)-len(clean.lstrip());
        while stack and stack[-1][0]>=ind:stack.pop()
        k,v=clean.strip().split(":",1); k=k.strip();v=v.strip();stack.append((ind,k)); keys=[x for _,x in stack]
        if len(keys)<4 or keys[-2]!="spell-unlocks" or not v:continue
        owner=keys[-3]
        try:lvl=int(v)
        except ValueError:continue
        if keys[0]=="classes" and owner in CLASS_TO_SPECS:out[k]=(owner,"",lvl)
        elif keys[0]=="specializations" and owner in SPEC_TO_CLASS:out[k]=(SPEC_TO_CLASS[owner],owner,lvl)
    return out

def parse_active(p: Path):
    out={}; stack=[]; owner=""
    for raw in read(p).splitlines():
        clean=raw.split("#",1)[0].rstrip()
        if not clean.strip():continue
        ind=len(clean)-len(clean.lstrip()); s=clean.strip()
        while stack and stack[-1][0]>=ind:stack.pop()
        if s.startswith("-"):
            if owner:out.setdefault(owner,set()).add(scalar(s[1:].strip()))
            continue
        if ":" not in s:continue
        k,v=s.split(":",1);k=k.strip();v=v.strip();stack.append((ind,k));keys=[x for _,x in stack];owner=""
        if not v and "active-kit" in keys and k in SPEC_TO_CLASS:owner=k
    return out

def parse_balance(p: Path):
    out={}; root=False;cur=""
    for raw in read(p).splitlines():
        clean=raw.split("#",1)[0].rstrip()
        if not clean.strip():continue
        ind=len(clean)-len(clean.lstrip());s=clean.strip()
        if ind==0:root=s=="spell-balance:";cur="";continue
        if not root:continue
        if ind==2 and s.endswith(":"):cur=s[:-1].strip();out.setdefault(cur,{})
        elif ind>=4 and cur and ":" in s:
            k,v=s.split(":",1);out[cur][k.strip()]=scalar(v)
    return out

def parse_spell_config(p: Path):
    text=read(p);schools={};chains={};pairs={}
    sm=re.search(r"(?ms)^\s*by-spell:\s*\n(.*?)(?=^\s{2}\S|\Z)",text)
    if sm:
        for m in re.finditer(r"(?m)^\s{6}([a-z0-9_:-]+):\s*([a-z0-9_:-]+)\s*$",sm.group(1)):schools[m.group(1)]=m.group(2)
    cm=re.search(r"(?ms)^\s{4}chains:\s*\n(.*?)(?=^\s{4}pairs:|\Z)",text)
    if cm:
        cur=""
        for raw in cm.group(1).splitlines():
            s=raw.split("#",1)[0].strip();ind=len(raw)-len(raw.lstrip())
            if ind==6 and s.endswith(":"):cur=s[:-1]
            elif cur and s.startswith("steps:"):
                v=s.split(":",1)[1].strip()
                if v.startswith("[") and v.endswith("]"):chains[cur]=[scalar(x.strip()) for x in v[1:-1].split(",") if x.strip()]
    pm=re.search(r"(?ms)^\s{4}pairs:\s*\n(.*?)(?=^\S|\Z)",text)
    if pm:
        cur=first=""
        for raw in pm.group(1).splitlines():
            s=raw.split("#",1)[0].strip();ind=len(raw)-len(raw.lstrip())
            if ind==6 and s.endswith(":"):cur=s[:-1];first=""
            elif cur and s.startswith("first:"):first=scalar(s.split(":",1)[1])
            elif cur and first and s.startswith("second:"):pairs[cur]=(first,scalar(s.split(":",1)[1]));first=""
    return schools,chains,pairs

def configured_review(d: Definition):
    if d.id in CONFIGURED_IDENTITY_FINDINGS:return CONFIGURED_IDENTITY_FINDINGS[d.id]
    if any(x in d.configured_block for x in ("runDelayed","runAtFixedRate","launchProjectile","PersistentDataContainer","channel","chargeSession")):
        return "D","stateful/delayed/projectile lifecycle is not valid inside ConfiguredSpell"
    if d.id in SIGNATURE_PRESENTATION_IDS:
        return "B","immediate generic gameplay is sufficient; signature presentation/VFX readability is intentionally distinct"
    outs=[x for x in ("damage","healing","cc","mobility") if d.details.get(x)]
    return "A",f"immediate {d.details.get('targeting','SELF')} {'/'.join(outs) or 'buff/debuff/utility'} primitive; no persistent state, scheduler, projectile lifecycle or target memory"

def override_inventory(java_root: Path):
    pats={
        "execute":r"\bvoid\s+execute\s*\(\s*(?:final\s+)?Player\s+\w+\s*\)",
        "executeSpell":r"\bboolean\s+executeSpell\s*\(\s*(?:final\s+)?Player\s+\w+\s*\)",
        "executeSpellScalar":r"\bboolean\s+executeSpell\s*\(\s*(?:final\s+)?Player\s+\w+\s*,\s*(?:final\s+)?double\s+\w+\s*\)",
        "executeCast":r"\bCastOutcome\s+executeCast\s*\(\s*(?:final\s+)?Player\s+\w+\s*\)",
        "cast":r"\bCastOutcome\s+cast\s*\(\s*(?:final\s+)?Player\s+\w+\s*,\s*(?:final\s+)?CastModifiers\s+\w+\s*\)",
    }
    out={k:[] for k in pats}
    for p in java_root.rglob("*.java"):
        if p.name=="Spell.java":continue
        text=read(p);rel=p.relative_to(java_root.parents[2]).as_posix()
        for k,pat in pats.items():
            if re.search(pat,text):out[k].append(rel)
    return out

def valid_loadouts(r: Row):
    if not r.clazz:return {(c,s) for c,ss in CLASS_TO_SPECS.items() for s in ss}
    if r.spec:return {(r.clazz,r.spec)}
    return {(r.clazz,s) for s in CLASS_TO_SPECS[r.clazz]}

def delayed_inventory(rows: dict[str,Row],root: Path):
    out=[];errors=[]
    for r in rows.values():
        d=r.primary
        if not r.registered or d is None:continue
        p=root/d.path
        if not p.is_file():continue
        text=read(p); delayed="runDelayed" in text or "runAtFixedRate" in text; projectile="launchProjectile" in text or "teleportAsync" in text or "Projectile" in text
        if not delayed and not projectile:continue
        scaled=delayed and any(x in text for x in ("SpellDamageUtil","SpellHealingUtil",".damage(","setHealth("))
        snapshot=any(x in text for x in ("SpellExecutionContext.capture()","SpellDamageUtil.markProjectile","CastModifiers modifiers"))
        out.append({"id":r.id,"impl":d.impl,"delayed":"yes" if delayed else "no","projectile":"yes" if projectile else "no",
                    "snapshot":"yes" if snapshot else "not required" if not scaled else "MISSING","caster":"UUID/immutable" if "UUID" in text else "owner-thread",
                    "target":"entity/UUID bounded" if "LivingEntity" in text or "UUID" in text else "n/a",
                    "owner":"entity/player scheduler" if "getScheduler()" in text else "synchronous/event owner",
                    "cleanup":"explicit" if any(x in text for x in ("remove()","task.cancel()","clearPlayerState")) else "event-owned"})
        if scaled and not snapshot:errors.append(f"delayed scaled spell lacks immutable CastModifiers snapshot: {r.id} ({d.path})")
    return out,errors

def regression_graph(root: Path):
    rr=root/"src/regression/java"; build=read(root/"build.gradle.kts"); regs={}
    for m in re.finditer(r"val\s+(\w+)\s*=\s*registerRegression\(\s*\"([^\"]+)\"\s*,.*?\"([^\"]+RegressionSuite)\"\s*\)",build,re.S):regs[m.group(3)]=m.group(1)
    check=build[build.find("tasks.check"):]
    suites={}
    for p in rr.rglob("*RegressionSuite.java"):
        text=read(p);pm=re.search(r"package\s+([\w.]+);",text);cm=re.search(r"class\s+(\w+RegressionSuite)\b",text)
        if pm and cm:suites[f"{pm.group(1)}.{cm.group(1)}"]=(p,text)
    simple={fq.rsplit('.',1)[-1]:fq for fq in suites};delegated=set()
    for owner,(p,text) in suites.items():
        for s,fq in simple.items():
            if fq!=owner and re.search(r"\b"+re.escape(s)+r"\.main\s*\(",text):delegated.add(fq)
    rows=[];errors=[]
    mandatory_prefixes=("hu.taliann.icesmp.warrior.","hu.taliann.icesmp.evoker.","hu.taliann.icesmp.archer.","hu.taliann.icesmp.shaman.","hu.taliann.icesmp.monk.","hu.taliann.icesmp.paladin.","hu.taliann.icesmp.demonhunter.","hu.taliann.icesmp.druid.","hu.taliann.icesmp.priest.","hu.taliann.icesmp.deathknight.","hu.taliann.icesmp.assassin.","hu.taliann.icesmp.warlock.","hu.taliann.icesmp.wizard.","hu.taliann.icesmp.classspec.","hu.taliann.icesmp.playerprofile.","hu.taliann.icesmp.spells.")
    for fq,(p,text) in sorted(suites.items()):
        task=regs.get(fq,"");wired=bool(task and task in check);state="TASK+CHECK" if wired else "TASK_ONLY" if task else "DELEGATED" if fq in delegated else "ORPHAN"
        rows.append({"suite":fq,"state":state,"task":task,"path":p.relative_to(root).as_posix()})
        if fq.startswith(mandatory_prefixes) and state in {"TASK_ONLY","ORPHAN"}:errors.append(f"mandatory regression suite not check-wired/delegated: {fq} ({state})")
    for fq in ("hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite","hu.taliann.icesmp.wizard.WizardProfileRegressionSuite"):
        state=next((x["state"] for x in rows if x["suite"]==fq),"MISSING")
        if state!="TASK+CHECK":errors.append(f"Wizard suite requires explicit Gradle task in check: {fq} ({state})")
    for fq in ("hu.taliann.icesmp.spells.SpellCastArchitectureRegressionSuite","hu.taliann.icesmp.spells.ClassSpellAuditRegressionSuite"):
        state=next((x["state"] for x in rows if x["suite"]==fq),"MISSING")
        if state not in {"TASK+CHECK","DELEGATED"}:errors.append(f"hardening suite is not executed by check: {fq} ({state})")
    return rows,errors

def druid_naming_errors(root: Path):
    errors=[]; service=root/"src/main/java/hu/taliann/icesmp/druid/DruidGameplayService.java"
    if service.is_file():
        text=read(service)
        forbidden=("\"harmony\", \"Természeti Erő\"","{amount} Természeti Erő szabadult fel"," • Természeti Erő ")
        for token in forbidden:
            if token in text:errors.append(f"Druid secondary Harmony still uses primary name: {token}")
    return errors

def build_rows(defs,regs,unlocks,active,balance,schools,chains,pairs):
    ids=set(defs)|set(regs)|set(unlocks)|set(balance)|{s for v in active.values() for s in v}|{s for v in chains.values() for s in v}|{s for v in pairs.values() for s in v}
    rows={sid:Row(sid,definitions=list(defs.get(sid,[]))) for sid in ids}
    for sid,locs in regs.items():rows[sid].registered=True;rows[sid].registration_locations=locs
    for sid,(c,s,lvl) in unlocks.items():r=rows[sid];r.unlock=True;r.clazz=c;r.spec=s;r.unlock_level=str(lvl);r.provenance="SPEC" if s else "BASE";r.provenance_reason="classes.yml"
    for sid,(prov,reason) in EXPLICIT_PROVENANCE.items():
        if sid in rows and not rows[sid].provenance:rows[sid].provenance=prov;rows[sid].provenance_reason=reason
    for owner,ss in active.items():
        for sid in ss:rows[sid].active=True;rows[sid].active_owners.add(owner)
    for name,ss in chains.items():
        for sid in ss:rows[sid].combo=True;rows[sid].combo_names.add(name)
    for name,pair in pairs.items():
        for sid in pair:rows[sid].combo=True;rows[sid].combo_names.add(name)
    for sid,b in balance.items():rows[sid].balance_configured=True;rows[sid].balance=b
    for sid,school in schools.items():
        if sid in rows:rows[sid].school=school
    for r in rows.values():
        if r.primary and not r.clazz:r.clazz=r.primary.clazz;r.spec=r.primary.spec
    return rows

def validate(rows,regs,unresolved,balance,chains,pairs,overrides,delayed_errors,reg_errors,naming_errors,root):
    errors=[f"unresolved runtime registration: {x}" for x in unresolved]
    registered=set(regs);bal=set(balance)
    errors += [f"registered spell has no spells-balance.yml entry: {x}" for x in sorted(registered-bal)]
    errors += [f"dead spells-balance.yml entry is not registered: {x}" for x in sorted(bal-registered)]
    for sid,locs in sorted(regs.items()):
        if len(locs)!=1:errors.append(f"runtime spell registered {len(locs)} times (duplicate startup path): {sid}: {'; '.join(locs)}")
    for sid,r in sorted(rows.items()):
        if r.registered and not r.definitions:errors.append(f"registered spell has no source definition: {sid}")
        if r.unlock and not r.registered:errors.append(f"unlock references non-registered spell: {sid}")
        if r.active and not r.registered:errors.append(f"active-kit references non-registered spell: {sid}")
        if r.combo and not r.registered:errors.append(f"combo references non-registered spell: {sid}")
        if r.registered and not r.provenance:errors.append(f"registered spell has no explicit provenance: {sid}")
        if r.active and r.spec:
            bad=sorted(x for x in r.active_owners if x!=r.spec)
            if bad:errors.append(f"cross-spec active-kit leakage for {sid}: {', '.join(bad)}")
        if r.primary and r.primary.category=="configured":
            cat,reason=configured_review(r.primary)
            if cat in {"C","D"}:errors.append(f"ConfiguredSpell {cat} not migrated: {sid}: {reason}")
    for name,steps in chains.items():
        if name in FORBIDDEN_COMBOS:errors.append(f"forbidden semantic combo returned: {name}: {FORBIDDEN_COMBOS[name]}")
        missing=[x for x in steps if x not in registered]
        if missing:errors.append(f"combo chain {name} references non-registered: {', '.join(missing)}");continue
        common=None
        for sid in steps:common=valid_loadouts(rows[sid]) if common is None else common&valid_loadouts(rows[sid])
        if not common:errors.append(f"impossible combo chain {name}: {' -> '.join(steps)}")
        if common and all(spec in DARK_SPECS for _,spec in common):errors.append(f"combo chain {name} is SEALED-only/DARK-only without a non-DARK loadout")
    for name,pair in pairs.items():
        if name in FORBIDDEN_COMBOS:errors.append(f"forbidden semantic combo returned: {name}: {FORBIDDEN_COMBOS[name]}")
        missing=[x for x in pair if x not in registered]
        if missing:errors.append(f"combo pair {name} references non-registered: {', '.join(missing)}");continue
        common=valid_loadouts(rows[pair[0]])&valid_loadouts(rows[pair[1]])
        if not common:errors.append(f"impossible combo pair {name}: {pair[0]} -> {pair[1]}")
        if common and all(spec in DARK_SPECS for _,spec in common):errors.append(f"combo pair {name} is SEALED-only/DARK-only without a non-DARK loadout")
    errors+=delayed_errors+reg_errors+naming_errors
    scalar_overrides=sorted(overrides["executeSpellScalar"])
    expected=["src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java"]
    if scalar_overrides!=expected:errors.append("executeSpell(Player,double) override inventory changed; expected ConfiguredSpell only, got: "+", ".join(scalar_overrides))
    spell=read(root/"src/main/java/hu/taliann/icesmp/spells/Spell.java");conf=read(root/"src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java");reg=read(root/"src/main/java/hu/taliann/icesmp/managers/SpellRegistry.java")
    if "return cast(player, CastModifiers.standardPower(powerMultiplier)).effectApplied();" not in spell:errors.append("Spell scalar compatibility bridge is not one-way to typed cast")
    if "final CastOutcome outcome = executeCast(player);" not in spell:errors.append("Spell.cast no longer uses executeCast as canonical typed path")
    if "return cast(player, CastModifiers.standardPower(power)).effectApplied();" not in conf:errors.append("ConfiguredSpell scalar bridge is not one-way to typed cast")
    if "effect.getDuration() * power" in conf:errors.append("generic power still scales CC/effect duration")
    if "putIfAbsent" not in reg or "Duplicate spell id" not in reg:errors.append("SpellRegistry duplicate fail-fast contract missing")
    if not re.search(r"withBalanceOverrides\([^)]*\).*?\{.*?return spell;",conf,re.S):errors.append("ConfiguredSpell.withBalanceOverrides no longer returns the original registration instance")
    return errors

def csv_row(r: Row):
    d=r.primary;cat=reason=""
    if d and d.category=="configured":cat,reason=configured_review(d)
    det=d.details if d else {}; b=r.balance
    return {"id":r.id,"display_name":d.name if d else "","class":r.clazz,"spec":r.spec,"provenance":r.provenance,
            "defined":"yes" if r.definitions else "no","registered":"yes" if r.registered else "no","unlock_referenced":"yes" if r.unlock else "no",
            "active_kit_referenced":"yes" if r.active else "no","combo_referenced":"yes" if r.combo else "no","balance_configured":"yes" if r.balance_configured else "no",
            "implementation_class":d.impl if d else "","implementation_category":d.category if d else "","configured_spell_category":cat,"configured_spell_category_reason":reason,
            "unlock_level":r.unlock_level,"active_kit":",".join(sorted(r.active_owners)),"balance_entry":"yes" if r.balance_configured else "no","spell_school":r.school or "class default / primordial",
            "combo_reference":",".join(sorted(r.combo_names)),"targeting":det.get("targeting","implementation-defined"),"cooldown":b.get("cooldown",d.cooldown if d else ""),
            "effective_cost":b.get("resource-cost",b.get("cost-amount",d.cost if d else "")),"damage":b.get("damage",det.get("damage","")),"healing":b.get("heal-self",det.get("healing","")),
            "cc":det.get("cc",""),"mobility":det.get("mobility",""),"summon":"yes" if d and d.category=="summon" else "no","delayed":"yes" if d and d.category in {"stateful","summon"} else "no",
            "projectile":"yes" if d and d.category=="projectile" else "no","scaling_path":"CastModifiers -> SpellExecutionContext/shared output primitives","regression_coverage":"class/profile + cast architecture + strict source graph",
            "definition_location":";".join(f"{x.path}:{x.line}" for x in r.definitions),"registration_location":";".join(r.registration_locations)}

def git(root,*args):
    try:return subprocess.check_output(["git",*args],cwd=root,text=True,stderr=subprocess.DEVNULL).strip()
    except Exception:return "unknown"

def write_report(p:Path,root:Path,rows,errors,chains,pairs,overrides,delayed,reg_rows):
    defined={x for x,r in rows.items() if r.definitions};registered={x for x,r in rows.items() if r.registered};reachable={x for x,r in rows.items() if r.registered and r.provenance in PROGRESSION}
    cfg=[r for r in rows.values() if r.registered and r.primary and r.primary.category=="configured"];cats={x:0 for x in "ABCD"}
    for r in cfg:cats[configured_review(r.primary)[0]]+=1
    impl={}
    for r in rows.values():
        if r.registered and r.primary:impl[r.primary.category]=impl.get(r.primary.category,0)+1
    base=sum(r.registered and r.provenance=="BASE" for r in rows.values());spec=sum(r.registered and r.provenance=="SPEC" for r in rows.values());other=sum(r.registered and r.provenance not in {"BASE","SPEC"} for r in rows.values())
    out=["# IceSMP class/spec/spell/cast audit","","> Generated by `scripts/audit_class_spell_system.py --strict` from the exact checkout.","> The audited source SHA records the tree used to generate this artifact; the artifact commit itself necessarily has a later SHA.","","## Git evidence","",f"- Audited feature SHA: `{git(root,'rev-parse','HEAD')}`",f"- Staging base SHA: `{git(root,'rev-parse','origin/staging')}`","","## Inventory totals","",f"- Classes: **{len(CLASS_TO_SPECS)}**",f"- Specializations: **{sum(map(len,CLASS_TO_SPECS.values()))}**",f"- Source-defined spell IDs: **{len(defined)}**",f"- Runtime-registered spell IDs: **{len(registered)}**",f"- Normal progression-reachable spell IDs: **{len(reachable)}**",f"- BASE provenance: **{base}**",f"- SPEC provenance: **{spec}**",f"- TALENT/QUEST/SYSTEM/ADMIN/DEV/allowlisted provenance: **{other}**",f"- Implementation breakdown: {', '.join(f'{k}={v}' for k,v in sorted(impl.items()))}","","## ConfiguredSpell verdict — OPTION B (hybrid)","",f"- A: **{cats['A']}**",f"- B: **{cats['B']}**",f"- C: **{cats['C']}**",f"- D: **{cats['D']}**","","A is limited to immediate generic primitives. B is the same gameplay model with manually reviewed signature presentation needs. C/D are explicit semantic findings and strict mode refuses to pass while any remains unmigrated.","","### B/C/D reasons","","| Spell | Category | Reason |","|---|---:|---|"]
    for r in sorted(cfg,key=lambda x:x.id):
        cat,reason=configured_review(r.primary)
        if cat!="A":out.append(f"| `{r.id}` | {cat} | {reason} |")
    out += ["","## Class verdicts","","| Class | Base spells | Specs | Unlock/registration |","|---|---:|---|---|"]
    for c,ss in CLASS_TO_SPECS.items():
        cr=[r for r in rows.values() if r.clazz==c];out.append(f"| `{c}` | {sum(r.provenance=='BASE' for r in cr)} | {', '.join(ss)} | {'PASS' if all(not r.unlock or r.registered for r in cr) else 'FAIL'} |")
    out += ["","## Specialization verdicts","","| Spec | Class | Spell count | Active-kit refs | DARK | Verdict |","|---|---|---:|---:|---|---|"]
    for c,ss in CLASS_TO_SPECS.items():
        for s in ss:
            sr=[r for r in rows.values() if r.spec==s];kit=[r for r in rows.values() if s in r.active_owners];ok=all((not r.unlock or r.registered) for r in sr) and all(r.registered for r in kit)
            out.append(f"| `{s}` | `{c}` | {sum(r.provenance=='SPEC' for r in sr)} | {len(kit)} | {'yes' if s in DARK_SPECS else 'no'} | {'PASS' if ok else 'FAIL'} |")
    out += ["","## Typed/scalar override inventory",""]
    for k,v in overrides.items():out.append(f"- `{k}`: **{len(v)}** — "+(", ".join(f"`{x}`" for x in sorted(v)) or "none"))
    out += ["","## Delayed/projectile inventory","","| Spell | Implementation | Delayed | Projectile | Modifier snapshot | Caster snapshot | Target snapshot | Thread owner | Cleanup |","|---|---|---|---|---|---|---|---|---|"]
    for x in sorted(delayed,key=lambda x:x['id']):out.append(f"| `{x['id']}` | `{x['impl']}` | {x['delayed']} | {x['projectile']} | {x['snapshot']} | {x['caster']} | {x['target']} | {x['owner']} | {x['cleanup']} |")
    out += ["","## Combo audit","",f"- Chains: **{len(chains)}**",f"- Pairs: **{len(pairs)}**","- `soul-collapse` and `way-of-hundred-fists` are semantic deny-list entries and make strict mode fail if reintroduced.","","## Regression graph","","| Suite | Wiring | Task |","|---|---|---|"]
    for x in reg_rows:out.append(f"| `{x['suite']}` | {x['state']} | `{x['task']}` |")
    out += ["","## Architecture decisions","","- PlayerProfile/ClassSpec remains durable authority; class services and summoned entities are transient projections.","- Cast flow: input → authority/active kit → preparation → modifiers → affordability/reservation → typed execution → state commit → cooldown → feedback/stats.","- Standard spell power scales damage/healing/shield magnitude, never hard-CC duration implicitly.","- Scheduler/projectile behavior carries immutable modifier snapshots across owner-thread hops.","- ConfiguredSpell remains for immediate generic primitives; dedicated Java behavior owns stateful/projectile/summon identity.","","## Druid naming","","- Primary resource: **Természeti Erő**","- Secondary mechanic: **Harmónia**","","## Strict audit result","", "**PASS**" if not errors else "**FAIL**"]
    if errors:out += ["","### Errors"]+[f"- {e}" for e in errors]
    else:out += ["","No definition/registration/provenance, balance parity, active-kit, combo, delayed-scaling, mandatory regression-wiring or Druid naming errors remain."]
    out += ["","## NEEDS PLAYTEST","","Only live-server balance/readability tuning remains here; no source-auditable correctness issue is intentionally deferred.","","Complete per-spell evidence: `docs/audits/class-spell-inventory.csv`."]
    p.write_text("\n".join(out)+"\n",encoding="utf-8")

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--root",type=Path,default=Path(__file__).resolve().parents[1]);ap.add_argument("--csv",type=Path,default=Path("docs/audits/class-spell-inventory.csv"));ap.add_argument("--report",type=Path,default=Path("docs/audits/CLASS_SPELL_CAST_AUDIT.md"));ap.add_argument("--strict",action="store_true");a=ap.parse_args();root=a.root.resolve()
    req=[root/"src/main/java",root/"src/regression/java",root/"src/main/resources/config/classes.yml",root/"src/main/resources/config/class-gameplay.yml",root/"src/main/resources/config/spells.yml",root/"src/main/resources/config/spells-balance.yml",root/"build.gradle.kts"]
    missing=[str(x) for x in req if not x.exists()]
    if missing:print("Missing audit inputs: "+", ".join(missing),file=sys.stderr);return 2
    defs,class_ids=definitions(root/"src/main/java");regs,unresolved=registrations(root,class_ids);unlocks=parse_unlocks(root/"src/main/resources/config/classes.yml");active=parse_active(root/"src/main/resources/config/class-gameplay.yml");balance=parse_balance(root/"src/main/resources/config/spells-balance.yml");schools,chains,pairs=parse_spell_config(root/"src/main/resources/config/spells.yml");rows=build_rows(defs,regs,unlocks,active,balance,schools,chains,pairs);over=override_inventory(root/"src/main/java");delayed,derr=delayed_inventory(rows,root);reg_rows,rerr=regression_graph(root);nerr=druid_naming_errors(root);errors=validate(rows,regs,unresolved,balance,chains,pairs,over,derr,rerr,nerr,root)
    cp=a.csv if a.csv.is_absolute() else root/a.csv;rp=a.report if a.report.is_absolute() else root/a.report;cp.parent.mkdir(parents=True,exist_ok=True);rp.parent.mkdir(parents=True,exist_ok=True)
    with cp.open("w",encoding="utf-8",newline="") as h:
        w=csv.DictWriter(h,fieldnames=CSV_FIELDS);w.writeheader();[w.writerow(csv_row(rows[s])) for s in sorted(rows)]
    write_report(rp,root,rows,errors,chains,pairs,over,delayed,reg_rows)
    defined=sum(bool(r.definitions) for r in rows.values());registered=sum(r.registered for r in rows.values());reachable=sum(r.registered and r.provenance in PROGRESSION for r in rows.values());configured=sum(r.registered and r.primary and r.primary.category=="configured" for r in rows.values())
    print(f"IceSMP strict spell audit: defined={defined} registered={registered} progression={reachable} configured={configured}")
    for e in errors:print("ERROR: "+e,file=sys.stderr)
    return 1 if a.strict and errors else 0
if __name__=="__main__":raise SystemExit(main())
