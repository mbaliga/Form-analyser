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
    def test_mobile_nav_uses_bocal_two_rail_geometry_and_five_tabs(self) -> None:
        source = bottom_bar_source()
        self.assertEqual(source.count("quadraticTo("), 2, "primary nav must have both Bocal rails")
        self.assertIn("height(150.dp * scale)", source)
        self.assertIn("moveTo(22f * s, 112f * s)", source)
        self.assertIn("quadraticTo(200f * s, 77f * s, 378f * s, 112f * s)", source)
        self.assertIn("moveTo(82.52f * s, 40.12f * s)", source)
        self.assertIn("quadraticTo(200f * s, 24.88f * s, 317.48f * s, 40.12f * s)", source)
        for route in ("HOME", "TRAIN", "PROGRESS", "BODY", "CALENDAR"):
            self.assertIn(f"Routes.{route} to", source)
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        self.assertIn("Text(\n                                label,\n                                color = Hyle.Background,", source)

    def test_context_actions_are_anchored_to_the_upper_rail(self) -> None:
        source = bottom_bar_source()
        self.assertIn("ContextDockButton(", source)
        self.assertIn("18.dp - 22.dp", source)
        self.assertIn("382.dp - 22.dp", source)
        self.assertIn('contentDescription = "Swipe across the upper arc', source)
        self.assertIn('testTag("nav-context-arc")', source)

    def test_upper_rail_preview_and_direct_tab_selection_are_supported(self) -> None:
        source = bottom_bar_source()
        self.assertIn("detectDragGestures(", source)
        self.assertIn("scrubbedDestination = preview", source)
        self.assertIn("onDragEnd = { preview?.let(selectDestination)", source)
        self.assertIn("selectable(selected = selected, role = Role.Tab", source)
        tests = SMOKE_TEST.read_text(encoding="utf-8")
        self.assertIn("fun bocalStyleArcNavigationSelectsCalendar()", tests)
        self.assertIn('clickBottomTabEventually(instrumentation, "Calendar")', tests)


if __name__ == "__main__":
    unittest.main()
