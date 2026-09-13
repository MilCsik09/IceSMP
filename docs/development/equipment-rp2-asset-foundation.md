# Equipment Resource Pack 2.0-A/B — Asset Foundation and Four-Family Pilot

## Scope

RP 2.0-A established the production-asset authority and temporary presentation reset. RP2-B adds the canonical Art Bible plus one production-ready four-piece pilot for each ArmorFamily. It does not roll out the remaining 36 lines.

The audit starts from production configuration and runtime presentation consumers, then follows Minecraft item-definition, model-parent, texture, equipment-layer, font and atlas references. A direct filename grep is never sufficient proof that an asset is unused.

## Current asset truth

The deterministic full-pack graph contains **1851 physical resource-pack files**. The 60-file delta is exactly 16 item definitions + 16 models + 16 inventory textures + four equipment definitions + eight worn textures. The committed focus manifest pins the complete graph with SHA-256 `571e1ef5bd96a299ebbc2243a5da1fe0074c45639de40584fb44ef9597942060`; unrelated files stay present in the generated full audit and are never deletion candidates by omission.

The pilot art is no longer a code-drawn placeholder. Each selected line has a committed built-in OpenAI imagegen inventory source sheet and worn turnaround under `docs/development/equipment-rp2-authored-sources/`. The runtime generator performs only deterministic extraction, pixel cleanup, palette reduction and vanilla 1.21.11 UV adaptation; it cannot invent a replacement design when an authored source is absent. Source paths and SHA-256 hashes are part of the canonical pilot manifest and validation gate.

Physical primary states at RP2-A closure:

- ACTIVE: 879
- ACTIVE_SHARED: 267
- STALE: 2
- DUPLICATE: 41
- FUTURE_HANDOFF: 1
- UNKNOWN_REVIEW_REQUIRED: 661
- ORPHAN: 0

The 661 unknown physical files are outside the equipment/material cleanup focus. They are deliberately retained. `UNKNOWN_REVIEW_REQUIRED` is not treated as an orphan state.

The full-pack graph covers equipment, weapon/profession item chains, materials, runes, currency, mobs/entities, HUD/UI, fonts/icons and miscellaneous or historical paths before any equipment/material deletion decision is made. Hash equality is evidence for duplicate review only; it is not deletion authority.

## Canonical equipment authority

Production resolves to exactly **160 canonical armor pieces**, **40 CLOTH**, **40 LEATHER**, **40 MAIL**, and **40 PLATE**, grouped into exactly **40 four-piece gear lines**. The machine-readable per-piece matrix is `equipment-rp2-armor-matrix.json`; the later art-phase input is `equipment-rp2-gear-lines.json`.

All 160 pieces retain a valid inventory representation. The 16 pilot pieces now use RP2 inventory representations; the previous valid Glatziendorf representation remains retained outside the pilot. The current derived remaining inventory-production count is **143**, not a guessed 144.

## Pilot custom worn boundary and temporary fallback

Exactly 16 canonical pilot pieces resolve through four RP2 custom equipment assets at the central `WearablePresentation` boundary. The other 144 pieces still resolve to their backing Material's vanilla worn presentation, including the ELYTRA-backed `fonixpihe_kopeny` anchor.

The reset changes presentation only. ItemTemplate identity, ItemInstance/checksum semantics, attributes, ArmorFamily/proficiency, set behavior, Rune, Signature, Ascension, profession crafting and market/gameplay authority are not redesigned by RP2-A.

The canonical pilot manifest generates a 16-entry immutable runtime index. Only an exact manifest-backed `item-model` + `equipment-asset` pair may bypass the vanilla reset; every nonpilot path still actively restores the backing Material's vanilla `EQUIPPABLE.assetId`. No filesystem, JSON parse or 160-item scan occurs on the equip hot path.

Developer/staging note: **the automated pilot pipeline is complete, but human Minecraft-client visual staging remains required before mass production.**

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
- `equipment-rp2-worn-fallback.json` — 144 temporary worn fallbacks + 16 RP2 custom records + retained legacy files
- `equipment-rp2-safe-delete.json` — explicit deletion authority
- `equipment-rp2-required-new.json` — future inventory/worn requirements
- `equipment-rp2-final-authority.json` — acceptance counts and readiness verdict

The full 1851-file reference graph is generated in CI by `scripts/equipment_rp2_asset_audit.py`; `scripts/generate_equipment_rp2_docs.py --check` proves the committed compact handoff is deterministic and current. `equipment-rp2-art-bible.json` specifies all 40 lines, while `equipment-rp2-pilot-manifest.json` is the single selected-asset authority for the 4/16 pilot.

## Future RP 2.0 roadmap

### RP 2.0-A — Asset Audit + Cleanup + Vanilla Worn Reset

This document's scope. Production authority, fallbacks, safe cleanup proof and handoff only.

### RP 2.0-B — Family Art Bible + Four-Family Pilot

Current scope: a deterministic 40-line Art Bible plus `holdlen`, `vadbor`, `konnyu_otvozet` and `borostyan_tarna` as four complete inventory/worn pilot lines. The pilot uses mid-level crafted lines so family construction can be compared without consuming boss/prestige headroom.

### RP 2.0-C — Remaining Inventory Item Texture Production

Produces only the remaining 143 inventory items declared by the RP2 authority after human pilot acceptance.

### RP 2.0-D — Remaining Worn Equipment Production

Replaces the remaining 144 temporary vanilla worn pieces with 36 coherent line-level worn sets.

### RP 2.0-E — Integration + Visual QA + Staging

Integrates final assets, validates resource-pack/runtime binding and performs staging visual QA. RP2-A does not deploy a production or staging pack.

## Validation contract

Final CI runs the authored catalog audit, full asset graph, final RP2 authority enforcement, committed-manifest drift check, resource-pack unit/validation tooling, equipment visual audit, repository consistency, Java 21 clean build and resource-pack regressions on the exact PR head. It also verifies the stacked parent merge-base and a clean tracked checkout.

## RP2-B readiness

`AUTOMATED_VISUAL_PIPELINE_COMPLETE` means the Art Bible, 4/16 pilot, runtime binding, schema validation, checksums and deterministic offline render evidence are complete. The mass-production verdict is `TECHNICAL_GO_HUMAN_VISUAL_ACCEPTANCE_REQUIRED`: a real 1.21.11 client must still inspect front/back/side, skin compatibility and multiplayer-distance readability before the remaining 36 lines begin.
