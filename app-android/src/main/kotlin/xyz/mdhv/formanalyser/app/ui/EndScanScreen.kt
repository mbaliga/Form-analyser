package xyz.mdhv.formanalyser.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import xyz.mdhv.formanalyser.app.capture.EndScanCapture
import xyz.mdhv.formanalyser.app.domain.EndScanStage
import xyz.mdhv.formanalyser.app.domain.EndScanUiState
import xyz.mdhv.formanalyser.app.domain.ScoringViewModel
import xyz.mdhv.formanalyser.app.ui.theme.HapticCue
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.provenanceGlow
import xyz.mdhv.formanalyser.app.ui.theme.rememberHaptics
import xyz.mdhv.formanalyser.scoring.EndScan
import xyz.mdhv.formanalyser.scoring.FaceCalibration
import xyz.mdhv.formanalyser.scoring.FaceLayout
import xyz.mdhv.formanalyser.scoring.ImagePoint

/**
 * End Scan: photograph the shot end, say where the face is, look at what the phone thinks it found.
 *
 * Three stages, and the third one is not scoring. Nothing this screen does touches a total: the last
 * button files what the detector found as *proposals*, and the End Scan review panel on the scoring
 * screen is where the athlete confirms them one at a time. That separation is the point of the whole
 * feature — a machine looking at a photograph of a target is a useful assistant and a terrible
 * witness, and the app has no business writing a score from one.
 *
 * The calibration stage is athlete-assisted on purpose rather than for want of a ring detector. See
 * [FaceCalibration] for why: a frame of reference the athlete can see and correct is worth more here
 * than an automatic one that fails invisibly under a cloud shadow and takes every arrow with it.
 */
@Composable
fun EndScanFlow(vm: ScoringViewModel, scan: EndScanUiState, layout: FaceLayout) {
    // Back leaves the ceremony rather than the Score tab. Nothing has been written at any stage, so
    // there is nothing to guard — but landing back on the scorecard is what the athlete means.
    BackHandler { vm.cancelEndScan() }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("End Scan", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        scan.error?.let { Text(it, color = Hyle.Danger) }
        when (scan.stage) {
            EndScanStage.CAMERA -> CameraStage(vm, scan)
            EndScanStage.CALIBRATE -> CalibrateStage(vm, scan, layout)
            EndScanStage.REVIEW -> ReviewStage(vm, scan, layout)
        }
        TextButton(onClick = vm::cancelEndScan, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel and score by hand")
        }
    }
}

/**
 * Stage one: frame the target.
 *
 * The instruction is not decoration. The detector reads a nocked arrow as a streak pointing outward
 * from the face centre, and that only happens when the photograph is taken close and square-on —
 * from the shooting line every arrow looks exactly like every hole from every previous end. Saying
 * so here is cheaper than a review queue full of last week's arrows.
 */
@Composable
private fun CameraStage(vm: ScoringViewModel, scan: EndScanUiState) {
    val context = LocalContext.current
    val haptic = rememberHaptics()
    val hasCameraHardware =
        remember(context) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
        }
    LaunchedEffect(hasCameraHardware) {
        if (hasCameraHardware && !granted) launcher.launch(Manifest.permission.CAMERA)
    }
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }

    Text(
        "Walk to the target and photograph it square-on from a couple of paces, with the arrows " +
            "still in the boss and the whole face in the frame.",
        color = Hyle.OnSurfaceDim,
    )
    Text(
        "${scan.arrowsExpected} arrow(s) still to record in this end.",
        color = Hyle.OnSurfaceDim,
    )
    when {
        !hasCameraHardware ->
            Text(
                "This device has no camera, so End Scan is unavailable here. Numeric and plot " +
                    "scoring are unaffected.",
                color = Hyle.OnSurfaceDim,
            )
        granted ->
            EndScanCameraPreview(
                capture,
                Modifier.fillMaxWidth().height(380.dp).provenanceGlow(Hyle.RadiumGreen),
            )
        else -> {
            Text("Camera permission is required to photograph the target.", color = Hyle.Danger)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera")
            }
        }
    }
    Button(
        onClick = {
            haptic(HapticCue.ARROW)
            takeTargetPhoto(capture, executor, vm)
        },
        enabled = hasCameraHardware && granted && !scan.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Photograph the target")
    }
}

/**
 * Stage two: say where the face is.
 *
 * The overlay draws every scoring ring through the calibration the athlete is dragging, which turns
 * "are these four handles right?" into a question they can answer at a glance: the drawn rings sit
 * on the printed ones, or they visibly do not. Nothing else in this flow gives them a way to check
 * the frame of reference before it is used on every arrow.
 */
@Composable
private fun CalibrateStage(vm: ScoringViewModel, scan: EndScanUiState, layout: FaceLayout) {
    val photo = scan.photo ?: return
    val calibration = scan.calibration ?: return
    // The gesture block is started once and never restarted, so it cannot capture the calibration
    // directly — it would go on editing whatever the handles were when the drag surface was built.
    val latest = rememberUpdatedState(calibration)
    Text(
        "Drag the four marks onto the edge of the printed face — top, right, bottom and left. The " +
            "rings drawn on top show what the phone thinks the face is.",
        color = Hyle.OnSurfaceDim,
    )
    PhotoOverlay(
        photo,
        overlay =
            Modifier.pointerInput(Unit) {
                var handle = -1
                val reach = HANDLE_REACH_DP.dp.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        handle =
                            nearestHandle(
                                latest.value,
                                start,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                reach,
                            )
                    },
                    onDragEnd = { handle = -1 },
                    onDragCancel = { handle = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        if (handle >= 0) {
                            val moved =
                                ImagePoint(
                                    (change.position.x / size.width).toDouble().coerceIn(0.0, 1.0),
                                    (change.position.y / size.height).toDouble().coerceIn(0.0, 1.0),
                                )
                            vm.updateEndScanCalibration(latest.value.withHandle(handle, moved))
                        }
                    },
                )
            },
    ) {
        drawRings(calibration, layout, Hyle.RadiumGreen.copy(alpha = 0.75f))
        drawHandles(calibration)
    }
    scan.calibrationProblem?.let { Text(it.message, color = Hyle.Danger) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = vm::retakeEndScanPhoto, enabled = !scan.busy) { Text("Retake") }
        Button(
            onClick = vm::runEndScanDetection,
            enabled = !scan.busy && scan.calibrationProblem == null,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (scan.busy) "Reading the face…" else "Find the arrows")
        }
    }
    if (scan.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
}

/**
 * Stage three: look at what was found, before any of it is filed.
 *
 * Deliberately shows the refusals as well as the finds. "Two of three arrows" with no explanation
 * reads as a broken feature; "the third looked like a ring line" reads as a machine that knows what
 * it does not know, and tells the athlete exactly where to look themselves.
 */
@Composable
private fun ReviewStage(vm: ScoringViewModel, scan: EndScanUiState, layout: FaceLayout) {
    val photo = scan.photo ?: return
    val calibration = scan.calibration ?: return
    val found = scan.result ?: return
    var selected by remember(found) { mutableStateOf(-1) }
    PhotoOverlay(photo) {
        drawRings(calibration, layout, Hyle.RadiumGreen.copy(alpha = 0.35f))
        drawImpacts(calibration, found.impacts, selected)
    }
    Text(
        "${found.impacts.size} of ${found.arrowsExpected} arrow(s) found. Nothing here is a score " +
            "yet — filing them puts each one in the review list to confirm or reject.",
        color = Hyle.OnSurfaceDim,
    )
    found.impacts.forEachIndexed { index, impact ->
        Card(
            Modifier.fillMaxWidth().clickable {
                selected = if (selected == index) -1 else index
            }
        ) {
            Column(Modifier.padding(12.dp)) {
                val marked = if (selected == index) " · marked on the photo" else ""
                Text(
                    "${scoreLabel(impact.score.points, impact.score.isX)} · " +
                        "${(impact.confidence * 100).roundToInt()}% confident$marked",
                    color = Hyle.OnBackground,
                )
                if (impact.lineCutter)
                    Text(
                        "Close to a ring line. A line-cutter takes the higher value and the photo " +
                            "cannot see whether it touches — check this one on the boss.",
                        color = Hyle.Warning,
                    )
                else if (impact.needsCloseLook)
                    Text("Uncertain — check this one before confirming.", color = Hyle.Warning)
            }
        }
    }
    if (found.countMismatch)
        Text(
            "That does not match the end. Record the rest on the keypad rather than trusting this.",
            color = Hyle.Warning,
        )
    val refusals = found.rejections.entries.filter { it.value > 0 }
    if (refusals.isNotEmpty())
        Text(
            "Marks not offered: " + refusals.joinToString("; ") { "${it.value} ${it.key.label}" },
            color = Hyle.OnSurfaceDim,
        )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = vm::retakeEndScanPhoto, enabled = !scan.busy) { Text("Retake") }
        Button(
            onClick = vm::proposeEndScanFindings,
            enabled = !scan.busy && found.impacts.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) {
            Text("File ${found.impacts.size} for review")
        }
    }
}

/**
 * The photograph with a drawing on top of it, in one coordinate system.
 *
 * The box is pinned to the bitmap's own aspect ratio so the drawn area is exactly the image area.
 * That is what lets normalized image coordinates — the space [FaceCalibration] is stored in — become
 * canvas pixels by a single multiply, with no letterboxing offsets to get wrong. A calibration that
 * is a few pixels off because of a content-scale rounding error is a calibration that is wrong.
 */
@Composable
private fun PhotoOverlay(
    photo: Bitmap,
    overlay: Modifier = Modifier,
    draw: DrawScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height.toFloat())) {
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = "The target face just photographed",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(Modifier.fillMaxSize().then(overlay)) { draw() }
    }
}

/** Normalized image coordinates to canvas pixels. Exact, because the box *is* the image. */
private fun DrawScope.canvasPoint(p: ImagePoint) =
    Offset((p.x * size.width).toFloat(), (p.y * size.height).toFloat())

private fun DrawScope.drawHandles(calibration: FaceCalibration) {
    calibration.handles().forEach { handle ->
        val centre = canvasPoint(handle)
        drawCircle(Color.Black.copy(alpha = 0.55f), 22f, centre, style = Stroke(6f))
        drawCircle(Hyle.RadiumGreen, 22f, centre, style = Stroke(3f))
        drawCircle(Hyle.RadiumGreen, 4f, centre)
    }
}

/**
 * Every scoring ring, drawn through the athlete's current calibration.
 *
 * A closed polyline of 64 samples per ring: a homography maps a circle to a conic, not to a circle,
 * so there is no radius to hand `drawCircle` — and drawing one anyway is exactly the flattering
 * approximation that would hide a bad calibration.
 */
private fun DrawScope.drawRings(calibration: FaceCalibration, layout: FaceLayout, colour: Color) {
    val projection =
        (calibration.project() as? FaceCalibration.Result.Usable)?.projection ?: return
    val innermost = if (layout == FaceLayout.SINGLE) 1 else 6
    val radii = (innermost..10).map { (11 - it) / 10.0 } + 0.05
    radii.forEach { radius ->
        var previous: Offset? = null
        for (step in 0..RING_SAMPLES) {
            val angle = step * 2.0 * PI / RING_SAMPLES
            val mapped = projection.toImage(radius * cos(angle), radius * sin(angle))
            if (mapped == null) {
                previous = null
                continue
            }
            val point = canvasPoint(mapped)
            previous?.let { drawLine(colour, it, point, 2f) }
            previous = point
        }
    }
}

/** Detected impacts, back on the photograph they came from. */
private fun DrawScope.drawImpacts(
    calibration: FaceCalibration,
    impacts: List<EndScan.Impact>,
    selected: Int,
) {
    val projection =
        (calibration.project() as? FaceCalibration.Result.Usable)?.projection ?: return
    impacts.forEachIndexed { index, impact ->
        val mapped = projection.toImage(impact.plot.x, impact.plot.y) ?: return@forEachIndexed
        val centre = canvasPoint(mapped)
        val colour = if (impact.needsCloseLook) Hyle.Warning else Hyle.RadiumGreen
        val radius = if (index == selected) 26f else 16f
        drawCircle(Color.Black.copy(alpha = 0.6f), radius, centre, style = Stroke(6f))
        drawCircle(colour, radius, centre, style = Stroke(3f))
        drawCircle(colour, 3f, centre)
    }
}

/** Index of the handle within [slop] of [at], or -1. Order matches [FaceCalibration.handles]. */
private fun nearestHandle(
    calibration: FaceCalibration,
    at: Offset,
    width: Float,
    height: Float,
    slop: Float,
): Int {
    var best = -1
    var bestDistance = slop
    calibration.handles().forEachIndexed { index, handle ->
        val distance =
            hypot((handle.x * width - at.x).toFloat(), (handle.y * height - at.y).toFloat())
        if (distance <= bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

private fun FaceCalibration.handles(): List<ImagePoint> = listOf(top, right, bottom, left)

private fun FaceCalibration.withHandle(index: Int, point: ImagePoint): FaceCalibration =
    when (index) {
        0 -> copy(top = point)
        1 -> copy(right = point)
        2 -> copy(bottom = point)
        else -> copy(left = point)
    }

private fun scoreLabel(points: Int, isX: Boolean) =
    if (isX) "X" else if (points == 0) "M" else points.toString()

private const val RING_SAMPLES = 64

/**
 * How far from a handle a drag may start and still grab it, in dp.
 *
 * Generous on purpose: this is done on a shooting line, often with a glove on, and grabbing nothing
 * is a worse failure than grabbing the wrong one — a wrong handle is visible immediately in the
 * drawn rings, whereas an unresponsive handle just reads as a broken screen.
 */
private const val HANDLE_REACH_DP = 44

/**
 * The shutter.
 *
 * Deliberately delivered on the **main** executor rather than a private background one. A capture is
 * in flight for a second or so, and the athlete can leave the flow inside that window; a screen-owned
 * executor would already have been shut down when CameraX went to deliver, and the rejected execution
 * surfaces on CameraX's own thread as a crash rather than as a lost photo. The main executor cannot
 * be shut down, and the only work done on it here is copying the frame's bytes out — the decode
 * itself is the ViewModel's, on a worker.
 *
 * The `ImageProxy` is closed on every path. CameraX hands them out from a small fixed pool, and
 * leaking one stalls the next shutter press with no error anyone can see.
 */
private fun takeTargetPhoto(capture: ImageCapture, executor: Executor, vm: ScoringViewModel) {
    capture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val frame = EndScanCapture.read(image)
                    if (frame == null) vm.endScanCaptureFailed("That photo came back empty.")
                    else vm.endScanPhotoCaptured(frame)
                } catch (t: Throwable) {
                    vm.endScanCaptureFailed(t.message ?: "That photo could not be read.")
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                vm.endScanCaptureFailed(
                    exception.message ?: "The camera could not take that photo."
                )
            }
        },
    )
}

/**
 * Live preview bound to this composable's lifecycle.
 *
 * Follows `CaptureScreen`'s camera binding exactly, including the parts that look like paranoia and
 * are not: only this screen's own use cases are unbound (`unbindAll` is process-global and would
 * tear down another window's camera on a foldable), the back camera is preferred but not assumed,
 * and the capture's target rotation is refreshed on every configuration change so the photograph is
 * oriented the way the athlete was holding the phone.
 */
@Composable
private fun EndScanCameraPreview(capture: ImageCapture, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LocalConfiguration.current
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val preview = remember { Preview.Builder().build() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener(
                {
                    val p = future.get()
                    provider = p
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val selector =
                        when {
                            p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                                CameraSelector.DEFAULT_BACK_CAMERA
                            p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            else -> null
                        }
                    if (selector != null) {
                        runCatching {
                            p.unbind(preview, capture)
                            p.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        }
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
        update = { previewView ->
            previewView.display?.rotation?.let { capture.targetRotation = it }
        },
    )

    DisposableEffect(Unit) { onDispose { runCatching { provider?.unbind(preview, capture) } } }
}
