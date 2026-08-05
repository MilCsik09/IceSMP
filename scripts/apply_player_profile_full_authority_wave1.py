#!/usr/bin/env python3
"""Apply the first PlayerProfile full-authority integration wave deterministically."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"

PLATFORM_FIELD = (
    "    private final hu.taliann.icesmp.playerprofile.integration."
    "PlayerProfilePlatform playerProfilePlatform;\n"
)
AUTHORITY_FIELD = (
    "    private final hu.taliann.icesmp.playerprofile.application."
    "PlayerProfileAuthority playerProfileAuthority;\n"
)
PLATFORM_CONSTRUCTION = (
    "        this.playerProfilePlatform = new hu.taliann.icesmp.playerprofile.integration."
    "PlayerProfilePlatform(plugin, configManager);\n"
)
AUTHORITY_INSTALL = (
    "        this.playerProfileAuthority = hu.taliann.icesmp.playerprofile.application."
    "PlayerProfileAuthority.install(\n"
    "                playerProfilePlatform.service(), playerProfilePlatform.repository(),\n"
    "                playerProfilePlatform.transactions());\n"
)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def normalize_after(text: str, anchor: str, block: str, label: str) -> str:
    """Remove duplicate exact blocks and insert one canonical copy after anchor."""
    text = text.replace(block, "")
    count = text.count(anchor)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(anchor, anchor + block, 1)


def patch_core() -> None:
    text = CORE.read_text(encoding="utf-8")

    # The anchor is a prefix of the desired two-line field block, therefore a naive
    # substring test is not idempotent. Canonicalize by removing all authority-field
    # copies first and then inserting exactly one after the platform field.
    text = text.replace(AUTHORITY_FIELD, "")
    if text.count(PLATFORM_FIELD) != 1:
        raise RuntimeError(
            f"authority field: expected one platform field, found {text.count(PLATFORM_FIELD)}"
        )
    text = text.replace(PLATFORM_FIELD, PLATFORM_FIELD + AUTHORITY_FIELD, 1)

    text = normalize_after(
        text,
        PLATFORM_CONSTRUCTION,
        AUTHORITY_INSTALL,
        "authority install",
    )

    text = replace_once(
        text,
        "        if (!enableCompleted) {\n"
        "            plugin.getLogger().severe(\"IceSMP enable did not complete — skipping stateful manager shutdown \"\n"
        "                    + \"and persistent-store writes to protect the last durable state.\");\n"
        "            return;\n"
        "        }\n",
        "        if (!enableCompleted) {\n"
        "            plugin.getLogger().severe(\"IceSMP enable did not complete — skipping stateful manager shutdown \"\n"
        "                    + \"and persistent-store writes to protect the last durable state.\");\n"
        "            if (hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.installed()\n"
        "                    .filter(installed -> installed == playerProfileAuthority).isPresent()) {\n"
        "                playerProfileAuthority.uninstall();\n"
        "            }\n"
        "            return;\n"
        "        }\n",
        "failed enable cleanup",
    )
    text = replace_once(
        text,
        "            if (!shutdown.drained()) {\n"
        "                plugin.getLogger().severe(\"PlayerProfile disable drain incomplete: \" + shutdown.detail());\n"
        "                return;\n"
        "            }\n"
        "        } catch (final InterruptedException interrupted) {\n",
        "            if (!shutdown.drained()) {\n"
        "                plugin.getLogger().severe(\"PlayerProfile disable drain incomplete: \" + shutdown.detail());\n"
        "                return;\n"
        "            }\n"
        "            playerProfileAuthority.uninstall();\n"
        "        } catch (final InterruptedException interrupted) {\n",
        "normal authority uninstall",
    )
    CORE.write_text(text, encoding="utf-8")


def main() -> int:
    patch_core()
    print("PlayerProfile full-authority wave 1 applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
