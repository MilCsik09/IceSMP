#!/usr/bin/env python3
import json
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
subprocess.run([
    'python3', str(root / 'scripts/check_player_profile_authority.py'),
    '--root', str(root),
], check=True)
with tempfile.TemporaryDirectory() as td:
    report = Path(td) / 'report.json'
    subprocess.run([
        'python3', str(root / 'scripts/check_player_profile_authority.py'),
        '--root', str(root), '--write-report', str(report),
    ], check=True)
    data = json.loads(report.read_text(encoding='utf-8'))
    assert not data['unknown']
    assert not data['stale']
    assert not data['invalid']
    assert data['transition_count'] == 0
    assert not data['transitions']
print('PlayerProfile authority guard self-test: PASS')
