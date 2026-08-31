package xyz.mdhv.formanalyser.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.provenanceGlow

@Composable
fun CaptureScreen(vm: SessionViewModel, onReview: () -> Unit) {
    val context = LocalContext.current
    // Two different questions that were previously conflated under one `hasCamera` flag: does this
    // device HAVE a camera, and have we been GRANTED it. The manifest no longer requires camera
    // hardware (that made the app un-installable on camera-less Chromebooks), so the app must now
    // run sensibly on a device where the answer to the first question is simply no — asking for a
    // permission that cannot help would be a dead end.
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

    val recording by vm.isRecording.collectAsState()
    val tracking by vm.liveTracking.collectAsState()
    val bowAngle by vm.liveBowArmAngle.collectAsState()
    val shots by vm.shots.collectAsState()
    val postPending by vm.postPending.collectAsState()

    postPending?.let { pending ->
        PostCheckinSheet(
            pending = pending,
            onSave = { rpe, feel, durS, arrows ->
                vm.savePostCheckin(rpe, feel, durS, arrows)
                onReview()
            },
            onSkip = {
                vm.skipPostCheckin()
                onReview()
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Film side-on (sagittal)",
            style = MaterialTheme.typography.titleLarge,
            color = Hyle.OnBackground,
        )

        if (!hasCameraHardware) {
            Text(
                "This device has no camera, so form capture is unavailable here. Scoring, " +
                    "training logs and everything else still work.",
                color = Hyle.OnSurfaceDim,
            )
        } else if (granted) {
            CameraPreview(
                vm,
                Modifier.fillMaxWidth().height(360.dp).provenanceGlow(Hyle.RadiumGreen),
            )
        } else {
            Text("Camera permission is required to analyse form.", color = Hyle.Danger)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera")
            }
        }

        Text(
            when {
                !vm.recorder.isAvailable ->
                    "Pose model missing (add ${"pose_landmarker_lite.task"} to assets)."
                tracking -> "Tracking ✓  bow arm ${bowAngle?.toInt() ?: "—"}°"
                else -> "No archer detected — frame the full body, side-on."
            },
            color = if (tracking) Hyle.RadiumGreen else Hyle.OnSurfaceDim,
        )

        Button(
            onClick = { if (recording) vm.stopRecordingAndAnalyze() else vm.startRecording() },
            enabled = hasCameraHardware && granted && vm.recorder.isAvailable,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (recording) Hyle.Danger else Hyle.Accent
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (recording) "Stop & analyze end" else "Start recording")
        }

        Text("Shots detected this session: ${shots.size}", color = Hyle.OnBackground)
        OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
            Text("Review session →")
        }
    }
}

@Composable
private fun CameraPreview(vm: SessionViewModel, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    // Recompose (and therefore re-run `update`) whenever the configuration changes, which is what
    // carries a rotation or a window resize. Reading it here is the subscription.
    LocalConfiguration.current

    // Held so the use cases can be unbound precisely on dispose without blocking the main thread
    // re-resolving the provider future.
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val preview = remember { Preview.Builder().build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(executor) { img -> vm.recorder.process(img) } }
    }

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
                    // Back camera preferred — the athlete films side-on with the phone facing them
                    // — but a Chromebook or tablet may only have a front one, and hardcoding BACK
                    // meant bindToLifecycle simply threw there.
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
                            // Unbind only OUR use cases. unbindAll() is process-global and would
                            // tear down another window's camera on a foldable or desktop.
                            p.unbind(preview, analysis)
                            p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                        }
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
        update = { previewView ->
            // The analyzer must know how the sensor is oriented relative to the display, or
            // MediaPipe receives a mis-rotated frame and every landmark, bow-arm angle, deviation
            // and fatigue figure downstream is quietly wrong — no crash, just bad numbers. This
            // was previously never set at all, which was survivable ONLY because rotation
            // destroyed the activity. It stops being survivable the moment the window can resize.
            previewView.display?.rotation?.let { analysis.targetRotation = it }
        },
    )

    DisposableEffect(Unit) { onDispose { runCatching { provider?.unbind(preview, analysis) } } }
}
