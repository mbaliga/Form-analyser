package xyz.mdhv.formanalyser.app.capture

/** Whether a denied camera permission can still be requested, or must be changed in app settings. */
internal fun cameraPermissionRequiresSettings(
    requestCompleted: Boolean,
    granted: Boolean,
    shouldShowRationale: Boolean,
): Boolean = requestCompleted && !granted && !shouldShowRationale

/** Capture is safe to begin only after a persisted session and an available camera/model exist. */
internal fun captureCanStart(
    hasCameraHardware: Boolean,
    permissionGranted: Boolean,
    poseModelAvailable: Boolean,
    sessionReady: Boolean,
): Boolean = hasCameraHardware && permissionGranted && poseModelAvailable && sessionReady
