package com.renju.game

import kotlin.math.abs

class Move(val x: Int, val y: Int, val color: Int)

class ForbiddenMark(val x: Int, val y: Int, val foul: Foul)

enum class MoveResult { OK, OCCUPIED, GAME_OVER, OPENING_ZONE, FORBIDDEN }

class Game {
    val board = Board()
    val rules = RenjuRules(board)
    val moves = ArrayList<Move>()

    var turn = BLACK
        private set

    /** Победитель (BLACK / WHITE) или EMPTY, если партия продолжается. */
    var winner = EMPTY
        private set
    var isDraw = false
        private set

    /** Если чёрные проиграли из-за запрещённого хода (режим «нарушение = поражение»). */
    var foulLoss = Foul.NONE
        private set

    /** Клетки выигрышного ряда (пары x, y). */
    var winLine: List<IntArray> = emptyList()
        private set

    /** true: запрещённый ход чёрных не отклоняется, а сразу проигрывает партию. */
    var strictFouls = false

    val center = BOARD_SIZE / 2
    val isOver get() = winner != EMPTY || isDraw

    private var marksCache: List<ForbiddenMark>? = null

    init {
        reset()
    }

    fun reset() {
        board.clear()
        moves.clear()
        turn = BLACK
        winner = EMPTY
        isDraw = false
        foulLoss = Foul.NONE
        winLine = emptyList()
        marksCache = null
        applyMove(center, center)   // 1-й ход чёрных — строго в центр
    }

    /** Полуразмер зоны, в которой обязан лежать ближайший ход (1 → 3×3, 2 → 5×5), либо -1 — без ограничений. */
    fun openingZone(): Int = if (isOver) -1 else when (moves.size) {
        1 -> 1   // 2-й ход (белые): рядом с центром
        2 -> 2   // 3-й ход (чёрные): внутри квадрата 5×5
        else -> -1
    }

    fun inOpeningZone(x: Int, y: Int): Boolean {
        val h = openingZone()
        return h < 0 || (abs(x - center) <= h && abs(y - center) <= h)
    }

    /** Нарушение, которое совершили бы чёрные, пойдя в (x, y). Для белых и занятых клеток — NONE. */
    fun foulAt(x: Int, y: Int): Foul =
        if (turn == BLACK && !isOver && board[x, y] == EMPTY) rules.foulFor(x, y) else Foul.NONE

    fun tryMove(x: Int, y: Int): MoveResult {
        if (isOver) return MoveResult.GAME_OVER
        if (!board.inside(x, y) || board[x, y] != EMPTY) return MoveResult.OCCUPIED
        if (!inOpeningZone(x, y)) return MoveResult.OPENING_ZONE
        if (turn == BLACK && !strictFouls && rules.foulFor(x, y) != Foul.NONE) return MoveResult.FORBIDDEN
        applyMove(x, y)
        return MoveResult.OK
    }

    private fun applyMove(x: Int, y: Int) {
        val color = turn
        val foul = if (color == BLACK) rules.foulFor(x, y) else Foul.NONE
        board[x, y] = color
        moves.add(Move(x, y, color))
        marksCache = null
        if (foul != Foul.NONE) {
            winner = WHITE
            foulLoss = foul
            return
        }
        val line = findWinLine(x, y, color)
        if (line != null) {
            winner = color
            winLine = line
            return
        }
        if (moves.size == BOARD_SIZE * BOARD_SIZE) {
            isDraw = true
            return
        }
        turn = opponent(color)
    }

    /** Чёрные выигрывают ровно пятью, белые — пятью и более. */
    private fun findWinLine(x: Int, y: Int, color: Int): List<IntArray>? {
        for (d in DIRECTIONS) {
            val n = runLength(board, x, y, d[0], d[1], color)
            val win = if (color == BLACK) n == 5 else n >= 5
            if (!win) continue
            var lo = 0
            while (board[x + d[0] * (lo - 1), y + d[1] * (lo - 1)] == color) lo--
            var hi = 0
            while (board[x + d[0] * (hi + 1), y + d[1] * (hi + 1)] == color) hi++
            return (lo..hi).map { intArrayOf(x + d[0] * it, y + d[1] * it) }
        }
        return null
    }

    /** Отмена последнего хода (первый ход в центре не отменяется). */
    fun undo(): Boolean {
        if (moves.size <= 1) return false
        val m = moves.removeAt(moves.size - 1)
        board[m.x, m.y] = EMPTY
        winner = EMPTY
        isDraw = false
        foulLoss = Foul.NONE
        winLine = emptyList()
        turn = opponent(moves[moves.size - 1].color)
        marksCache = null
        return true
    }

    /** Запретные для чёрных поля (кешируется до следующего хода). */
    val forbiddenMarks: List<ForbiddenMark>
        get() {
            marksCache?.let { return it }
            val computed = computeMarks()
            marksCache = computed
            return computed
        }

    private fun computeMarks(): List<ForbiddenMark> {
        if (isOver || turn != BLACK) return emptyList()
        if (moves.count { it.color == BLACK } < 4) return emptyList()
        val size = board.size
        val near = BooleanArray(size * size)
        for (m in moves) {
            if (m.color != BLACK) continue
            for (dy in -5..5) for (dx in -5..5) {
                val nx = m.x + dx
                val ny = m.y + dy
                if (board.inside(nx, ny)) near[ny * size + nx] = true
            }
        }
        val result = ArrayList<ForbiddenMark>()
        for (y in 0 until size) for (x in 0 until size) {
            if (!near[y * size + x] || board[x, y] != EMPTY) continue
            val foul = rules.foulFor(x, y)
            if (foul != Foul.NONE) result.add(ForbiddenMark(x, y, foul))
        }
        return result
    }

    fun serialize(): String = moves.joinToString(";") { "${it.x},${it.y}" }

    /** Восстановление партии по записи ходов; при ошибке в записи восстанавливается то, что удалось. */
    fun restore(data: String) {
        reset()
        val parts = data.split(";")
        for (i in 1 until parts.size) {
            if (isOver) break
            val xy = parts[i].split(",")
            val x = xy.getOrNull(0)?.toIntOrNull() ?: break
            val y = xy.getOrNull(1)?.toIntOrNull() ?: break
            if (!board.inside(x, y) || board[x, y] != EMPTY) break
            applyMove(x, y)
        }
    }
}
