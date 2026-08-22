# Equipment Resource Pack 2.0 Art Bible

Generated from production-derived gear-line and armor-matrix authority. The JSON sibling is the machine-readable contract; committed PNG/JSON assets are runtime authority.

## Global rules

- Style: Minecraft-native MMORPG.
- Inventory: 64×64 physical texture, authored on a 32×32 logical pixel grid.
- Worn: Minecraft 1.21.11 fixed 64×32 humanoid equipment UV coordinate grid, sampled at the same 4× density as inventory art, producing 256×128 runtime textures.
- Material/construction and silhouette must communicate family before palette.
- Emissive levels are 0–3 and are reserved; the pilot introduces no emissive/shader dependency.
- Higher progression means more sophisticated construction and motif integration, not automatic gold, glow or visual noise.
- Boss/prestige silhouette exceptions remain reserved headroom.

## Family authority

### CLOTH

- Silhouette: light vertical flow, hood/veil framing, layered hems and narrow rigid edges
- Shape grammar: vertical taper, soft stacked strips, narrow seam edge, open negative space
- Materials: woven textile, magical fiber, embroidery, cord, resin-stiffened seam
- Palette: textile value blocks dominate; metallic or crystal colour is a small seam/focal accent
- Forbidden: full metal shell, broad plate shoulder, chain field, solid face plate

### LEATHER

- Silhouette: mobile fitted mass with diagonal panels, straps and organic reinforcement
- Shape grammar: diagonal overlap, segmented organic panel, strap crossing, tapered limb guard
- Materials: treated hide, stitch, tendon, resin, chitin or scale insert
- Palette: hide ramps and treatment stains dominate; metal is limited to buckles or small reinforcement
- Forbidden: robe hem, complete ring field, full metal coverage, plate cuirass mass

### MAIL

- Silhouette: medium mass between leather and plate with visible backing and repeated ring/scale rhythm
- Shape grammar: small repeated unit, medium hard edge, hybrid yoke, compact layered skirt
- Materials: ring mail, scale, light alloy, wire, leather or textile backing
- Palette: metal rhythm is broken by visible backing and joins; never read as a solid recoloured plate
- Forbidden: solid full-body plate, flowing robe, oversized pauldron, unbroken smooth cuirass

### PLATE

- Silhouette: heavy geometric mass with framed forged panels, broad hard edges and structural joints
- Shape grammar: large geometric panel, broad framed edge, rivet rhythm, articulated hard joint
- Materials: forged plate, structural reinforcement, rivet, tempered edge, mineral or rune inset
- Palette: large metal value blocks dominate while undersuit and inset separate articulation
- Forbidden: soft robe taper, mostly exposed hide, all-over chain, unbounded fantasy spike mass

## Acquisition and progression

Profession-crafted pieces use clean deliberate joins; wilderness lines expose environmental wear; boss lines carry encounter-specific motifs; prestige lines use rare treatments without breaking Minecraft readability. Early→endgame increases finish and specificity while preserving sidegrade parity.

## Forty canonical gear lines

| Line | Family | Acquisition | Progression | Core fantasy | Mandatory differentiation |
|---|---|---|---|---|---|
| `alkimista_fatyol` | CLOTH | crafted | mid | healing tincture veil | Only CLOTH line with sealed medicinal wraps and amber resin knots. |
| `aranyfust` | CLOTH | prestige | endgame | gold-leaf smoke regalia | Only CLOTH line with smoke-dark negative space and fragmented gold-leaf perimeter. |
| `csillagfatyol` | CLOTH | boss | endgame | astral encounter veil | Only CLOTH line with a two-tail comet silhouette and constellation clusters. |
| `csontvarro` | CLOTH | world | early | field-stitched ossuary cloth | Only CLOTH line with coarse rag edges and functional bone-toggle construction. |
| `fonixszovet` | CLOTH | crafted | endgame | fire-tempered feather weave | Only CLOTH line whose cloth construction rises in feather-like chevrons from charred edges. |
| `holdlen` | CLOTH | crafted | mid | moon-linen ritual vestment | Only CLOTH line with a clean crescent/orbit grammar and uninterrupted vertical moon-linen flow. |
| `kodszovo` | CLOTH | world | early | fog-woven wanderer cloth | Only CLOTH line built from horizontally offset fog bands and low-contrast broken edges. |
| `melyseg_ritual` | CLOTH | world | high | abyssal weighted ritual robe | Only CLOTH line with weighted cords and downward abyss-prayer tabs. |
| `nemakristaly` | CLOTH | crafted | high | silent crystal-seamed cloth | Only CLOTH line with deliberately disconnected seams and sparse mute-crystal chips. |
| `szenthamvak` | CLOTH | boss | endgame | sanctified ash vestment | Only CLOTH line with an ash-white mantle and single censer-chain sash. |
| `demonbor` | LEATHER | boss | endgame | boss-forged infernal hide | Only LEATHER line with black sinew-bound heat fissures in infernal hide. |
| `holdarnyek` | LEATHER | prestige | endgame | prestige lunar shadow leather | Only LEATHER line with crescent-shaped negative cuts and matte moon-cured hide. |
| `kitinbor` | LEATHER | crafted | endgame | chitin-reinforced stalker hide | Only LEATHER line with notched chitin carapace segments and tendon-laced joints. |
| `predator_karma` | LEATHER | boss | endgame | apex encounter trophy harness | Only LEATHER line with a single bone-claw shoulder harness and red karma cord. |
| `sotetmoha` | LEATHER | world | early | moss-reclaimed early hide | Only LEATHER line with rounded moss patches and root-like repair stitches. |
| `utjaro` | LEATHER | world | early | road-worn passage leathers | Only LEATHER line with route-like cross straps and road-polished edges. |
| `vadbor` | LEATHER | crafted | mid | alchemically treated wild-hide armor | Only LEATHER line with pine-resin seals and a double-stitched diagonal wild-hide panel grammar. |
| `vadorzo` | LEATHER | crafted | mid | crafted predator-warden leather | Only LEATHER line with compact ward chevrons and reinforced predator bracers. |
| `verszavanna` | LEATHER | world | high | sun-scorched blood-savanna hunt hide | Only LEATHER line with long ochre hunt slashes over sun-cracked red savanna hide. |
| `vizbor` | LEATHER | crafted | high | water-cured demonhide sidegrade | Only LEATHER line with rounded waterline overlaps and sparse shell-stud closures. |
| `csontenyv` | MAIL | crafted | mid | bone-glued warden mail | Only MAIL line whose ring field is interrupted by straight pale bone-glue splints. |
| `gyongyhaz_warden` | MAIL | world | early | shore-found pearl warden mail | Only MAIL line with a pearlescent shell-fan yoke and intentionally sparse ring coverage. |
| `konnyu_otvozet` | MAIL | crafted | mid | precision light-alloy field mail | Only MAIL line with precise pewter ring blocks, compact alloy scales and desaturated copper service joins. |
| `melyvizi_vadasz` | MAIL | prestige | endgame | prestige abyss hunter mail | Only MAIL line with diagonal net-mail and pearl/brass hook closures. |
| `runalanc` | MAIL | crafted | high | conductive runic chain | Only MAIL line with a continuous violet rune-link border integrated into blackened chain. |
| `runapajzs` | MAIL | boss | endgame | boss shield-lamella mail | Only MAIL line with square rune-shield lamella nested inside visible ring fields. |
| `vadvadasz` | MAIL | world | early | early wilderness hunting mail | Only MAIL line with deliberately sparse recovered-ring clusters over dominant hunting hide. |
| `viharjaro` | MAIL | boss | endgame | storm encounter conductor mail | Only MAIL line whose ring rows visibly converge into a controlled cyan storm clasp. |
| `viharkvarc_runas` | MAIL | crafted | endgame | quartz-conduit tempest mail | Only MAIL line with braided conductor columns and evenly spaced storm-quartz nodes. |
| `viharszel` | MAIL | world | high | wind-scoured highland mail | Only MAIL line with pale wind-swept diagonal ring bands and an intentionally open lower edge. |
| `borostyan_tarna` | PLATE | crafted | mid | amber mine bulwark plate | Only PLATE line with soot-dark rectangular mine panels and recessed amber windows. |
| `csillagacel` | PLATE | prestige | endgame | prestige star-steel harness | Only PLATE line with clean pale star-steel facets and small five-notch edges. |
| `glatziendorfi` | PLATE | crafted | high | frost-ward runeforged plate | Only PLATE line with stepped glacier shelves and frost-rune bevels. |
| `hataror` | PLATE | world | early | practical border guard plate | Only PLATE line with a plain green border tabard breaking practical service-iron panels. |
| `melyseg_orseg` | PLATE | boss | endgame | abyss pressure-guard plate | Only PLATE line with sealed teal pressure windows inside black framed plates. |
| `osicsarnok` | PLATE | world | high | ancestral hall sentinel plate | Only PLATE line with ancestral hall arches and column-like vertical articulation. |
| `ostromtoro` | PLATE | boss | endgame | siege-engine boss plate | Only PLATE line with horizontal battering bands and red shock-leather compression strips. |
| `runaforged` | PLATE | crafted | endgame | endgame enchanted forge plate | Only PLATE line with recessed violet forge channels nested inside charcoal structural frames. |
| `salakfal` | PLATE | world | early | improvised slag-wall plate | Only PLATE line with uneven slag-block overlaps and narrow furnace-orange seams. |
| `sarkfeny` | PLATE | crafted | mid | aurora-tempered crusader plate | Only PLATE line with one restrained two-tone aurora-enamel sweep across clean crusader frames. |
