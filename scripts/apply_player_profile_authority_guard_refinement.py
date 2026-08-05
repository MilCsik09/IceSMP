#!/usr/bin/env python3
"""Remove non-authoritative key declarations from the PDC authority scan."""
from pathlib import Path

path = Path(__file__).resolve().parent / "check_player_profile_authority.py"
text = path.read_text(encoding="utf-8")
old = '''    "PLAYER_PDC": re.compile(
        r"(?:getPersistentDataContainer\\s*\\(|PersistentDataContainer|NamespacedKey\\s*\\()"
    ),
'''
new = '''    # A key declaration or PersistentDataContainer local variable is not an authority.
    # Scan only actual container access; the receiver and operation are then classified below.
    "PLAYER_PDC": re.compile(r"getPersistentDataContainer\\s*\\("),
'''
if old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
elif new not in text:
    raise SystemExit("authority scan anchor not found")
print("PlayerProfile authority PDC scan refined.")
