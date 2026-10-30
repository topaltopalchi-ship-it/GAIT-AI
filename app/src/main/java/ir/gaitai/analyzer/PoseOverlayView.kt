package ir.gaitai.analyzer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class PoseOverlayView(context: Context) : View(context) {
    @Volatile private var points: List<PosePoint> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(54, 214, 176)
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val connections = listOf(
        11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 24,
        23 to 25, 25 to 27, 27 to 29, 29 to 31,
        24 to 26, 26 to 28, 28 to 30, 30 to 32,
        15 to 17, 15 to 19, 15 to 21, 16 to 18, 16 to 20, 16 to 22
    )

    fun setLandmarks(value: List<PosePoint>) {
        points = value
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        val visible = points.mapIndexedNotNull { index, p ->
            if (p.visibility >= 0.35f) index to p else null
        }.toMap()

        fun px(p: PosePoint) = p.x * width
        fun py(p: PosePoint) = p.y * height

        connections.forEach { (a, b) ->
            val p1 = visible[a] ?: return@forEach
            val p2 = visible[b] ?: return@forEach
            canvas.drawLine(px(p1), py(p1), px(p2), py(p2), linePaint)
        }
        visible.values.forEach { canvas.drawCircle(px(it), py(it), 6f, pointPaint) }
    }
}
