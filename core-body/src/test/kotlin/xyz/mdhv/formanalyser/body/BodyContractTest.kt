package xyz.mdhv.formanalyser.body

import xyz.mdhv.formanalyser.model.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionIdsTest {
    @Test fun fiftyTwoUniqueIdsWithSides() {
        assertEquals(52, RegionIds.ALL.size)
        assertEquals(52, RegionIds.ALL.toSet().size) // unique
        assertEquals(25, RegionIds.META.values.count { it.face == BodyFace.FRONT })
        assertEquals(27, RegionIds.META.values.count { it.face == BodyFace.BACK })
        // side inference
        assertEquals(Side.LEFT, RegionIds.META[RegionIds.DELT_ANT_L]!!.side)
        assertEquals(Side.RIGHT, RegionIds.META[RegionIds.ROTATOR_CUFF_R]!!.side)
        assertEquals(Side.CENTER, RegionIds.META[RegionIds.NECK_ANT]!!.side)
    }
}

class ChipResolverTest {
    @Test fun handednessAwareBothWays() {
        assertTrue(SorenessChipResolver.resolve(SorenessChip.DRAW_SHOULDER, Handedness.RH).contains(RegionIds.ROTATOR_CUFF_R))
        assertTrue(SorenessChipResolver.resolve(SorenessChip.DRAW_SHOULDER, Handedness.LH).contains(RegionIds.ROTATOR_CUFF_L))
        assertTrue(SorenessChipResolver.resolve(SorenessChip.BOW_SHOULDER, Handedness.RH).contains(RegionIds.ROTATOR_CUFF_L))
        assertTrue(SorenessChipResolver.resolve(SorenessChip.BOW_ARM, Handedness.RH).contains(RegionIds.BICEPS_L))
        assertTrue(SorenessChipResolver.resolve(SorenessChip.BOW_ARM, Handedness.LH).contains(RegionIds.BICEPS_R))
    }

    @Test fun everyChipMapsToValidRegions() {
        for (chip in SorenessChip.entries) {
            for (h in Handedness.entries) {
                val ids = SorenessChipResolver.resolve(chip, h)
                assertTrue(ids.isNotEmpty(), "$chip/$h empty")
                ids.forEach { assertTrue(RegionIds.isValid(it), "$chip/$h → invalid region $it") }
            }
        }
    }
}
