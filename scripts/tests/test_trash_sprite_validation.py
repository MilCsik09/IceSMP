"""Production pixel constraints remain strict on the CI-pinned Pillow API."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image
from scripts import process_trash_sprite_sheets as audit


class TrashSpriteValidationTest(unittest.TestCase):
    def check_image(self, image):
        with tempfile.TemporaryDirectory() as directory:
            image.save(Path(directory) / "fixture.png")
            with patch.object(audit, "TEXTURE_ROOT", Path(directory)), \
                    patch.object(audit, "process", return_value=(330, 27)), \
                    patch.object(Image.Image, "get_flattened_data", create=True,
                                 side_effect=AssertionError("Pillow 11.3 has no flattened-data API")):
                audit.validate(require_complete=True, check_only=True)

    def test_binary_alpha_and_eight_visible_tones(self):
        image = Image.new("RGBA", (64, 64))
        for i in range(8):
            image.putpixel((i, 0), (i, i, i, 255))
        self.check_image(image)

    def test_transparent_colours_do_not_spend_visible_tone_budget(self):
        image = Image.new("RGBA", (64, 64))
        for i in range(20):
            image.putpixel((i, 0), (i, i, i, 0))
        image.putpixel((0, 1), (255, 255, 255, 255))
        self.check_image(image)

    def test_rejects_missing_or_partial_alpha(self):
        for alpha in (0, 128, 255):
            with self.subTest(alpha=alpha), self.assertRaisesRegex(ValueError, "binary alpha"):
                self.check_image(Image.new("RGBA", (64, 64), (1, 1, 1, alpha)))

    def test_rejects_ninth_visible_tone(self):
        image = Image.new("RGBA", (64, 64))
        for i in range(9):
            image.putpixel((i, 0), (i, i, i, 255))
        with self.assertRaisesRegex(ValueError, "tone budget"):
            self.check_image(image)

    def test_rejects_wrong_dimensions_and_mode(self):
        for image in (Image.new("RGB", (64, 64)), Image.new("RGBA", (32, 32))):
            with self.subTest(mode=image.mode, size=image.size), self.assertRaisesRegex(ValueError, "invalid final"):
                self.check_image(image)

    def test_complete_catalog_requirement_is_retained(self):
        for counts in ((329, 27), (330, 26)):
            with self.subTest(counts=counts), patch.object(audit, "process", return_value=counts), \
                    self.assertRaisesRegex(ValueError, "production Trash asset gate"):
                audit.validate(require_complete=True, check_only=True)


if __name__ == "__main__":
    unittest.main()
