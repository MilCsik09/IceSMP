# Textúra-munkalap — a hiányzó item-textúrák

<!-- icesmp-doc-id: feature.resource-pack.worksheet -->

Ez a **munkalap**: kész, bemásolható promptok a még le nem gyártott item-textúrákhoz.
A per-tárgy kanonikus leírás (Ábrázolás / Színvilág / lore) a
[`RESOURCE_PACK_CMD.md`](RESOURCE_PACK_CMD.md) manifestben él — ez a fájl abból
generálódik, és csak azt sorolja, amihez MÉG NINCS PNG.

## Egyeztetés

| | db |
|---|---:|
| Bekötött `item-model` (egyedi + ritkaság recept) | **85** |
| Ebből már van PNG a packban | 1 |
| **Legyártandó textúra** | **84** |
| Lapok száma (5 tárgy / lap) | 17 |

Már kész, ezért NEM szerepel a lapokon: `bokic_gyogytea` — a grafikája a recept újraírása előttről
származik, és a manifest leírása a meglévő PNG-t rögzíti.

> A számot a `scripts/check_consistency.py` is figyeli: WARN-ol, ha egy deklarált
> `ITEM_MODEL`-hez nincs PNG a packban. Amikor nullára csökken, minden tárgy a saját
> grafikáját viseli.

## Mielőtt generálsz

- **64×64 px, átlátszó háttér.** A csomagolt pack mind a 304 item-textúrája ilyen — vegyes felbontás nem kerülhet be.
- **Lapokban kérd, ne egyesével.** Öt tárgy egy lapon: így tartja a stílust a generátor.
- **Fehér háttér a generáláskor**, alfára vágás utólag. Félig átlátszó élpixel tilos — a játékban csúnya szegély lesz belőle.
- **Átméretezés PONTOSAN 64×64-re, nearest-neighbour.** Bilineáris skálázás elmossa a pixeleket.
- **Ha festményt ad** a generátor: told meg a `chunky pixels, visible square pixels, retro game sprite` kifejezésekkel.
- **Ellenőrizd 16×16-ra kicsinyítve.** A 4 px-nél vékonyabb részlet az inventoryban eltűnik.
- **Fájlnév = modell-id.** A kész PNG helye: `assets/icesmp/textures/item/<id>.png`.

## Lapok

### 1. lap — Bűvölő

Fájlnevek: `tuzvedelem_tomus.png`, `izeltlabu_tomus.png`, `robbanasvedelem_tomus.png`, `vizi_ugyesseg_tomus.png`, `lovedekvedelem_tomus.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. an enchanted tome seen from the side showing a shield rune among flame tongues, deep red leather with gold fittings
2. an enchanted tome seen from the side showing an eight-legged rune struck through, faded purple binding
3. an enchanted tome seen from the side showing a cracked stone shield rune with shard glyphs, sooty grey binding
4. an enchanted tome seen from the side showing a wave rune crossing a pickaxe silhouette, sea blue binding
5. an enchanted tome seen from the side showing a slanted arrow rune breaking on a shield, greenish leather with copper

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 2. lap — Bűvölő

Fájlnevek: `legzes_tomus.png`, `lokes_tomus.png`, `sokloves_tomus.png`, `tovis_tomus.png`, `lang_tomus.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. an enchanted tome seen from the side showing a bubble-chain rune in a shell arc, pearl white binding
2. an enchanted tome seen from the side showing an outward shockwave rune, grey-brown binding
3. an enchanted tome seen from the side showing three fanning arrow runes, beige binding with feather fittings
4. an enchanted tome seen from the side showing a thorn-crown rune with inward spikes, dark green binding
5. an enchanted tome seen from the side showing an arrow rune with a burning tip, warm red-brown binding

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 3. lap — Bűvölő

Fájlnevek: `melyseglepte_tomus.png`, `fagyjaro_tomus.png`, `dofes_tomus.png`, `lelekfutas_tomus.png`, `husegeskuu_tomus.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. an enchanted tome seen from the side showing a footprint rune beneath wave lines, deep blue binding with net-cord lacing
2. an enchanted tome seen from the side showing a six-point ice crystal rune under a sole, icy blue binding
3. an enchanted tome seen from the side showing a downward-stabbing trident rune, prismarine turquoise binding
4. an enchanted tome seen from the side showing a running flame rune above soul sand grains, sandy binding with cold turquoise soul-fire
5. an enchanted tome seen from the side showing a trident rune with a returning arc, pearl binding with a gold thread

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 4. lap — Bűvölő

Fájlnevek: `sunnyogas_tomus.png`, `szigonyvihar_tomus.png`, `surites_tomus.png`, `szellokitores_tomus.png`, `vegtelen_tomus.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. an enchanted tome seen from the side showing a crouching figure rune among sculk veins, pitch black binding with turquoise glow
2. an enchanted tome seen from the side showing a vortex rune with a trident tip at its centre, storm grey binding
3. an enchanted tome seen from the side showing a downward-pressing mace head rune above an impact ring, heavy iron grey binding
4. an enchanted tome seen from the side showing an outward-bursting wind rune in a spiral, pale grey-blue binding
5. an enchanted tome seen from the side showing an infinity loop rune formed from an arrow, deep purple binding with ender glow

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 5. lap — Bűvölő, Alkimista

Fájlnevek: `villamhivo_tomus.png`, `kezdo_fozet.png`, `gyogyito_fozet.png`, `ero_fozet.png`, `sebesseg_fozet.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. an enchanted tome seen from the side showing a zigzag lightning rune inside a copper ring, patina green binding
2. a simple glass flask of pale pink brew with a crumpled cork
3. a round flask of bright red brew with a glistering melon glint through the glass
4. a stout flask of dark red brew with blaze powder sediment at the base
5. a slender flask of light blue brew with streaking lines in the liquid

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 6. lap — Alkimista

Fájlnevek: `tuzallosag_fozet.png`, `ejjellatas_fozet.png`, `vizlegzes_fozet.png`, `dobo_gyogyito_fozet.png`, `lathatatlansag_fozet.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a stout flask of orange brew with a magma cream globule at the bottom
2. a slender flask of deep night blue brew with tiny star sparks
3. a slender flask of sea blue liquid with a chain of small bubbles rising
4. a round throwing flask of bright red brew, long neck, grey powder band
5. a slender flask of barely visible smoky grey liquid, its outline faint

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 7. lap — Alkimista

Fájlnevek: `eszencias_fozet.png`, `lebego_fozet.png`, `kivonatos_regeneracio_fozet.png`, `dobo_ero_fozet.png`, `dobo_sebesseg_fozet.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a squat flask of brown-red brew with swirling beast essence
2. a slender flask of pale whitish liquid with a floating phantom membrane scrap
3. a flask of pink-purple brew with an herb leaf tied to the glass
4. a round throwing flask of red liquid with a long neck and a grey powder band
5. a round throwing flask of light blue liquid with sugar crystals at the base

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 8. lap — Alkimista

Fájlnevek: `runasav_bomba.png`, `dobo_tuzallosag_fozet.png`, `dobo_ejjellatas_fozet.png`, `sietseg_fozet.png`, `dobo_lathatatlansag_fozet.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a round throwing flask of caustic purple liquid with rune scratches on the glass
2. a round throwing flask of orange liquid with bubbling magma cream
3. a round throwing flask of dark blue liquid with a small golden carrot silhouette inside
4. a slender flask of honey yellow liquid with swirling red redstone specks
5. a round throwing flask of pale nearly translucent grey liquid

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 9. lap — Alkimista

Fájlnevek: `elnyulo_regeneracio_fozet.png`, `elnyulo_gyogyito_fozet.png`, `hosszu_erofozet.png`, `elnyulo_tuzallosag_fozet.png`, `vasbor_fozet.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a wide-based lingering flask of pink-purple brew with spreading mist below
2. a wide-based lingering flask of pink-red liquid with a spreading mist at the bottom
3. a wide-based lingering flask of dark red brew with heavy red mist below
4. a wide-based lingering flask of orange liquid with warm creeping mist below
5. a squat glass flask of grey metallic-sheened liquid with an iron stopper

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 10. lap — Alkimista, Gyógynövényes

Fájlnevek: `dobo_vasbor_fozet.png`, `elnyulo_sebesseg_fozet.png`, `sarkanyver_fozet.png`, `gyogyfuves_kotes.png`, `taplalo_pep.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a round throwing flask of pewter-grey liquid with settling iron granules
2. a wide-based lingering flask of sky blue liquid with drifting mist below
3. a slender flask of deep red viscous liquid with a small bone shard at its base
4. a rolled white linen bandage with green herb leaves tucked into the fold
5. a thick slice of brown bread spread with honey, dark peat crumbs on top

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 11. lap — Gyógynövényes

Fájlnevek: `meregvono_pep.png`, `laposfoldi_kenocs.png`, `vizi_fu_fozet.png`, `eronlet_kenocs.png`, `vandor_labkenocs.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a small clay bowl of thick grey-brown paste with a mushroom cap on top and a faint green vapour wisp
2. a flat shell dish of grey-green sticky salve with a single reed laid across it
3. a slender glass jug of light green brew with strands of seagrass floating inside
4. an open flat copper tin filled with golden beeswax salve, honeycomb pattern on its surface
5. a small leather-wrapped jar tied with cloth, pale grey ointment peeking out

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 12. lap — Gyógynövényes

Fájlnevek: `szerencsefu_tea.png`, `jegviragos_borogatas.png`, `arnygomba_fozet.png`, `hosnapi_fozet.png`, `sarkanylehelet_parlat.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a clay cup of steaming golden tea with a four-leaf clover resting on the rim
2. a folded pale blue cloth compress with frosted ice-flower petals and tiny ice crystals
3. a squat glass vial of dark grey mushroom brew with a cork, pale mushroom silhouette visible through the glass
4. a festive glass goblet of deep green drink with a green ribbon and a tiny emerald chip at the base
5. a wide-mouthed flask of swirling purple vapour escaping the neck

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 13. lap — Gyógynövényes, Halász

Fájlnevek: `eletfa_balzsam.png`, `vizallo_nadrag.png`, `melysegjaro_csizma.png`, `buvar_sisak.png`, `dofoszigony.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a carved wooden jar of thick golden-green balm with one glowing leaf bud on top
2. grease-treated leather trousers with dark patches and net-cord stitching along the side
3. tall leather boots with pearl scales over the toes and net-cord lacing
4. a rounded iron diving helmet with a large circular glass window and pearl rivets
5. a three-pronged prismarine trident with long sharp tines and hooks at their base

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 14. lap — Halász

Fájlnevek: `husegeskuu_szigony.png`, `viharszigony.png`, `tenger_szive_foglalat.png`, `villamszigony.png`, `mesterfoku_horgaszbot.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a prismarine trident with a pearl ring on the shaft and a thin luminous thread from the ring
2. a prismarine trident with storm-quartz inlay and a swirling water ribbon coiling around the shaft
3. a turquoise heart shell in a silver setting with pearl veins and a faint blue glow
4. a prismarine trident with copper rings on the shaft and a small lightning arc between the tines
5. a curved well-repaired fishing rod with two hooks, a cork float on the line, and pearl guide rings

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 15. lap — Favágó

Fájlnevek: `erdojaro_labszar.png`, `favago_szekerce.png`, `erdojaro_csizma.png`, `vadonjaro_szamszerij.png`, `erdojaro_csuklya.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. leather leggings reinforced with dark stained bark strips and a mossy green knee binding
2. an iron axe with a broad blade, whetstone marks along the edge, resin-darkened wooden haft
3. soft laced leather boots with grass-rope wrapped at the ankle and muddy soles
4. a wooden crossbow with three arrows fanned out and bone-glue binding on the crosspiece
5. a raised leather hood with a bark headband and an oak leaf at the temple

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 16. lap — Favágó, Bányász

Fájlnevek: `tuskes_pajzs.png`, `mestervadasz_ij.png`, `osfa_gerenda.png`, `tarnasz_lapat.png`, `banyasz_vesocsakany.png`

```
Pixel art sprite sheet of 5 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. a round wooden shield with an iron rim, short iron spikes along the edge, bone-glue seams
2. a long slender bow of dark hardwood with a small ember spark glowing at the string tip
3. a leather chestplate with thick stained bark armour plates and grass-rope stitching
4. a stout iron shovel with a short riveted handle, worn blade edge, dusty surface
5. a narrow chisel-nosed iron pickaxe with a coil of measuring cord on the haft

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```

### 17. lap — Bányász

Fájlnevek: `visszhang_szilank.png`, `melyszikla_lapat.png`, `viharkvarc_csakany.png`, `netherit_lapat.png`

```
Pixel art sprite sheet of 4 fantasy RPG item icons for a Minecraft-style game,
arranged in a single row on a plain white background, with clear spacing between items.

Items:
1. an angular dark sculk shard with rippling echo rings on its surface
2. a diamond-bladed shovel with deepslate-patterned face and an amber drop at the haft
3. a diamond pickaxe with white quartz inlays on the head and a tiny lightning crack in the metal
4. a netherite shovel with a dark matte blade and glowing amber veins in the metal

Style: detailed 64x64 pixel art (chunky pixels, visible square pixels, retro game
sprite — NOT smooth digital painting), limited palette (4-8 tones per material),
2-3px dark outline in the material's own darkest shade (not pure black), light source
from the top-left, crisp hard pixels, no anti-aliasing, no gradients, no drop shadows,
flat 2D sprite view (no isometric perspective), each item centered with a small margin,
consistent style across all icons, cohesive fantasy RPG game asset set, readable when
scaled down to 16x16, high quality pixel art. No text, no labels, no watermarks.
```
