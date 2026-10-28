#!/usr/bin/env python3
"""
FetchJet - Multiplatform App Icon & Logo Asset Generator
Automatically generates native icon formats for Windows (.ico), macOS (.icns),
Linux (.png), Android Adaptive Mipmaps, and iOS AppIcon.appiconset.
"""

import os
import sys
from PIL import Image

def generate_icons(master_logo_path):
    if not os.path.exists(master_logo_path):
        print(f"Error: Master logo file not found at {master_logo_path}")
        return

    img = Image.open(master_logo_path)
    if img.mode != 'RGBA':
        img = img.convert('RGBA')

    print("Generating FetchJet Multiplatform Icon Assets...")

    # 1. Windows .ico
    win_ico_path = "desktopApp/src/main/resources/launcher.ico"
    os.makedirs(os.path.dirname(win_ico_path), exist_ok=True)
    img.save(win_ico_path, format="ICO", sizes=[(16,16), (32,32), (48,48), (64,64), (128,128), (256,256)])
    print(f"  [Windows] Generated {win_ico_path}")

    # 2. Linux .png
    linux_png_path = "desktopApp/src/main/resources/launcher.png"
    img.resize((512, 512), Image.Resampling.LANCZOS).save(linux_png_path)
    print(f"  [Linux] Generated {linux_png_path}")

    # 3. macOS .icns / .png
    mac_icns_path = "desktopApp/src/main/resources/launcher.icns"
    img.save(mac_icns_path)
    print(f"  [macOS] Generated {mac_icns_path}")

    # 4. Android Mipmaps
    android_res = "androidApp/src/main/res"
    android_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }
    for folder, size in android_sizes.items():
        folder_path = os.path.join(android_res, folder)
        os.makedirs(folder_path, exist_ok=True)
        img.resize((size, size), Image.Resampling.LANCZOS).save(os.path.join(folder_path, "ic_launcher.png"))
        img.resize((size, size), Image.Resampling.LANCZOS).save(os.path.join(folder_path, "ic_launcher_round.png"))
    print("  [Android] Generated Mipmap PNGs (mdpi to xxxhdpi)")

    # 5. iOS AppIcon set
    ios_icon_dir = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
    os.makedirs(ios_icon_dir, exist_ok=True)
    img.resize((1024, 1024), Image.Resampling.LANCZOS).save(os.path.join(ios_icon_dir, "AppIcon.png"))
    img.resize((1024, 1024), Image.Resampling.LANCZOS).save(os.path.join(ios_icon_dir, "AppIcon-Dark.png"))
    img.resize((1024, 1024), Image.Resampling.LANCZOS).save(os.path.join(ios_icon_dir, "AppIcon-Tinted.png"))
    print("  [iOS] Generated AppIcon.appiconset (1024x1024 Universal, Dark, Tinted)")

    print("\nAll Multiplatform FetchJet icon assets generated successfully!")

if __name__ == "__main__":
    master_path = sys.argv[1] if len(sys.argv) > 1 else "master_logo_1024.png"
    generate_icons(master_path)
