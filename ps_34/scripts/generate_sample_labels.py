from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT_CANDIDATES = [
    "C:/Windows/Fonts/arialbd.ttf",
    "C:/Windows/Fonts/arial.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def _font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = FONT_CANDIDATES if not bold else [FONT_CANDIDATES[0], FONT_CANDIDATES[-1], FONT_CANDIDATES[2]]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def draw_label(lines: list[tuple[str, int, str]], width: int = 720, height: int = 960) -> Image.Image:
    image = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(image)
    y = 24
    for text, size, color in lines:
        draw.text((32, y), text, font=_font(size, bold=True), fill=color)
        y += size + 22
    return image


def generate(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    compliant = [
        ("Premium Protein Biscuit", 44, (20, 40, 90)),
        ("NUTRITIVE BISCUITS WITH MILK", 24, (90, 90, 90)),
        ("", 12, (0, 0, 0)),
        ("Net Weight: 500 g", 30, (0, 0, 0)),
        ("MRP Rs 100 (incl. of all taxes)", 30, (200, 20, 20)),
        ("", 12, (0, 0, 0)),
        ("Manufactured by: ABC Foods Pvt Ltd", 26, (0, 0, 0)),
        ("12 Industrial Area, New Delhi 110001", 24, (0, 0, 0)),
        ("Packed by: ABC Foods Pvt Ltd", 26, (0, 0, 0)),
        ("Manufactured Date: 08/2026", 26, (0, 0, 0)),
        ("Best Before: Nine months from packing", 22, (0, 0, 0)),
        ("", 12, (0, 0, 0)),
        ("For consumer complaints, contact:", 24, (0, 0, 0)),
        ("Customer Care: 1800-123-4567", 26, (0, 0, 0)),
        ("care@abcfoods.in", 22, (0, 0, 0)),
    ]
    compliant_image = draw_label(compliant)
    compliant_image.save(output_dir / "sample_compliant.jpg", quality=95)

    non_compliant = [
        ("Premium Protein Biscuit", 44, (20, 40, 90)),
        ("NUTRITIVE BISCUITS WITH MILK", 24, (90, 90, 90)),
        ("", 12, (0, 0, 0)),
        ("Net Weight: 500 g", 30, (0, 0, 0)),
        ("Manufactured by: ABC Foods Pvt Ltd", 26, (0, 0, 0)),
        ("12 Industrial Area, New Delhi 110001", 24, (0, 0, 0)),
        ("Mfg: 08/2026", 26, (0, 0, 0)),
    ]
    non_compliant_image = draw_label(non_compliant)
    non_compliant_image.save(output_dir / "sample_non_compliant.jpg", quality=95)

    print(f"Wrote {output_dir / 'sample_compliant.jpg'}")
    print(f"Wrote {output_dir / 'sample_non_compliant.jpg'}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate synthetic package label images for demo/testing")
    parser.add_argument("-o", "--output", default="./samples")
    args = parser.parse_args()
    generate(Path(args.output))