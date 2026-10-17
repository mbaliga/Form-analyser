package xyz.mdhv.formanalyser.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.formanalyser.body.BodyAtlas
import xyz.mdhv.formanalyser.body.RegionIds

class BodyRegionDisplayNameTest {
    @Test
    fun everyBodyAtlasRegionHasAnAccessibleDistinctLabel() {
        val regions = BodyAtlas.regions
        val labels = regions.map { bodyRegionDisplayName(it.id) }

        assertEquals(RegionIds.ALL.toSet(), regions.map { it.id }.toSet())
        assertTrue(labels.all { it.isNotBlank() })
        assertFalse(labels.any { '_' in it })
        assertEquals(
            "Every body region should be distinguishable in the picker",
            labels.size,
            labels.toSet().size,
        )
    }

    @Test
    fun commonCenterlineRegionsUsePlainLanguage() {
        assertEquals("Front of neck", bodyRegionDisplayName(RegionIds.NECK_ANT))
        assertEquals("Back of neck", bodyRegionDisplayName(RegionIds.NECK_POST))
        assertEquals("Upper abdominals", bodyRegionDisplayName(RegionIds.ABS_UPPER))
    }
}
