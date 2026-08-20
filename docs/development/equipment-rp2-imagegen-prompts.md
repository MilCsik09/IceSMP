# Equipment RP2-B imagegen authoring record

The eight committed pilot source sheets were generated with the built-in OpenAI imagegen tool. They are design sources, not files loaded by the Minecraft client. `generate_equipment_rp2_pilot.py` extracts the inventory quadrants and adapts the worn direction to the fixed Minecraft 1.21.11 humanoid UV layout.

Shared inventory constraints: transparent 2×2 sheet; HEAD, CHEST, LEGS, FEET order; crisp 64px-style pixel art; hard square pixels; binary alpha; top-left light; readable at 16px; no labels, mannequin, watermark, gradients, bloom or unrelated equipment.

Shared worn constraints: three equal-scale Minecraft-proportioned views in FRONT, BACK, RIGHT SIDE order; neutral pose; vanilla player silhouette; transparent background; clear surface information; no text, environment, weapons or skin details.

## Holdlen / CLOTH

Inventory prompt: four separate midnight-indigo cloth pieces with layered linen, wrapped bands, silver crescent embroidery and tiny restrained lilac stitch accents; lightweight silhouettes; no mail or plate mass.

Worn prompt: complete Holdlen hooded robe with vertical split panels, silver seam language and wrapped boots; preserve the inventory source identity; no cape mesh, large shoulders or glow bloom.

## Vadbor / LEATHER

Inventory prompt: fitted hunting hood, reinforced vest, segmented leggings and strapped boots in umber/chestnut hide, moss accents, bone stitching and dull brass buckles; diagonal panels and mobile silhouette.

Worn prompt: complete agile leather set with crossed chest straps, layered hide, visible stitching and restrained organic edge accents; no robe, chainmail or rigid shoulder mass.

## Konnyu otvozet / MAIL

Inventory prompt: chain coif, light chain shirt, chain leggings and chain/leather boots; gunmetal/silver interlocking rings over charcoal backing with restrained copper fasteners; unmistakably actual mail between leather and plate.

Worn prompt: complete ring-mail set dominated by small repeated rings, hanging mail panels, dark backing and leather cuffs; solid alloy panels remain secondary; no plate torso, scales or giant pauldrons.

## Borostyan tarna / PLATE

Inventory prompt: enclosed mining helmet, forged breastplate, articulated leggings and sabatons; dark iron, framed plates, small amber mineral insets, brass rivets and leather joints; heavy and practical.

Worn prompt: complete mine-forged plate set with modest shoulders and a single chest focal inset plus tiny helmet/knee accents; dark iron dominates; no netherite recolor, spikes, excessive gold or glow.

## Deterministic handoff

Every source path, authoring mode and SHA-256 digest is stored in `equipment-rp2-pilot-manifest.json`. Missing or changed source sheets fail validation. Runtime production does not require imagegen: committed 64×64 inventory and 64×32 worn PNGs remain the client authority.
