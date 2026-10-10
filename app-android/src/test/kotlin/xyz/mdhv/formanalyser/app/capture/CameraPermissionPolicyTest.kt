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

    @Test
    fun captureWaitsForPersistedSession() {
        assertFalse(
            captureCanStart(
                hasCameraHardware = true,
                permissionGranted = true,
                poseModelAvailable = true,
                sessionReady = false,
            )
        )
    }

    @Test
    fun captureRequiresCameraPermissionModelHardwareAndSession() {
        assertTrue(captureCanStart(true, true, true, true))
        assertFalse(captureCanStart(false, true, true, true))
        assertFalse(captureCanStart(true, false, true, true))
        assertFalse(captureCanStart(true, true, false, true))
    }
}
