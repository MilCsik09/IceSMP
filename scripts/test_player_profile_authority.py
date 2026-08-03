#!/usr/bin/env python3
import json,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
subprocess.run(['python3',str(root/'scripts/check_player_profile_authority.py'),'--root',str(root)],check=True)
with tempfile.TemporaryDirectory() as td:
    report=Path(td)/'report.json'
    subprocess.run(['python3',str(root/'scripts/check_player_profile_authority.py'),'--root',str(root),'--write-report',str(report)],check=True)
    data=json.loads(report.read_text())
    assert not data['unknown'] and not data['stale'] and not data['invalid']
print('PlayerProfile authority guard self-test: PASS')
