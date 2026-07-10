package xyz.mdhv.formanalyser.coach

import xyz.mdhv.formanalyser.wellness.InjurySummary
import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.ReadinessLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionTest {

    private fun richFacts() = CoachFacts(
        readinessLevel = ReadinessLevel.CAUTION,
        readinessReasons = listOf("Poor sleep", "high-stress window"),
        acwr = 1.42,
        load = ShotLoadSummary(sessions7d = 4, shots7d = 320, sessions28d = 15, shots28d = 1200),
        activeInjuries = listOf(InjurySummary(listOf("rotator_cuff_r"), severity = 3)),
        medications = listOf("ibuprofen 400mg"),
        moodNote = "felt anxious about work",
        lifeEventNote = "breakup last week",
        cyclePhase = "luteal",
    )

    private val PRIVATE_CONTENT = listOf("anxious about work", "breakup last week", "luteal")
    private val MEDICAL_CONTENT = "ibuprofen"

    @Test
    fun `cloud never leaks PRIVATE`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.CLOUD, medicalGrant = true, keepPrivate = false))
        // keepPrivate=false must NOT open PRIVATE for cloud.
        for (c in PRIVATE_CONTENT) assertFalse(res.factsheet.contains(c), "PRIVATE '$c' leaked to cloud")
        assertTrue(res.kept.none { it.privacy == PrivacyClass.PRIVATE })
        assertTrue(res.withheld.any { it.key == "mood" })
        assertTrue(res.withheld.any { it.key == "life_event" })
        assertTrue(res.withheld.any { it.key == "cycle" })
    }

    @Test
    fun `export never leaks PRIVATE`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.EXPORT, medicalGrant = true, keepPrivate = false))
        for (c in PRIVATE_CONTENT) assertFalse(res.factsheet.contains(c))
        assertTrue(res.kept.none { it.privacy == PrivacyClass.PRIVATE })
    }

    @Test
    fun `medical withheld without grant`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.CLOUD, medicalGrant = false))
        assertFalse(res.factsheet.contains(MEDICAL_CONTENT))
        assertTrue(res.withheld.any { it.key == "medication" && it.privacy == PrivacyClass.MEDICAL })
    }

    @Test
    fun `medical included with grant`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.CLOUD, medicalGrant = true))
        assertTrue(res.factsheet.contains(MEDICAL_CONTENT))
        assertFalse(res.wasWithheld("medication"))
    }

    @Test
    fun `medical gated even on-device without grant`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.ON_DEVICE, medicalGrant = false, keepPrivate = false))
        assertFalse(res.factsheet.contains(MEDICAL_CONTENT), "MEDICAL still needs a grant on-device")
        assertTrue(res.wasWithheld("medication"))
    }

    @Test
    fun `on-device with keepPrivate off includes PRIVATE`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.ON_DEVICE, keepPrivate = false))
        for (c in PRIVATE_CONTENT) assertTrue(res.factsheet.contains(c), "PRIVATE '$c' should be allowed on-device when opted in")
        assertTrue(res.withheld.none { it.privacy == PrivacyClass.PRIVATE })
    }

    @Test
    fun `on-device with keepPrivate on withholds PRIVATE`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.ON_DEVICE, keepPrivate = true))
        for (c in PRIVATE_CONTENT) assertFalse(res.factsheet.contains(c))
        assertEquals(3, res.withheld.count { it.privacy == PrivacyClass.PRIVATE })
    }

    @Test
    fun `shareable always kept`() {
        for (dest in CoachDestination.values()) {
            val res = Redaction.redact(richFacts(), RedactionPolicy(dest))
            assertTrue(res.factsheet.contains("Readiness: CAUTION"))
            assertTrue(res.factsheet.contains("ACWR: 1.42"))
            assertTrue(res.kept.any { it.privacy == PrivacyClass.SHAREABLE })
        }
    }

    @Test
    fun `withheld report explains every withholding`() {
        val res = Redaction.redact(richFacts(), RedactionPolicy(CoachDestination.CLOUD, medicalGrant = false))
        assertTrue(res.withheld.isNotEmpty())
        assertTrue(res.withheld.all { it.reason.isNotBlank() })
        // 3 private + 1 medical.
        assertEquals(4, res.withheld.size)
    }

    @Test
    fun `policyFor binds destination to the model kind`() {
        val cloud = ModelRegistry.cloudModels().first()
        val onDevice = ModelRegistry.onDeviceModels().first()
        assertEquals(CoachDestination.CLOUD, Redaction.policyFor(cloud).destination)
        assertEquals(CoachDestination.ON_DEVICE, Redaction.policyFor(onDevice).destination)
    }

    @Test
    fun `redactFor a cloud model can never keep PRIVATE even with keepPrivate off`() {
        val cloud = ModelRegistry.cloudModels().first()
        // The footgun this closes: asking to keep private, then targeting a cloud model.
        val res = Redaction.redactFor(richFacts(), cloud, medicalGrant = true, keepPrivate = false)
        for (c in PRIVATE_CONTENT) assertFalse(res.factsheet.contains(c), "PRIVATE '$c' reached a cloud model via redactFor")
        assertTrue(res.kept.none { it.privacy == PrivacyClass.PRIVATE })
    }

    @Test
    fun `exhaustive privacy x destination x grant matrix`() {
        for (dest in CoachDestination.values()) {
            for (grant in listOf(false, true)) {
                for (keep in listOf(false, true)) {
                    val res = Redaction.redact(richFacts(), RedactionPolicy(dest, grant, keep))
                    val privateKept = res.kept.any { it.privacy == PrivacyClass.PRIVATE }
                    val medicalKept = res.kept.any { it.privacy == PrivacyClass.MEDICAL }

                    // PRIVATE: only ever kept on-device with keepPrivate off.
                    val privateExpected = dest == CoachDestination.ON_DEVICE && !keep
                    assertEquals(privateExpected, privateKept, "PRIVATE dest=$dest grant=$grant keep=$keep")

                    // MEDICAL: kept iff grant, regardless of destination.
                    assertEquals(grant, medicalKept, "MEDICAL dest=$dest grant=$grant keep=$keep")
                }
            }
        }
    }
}
