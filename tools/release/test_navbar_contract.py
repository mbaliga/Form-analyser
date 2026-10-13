"""Keep Crocodyl's mobile primary navigation aligned with Bocal's floating dock."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAIN_ACTIVITY = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/MainActivity.kt"
ARC_DOCK = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ArcNavigationDock.kt"
SMOKE_TEST = ROOT / "app-android/src/androidTest/kotlin/xyz/mdhv/formanalyser/app/ProductionSmokeTest.kt"
HERO = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/components/AthleteVisuals.kt"


def bottom_bar_source() -> str:
    return "\n".join(
        path.read_text(encoding="utf-8") for path in (MAIN_ACTIVITY, ARC_DOCK)
    )


class NavbarContractTest(unittest.TestCase):
    def test_mobile_nav_uses_bocal_floating_dock_and_five_tabs(self) -> None:
        source = bottom_bar_source()
        self.assertIn("RoundedCornerShape(24.dp)", source)
        self.assertIn("heightIn(min = 68.dp)", source)
        self.assertIn("Color(0xFF182622)", source)
        for route in ("HOME", "TRAIN", "PROGRESS", "BODY", "CALENDAR"):
            self.assertIn(f"Routes.{route} to", source)
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        self.assertIn("Text(\n                    label,", source)
        self.assertIn("contentDescription = label", source)

    def test_destination_icons_are_above_labels_and_active_tab_is_filled(self) -> None:
        source = bottom_bar_source()
        self.assertIn("Icon(icon, label", source)
        self.assertIn("if (selected) Modifier.background(Color(0xFF182622))", source)
        self.assertIn("Hyle.AccentBright else Color(0xFF77777D)", source)

    def test_direct_tab_selection_is_exercised_in_android_smoke_test(self) -> None:
        source = bottom_bar_source()
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        self.assertIn('.semantics(mergeDescendants = true) { contentDescription = label }', source)
        tests = SMOKE_TEST.read_text(encoding="utf-8")
        self.assertIn("fun bocalStyleDockNavigationSelectsCalendar()", tests)
        self.assertIn('clickBottomTabEventually(instrumentation, "Calendar")', tests)

    def test_full_labels_keep_accessible_names_and_visual_capture_is_opt_in(self) -> None:
        source = bottom_bar_source()
        self.assertIn('maxLines = 1', source)
        self.assertIn('fontSize = 9.sp', source)
        self.assertIn('contentDescription = label', source)
        tests = SMOKE_TEST.read_text(encoding="utf-8")
        self.assertIn('getString("captureDockVisual") == "true"', tests)
        self.assertIn("MediaStore.Images.Media.RELATIVE_PATH", tests)

    def test_home_actions_stack_at_compact_width_and_large_font(self) -> None:
        hero = HERO.read_text(encoding="utf-8")
        self.assertIn("val stackedActions = compactWidth || largeFont", hero)
        self.assertGreaterEqual(hero.count("if (stackedActions)"), 2)
        self.assertIn('Text("Train", maxLines = 1, softWrap = false)', hero)
        self.assertIn('Text("Score", maxLines = 1, softWrap = false)', hero)


if __name__ == "__main__":
    unittest.main()
