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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.provenanceGlow
import java.util.concurrent.Executors

@Composable
fun CaptureScreen(vm: SessionViewModel, onReview: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) launcher.launch(Manifest.permission.CAMERA) }

    val recording by vm.isRecording.collectAsState()
    val tracking by vm.liveTracking.collectAsState()
    val bowAngle by vm.liveBowArmAngle.collectAsState()
    val shots by vm.shots.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Film side-on (sagittal)", style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground)

        if (hasCamera) {
            CameraPreview(vm, Modifier.fillMaxWidth().height(360.dp).provenanceGlow(Hyle.RadiumGreen))
        } else {
            Text("Camera permission is required to analyse form.", color = Hyle.Danger)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera") }
        }

        Text(
            when {
                !vm.recorder.isAvailable -> "Pose model missing (add ${"pose_landmarker_lite.task"} to assets)."
                tracking -> "Tracking ✓  bow arm ${bowAngle?.toInt() ?: "—"}°"
                else -> "No archer detected — frame the full body, side-on."
            },
            color = if (tracking) Hyle.RadiumGreen else Hyle.OnSurfaceDim,
        )

        Button(
            onClick = { if (recording) vm.stopRecordingAndAnalyze() else vm.startRecording() },
            enabled = hasCamera && vm.recorder.isAvailable,
            colors = ButtonDefaults.buttonColors(containerColor = if (recording) Hyle.Danger else Hyle.Accent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (recording) "Stop & analyze end" else "Start recording")
        }

        Text("Shots detected this session: ${shots.size}", color = Hyle.OnBackground)
        OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review session →") }
    }
}

@Composable
private fun CameraPreview(vm: SessionViewModel, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(executor) { img -> vm.recorder.process(img) } }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
