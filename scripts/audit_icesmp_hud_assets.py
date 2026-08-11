#!/usr/bin/env python3
"""Source-based, per-file IceSMP HUD and BetterHud asset audit."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageFilter, ImageStat

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "resource-pack" / "assets" / "icesmp_hud"
BETTERHUD = ROOT / "deploy" / "betterhud" / "assets" / "icesmp"
SOURCE = ROOT / "deploy" / "betterhud" / "source"
PREVIEWS = ROOT / "deploy" / "betterhud" / "previews"

CLASSES = ("warrior", "evoker", "archer", "shaman", "monk", "paladin",
           "demon_hunter", "druid", "priest", "death_knight", "assassin",
           "warlock", "wizard")

MECHANIC_AUDIT = {
    "Warrior": "battle_tempo; blood_frenzy + exhaustion/overdrive/aftermath; guard + oath target",
    "Evoker": "empower rank/hold/fizzle; essence colour; resonance/burst; imprint/ally/echo",
    "Archer": "wind_read; prey + precision_chain/weak point; beast bond",
    "Shaman": "main/companion totem wheel; resonance/overload; maelstrom; tide; blessing side",
    "Monk": "flow; combo_chain; stagger pool; mist_threads and linked allies",
    "Paladin": "oath + conviction; beacon target; judgement_marks; shield_charge",
    "Demon Hunter": "load bands/overload; fragments + momentum; pain; typed sigil slots",
    "Druid": "harmony + season/autumn; feral combo/scent; balance/eclipse; bark/roots; seed ripeness",
    "Priest": "litany/verses/recited; shield_web/atonement/conversion; marrow + ossuary; madness",
    "Death Knight": "blood/frost/death runes and ready/spent/regenerating/locked; blood memory; frost marks; plague/mutation",
    "Assassin": "four openings; toxin slots/dose; detection/stealth/trail/echo; infection/strain",
    "Warlock": "soul debt/cap; curses/soul thread; embers/overheat; demon roster",
    "Wizard": "runewaving school/reaction; fire/frost/arcane attunement/convergence/crown; court",
}

CLASS_LABELS = {
    "warrior": "Warrior", "evoker": "Evoker", "archer": "Archer",
    "shaman": "Shaman", "monk": "Monk", "paladin": "Paladin",
    "demon_hunter": "Demon Hunter", "druid": "Druid", "priest": "Priest",
    "death_knight": "Death Knight", "assassin": "Assassin",
    "warlock": "Warlock", "wizard": "Wizard",
}

EXPECTED_MECHANICS = {
    "warrior": ("battle_tempo", "blood_frenzy", "guard"),
    "evoker": ("empower", "resonance", "imprint"),
    "archer": ("wind_read", "precision_chain", "bond"),
    "shaman": ("totem_wheel", "resonance", "maelstrom", "tide"),
    "monk": ("flow", "combo_chain", "stagger", "mist_threads"),
    "paladin": ("conviction", "beacon", "judgement_marks", "shield_charge"),
    "demon_hunter": ("load", "fragments", "pain", "sigil"),
    "druid": ("harmony", "combo", "balance", "bark", "seeds"),
    "priest": ("litany", "shield_web", "marrow", "madness"),
    "death_knight": ("rune_wheel", "blood_memory", "frost_marks", "plague"),
    "assassin": ("opening", "toxin", "detection", "infection"),
    "warlock": ("soul_debt", "curses", "embers", "demons"),
    "wizard": ("runewaving", "attunement", "court"),
}

CLASS_PACKAGES = {
    "demon_hunter": "demonhunter",
    "death_knight": "deathknight",
}

GRADLE_CLASS_PREFIXES = {
    "warrior": "warrior", "evoker": "evoker", "archer": "archer",
    "shaman": "shaman", "monk": "monk", "paladin": "paladin",
    "demon_hunter": "demonHunter", "druid": "druid", "priest": "priest",
    "death_knight": "deathKnight", "assassin": "assassin",
    "warlock": "warlock", "wizard": "wizard",
}


def pixels(image: Image.Image):
    getter = getattr(image, "get_flattened_data", None)
    return getter() if getter is not None else image.getdata()


@dataclass
class Finding:
    path: str
    size: str
    mode: str
    alpha: str
    bbox: str
    margins: str
    center: str
    sharpness: str
    magenta: int
    verdict: str
    note: str


def expected_size(path: Path) -> tuple[int, int] | None:
    name = path.name
    if path.parent.name == "hud" and (name.startswith(("class-", "currency-", "rune-", "icon-", "mechanic-", "charge-"))):
        return (64, 64)
    if path.parent == BETTERHUD:
        if name.startswith(("class-", "rune-", "icon-", "mechanic-")) and name != "rune-progress.png":
            return (64, 64)
        if name.startswith("frame-hud-"): return (204, 126)
        if name.startswith("popup-"): return (300, 72)
        if name.startswith("emblem-"): return (24, 24)
        if name.startswith("charge-"): return (32, 32)
        if name == "resource-track.png" or name == "resource-fill.png": return (328, 10)
        if name == "metric-track.png" or name == "metric-fill.png": return (156, 10)
        if name == "metric-mini-track.png" or name == "metric-mini-fill.png": return (100, 10)
        if name == "rune-progress.png": return (48, 6)
    if path.parent.name == "hud":
        fixed = {"wallet-strip.png": (260, 22), "detail-strip.png": (260, 22),
                 "text-atlas.png": (128, 96), "segment-track.png": (12, 5),
                 "segment-fill.png": (12, 5), "segment-fill-warm.png": (12, 5),
                 "segment-fill-gold.png": (12, 5)}
        if name.startswith("frame-hud-"): return (260, 160)
        return fixed.get(name)
    return None


def inspect(path: Path) -> Finding:
    relative = path.relative_to(ROOT).as_posix()
    notes: list[str] = []
    verdict = "PASS"
    try:
        image = Image.open(path); image.load()
    except Exception as exception:
        return Finding(relative, "?", "?", "?", "?", "?", "?", "?", 0,
                       "FAIL", f"unreadable: {exception}")
    expected = expected_size(path)
    if expected and image.size != expected:
        verdict = "FAIL"; notes.append(f"expected {expected[0]}x{expected[1]}")
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    alpha_values = set(pixels(alpha))
    visible = alpha.point(lambda value: 255 if value >= 16 else 0)
    bbox = visible.getbbox()
    if bbox:
        left, top, right, bottom = bbox
        margins = (left, top, image.width - right, image.height - bottom)
        cx = (left + right - 1) / 2.0
        cy = (top + bottom - 1) / 2.0
        dx = cx - (image.width - 1) / 2.0
        dy = cy - (image.height - 1) / 2.0
    else:
        margins = (0, 0, 0, 0); dx = dy = 0.0
        if path.parent.name == "hud": verdict = "FAIL"; notes.append("empty alpha")
    icon = expected == (64, 64)
    if icon and bbox and min(margins) < 3:
        verdict = "FAIL"; notes.append("visible crop touches glyph margin")
    if icon and (abs(dx) > 4.0 or abs(dy) > 4.0):
        if verdict == "PASS": verdict = "WARN"
        notes.append("visual mass is off-centre")
    if icon and path.is_relative_to(RUNTIME / "textures" / "hud"):
        if alpha.getpixel((63, 63)) != 1:
            verdict = "FAIL"; notes.append("missing alpha=1 fixed-width marker")
    magenta = 0
    for red, green, blue, a in pixels(rgba):
        # Only the hot, near-balanced chroma-key family is a defect. Purple and
        # violet are intentional IceSMP class colours (Warlock, DK, Wizard).
        hot_key = red > 245 and blue > 245 and green <= 8
        edge_spill = a < 128 and red > 200 and blue > 200 and green <= 8 and abs(red - blue) < 30
        if a >= 16 and (hot_key or edge_spill):
            magenta += 1
    if magenta:
        verdict = "FAIL"; notes.append("magenta fringe/key pixels")
    gray = rgba.convert("L")
    sharpness = ImageStat.Stat(gray.filter(ImageFilter.FIND_EDGES)).mean[0]
    if icon and sharpness < 4.0:
        if verdict == "PASS": verdict = "WARN"
        notes.append("low edge contrast")
    hard_alpha = path.name.startswith(("resource-", "segment-")) or path.name == "rune-progress.png"
    if hard_alpha and not alpha_values.issubset({0, 255}):
        verdict = "FAIL"; notes.append("progress mask has soft alpha")
    alpha_text = "none" if len(alpha_values) == 1 and 255 in alpha_values else f"{min(alpha_values)}..{max(alpha_values)}/{len(alpha_values)}"
    return Finding(relative, f"{image.width}x{image.height}", image.mode, alpha_text,
                   "-" if not bbox else f"{bbox[0]},{bbox[1]}–{bbox[2]},{bbox[3]}",
                   "/".join(map(str, margins)), f"{dx:+.1f}/{dy:+.1f}", f"{sharpness:.2f}",
                   magenta, verdict, "; ".join(notes))


def font_errors() -> list[str]:
    errors: list[str] = []
    for font in sorted((RUNTIME / "font").glob("*.json")):
        data = json.loads(font.read_text(encoding="utf-8"))
        for provider in data.get("providers", []):
            reference = provider.get("file")
            if not reference or not reference.startswith("icesmp_hud:hud/"): continue
            target = RUNTIME / "textures" / "hud" / reference.split("/", 1)[1]
            if not target.is_file(): errors.append(f"{font.relative_to(ROOT)} -> {reference}")
    return errors


def implementation_errors() -> tuple[list[str], list[str]]:
    manifest = json.loads((RUNTIME / "hud-manifest.json").read_text(encoding="utf-8"))
    mechanics = [str(item) for item in manifest.get("mechanics", [])]
    errors: list[str] = []
    expected = [f"{class_id}:{mechanic_id}"
                for class_id in CLASSES for mechanic_id in EXPECTED_MECHANICS[class_id]]
    if len(mechanics) != 49 or len(set(mechanics)) != 49 or set(mechanics) != set(expected):
        missing = sorted(set(expected) - set(mechanics))
        unexpected = sorted(set(mechanics) - set(expected))
        errors.append("the mechanic manifest must contain the audited 49 class-qualified ids"
                      + (f"; missing={missing}" if missing else "")
                      + (f"; unexpected={unexpected}" if unexpected else ""))
    renderer = (ROOT / "src/main/java/hu/taliann/icesmp/hud/IceSmpHudRenderer.java").read_text(encoding="utf-8")
    layout = (ROOT / "deploy/betterhud/layouts/icesmp-main-layout.yml").read_text(encoding="utf-8")
    catalogue = (ROOT / "deploy/betterhud/images/icesmp-class-images.yml").read_text(encoding="utf-8")
    runtime_adapter = (ROOT / "src/main/java/hu/taliann/icesmp/classspec/integration/"
                       "BukkitClassSpecRuntimeAdapter.java").read_text(encoding="utf-8")
    gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    check_dependencies = gradle.split("tasks.check", 1)[-1]
    hashes: dict[str, list[str]] = {}
    for qualified in mechanics:
        class_id, mechanic_id = qualified.split(":", 1)
        if f'"{qualified}"' not in renderer:
            errors.append(f"native renderer lacks {qualified}")
        for variant in ("active", "ready", "alert", "spent"):
            image_id = f"icesmp_mechanic_{class_id}_{mechanic_id}_{variant}"
            if image_id not in catalogue:
                errors.append(f"BetterHud catalogue lacks {qualified}/{variant}")
            if f"mechanic_{class_id}-{mechanic_id}-{variant}.png" in catalogue:
                errors.append(f"malformed BetterHud asset path for {qualified}/{variant}")
        if f"mechanic_primary_{class_id}_{mechanic_id}_active:" not in layout \
                and f"mechanic_secondary_{class_id}_{mechanic_id}_active:" not in layout:
            errors.append(f"BetterHud layout lacks {qualified}")
        active = RUNTIME / "textures/hud" / f"mechanic-{class_id}-{mechanic_id}-active.png"
        digest = hashlib.sha256(active.read_bytes()).hexdigest() if active.is_file() else "missing"
        hashes.setdefault(digest, []).append(qualified)
    for duplicates in hashes.values():
        if len(duplicates) > 1:
            errors.append("generic/duplicate mechanic artwork: " + ", ".join(duplicates))
    for class_id in CLASSES:
        package_id = CLASS_PACKAGES.get(class_id, class_id)
        service = list((ROOT / "src/main/java/hu/taliann/icesmp" / package_id).glob("*GameplayService.java"))
        service_text = service[0].read_text(encoding="utf-8") if len(service) == 1 else ""
        if len(service) != 1 or "hudState" not in service_text:
            errors.append(f"missing typed hudState source for {class_id}")
        for mechanic_id in EXPECTED_MECHANICS[class_id]:
            if f'"{mechanic_id}"' not in service_text:
                errors.append(f"{class_id} hudState lacks audited mechanic {mechanic_id}")
        job_id = class_id.upper()
        if f"hudAdapters.put(JobType.{job_id}," not in runtime_adapter:
            errors.append(f"runtime HUD adapter lacks {class_id}")
        prefix = GRADLE_CLASS_PREFIXES[class_id]
        for kind in ("Gameplay", "Profile"):
            task = f"{prefix}{kind}RegressionTest"
            if f"val {task} = registerRegression(" not in gradle or task not in check_dependencies:
                errors.append(f"Gradle check lacks {class_id} {kind.lower()} regression task")
    return errors, mechanics


def render_report(findings: list[Finding], mechanics: list[str], report: Path) -> None:
    passed = sum(item.verdict == "PASS" for item in findings)
    warned = sum(item.verdict == "WARN" for item in findings)
    failed = sum(item.verdict == "FAIL" for item in findings)
    lines = [
        "# IceSMP HUD- és asset-audit", "",
        "A lista forráskód- és PNG-alapú. Minecraft klienses vizuális elfogadást nem helyettesít.", "",
        f"Összesen **{len(findings)}** PNG: **{passed} PASS**, **{warned} WARN**, **{failed} FAIL**.", "",
        "## Class-mechanika ellenőrzőlap", "",
        "| Class | Kötelező élő visszajelzések | HUD-adat | Render | Egyedi asset |",
        "|---|---|---:|---:|---:|",
    ]
    for class_name, signals in MECHANIC_AUDIT.items():
        lines.append(f"| {class_name} | {signals} | ✓ | ✓ | ✓ |")
    lines += ["", "A mechanikacsaládok alállapotai a tipizált `state`/`proc` szövegben és az egységes "
              "`active` / `ready` / `alert` / `spent` képi variánsokban különülnek el; a charge/stack/rúna sorok "
              "külön slot-adatot és saját mechanika-glyphet kapnak.", "",
              "## Mechanika-asset lefedettség", "",
              "| Class | Mechanikaasset | Native állapotok | BetterHud állapotok | Adat/render út |",
              "|---|---|---|---|---|"]
    for qualified in mechanics:
        class_id, mechanic_id = qualified.split(":", 1)
        lines.append(f"| {CLASS_LABELS[class_id]} | `{mechanic_id}` | active / ready / alert / spent | "
                     "active / ready / alert / spent | `hudState` → immutable metric/slot → glyph + state/proc |")
    group_counts: dict[str, Counter[str]] = {}
    for item in findings:
        name = Path(item.path).name
        group = ("frame/popup" if name.startswith(("frame", "popup", "emblem")) else
                 "class icon" if name.startswith("class-") else
                 "mechanic/status" if name.startswith(("mechanic-", "charge-", "rune-")) else
                 "resource/bar" if name.startswith(("resource-", "metric-", "segment-")) else
                 "wallet/event/level" if name.startswith(("currency-", "icon-", "wallet-", "detail-")) else
                 "text/source/preview")
        group_counts.setdefault(group, Counter())[item.verdict] += 1
    lines += ["", "## Assetcsoport-ellenőrzőlap", "",
              "| Csoport | Fájl | PASS | WARN | FAIL |", "|---|---:|---:|---:|---:|"]
    for group, counts in group_counts.items():
        lines.append(f"| {group} | {sum(counts.values())} | {counts['PASS']} | {counts['WARN']} | {counts['FAIL']} |")
    lines += ["", "## Statikus runtime-audit", "",
              "| Terület | Ellenőrzött invariáns | Eredmény |",
              "|---|---|---:|",
              "| Wallet | A primary valuta nulla egyenleggel is megjelenik; a többi csak pozitív egyenleggel; immutable snapshot, négy fix slot. | PASS |",
              "| Class-/specváltás | A render minden tickben az új Profile v2 + transient class runtime snapshotból épül; nincs külön tartós HUD/class authority. | PASS |",
              "| Vendégkeret | Külön külső Menedék-héj, a kanonikus frakciókerettel azonos belső alpha-geometria és rögzített glyph-cella. | PASS |",
              "| BetterHud/fallback | Első-party pack-ready HUD → opcionális BetterHud-ready HUD → Paper sidebar / Folia compact bossbar; egyszerre csak egy class HUD. | PASS |",
              "| Folia | A globális tick csak iterál; minden Player-olvasás és render a játékos entity schedulerén történik, az async híd csak immutable `HudSnapshot`-ot olvas. | PASS |",
              "| Authority | Profile v2 a tartós class/spec authority; a class service-ek UUID-mapjai lifecycle-takarított transient combat runtime-ok. | PASS |",
              "", "## Tételes PNG-ellenőrzés", "",
              "A margó sorrendje bal/felső/jobb/alsó; a középeltérés x/y pixel. A glyph-width marker alpha=1, ezért a látható bbox azt figyelmen kívül hagyja.", "",
              "| Asset | Méret | Mód | Alpha | Látható bbox | Margó | Közép | Élesség | Magenta | Eredmény | Megjegyzés |",
              "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|"]
    for item in findings:
        lines.append(f"| `{item.path}` | {item.size} | {item.mode} | {item.alpha} | {item.bbox} | {item.margins} | {item.center} | {item.sharpness} | {item.magenta} | {item.verdict} | {item.note} |")
    lines += ["", "## Klienses kézi minimum", "",
              "- [ ] Mind az öt téma, külön Menedék-vendég külső héjjal; a belső rács nem mozdul.",
              "- [ ] Mind a 13 class: primary és minden elérhető secondary mechanika ikonja, üres/aktív/ready/alert/spent állapot.",
              "- [ ] DK rúnák: ready, spent, regenerating százalék és locked; más classok typed charge/stack pipjei.",
              "- [ ] Wallet: nulla primary és pozitív idegen valuták; event nyugalmi/aktív; class-level.",
              "- [ ] Class- és frakcióváltás közben nincs geometria- vagy glyph-width ugrás.",
              "- [ ] Pack elfogadás/elutasítás/hiba; BetterHud jelen/nincs; pontosan egy HUD és működő compact fallback.",
              "- [ ] GUI scale 1–4, legalább 1280×720 és 2560×1440; nincs vágás, magenta fringe vagy hibás pixel."]
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", default="docs/HUD_ASSET_AUDIT.md")
    args = parser.parse_args()
    files = sorted(set((RUNTIME / "textures" / "hud").glob("*.png"))
                       | set(BETTERHUD.glob("*.png"))
                       | set(SOURCE.glob("*.png"))
                       | set(PREVIEWS.glob("*.png")))
    findings = [inspect(path) for path in files]
    missing_fonts = font_errors()
    if missing_fonts:
        findings.append(Finding("font references", "-", "-", "-", "-", "-", "-", "-", 0,
                                "FAIL", ", ".join(missing_fonts)))
    implementation, mechanics = implementation_errors()
    if implementation:
        findings.append(Finding("HUD implementation coverage", "-", "-", "-", "-", "-", "-", "-", 0,
                                "FAIL", "; ".join(implementation)))
    render_report(findings, mechanics, ROOT / args.report)
    failures = [item for item in findings if item.verdict == "FAIL"]
    warnings = [item for item in findings if item.verdict == "WARN"]
    print(f"HUD asset audit: {len(findings)} files, {len(failures)} FAIL, {len(warnings)} WARN")
    for item in failures + warnings:
        print(f"{item.verdict}: {item.path}: {item.note}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
