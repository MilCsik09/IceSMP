# IceSMP HUD- és asset-audit

A lista forráskód- és PNG-alapú. Minecraft klienses vizuális elfogadást nem helyettesít.

Összesen **499** PNG: **499 PASS**, **0 WARN**, **0 FAIL**.

## Class-mechanika ellenőrzőlap

| Class | Kötelező élő visszajelzések | HUD-adat | Render | Egyedi asset |
|---|---|---:|---:|---:|
| Warrior | battle_tempo; blood_frenzy + exhaustion/overdrive/aftermath; guard + oath target | ✓ | ✓ | ✓ |
| Evoker | empower rank/hold/fizzle; essence colour; resonance/burst; imprint/ally/echo | ✓ | ✓ | ✓ |
| Archer | wind_read; prey + precision_chain/weak point; beast bond | ✓ | ✓ | ✓ |
| Shaman | main/companion totem wheel; resonance/overload; maelstrom; tide; blessing side | ✓ | ✓ | ✓ |
| Monk | flow; combo_chain; stagger pool; mist_threads and linked allies | ✓ | ✓ | ✓ |
| Paladin | oath + conviction; beacon target; judgement_marks; shield_charge | ✓ | ✓ | ✓ |
| Demon Hunter | load bands/overload; fragments + momentum; pain; typed sigil slots | ✓ | ✓ | ✓ |
| Druid | harmony + season/autumn; feral combo/scent; balance/eclipse; bark/roots; seed ripeness | ✓ | ✓ | ✓ |
| Priest | litany/verses/recited; shield_web/atonement/conversion; marrow + ossuary; madness | ✓ | ✓ | ✓ |
| Death Knight | blood/frost/death runes and ready/spent/regenerating/locked; blood memory; frost marks; plague/mutation | ✓ | ✓ | ✓ |
| Assassin | four openings; toxin slots/dose; detection/stealth/trail/echo; infection/strain | ✓ | ✓ | ✓ |
| Warlock | soul debt/cap; curses/soul thread; embers/overheat; demon roster | ✓ | ✓ | ✓ |
| Wizard | runewaving school/reaction; fire/frost/arcane attunement/convergence/crown; court | ✓ | ✓ | ✓ |

A mechanikacsaládok alállapotai a tipizált `state`/`proc` szövegben és az egységes `active` / `ready` / `alert` / `spent` képi variánsokban különülnek el; a charge/stack/rúna sorok külön slot-adatot és saját mechanika-glyphet kapnak.

## Mechanika-asset lefedettség

| Class | Mechanikaasset | Native állapotok | BetterHud állapotok | Adat/render út |
|---|---|---|---|---|
| Warrior | `battle_tempo` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Evoker | `empower` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Archer | `wind_read` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Shaman | `totem_wheel` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Monk | `flow` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Paladin | `conviction` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Demon Hunter | `load` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Druid | `harmony` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Priest | `litany` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Death Knight | `rune_wheel` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Assassin | `opening` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Warlock | `soul_debt` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Wizard | `runewaving` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Warrior | `blood_frenzy` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Warrior | `guard` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Evoker | `resonance` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Evoker | `imprint` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Archer | `precision_chain` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Archer | `bond` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Shaman | `resonance` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Shaman | `maelstrom` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Shaman | `tide` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Monk | `combo_chain` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Monk | `stagger` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Monk | `mist_threads` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Paladin | `beacon` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Paladin | `judgement_marks` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Paladin | `shield_charge` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Demon Hunter | `fragments` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Demon Hunter | `pain` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Demon Hunter | `sigil` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Druid | `combo` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Druid | `balance` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Druid | `bark` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Druid | `seeds` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Priest | `shield_web` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Priest | `marrow` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Priest | `madness` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Death Knight | `blood_memory` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Death Knight | `frost_marks` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Death Knight | `plague` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Assassin | `toxin` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Assassin | `detection` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Assassin | `infection` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Warlock | `curses` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Warlock | `embers` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Warlock | `demons` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Wizard | `attunement` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |
| Wizard | `court` | active / ready / alert / spent | active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |

## Assetcsoport-ellenőrzőlap

| Csoport | Fájl | PASS | WARN | FAIL |
|---|---:|---:|---:|---:|
| mechanic/status | 421 | 421 | 0 | 0 |
| class icon | 27 | 27 | 0 | 0 |
| frame/popup | 21 | 21 | 0 | 0 |
| wallet/event/level | 12 | 12 | 0 | 0 |
| resource/bar | 10 | 10 | 0 | 0 |
| text/source/preview | 8 | 8 | 0 | 0 |

## Statikus runtime-audit

| Terület | Ellenőrzött invariáns | Eredmény |
|---|---|---:|
| Wallet | A primary valuta nulla egyenleggel is megjelenik; a többi csak pozitív egyenleggel; immutable snapshot, négy fix slot. | PASS |
| Class-/specváltás | A render minden tickben az új Profile v2 + transient class runtime snapshotból épül; nincs külön tartós HUD/class authority. | PASS |
| Vendégkeret | Külön külső Menedék-héj, a kanonikus frakciókerettel azonos belső alpha-geometria és rögzített glyph-cella. | PASS |
| BetterHud/fallback | Első-party pack-ready HUD → opcionális BetterHud-ready HUD → Paper sidebar / Folia compact bossbar; egyszerre csak egy class HUD. | PASS |
| Folia | A globális tick csak iterál; minden Player-olvasás és render a játékos entity schedulerén történik, az async híd csak immutable `HudSnapshot`-ot olvas. | PASS |
| Authority | Profile v2 a tartós class/spec authority; a class service-ek UUID-mapjai lifecycle-takarított transient combat runtime-ok. | PASS |

## Tételes PNG-ellenőrzés

A margó sorrendje bal/felső/jobb/alsó; a középeltérés x/y pixel. A glyph-width marker alpha=1, ezért a látható bbox azt figyelmen kívül hagyja.

| Asset | Méret | Mód | Alpha | Látható bbox | Margó | Közép | Élesség | Magenta | Eredmény | Megjegyzés |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `deploy/betterhud/assets/icesmp/charge-ready.png` | 32x32 | RGBA | 0..255/33 | 1,1–31,31 | 1/1/1/1 | +0.0/+0.0 | 65.55 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/charge-spent.png` | 32x32 | RGBA | 0..255/33 | 1,1–31,31 | 1/1/1/1 | +0.0/+0.0 | 54.28 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-archer.png` | 64x64 | RGBA | 0..255/2 | 5,6–59,58 | 5/6/5/6 | +0.0/+0.0 | 22.53 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-assassin.png` | 64x64 | RGBA | 0..255/2 | 15,5–48,59 | 15/5/16/5 | -0.5/+0.0 | 14.52 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-death_knight.png` | 64x64 | RGBA | 0..255/2 | 17,5–47,59 | 17/5/17/5 | +0.0/+0.0 | 16.61 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-demon_hunter.png` | 64x64 | RGBA | 0..255/2 | 13,5–51,59 | 13/5/13/5 | +0.0/+0.0 | 15.89 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-druid.png` | 64x64 | RGBA | 0..255/2 | 17,5–47,59 | 17/5/17/5 | +0.0/+0.0 | 12.92 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-evoker.png` | 64x64 | RGBA | 0..255/2 | 5,5–59,59 | 5/5/5/5 | +0.0/+0.0 | 23.28 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-monk.png` | 64x64 | RGBA | 0..255/2 | 9,5–55,59 | 9/5/9/5 | +0.0/+0.0 | 25.12 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-paladin.png` | 64x64 | RGBA | 0..255/2 | 8,5–55,59 | 8/5/9/5 | -0.5/+0.0 | 31.62 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-priest.png` | 64x64 | RGBA | 0..255/2 | 16,5–47,59 | 16/5/17/5 | -0.5/+0.0 | 11.68 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-shaman.png` | 64x64 | RGBA | 0..255/2 | 11,5–53,58 | 11/5/11/6 | +0.0/-0.5 | 26.65 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-warlock.png` | 64x64 | RGBA | 0..255/2 | 15,5–49,58 | 15/5/15/6 | +0.0/-0.5 | 11.21 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-warrior.png` | 64x64 | RGBA | 0..255/2 | 6,5–57,59 | 6/5/7/5 | -0.5/+0.0 | 20.07 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/class-wizard.png` | 64x64 | RGBA | 0..255/2 | 5,8–59,55 | 5/8/5/9 | +0.0/-0.5 | 16.29 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/emblem-blue.png` | 24x24 | RGBA | 0..255/2 | 2,1–23,24 | 2/1/1/0 | +0.5/+0.5 | 58.70 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/emblem-dark.png` | 24x24 | RGBA | 0..255/2 | 2,1–23,24 | 2/1/1/0 | +0.5/+0.5 | 56.08 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/emblem-guest.png` | 24x24 | RGBA | 0..255/2 | 2,1–23,24 | 2/1/1/0 | +0.5/+0.5 | 47.78 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/emblem-neutral.png` | 24x24 | RGBA | 0..255/2 | 2,1–23,24 | 2/1/1/0 | +0.5/+0.5 | 52.60 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/emblem-red.png` | 24x24 | RGBA | 0..255/2 | 2,1–23,24 | 2/1/1/0 | +0.5/+0.5 | 46.18 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/frame-hud-blue.png` | 204x126 | RGBA | 0..255/218 | 2,2–202,124 | 2/2/2/2 | +0.0/+0.0 | 35.06 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/frame-hud-dark.png` | 204x126 | RGBA | 0..255/247 | 6,1–198,125 | 6/1/6/1 | +0.0/+0.0 | 34.05 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/frame-hud-guest.png` | 204x126 | RGBA | 0..255/218 | 2,2–202,124 | 2/2/2/2 | +0.0/+0.0 | 35.72 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/frame-hud-neutral.png` | 204x126 | RGBA | 0..255/199 | 9,3–195,122 | 9/3/9/4 | +0.0/-0.5 | 33.56 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/frame-hud-red.png` | 204x126 | RGBA | 0..255/219 | 8,3–196,123 | 8/3/8/3 | +0.0/+0.0 | 32.14 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/icon-event.png` | 64x64 | RGBA | 0..255/2 | 9,7–54,57 | 9/7/10/7 | -0.5/+0.0 | 20.47 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/icon-level.png` | 64x64 | RGBA | 0..255/2 | 16,7–48,57 | 16/7/16/7 | +0.0/+0.0 | 17.99 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/icon-money.png` | 64x64 | RGBA | 0..255/2 | 8,7–55,57 | 8/7/9/7 | -0.5/+0.0 | 39.32 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-bond-active.png` | 64x64 | RGBA | 0..255/179 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 35.25 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-bond-alert.png` | 64x64 | RGBA | 0..255/159 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 31.45 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-bond-ready.png` | 64x64 | RGBA | 0..255/225 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 48.67 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-bond-spent.png` | 64x64 | RGBA | 0..191/150 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 25.78 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-precision_chain-active.png` | 64x64 | RGBA | 0..255/198 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 23.07 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-precision_chain-alert.png` | 64x64 | RGBA | 0..255/175 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 22.04 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-precision_chain-ready.png` | 64x64 | RGBA | 0..255/235 | 3,8–61,55 | 3/8/3/9 | +0.0/-0.5 | 37.27 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-precision_chain-spent.png` | 64x64 | RGBA | 0..191/158 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 17.81 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-wind_read-active.png` | 64x64 | RGBA | 0..255/228 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 51.23 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-wind_read-alert.png` | 64x64 | RGBA | 0..255/201 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 49.73 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-wind_read-ready.png` | 64x64 | RGBA | 0..255/248 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 56.79 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-archer-wind_read-spent.png` | 64x64 | RGBA | 0..191/178 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 38.50 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-detection-active.png` | 64x64 | RGBA | 0..255/198 | 6,12–58,51 | 6/12/6/13 | +0.0/-0.5 | 36.20 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-detection-alert.png` | 64x64 | RGBA | 0..255/176 | 6,12–58,51 | 6/12/6/13 | +0.0/-0.5 | 32.65 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-detection-ready.png` | 64x64 | RGBA | 0..255/228 | 3,9–61,54 | 3/9/3/10 | +0.0/-0.5 | 36.46 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-detection-spent.png` | 64x64 | RGBA | 0..191/156 | 6,12–58,51 | 6/12/6/13 | +0.0/-0.5 | 27.84 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-infection-active.png` | 64x64 | RGBA | 0..255/156 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 38.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-infection-alert.png` | 64x64 | RGBA | 0..255/141 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 33.63 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-infection-ready.png` | 64x64 | RGBA | 0..255/208 | 4,5–61,58 | 4/5/3/6 | +0.5/-0.5 | 47.38 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-infection-spent.png` | 64x64 | RGBA | 0..191/133 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 28.68 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-opening-active.png` | 64x64 | RGBA | 0..255/166 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 25.31 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-opening-alert.png` | 64x64 | RGBA | 0..255/147 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 23.68 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-opening-ready.png` | 64x64 | RGBA | 0..255/217 | 4,3–59,61 | 4/3/5/3 | -0.5/+0.0 | 37.01 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-opening-spent.png` | 64x64 | RGBA | 0..191/135 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 17.79 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-toxin-active.png` | 64x64 | RGBA | 0..255/153 | 19,6–44,58 | 19/6/20/6 | -0.5/+0.0 | 26.40 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-toxin-alert.png` | 64x64 | RGBA | 0..255/139 | 19,6–44,58 | 19/6/20/6 | -0.5/+0.0 | 24.07 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-toxin-ready.png` | 64x64 | RGBA | 0..255/205 | 16,3–47,61 | 16/3/17/3 | -0.5/+0.0 | 37.82 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-assassin-toxin-spent.png` | 64x64 | RGBA | 0..191/132 | 19,6–44,58 | 19/6/20/6 | -0.5/+0.0 | 19.90 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-blood_memory-active.png` | 64x64 | RGBA | 0..255/181 | 11,6–53,58 | 11/6/11/6 | +0.0/+0.0 | 33.46 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-blood_memory-alert.png` | 64x64 | RGBA | 0..255/158 | 11,6–53,58 | 11/6/11/6 | +0.0/+0.0 | 30.35 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-blood_memory-ready.png` | 64x64 | RGBA | 0..255/226 | 8,3–56,61 | 8/3/8/3 | +0.0/+0.0 | 46.76 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-blood_memory-spent.png` | 64x64 | RGBA | 0..191/148 | 11,6–53,58 | 11/6/11/6 | +0.0/+0.0 | 24.20 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-frost_marks-active.png` | 64x64 | RGBA | 0..255/194 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 39.92 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-frost_marks-alert.png` | 64x64 | RGBA | 0..255/171 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 35.64 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-frost_marks-ready.png` | 64x64 | RGBA | 0..255/227 | 7,3–56,61 | 7/3/8/3 | -0.5/+0.0 | 53.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-frost_marks-spent.png` | 64x64 | RGBA | 0..191/156 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 29.45 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-plague-active.png` | 64x64 | RGBA | 0..255/202 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 42.54 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-plague-alert.png` | 64x64 | RGBA | 0..255/178 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 38.60 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-plague-ready.png` | 64x64 | RGBA | 0..255/235 | 3,6–61,58 | 3/6/3/6 | +0.0/+0.0 | 52.48 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-plague-spent.png` | 64x64 | RGBA | 0..191/165 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 32.46 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-rune_wheel-active.png` | 64x64 | RGBA | 0..255/161 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 38.19 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-rune_wheel-alert.png` | 64x64 | RGBA | 0..255/141 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 33.89 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-rune_wheel-ready.png` | 64x64 | RGBA | 0..255/207 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 50.67 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-death_knight-rune_wheel-spent.png` | 64x64 | RGBA | 0..191/135 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 26.29 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-fragments-active.png` | 64x64 | RGBA | 0..255/149 | 16,6–47,58 | 16/6/17/6 | -0.5/+0.0 | 29.50 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-fragments-alert.png` | 64x64 | RGBA | 0..255/138 | 16,6–47,58 | 16/6/17/6 | -0.5/+0.0 | 26.86 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-fragments-ready.png` | 64x64 | RGBA | 0..255/201 | 13,3–50,61 | 13/3/14/3 | -0.5/+0.0 | 39.58 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-fragments-spent.png` | 64x64 | RGBA | 0..191/126 | 16,6–47,58 | 16/6/17/6 | -0.5/+0.0 | 23.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-load-active.png` | 64x64 | RGBA | 0..255/170 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 40.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-load-alert.png` | 64x64 | RGBA | 0..255/150 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 36.58 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-load-ready.png` | 64x64 | RGBA | 0..255/216 | 4,3–59,61 | 4/3/5/3 | -0.5/+0.0 | 54.17 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-load-spent.png` | 64x64 | RGBA | 0..191/140 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 28.16 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-pain-active.png` | 64x64 | RGBA | 0..255/205 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 39.09 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-pain-alert.png` | 64x64 | RGBA | 0..255/181 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 34.64 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-pain-ready.png` | 64x64 | RGBA | 0..255/238 | 6,3–58,61 | 6/3/6/3 | +0.0/+0.0 | 49.83 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-pain-spent.png` | 64x64 | RGBA | 0..191/164 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 28.64 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-sigil-active.png` | 64x64 | RGBA | 0..255/189 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 45.08 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-sigil-alert.png` | 64x64 | RGBA | 0..255/167 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 43.25 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-sigil-ready.png` | 64x64 | RGBA | 0..255/232 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 44.11 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-demon_hunter-sigil-spent.png` | 64x64 | RGBA | 0..191/153 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 37.44 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-balance-active.png` | 64x64 | RGBA | 0..255/229 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 47.94 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-balance-alert.png` | 64x64 | RGBA | 0..255/203 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 46.50 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-balance-ready.png` | 64x64 | RGBA | 0..255/247 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 55.10 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-balance-spent.png` | 64x64 | RGBA | 0..191/176 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 38.45 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-bark-active.png` | 64x64 | RGBA | 0..255/172 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 35.35 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-bark-alert.png` | 64x64 | RGBA | 0..255/153 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 31.33 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-bark-ready.png` | 64x64 | RGBA | 0..255/218 | 7,3–56,61 | 7/3/8/3 | -0.5/+0.0 | 49.21 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-bark-spent.png` | 64x64 | RGBA | 0..191/147 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 24.91 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-combo-active.png` | 64x64 | RGBA | 0..255/218 | 12,6–51,58 | 12/6/13/6 | -0.5/+0.0 | 39.33 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-combo-alert.png` | 64x64 | RGBA | 0..255/193 | 12,6–51,58 | 12/6/13/6 | -0.5/+0.0 | 38.57 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-combo-ready.png` | 64x64 | RGBA | 0..255/244 | 9,3–54,61 | 9/3/10/3 | -0.5/+0.0 | 50.31 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-combo-spent.png` | 64x64 | RGBA | 0..191/174 | 12,6–51,58 | 12/6/13/6 | -0.5/+0.0 | 32.23 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-harmony-active.png` | 64x64 | RGBA | 0..255/185 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 48.39 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-harmony-alert.png` | 64x64 | RGBA | 0..255/165 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 42.51 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-harmony-ready.png` | 64x64 | RGBA | 0..255/218 | 3,4–61,60 | 3/4/3/4 | +0.0/+0.0 | 60.66 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-harmony-spent.png` | 64x64 | RGBA | 0..191/156 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 34.22 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-seeds-active.png` | 64x64 | RGBA | 0..255/147 | 19,6–45,58 | 19/6/19/6 | +0.0/+0.0 | 24.53 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-seeds-alert.png` | 64x64 | RGBA | 0..255/136 | 19,6–45,58 | 19/6/19/6 | +0.0/+0.0 | 22.30 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-seeds-ready.png` | 64x64 | RGBA | 0..255/206 | 16,3–48,61 | 16/3/16/3 | +0.0/+0.0 | 35.30 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-druid-seeds-spent.png` | 64x64 | RGBA | 0..191/125 | 19,6–45,58 | 19/6/19/6 | +0.0/+0.0 | 18.64 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-empower-active.png` | 64x64 | RGBA | 0..255/186 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 40.56 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-empower-alert.png` | 64x64 | RGBA | 0..255/161 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 36.31 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-empower-ready.png` | 64x64 | RGBA | 0..255/234 | 4,3–60,60 | 4/3/4/4 | +0.0/-0.5 | 51.22 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-empower-spent.png` | 64x64 | RGBA | 0..191/153 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 28.35 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-imprint-active.png` | 64x64 | RGBA | 0..255/221 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 54.51 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-imprint-alert.png` | 64x64 | RGBA | 0..255/198 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 49.84 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-imprint-ready.png` | 64x64 | RGBA | 0..255/245 | 5,3–59,61 | 5/3/5/3 | +0.0/+0.0 | 55.69 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-imprint-spent.png` | 64x64 | RGBA | 0..191/173 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 42.01 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-resonance-active.png` | 64x64 | RGBA | 0..255/227 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 47.79 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-resonance-alert.png` | 64x64 | RGBA | 0..255/200 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 44.87 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-resonance-ready.png` | 64x64 | RGBA | 0..255/249 | 4,4–61,60 | 4/4/3/4 | +0.5/+0.0 | 58.72 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-evoker-resonance-spent.png` | 64x64 | RGBA | 0..191/178 | 7,7–58,57 | 7/7/6/7 | +0.5/+0.0 | 38.23 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-combo_chain-active.png` | 64x64 | RGBA | 0..255/163 | 6,19–58,44 | 6/19/6/20 | +0.0/-0.5 | 24.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-combo_chain-alert.png` | 64x64 | RGBA | 0..255/145 | 6,19–58,44 | 6/19/6/20 | +0.0/-0.5 | 22.93 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-combo_chain-ready.png` | 64x64 | RGBA | 0..255/214 | 3,16–61,47 | 3/16/3/17 | +0.0/-0.5 | 40.73 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-combo_chain-spent.png` | 64x64 | RGBA | 0..191/135 | 6,19–58,44 | 6/19/6/20 | +0.0/-0.5 | 17.53 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-flow-active.png` | 64x64 | RGBA | 0..255/171 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 49.32 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-flow-alert.png` | 64x64 | RGBA | 0..255/154 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 44.30 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-flow-ready.png` | 64x64 | RGBA | 0..255/226 | 3,3–61,61 | 3/3/3/3 | +0.0/+0.0 | 57.18 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-flow-spent.png` | 64x64 | RGBA | 0..191/143 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 36.35 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-mist_threads-active.png` | 64x64 | RGBA | 0..255/200 | 17,6–47,58 | 17/6/17/6 | +0.0/+0.0 | 34.83 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-mist_threads-alert.png` | 64x64 | RGBA | 0..255/178 | 17,6–47,58 | 17/6/17/6 | +0.0/+0.0 | 33.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-mist_threads-ready.png` | 64x64 | RGBA | 0..255/238 | 14,4–50,61 | 14/4/14/3 | +0.0/+0.5 | 28.77 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-mist_threads-spent.png` | 64x64 | RGBA | 0..191/161 | 17,6–47,58 | 17/6/17/6 | +0.0/+0.0 | 29.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-stagger-active.png` | 64x64 | RGBA | 0..255/205 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 39.74 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-stagger-alert.png` | 64x64 | RGBA | 0..255/185 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 36.28 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-stagger-ready.png` | 64x64 | RGBA | 0..255/237 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 47.92 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-monk-stagger-spent.png` | 64x64 | RGBA | 0..191/165 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 29.89 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-beacon-active.png` | 64x64 | RGBA | 0..255/234 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 51.62 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-beacon-alert.png` | 64x64 | RGBA | 0..255/204 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 45.52 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-beacon-ready.png` | 64x64 | RGBA | 0..255/249 | 5,3–59,61 | 5/3/5/3 | +0.0/+0.0 | 51.10 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-beacon-spent.png` | 64x64 | RGBA | 0..191/177 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 36.82 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-conviction-active.png` | 64x64 | RGBA | 0..255/152 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 43.37 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-conviction-alert.png` | 64x64 | RGBA | 0..255/138 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 39.06 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-conviction-ready.png` | 64x64 | RGBA | 0..255/206 | 5,3–60,61 | 5/3/4/3 | +0.5/+0.0 | 53.27 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-conviction-spent.png` | 64x64 | RGBA | 0..191/128 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 30.11 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-judgement_marks-active.png` | 64x64 | RGBA | 0..255/154 | 6,17–58,47 | 6/17/6/17 | +0.0/+0.0 | 29.86 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-judgement_marks-alert.png` | 64x64 | RGBA | 0..255/139 | 6,17–58,47 | 6/17/6/17 | +0.0/+0.0 | 28.23 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-judgement_marks-ready.png` | 64x64 | RGBA | 0..255/204 | 3,14–61,50 | 3/14/3/14 | +0.0/+0.0 | 39.90 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-judgement_marks-spent.png` | 64x64 | RGBA | 0..191/129 | 6,17–58,47 | 6/17/6/17 | +0.0/+0.0 | 23.24 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-shield_charge-active.png` | 64x64 | RGBA | 0..255/183 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 43.98 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-shield_charge-alert.png` | 64x64 | RGBA | 0..255/160 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 40.15 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-shield_charge-ready.png` | 64x64 | RGBA | 0..255/228 | 3,5–61,58 | 3/5/3/6 | +0.0/-0.5 | 43.79 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-paladin-shield_charge-spent.png` | 64x64 | RGBA | 0..191/146 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 34.54 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-litany-active.png` | 64x64 | RGBA | 0..255/155 | 6,7–58,56 | 6/7/6/8 | +0.0/-0.5 | 37.88 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-litany-alert.png` | 64x64 | RGBA | 0..255/135 | 6,7–58,56 | 6/7/6/8 | +0.0/-0.5 | 32.70 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-litany-ready.png` | 64x64 | RGBA | 0..255/208 | 3,4–61,59 | 3/4/3/5 | +0.0/-0.5 | 49.28 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-litany-spent.png` | 64x64 | RGBA | 0..191/130 | 6,7–58,56 | 6/7/6/8 | +0.0/-0.5 | 24.92 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-madness-active.png` | 64x64 | RGBA | 0..255/185 | 8,6–55,58 | 8/6/9/6 | -0.5/+0.0 | 38.53 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-madness-alert.png` | 64x64 | RGBA | 0..255/161 | 8,6–55,58 | 8/6/9/6 | -0.5/+0.0 | 35.17 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-madness-ready.png` | 64x64 | RGBA | 0..255/228 | 5,3–58,61 | 5/3/6/3 | -0.5/+0.0 | 47.24 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-madness-spent.png` | 64x64 | RGBA | 0..191/154 | 8,6–55,58 | 8/6/9/6 | -0.5/+0.0 | 29.49 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-marrow-active.png` | 64x64 | RGBA | 0..255/171 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 41.51 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-marrow-alert.png` | 64x64 | RGBA | 0..255/150 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 37.82 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-marrow-ready.png` | 64x64 | RGBA | 0..255/219 | 6,3–57,61 | 6/3/7/3 | -0.5/+0.0 | 54.39 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-marrow-spent.png` | 64x64 | RGBA | 0..191/137 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 31.34 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-shield_web-active.png` | 64x64 | RGBA | 0..255/212 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 40.49 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-shield_web-alert.png` | 64x64 | RGBA | 0..255/187 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 37.31 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-shield_web-ready.png` | 64x64 | RGBA | 0..255/240 | 3,8–61,55 | 3/8/3/9 | +0.0/-0.5 | 54.11 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-priest-shield_web-spent.png` | 64x64 | RGBA | 0..191/166 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 28.98 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-maelstrom-active.png` | 64x64 | RGBA | 0..255/229 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 52.24 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-maelstrom-alert.png` | 64x64 | RGBA | 0..255/203 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 48.27 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-maelstrom-ready.png` | 64x64 | RGBA | 0..255/249 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 63.51 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-maelstrom-spent.png` | 64x64 | RGBA | 0..191/177 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 40.63 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-resonance-active.png` | 64x64 | RGBA | 0..255/210 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 49.26 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-resonance-alert.png` | 64x64 | RGBA | 0..255/182 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 45.52 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-resonance-ready.png` | 64x64 | RGBA | 0..255/243 | 3,3–61,61 | 3/3/3/3 | +0.0/+0.0 | 48.58 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-resonance-spent.png` | 64x64 | RGBA | 0..191/162 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 39.11 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-tide-active.png` | 64x64 | RGBA | 0..255/225 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 47.05 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-tide-alert.png` | 64x64 | RGBA | 0..255/200 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 42.80 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-tide-ready.png` | 64x64 | RGBA | 0..255/237 | 3,6–61,58 | 3/6/3/6 | +0.0/+0.0 | 54.81 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-tide-spent.png` | 64x64 | RGBA | 0..191/171 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 34.01 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-totem_wheel-active.png` | 64x64 | RGBA | 0..255/196 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 41.27 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-totem_wheel-alert.png` | 64x64 | RGBA | 0..255/174 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 38.09 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-totem_wheel-ready.png` | 64x64 | RGBA | 0..255/226 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 54.49 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-shaman-totem_wheel-spent.png` | 64x64 | RGBA | 0..191/161 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 29.10 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-curses-active.png` | 64x64 | RGBA | 0..255/197 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 42.68 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-curses-alert.png` | 64x64 | RGBA | 0..255/176 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 38.87 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-curses-ready.png` | 64x64 | RGBA | 0..255/228 | 3,3–61,61 | 3/3/3/3 | +0.0/+0.0 | 56.42 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-curses-spent.png` | 64x64 | RGBA | 0..191/156 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 31.40 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-demons-active.png` | 64x64 | RGBA | 0..255/147 | 17,6–46,58 | 17/6/18/6 | -0.5/+0.0 | 28.87 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-demons-alert.png` | 64x64 | RGBA | 0..255/136 | 17,6–46,58 | 17/6/18/6 | -0.5/+0.0 | 26.77 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-demons-ready.png` | 64x64 | RGBA | 0..255/206 | 14,3–49,61 | 14/3/15/3 | -0.5/+0.0 | 39.13 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-demons-spent.png` | 64x64 | RGBA | 0..191/124 | 17,6–46,58 | 17/6/18/6 | -0.5/+0.0 | 22.74 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-embers-active.png` | 64x64 | RGBA | 0..255/213 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 39.36 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-embers-alert.png` | 64x64 | RGBA | 0..255/189 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 36.24 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-embers-ready.png` | 64x64 | RGBA | 0..255/246 | 7,3–56,61 | 7/3/8/3 | -0.5/+0.0 | 51.69 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-embers-spent.png` | 64x64 | RGBA | 0..191/170 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 31.02 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-soul_debt-active.png` | 64x64 | RGBA | 0..255/196 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 35.56 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-soul_debt-alert.png` | 64x64 | RGBA | 0..255/170 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 31.65 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-soul_debt-ready.png` | 64x64 | RGBA | 0..255/231 | 6,3–58,61 | 6/3/6/3 | +0.0/+0.0 | 49.46 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warlock-soul_debt-spent.png` | 64x64 | RGBA | 0..191/163 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 24.40 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-battle_tempo-active.png` | 64x64 | RGBA | 0..255/243 | 6,10–58,54 | 6/10/6/10 | +0.0/+0.0 | 47.66 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-battle_tempo-alert.png` | 64x64 | RGBA | 0..255/213 | 6,10–58,54 | 6/10/6/10 | +0.0/+0.0 | 45.72 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-battle_tempo-ready.png` | 64x64 | RGBA | 0..255/254 | 3,7–61,57 | 3/7/3/7 | +0.0/+0.0 | 58.46 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-battle_tempo-spent.png` | 64x64 | RGBA | 0..191/185 | 6,10–58,54 | 6/10/6/10 | +0.0/+0.0 | 34.73 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-blood_frenzy-active.png` | 64x64 | RGBA | 0..255/201 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 38.96 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-blood_frenzy-alert.png` | 64x64 | RGBA | 0..255/179 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 36.91 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-blood_frenzy-ready.png` | 64x64 | RGBA | 0..255/239 | 4,3–59,61 | 4/3/5/3 | -0.5/+0.0 | 47.33 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-blood_frenzy-spent.png` | 64x64 | RGBA | 0..191/165 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 31.83 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-guard-active.png` | 64x64 | RGBA | 0..255/173 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 35.70 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-guard-alert.png` | 64x64 | RGBA | 0..255/152 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 31.92 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-guard-ready.png` | 64x64 | RGBA | 0..255/209 | 3,4–61,60 | 3/4/3/4 | +0.0/+0.0 | 49.22 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-warrior-guard-spent.png` | 64x64 | RGBA | 0..191/146 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 26.10 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-attunement-active.png` | 64x64 | RGBA | 0..255/210 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 47.51 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-attunement-alert.png` | 64x64 | RGBA | 0..255/184 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 44.72 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-attunement-ready.png` | 64x64 | RGBA | 0..255/239 | 3,4–61,60 | 3/4/3/4 | +0.0/+0.0 | 51.54 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-attunement-spent.png` | 64x64 | RGBA | 0..191/173 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 38.05 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-court-active.png` | 64x64 | RGBA | 0..255/189 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 46.68 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-court-alert.png` | 64x64 | RGBA | 0..255/167 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 40.15 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-court-ready.png` | 64x64 | RGBA | 0..255/229 | 3,5–61,58 | 3/5/3/6 | +0.0/-0.5 | 54.70 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-court-spent.png` | 64x64 | RGBA | 0..191/152 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 33.33 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-runewaving-active.png` | 64x64 | RGBA | 0..255/146 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 44.07 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-runewaving-alert.png` | 64x64 | RGBA | 0..255/134 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 39.05 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-runewaving-ready.png` | 64x64 | RGBA | 0..255/203 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 54.05 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/mechanic-wizard-runewaving-spent.png` | 64x64 | RGBA | 0..191/120 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 31.05 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/metric-fill.png` | 156x10 | RGBA | none | 0,0–156,10 | 0/0/0/0 | +0.0/+0.0 | 51.30 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/metric-mini-fill.png` | 100x10 | RGBA | none | 0,0–100,10 | 0/0/0/0 | +0.0/+0.0 | 52.70 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/metric-mini-track.png` | 100x10 | RGBA | none | 0,0–100,10 | 0/0/0/0 | +0.0/+0.0 | 47.48 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/metric-track.png` | 156x10 | RGBA | none | 0,0–156,10 | 0/0/0/0 | +0.0/+0.0 | 46.95 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/popup-blue.png` | 300x72 | RGBA | 0..255/46 | 1,3–299,69 | 1/3/1/3 | +0.0/+0.0 | 16.12 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/popup-dark.png` | 300x72 | RGBA | 0..255/53 | 2,3–299,69 | 2/3/1/3 | +0.5/+0.0 | 15.28 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/popup-guest.png` | 300x72 | RGBA | 0..255/39 | 2,3–298,69 | 2/3/2/3 | +0.0/+0.0 | 13.66 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/popup-neutral.png` | 300x72 | RGBA | 0..255/47 | 1,3–299,69 | 1/3/1/3 | +0.0/+0.0 | 16.78 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/popup-red.png` | 300x72 | RGBA | 0..255/60 | 2,2–298,70 | 2/2/2/2 | +0.0/+0.0 | 14.58 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/resource-fill.png` | 328x10 | RGBA | none | 0,0–328,10 | 0/0/0/0 | +0.0/+0.0 | 59.88 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/resource-track.png` | 328x10 | RGBA | none | 0,0–328,10 | 0/0/0/0 | +0.0/+0.0 | 46.45 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-blood-locked.png` | 64x64 | RGBA | 0..255/2 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 21.45 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-blood-ready.png` | 64x64 | RGBA | 0..255/2 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 19.75 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-blood-regenerating.png` | 64x64 | RGBA | 0..255/2 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 23.39 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-blood-spent.png` | 64x64 | RGBA | 0..255/2 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 22.10 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-death-locked.png` | 64x64 | RGBA | 0..255/2 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 22.33 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-death-ready.png` | 64x64 | RGBA | 0..255/2 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 22.08 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-death-regenerating.png` | 64x64 | RGBA | 0..255/2 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 28.81 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-death-spent.png` | 64x64 | RGBA | 0..255/2 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 22.38 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-frost-locked.png` | 64x64 | RGBA | 0..255/2 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 25.38 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-frost-ready.png` | 64x64 | RGBA | 0..255/2 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 26.05 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-frost-regenerating.png` | 64x64 | RGBA | 0..255/2 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 29.57 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-frost-spent.png` | 64x64 | RGBA | 0..255/2 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 22.32 | 0 | PASS |  |
| `deploy/betterhud/assets/icesmp/rune-progress.png` | 48x6 | RGBA | none | 0,0–48,6 | 0/0/0/0 | +0.0/+0.0 | 88.11 | 0 | PASS |  |
| `deploy/betterhud/previews/icesmp-hud-concept.png` | 700x660 | RGBA | none | 0,0–700,660 | 0/0/0/0 | +0.0/+0.0 | 18.26 | 0 | PASS |  |
| `deploy/betterhud/previews/icesmp-hud-contact-sheet.png` | 700x660 | RGBA | none | 0,0–700,660 | 0/0/0/0 | +0.0/+0.0 | 18.14 | 0 | PASS |  |
| `deploy/betterhud/previews/icesmp-hud-full-audit.png` | 960x1260 | RGBA | none | 0,0–960,1260 | 0/0/0/0 | +0.0/+0.0 | 13.12 | 0 | PASS |  |
| `deploy/betterhud/previews/icesmp-hud-icons-source-v2.png` | 768x640 | RGBA | 0..255/2 | 37,37–728,602 | 37/37/40/38 | -1.5/-0.5 | 5.10 | 0 | PASS |  |
| `deploy/betterhud/previews/icesmp-hud-runtime-source.png` | 1560x1000 | RGBA | 0..255/2 | 85,54–1470,945 | 85/54/90/55 | -2.5/-0.5 | 14.66 | 0 | PASS |  |
| `deploy/betterhud/source/frame-guest-v2.png` | 204x126 | RGBA | 0..255/218 | 2,2–202,124 | 2/2/2/2 | +0.0/+0.0 | 35.72 | 0 | PASS |  |
| `deploy/betterhud/source/mechanics-core-v1.png` | 1254x1254 | RGBA | 0..255/9 | 47,41–1208,1207 | 47/41/46/47 | +0.5/-3.0 | 18.39 | 0 | PASS |  |
| `deploy/betterhud/source/mechanics-spec-v1.png` | 1254x1254 | RGBA | 0..255/9 | 24,48–1221,1217 | 24/48/33/37 | -4.5/+5.5 | 18.91 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/charge-ready.png` | 64x64 | RGBA | 0..255/33 | 17,17–47,47 | 17/17/17/17 | +0.0/+0.0 | 16.65 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/charge-spent.png` | 64x64 | RGBA | 0..255/33 | 17,17–47,47 | 17/17/17/17 | +0.0/+0.0 | 13.83 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-archer.png` | 64x64 | RGBA | 0..255/3 | 5,6–59,58 | 5/6/5/6 | +0.0/+0.0 | 22.60 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-assassin.png` | 64x64 | RGBA | 0..255/3 | 15,15–48,49 | 15/15/16/15 | -0.5/+0.0 | 13.37 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-death_knight.png` | 64x64 | RGBA | 0..255/3 | 17,14–47,49 | 17/14/17/15 | +0.0/-0.5 | 15.72 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-demon_hunter.png` | 64x64 | RGBA | 0..255/3 | 13,13–51,50 | 13/13/13/14 | +0.0/-0.5 | 15.50 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-druid.png` | 64x64 | RGBA | 0..255/3 | 17,14–47,50 | 17/14/17/14 | +0.0/+0.0 | 12.79 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-evoker.png` | 64x64 | RGBA | 0..255/3 | 5,5–59,59 | 5/5/5/5 | +0.0/+0.0 | 23.34 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-monk.png` | 64x64 | RGBA | 0..255/3 | 9,5–55,59 | 9/5/9/5 | +0.0/+0.0 | 25.18 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-none.png` | 64x64 | RGBA | 0..255/122 | 7,7–57,57 | 7/7/7/7 | +0.0/+0.0 | 46.21 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-paladin.png` | 64x64 | RGBA | 0..255/3 | 8,5–55,59 | 8/5/9/5 | -0.5/+0.0 | 31.68 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-priest.png` | 64x64 | RGBA | 0..255/3 | 17,13–47,51 | 17/13/17/13 | +0.0/+0.0 | 11.54 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-shaman.png` | 64x64 | RGBA | 0..255/3 | 11,5–53,58 | 11/5/11/6 | +0.0/-0.5 | 26.71 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-warlock.png` | 64x64 | RGBA | 0..255/3 | 15,14–49,49 | 15/14/15/15 | +0.0/-0.5 | 10.68 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-warrior.png` | 64x64 | RGBA | 0..255/3 | 6,5–57,59 | 6/5/7/5 | -0.5/+0.0 | 20.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/class-wizard.png` | 64x64 | RGBA | 0..255/3 | 5,8–59,55 | 5/8/5/9 | +0.0/-0.5 | 16.35 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/currency-blue.png` | 64x64 | RGBA | 0..255/62 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 45.52 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/currency-dark.png` | 64x64 | RGBA | 0..255/77 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 21.73 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/currency-neutral.png` | 64x64 | RGBA | 0..255/69 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 41.66 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/currency-red.png` | 64x64 | RGBA | 0..255/53 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 37.84 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/detail-strip.png` | 260x22 | RGBA | 0..255/60 | 0,0–259,21 | 0/0/1/1 | -0.5/-0.5 | 25.42 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/frame-hud-blue.png` | 260x160 | RGBA | 0..255/233 | 0,0–260,160 | 0/0/0/0 | +0.0/+0.0 | 33.18 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/frame-hud-dark.png` | 260x160 | RGBA | 0..255/253 | 5,0–254,160 | 5/0/6/0 | -0.5/+0.0 | 32.49 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/frame-hud-guest.png` | 260x160 | RGBA | 0..255/244 | 5,0–255,160 | 5/0/5/0 | +0.0/+0.0 | 32.96 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/frame-hud-neutral.png` | 260x160 | RGBA | 0..255/230 | 5,0–255,160 | 5/0/5/0 | +0.0/+0.0 | 33.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/frame-hud-red.png` | 260x160 | RGBA | 0..255/234 | 5,0–255,160 | 5/0/5/0 | +0.0/+0.0 | 32.05 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/icon-event.png` | 64x64 | RGBA | 0..255/3 | 9,7–54,57 | 9/7/10/7 | -0.5/+0.0 | 20.53 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/icon-level.png` | 64x64 | RGBA | 0..255/3 | 16,7–48,57 | 16/7/16/7 | +0.0/+0.0 | 18.05 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/icon-money.png` | 64x64 | RGBA | 0..255/3 | 8,7–55,57 | 8/7/9/7 | -0.5/+0.0 | 39.38 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-bond-active.png` | 64x64 | RGBA | 0..255/179 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 35.32 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-bond-alert.png` | 64x64 | RGBA | 0..255/159 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 31.51 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-bond-ready.png` | 64x64 | RGBA | 0..255/225 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 46.67 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-bond-spent.png` | 64x64 | RGBA | 0..191/150 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 25.84 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-precision_chain-active.png` | 64x64 | RGBA | 0..255/198 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 23.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-precision_chain-alert.png` | 64x64 | RGBA | 0..255/175 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 22.10 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-precision_chain-ready.png` | 64x64 | RGBA | 0..255/235 | 3,8–61,55 | 3/8/3/9 | +0.0/-0.5 | 35.27 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-precision_chain-spent.png` | 64x64 | RGBA | 0..191/158 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 17.87 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-wind_read-active.png` | 64x64 | RGBA | 0..255/228 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 51.30 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-wind_read-alert.png` | 64x64 | RGBA | 0..255/201 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 49.79 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-wind_read-ready.png` | 64x64 | RGBA | 0..255/248 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 54.79 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-archer-wind_read-spent.png` | 64x64 | RGBA | 0..191/178 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 38.57 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-detection-active.png` | 64x64 | RGBA | 0..255/198 | 6,12–58,51 | 6/12/6/13 | +0.0/-0.5 | 36.26 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-detection-alert.png` | 64x64 | RGBA | 0..255/176 | 6,12–58,51 | 6/12/6/13 | +0.0/-0.5 | 32.71 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-detection-ready.png` | 64x64 | RGBA | 0..255/228 | 3,9–61,54 | 3/9/3/10 | +0.0/-0.5 | 34.46 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-detection-spent.png` | 64x64 | RGBA | 0..191/156 | 6,12–58,51 | 6/12/6/13 | +0.0/-0.5 | 27.90 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-infection-active.png` | 64x64 | RGBA | 0..255/156 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 38.19 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-infection-alert.png` | 64x64 | RGBA | 0..255/141 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 33.69 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-infection-ready.png` | 64x64 | RGBA | 0..255/208 | 4,5–61,58 | 4/5/3/6 | +0.5/-0.5 | 45.38 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-infection-spent.png` | 64x64 | RGBA | 0..191/133 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 28.75 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-opening-active.png` | 64x64 | RGBA | 0..255/166 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 25.37 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-opening-alert.png` | 64x64 | RGBA | 0..255/147 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 23.74 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-opening-ready.png` | 64x64 | RGBA | 0..255/217 | 4,3–59,61 | 4/3/5/3 | -0.5/+0.0 | 35.01 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-opening-spent.png` | 64x64 | RGBA | 0..191/135 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 17.86 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-toxin-active.png` | 64x64 | RGBA | 0..255/153 | 19,6–44,58 | 19/6/20/6 | -0.5/+0.0 | 26.47 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-toxin-alert.png` | 64x64 | RGBA | 0..255/139 | 19,6–44,58 | 19/6/20/6 | -0.5/+0.0 | 24.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-toxin-ready.png` | 64x64 | RGBA | 0..255/205 | 16,3–47,61 | 16/3/17/3 | -0.5/+0.0 | 35.82 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-assassin-toxin-spent.png` | 64x64 | RGBA | 0..191/132 | 19,6–44,58 | 19/6/20/6 | -0.5/+0.0 | 19.96 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-blood_memory-active.png` | 64x64 | RGBA | 0..255/181 | 11,6–53,58 | 11/6/11/6 | +0.0/+0.0 | 33.53 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-blood_memory-alert.png` | 64x64 | RGBA | 0..255/158 | 11,6–53,58 | 11/6/11/6 | +0.0/+0.0 | 30.41 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-blood_memory-ready.png` | 64x64 | RGBA | 0..255/226 | 8,3–56,61 | 8/3/8/3 | +0.0/+0.0 | 44.76 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-blood_memory-spent.png` | 64x64 | RGBA | 0..191/148 | 11,6–53,58 | 11/6/11/6 | +0.0/+0.0 | 24.26 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-frost_marks-active.png` | 64x64 | RGBA | 0..255/194 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 39.98 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-frost_marks-alert.png` | 64x64 | RGBA | 0..255/171 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 35.70 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-frost_marks-ready.png` | 64x64 | RGBA | 0..255/227 | 7,3–56,61 | 7/3/8/3 | -0.5/+0.0 | 51.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-frost_marks-spent.png` | 64x64 | RGBA | 0..191/156 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 29.51 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-plague-active.png` | 64x64 | RGBA | 0..255/202 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 42.61 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-plague-alert.png` | 64x64 | RGBA | 0..255/178 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 38.66 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-plague-ready.png` | 64x64 | RGBA | 0..255/235 | 3,6–61,58 | 3/6/3/6 | +0.0/+0.0 | 50.48 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-plague-spent.png` | 64x64 | RGBA | 0..191/165 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 32.52 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-rune_wheel-active.png` | 64x64 | RGBA | 0..255/161 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 38.25 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-rune_wheel-alert.png` | 64x64 | RGBA | 0..255/141 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 33.95 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-rune_wheel-ready.png` | 64x64 | RGBA | 0..255/207 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 48.67 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-death_knight-rune_wheel-spent.png` | 64x64 | RGBA | 0..191/135 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 26.36 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-fragments-active.png` | 64x64 | RGBA | 0..255/149 | 16,6–47,58 | 16/6/17/6 | -0.5/+0.0 | 29.56 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-fragments-alert.png` | 64x64 | RGBA | 0..255/138 | 16,6–47,58 | 16/6/17/6 | -0.5/+0.0 | 26.92 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-fragments-ready.png` | 64x64 | RGBA | 0..255/201 | 13,3–50,61 | 13/3/14/3 | -0.5/+0.0 | 37.58 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-fragments-spent.png` | 64x64 | RGBA | 0..191/126 | 16,6–47,58 | 16/6/17/6 | -0.5/+0.0 | 23.19 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-load-active.png` | 64x64 | RGBA | 0..255/170 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 40.19 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-load-alert.png` | 64x64 | RGBA | 0..255/150 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 36.64 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-load-ready.png` | 64x64 | RGBA | 0..255/216 | 4,3–59,61 | 4/3/5/3 | -0.5/+0.0 | 52.17 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-load-spent.png` | 64x64 | RGBA | 0..191/140 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 28.23 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-pain-active.png` | 64x64 | RGBA | 0..255/205 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 39.15 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-pain-alert.png` | 64x64 | RGBA | 0..255/181 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 34.70 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-pain-ready.png` | 64x64 | RGBA | 0..255/238 | 6,3–58,61 | 6/3/6/3 | +0.0/+0.0 | 47.83 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-pain-spent.png` | 64x64 | RGBA | 0..191/164 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 28.70 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-sigil-active.png` | 64x64 | RGBA | 0..255/189 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 45.15 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-sigil-alert.png` | 64x64 | RGBA | 0..255/167 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 43.31 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-sigil-ready.png` | 64x64 | RGBA | 0..255/232 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 42.11 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-demon_hunter-sigil-spent.png` | 64x64 | RGBA | 0..191/153 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 37.50 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-balance-active.png` | 64x64 | RGBA | 0..255/229 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 48.00 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-balance-alert.png` | 64x64 | RGBA | 0..255/203 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 46.56 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-balance-ready.png` | 64x64 | RGBA | 0..255/247 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 53.10 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-balance-spent.png` | 64x64 | RGBA | 0..191/176 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 38.51 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-bark-active.png` | 64x64 | RGBA | 0..255/172 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 35.41 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-bark-alert.png` | 64x64 | RGBA | 0..255/153 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 31.39 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-bark-ready.png` | 64x64 | RGBA | 0..255/218 | 7,3–56,61 | 7/3/8/3 | -0.5/+0.0 | 47.21 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-bark-spent.png` | 64x64 | RGBA | 0..191/147 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 24.97 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-combo-active.png` | 64x64 | RGBA | 0..255/218 | 12,6–51,58 | 12/6/13/6 | -0.5/+0.0 | 39.39 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-combo-alert.png` | 64x64 | RGBA | 0..255/193 | 12,6–51,58 | 12/6/13/6 | -0.5/+0.0 | 38.63 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-combo-ready.png` | 64x64 | RGBA | 0..255/244 | 9,3–54,61 | 9/3/10/3 | -0.5/+0.0 | 48.31 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-combo-spent.png` | 64x64 | RGBA | 0..191/174 | 12,6–51,58 | 12/6/13/6 | -0.5/+0.0 | 32.30 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-harmony-active.png` | 64x64 | RGBA | 0..255/185 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 48.45 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-harmony-alert.png` | 64x64 | RGBA | 0..255/165 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 42.57 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-harmony-ready.png` | 64x64 | RGBA | 0..255/218 | 3,4–61,60 | 3/4/3/4 | +0.0/+0.0 | 58.66 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-harmony-spent.png` | 64x64 | RGBA | 0..191/156 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 34.28 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-seeds-active.png` | 64x64 | RGBA | 0..255/147 | 19,6–45,58 | 19/6/19/6 | +0.0/+0.0 | 24.59 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-seeds-alert.png` | 64x64 | RGBA | 0..255/136 | 19,6–45,58 | 19/6/19/6 | +0.0/+0.0 | 22.36 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-seeds-ready.png` | 64x64 | RGBA | 0..255/206 | 16,3–48,61 | 16/3/16/3 | +0.0/+0.0 | 33.31 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-druid-seeds-spent.png` | 64x64 | RGBA | 0..191/125 | 19,6–45,58 | 19/6/19/6 | +0.0/+0.0 | 18.70 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-empower-active.png` | 64x64 | RGBA | 0..255/186 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 40.62 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-empower-alert.png` | 64x64 | RGBA | 0..255/161 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 36.38 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-empower-ready.png` | 64x64 | RGBA | 0..255/234 | 4,3–60,60 | 4/3/4/4 | +0.0/-0.5 | 49.23 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-empower-spent.png` | 64x64 | RGBA | 0..191/153 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 28.41 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-imprint-active.png` | 64x64 | RGBA | 0..255/221 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 54.57 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-imprint-alert.png` | 64x64 | RGBA | 0..255/198 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 49.90 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-imprint-ready.png` | 64x64 | RGBA | 0..255/245 | 5,3–59,61 | 5/3/5/3 | +0.0/+0.0 | 53.69 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-imprint-spent.png` | 64x64 | RGBA | 0..191/173 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 42.08 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-resonance-active.png` | 64x64 | RGBA | 0..255/227 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 47.85 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-resonance-alert.png` | 64x64 | RGBA | 0..255/200 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 44.94 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-resonance-ready.png` | 64x64 | RGBA | 0..255/249 | 4,4–61,60 | 4/4/3/4 | +0.5/+0.0 | 56.72 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-evoker-resonance-spent.png` | 64x64 | RGBA | 0..191/178 | 7,7–58,57 | 7/7/6/7 | +0.5/+0.0 | 38.29 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-combo_chain-active.png` | 64x64 | RGBA | 0..255/163 | 6,19–58,44 | 6/19/6/20 | +0.0/-0.5 | 24.19 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-combo_chain-alert.png` | 64x64 | RGBA | 0..255/145 | 6,19–58,44 | 6/19/6/20 | +0.0/-0.5 | 22.99 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-combo_chain-ready.png` | 64x64 | RGBA | 0..255/214 | 3,16–61,47 | 3/16/3/17 | +0.0/-0.5 | 38.73 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-combo_chain-spent.png` | 64x64 | RGBA | 0..191/135 | 6,19–58,44 | 6/19/6/20 | +0.0/-0.5 | 17.60 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-flow-active.png` | 64x64 | RGBA | 0..255/171 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 49.38 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-flow-alert.png` | 64x64 | RGBA | 0..255/154 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 44.36 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-flow-ready.png` | 64x64 | RGBA | 0..255/226 | 3,3–61,61 | 3/3/3/3 | +0.0/+0.0 | 55.18 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-flow-spent.png` | 64x64 | RGBA | 0..191/143 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 36.41 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-mist_threads-active.png` | 64x64 | RGBA | 0..255/200 | 17,6–47,58 | 17/6/17/6 | +0.0/+0.0 | 34.89 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-mist_threads-alert.png` | 64x64 | RGBA | 0..255/178 | 17,6–47,58 | 17/6/17/6 | +0.0/+0.0 | 33.20 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-mist_threads-ready.png` | 64x64 | RGBA | 0..255/238 | 14,4–50,61 | 14/4/14/3 | +0.0/+0.5 | 26.77 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-mist_threads-spent.png` | 64x64 | RGBA | 0..191/161 | 17,6–47,58 | 17/6/17/6 | +0.0/+0.0 | 29.19 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-stagger-active.png` | 64x64 | RGBA | 0..255/205 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 39.80 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-stagger-alert.png` | 64x64 | RGBA | 0..255/185 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 36.34 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-stagger-ready.png` | 64x64 | RGBA | 0..255/237 | 3,5–61,59 | 3/5/3/5 | +0.0/+0.0 | 45.93 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-monk-stagger-spent.png` | 64x64 | RGBA | 0..191/165 | 6,8–58,56 | 6/8/6/8 | +0.0/+0.0 | 29.96 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-beacon-active.png` | 64x64 | RGBA | 0..255/234 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 51.68 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-beacon-alert.png` | 64x64 | RGBA | 0..255/204 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 45.58 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-beacon-ready.png` | 64x64 | RGBA | 0..255/249 | 5,3–59,61 | 5/3/5/3 | +0.0/+0.0 | 49.10 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-beacon-spent.png` | 64x64 | RGBA | 0..191/177 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 36.89 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-conviction-active.png` | 64x64 | RGBA | 0..255/152 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 43.43 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-conviction-alert.png` | 64x64 | RGBA | 0..255/138 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 39.12 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-conviction-ready.png` | 64x64 | RGBA | 0..255/206 | 5,3–60,61 | 5/3/4/3 | +0.5/+0.0 | 51.27 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-conviction-spent.png` | 64x64 | RGBA | 0..191/128 | 7,6–57,58 | 7/6/7/6 | +0.0/+0.0 | 30.17 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-judgement_marks-active.png` | 64x64 | RGBA | 0..255/154 | 6,17–58,47 | 6/17/6/17 | +0.0/+0.0 | 29.92 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-judgement_marks-alert.png` | 64x64 | RGBA | 0..255/139 | 6,17–58,47 | 6/17/6/17 | +0.0/+0.0 | 28.29 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-judgement_marks-ready.png` | 64x64 | RGBA | 0..255/204 | 3,14–61,50 | 3/14/3/14 | +0.0/+0.0 | 37.90 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-judgement_marks-spent.png` | 64x64 | RGBA | 0..191/129 | 6,17–58,47 | 6/17/6/17 | +0.0/+0.0 | 23.30 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-shield_charge-active.png` | 64x64 | RGBA | 0..255/183 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 44.04 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-shield_charge-alert.png` | 64x64 | RGBA | 0..255/160 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 40.21 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-shield_charge-ready.png` | 64x64 | RGBA | 0..255/228 | 3,5–61,58 | 3/5/3/6 | +0.0/-0.5 | 41.80 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-paladin-shield_charge-spent.png` | 64x64 | RGBA | 0..191/146 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 34.60 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-litany-active.png` | 64x64 | RGBA | 0..255/155 | 6,7–58,56 | 6/7/6/8 | +0.0/-0.5 | 37.95 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-litany-alert.png` | 64x64 | RGBA | 0..255/135 | 6,7–58,56 | 6/7/6/8 | +0.0/-0.5 | 32.76 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-litany-ready.png` | 64x64 | RGBA | 0..255/208 | 3,4–61,59 | 3/4/3/5 | +0.0/-0.5 | 47.28 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-litany-spent.png` | 64x64 | RGBA | 0..191/130 | 6,7–58,56 | 6/7/6/8 | +0.0/-0.5 | 24.98 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-madness-active.png` | 64x64 | RGBA | 0..255/185 | 8,6–55,58 | 8/6/9/6 | -0.5/+0.0 | 38.59 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-madness-alert.png` | 64x64 | RGBA | 0..255/161 | 8,6–55,58 | 8/6/9/6 | -0.5/+0.0 | 35.23 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-madness-ready.png` | 64x64 | RGBA | 0..255/228 | 5,3–58,61 | 5/3/6/3 | -0.5/+0.0 | 45.24 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-madness-spent.png` | 64x64 | RGBA | 0..191/154 | 8,6–55,58 | 8/6/9/6 | -0.5/+0.0 | 29.55 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-marrow-active.png` | 64x64 | RGBA | 0..255/171 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 41.57 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-marrow-alert.png` | 64x64 | RGBA | 0..255/150 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 37.88 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-marrow-ready.png` | 64x64 | RGBA | 0..255/219 | 6,3–57,61 | 6/3/7/3 | -0.5/+0.0 | 52.40 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-marrow-spent.png` | 64x64 | RGBA | 0..191/137 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 31.41 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-shield_web-active.png` | 64x64 | RGBA | 0..255/212 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 40.55 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-shield_web-alert.png` | 64x64 | RGBA | 0..255/187 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 37.37 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-shield_web-ready.png` | 64x64 | RGBA | 0..255/240 | 3,8–61,55 | 3/8/3/9 | +0.0/-0.5 | 52.11 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-priest-shield_web-spent.png` | 64x64 | RGBA | 0..191/166 | 6,11–58,52 | 6/11/6/12 | +0.0/-0.5 | 29.04 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-maelstrom-active.png` | 64x64 | RGBA | 0..255/229 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 52.30 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-maelstrom-alert.png` | 64x64 | RGBA | 0..255/203 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 48.33 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-maelstrom-ready.png` | 64x64 | RGBA | 0..255/249 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 61.51 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-maelstrom-spent.png` | 64x64 | RGBA | 0..191/177 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 40.69 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-resonance-active.png` | 64x64 | RGBA | 0..255/210 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 49.32 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-resonance-alert.png` | 64x64 | RGBA | 0..255/182 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 45.58 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-resonance-ready.png` | 64x64 | RGBA | 0..255/243 | 3,3–61,61 | 3/3/3/3 | +0.0/+0.0 | 46.58 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-resonance-spent.png` | 64x64 | RGBA | 0..191/162 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 39.17 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-tide-active.png` | 64x64 | RGBA | 0..255/225 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 47.11 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-tide-alert.png` | 64x64 | RGBA | 0..255/200 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 42.87 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-tide-ready.png` | 64x64 | RGBA | 0..255/237 | 3,6–61,58 | 3/6/3/6 | +0.0/+0.0 | 52.82 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-tide-spent.png` | 64x64 | RGBA | 0..191/171 | 6,9–58,55 | 6/9/6/9 | +0.0/+0.0 | 34.07 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-totem_wheel-active.png` | 64x64 | RGBA | 0..255/196 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 41.33 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-totem_wheel-alert.png` | 64x64 | RGBA | 0..255/174 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 38.16 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-totem_wheel-ready.png` | 64x64 | RGBA | 0..255/226 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 52.49 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-shaman-totem_wheel-spent.png` | 64x64 | RGBA | 0..191/161 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 29.16 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-curses-active.png` | 64x64 | RGBA | 0..255/197 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 42.74 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-curses-alert.png` | 64x64 | RGBA | 0..255/176 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 38.93 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-curses-ready.png` | 64x64 | RGBA | 0..255/228 | 3,3–61,61 | 3/3/3/3 | +0.0/+0.0 | 54.42 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-curses-spent.png` | 64x64 | RGBA | 0..191/156 | 6,6–58,58 | 6/6/6/6 | +0.0/+0.0 | 31.46 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-demons-active.png` | 64x64 | RGBA | 0..255/147 | 17,6–46,58 | 17/6/18/6 | -0.5/+0.0 | 28.93 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-demons-alert.png` | 64x64 | RGBA | 0..255/136 | 17,6–46,58 | 17/6/18/6 | -0.5/+0.0 | 26.83 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-demons-ready.png` | 64x64 | RGBA | 0..255/206 | 14,3–49,61 | 14/3/15/3 | -0.5/+0.0 | 37.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-demons-spent.png` | 64x64 | RGBA | 0..191/124 | 17,6–46,58 | 17/6/18/6 | -0.5/+0.0 | 22.80 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-embers-active.png` | 64x64 | RGBA | 0..255/213 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 39.42 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-embers-alert.png` | 64x64 | RGBA | 0..255/189 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 36.30 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-embers-ready.png` | 64x64 | RGBA | 0..255/246 | 7,3–56,61 | 7/3/8/3 | -0.5/+0.0 | 49.69 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-embers-spent.png` | 64x64 | RGBA | 0..191/170 | 10,6–53,58 | 10/6/11/6 | -0.5/+0.0 | 31.08 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-soul_debt-active.png` | 64x64 | RGBA | 0..255/196 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 35.62 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-soul_debt-alert.png` | 64x64 | RGBA | 0..255/170 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 31.72 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-soul_debt-ready.png` | 64x64 | RGBA | 0..255/231 | 6,3–58,61 | 6/3/6/3 | +0.0/+0.0 | 47.46 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warlock-soul_debt-spent.png` | 64x64 | RGBA | 0..191/163 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 24.47 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-battle_tempo-active.png` | 64x64 | RGBA | 0..255/243 | 6,10–58,54 | 6/10/6/10 | +0.0/+0.0 | 47.73 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-battle_tempo-alert.png` | 64x64 | RGBA | 0..255/213 | 6,10–58,54 | 6/10/6/10 | +0.0/+0.0 | 45.78 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-battle_tempo-ready.png` | 64x64 | RGBA | 0..255/254 | 3,7–61,57 | 3/7/3/7 | +0.0/+0.0 | 56.47 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-battle_tempo-spent.png` | 64x64 | RGBA | 0..191/185 | 6,10–58,54 | 6/10/6/10 | +0.0/+0.0 | 34.79 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-blood_frenzy-active.png` | 64x64 | RGBA | 0..255/201 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 39.02 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-blood_frenzy-alert.png` | 64x64 | RGBA | 0..255/179 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 36.97 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-blood_frenzy-ready.png` | 64x64 | RGBA | 0..255/239 | 4,3–59,61 | 4/3/5/3 | -0.5/+0.0 | 45.33 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-blood_frenzy-spent.png` | 64x64 | RGBA | 0..191/165 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 31.89 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-guard-active.png` | 64x64 | RGBA | 0..255/173 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 35.77 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-guard-alert.png` | 64x64 | RGBA | 0..255/152 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 31.98 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-guard-ready.png` | 64x64 | RGBA | 0..255/209 | 3,4–61,60 | 3/4/3/4 | +0.0/+0.0 | 47.22 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-warrior-guard-spent.png` | 64x64 | RGBA | 0..191/146 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 26.16 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-attunement-active.png` | 64x64 | RGBA | 0..255/210 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 47.57 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-attunement-alert.png` | 64x64 | RGBA | 0..255/184 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 44.78 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-attunement-ready.png` | 64x64 | RGBA | 0..255/239 | 3,4–61,60 | 3/4/3/4 | +0.0/+0.0 | 49.54 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-attunement-spent.png` | 64x64 | RGBA | 0..191/173 | 6,7–58,57 | 6/7/6/7 | +0.0/+0.0 | 38.12 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-court-active.png` | 64x64 | RGBA | 0..255/189 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 46.74 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-court-alert.png` | 64x64 | RGBA | 0..255/167 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 40.21 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-court-ready.png` | 64x64 | RGBA | 0..255/229 | 3,5–61,58 | 3/5/3/6 | +0.0/-0.5 | 52.70 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-court-spent.png` | 64x64 | RGBA | 0..191/152 | 6,8–58,55 | 6/8/6/9 | +0.0/-0.5 | 33.39 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-runewaving-active.png` | 64x64 | RGBA | 0..255/146 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 44.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-runewaving-alert.png` | 64x64 | RGBA | 0..255/134 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 39.11 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-runewaving-ready.png` | 64x64 | RGBA | 0..255/203 | 3,3–60,61 | 3/3/4/3 | -0.5/+0.0 | 52.05 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/mechanic-wizard-runewaving-spent.png` | 64x64 | RGBA | 0..191/120 | 6,6–57,58 | 6/6/7/6 | -0.5/+0.0 | 31.11 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-blood-locked.png` | 64x64 | RGBA | 0..255/3 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 21.51 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-blood-ready.png` | 64x64 | RGBA | 0..255/3 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 19.82 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-blood-regenerating.png` | 64x64 | RGBA | 0..255/3 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 23.45 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-blood-spent.png` | 64x64 | RGBA | 0..255/3 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 22.16 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-death-locked.png` | 64x64 | RGBA | 0..255/3 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 22.39 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-death-ready.png` | 64x64 | RGBA | 0..255/3 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 22.14 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-death-regenerating.png` | 64x64 | RGBA | 0..255/3 | 7,6–56,58 | 7/6/8/6 | -0.5/+0.0 | 28.88 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-death-spent.png` | 64x64 | RGBA | 0..255/3 | 9,6–55,58 | 9/6/9/6 | +0.0/+0.0 | 22.44 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-frost-locked.png` | 64x64 | RGBA | 0..255/3 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 25.44 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-frost-ready.png` | 64x64 | RGBA | 0..255/3 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 26.11 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-frost-regenerating.png` | 64x64 | RGBA | 0..255/3 | 8,6–56,58 | 8/6/8/6 | +0.0/+0.0 | 29.63 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/rune-frost-spent.png` | 64x64 | RGBA | 0..255/3 | 9,6–54,58 | 9/6/10/6 | -0.5/+0.0 | 22.38 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/segment-fill-gold.png` | 12x5 | RGBA | 0..255/2 | 0,0–12,5 | 0/0/0/0 | +0.0/+0.0 | 62.83 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/segment-fill-warm.png` | 12x5 | RGBA | 0..255/2 | 0,0–12,5 | 0/0/0/0 | +0.0/+0.0 | 57.53 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/segment-fill.png` | 12x5 | RGBA | 0..255/2 | 0,0–12,5 | 0/0/0/0 | +0.0/+0.0 | 87.77 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/segment-track.png` | 12x5 | RGBA | 0..255/2 | 0,0–12,5 | 0/0/0/0 | +0.0/+0.0 | 14.13 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/text-atlas.png` | 128x96 | RGBA | 0..255/254 | 0,0–127,93 | 0/0/1/3 | -0.5/-1.5 | 56.36 | 0 | PASS |  |
| `resource-pack/assets/icesmp_hud/textures/hud/wallet-strip.png` | 260x22 | RGBA | 0..255/74 | 0,0–259,21 | 0/0/1/1 | -0.5/-0.5 | 45.83 | 0 | PASS |  |

## Klienses kézi minimum

- [ ] Mind az öt téma, külön Menedék-vendég külső héjjal; a belső rács nem mozdul.
- [ ] Mind a 13 class: primary és minden elérhető secondary mechanika ikonja, üres/aktív/ready/alert/spent állapot.
- [ ] DK rúnák: ready, spent, regenerating százalék és locked; más classok typed charge/stack pipjei.
- [ ] Wallet: nulla primary és pozitív idegen valuták; event nyugalmi/aktív; class-level.
- [ ] Class- és frakcióváltás közben nincs geometria- vagy glyph-width ugrás.
- [ ] Pack elfogadás/elutasítás/hiba; BetterHud jelen/nincs; pontosan egy HUD és működő compact fallback.
- [ ] GUI scale 1–4, legalább 1280×720 és 2560×1440; nincs vágás, magenta fringe vagy hibás pixel.
