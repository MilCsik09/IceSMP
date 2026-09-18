# Special Item Tooltip Profiles — Internal Acceptance

This document is intentionally **development-only**. Do not mirror hidden developer-artifact
names or interaction surfaces into README, FEATURES, PLAYER_GUIDE, BUILDER_GUIDE,
ADMIN_GUIDE, LATEST_CHANGES or RESOURCE_PACK_CMD.

## Scope

The shared tooltip presentation system covers:

- blueprints
- profession materials
- non-canonical profession results
- physical currencies
- money pouches
- relics
- developer artifacts

The presentation layer owns only readable item explanation and visual hierarchy. Identity, PDC,
recipe learning, profession rules, currency/bank semantics, relic ownership/passives and developer
artifact lifecycle remain in their existing authorities.

## Developer Artifact profiles

### Csodálatos Bingulus

Expected presentation:

- `FEJLESZTŐI EREKLYE`
- role: `Jutalomgenerátor`
- operation: passive
- automatic restore line only when the artifact policy enables it
- owner-only authority statement
- existing authored flavor lore
- shared IceSMP dark chrome with the `developer` purple accent

The tooltip must not present the artifact as a disposable debug/test probe.

### Világszövő

Expected presentation:

- `FEJLESZTŐI EREKLYE`
- role: `Világformáló eszköz`
- integrity: `SANDBOX / LIVE_GM`
- current physical presentation state:
  - IDLE → `nyugalmi`
  - SUBJECT_LOCKED → `célpont rögzítve`
  - THREAD_HELD → `szál megtartva`
  - CANON_ARMED → `kanonikus művelet élesítve`
- owner-only authority statement
- existing authored flavor lore
- shared IceSMP dark chrome with the `developer` purple accent

`DevItemManager.renderState(...)` recreates the authoritative physical artifact through
`DevItemFactory.create(..., state)`, so the tooltip state must update together with the item-model
state and must not be cached separately.

## Manual acceptance matrix

Use real factory/runtime paths rather than hand-edited lore.

### Public/survival special items

- Blueprint: `/iceitem tervrajz netherit_csakany 1`
  - TERVRAJZ badge
  - Bányász
  - unlocked recipe/category
  - required profession level
  - right-click learning hint
  - blueprint blue/cyan frame
- Profession material: `/iceitem unique vad_esszencia 1`
  - SZAKMAI ALAPANYAG
  - authored lore
  - source/process/sink where authored
  - profession gold frame
- Profession result: `/iceitem recept netherit_csakany 1` plus a potion/utility recipe
  - SZAKMAI TÁRGY
  - category/kind/required profession level
  - canonical/rolled gear keeps rarity frame
  - non-rarity utility output uses profession gold frame
- Custom potion:
  - semantic HATÁS section from `result.potion-effects`
  - translated effect name
  - amplifier when > 0
  - duration for non-instant effects
  - vanilla additional potion tooltip hidden to avoid duplicate information
- Currency: withdraw one physical token through the normal bank path in a capital
  - FIZIKAI VALUTA
  - faction name
  - bank/physical-cash explanation
  - faction-coloured category frame
- Money pouch: `/iceitem erszeny 25 1`
  - TALÁLT ERSZÉNY
  - hidden currency/value until opening
  - right-click instruction
  - gold frame
- Relic: `/iceitem relikvia metelytepo 1`
  - RELIKVIA
  - authored Rendeltetés
  - existing lore
  - Ereklye teal frame

### Hidden developer artifacts

- Bingulus: issue via the configured owner-only path (or owner-only DEV item command where
  permitted by the existing authority)
- Világszövő: use the existing owner-bound issuance/session path

Do not add a public command, public permission, public feature catalogue entry or player guide
surface for hidden developer artifacts.

## Visual gate

For every profile:

- no magenta/missing-texture area
- category/rarity frame survives ItemMeta/data-component refreshes
- custom glyph renders when the IceSMP pack is active
- readable text remains meaningful without relying on the private-use glyph itself
- item name is the dominant header
- category badge is compact
- requirements/use/role information is grouped instead of flattened into a long lore list
- authored flavor remains subordinate to mechanics/use copy

## Automated contracts

- `TooltipPresentationRegressionSuite`
- `SpecialItemTooltipRegressionSuite`
- `scripts/test_resource_pack.py`
- resource-pack validate-only workflow executes `onboardingDialogRegressionTest`, which chains
  through the immersive UX and tooltip presentation regression suites.
