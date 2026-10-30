package ir.gaitai.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.atomic.AtomicBoolean

data class PosePoint(val x: Float, val y: Float, val visibility: Float)

class PoseAnalyzer(
    context: Context,
    private val onPose: (List<PosePoint>, Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val busy = AtomicBoolean(false)
    private val landmarker: PoseLandmarker

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.55f)
            .setMinPosePresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .build()
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun analyze(proxy: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        var bitmap: Bitmap? = null
        var rotated: Bitmap? = null
        try {
            val plane = proxy.planes[0]
            bitmap = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
            plane.buffer.rewind()
            bitmap.copyPixelsFromBuffer(plane.buffer)
            rotated = rotate(bitmap, proxy.imageInfo.rotationDegrees)
            val result = landmarker.detect(BitmapImageBuilder(rotated).build())
            val pose = result.landmarks().firstOrNull()
            if (pose == null) {
                onPose(emptyList(), false)
            } else {
                val points = pose.map {
                    PosePoint(it.x(), it.y(), it.visibility().orElse(0f))
                }
                val required = listOf(11, 12, 23, 24, 25, 26, 27, 28)
                val visible = required.all { i -> points.getOrNull(i)?.visibility?.let { it >= 0.35f } == true }
                onPose(points, visible)
            }
        } catch (e: Exception) {
            onError("خطا در تحلیل تصویر: ${e.localizedMessage ?: "نامشخص"}")
        } finally {
            if (rotated !== bitmap) rotated?.recycle()
            bitmap?.recycle()
            proxy.close()
            busy.set(false)
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun close() = landmarker.close()
}
