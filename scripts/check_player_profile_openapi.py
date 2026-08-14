#!/usr/bin/env python3
import json,sys
from pathlib import Path
p=Path(sys.argv[1] if len(sys.argv)>1 else 'docs/openapi/player-profile-v1.yaml')
data=json.loads(p.read_text(encoding='utf-8'))
assert data.get('openapi','').startswith('3.1')
paths=data.get('paths',{})
required=['/api/v1/health','/api/v1/players/{uuid}','/api/v1/players/{uuid}/public','/api/v1/players/{uuid}/sections/{section}','/api/v1/players/by-name/{name}']
for path in required:
    assert path in paths and 'get' in paths[path], path
schemes=data.get('components',{}).get('securitySchemes',{})
assert schemes.get('bearerAuth',{}).get('scheme')=='bearer'
text=p.read_text()
for forbidden in ('password','rawYaml','filePath','stackTrace','accessToken'):
    assert forbidden not in text, forbidden
print(f'PlayerProfile OpenAPI contract: PASS ({len(paths)} paths)')
