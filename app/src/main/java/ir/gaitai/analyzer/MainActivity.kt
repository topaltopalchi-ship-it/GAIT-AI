package ir.gaitai.analyzer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: PoseOverlayView
    private lateinit var statusText: TextView
    private lateinit var analyzerExecutor: ExecutorService
    private var poseAnalyzer: PoseAnalyzer? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else statusText.text = "برای تشخیص بدن، اجازه دوربین لازم است."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyzerExecutor = Executors.newSingleThreadExecutor()
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 24, 39))
        }
        val header = TextView(this).apply {
            text = "GAIT AI  •  تحلیل حرکت انسان"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 18, 20, 8)
        }
        val subtitle = TextView(this).apply {
            text = "تمام بدن را در کادر قرار دهید و چند قدم راه بروید"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(20, 0, 20, 12)
        }
        val cameraFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        overlay = PoseOverlayView(this)
        cameraFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        cameraFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        statusText = TextView(this).apply {
            text = "در انتظار دوربین…"
            setTextColor(Color.rgb(54, 214, 176))
            textSize = 14f
            setPadding(20, 14, 20, 14)
        }
        val note = TextView(this).apply {
            text = "نسخه آزمایشی: الگوی حرکتی را نمایش می‌دهد و تشخیص شخصیت یا بیماری انجام نمی‌دهد."
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setPadding(20, 4, 20, 16)
        }
        root.addView(header)
        root.addView(subtitle)
        root.addView(cameraFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(statusText)
        root.addView(note)
        setContentView(root)
    }

    private fun startCamera() {
        statusText.text = "در حال آماده‌سازی موتور تشخیص…"
        try {
            poseAnalyzer = PoseAnalyzer(
                context = this,
                onPose = { points, visible ->
                    runOnUiThread {
                        overlay.setLandmarks(points)
                        statusText.text = if (visible)
                            "بدن شناسایی شد • نقاط مفصلی: ${points.size}"
                        else "بدن کامل دیده نشد؛ فاصله و نور را تنظیم کنید."
                    }
                },
                onError = { message -> runOnUiThread { statusText.text = message } }
            )
        } catch (e: Exception) {
            statusText.text = "خطا در راه‌اندازی مدل: ${e.localizedMessage}"
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { image -> poseAnalyzer?.analyze(image) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                statusText.text = "دوربین آماده است؛ تمام بدن را داخل کادر قرار دهید."
            } catch (e: Exception) {
                statusText.text = "خطای دوربین: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        poseAnalyzer?.close()
        if (::analyzerExecutor.isInitialized) analyzerExecutor.shutdown()
    }
}
