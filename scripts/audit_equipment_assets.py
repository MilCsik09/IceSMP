#!/usr/bin/env python3
"""Audit the visual contracts that the generic resource-pack validator cannot prove."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

from generate_equipment_assets import (
    ARMOR,
    BOWS,
    CROSSBOWS,
    EQUIPMENT_TEXTURES,
    FISHING_RODS,
    HORSE_BODY,
    HORSE_SADDLE,
    HANDHELD_ITEMS,
    ITEM_TEXTURES,
    SHIELDS,
    TRIDENTS,
    WINGS,
    canonical_mask,
)


class AuditError(RuntimeError):
    pass


def load(path: Path, size: tuple[int, int]) -> Image.Image:
    try:
        image = Image.open(path).convert("RGBA")
    except OSError as exception:
        raise AuditError(f"Unreadable equipment texture: {path}") from exception
    if image.size != size:
        raise AuditError(f"Wrong equipment texture size: {path} is {image.size}, expected {size}")
    alpha_values = set(image.getchannel("A").get_flattened_data())
    if not alpha_values.issubset({0, 255}):
        raise AuditError(f"Equipment texture has partial alpha: {path}")
    if alpha_values == {0}:
        raise AuditError(f"Equipment texture is fully transparent: {path}")
    return image


def opaque_colours(image: Image.Image) -> int:
    return len({pixel[:3] for pixel in image.get_flattened_data() if pixel[3]})


def allowed(slot: str, x: int, y: int) -> bool:
    if slot == "head":
        return 0 <= x < 32 and 0 <= y < 16
    if slot == "chest":
        return 16 <= x < 56 and 16 <= y < 32
    if slot == "legs":
        return 0 <= x < 40 and 16 <= y < 32
    if slot == "feet":
        return 0 <= x < 16 and 16 <= y < 32
    return False


def audit_armor() -> None:
    for name, spec in ARMOR.items():
        layer = "humanoid_leggings" if spec.slot == "legs" else "humanoid"
        path = EQUIPMENT_TEXTURES / layer / f"{name}.png"
        image = load(path, (64, 32))
        if opaque_colours(image) < 4:
            raise AuditError(f"Armor texture lost its material palette: {path}")
        for y in range(32):
            for x in range(64):
                if image.getpixel((x, y))[3] and not allowed(spec.slot, x, y):
                    raise AuditError(
                        f"Armor texture paints outside its {spec.slot} UV islands: {path} at {x},{y}"
                    )


def audit_non_player_equipment() -> None:
    for name in WINGS:
        image = load(EQUIPMENT_TEXTURES / "wings" / f"{name}.png", (64, 32))
        if opaque_colours(image) < 5:
            raise AuditError(f"Wing texture lost its feather palette: {name}")
        if image.getchannel("A").tobytes() != canonical_mask("wing").tobytes():
            raise AuditError(f"Wing texture does not match the vanilla elytra UV mask: {name}")
    for layer, names in (("horse_body", HORSE_BODY), ("horse_saddle", HORSE_SADDLE)):
        for name in names:
            image = load(EQUIPMENT_TEXTURES / layer / f"{name}.png", (64, 64))
            if opaque_colours(image) < 5:
                raise AuditError(f"Horse equipment texture lost its material palette: {name}")
            if image.getchannel("A").tobytes() != canonical_mask(layer).tobytes():
                raise AuditError(f"Horse equipment texture has the wrong vanilla UV mask: {name}")


def state(name: str, suffix: str) -> Image.Image:
    return load(ITEM_TEXTURES / "states" / f"{name}_{suffix}.png", (64, 64))


def audit_state_group(name: str, suffixes: tuple[str, ...]) -> None:
    base_image = load(ITEM_TEXTURES / f"{name}.png", (64, 64))
    base = base_image.tobytes()
    base_small = base_image.resize((16, 16), Image.Resampling.NEAREST).tobytes()
    variants = []
    small_variants = []
    for suffix in suffixes:
        image = state(name, suffix)
        pixels = image.tobytes()
        if pixels == base:
            raise AuditError(f"Visual state is identical to its base texture: {name}_{suffix}")
        variants.append(pixels)
        small = image.resize((16, 16), Image.Resampling.NEAREST).tobytes()
        changed_pixels = sum(
            small[index:index + 4] != base_small[index:index + 4]
            for index in range(0, len(small), 4)
        )
        if changed_pixels < 4:
            raise AuditError(
                f"Visual state is not legible at inventory scale: {name}_{suffix} "
                f"({changed_pixels} changed pixels)"
            )
        small_variants.append(small)
    if len(set(variants)) != len(variants):
        raise AuditError(f"Visual states are duplicates within one item: {name}")
    if len(set(small_variants)) != len(small_variants):
        raise AuditError(f"Visual states collapse to duplicates at inventory scale: {name}")


def audit_states() -> None:
    for name in BOWS:
        audit_state_group(name, ("pull_0", "pull_1", "pull_2"))
    for name in CROSSBOWS:
        audit_state_group(name, ("pull_0", "pull_1", "pull_2", "charged", "charged_rocket"))
    for name in FISHING_RODS:
        audit_state_group(name, ("cast",))
    for name in SHIELDS:
        audit_state_group(name, ("blocking",))
    for name in TRIDENTS:
        audit_state_group(name, ("in_hand", "throwing"))


def audit_hand_orientation() -> None:
    models = ITEM_TEXTURES.parent.parent / "models/item"
    for name in HANDHELD_ITEMS:
        path = models / f"{name}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("parent") != "minecraft:item/handheld":
            raise AuditError(f"Weapon or tool uses the wrong hand orientation parent: {path}")
    for name in FISHING_RODS:
        for path in (models / f"{name}.json", models / "states" / f"{name}_cast.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            if data.get("parent") != "minecraft:item/handheld_rod":
                raise AuditError(f"Fishing rod uses the wrong hand orientation parent: {path}")


def main() -> None:
    audit_armor()
    audit_non_player_equipment()
    audit_states()
    audit_hand_orientation()
    print(
        f"Equipment asset audit passed: {len(ARMOR)} armor, {len(WINGS)} wings, "
        f"{len(HORSE_BODY) + len(HORSE_SADDLE)} horse/saddle assets, "
        f"{len(BOWS) + len(CROSSBOWS) + len(FISHING_RODS) + len(SHIELDS) + len(TRIDENTS)} "
        "stateful items"
    )


if __name__ == "__main__":
    main()
