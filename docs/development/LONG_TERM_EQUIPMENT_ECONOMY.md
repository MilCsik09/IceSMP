# Long-Term Equipment & Economy Authoring Contract

Ez a dokumentum a #127–#130 foundation fölötti tartalombővítés szerződése. Nem új Itemization-, Equipment-, Profession-, loot- vagy market-framework: az `ItemTemplate`, `ItemInstance`, `ItemIdentity`, active-equipment authority, `ArmorFamily`, `ProfessionRecipeCatalog`, `BuildAwareLootService` és a PlayerProfile authority marad mérvadó.

## North star

Egy 500 órás játékos ugyanazokkal a világ- és gazdasági rendszerekkel marad kapcsolatban, mint 20 óránál, de más célokért: alternatív loadout, minőségoptimalizálás, resistance/utility niche, set interaction, Masterwork, kiválasztott Ascension target és player-market supply. A késői content elsősorban sidegrade és buildválasztás; nem `+20% mindenből` treadmill.

## Material-role vocabulary

Új material csak akkor indokolt, ha valódi gazdasági funkciót tölt be. A preferred vocabulary szerepalapú: bulk/heavy/light/conductive metal; crystal/gem; magical fiber; tannin; binding resin; treated/aquatic hide; scale/shell; tendon/cordage; chitin; arcane/aquatic/wild essence; organic binder; catalyst. A fantasy név ezt a funkciót nevezi el, nem egy content patch sorszámát.

A raw → final component processing útvonal normál esetben legfeljebb két meaningful stage. A gathering profession resource/processing supplier; a final armor crafting továbbra is Armorer/Alchemist/Enchanter tulajdon. Új primary crafting profession, Tailor/Leatherworker/Hunter taxonómia nem vezethető be e contract alatt.

### Material consumer contract

- Normál managed material: legalább két meaningful consumer vagy sink.
- Rare/endgame material: legalább egy high-value consumer és egy alternate/secondary sink intent.
- High-value material nem kaphat korlátlan NPC baseline faucetet.
- Patch-specifikus dust/token csak azért, hogy egy új bossnak saját valutája legyen: tilos.
- Feldolgozási vagy salvage round-trip csak nettó veszteséges lehet.
- A `source-types`, `primary-profession`, `sink-types` metadata player-facing hint és audit input; nem tartható fenn mellette shadow encyclopedia.

## Gear-line schema

Az első production catalog 40 koherens, négy darabos line: 10/family. A line kötelező authored identitása:

- family és négy slot;
- gameplay archetype/niche;
- progression band;
- acquisition identity;
- profession owner, ha crafted;
- material/economy integration;
- source/rank tags;
- vizuális theme handoff;
- set/Signature/Ascension státusz, ha releváns.

A production baseline pontosan 160 armor: 40 CLOTH, 40 LEATHER, 40 MAIL, 40 PLATE; familyként 10 head/chest/legs/feet. Az első split: 64 crafted, 48 wilderness/rank, 32 boss/mechanical-set, 16 prestige. A crafted ownership: Armorer 24, Enchanter 24, Alchemist 16.

## Family identity

- **CLOTH:** Arcane offense, Ritual resource/utility, Veil survival/mobility, Sanctified sustain/support.
- **LEATHER:** Predator offense/crit, Shadow mobility/evasion, Wildheart sustain/resource, Demonhide resistance/aggressive hybrid.
- **MAIL:** Hunter physical/ranged, Warden resistance/defense, Tempest elemental offense, Runic ability/utility.
- **PLATE:** Bulwark mitigation, Crusader defense/support, Dread bruiser, Runeforged magic resistance/utility.

A family budget profile authority nem változik. PLATE nem lehet automatikusan minden dimenzióban jobb; az endgame line-oknál source-math dominancia `BALANCE_REQUIRED`, nem automatikus stat-emelés.

## Crafted vs dropped

Crafted gear előnye a targetálhatóság, deterministic recipe, Masterwork, profession quality-control és market supply. Dropped gear előnye az encounter identity, ritkább stat kombináció, set/Signature/special visual niche. Egyik sem általános superior tier a másikhoz képest.

Mechanical setből az első katalógusban pontosan nyolc 4-piece armor set van. A set build enabler; nyers, kötelező `+25% damage` jellegű stat-tax nem megengedett. Signature ritka marad. Ascension csak kiválasztott high-value/endgame target, nem 160-item checklist. Masterwork crafted identity, nem rarity.

## PvE reward contract

A rank nem loot quantity multiplier. NORMAL → VETERAN → ELITE → CHAMPION világ-rankok növekvő, egymást tartalmazó eligibility poolt, kis bounded additív gear-esélyt, blueprint weightet és esetenként reusable special-material eligibilityt kapnak. BOSS külön boss/set source pool és a meglévő boss-component authority. BuildAware personalization cap továbbra is legfeljebb 1.5× és csak ACTIVE canonical equipment formálhatja.

A `mob-loot-profiles` csak referencianév-registry. `sources` vagy `rewards` blokk ott runtime-halott duplicate truth lenne, ezért a production parser fail-closed elutasítja. A reward truth az existing `loot.*` + canonical ItemTemplate source tags.

## Performance contract

Mob-death, equipment tick vagy GUI hot path nem szkennelheti a teljes production katalógust. A `ItemTemplateCatalogIndex` a canonical registry immutable snapshotjához épít source/family/slot/profession secondary indexet, és csak registry snapshot-cserénél rebuildel.

## Future armor authoring gate

Új armor line csak akkor adható hozzá, ha egyszerre igaz:

1. van külön gameplay niche vagy encounter preference;
2. van acquisition identity;
3. van material/economy integration;
4. nem invalidálja automatikusan a régi gear-t;
5. betartja a family normalized budgetet;
6. nem tesz szükségessé új frameworköt vagy disposable currencyt;
7. az RP handoff koherens line-ként kezeli, nem négy random assetként.

Új material csak új economic function és meaningful consumer mellett adható hozzá. Új boss/event e contract szerint meglévő reusable material, boss component, blueprint, set/Signature vagy selected Ascension sink kombinációjával bővíthető.

## Machine-readable gates

A cumulative workflow generálja/ellenőrzi:

- `material-economy.json` — processing topology, dangerous cycle, consumer contract;
- `equipment-catalog.json` — exact counts, slot/family/source/profession, normalized budget és horizontal-dominance finding;
- `reward-discoverability.json` — rank eligibility, dead loot-profile authoring, source coverage és hot-path scan;
- `profession-graph.json` — producer → consumer profession edge-ek és dependency depth;
- `economy-safety.json` — managed faucet/sink matrix, dead/reusable material és salvage/vendor flags.

Ezek balance observability reportok; automatikus dinamikus economy-balancer szándékosan nincs.