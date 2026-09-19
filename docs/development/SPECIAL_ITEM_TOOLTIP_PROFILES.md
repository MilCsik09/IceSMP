# Special Item Tooltip Profiles — Internal Acceptance

This document is intentionally **development-only**. Do not mirror hidden developer-artifact
names or interaction surfaces into README, FEATURES, PLAYER_GUIDE, BUILDER_GUIDE,
ADMIN_GUIDE, LATEST_CHANGES or RESOURCE_PACK_CMD.

## Live visual language

The post-screenshot production target is a compact MMORPG stat-card rather than a sectioned
mini-document:

- the Minecraft item display name remains the dominant header;
- the first lore row is a compact classification such as `VALUTA • CRYGHALIRIS` or
  `TERVRAJZ • Bányász`;
- mechanics follow as short label/value rows without redundant section headings;
- fixed + rolled values of the same canonical stat render as one combined row;
- authored lore remains important, but is secondary, wrapped and usually limited to a few lines;
- explanatory prose that repeats obvious mechanics is removed;
- production readability must not depend on private-use font glyphs;
- long archaeology observations are wrapped and their temporary display clone hides the vanilla
  attribute block;
- frame/accent geometry remains unchanged until the compact layout is accepted live.

Currency classification uses canonical issuer/world names:
- RED → `Perinfernicitas`
- BLUE → `Cryghaliris`
- NEUTRAL → `Ryanora & Caldestera`
- DARK → `Thanaopolis`

A physical currency item is one unit by definition, therefore no redundant worth/value row is
shown.

## Scope

The shared tooltip presentation system covers:

- blueprints
- profession materials
- non-canonical profession results
- physical currencies
- money pouches
- relics
- quest items
- progression tokens
- crate keys
- upgrades / runes
- utility items
- companion / capture / summon items
- siege weapons
- class catalyst artifacts
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
- Quest item: `/iceitem unique suttogas_meghivo 1`
  - KÜLDETÉSI TÁRGY
  - authored purpose/use
  - quest gold frame
- Progression token: `/iceitem unique emlekszilank 1`
  - HALADÁSI TÁRGY
  - redeem/use hint
  - token purple frame
- Upgrade/rune: `/iceitem unique runa_elek 1`
  - FEJLESZTÉS
  - authored mechanical lore
  - upgrade teal frame
- Utility: `/iceitem unique ures_kupa 1`
  - SEGÉDESZKÖZ
  - reusable-purpose hint
  - utility gray frame
- Crate key: `/crate give <player> <crate-id> 1`
  - LÁDAKULCS
  - target crate and interaction
  - top three reward odds when available
  - key light/silver frame
- Companion item: use the normal `/pet item` path with an eligible specialization
  - TÁRSFELSZERELÉS / IDÉZŐ KELLÉK / TÁRSKÖTŐ ESZKÖZ according to the item tag
  - purpose/restriction copy from the existing companion item authority
  - capture green frame
- Siege weapon: use the existing craft/raid acquisition path
  - OSTROMESZKÖZ
  - explicit only-during-siege condition
  - right-click combat use
  - siege red frame
- Catalyst / class artifact: use the normal `/profile` claim path
  - LÉLEKKAPOCS
  - current class
  - current form/evolution
  - active specialization, or the explicit no-specialization state
  - catalyst purple frame

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
