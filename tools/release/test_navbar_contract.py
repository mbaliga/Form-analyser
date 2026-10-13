"""Keep Crocodyl's mobile primary navigation aligned with Bocal's floating dock."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAIN_ACTIVITY = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/MainActivity.kt"
ARC_DOCK = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ArcNavigationDock.kt"
SMOKE_TEST = ROOT / "app-android/src/androidTest/kotlin/xyz/mdhv/formanalyser/app/ProductionSmokeTest.kt"


def bottom_bar_source() -> str:
    return "\n".join(
        path.read_text(encoding="utf-8") for path in (MAIN_ACTIVITY, ARC_DOCK)
    )


class NavbarContractTest(unittest.TestCase):
    def test_mobile_nav_uses_bocal_floating_dock_and_five_tabs(self) -> None:
        source = bottom_bar_source()
        self.assertIn("moveTo(22f * scale, 112f * scale)", source)
        self.assertIn("moveTo(82.52f * scale, 40.12f * scale)", source)
        self.assertIn("drawTextOnPath(", source)
        self.assertIn('description = "Quick add"', source)
        self.assertIn('description = "Settings"', source)
        for route in ("HOME", "TRAIN", "PROGRESS", "BODY", "CALENDAR"):
            self.assertIn(f"Routes.{route} to", source)
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        self.assertIn("Text(visibleLabel, color = Color(0xFF17181D)", source)
        self.assertIn("contentDescription = label", source)

    def test_destination_icons_are_above_labels_and_active_tab_is_filled(self) -> None:
        source = bottom_bar_source()
        self.assertIn("Icon(icon, label", source)
        self.assertIn("if (selected) Modifier.background(", source)
        self.assertIn("graphicsLayer { rotationZ = rotation }", source)

    def test_direct_tab_selection_is_exercised_in_android_smoke_test(self) -> None:
        source = bottom_bar_source()
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        self.assertIn('.semantics(mergeDescendants = true) { contentDescription = label }', source)
        tests = SMOKE_TEST.read_text(encoding="utf-8")
        self.assertIn("fun bocalStyleDockNavigationSelectsCalendar()", tests)
        self.assertIn('clickBottomTabEventually(instrumentation, "Calendar")', tests)

    def test_large_font_labels_keep_full_accessible_names_and_visual_capture_is_opt_in(self) -> None:
        source = bottom_bar_source()
        self.assertIn('"Progress" -> "Prog."', source)
        self.assertIn('"Calendar" -> "Cal."', source)
        self.assertIn('contentDescription = label', source)
        tests = SMOKE_TEST.read_text(encoding="utf-8")
        self.assertIn('getString("captureDockVisual") == "true"', tests)
        self.assertIn("MediaStore.Images.Media.RELATIVE_PATH", tests)


if __name__ == "__main__":
    unittest.main()
