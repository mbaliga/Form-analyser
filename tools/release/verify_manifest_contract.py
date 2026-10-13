#!/usr/bin/env python3
"""Audit security-relevant components and permissions in aapt2's merged-manifest dump."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ALLOWED_PERMISSIONS = {
    "android.permission.CAMERA",
    "android.permission.VIBRATE",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "xyz.mdhv.formanalyser.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
ANDROID_ATTRIBUTE = re.compile(r'android:name\([^)]*\)="([^"]+)"')
EXPORTED_ATTRIBUTE = re.compile(r'android:exported\([^)]*\)=(true|false)')
DEBUGGABLE_ATTRIBUTE = re.compile(r'android:debuggable\([^)]*\)=(true|false)')
TEST_ONLY_ATTRIBUTE = re.compile(r'android:testOnly\([^)]*\)=(true|false)')
PERMISSION_ATTRIBUTE = re.compile(r'android:permission\([^)]*\)="([^"]+)"')
ELEMENT = re.compile(r"^(\s*)E: ([a-zA-Z0-9_-]+)(?:\s|$)")
COMPONENT_TAGS = {"activity", "activity-alias", "service", "receiver", "provider"}
PERMISSION_TAGS = {"uses-permission", "uses-permission-sdk-23"}
APPLICATION_TAGS = {"application"}
MAIN_ACTIVITY = "xyz.mdhv.formanalyser.app.MainActivity"
PROFILE_RECEIVER = "androidx.profileinstaller.ProfileInstallReceiver"


def blocks(lines: list[str], tags: set[str]) -> list[tuple[str, list[str]]]:
    found: list[tuple[str, list[str]]] = []
    index = 0
    while index < len(lines):
        match = ELEMENT.match(lines[index])
        if not match or match.group(2) not in tags:
            index += 1
            continue
        indent = len(match.group(1))
        tag = match.group(2)
        end = index + 1
        while end < len(lines):
            child = ELEMENT.match(lines[end])
            if child and len(child.group(1)) <= indent:
                break
            end += 1
        found.append((tag, lines[index:end]))
        index = end
    return found


def attribute(pattern: re.Pattern[str], text: str) -> str | None:
    match = pattern.search(text)
    return match.group(1) if match else None


def audit(manifest_dump: str) -> list[str]:
    errors: list[str] = []
    lines = manifest_dump.splitlines()
    permissions: set[str] = set()
    for _, block in blocks(lines, PERMISSION_TAGS):
        name = attribute(ANDROID_ATTRIBUTE, "\n".join(block))
        if name:
            permissions.add(name)
    unexpected = permissions - ALLOWED_PERMISSIONS
    missing = ALLOWED_PERMISSIONS - permissions
    if unexpected:
        errors.append(f"Unexpected merged permissions: {', '.join(sorted(unexpected))}")
    if missing:
        errors.append(f"Expected permissions missing from merged manifest: {', '.join(sorted(missing))}")

    applications = blocks(lines, APPLICATION_TAGS)
    if len(applications) != 1:
        errors.append("Exactly one application element is required")
    else:
        application_text = "\n".join(applications[0][1])
        if attribute(DEBUGGABLE_ATTRIBUTE, application_text) == "true":
            errors.append("Release application must not be debuggable")
        if attribute(TEST_ONLY_ATTRIBUTE, application_text) == "true":
            errors.append("Release application must not be marked testOnly")

    components = []
    for tag, block in blocks(lines, COMPONENT_TAGS):
        text = "\n".join(block)
        name = attribute(ANDROID_ATTRIBUTE, text)
        exported = attribute(EXPORTED_ATTRIBUTE, text)
        permission = attribute(PERMISSION_ATTRIBUTE, text)
        if not name:
            errors.append(f"Merged {tag} has no android:name")
            continue
        if exported is None:
            errors.append(f"Merged {tag} {name} omits explicit android:exported")
            continue
        components.append((tag, name, exported, permission, text))

    launchers = [
        item for item in components
        if item[0] == "activity"
        and item[1] == MAIN_ACTIVITY
        and item[2] == "true"
        and "android.intent.action.MAIN" in item[4]
        and "android.intent.category.LAUNCHER" in item[4]
    ]
    if len(launchers) != 1:
        errors.append("Exactly one exported Crocodyl MAIN/LAUNCHER activity is required")

    for tag, name, exported, permission, _ in components:
        if exported != "true":
            continue
        if tag == "activity" and name == MAIN_ACTIVITY and permission is None:
            continue
        if tag == "receiver" and name == PROFILE_RECEIVER and permission == "android.permission.DUMP":
            continue
        errors.append(f"Unexpected exported component: {tag} {name} (permission={permission})")

    profile_receivers = [item for item in components if item[1] == PROFILE_RECEIVER]
    for _, name, exported, permission, _ in profile_receivers:
        if exported != "true" or permission != "android.permission.DUMP":
            errors.append(f"ProfileInstallReceiver must remain exported only behind android.permission.DUMP: {name}")

    return errors


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {Path(sys.argv[0]).name} AAPT2_MANIFEST_DUMP", file=sys.stderr)
        return 2
    errors = audit(Path(sys.argv[1]).read_text())
    if errors:
        print("Merged Android manifest contract failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("Merged Android manifest contract passed: permissions and exported components are audited.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
