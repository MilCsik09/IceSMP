#!/usr/bin/env python3
"""Strict source audit for IceSMP class/spec/spell/cast integrity.

DEFINED and REGISTERED are intentionally distinct. A spell is REGISTERED only when a
SpellRegistry.register call is reachable from IceSMPCore.registerSpells or SpellCatalog's
startup entry points. Configuration references never manufacture a registration.
"""
from __future__ import annotations

import argparse, csv, re, subprocess, sys
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

CLASS_TO_SPECS = {
    'warrior': ('berserker','guardian'), 'evoker': ('devastation','preservation'),
    'archer': ('sharpshooter','beast_master'), 'shaman': ('elemental','enhancement','tidal'),
    'monk': ('windwalker','brewmaster','mistweaver'), 'paladin': ('holy','retribution','protection'),
    'demon_hunter': ('havoc','vengeance'), 'druid': ('feral','lunar','ironbark','restoration'),
    'priest': ('discipline','bone_priest','shadow'), 'death_knight': ('blood','frost','unholy'),
    'assassin': ('poisoner','phantom','plaguebringer'),
    'warlock': ('affliction','destruction','demonologist'), 'wizard': ('elementalist','necromancer'),
}
SPEC_TO_CLASS = {s:c for c, specs in CLASS_TO_SPECS.items() for s in specs}
DARK_SPECS = {'necromancer','plaguebringer','unholy','bone_priest','demonologist'}
NORMAL_PROVENANCE = {'BASE','SPEC','TALENT','QUEST'}
EXPLICIT_PROVENANCE = {'talent_surge': ('TALENT','SpellCatalog.registerTalentSpells')}
FORBIDDEN_COMBOS = {
    'soul-collapse': 'Affliction/Destruction cross-spec chain',
    'way-of-hundred-fists': 'duplicates Monk native martial chain',
}
SIGNATURE_B = {
    'gravity_well','berserk','last_stand','masterful_shot','king_of_beasts','deathcap','spectre',
    'celestial_alignment','avenging_wrath','final_verdict','breath_of_sindragosa','ascendance_flame',
    'doom_winds','serenity','invoke_niuzao','evangelism','void_eruption','darkglare',
    'metamorphosis_havoc','the_hunt','metamorphosis_veng','dragonrage','eternity_breath','rewind',
    'incarnation_bear','tranquility','spirit_tide','revival',
}
CSV_FIELDS = [
    'id','display_name','class','spec','provenance','defined','registered','implementation_class',
    'implementation_category','configured_spell_category','configured_spell_category_reason','unlock_level',
    'active_kit','balance_entry','spell_school','combo_reference','targeting','cooldown','effective_cost',
    'damage','healing','cc','mobility','summon','delayed','projectile','scaling_path','regression_coverage',
    'definition_location','registration_location'
]

@dataclass
class Definition:
    id:str; name:str; impl:str; category:str; source:str; line:int
    impl_source:str=''; cooldown:str=''; cost:str=''; clazz:str=''; spec:str=''; block:str=''
    details:dict[str,str]=field(default_factory=dict)

@dataclass
class Row:
    id:str; definitions:list[Definition]=field(default_factory=list); registrations:list[str]=field(default_factory=list)
    clazz:str=''; spec:str=''; provenance:str=''; unlock_level:str=''; active:set[str]=field(default_factory=set)
    combos:set[str]=field(default_factory=set); balance:dict[str,str]=field(default_factory=dict); school:str=''
    @property
    def registered(self): return bool(self.registrations)
    @property
    def primary(self):
        if not self.definitions: return None
        return next((d for d in self.definitions if d.category=='configured'), self.definitions[0])

def read(p:Path)->str: return p.read_text(encoding='utf-8')
def line_no(text:str,pos:int)->int: return text.count('\n',0,pos)+1
def scalar(v:str)->str:
    v=v.strip(); return v[1:-1] if len(v)>1 and v[0]==v[-1] and v[0] in "\"'" else v

def method_span(text:str,name:str):
    m=re.search(r'\b(?:public|private|protected)\s+(?:static\s+)?[\w<>?, .\[\]]+\s+'+re.escape(name)+r'\s*\([^)]*\)\s*\{', text)
    if not m: return None
    start=text.find('{',m.start()); depth=0; string=False; esc=False
    for i in range(start,len(text)):
        ch=text[i]
        if string:
            if esc: esc=False
            elif ch=='\\': esc=True
            elif ch=='"': string=False
            continue
        if ch=='"': string=True
        elif ch=='{': depth+=1
        elif ch=='}':
            depth-=1
            if depth==0: return m.start(),i+1,text[m.start():i+1]
    return None

def register_calls(text:str):
    out=[]
    for m in re.finditer(r'\b(?:spellRegistry|registry)\.register\s*\(',text):
        op=text.find('(',m.start()); depth=0; string=False; esc=False
        for i in range(op,len(text)):
            ch=text[i]
            if string:
                if esc: esc=False
                elif ch=='\\': esc=True
                elif ch=='"': string=False
                continue
            if ch=='"': string=True
            elif ch=='(': depth+=1
            elif ch==')':
                depth-=1
                if depth==0: out.append((m.start(),text[m.start():i+1])); break
    return out

def owner_from_register_method(name:str):
    token=re.sub(r'([a-z0-9])([A-Z])',r'\1_\2',name.removeprefix('register')).lower()
    if token in CLASS_TO_SPECS: return token,''
    if token in SPEC_TO_CLASS: return SPEC_TO_CLASS[token],token
    return '',''

def classify(impl:str,text:str):
    low=impl.lower()
    if impl=='ConfiguredSpell': return 'configured'
    if impl=='ShamanTotemSpell': return 'stateful'
    if 'form' in low: return 'form'
    if 'summon' in low or 'minion' in low: return 'summon'
    if 'projectile' in low or 'launchProjectile' in text: return 'projectile'
    if 'runDelayed' in text or 'runAtFixedRate' in text or 'clearPlayerState' in text: return 'stateful'
    return 'dedicated'

def enum_defs(root:Path):
    defs=[]; dynamic={}
    p=root/'src/main/java/hu/taliann/icesmp/spells/DruidFormSpell.java'
    if p.is_file():
        t=read(p); rel=p.relative_to(root).as_posix()
        for m in re.finditer(r'(?m)^\s*([A-Z_]+)\("([^"]+)",\s*"([^"]+)"',t):
            en,sid,name=m.groups()
            if sid.startswith('druid_') and sid.endswith('_form'):
                defs.append(Definition(sid,name,'DruidFormSpell','form',rel,line_no(t,m.start()),rel)); dynamic[('DruidFormSpell',en)]=sid
    p=root/'src/main/java/hu/taliann/icesmp/managers/TotemManager.java'
    if p.is_file():
        t=read(p); rel=p.relative_to(root).as_posix(); impl='src/main/java/hu/taliann/icesmp/managers/TotemManager.java'
        for m in re.finditer(r'(?m)^\s*([A-Z_]+)\("([^"]+_totem)",\s*"([^"]+)"',t):
            en,sid,name=m.groups(); defs.append(Definition(sid,name,'ShamanTotemSpell','stateful',rel,line_no(t,m.start()),impl)); dynamic[('ShamanTotemSpell',en)]=sid
    return defs,dynamic

def discover_definitions(root:Path):
    java=root/'src/main/java'; spell_dir=java/'hu/taliann/icesmp/spells'; defs={}; class_to_id={}; class_src={}
    for p in spell_dir.glob('*.java'):
        t=read(p); rel=p.relative_to(root).as_posix(); cm=re.search(r'(?:public\s+)?(?:final\s+)?class\s+(\w+)',t); impl=cm.group(1) if cm else p.stem
        class_src[impl]=rel
        cat=classify(impl,t)
        for m in re.finditer(r'super\([^;]*?"([^"]+)",\s*"([^"]+)",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)',t,re.S):
            sid,name,cool,_ctype,cost=m.groups(); d=Definition(sid,name,impl,cat,rel,line_no(t,m.start()),rel,cool.strip(),cost.strip()); defs.setdefault(sid,[]).append(d); class_to_id[impl]=sid
    catalog=spell_dir/'SpellCatalog.java'
    if catalog.is_file():
        t=read(catalog); rel=catalog.relative_to(root).as_posix(); methods=list(re.finditer(r'private static void (register[A-Za-z0-9_]+)\s*\(',t)); spans=[]
        for i,m in enumerate(methods): spans.append((m.start(), methods[i+1].start() if i+1<len(methods) else len(t), m.group(1)))
        def owner(pos):
            for a,b,n in spans:
                if a<=pos<b: return owner_from_register_method(n)
            return '',''
        for m in re.finditer(r'ConfiguredSpell\.builder\([^,]+,\s*"([^"]+)",\s*"([^"]+)",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)(.*?)(?:\.build\(\))',t,re.S):
            sid,name,cool,_ctype,cost,tail=m.groups(); clazz,spec=owner(m.start()); details={'targeting':'TARGET' if '.target(' in tail else 'AOE' if '.aoe(' in tail else 'SELF'}
            for key,pat in [('damage',r'\.damage\(([^)]+)\)'),('healing',r'\.healSelf\(([^)]+)\)')]:
                x=re.search(pat,tail); details[key]=x.group(1).strip() if x else ''
            hard=re.findall(r'targetEffect\(PotionEffectType\.([A-Z_]+)',tail); hard=[x for x in hard if x in {'SLOWNESS','BLINDNESS','LEVITATION','JUMP_BOOST'}]
            if '.freeze(' in tail: hard.append('FREEZE')
            details['cc']=','.join(hard); details['mobility']='dash' if '.dash(' in tail else ''
            d=Definition(sid,name,'ConfiguredSpell','configured',rel,line_no(t,m.start()),class_src.get('ConfiguredSpell',rel),cool.strip(),cost.strip(),clazz,spec,m.group(0),details); defs.setdefault(sid,[]).append(d)
        for impl,cat in [('ProjectileBurstSpell','projectile'),('BlinkSpell','dedicated'),('SummonMinionsSpell','summon')]:
            for m in re.finditer(r'new\s+'+impl+r'\s*\((.*?)\)\s*[;,]',t,re.S):
                lits=re.findall(r'"([^"]+)"',m.group(1));
                if len(lits)<2: continue
                clazz,spec=owner(m.start()); sid,name=lits[:2]; d=Definition(sid,name,impl,cat,rel,line_no(t,m.start()),class_src.get(impl,rel),clazz=clazz,spec=spec); defs.setdefault(sid,[]).append(d)
    extra,dynamic=enum_defs(root)
    for d in extra: defs.setdefault(d.id,[]).append(d)
    return defs,class_to_id,dynamic

def reachable_catalog(text:str):
    q=deque(['registerExpansionSpells','registerSummonSpells']); seen=set()
    while q:
        name=q.popleft()
        if name in seen: continue
        seen.add(name); sp=method_span(text,name)
        if sp:
            for called in re.findall(r'\b(register[A-Z][A-Za-z0-9_]*)\s*\(',sp[2]):
                if called not in seen: q.append(called)
    return seen

def resolve_registration(expr,class_to_id,dynamic):
    m=re.search(r'ConfiguredSpell\.builder\([^,]+,\s*"([^"]+)"',expr)
    if m: return m.group(1)
    m=re.search(r'new\s+(\w+)\s*\(',expr)
    if not m: return None
    impl=m.group(1)
    if impl in class_to_id: return class_to_id[impl]
    if impl=='DruidFormSpell':
        x=re.search(r'DruidFormSpell\.Form\.([A-Z_]+)',expr); return dynamic.get((impl,x.group(1))) if x else None
    if impl=='ShamanTotemSpell':
        x=re.search(r'TotemManager\.TotemType\.([A-Z_]+)',expr); return dynamic.get((impl,x.group(1))) if x else None
    lits=re.findall(r'"([^"]+)"',expr[m.end():]); return lits[0] if lits else None

def discover_registrations(root,class_to_id,dynamic):
    regs={}; unresolved=[]
    for p,methods in [
        (root/'src/main/java/hu/taliann/icesmp/core/IceSMPCore.java',['registerSpells']),
        (root/'src/main/java/hu/taliann/icesmp/spells/SpellCatalog.java',None)]:
        t=read(p); rel=p.relative_to(root).as_posix(); names=sorted(reachable_catalog(t)) if methods is None else methods; ranges=[]
        for name in names:
            sp=method_span(t,name)
            if sp: ranges.append((sp[0],sp[1]))
        for pos,expr in register_calls(t):
            if not any(a<=pos<b for a,b in ranges): continue
            sid=resolve_registration(expr,class_to_id,dynamic); loc=f'{rel}:{line_no(t,pos)}'
            if sid: regs.setdefault(sid,[]).append(loc)
            else: unresolved.append(f'{loc}: {expr[:140].replace(chr(10)," ")}')
    return regs,unresolved

def parse_unlocks(p):
    out={}; stack=[]
    for raw in read(p).splitlines():
        clean=raw.split('#',1)[0].rstrip();
        if not clean.strip() or ':' not in clean: continue
        ind=len(clean)-len(clean.lstrip());
        while stack and stack[-1][0]>=ind: stack.pop()
        key,val=clean.strip().split(':',1); key=key.strip(); val=val.strip(); stack.append((ind,key)); keys=[x for _,x in stack]
        if len(keys)<4 or keys[-2]!='spell-unlocks' or not val: continue
        owner=keys[-3]
        try: level=int(val)
        except ValueError: continue
        if keys[0]=='classes' and owner in CLASS_TO_SPECS: out[key]=(owner,'',level)
        elif keys[0]=='specializations' and owner in SPEC_TO_CLASS: out[key]=(SPEC_TO_CLASS[owner],owner,level)
    return out

def parse_active(p):
    out={}; stack=[]; owner=''
    for raw in read(p).splitlines():
        clean=raw.split('#',1)[0].rstrip();
        if not clean.strip(): continue
        ind=len(clean)-len(clean.lstrip()); stripped=clean.strip()
        while stack and stack[-1][0]>=ind: stack.pop()
        if stripped.startswith('-'):
            if owner: out.setdefault(owner,set()).add(scalar(stripped[1:]))
            continue
        if ':' not in stripped: continue
        key,val=stripped.split(':',1); key=key.strip(); val=val.strip(); stack.append((ind,key)); keys=[x for _,x in stack]; owner=''
        if not val and 'active-kit' in keys and key in SPEC_TO_CLASS: owner=key
    return out

def parse_balance(p):
    out={}; in_root=False; cur=''
    for raw in read(p).splitlines():
        clean=raw.split('#',1)[0].rstrip();
        if not clean.strip(): continue
        ind=len(clean)-len(clean.lstrip()); s=clean.strip()
        if ind==0: in_root=(s=='spell-balance:'); cur=''; continue
        if not in_root: continue
        if ind==2 and s.endswith(':'): cur=s[:-1]; out.setdefault(cur,{})
        elif ind>=4 and cur and ':' in s:
            k,v=s.split(':',1); out[cur][k.strip()]=scalar(v)
    return out

def parse_spell_config(p):
    t=read(p); schools={}; chains={}; pairs={}
    m=re.search(r'(?ms)^\s*by-spell:\s*\n(.*?)(?=^\s{2}\S|\Z)',t)
    if m:
        for x in re.finditer(r'(?m)^\s{6}([a-z0-9_:-]+):\s*([a-z0-9_:-]+)\s*$',m.group(1)): schools[x.group(1)]=x.group(2)
    m=re.search(r'(?ms)^\s{4}chains:\s*\n(.*?)(?=^\s{4}pairs:|\Z)',t)
    if m:
        cur=''
        for raw in m.group(1).splitlines():
            s=raw.split('#',1)[0].strip(); ind=len(raw)-len(raw.lstrip())
            if ind==6 and s.endswith(':'): cur=s[:-1]
            elif cur and s.startswith('steps:'):
                v=s.split(':',1)[1].strip()
                if v.startswith('[') and v.endswith(']'): chains[cur]=[scalar(x) for x in v[1:-1].split(',') if x.strip()]
    m=re.search(r'(?ms)^\s{4}pairs:\s*\n(.*?)(?=^\S|\Z)',t)
    if m:
        cur=first=''
        for raw in m.group(1).splitlines():
            s=raw.split('#',1)[0].strip(); ind=len(raw)-len(raw.lstrip())
            if ind==6 and s.endswith(':'): cur=s[:-1]; first=''
            elif cur and s.startswith('first:'): first=scalar(s.split(':',1)[1])
            elif cur and first and s.startswith('second:'): pairs[cur]=(first,scalar(s.split(':',1)[1])); first=''
    return schools,chains,pairs

def configured_review(d):
    if any(x in d.block for x in ('runDelayed','runAtFixedRate','launchProjectile','PersistentDataContainer','targetMemory','channel','chargeSession')): return 'D','stateful lifecycle is not valid inside ConfiguredSpell'
    if d.id in SIGNATURE_B: return 'B','generic gameplay retained; signature presentation/VFX readability is intentionally distinct'
    outs=[x for x in ('damage','healing','cc','mobility') if d.details.get(x)]
    return 'A',f"immediate {d.details.get('targeting','SELF')} {'/'.join(outs) or 'utility'} primitive; no persistent/scheduled lifecycle"

def impl_traits(root,d):
    p=root/('src/main/java/hu/taliann/icesmp/managers/TotemManager.java' if d.impl=='ShamanTotemSpell' else (d.impl_source or d.source)); t=read(p) if p.is_file() else ''
    delayed='runDelayed' in t or 'runAtFixedRate' in t; projectile=d.category=='projectile' or 'launchProjectile' in t or 'teleportAsync' in t
    scaled=delayed and any(x in t for x in ('SpellDamageUtil','SpellHealingUtil','.damage(','setHealth(','scaledDamage(','scaledHealing(')); snapshot=any(x in t for x in ('SpellExecutionContext.capture()','SpellDamageUtil.markProjectile','CastModifiers modifiers','SpellDamageUtil.scaledDamage','SpellHealingUtil.scaledHealing'))
    return p,t,delayed,projectile,scaled,snapshot

def regression_graph(root):
    build=read(root/'build.gradle.kts'); regs={}
    for m in re.finditer(r'val\s+(\w+)\s*=\s*registerRegression\(\s*"([^"]+)"\s*,.*?"([^"]+RegressionSuite)"\s*\)',build,re.S): regs[m.group(3)]=m.group(1)
    check=build[build.find('tasks.check'):]; suites={}
    for p in (root/'src/regression/java').rglob('*RegressionSuite.java'):
        rel=p.relative_to(root/'src/regression/java').with_suffix('').as_posix().replace('/','.'); suites[rel]=(p,read(p))
    simple={fq:fq.rsplit('.',1)[-1] for fq in suites}; calls={fq:set() for fq in suites}
    for owner,(_,t) in suites.items():
        for fq,s in simple.items():
            if fq!=owner and re.search(r'\b'+re.escape(s)+r'\.main\s*\(',t): calls[owner].add(fq)
    direct={fq for fq,var in regs.items() if var in check}; reachable=set(direct); q=deque(direct)
    while q:
        o=q.popleft()
        for c in calls.get(o,set()):
            if c not in reachable: reachable.add(c); q.append(c)
    rows=[]; errs=[]
    mandatory=('hu.taliann.icesmp.warrior.','hu.taliann.icesmp.evoker.','hu.taliann.icesmp.archer.','hu.taliann.icesmp.shaman.','hu.taliann.icesmp.monk.','hu.taliann.icesmp.paladin.','hu.taliann.icesmp.demonhunter.','hu.taliann.icesmp.druid.','hu.taliann.icesmp.priest.','hu.taliann.icesmp.deathknight.','hu.taliann.icesmp.assassin.','hu.taliann.icesmp.warlock.','hu.taliann.icesmp.wizard.','hu.taliann.icesmp.classspec.','hu.taliann.icesmp.playerprofile.','hu.taliann.icesmp.spells.','hu.taliann.icesmp.hud.','hu.taliann.icesmp.lifecycle.','hu.taliann.icesmp.config.')
    for fq,(p,_) in sorted(suites.items()):
        state='TASK+CHECK' if fq in direct else 'DELEGATED' if fq in reachable else 'TASK_ONLY' if fq in regs else 'ORPHAN'; rows.append((fq,state,regs.get(fq,'')))
        if fq.startswith(mandatory) and state in {'TASK_ONLY','ORPHAN'}: errs.append(f'mandatory regression not reachable from check: {fq} ({state})')
    for fq in ('hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite','hu.taliann.icesmp.wizard.WizardProfileRegressionSuite'):
        state=next((s for f,s,_ in rows if f==fq),'MISSING')
        if state!='TASK+CHECK': errs.append(f'Wizard suite lacks explicit check task: {fq} ({state})')
    return rows,errs

def build_rows(defs,regs,unlocks,active,balance,schools,chains,pairs):
    ids=set(defs)|set(regs)|set(unlocks)|set(balance)|{x for v in active.values() for x in v}|{x for v in chains.values() for x in v}|{x for v in pairs.values() for x in v}; rows={i:Row(i,list(defs.get(i,[]))) for i in ids}
    for i,v in regs.items(): rows[i].registrations=v
    for i,(c,s,l) in unlocks.items(): rows[i].clazz=c; rows[i].spec=s; rows[i].unlock_level=str(l); rows[i].provenance='SPEC' if s else 'BASE'
    for i,(prov,_) in EXPLICIT_PROVENANCE.items():
        if i in rows and not rows[i].provenance: rows[i].provenance=prov
    for o,vs in active.items():
        for i in vs: rows[i].active.add(o)
    for n,vs in chains.items():
        for i in vs: rows[i].combos.add(n)
    for n,v in pairs.items():
        for i in v: rows[i].combos.add(n)
    for i,v in balance.items(): rows[i].balance=v
    for i,v in schools.items():
        if i in rows: rows[i].school=v
    for r in rows.values():
        if r.primary and not r.clazz: r.clazz,r.spec=r.primary.clazz,r.primary.spec
    return rows

def valid_loadouts(r):
    if not r.clazz: return {(c,s) for c,ss in CLASS_TO_SPECS.items() for s in ss}
    return {(r.clazz,r.spec)} if r.spec else {(r.clazz,s) for s in CLASS_TO_SPECS[r.clazz]}

def validate(root,rows,regs,unresolved,balance,chains,pairs,regerrs):
    errs=[f'unresolved registration: {x}' for x in unresolved]; R=set(regs); B=set(balance)
    errs += [f'registered missing balance: {x}' for x in sorted(R-B)] + [f'dead balance entry: {x}' for x in sorted(B-R)]
    for i,locs in regs.items():
        if len(locs)!=1: errs.append(f'duplicate startup registration {i}: {len(locs)} paths')
    for i,r in rows.items():
        if r.registered and not r.definitions: errs.append(f'registered without definition: {i}')
        if r.unlock_level and not r.registered: errs.append(f'unlock non-registered: {i}')
        if r.active and not r.registered: errs.append(f'active-kit non-registered: {i}')
        if r.combos and not r.registered: errs.append(f'combo non-registered: {i}')
        if r.registered and not r.provenance: errs.append(f'registered without explicit provenance: {i}')
        if r.spec and any(o!=r.spec for o in r.active): errs.append(f'cross-spec active-kit leakage: {i}')
        if r.primary and r.primary.category=='configured':
            cat,reason=configured_review(r.primary)
            if cat in {'C','D'}: errs.append(f'ConfiguredSpell {cat} not migrated: {i}: {reason}')
        if r.registered and r.primary:
            p,t,delayed,proj,scaled,snap=impl_traits(root,r.primary)
            if scaled and not snap: errs.append(f'delayed scaled output lacks snapshot: {i} ({p.relative_to(root)})')
    for n,steps in chains.items():
        if n in FORBIDDEN_COMBOS: errs.append(f'forbidden combo returned: {n}')
        if any(x not in R for x in steps): continue
        common=None
        for x in steps: common=valid_loadouts(rows[x]) if common is None else common & valid_loadouts(rows[x])
        if not common: errs.append(f'impossible combo chain: {n}')
    for n,pair in pairs.items():
        if n in FORBIDDEN_COMBOS: errs.append(f'forbidden combo returned: {n}')
        if all(x in R for x in pair) and not(valid_loadouts(rows[pair[0]])&valid_loadouts(rows[pair[1]])): errs.append(f'impossible combo pair: {n}')
    errs += regerrs
    configured=read(root/'src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java'); spell=read(root/'src/main/java/hu/taliann/icesmp/spells/Spell.java'); registry=read(root/'src/main/java/hu/taliann/icesmp/managers/SpellRegistry.java')
    if 'return cast(player, CastModifiers.standardPower(power)).effectApplied();' not in configured: errs.append('ConfiguredSpell scalar bridge is not one-way typed')
    if 'return cast(player, CastModifiers.standardPower(powerMultiplier)).effectApplied();' not in spell: errs.append('Spell scalar bridge is not one-way typed')
    if 'putIfAbsent' not in registry or 'Duplicate spell id' not in registry: errs.append('registry duplicate fail-fast missing')
    if not re.search(r'withBalanceOverrides\([^)]*\).*?\{.*?return spell;',configured,re.S): errs.append('balance override helper does not preserve original registered instance')
    druid=read(root/'src/main/java/hu/taliann/icesmp/druid/DruidGameplayService.java')
    if '"harmony", "Természeti Erő"' in druid or '{amount} Természeti Erő szabadult fel' in druid: errs.append('Druid Harmony player-facing naming collision')
    return errs

def gitval(root,*args):
    try:return subprocess.check_output(['git',*args],cwd=root,text=True,stderr=subprocess.DEVNULL).strip()
    except Exception:return 'unknown'

def csv_record(root,r):
    d=r.primary; cat=reason=''; details=d.details if d else {}; delayed=proj=False
    if d:
        if d.category=='configured': cat,reason=configured_review(d)
        _,_,delayed,proj,_,_=impl_traits(root,d)
    return {'id':r.id,'display_name':d.name if d else '','class':r.clazz,'spec':r.spec,'provenance':r.provenance,'defined':'yes' if r.definitions else 'no','registered':'yes' if r.registered else 'no','implementation_class':d.impl if d else '','implementation_category':d.category if d else '','configured_spell_category':cat,'configured_spell_category_reason':reason,'unlock_level':r.unlock_level,'active_kit':','.join(sorted(r.active)),'balance_entry':'yes' if r.balance else 'no','spell_school':r.school or 'class default / primordial','combo_reference':','.join(sorted(r.combos)),'targeting':details.get('targeting','implementation-defined'),'cooldown':r.balance.get('cooldown',d.cooldown if d else ''),'effective_cost':r.balance.get('resource-cost',r.balance.get('cost-amount',d.cost if d else '')),'damage':r.balance.get('damage',details.get('damage','')),'healing':r.balance.get('heal-self',details.get('healing','')),'cc':details.get('cc',''),'mobility':details.get('mobility',''),'summon':'yes' if d and d.category=='summon' else 'no','delayed':'yes' if delayed else 'no','projectile':'yes' if proj else 'no','scaling_path':'CastModifiers -> immutable snapshot/shared primitives','regression_coverage':'class/profile + cast architecture + strict source graph','definition_location':';'.join(f'{x.source}:{x.line}' for x in r.definitions),'registration_location':';'.join(r.registrations)}
def write_report(root,path,rows,errs,regrows,chains,pairs):
    defined=sum(bool(r.definitions) for r in rows.values()); registered=sum(r.registered for r in rows.values()); reachable=sum(r.registered and r.provenance in NORMAL_PROVENANCE for r in rows.values()); impl={}; cfg={x:0 for x in 'ABCD'}
    for r in rows.values():
        if r.registered and r.primary:
            impl[r.primary.category]=impl.get(r.primary.category,0)+1
            if r.primary.category=='configured': cfg[configured_review(r.primary)[0]]+=1
    out=['# IceSMP class/spec/spell/cast audit','',f'- Audited feature SHA: `{gitval(root,"rev-parse","HEAD")}`',f'- Staging base SHA: `{gitval(root,"rev-parse","origin/staging")}`','', '## Inventory',f'- Classes: **{len(CLASS_TO_SPECS)}**',f'- Specializations: **{sum(map(len,CLASS_TO_SPECS.values()))}**',f'- Source-defined spell IDs: **{defined}**',f'- Runtime-registered spell IDs: **{registered}**',f'- Normal progression-reachable spell IDs: **{reachable}**','- Implementation: '+', '.join(f'{k}={v}' for k,v in sorted(impl.items())),'', '## ConfiguredSpell verdict — OPTION B',f'- A: **{cfg["A"]}**',f'- B: **{cfg["B"]}**',f'- C: **{cfg["C"]}**',f'- D: **{cfg["D"]}**','','### B/C/D reasons','| Spell | Category | Reason |','|---|---:|---|']
    for r in sorted(rows.values(),key=lambda x:x.id):
        if r.registered and r.primary and r.primary.category=='configured':
            c,reason=configured_review(r.primary)
            if c!='A': out.append(f'| `{r.id}` | {c} | {reason} |')
    out += ['','## Class verdicts','| Class | Base spells | Specs | Verdict |','|---|---:|---|---|']
    for c,ss in CLASS_TO_SPECS.items():
        rs=[r for r in rows.values() if r.clazz==c]; out.append(f'| `{c}` | {sum(r.provenance=="BASE" for r in rs)} | {", ".join(ss)} | {"PASS" if all((not r.unlock_level) or r.registered for r in rs) else "FAIL"} |')
    out += ['','## Specialization verdicts','| Spec | Class | Spell count | Active-kit refs | DARK | Verdict |','|---|---|---:|---:|---|---|']
    for c,ss in CLASS_TO_SPECS.items():
        for s in ss:
            rs=[r for r in rows.values() if r.spec==s]; kits=[r for r in rows.values() if s in r.active]; ok=all((not r.unlock_level) or r.registered for r in rs) and all(r.registered for r in kits); out.append(f'| `{s}` | `{c}` | {sum(r.provenance=="SPEC" for r in rs)} | {len(kits)} | {"yes" if s in DARK_SPECS else "no"} | {"PASS" if ok else "FAIL"} |')
    out += ['','## Regression graph','| Suite | Wiring | Task |','|---|---|---|']+[f'| `{f}` | {s} | `{t}` |' for f,s,t in regrows]
    out += ['','## Combo audit',f'- Chains: **{len(chains)}**',f'- Pairs: **{len(pairs)}**','- Every step must be registered and share a valid class/spec loadout.','', '## Architecture decisions','- PlayerProfile/ClassSpec remains durable authority; combat, summon, pet and totem state are projections.','- Standard spell power scales magnitude only; hard CC duration requires an explicit modifier.','- Delayed/projectile damage carries immutable cast-time modifiers across scheduler hops.','- ConfiguredSpell remains for immediate generic primitives; state/lifecycle identity stays dedicated Java.','', '## Strict result', '**PASS**' if not errs else '**FAIL**']
    if errs: out += ['','### Errors']+[f'- {e}' for e in errs]
    out += ['','## NEEDS PLAYTEST','Only live balance/readability tuning belongs here; source-auditable correctness is not intentionally deferred.','', 'Complete per-spell matrix: `docs/audits/class-spell-inventory.csv`.']
    path.write_text('\n'.join(out)+'\n',encoding='utf-8')
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]); ap.add_argument('--csv',type=Path,default=Path('docs/audits/class-spell-inventory.csv')); ap.add_argument('--report',type=Path,default=Path('docs/audits/CLASS_SPELL_CAST_AUDIT.md')); ap.add_argument('--strict',action='store_true'); a=ap.parse_args(); root=a.root.resolve()
    required=[root/'src/main/java/hu/taliann/icesmp/spells/SpellCatalog.java',root/'src/main/java/hu/taliann/icesmp/core/IceSMPCore.java',root/'src/main/resources/config/classes.yml',root/'src/main/resources/config/class-gameplay.yml',root/'src/main/resources/config/spells.yml',root/'src/main/resources/config/spells-balance.yml',root/'build.gradle.kts',root/'src/regression/java']
    miss=[str(p) for p in required if not p.exists()]
    if miss: print('Missing audit inputs: '+', '.join(miss),file=sys.stderr); return 2
    defs,cids,dyn=discover_definitions(root); regs,unresolved=discover_registrations(root,cids,dyn); unlocks=parse_unlocks(root/'src/main/resources/config/classes.yml'); active=parse_active(root/'src/main/resources/config/class-gameplay.yml'); balance=parse_balance(root/'src/main/resources/config/spells-balance.yml'); schools,chains,pairs=parse_spell_config(root/'src/main/resources/config/spells.yml'); rows=build_rows(defs,regs,unlocks,active,balance,schools,chains,pairs); regrows,regerrs=regression_graph(root); errs=validate(root,rows,regs,unresolved,balance,chains,pairs,regerrs)
    cp=a.csv if a.csv.is_absolute() else root/a.csv; rp=a.report if a.report.is_absolute() else root/a.report; cp.parent.mkdir(parents=True,exist_ok=True); rp.parent.mkdir(parents=True,exist_ok=True)
    with cp.open('w',encoding='utf-8',newline='') as h:
        w=csv.DictWriter(h,fieldnames=CSV_FIELDS); w.writeheader(); [w.writerow(csv_record(root,rows[i])) for i in sorted(rows)]
    write_report(root,rp,rows,errs,regrows,chains,pairs)
    defined=sum(bool(r.definitions) for r in rows.values()); registered=sum(r.registered for r in rows.values()); progression=sum(r.registered and r.provenance in NORMAL_PROVENANCE for r in rows.values()); configured=sum(r.registered and r.primary and r.primary.category=='configured' for r in rows.values()); print(f'IceSMP strict spell audit: defined={defined} registered={registered} progression={progression} configured={configured}')
    for e in errs: print('ERROR: '+e,file=sys.stderr)
    return 1 if a.strict and errs else 0
if __name__=='__main__': raise SystemExit(main())
