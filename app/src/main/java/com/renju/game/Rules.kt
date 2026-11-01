package com.renju.game

const val BOARD_SIZE = 19
const val EMPTY = 0
const val BLACK = 1
const val WHITE = 2
const val OUTSIDE = 3

fun opponent(color: Int) = if (color == BLACK) WHITE else BLACK

/** Четыре направления линий: горизонталь, вертикаль, две диагонали. */
val DIRECTIONS = arrayOf(
    intArrayOf(1, 0),
    intArrayOf(0, 1),
    intArrayOf(1, 1),
    intArrayOf(1, -1)
)

/** Виды запрещённых для чёрных ходов. */
enum class Foul(val title: String) {
    NONE(""),
    OVERLINE("длинный ряд (6 и более)"),
    DOUBLE_FOUR("«четыре–четыре» (4×4)"),
    DOUBLE_THREE("«три–три» (3×3)")
}

class Board(val size: Int = BOARD_SIZE) {
    private val cells = IntArray(size * size)

    fun inside(x: Int, y: Int) = x in 0 until size && y in 0 until size

    /** Содержимое клетки; за пределами доски — OUTSIDE. */
    operator fun get(x: Int, y: Int): Int = if (inside(x, y)) cells[y * size + x] else OUTSIDE

    operator fun set(x: Int, y: Int, value: Int) {
        cells[y * size + x] = value
    }

    fun clear() = cells.fill(EMPTY)
}

/** Длина непрерывного ряда камней цвета [color] через клетку (x, y) вдоль (dx, dy). В клетке уже стоит камень. */
fun runLength(board: Board, x: Int, y: Int, dx: Int, dy: Int, color: Int): Int {
    var n = 1
    var i = 1
    while (board[x + dx * i, y + dy * i] == color) { n++; i++ }
    i = 1
    while (board[x - dx * i, y - dy * i] == color) { n++; i++ }
    return n
}

/**
 * Проверка запрещённых ходов чёрных по международным правилам рендзю (RIF):
 *  - ход, образующий ровно пять в ряд, всегда выигрывает и запретным не считается;
 *  - длинный ряд (6+), «четыре–четыре» и «три–три» запрещены;
 *  - «тройка» считается только если из неё можно сделать прямую (открытую) четвёрку
 *    ходом, который сам не является запрещённым (рекурсивно, п. 9.3 правил).
 */
class RenjuRules(private val board: Board) {

    /** Какое нарушение совершили бы чёрные, поставив камень в пустую клетку (x, y). */
    fun foulFor(x: Int, y: Int): Foul = check(x, y, 0)

    private fun check(x: Int, y: Int, depth: Int): Foul {
        if (board[x, y] != EMPTY) return Foul.NONE
        board[x, y] = BLACK
        try {
            var overline = false
            for (d in DIRECTIONS) {
                val n = runLength(board, x, y, d[0], d[1], BLACK)
                if (n == 5) return Foul.NONE          // ровно пять — победа, запреты не действуют
                if (n > 5) overline = true
            }
            if (overline) return Foul.OVERLINE

            var fours = 0
            for (d in DIRECTIONS) fours += countFours(x, y, d[0], d[1])
            if (fours >= 2) return Foul.DOUBLE_FOUR

            var threes = 0
            for (d in DIRECTIONS) {
                if (hasOpenThree(x, y, d[0], d[1], depth)) {
                    threes++
                    if (threes >= 2) return Foul.DOUBLE_THREE
                }
            }
            return Foul.NONE
        } finally {
            board[x, y] = EMPTY
        }
    }

    /**
     * Сколько различных «четвёрок» с участием (x, y) есть на линии. Четвёрка — четыре камня,
     * к которым одним ходом можно добавить пятый и получить ровно пять. Прямая четвёрка
     * (два способа завершить) считается за одну; «X.XXX.X» — за две.
     */
    private fun countFours(x: Int, y: Int, dx: Int, dy: Int): Int {
        val masks = ArrayList<Int>(2)
        for (k in -4..4) {
            if (k == 0) continue
            val ex = x + dx * k
            val ey = y + dy * k
            if (board[ex, ey] != EMPTY) continue
            board[ex, ey] = BLACK
            var lo = k
            while (board[x + dx * (lo - 1), y + dy * (lo - 1)] == BLACK) lo--
            var hi = k
            while (board[x + dx * (hi + 1), y + dy * (hi + 1)] == BLACK) hi++
            board[ex, ey] = EMPTY
            if (hi - lo + 1 == 5 && lo <= 0 && 0 <= hi) {
                var mask = 0
                for (t in lo..hi) if (t != k) mask = mask or (1 shl (t + 8))
                if (!masks.contains(mask)) masks.add(mask)
            }
        }
        return masks.size
    }

    /**
     * Есть ли на линии через (x, y) настоящая «тройка»: существует ход q, после которого
     * получается прямая четвёрка (оба конца свободны, оба завершения дают ровно пять),
     * и сам ход q не запрещён.
     */
    private fun hasOpenThree(x: Int, y: Int, dx: Int, dy: Int, depth: Int): Boolean {
        for (k in -4..4) {
            if (k == 0) continue
            val qx = x + dx * k
            val qy = y + dy * k
            if (board[qx, qy] != EMPTY) continue
            board[qx, qy] = BLACK
            var lo = k
            while (board[x + dx * (lo - 1), y + dy * (lo - 1)] == BLACK) lo--
            var hi = k
            while (board[x + dx * (hi + 1), y + dy * (hi + 1)] == BLACK) hi++
            val straightFour = hi - lo + 1 == 4 && lo <= 0 && 0 <= hi &&
                board[x + dx * (lo - 1), y + dy * (lo - 1)] == EMPTY &&
                board[x + dx * (hi + 1), y + dy * (hi + 1)] == EMPTY &&
                board[x + dx * (lo - 2), y + dy * (lo - 2)] != BLACK &&
                board[x + dx * (hi + 2), y + dy * (hi + 2)] != BLACK
            board[qx, qy] = EMPTY
            if (!straightFour) continue
            // Тройка «ложная», если единственный способ сделать четвёрку — запрещённый ход.
            if (depth < MAX_DEPTH && check(qx, qy, depth + 1) != Foul.NONE) continue
            return true
        }
        return false
    }

    private companion object {
        const val MAX_DEPTH = 6
    }
}
