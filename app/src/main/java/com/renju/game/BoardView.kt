package com.renju.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Рисует доску 19×19, камни, запретные поля; поддерживает щипок-масштаб и перетаскивание. */
class BoardView(context: Context) : View(context) {

    interface Listener {
        fun onCellSelected(x: Int, y: Int)
        fun onCellConfirmed(x: Int, y: Int)
    }

    var listener: Listener? = null
    var game: Game = Game()
        set(value) {
            field = value
            clearSelection()
        }
    var showForbidden = true
        set(value) { field = value; invalidate() }
    var showNumbers = false
        set(value) { field = value; invalidate() }
    /** true — сначала выбор пересечения, потом подтверждение; false — камень ставится сразу. */
    var confirmMoves = true

    var selX = -1
        private set
    var selY = -1
        private set

    fun clearSelection() {
        selX = -1
        selY = -1
        invalidate()
    }

    // ---------- геометрия ----------
    private var cell = 0f
    private var baseLeft = 0f
    private var baseTop = 0f
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private val maxScale = 3.5f

    private val dp = resources.displayMetrics.density

    // ---------- краски ----------
    private val tableColor = 0xFF1E2A38.toInt()
    private val boardPaint = Paint().apply { color = 0xFFE3B866.toInt() }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A2A14.toInt(); style = Paint.Style.STROKE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A2A14.toInt(); style = Paint.Style.STROKE }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A2A14.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B4F22.toInt(); textAlign = Paint.Align.CENTER
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -0.35f, -0.35f, 1.5f,
            intArrayOf(0xFF7A7A7A.toInt(), 0xFF1C1C1C.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -0.35f, -0.35f, 1.5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFEDEDED.toInt(), 0xFFB9B9B9.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8C8C8C.toInt(); style = Paint.Style.STROKE
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC8372D.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    // ---------- жесты ----------
    private var multiTouch = false
    private var lastFx = 0f
    private var lastFy = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFx = detector.focusX
            lastFy = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scale * detector.scaleFactor).coerceIn(1f, maxScale)
            val bx = (lastFx - tx) / scale
            val by = (lastFy - ty) / scale
            scale = newScale
            tx = detector.focusX - bx * newScale
            ty = detector.focusY - by * newScale
            lastFx = detector.focusX
            lastFy = detector.focusY
            clampTranslation()
            invalidate()
            return true
        }
    }).apply { isQuickScaleEnabled = false }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleDetector.isInProgress || e2.pointerCount > 1) return false
            tx -= distanceX
            ty -= distanceY
            clampTranslation()
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!multiTouch) handleTap(e.x, e.y)
            return true
        }
    }).apply { setIsLongpressEnabled(false) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> multiTouch = false
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun handleTap(sx: Float, sy: Float) {
        if (game.isOver || cell <= 0f) return
        val bx = (sx - tx) / scale
        val by = (sy - ty) / scale
        val i = ((bx - baseLeft) / cell - 1f).roundToInt()
        val j = ((by - baseTop) / cell - 1f).roundToInt()
        if (i !in 0 until BOARD_SIZE || j !in 0 until BOARD_SIZE) return
        if (game.board[i, j] != EMPTY) return
        if (!confirmMoves) {
            selX = i; selY = j
            listener?.onCellConfirmed(i, j)
        } else if (i == selX && j == selY) {
            listener?.onCellConfirmed(i, j)
        } else {
            selX = i; selY = j
            invalidate()
            listener?.onCellSelected(i, j)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cell = min(w, h) / (BOARD_SIZE + 1f)
        baseLeft = (w - (BOARD_SIZE + 1) * cell) / 2f
        baseTop = (h - (BOARD_SIZE + 1) * cell) / 2f
        scale = 1f
        tx = 0f
        ty = 0f
    }

    fun resetZoom() {
        scale = 1f
        tx = 0f
        ty = 0f
        invalidate()
    }

    private fun clampTranslation() {
        val full = (BOARD_SIZE + 1) * cell
        tx = clampAxis(tx, baseLeft, full, width.toFloat())
        ty = clampAxis(ty, baseTop, full, height.toFloat())
    }

    private fun clampAxis(t: Float, base: Float, size: Float, view: Float): Float {
        return if (size * scale <= view) {
            (view - size * scale) / 2f - base * scale
        } else {
            min(max(t, view - (base + size) * scale), -base * scale)
        }
    }

    // ---------- рисование ----------
    private fun px(i: Int) = baseLeft + (i + 1) * cell
    private fun py(j: Int) = baseTop + (j + 1) * cell

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(tableColor)
        if (cell <= 0f) return
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(scale, scale)
        drawBoard(canvas)
        drawOpeningZone(canvas)
        drawWinLine(canvas)
        drawForbiddenMarks(canvas)
        drawStones(canvas)
        drawSelection(canvas)
        canvas.restore()
    }

    private fun drawTextCentered(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val m = paint.fontMetrics
        canvas.drawText(text, cx, cy - (m.ascent + m.descent) / 2f, paint)
    }

    private fun drawBoard(canvas: Canvas) {
        val n = BOARD_SIZE
        canvas.drawRect(baseLeft, baseTop, baseLeft + (n + 1) * cell, baseTop + (n + 1) * cell, boardPaint)
        gridPaint.strokeWidth = max(1f, cell * 0.035f)
        borderPaint.strokeWidth = max(2f, cell * 0.08f)
        for (i in 0 until n) {
            canvas.drawLine(px(i), py(0), px(i), py(n - 1), gridPaint)
            canvas.drawLine(px(0), py(i), px(n - 1), py(i), gridPaint)
        }
        canvas.drawRect(px(0), py(0), px(n - 1), py(n - 1), borderPaint)
        for (a in intArrayOf(3, 9, 15)) for (b in intArrayOf(3, 9, 15)) {
            canvas.drawCircle(px(a), py(b), cell * 0.1f, starPaint)
        }
        labelPaint.textSize = cell * 0.36f
        for (i in 0 until n) {
            val letter = ('A' + i).toString()
            drawTextCentered(canvas, letter, px(i), py(0) - cell * 0.62f, labelPaint)
            drawTextCentered(canvas, letter, px(i), py(n - 1) + cell * 0.62f, labelPaint)
            val number = (n - i).toString()
            drawTextCentered(canvas, number, px(0) - cell * 0.62f, py(i), labelPaint)
            drawTextCentered(canvas, number, px(n - 1) + cell * 0.62f, py(i), labelPaint)
        }
    }

    private fun drawOpeningZone(canvas: Canvas) {
        val h = game.openingZone()
        if (h < 0) return
        val c = game.center
        val l = px(c - h) - cell * 0.5f
        val t = py(c - h) - cell * 0.5f
        val r = px(c + h) + cell * 0.5f
        val b = py(c + h) + cell * 0.5f
        fillPaint.color = 0x332E7D32
        canvas.drawRect(l, t, r, b, fillPaint)
        ringPaint.color = 0xAA2E7D32.toInt()
        ringPaint.strokeWidth = max(2f, cell * 0.06f)
        canvas.drawRect(l, t, r, b, ringPaint)
    }

    private fun drawWinLine(canvas: Canvas) {
        val line = game.winLine
        if (line.size < 2) return
        val first = line.first()
        val last = line.last()
        linePaint.color = 0xE6F2B83B.toInt()
        linePaint.strokeWidth = cell * 0.2f
        canvas.drawLine(px(first[0]), py(first[1]), px(last[0]), py(last[1]), linePaint)
    }

    private fun drawForbiddenMarks(canvas: Canvas) {
        if (!showForbidden || game.isOver || game.turn != BLACK) return
        markPaint.strokeWidth = max(2f, cell * 0.1f)
        val a = cell * 0.2f
        for (m in game.forbiddenMarks) {
            val cx = px(m.x)
            val cy = py(m.y)
            canvas.drawLine(cx - a, cy - a, cx + a, cy + a, markPaint)
            canvas.drawLine(cx - a, cy + a, cx + a, cy - a, markPaint)
        }
    }

    private fun drawStone(canvas: Canvas, cx: Float, cy: Float, color: Int, alpha: Int = 255) {
        val r = cell * 0.46f
        shadowPaint.alpha = 100 * alpha / 255
        canvas.drawCircle(cx + r * 0.1f, cy + r * 0.14f, r, shadowPaint)
        val paint = if (color == BLACK) blackPaint else whitePaint
        paint.alpha = alpha
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(r, r)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        if (color == WHITE) {
            outlinePaint.strokeWidth = max(1f, cell * 0.03f)
            outlinePaint.alpha = alpha
            canvas.drawCircle(cx, cy, r, outlinePaint)
        }
    }

    private fun drawStones(canvas: Canvas) {
        val moves = game.moves
        for (m in moves) drawStone(canvas, px(m.x), py(m.y), m.color)
        if (moves.isEmpty()) return

        if (showNumbers) {
            for ((index, m) in moves.withIndex()) {
                val number = index + 1
                numberPaint.textSize = cell * if (number >= 100) 0.34f else 0.42f
                val isLast = index == moves.size - 1
                numberPaint.color = when {
                    isLast -> if (m.color == BLACK) 0xFFFF6E64.toInt() else 0xFFC8372D.toInt()
                    m.color == BLACK -> 0xFFFFFFFF.toInt()
                    else -> 0xFF111111.toInt()
                }
                drawTextCentered(canvas, number.toString(), px(m.x), py(m.y), numberPaint)
            }
        } else {
            val last = moves.last()
            fillPaint.color = if (last.color == BLACK) 0xFFFF6E64.toInt() else 0xFFC8372D.toInt()
            canvas.drawCircle(px(last.x), py(last.y), cell * 0.12f, fillPaint)
        }

        if (game.foulLoss != Foul.NONE) {
            val last = moves.last()
            ringPaint.color = 0xFFC8372D.toInt()
            ringPaint.strokeWidth = cell * 0.1f
            canvas.drawCircle(px(last.x), py(last.y), cell * 0.55f, ringPaint)
        }
        for (c in game.winLine) {
            ringPaint.color = 0xFFF2B83B.toInt()
            ringPaint.strokeWidth = cell * 0.09f
            canvas.drawCircle(px(c[0]), py(c[1]), cell * 0.52f, ringPaint)
        }
    }

    private fun drawSelection(canvas: Canvas) {
        if (selX < 0 || game.isOver || game.board[selX, selY] != EMPTY) return
        val cx = px(selX)
        val cy = py(selY)
        drawStone(canvas, cx, cy, game.turn, 150)
        val foul = game.foulAt(selX, selY)
        ringPaint.color = if (foul != Foul.NONE) 0xFFC8372D.toInt() else 0xFF1E88E5.toInt()
        ringPaint.strokeWidth = max(2f, cell * 0.08f)
        canvas.drawCircle(cx, cy, cell * 0.56f, ringPaint)
    }
}
