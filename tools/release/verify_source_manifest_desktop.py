#!/usr/bin/env python3
"""Guard the desktop-window settings in the source AndroidManifest.xml.

The app runs in freeform/desktop windows only while MainActivity keeps its <layout> sizing and
nothing locks orientation or disables resizing. This reads the source manifest, not the merged one
(see verify_manifest_contract.py for that).
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
MAIN_ACTIVITY = ".MainActivity"
LAYOUT_ATTRIBUTES = ("defaultWidth", "defaultHeight", "minWidth", "minHeight")
DEFAULT_MANIFEST = Path("app-android/src/main/AndroidManifest.xml")


def audit(manifest_xml: str) -> list[str]:
    """Return a list of problems; empty means the desktop-window settings are intact."""
    problems: list[str] = []
    root = ET.fromstring(manifest_xml)
    application = root.find("application")
    if application is None:
        return ["no <application> element"]

    if application.get(ANDROID + "resizeableActivity") == "false":
        problems.append('<application> sets android:resizeableActivity="false"')
    if application.get(ANDROID + "screenOrientation") is not None:
        problems.append("<application> sets android:screenOrientation")

    main = None
    for activity in application.findall("activity"):
        name = activity.get(ANDROID + "name")
        if activity.get(ANDROID + "resizeableActivity") == "false":
            problems.append(f'{name} sets android:resizeableActivity="false"')
        if activity.get(ANDROID + "screenOrientation") is not None:
            problems.append(f"{name} sets android:screenOrientation")
        if name == MAIN_ACTIVITY:
            main = activity

    if main is None:
        problems.append(f"{MAIN_ACTIVITY} not found")
        return problems
    layout = main.find("layout")
    if layout is None:
        problems.append(f"{MAIN_ACTIVITY} has no <layout> element")
        return problems
    for attribute in LAYOUT_ATTRIBUTES:
        value = layout.get(ANDROID + attribute)
        if not value:
            problems.append(f"<layout> is missing android:{attribute}")
    return problems


def main(argv: list[str]) -> int:
    path = Path(argv[1]) if len(argv) > 1 else DEFAULT_MANIFEST
    problems = audit(path.read_text(encoding="utf-8"))
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"OK: {path} keeps its desktop-window settings")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
