package com.renju.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.min

/** Круглый значок камня: чёрный, белый или пустое кольцо (ничья). */
class StoneBadge(context: Context) : View(context) {

    var stone = BLACK
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9FB3C8.toInt()
    }
    private var blackShader: Shader? = null
    private var whiteShader: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = min(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f
        blackShader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.35f, r * 1.5f,
            intArrayOf(0xFF7A7A7A.toInt(), 0xFF1C1C1C.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        whiteShader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.35f, r * 1.5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFEDEDED.toInt(), 0xFFB9B9B9.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f - 2f * resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        if (stone == EMPTY) {
            ring.strokeWidth = 2f * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, r, ring)
            return
        }
        paint.shader = if (stone == BLACK) blackShader else whiteShader
        canvas.drawCircle(cx, cy, r, paint)
        if (stone == BLACK) {   // тонкое кольцо, чтобы чёрный камень был виден на тёмном фоне
            ring.strokeWidth = 1.5f * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, r, ring)
        }
    }
}
