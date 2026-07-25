#!/usr/bin/env python3
"""A jar-datapack advancement-JSON-jainak generalasa az AdvancementService NODES listajabol.

Az igazsag EGYETLEN forrasa a Java NODES lista; a
src/main/resources/datapack/data/icesmp/advancement/*.json fajlok ebbol szarmaznak. Uj
csomopont felvetele: irj egy `new Node(...)` sort az AdvancementService-be, fusd le ezt a
scriptet, majd a check_consistency.py-t (az FAIL-el, ha a ket oldal szetdriftel).

A ToastUtil harom fix toast-bejegyzese (toast_quest/milestone/discovery) NEM innen jon: azok
kezzel irt, show_toast:true JSON-ok, es a script nem nyul hozzajuk.

Hasznalat:
    python3 scripts/gen_advancements.py            # ir
    python3 scripts/gen_advancements.py --check    # csak ellenorzi, hogy naprakesz-e
"""

import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
SERVICE = REPO / "src/main/java/hu/taliann/icesmp/managers/AdvancementService.java"
OUT_DIR = REPO / "src/main/resources/datapack/data/icesmp/advancement"
NS = "icesmp"

# A ToastUtil fix bejegyzesei — kezzel gondozottak, a generator hagyja bekeen.
HAND_WRITTEN = {"toast_quest", "toast_milestone", "toast_discovery"}

# new Node("id", parent, "title", "description", "icon", "frame", hidden, background)
# A `parent`/`background` lehet `null` vagy string-literal; a Java-argumentumok tobb sorra
# tordelve is allhatnak, ezert a teljes hivast egy stringkent nezzuk.
NODE_CALL = re.compile(r"new\s+Node\s*\((.*?)\)\s*(?:,|\))", re.DOTALL)


def split_args(raw):
    """Vesszo szerinti szetvagas, a string-literalokon BELULI vesszot megtartva."""
    args, buf, in_string, escaped = [], [], False, False
    for ch in raw:
        if in_string:
            buf.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
            buf.append(ch)
        elif ch == ",":
            args.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
    if buf:
        args.append("".join(buf).strip())
    return args


# A Java-escape-ek celzott visszafejtese. NEM hasznalhatunk `unicode_escape`-et: az a
# nem-ASCII karaktereket (magyar ekezetek) mojibake-re rontja.
JAVA_ESCAPES = {"\\": "\\", '"': '"', "'": "'", "n": "\n", "t": "\t", "r": "\r"}


def unquote(arg):
    """Java string-literal -> Python str; `null` -> None. Tobb egymas utani literal fuzese."""
    if arg == "null":
        return None
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', arg)
    if not parts:
        raise SystemExit(f"nem ertelmezheto Node-argumentum: {arg!r}")
    return re.sub(r"\\(.)", lambda m: JAVA_ESCAPES.get(m.group(1), m.group(0)), "".join(parts))


def parse_nodes():
    source = SERVICE.read_text(encoding="utf-8")
    # Csak a NODES lista torzse erdekes (a javadoc-peldak/kommentek ne zavarjanak be).
    start = source.index("private static final List<Node> NODES")
    body = source[start:source.index("private static volatile AdvancementService instance", start)]
    nodes = []
    for match in NODE_CALL.finditer(body):
        args = split_args(match.group(1))
        if len(args) != 8:
            raise SystemExit(f"vart 8 Node-argumentumot, kaptam {len(args)}: {args}")
        nodes.append({
            "id": unquote(args[0]),
            "parent": unquote(args[1]),
            "title": unquote(args[2]),
            "description": unquote(args[3]),
            "icon": unquote(args[4]),
            "frame": unquote(args[5]),
            "hidden": args[6].strip() == "true",
            "background": unquote(args[7]),
        })
    if not nodes:
        raise SystemExit("egyetlen Node-ot sem talaltam az AdvancementService-ben")
    return nodes


def build(node):
    doc = {}
    if node["parent"]:
        doc["parent"] = f"{NS}:{node['parent']}"
    doc["criteria"] = {"granted": {"trigger": "minecraft:impossible"}}
    doc["requirements"] = [["granted"]]
    display = {
        "icon": {"id": node["icon"]},
        "title": {"text": node["title"]},
        "description": {"text": node["description"]},
        "frame": node["frame"],
        # Szandekos: a fa-bejegyzesek nem toastolnak es nem irnak chatbe — a toast-reteg
        # kulon, celzott (ToastUtil), a grant-pontok pedig maguk uzennek a jatekosnak.
        "show_toast": False,
        "announce_to_chat": False,
        "hidden": node["hidden"],
    }
    if node["background"]:
        display["background"] = node["background"]
    doc["display"] = display
    return json.dumps(doc, ensure_ascii=False, indent=2) + "\n"


def main():
    check_only = "--check" in sys.argv
    nodes = parse_nodes()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    stale, written = [], 0
    generated = set()
    for node in nodes:
        generated.add(node["id"])
        target = OUT_DIR / f"{node['id']}.json"
        content = build(node)
        if target.exists() and target.read_text(encoding="utf-8") == content:
            continue
        if check_only:
            stale.append(node["id"])
        else:
            target.write_text(content, encoding="utf-8")
            written += 1

    for existing in sorted(OUT_DIR.glob("*.json")):
        name = existing.stem
        if name in generated or name in HAND_WRITTEN:
            continue
        if check_only:
            stale.append(f"{name} (arva JSON)")
        else:
            existing.unlink()
            print(f"torolve (arva): {name}.json")

    if check_only:
        if stale:
            print("NAPRAKESZ NEM: " + ", ".join(stale))
            print("futtasd: python3 scripts/gen_advancements.py")
            return 1
        print(f"OK — {len(nodes)} advancement-JSON naprakesz")
        return 0
    print(f"OK — {len(nodes)} csomopont, {written} JSON irva/frissitve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
