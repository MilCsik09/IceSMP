#!/usr/bin/env python3
"""Bounded exact-head gates for the verified player-facing messaging findings."""
from __future__ import annotations

from pathlib import Path
import json
import re
import sys
import yaml

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def messages(path: str) -> dict:
    data = yaml.safe_load(text(path)) or {}
    return data.get("messages", {}) or {}


system = messages("src/main/resources/messages/system.yml")
world = messages("src/main/resources/messages/world.yml")
quest = messages("src/main/resources/messages/quest.yml")
spec = messages("src/main/resources/messages/spec.yml")
party = messages("src/main/resources/messages/party.yml")
profession = messages("src/main/resources/messages/profession.yml")
market = messages("src/main/resources/messages/market.yml")
faction = messages("src/main/resources/messages/faction.yml")
territory = messages("src/main/resources/messages/territory.yml")

# M0/M1 crate parity.
if str(system.get("crate-opened", "")).count("%s") != 3:
    fail("crate-opened must render crate, batch count and reward summary")
if str(system.get("crate-need-key", "")).count("%s") != 5:
    fail("crate-need-key must render display, required keys, price, currency and crate id")
if "%s" in str(system.get("crate-not-enough-keys", "")):
    fail("crate-not-enough-keys must be safe on the no-argument race path")

# M1 world-boss semantics.
spawn = str(world.get("world-boss-spawned", ""))
slain = str(world.get("world-boss-slain", ""))
if "érdemi hozzájárulás" not in spawn or "személyes jutalom" not in slain:
    fail("world-boss copy must describe contribution-gated personal rewards")
if "world-boss-slain-guest" not in world:
    fail("guest world-boss victory copy missing")

# M2 authority collision: territory owns territory-* bundled defaults.
if any(str(key).startswith("territory-") for key in faction):
    fail("faction.yml must not shadow territory-* keys")

# M2 daily wording / retired procedural player vocabulary.
if "kanonikus" in str(quest.get("daily-authored-route", "")).lower():
    fail("daily route leaks internal canonical terminology")

# M2 party terminology.
party_blob = "\n".join(map(str, party.values())).lower()
if "párttag" in party_blob or "[party]" in party_blob:
    fail("party surface still leaks inconsistent player terminology")

# M1/M2 spec player projection.
spec_command = text("src/main/java/hu/taliann/icesmp/commands/SpecCommand.java")
if "sendPlayerInfo(player, diagnostic)" not in spec_command:
    fail("/spec info lacks player-readable projection")
if "sendAdminDiagnosticInfo" not in spec_command:
    fail("staff diagnostic projection was not retained")
if "spec-doctrine-success-gui" not in spec or "spec-switch-success-gui" not in spec:
    fail("spec GUI requires exact-arity message keys")

# M1 market: no final-success wording on the GUI path until manager outcome represents durable finality.
market_listener = text("src/main/java/hu/taliann/icesmp/listeners/MarketGUIListener.java")
for forbidden in ("market-buy-success", "market-buyout-success", "market-bid-success", "market-sold-notice", "market-outbid-notice"):
    if f'"{forbidden}"' in market_listener:
        fail(f"market GUI still emits pre-commit finality key {forbidden}")
if "escapeTags(plainName)" not in market_listener:
    fail("market item-name MiniMessage escaping missing")

# M1 quest pending reward feedback.
quest_delivery = text("src/main/java/hu/taliann/icesmp/managers/QuestPhysicalRewardDeliveryService.java")
if "jutalma függőben maradt" not in quest_delivery:
    fail("pending physical quest reward lacks player feedback")

# M1 profession discoverability.
quest_gui = text("src/main/java/hu/taliann/icesmp/gui/QuestLogGUI.java")
for needle in ("Szakmai feltétel", "requires-profession-level", "/profile"):
    if needle not in quest_gui:
        fail(f"profession-gated quest guidance missing {needle}")

# M1 boss-specific personal receipt.
encounter = text("src/main/java/hu/taliann/icesmp/pve/EncounterRewardDeliveryService.java")
if "encounter-reward-delivered" not in encounter or "sendDeliveryReceipt" not in encounter:
    fail("successful personal encounter reward receipt missing")

# M1 profession/internal vocabulary cleanup on the reviewed surfaces.
profession_gui = text("src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java")
for forbidden in ("Canonical template:", "armorFamily().name()", "floor "):
    if forbidden in profession_gui:
        fail(f"profession recipe GUI leaks internal term: {forbidden}")
profession_blob = "\n".join(map(str, profession.values())).lower()
for forbidden in ("canonical iteminstance", "item uuid", "recovery journal", "metadata-vesztés", "vanilla smithing", "grindstone-ban"):
    if forbidden in profession_blob:
        fail(f"profession messages leak internal term: {forbidden}")

# M2 raw enum GUI leak.
bestiary = text("src/main/java/hu/taliann/icesmp/gui/BestiaryGUI.java")
if "template.rank().name()" in bestiary or "template.archetype().name()" in bestiary:
    fail("Bestiary still renders raw enum names")
crate_browser = text("src/main/java/hu/taliann/icesmp/gui/CrateBrowserGUI.java")
if "reward.type().name()" in crate_browser:
    fail("Crate browser still renders raw reward enum")

# Evidence must not falsely claim blocking leftovers.
evidence = json.loads(text("docs/development/player-facing-messaging-integrity-hardening.json"))
if evidence["findings"]["MSG-M0-001"] != "CLOSED" or evidence["findings"]["MSG-M0-002"] != "CLOSED":
    fail("M0 evidence is not closed")
if any(evidence["findings"][f"MSG-M1-{i:03d}"] != "CLOSED" for i in range(1, 12)):
    fail("one or more M1 evidence entries are not closed")

print("Player-facing messaging hardening audit passed: M0=2/2 M1=11/11 bounded-M2/M3 gates=PASS")
