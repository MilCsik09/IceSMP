"""Internal developer artifact asset linkage; runs in the existing regression discovery."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

class DevArtifactAssetsTest(unittest.TestCase):
    def test_every_code_defined_state_resolves_to_modern_item_model(self):
        source = (ROOT / 'src/main/java/hu/taliann/icesmp/dev/artifact/WorldWeaverArtifactBehavior.java').read_text()
        geometries = []
        for state in ('idle', 'subject', 'thread', 'canon'):
            name = 'dev_world_weaver_' + state
            self.assertIn('icesmp:' + name, source)
            item = json.loads((ROOT / 'resource-pack/assets/icesmp/items' / (name + '.json')).read_text())
            self.assertEqual(item['model'], {'type': 'minecraft:model', 'model': 'icesmp:item/' + name})
            model = json.loads((ROOT / 'resource-pack/assets/icesmp/models/item' / (name + '.json')).read_text())
            self.assertEqual(model['parent'], 'icesmp:item/dev_world_weaver_base')
            geometries.append(model['textures'])
        self.assertEqual(len({json.dumps(t, sort_keys=True) for t in geometries}), 4)
        base = json.loads((ROOT / 'resource-pack/assets/icesmp/models/item/dev_world_weaver_base.json').read_text())
        self.assertGreaterEqual(len(base['elements']), 8)
        self.assertNotIn('overrides', base)
