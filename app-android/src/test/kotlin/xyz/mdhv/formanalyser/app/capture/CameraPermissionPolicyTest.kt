package xyz.mdhv.formanalyser.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPermissionPolicyTest {
    @Test
    fun initialUndecidedPermissionDoesNotSendUserToSettings() {
        assertFalse(
            cameraPermissionRequiresSettings(
                requestCompleted = false,
                granted = false,
                shouldShowRationale = false,
            )
        )
    }

    @Test
    fun deniedPermissionWithRationaleCanBeRequestedAgain() {
        assertFalse(
            cameraPermissionRequiresSettings(
                requestCompleted = true,
                granted = false,
                shouldShowRationale = true,
            )
        )
    }

    @Test
    fun deniedPermissionWithoutRationaleRequiresSettings() {
        assertTrue(
            cameraPermissionRequiresSettings(
                requestCompleted = true,
                granted = false,
                shouldShowRationale = false,
            )
        )
    }

    @Test
    fun grantedPermissionNeverRequiresSettings() {
        assertFalse(
            cameraPermissionRequiresSettings(
                requestCompleted = true,
                granted = true,
                shouldShowRationale = false,
            )
        )
    }
}
