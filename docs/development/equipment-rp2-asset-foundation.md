# Equipment Resource Pack 2.0-A — Asset Foundation

## Scope

RP 2.0-A is the production-asset authority and temporary presentation-reset phase. It does **not** define art direction and does not generate final textures, icons, palettes, Blockbench models, or worn sets.

The audit starts from production configuration and runtime presentation consumers, then follows Minecraft item-definition, model-parent, texture, equipment-layer, font and atlas references. A direct filename grep is never sufficient proof that an asset is unused.

## Current asset truth

The deterministic full-pack graph contains **1791 physical resource-pack files**. The committed focus manifest records every equipment/material/armor-linked physical asset and pins the complete graph with SHA-256 `06ebf08227d752280418569b92f7077bc8a772385f01954a91ed95f43a8c985f`; unrelated files stay present in the generated full audit and are never deletion candidates by omission.

Physical primary states at RP2-A closure:

- ACTIVE: 839
- ACTIVE_SHARED: 247
- STALE: 2
- DUPLICATE: 41
- FUTURE_HANDOFF: 1
- UNKNOWN_REVIEW_REQUIRED: 661
- ORPHAN: 0

The 661 unknown physical files are outside the equipment/material cleanup focus. They are deliberately retained. `UNKNOWN_REVIEW_REQUIRED` is not treated as an orphan state.

The full-pack graph covers equipment, weapon/profession item chains, materials, runes, currency, mobs/entities, HUD/UI, fonts/icons and miscellaneous or historical paths before any equipment/material deletion decision is made. Hash equality is evidence for duplicate review only; it is not deletion authority.

## Canonical equipment authority

Production resolves to exactly **160 canonical armor pieces**, **40 CLOTH**, **40 LEATHER**, **40 MAIL**, and **40 PLATE**, grouped into exactly **40 four-piece gear lines**. The machine-readable per-piece matrix is `equipment-rp2-armor-matrix.json`; the later art-phase input is `equipment-rp2-gear-lines.json`.

All 160 pieces retain a valid inventory representation. One current line (`glatziendorfi`) still has one line-local custom inventory item model; the other current representations are vanilla/shared and therefore **159 pieces are explicit RP2 inventory-replacement requirements**. This is a derived work count, not an assumption that every production item must get a new PNG.

## Temporary vanilla worn reset

Every one of the 160 canonical armor pieces now resolves to the backing Material's vanilla worn presentation at the central `WearablePresentation` boundary. This includes the ELYTRA-backed `fonixpihe_kopeny` chest anchor as well as conventional helmet/chestplate/leggings/boots backings.

The reset changes presentation only. ItemTemplate identity, ItemInstance/checksum semantics, attributes, ArmorFamily/proficiency, set behavior, Rune, Signature, Ascension, profession crafting and market/gameplay authority are not redesigned by RP2-A.

For migration safety, the boundary actively restores the backing Material's vanilla `EQUIPPABLE.assetId` when an item is refreshed through normal presentation creation paths. It does not merely stop writing a new custom id, so a serialized legacy item cannot keep the old worn asset solely because it predates RP2-A.

Developer/staging note: **custom worn visuals intentionally reset to vanilla pending Equipment RP 2.0 worn-model production.**

## Legacy worn assets and cleanup policy

The previous Glatziendorf chest worn definition and texture are no longer active canonical worn presentation, but are retained as `STALE`, not deleted:

- `resource-pack/assets/icesmp/equipment/glatziendorfi_jegvert.json`
- `resource-pack/assets/icesmp/textures/entity/equipment/humanoid/glatziendorfi_jegvert.png`

Historic config/resource-pack validation still references these compatibility files. Under RP2-A safe-delete policy, a validator dependency blocks deletion. Therefore `SAFE_TO_DELETE = 0` and `Actually deleted = 0` at closure. This is intentional conservatism, not incomplete cleanup.

## Missing production presentation references

The production audit found 84 profession-recipe `result.item-model` ids that had no first-party `assets/<namespace>/items/*.json` definition. RP2-A does not fabricate replacement art for them. The exact 84-id set is versioned in `wearable-fallback-policy.properties`; runtime intentionally skips those broken ITEM_MODEL ids so the backing Material is the declared inventory fallback.

The final authority therefore has:

- broken effective production reference: 0
- missing mandatory active asset: 0
- SAFE_TO_DELETE asset still referenced: 0
- stale active reference: 0

Any newly introduced missing model id fails the RP2-A manifest gate unless it is deliberately covered by an explicit future policy change.

## Material asset authority

The cumulative economy authority contains **27 managed materials**. One currently has an explicit valid custom appearance (`vad_esszencia`); 26 intentionally use vanilla backing appearance. There are no missing, stale or orphan managed-material assets, and RP2-A does not invent custom textures for reagents solely because an ItemTemplate/material record exists.

## Canonical machine-readable handoff

Committed authority:

- `equipment-rp2-asset-manifest.json` — focused physical manifest + complete-graph digest/count authority
- `equipment-rp2-armor-matrix.json` — 160 production-derived armor records
- `equipment-rp2-gear-lines.json` — exactly 40 art-handoff line records
- `equipment-rp2-materials.json` — managed material visual authority
- `equipment-rp2-worn-fallback.json` — all 160 temporary worn fallbacks + retained legacy files
- `equipment-rp2-safe-delete.json` — explicit deletion authority
- `equipment-rp2-required-new.json` — future inventory/worn requirements
- `equipment-rp2-final-authority.json` — acceptance counts and readiness verdict

The full 1791-file reference graph is generated in CI by `scripts/equipment_rp2_asset_audit.py`; `scripts/generate_equipment_rp2_docs.py --check` proves the committed compact handoff is deterministic and current.

## Future RP 2.0 roadmap

### RP 2.0-A — Asset Audit + Cleanup + Vanilla Worn Reset

This document's scope. Production authority, fallbacks, safe cleanup proof and handoff only.

### RP 2.0-B — Family Art Bible + 40 Gear-Line Concept Design

Consumes the four families and exactly 40 records from `equipment-rp2-gear-lines.json`. One gear line is one coherent worn visual set. RP2-A makes no palette, silhouette, ornament, boss-language or concept recommendation.

### RP 2.0-C — Final Inventory Item Texture Production

Produces only inventory work declared by the RP2 authority.

### RP 2.0-D — Worn Equipment Model Production

Replaces the temporary vanilla worn state with coherent line-level worn sets.

### RP 2.0-E — Integration + Visual QA + Staging

Integrates final assets, validates resource-pack/runtime binding and performs staging visual QA. RP2-A does not deploy a production or staging pack.

## Validation contract

Final CI runs the generated catalog check, full asset graph, final RP2 authority enforcement, committed-manifest drift check, resource-pack unit/validation tooling, equipment visual audit, repository consistency, Java 21 clean build and resource-pack regressions on the exact PR head. It also verifies the stacked parent merge-base and a clean tracked checkout.

## RP2-B readiness

**READY FOR ART BIBLE** means the next agent has one production-derived 40-line list, complete per-piece armor matrix, explicit inventory work list, explicit 40-line worn requirement, managed material decision set, and no effective broken production reference. It does not mean RP2-B art direction has begun.
