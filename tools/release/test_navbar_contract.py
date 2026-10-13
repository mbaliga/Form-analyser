"""Keep Crocodyl's mobile primary navigation aligned with Bocal's arc interaction."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAIN_ACTIVITY = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/MainActivity.kt"
SMOKE_TEST = ROOT / "app-android/src/androidTest/kotlin/xyz/mdhv/formanalyser/app/ProductionSmokeTest.kt"


def bottom_bar_source() -> str:
    source = MAIN_ACTIVITY.read_text(encoding="utf-8")
    start = source.index("private fun BottomBar(")
    end = source.index("/**\n * Top bar for every non-tab destination", start)
    return source[start:end]


class NavbarContractTest(unittest.TestCase):
    def test_mobile_nav_is_one_arc_with_five_direct_tap_destinations(self) -> None:
        source = bottom_bar_source()
        self.assertEqual(source.count("quadraticTo("), 1, "primary nav must have a single arc")
        self.assertIn("moveTo(22f * s, 62f * s)", source)
        self.assertIn("quadraticTo(200f * s, 27f * s, 378f * s, 62f * s)", source)
        for route in ("HOME", "TRAIN", "PROGRESS", "BODY", "CALENDAR"):
            self.assertIn(f"Routes.{route} to", source)
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        self.assertIn("Text(\n                                label,\n                                color = Hyle.Background,", source)

    def test_context_actions_remain_separate_from_the_navigation_arc(self) -> None:
        source = bottom_bar_source()
        self.assertIn('Icons.Filled.CameraAlt to "Train"', source)
        self.assertIn('Icons.Filled.Add to "Add"', source)
        self.assertNotIn("ContextDockButton(", source)
        self.assertNotIn("nav-context-arc", source)

    def test_no_scrub_gesture_and_direct_tab_selection_is_smoke_covered(self) -> None:
        source = bottom_bar_source()
        self.assertNotIn("detectDragGestures", source)
        self.assertNotIn("scrubbedDestination", source)
        tests = SMOKE_TEST.read_text(encoding="utf-8")
        self.assertIn("fun bocalStyleArcNavigationSelectsCalendar()", tests)
        self.assertIn('clickBottomTabEventually(instrumentation, "Calendar")', tests)


if __name__ == "__main__":
    unittest.main()
