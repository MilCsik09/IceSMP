#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path.cwd()


def replace_pattern(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise SystemExit(f"{label}: expected one section, found {count}")
    return updated


def main() -> None:
    architecture_path = ROOT / "docs/ARCHITECTURE.md"
    architecture = architecture_path.read_text(encoding="utf-8")

    architecture = replace_pattern(
        architecture,
        r"  - \*\*`storage/BlockRegenJournal`\*\*.*?(?=  - \*\*`storage/TransactionJournal`\*\*)",
        """  - **`storage/BlockRegenJournal`** (block-regen.yml checkpoint + `block-regen.wal`):
    a tile-entity snapshot tartósan lemezre kerül a konténer kiürítése előtt, és a pending
    rekordok restart után újrapróbálhatók. Az `APPLYING/APPLIED` átmenet csökkenti az elvesző
    restore-ok esélyét, de valódi Folia + process-kill fault-injection nélkül nem állítunk
    pontosan-egyszeri konténer-NBT alkalmazást.
""",
        "block-regen guarantee",
    )
    architecture = replace_pattern(
        architecture,
        r"  - \*\*`storage/TransactionJournal`\*\*.*?(?=\n\s*- \*\*Szezon–community generation commit)",
        """  - **`storage/TransactionJournal`** (market-journal.yml): a prepare és a szigorú séma
    jelentősen csökkenti a félbehagyott listing/pénz/item műveletek elvesztését, és normál
    restartnál recoveryt ad. A wallet, market YAML és player inventory között nincs formális
    több-store atomicitás vagy exactly-once bizonyítás; a globális currency gate külön
    egyszerűsítési és runtime-validációs scope.
""",
        "market guarantee",
    )
    architecture = replace_pattern(
        architecture,
        r"### 4\.1 Audit-állapot \(baseline — ŐRIZD MEG\)\nA teljes kódbázist átnéztük Folia-kompatibilitásra; \*\*nulla sértés\*\*\. A bevált minták, amelyeket\núj kódnál is tartani kell:",
        """### 4.1 Folia audit-állapot (statikus baseline)
A központi scheduler-minták sokat javultak, de ez nem teljes runtime-garancia. A party
proximity/reward és más több-régiós hívási láncok valódi Folia tesztet igényelnek. A bevált
minták, amelyeket új kódnál is tartani kell:""",
        "Folia guarantee",
    )
    architecture_path.write_text(architecture, encoding="utf-8")

    audit_path = ROOT / "docs/audits/IceSMP_audit_update_b6db9d2.md"
    audit = audit_path.read_text(encoding="utf-8")
    old_priorities = """1. `TransientEntities` és world-event lifecycle valódi Folia régióhatár-tesztje.
2. Party XP és Wild Hunt personal loot távoli játékos-hozzáférése.
3. Rendszeresen beragadó world boss/escort/quest/dungeon utak runtime felderítése.
4. Season member reward egyszerűsítése külön branchben.
5. Market journal/gate külön, szűk gazdasági scope-ban."""
    new_priorities = """1. Party XP/personal-loot proximity: a jelenlegi `getNearbyMembers` idegen régiós
   `Player` location/world olvasást próbál és kivételnél jogos tagot hagy ki (`STILL_OPEN`).
2. Rituálék: a tartós/teleport/buff hatás jelenleg az általános áldozatfogyasztás előtt fut;
   a `home` future eredménye sincs a commit feltételéhez kötve (`STILL_OPEN`).
3. Block-regen tile-NBT replay valódi Folia/process-kill ellenőrzése (`NEEDS_RUNTIME_VALIDATION`).
4. Season member reward PDC/saveData protokoll egyszerűsítése külön branchben.
5. Market journal és globális currency gate külön, szűk gazdasági scope-ban."""
    if old_priorities not in audit:
        raise SystemExit("audit priorities: source block not found")
    audit = audit.replace(old_priorities, new_priorities, 1)

    anchor = "## Aktuális gameplay-prioritások\n"
    revalidated = """## Célzottan újraellenőrzött régi findingek

- `CRIT-08 / TransientEntities`: `PARTIALLY_FIXED` — a registry saját EntitySchedulert és
  heartbeatet használ; a teljes world-event call graph valódi Folia runtime-ja nem futott.
- `HIGH-35 / party proximity`: `STILL_OPEN` — a távoli member world/location olvasása
  továbbra is cross-region try/catch mintára épül, ezért jogos XP/loot maradhat el.
- `CRIT-03 / ritual outcome-before-consume`: `STILL_OPEN` — a konkrét vezérlési sorrend az
  aktuális masteren is fennáll.
- A történeti audit többi findingje: `NOT_REVALIDATED`.

"""
    if anchor not in audit:
        raise SystemExit("audit priority heading not found")
    audit = audit.replace(anchor, revalidated + anchor, 1)
    audit_path.write_text(audit, encoding="utf-8")


if __name__ == "__main__":
    main()
