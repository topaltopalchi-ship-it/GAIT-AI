package ir.gaitai.analyzer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
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
    private lateinit var metricsText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var analyzerExecutor: ExecutorService
    private val sessionTracker = GaitSessionTracker()
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
            text = "تمام بدن را در کادر قرار دهید؛ ترجیحاً از نمای جانبی و با فاصله ثابت"
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

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 4)
            gravity = Gravity.CENTER
        }
        startButton = Button(this).apply {
            text = "شروع ثبت"
            isEnabled = true
            setOnClickListener {
                sessionTracker.start()
                isEnabled = false
                stopButton.isEnabled = true
                statusText.text = "در حال ثبت حرکت؛ چند قدم با سرعت طبیعی راه بروید."
                metricsText.text = "ثبت در حال انجام است…"
            }
        }
        stopButton = Button(this).apply {
            text = "پایان و تحلیل"
            isEnabled = false
            setOnClickListener {
                val result = sessionTracker.finish()
                isEnabled = false
                startButton.isEnabled = true
                showReport(result)
            }
        }
        controls.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(stopButton, LinearLayout.LayoutParams(0, -2, 1f))

        statusText = TextView(this).apply {
            text = "در انتظار دوربین…"
            setTextColor(Color.rgb(54, 214, 176))
            textSize = 14f
            setPadding(20, 8, 20, 4)
        }
        metricsText = TextView(this).apply {
            text = "برای شروع، دکمه «شروع ثبت» را بزنید."
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(20, 4, 20, 10)
        }
        val note = TextView(this).apply {
            text = "نسخه آزمایشی: تعداد قدم و ریتم به‌صورت تخمینی محاسبه می‌شود؛ این برنامه ابزار تشخیص پزشکی نیست."
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setPadding(20, 2, 20, 12)
        }
        root.addView(header)
        root.addView(subtitle)
        root.addView(cameraFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(controls)
        root.addView(statusText)
        root.addView(metricsText)
        root.addView(note)
        setContentView(root)
    }

    private fun showReport(result: GaitMetrics) {
        metricsText.text = buildString {
            append("گزارش جلسه\n")
            append("مدت ثبت: ${result.durationSeconds} ثانیه\n")
            append("تعداد قدم تخمینی: ${result.estimatedSteps}\n")
            append("ریتم تخمینی: ${result.cadenceStepsPerMinute} قدم در دقیقه\n")
            append("قاب‌های تحلیل‌شده: ${result.analyzedFrames}\n")
            append("قاب‌های دارای بدن قابل‌مشاهده: ${result.visibilityPercent}%")
            if (result.durationSeconds < 5 || result.estimatedSteps < 4) {
                append("\nداده برای تحلیل اولیه کافی نیست؛ جلسه را طولانی‌تر تکرار کنید.")
            } else {
                append("\nاین اعداد تخمینی‌اند و برای ارزیابی بالینی معتبر نیستند.")
            }
        }
        statusText.text = "ثبت پایان یافت."
    }

    private fun startCamera() {
        statusText.text = "در حال آماده‌سازی موتور تشخیص…"
        try {
            poseAnalyzer = PoseAnalyzer(
                context = this,
                onPose = { points, visible ->
                    runOnUiThread {
                        overlay.setLandmarks(points)
                        if (sessionTracker.isRecording) {
                            sessionTracker.addFrame(points, visible)
                        }
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
