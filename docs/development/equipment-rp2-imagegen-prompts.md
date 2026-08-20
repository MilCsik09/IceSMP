# Equipment RP2-B imagegen authoring record

The eight committed pilot source sheets were generated with the built-in OpenAI imagegen tool. They are design sources, not files loaded by the Minecraft client. `generate_equipment_rp2_pilot.py` extracts the inventory quadrants and adapts the worn direction to the fixed Minecraft 1.21.11 humanoid UV layout.

Shared inventory constraints: transparent 2×2 sheet; HEAD, CHEST, LEGS, FEET order; crisp 64px-style pixel art; hard square pixels; binary alpha; top-left light; readable at 16px; no labels, mannequin, watermark, gradients, bloom or unrelated equipment.

Shared worn constraints: three equal-scale Minecraft-proportioned views in FRONT, BACK, RIGHT SIDE order; neutral pose; vanilla player silhouette; transparent background; clear surface information; no text, environment, weapons or skin details.

### UV-fidelity revision

The four worn sheets were regenerated after the first offline render showed that concept-scale details did not survive the vanilla atlas. The replacement prompts explicitly require flush vanilla player boxes, separated limbs, orthographic alignment and motifs readable at the real face budgets: head 8×8, torso 8×12, arm 4×12 and leg 4×12. Protruding geometry and details smaller than one useful UV pixel are forbidden. The generator then pins the reference's bold motifs directly to those UV faces instead of treating the turnaround as a generic resize target.

Shared replacement prompt core: redesign the attached line reference as a strict Minecraft Java 1.21.11 vanilla-humanoid worn set; exactly FRONT, BACK and RIGHT SIDE orthographic views at one scale and baseline; rigid neutral pose; only rectangular head/body/arm/leg boxes; deliberately low-resolution Minecraft pixel art with large clusters and hard edges; preserve the named line motifs at the real 8×8 / 8×12 / 4×12 / 4×12 face budgets; no external geometry, perspective, overlapping limbs, labels, watermark, cast shadow or details that cannot survive the final atlas.

## Holdlen / CLOTH

Inventory prompt: four separate midnight-indigo cloth pieces with layered linen, wrapped bands, silver crescent embroidery and tiny restrained lilac stitch accents; lightweight silhouettes; no mail or plate mass.

Worn replacement specifics: open-face hood; large centered front and back crescent; strong vertical silver seams; restrained lilac belt knot and leg accents; no cape or skirt beyond vanilla leg boxes.

## Vadbor / LEATHER

Inventory prompt: fitted hunting hood, reinforced vest, segmented leggings and strapped boots in umber/chestnut hide, moss accents, bone stitching and dull brass buckles; diagonal panels and mobile silhouette.

Worn replacement specifics: one bold diagonal front strap, returning/crossed back strap, two-pixel-scale stitches, blocky moss shoulder and cuff reinforcement, large belt buckle and readable knee/boot bands.

## Konnyu otvozet / MAIL

Inventory prompt: chain coif, light chain shirt, chain leggings and chain/leather boots; gunmetal/silver interlocking rings over charcoal backing with restrained copper fasteners; unmistakably actual mail between leather and plate.

Worn replacement specifics: oversized two-tone interlocking ring/check rhythm across coif, torso and legs; bold brown diagonal strap; dark backing; horizontal leather hems and sparse copper single-pixel accents; no noisy grey-dot substitute for mail.

## Borostyan tarna / PLATE

Inventory prompt: enclosed mining helmet, forged breastplate, articulated leggings and sabatons; dark iron, framed plates, small amber mineral insets, brass rivets and leather joints; heavy and practical.

Worn replacement specifics: closed helmet with bold brow and narrow dark visor; large framed chest plate with one amber square; plain framed back plate; vertical forged bands, joint leather, corner rivets and framed greaves; no external shoulder geometry.

## Deterministic handoff

Every source path, authoring mode and SHA-256 digest is stored in `equipment-rp2-pilot-manifest.json`. Missing or changed source sheets fail validation. Runtime production does not require imagegen: committed 64×64 inventory and 64×32 worn PNGs remain the client authority.
