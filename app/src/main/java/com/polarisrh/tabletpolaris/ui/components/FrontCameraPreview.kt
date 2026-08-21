package com.polarisrh.tabletpolaris.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.polarisrh.tabletpolaris.facial.FaceDetectionAnalyzer
import com.polarisrh.tabletpolaris.facial.FaceDetectionStatus
import java.util.concurrent.Executors

/**
 * Live front-camera feed. Quando [onFaceDetectionStatus] é passado, também liga um
 * ImageAnalysis com ML Kit rodando em cada frame — só classifica o enquadramento (nenhum
 * rosto / mais de um / longe demais / pronto), não gera nem compara nenhum embedding.
 *
 * [onCapturaDisponivel], se passado, é chamado uma vez com uma função que devolve um snapshot
 * do frame exibido no momento em que for chamada (via PreviewView.bitmap — já vem corrigido
 * de rotação/espelhamento, exatamente como aparece na tela).
 */
@Composable
fun FrontCameraPreview(
    modifier: Modifier = Modifier,
    onFaceDetectionStatus: ((FaceDetectionStatus) -> Unit)? = null,
    onCapturaDisponivel: ((() -> Bitmap?) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStatus by rememberUpdatedState(onFaceDetectionStatus)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    if (hasCameraPermission) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { viewContext ->
                val previewView = PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    // TextureView instead of the default SurfaceView: SurfaceView renders in
                    // its own compositing layer and can bleed over sibling composables (like
                    // our header) instead of respecting normal Compose layout/z-order.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                onCapturaDisponivel?.invoke { previewView.bitmap }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }

                        cameraProvider.unbindAll()

                        if (currentOnStatus != null) {
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(analysisExecutor, FaceDetectionAnalyzer { status ->
                                currentOnStatus?.invoke(status)
                            })
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                analysis
                            )
                        } else {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview
                            )
                        }
                    },
                    ContextCompat.getMainExecutor(viewContext)
                )
                previewView
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize())
    }
}
