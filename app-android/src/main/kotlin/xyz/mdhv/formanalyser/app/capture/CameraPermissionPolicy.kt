package xyz.mdhv.formanalyser.app.capture

/** Whether a denied camera permission can still be requested, or must be changed in app settings. */
internal fun cameraPermissionRequiresSettings(
    requestCompleted: Boolean,
    granted: Boolean,
    shouldShowRationale: Boolean,
): Boolean = requestCompleted && !granted && !shouldShowRationale
