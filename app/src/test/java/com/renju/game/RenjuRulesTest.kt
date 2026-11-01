package com.renju.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * В схемах: X — чёрный камень, O — белый, * — проверяемый ход чёрных, . — пусто.
 * Схема кладётся на доску со смещением (4, 4), так что края доски не мешают.
 */
class RenjuRulesTest {

    private fun foul(vararg rows: String): Foul {
        val board = Board()
        var px = -1
        var py = -1
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val x = 4 + c
                val y = 4 + r
                when (ch) {
                    'X' -> board[x, y] = BLACK
                    'O' -> board[x, y] = WHITE
                    '*' -> { px = x; py = y }
                }
            }
        }
        assertTrue("в схеме нет клетки *", px >= 0)
        return RenjuRules(board).foulFor(px, py)
    }

    // ---------- запреты ----------

    @Test fun doubleThreeIsForbidden() {
        assertEquals(Foul.DOUBLE_THREE, foul(
            "....X....",
            "....X....",
            "..XX*...."))
    }

    @Test fun singleThreeIsAllowed() {
        assertEquals(Foul.NONE, foul(
            ".........",
            ".........",
            "..XX*...."))
    }

    @Test fun threeBlockedByWhiteDoesNotCount() {
        assertEquals(Foul.NONE, foul(
            "....X....",
            "....X....",
            ".OXX*...."))
    }

    @Test fun brokenThreesCountToo() {
        assertEquals(Foul.DOUBLE_THREE, foul(
            "...X",
            "....",
            "...X",
            "X.X*"))
    }

    @Test fun doubleFourAcrossTwoLines() {
        assertEquals(Foul.DOUBLE_FOUR, foul(
            "XXX*",
            "...X",
            "...X",
            "...X"))
    }

    @Test fun doubleFourOnOneLine() {
        assertEquals(Foul.DOUBLE_FOUR, foul("X.X*X.X"))
    }

    @Test fun straightFourIsASingleFour() {
        assertEquals(Foul.NONE, foul("...XXX*...."))
    }

    @Test fun fourThreeIsAllowed() {
        assertEquals(Foul.NONE, foul(
            "XXX*",
            "...X",
            "...X"))
    }

    @Test fun fourThreeThreeIsForbidden() {
        assertEquals(Foul.DOUBLE_THREE, foul(
            "..X.X....",
            "...XX....",
            "XXX.*...."))
    }

    @Test fun overlineIsForbidden() {
        assertEquals(Foul.OVERLINE, foul("XX*XXX"))
    }

    @Test fun exactFiveIsNeverForbidden() {
        // пять в ряд одновременно с «три–три» — это победа
        assertEquals(Foul.NONE, foul(
            "..X.X....",
            "...XX....",
            "XXXX*...."))
        // а без горизонтальных камней тот же ход — запрещённое «три–три»
        assertEquals(Foul.DOUBLE_THREE, foul(
            "..X.X....",
            "...XX....",
            "....*...."))
    }

    @Test fun threeThatCanOnlyBeExtendedByForbiddenMoveIsFalse() {
        // горизонтальную тройку можно сделать открытой четвёркой только в точке, где
        // получился бы длинный ряд по вертикали, поэтому «три–три» нет
        assertEquals(Foul.NONE, foul(
            ".....X.....",
            ".....X..X..",
            ".....X..X..",
            "......XX*.O",
            ".....X.....",
            ".....X....."))
        // та же позиция без камней, запрещающих расширение, — настоящее «три–три»
        assertEquals(Foul.DOUBLE_THREE, foul(
            "...........",
            "........X..",
            "........X..",
            "......XX*.O",
            "...........",
            "..........."))
    }

    // ---------- партия ----------

    @Test fun firstMoveIsCenterAndOpeningZones() {
        val g = Game()
        assertEquals(1, g.moves.size)
        assertEquals(BLACK, g.board[9, 9])
        assertEquals(WHITE, g.turn)
        assertEquals(MoveResult.OCCUPIED, g.tryMove(9, 9))
        assertEquals(MoveResult.OPENING_ZONE, g.tryMove(12, 9))   // белые — только рядом с центром
        assertEquals(MoveResult.OK, g.tryMove(10, 9))
        assertEquals(MoveResult.OPENING_ZONE, g.tryMove(12, 9))   // чёрные — внутри 5×5
        assertEquals(MoveResult.OK, g.tryMove(11, 9))
        assertEquals(MoveResult.OK, g.tryMove(15, 15))            // дальше без ограничений
    }

    @Test fun blackWinsWithExactFive() {
        val g = Game()
        val seq = listOf(10 to 10, 8 to 9, 3 to 3, 7 to 9, 3 to 5, 6 to 9, 3 to 7, 5 to 9)
        for ((x, y) in seq) assertEquals(MoveResult.OK, g.tryMove(x, y))
        assertEquals(BLACK, g.winner)
        assertEquals(5, g.winLine.size)
        assertEquals(MoveResult.GAME_OVER, g.tryMove(0, 0))
    }

    @Test fun whiteWinsWithSix() {
        val g = Game()
        val seq = listOf(
            10 to 9, 7 to 7, 11 to 9, 2 to 2, 12 to 9, 2 to 6,
            14 to 9, 2 to 10, 15 to 9, 2 to 14, 13 to 9
        )
        for ((x, y) in seq) assertEquals("ход $x,$y", MoveResult.OK, g.tryMove(x, y))
        assertEquals(WHITE, g.winner)
        assertEquals(6, g.winLine.size)
    }

    private fun gameBeforeDoubleThree(): Game {
        val g = Game()
        val seq = listOf(8 to 8, 10 to 9, 3 to 3, 11 to 7, 3 to 5, 11 to 8, 3 to 7)
        for ((x, y) in seq) assertEquals(MoveResult.OK, g.tryMove(x, y))
        return g
    }

    @Test fun forbiddenMoveIsRejected() {
        val g = gameBeforeDoubleThree()
        assertEquals(BLACK, g.turn)
        assertTrue(g.forbiddenMarks.any { it.x == 11 && it.y == 9 && it.foul == Foul.DOUBLE_THREE })
        assertEquals(MoveResult.FORBIDDEN, g.tryMove(11, 9))
        assertEquals(EMPTY, g.board[11, 9])
        assertEquals(BLACK, g.turn)
        assertEquals(8, g.moves.size)
    }

    @Test fun strictModeLosesOnForbiddenMove() {
        val g = gameBeforeDoubleThree()
        g.strictFouls = true
        assertEquals(MoveResult.OK, g.tryMove(11, 9))
        assertEquals(WHITE, g.winner)
        assertEquals(Foul.DOUBLE_THREE, g.foulLoss)
        assertTrue(g.undo())
        assertEquals(EMPTY, g.winner)
        assertEquals(BLACK, g.turn)
        assertEquals(EMPTY, g.board[11, 9])
    }

    @Test fun undoAndRestore() {
        val g = Game()
        g.tryMove(10, 9); g.tryMove(11, 9); g.tryMove(5, 5)
        assertEquals(4, g.moves.size)
        assertTrue(g.undo())
        assertEquals(WHITE, g.turn)
        assertEquals(EMPTY, g.board[5, 5])
        assertTrue(g.undo()); assertTrue(g.undo())
        assertFalse(g.undo())                 // первый ход в центре не отменяется
        assertEquals(1, g.moves.size)

        g.tryMove(10, 9); g.tryMove(11, 9); g.tryMove(5, 5)
        val copy = Game()
        copy.restore(g.serialize())
        assertEquals(g.serialize(), copy.serialize())
        assertEquals(g.turn, copy.turn)
    }
}
