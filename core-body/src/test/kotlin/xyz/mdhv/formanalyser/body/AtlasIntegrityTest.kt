package xyz.mdhv.formanalyser.body

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Phase 3 gate: these tests are the definition of "tappable atlas". */
class AtlasIntegrityTest {

    @Test fun all52IdsPresentExactlyOnceAcrossSides() {
        assertEquals(52, BodyAtlas.regions.size)
        assertEquals(RegionIds.ALL.toSet(), BodyAtlas.regions.map { it.id }.toSet())
        assertEquals(52, BodyAtlas.regions.map { it.id }.toSet().size)
        // faces agree with the RegionIds contract
        for (r in BodyAtlas.regions) {
            assertEquals(RegionIds.META[r.id]!!.face, r.face, "face mismatch for ${r.id}")
        }
    }

    @Test fun leftRightPathsAreExactMirrors() {
        for (r in BodyAtlas.regions.filter { it.id.endsWith("_l") }) {
            val twin = BodyAtlas.byId[r.id.dropLast(2) + "_r"]
            assertNotNull(twin, "missing right twin for ${r.id}")
            assertEquals(BodyAtlas.WIDTH - r.x - r.w, twin.x, 1e-9, "mirror x for ${r.id}")
            assertEquals(r.y, twin.y, 1e-9)
            assertEquals(r.w, twin.w, 1e-9)
            assertEquals(r.h, twin.h, 1e-9)
        }
    }

    @Test fun everyRegionBoundingBoxAtLeast60x60Units() {
        for (r in BodyAtlas.regions) {
            assertTrue(r.w >= 60.0, "${r.id} width ${r.w} < 60")
            assertTrue(r.h >= 60.0, "${r.id} height ${r.h} < 60")
        }
    }

    @Test fun noOverlapsExactPairwise() {
        // Stronger than the brief's 200×400 grid sample: exact pairwise rect intersection.
        val list = BodyAtlas.regions
        for (i in list.indices) for (j in i + 1 until list.size) {
            assertTrue(!list[i].overlaps(list[j]), "${list[i].id} overlaps ${list[j].id}")
        }
    }

    @Test fun everyCentroidHitTestsToItsOwnRegion() {
        for (r in BodyAtlas.regions) {
            val hit = BodyAtlas.hitTest(r.face, r.centerX, r.centerY)
            assertEquals(r.id, hit?.id, "centroid of ${r.id} hit ${hit?.id}")
        }
    }

    @Test fun regionsFitInsideTheViewport() {
        for (r in BodyAtlas.regions) {
            assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.w <= BodyAtlas.WIDTH && r.y + r.h <= BodyAtlas.HEIGHT, r.id)
        }
    }
}
