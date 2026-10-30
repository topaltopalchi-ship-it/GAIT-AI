package ir.gaitai.analyzer

/**
 * On-device exploratory gait tracker.
 * Step count/cadence are estimates, not clinical measurements.
 */
data class GaitMetrics(
    val durationSeconds: Int,
    val estimatedSteps: Int,
    val cadenceStepsPerMinute: Int,
    val analyzedFrames: Int,
    val visibleFrames: Int,
    val visibilityPercent: Int
)

class GaitSessionTracker {
    private data class Sample(val timeMs: Long, val y: Float, val visibility: Float)

    @Volatile private var recording = false
    private var startedAt = 0L
    private var frames = 0
    private var visibleFrames = 0
    private var steps = 0
    private var previousLeft: Sample? = null
    private var middleLeft: Sample? = null
    private var previousRight: Sample? = null
    private var middleRight: Sample? = null
    private var lastLeftStepAt = 0L
    private var lastRightStepAt = 0L

    val isRecording: Boolean get() = recording

    @Synchronized
    fun start(nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        recording = true
        startedAt = nowMs
        frames = 0
        visibleFrames = 0
        steps = 0
        previousLeft = null
        middleLeft = null
        previousRight = null
        middleRight = null
        lastLeftStepAt = 0L
        lastRightStepAt = 0L
    }

    @Synchronized
    fun addFrame(
        points: List<PosePoint>,
        bodyVisible: Boolean,
        nowMs: Long = android.os.SystemClock.elapsedRealtime()
    ) {
        if (!recording) return
        frames++
        if (bodyVisible) visibleFrames++
        if (points.size < 29) return

        // Foot landmarks can remain usable even when shoulders/hips are partly occluded.
        val left = points[27]
        val right = points[28]
        if (left.visibility >= 0.30f) {
            detectFootStep(left, nowMs, true)
        } else {
            previousLeft = null
            middleLeft = null
        }
        if (right.visibility >= 0.30f) {
            detectFootStep(right, nowMs, false)
        } else {
            previousRight = null
            middleRight = null
        }
    }

    private fun detectFootStep(point: PosePoint, nowMs: Long, isLeft: Boolean) {
        val previous = if (isLeft) previousLeft else previousRight
        val middle = if (isLeft) middleLeft else middleRight
        val lastStepAt = if (isLeft) lastLeftStepAt else lastRightStepAt

        if (previous != null && middle != null &&
            middle.visibility >= 0.30f &&
            middle.y < previous.y - 0.004f &&
            middle.y < point.y - 0.004f &&
            nowMs - lastStepAt >= 350L
        ) {
            steps++
            if (isLeft) lastLeftStepAt = nowMs else lastRightStepAt = nowMs
        }

        if (isLeft) {
            previousLeft = middle
            middleLeft = Sample(nowMs, point.y, point.visibility)
        } else {
            previousRight = middle
            middleRight = Sample(nowMs, point.y, point.visibility)
        }
    }

    @Synchronized
    fun finish(nowMs: Long = android.os.SystemClock.elapsedRealtime()): GaitMetrics {
        recording = false
        val duration = ((nowMs - startedAt).coerceAtLeast(0L) / 1000L).toInt()
        val cadence = if (duration > 0) (steps * 60f / duration).toInt() else 0
        val visibility = if (frames > 0) (visibleFrames * 100f / frames).toInt() else 0
        return GaitMetrics(duration, steps, cadence, frames, visibleFrames, visibility)
    }
}
