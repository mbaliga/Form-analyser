"""Keep Crocodyl's mobile primary navigation aligned with Bocal's curved stage."""

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
    def test_mobile_nav_uses_bocal_curved_stage_and_five_tabs(self) -> None:
        source = bottom_bar_source()
        self.assertIn('"RECURVE  ·  ${shownContext.uppercase()}"', source)
        self.assertIn("drawTextOnPath(contextText", source)
        self.assertIn("dockSeat(18f, 50.79f", source)
        self.assertIn("rotationZ = -11.37f", source)
        self.assertIn("rotationZ = 11.37f", source)
        self.assertIn("detectDragGestures(", source)
        self.assertIn("previewDestination.value = it", source)
        self.assertIn("previewDestination.value?.let(onSelect)", source)
        self.assertIn("DockSeat(46.8f, 107.4f", source)
        self.assertIn("DockSeat(353.2f, 107.4f", source)
        for route in ("HOME", "TRAIN", "PROGRESS", "BODY", "CALENDAR"):
            self.assertIn(f"Routes.{route} to", source)
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        self.assertIn("contentDescription = label", source)

    def test_destination_icons_are_above_labels_and_active_tab_is_filled(self) -> None:
        source = bottom_bar_source()
        self.assertIn("Icon(icon, null", source)
        self.assertIn("if (selected) Modifier.background(Color(0xFFE6E2D8))", source)
        self.assertIn("if (active)", source)
        self.assertIn('label = "Quick add"', source)
        self.assertIn('label = "Settings"', source)

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
