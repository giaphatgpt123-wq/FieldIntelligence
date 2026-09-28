package vn.survivallibrary.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Lightweight line icons drawn with Canvas so the native UI does not depend on emoji fonts or Compose. */
enum class NativeIcon {
    SEARCH, WATER, FOOD, SHELTER, SHIELD, LEAF, MUSHROOM, FISH, BUG, FLOWER, TREE, FRUIT,
    MEDICINE, CAMERA, HOME, GRID, BOOKMARK, REFRESH, CHEVRON_RIGHT, BACK, HEART
}

class NativeIconView(
    context: Context,
    private val icon: NativeIcon,
    color: Int
) : View(context) {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    fun setIconColor(color: Int) {
        stroke.color = color
        fill.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val sx = width / 24f
        val sy = height / 24f
        canvas.save()
        canvas.scale(sx, sy)
        drawIcon(canvas)
        canvas.restore()
    }

    private fun drawIcon(c: Canvas) {
        when (icon) {
            NativeIcon.SEARCH -> {
                c.drawCircle(10.5f, 10.5f, 5.3f, stroke)
                c.drawLine(14.3f, 14.3f, 20f, 20f, stroke)
            }
            NativeIcon.WATER -> {
                val p = Path().apply {
                    moveTo(12f, 2.5f); cubicTo(9f, 6.8f, 5.5f, 10.2f, 5.5f, 14.2f)
                    cubicTo(5.5f, 18.1f, 8.4f, 21f, 12f, 21f)
                    cubicTo(15.6f, 21f, 18.5f, 18.1f, 18.5f, 14.2f)
                    cubicTo(18.5f, 10.2f, 15f, 6.8f, 12f, 2.5f); close()
                }
                c.drawPath(p, stroke)
            }
            NativeIcon.FOOD, NativeIcon.LEAF -> {
                val p = Path().apply {
                    moveTo(4f, 15.5f); cubicTo(6f, 7f, 12.5f, 4f, 20f, 4f)
                    cubicTo(20f, 11.5f, 15f, 18f, 7f, 20f)
                    cubicTo(5.5f, 18.5f, 4.5f, 17f, 4f, 15.5f); close()
                }
                c.drawPath(p, stroke)
                c.drawPath(Path().apply { moveTo(6.5f, 17.5f); cubicTo(10f, 14f, 13.5f, 10.5f, 18f, 7f) }, stroke)
            }
            NativeIcon.SHELTER -> {
                c.drawPath(Path().apply { moveTo(3f, 19f); lineTo(12f, 5f); lineTo(21f, 19f); close() }, stroke)
                c.drawLine(12f, 5f, 12f, 19f, stroke)
                c.drawLine(8.5f, 19f, 12f, 13.5f, stroke)
                c.drawLine(15.5f, 19f, 12f, 13.5f, stroke)
            }
            NativeIcon.SHIELD -> {
                val p = Path().apply {
                    moveTo(12f, 3f); lineTo(19f, 6f); lineTo(18f, 14.5f)
                    cubicTo(17.2f, 18f, 14.8f, 20.2f, 12f, 21.5f)
                    cubicTo(9.2f, 20.2f, 6.8f, 18f, 6f, 14.5f); lineTo(5f, 6f); close()
                }
                c.drawPath(p, stroke)
                c.drawLine(9f, 12f, 11f, 14f, stroke); c.drawLine(11f, 14f, 15.5f, 9.5f, stroke)
            }
            NativeIcon.MUSHROOM -> {
                c.drawPath(Path().apply { moveTo(4f, 12f); cubicTo(4.5f, 6.8f, 7.8f, 4f, 12f, 4f); cubicTo(16.2f, 4f, 19.5f, 6.8f, 20f, 12f); close() }, stroke)
                c.drawLine(4f, 12f, 20f, 12f, stroke)
                c.drawPath(Path().apply { moveTo(9f, 12f); lineTo(8f, 20f); lineTo(16f, 20f); lineTo(15f, 12f) }, stroke)
            }
            NativeIcon.FISH -> {
                c.drawPath(Path().apply { moveTo(3f, 12f); cubicTo(7f, 7.5f, 14f, 7.5f, 18f, 12f); cubicTo(14f, 16.5f, 7f, 16.5f, 3f, 12f); close() }, stroke)
                c.drawPath(Path().apply { moveTo(18f, 12f); lineTo(22f, 8.5f); lineTo(22f, 15.5f); close() }, stroke)
                c.drawCircle(8f, 10.7f, 0.8f, fill)
            }
            NativeIcon.BUG -> {
                c.drawOval(RectF(8f, 6f, 16f, 19f), stroke)
                c.drawLine(8f, 9f, 5f, 7f, stroke); c.drawLine(16f, 9f, 19f, 7f, stroke)
                c.drawLine(7.5f, 12f, 4f, 12f, stroke); c.drawLine(16.5f, 12f, 20f, 12f, stroke)
                c.drawLine(8f, 16f, 5f, 18f, stroke); c.drawLine(16f, 16f, 19f, 18f, stroke)
                c.drawLine(10f, 6f, 8f, 3.5f, stroke); c.drawLine(14f, 6f, 16f, 3.5f, stroke)
            }
            NativeIcon.FLOWER -> {
                c.drawCircle(12f, 12f, 2.2f, stroke)
                c.drawOval(RectF(10f, 4f, 14f, 9f), stroke); c.drawOval(RectF(10f, 15f, 14f, 20f), stroke)
                c.drawOval(RectF(4f, 10f, 9f, 14f), stroke); c.drawOval(RectF(15f, 10f, 20f, 14f), stroke)
                c.drawLine(12f, 20f, 12f, 22f, stroke)
            }
            NativeIcon.TREE -> {
                c.drawPath(Path().apply { moveTo(12f, 3f); lineTo(5f, 13f); lineTo(9f, 13f); lineTo(6.5f, 17f); lineTo(17.5f, 17f); lineTo(15f, 13f); lineTo(19f, 13f); close() }, stroke)
                c.drawLine(12f, 17f, 12f, 21f, stroke)
            }
            NativeIcon.FRUIT -> {
                c.drawCircle(11f, 13f, 6f, stroke); c.drawCircle(15f, 13f, 5f, stroke)
                c.drawPath(Path().apply { moveTo(12f, 7f); cubicTo(12.5f, 4.5f, 14f, 3f, 16.5f, 3f) }, stroke)
                c.drawPath(Path().apply { moveTo(14f, 5f); cubicTo(16f, 4f, 18f, 4.5f, 19f, 6f) }, stroke)
            }
            NativeIcon.MEDICINE -> {
                c.drawRoundRect(RectF(6f, 5f, 18f, 20f), 3f, 3f, stroke)
                c.drawLine(9f, 3f, 15f, 3f, stroke); c.drawLine(10f, 3f, 10f, 5f, stroke); c.drawLine(14f, 3f, 14f, 5f, stroke)
                c.drawLine(12f, 9f, 12f, 16f, stroke); c.drawLine(8.5f, 12.5f, 15.5f, 12.5f, stroke)
            }
            NativeIcon.CAMERA -> {
                c.drawRoundRect(RectF(3f, 7f, 21f, 19f), 2.5f, 2.5f, stroke)
                c.drawPath(Path().apply { moveTo(7f, 7f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(17f, 7f) }, stroke)
                c.drawCircle(12f, 13f, 3.5f, stroke)
            }
            NativeIcon.HOME -> {
                c.drawPath(Path().apply { moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f); moveTo(6f, 10f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 10f); moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f) }, stroke)
            }
            NativeIcon.GRID -> {
                c.drawRoundRect(RectF(4f, 4f, 10f, 10f), 1.2f, 1.2f, stroke); c.drawRoundRect(RectF(14f, 4f, 20f, 10f), 1.2f, 1.2f, stroke)
                c.drawRoundRect(RectF(4f, 14f, 10f, 20f), 1.2f, 1.2f, stroke); c.drawRoundRect(RectF(14f, 14f, 20f, 20f), 1.2f, 1.2f, stroke)
            }
            NativeIcon.BOOKMARK -> {
                c.drawPath(Path().apply { moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 21f); lineTo(12f, 17.5f); lineTo(7f, 21f); close() }, stroke)
            }
            NativeIcon.REFRESH -> {
                c.drawPath(Path().apply { moveTo(19f, 8f); cubicTo(17f, 4.5f, 12.5f, 3.5f, 9f, 5f); cubicTo(6f, 6.3f, 4.5f, 9f, 4.5f, 12f) }, stroke)
                c.drawPath(Path().apply { moveTo(19f, 4.5f); lineTo(19f, 8f); lineTo(15.5f, 8f) }, stroke)
                c.drawPath(Path().apply { moveTo(5f, 16f); cubicTo(7f, 19.5f, 11.5f, 20.5f, 15f, 19f); cubicTo(18f, 17.7f, 19.5f, 15f, 19.5f, 12f) }, stroke)
                c.drawPath(Path().apply { moveTo(5f, 19.5f); lineTo(5f, 16f); lineTo(8.5f, 16f) }, stroke)
            }
            NativeIcon.CHEVRON_RIGHT -> {
                c.drawPath(Path().apply { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }, stroke)
            }
            NativeIcon.BACK -> {
                c.drawPath(Path().apply { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }, stroke)
            }
            NativeIcon.HEART -> {
                c.drawPath(Path().apply { moveTo(12f, 20f); cubicTo(10f, 18f, 4.5f, 14.5f, 4.5f, 9.5f); cubicTo(4.5f, 6.5f, 6.5f, 4.5f, 9f, 4.5f); cubicTo(10.5f, 4.5f, 11.5f, 5.3f, 12f, 6.3f); cubicTo(12.5f, 5.3f, 13.5f, 4.5f, 15f, 4.5f); cubicTo(17.5f, 4.5f, 19.5f, 6.5f, 19.5f, 9.5f); cubicTo(19.5f, 14.5f, 14f, 18f, 12f, 20f); close() }, stroke)
            }
        }
    }
}
