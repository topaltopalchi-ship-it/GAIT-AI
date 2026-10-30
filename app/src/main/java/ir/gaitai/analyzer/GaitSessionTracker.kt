package ir.gaitai.analyzer

/**
 * Lightweight, on-device session tracker.
 * Step count is an estimate based on alternating ankle lift peaks;
 * it is not a clinical gait measurement.
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

    private var recording = false
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

    fun addFrame(points: List<PosePoint>, bodyVisible: Boolean,
                 nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        if (!recording) return
        frames++
        if (!bodyVisible || points.size < 33) return
        visibleFrames++

        val left = points[27]
        val right = points[28]
        middleLeft?.let { mid ->
            val before = previousLeft
            if (before != null && left.visibility >= 0.35f && mid.visibility >= 0.35f &&
                before.visibility >= 0.35f && mid.y < before.y - 0.012f &&
                mid.y < left.y - 0.012f && nowMs - lastLeftStepAt > 280L) {
                steps++
                lastLeftStepAt = nowMs
            }
        }
        middleRight?.let { mid ->
            val before = previousRight
            if (before != null && right.visibility >= 0.35f && mid.visibility >= 0.35f &&
                before.visibility >= 0.35f && mid.y < before.y - 0.012f &&
                mid.y < right.y - 0.012f && nowMs - lastRightStepAt > 280L) {
                steps++
                lastRightStepAt = nowMs
            }
        }

        previousLeft = middleLeft
        middleLeft = Sample(nowMs, left.y, left.visibility)
        previousRight = middleRight
        middleRight = Sample(nowMs, right.y, right.visibility)
    }

    fun finish(nowMs: Long = android.os.SystemClock.elapsedRealtime()): GaitMetrics {
        recording = false
        val duration = ((nowMs - startedAt).coerceAtLeast(0L) / 1000L).toInt()
        val cadence = if (duration > 0) (steps * 60f / duration).toInt() else 0
        val visibility = if (frames > 0) (visibleFrames * 100f / frames).toInt() else 0
        return GaitMetrics(duration, steps, cadence, frames, visibleFrames, visibility)
    }
}
