package com.renju.game

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity(), BoardView.Listener {

    private val game = Game()
    private lateinit var boardView: BoardView
    private lateinit var badge: StoneBadge
    private lateinit var titleView: TextView
    private lateinit var hintView: TextView
    private lateinit var counterView: TextView
    private lateinit var undoButton: Button
    private lateinit var confirmButton: Button
    private lateinit var prefs: SharedPreferences

    private var showForbidden = true
    private var showNumbers = false
    private var confirmMoves = true
    private var strictFouls = false
    private var colorChoiceHint = true

    /** Временное предупреждение под заголовком (сбрасывается при следующем выборе клетки или ходе). */
    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("renju", MODE_PRIVATE)
        loadSettings()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = BAR_COLOR
        window.navigationBarColor = BAR_COLOR
        setContentView(buildUi())
        prefs.getString(KEY_MOVES, null)?.let { game.restore(it) }
        boardView.game = game
        applySettings()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        prefs.edit().putString(KEY_MOVES, game.serialize()).apply()
    }

    // ---------- интерфейс ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_COLOR)
        }

        badge = StoneBadge(this)
        titleView = TextView(this).apply {
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        hintView = TextView(this).apply {
            textSize = 13f
            setTextColor(MUTED_COLOR)
            maxLines = 2
        }
        counterView = TextView(this).apply {
            textSize = 13f
            setTextColor(MUTED_COLOR)
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(hintView)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BAR_COLOR)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            })
            addView(counterView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(top, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        boardView = BoardView(this).also { it.listener = this }
        root.addView(boardView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        undoButton = makeButton("Отмена", SECONDARY_COLOR) { onUndo() }
        confirmButton = makeButton("Поставить", PRIMARY_COLOR) { onConfirmButton() }
        val newButton = makeButton("Заново", SECONDARY_COLOR) { onNewGame() }
        val menuButton = makeButton("Меню", SECONDARY_COLOR) { showMenu() }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BAR_COLOR)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        for (b in listOf(undoButton, confirmButton, newButton, menuButton)) {
            bottom.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
        root.addView(bottom, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun makeButton(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.textSize = 14f
        b.maxLines = 1
        b.minWidth = 0
        b.minimumWidth = 0
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(dp(4), dp(12), dp(4), dp(12))
        b.stateListAnimator = null
        val bg = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(10).toFloat()
        }
        b.background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), bg, null)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun setButtonEnabled(b: Button, enabled: Boolean) {
        b.isEnabled = enabled
        b.alpha = if (enabled) 1f else 0.4f
    }

    // ---------- состояние экрана ----------

    private fun refresh() {
        counterView.text = "Ходов: ${game.moves.size}"
        when {
            game.winner != EMPTY -> {
                badge.stone = game.winner
                titleView.text = if (game.winner == BLACK) "Победа чёрных" else "Победа белых"
                hintView.text = resultHint()
                hintView.setTextColor(MUTED_COLOR)
            }
            game.isDraw -> {
                badge.stone = EMPTY
                titleView.text = "Ничья"
                hintView.text = "Все пересечения заняты"
                hintView.setTextColor(MUTED_COLOR)
            }
            else -> {
                badge.stone = game.turn
                titleView.text = if (game.turn == BLACK) "Ход чёрных" else "Ход белых"
                val (text, warning) = currentHint()
                hintView.text = text
                hintView.setTextColor(if (warning) WARNING_COLOR else MUTED_COLOR)
            }
        }
        setButtonEnabled(undoButton, game.moves.size > 1)
        setButtonEnabled(confirmButton, confirmMoves && boardView.selX >= 0 && !game.isOver)
        boardView.invalidate()
    }

    private fun resultHint(): String = when {
        game.foulLoss != Foul.NONE -> "Чёрные сделали запрещённый ход: ${game.foulLoss.title}"
        game.winLine.size == 5 -> "Пять в ряд!"
        else -> "Ряд из ${game.winLine.size} камней"
    }

    /** Текст подсказки и признак предупреждения (красный цвет). */
    private fun currentHint(): Pair<String, Boolean> {
        message?.let { return it to true }
        if (game.turn == BLACK && boardView.selX >= 0) {
            val foul = game.foulAt(boardView.selX, boardView.selY)
            if (foul != Foul.NONE) {
                val text = if (strictFouls) {
                    "Запрещено: ${foul.title}. Если пойти сюда, чёрные проиграют"
                } else {
                    "Запрещено для чёрных: ${foul.title}"
                }
                return text to true
            }
        }
        val text = when (game.moves.size) {
            1 -> "Открытие: белые ставят камень рядом с центром (зелёная зона)"
            2 -> "Открытие: третий ход чёрных — внутри зоны 5×5"
            else -> if (confirmMoves) {
                "Выберите пересечение и нажмите «Поставить» · щипок — масштаб"
            } else {
                "Коснитесь пересечения · щипок — масштаб"
            }
        }
        return text to false
    }

    // ---------- действия ----------

    override fun onCellSelected(x: Int, y: Int) {
        message = null
        refresh()
    }

    override fun onCellConfirmed(x: Int, y: Int) = makeMove(x, y)

    private fun onConfirmButton() {
        if (boardView.selX >= 0) makeMove(boardView.selX, boardView.selY)
    }

    private fun makeMove(x: Int, y: Int) {
        when (game.tryMove(x, y)) {
            MoveResult.OK -> {
                message = null
                boardView.clearSelection()
                refresh()
                if (game.isOver) {
                    showResultDialog()
                } else if (game.moves.size == 3 && colorChoiceHint) {
                    showColorChoiceDialog()
                }
            }
            MoveResult.FORBIDDEN -> {
                message = "Запрещённый ход: ${game.foulAt(x, y).title}"
                if (!confirmMoves) boardView.clearSelection()
                refresh()
            }
            MoveResult.OPENING_ZONE -> {
                message = if (game.moves.size == 1) {
                    "Белые: 2-й ход только рядом с центром (зелёная зона)"
                } else {
                    "Чёрные: 3-й ход только внутри зоны 5×5 (зелёная зона)"
                }
                if (!confirmMoves) boardView.clearSelection()
                refresh()
            }
            else -> if (!confirmMoves) boardView.clearSelection()
        }
    }

    private fun onUndo() {
        if (game.undo()) {
            message = null
            boardView.clearSelection()
            refresh()
        }
    }

    private fun onNewGame() {
        if (game.moves.size <= 1 || game.isOver) {
            startNewGame()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Новая партия")
            .setMessage("Текущая партия будет потеряна. Начать заново?")
            .setPositiveButton("Начать") { _, _ -> startNewGame() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startNewGame() {
        game.reset()
        message = null
        boardView.clearSelection()
        boardView.resetZoom()
        refresh()
    }

    // ---------- диалоги ----------

    private fun showResultDialog() {
        if (isFinishing) return
        val title = when {
            game.winner == BLACK -> "Победили чёрные!"
            game.winner == WHITE -> "Победили белые!"
            else -> "Ничья"
        }
        val body = if (game.isDraw) "Все пересечения заняты." else resultHint()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("Новая партия") { _, _ -> startNewGame() }
            .setNegativeButton("Посмотреть доску", null)
            .show()
    }

    private fun showColorChoiceDialog() {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("Открытие завершено")
            .setMessage(
                "Первые три хода (чёрные, белые, чёрные) делает один игрок — так он выбирает " +
                    "начальную расстановку. Теперь второй игрок решает, за какой цвет играть: " +
                    "остаться белым или поменяться местами с первым игроком.\n\nДальше ходят белые."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Настройки", "Правила игры", "Сбросить масштаб")) { _, which ->
                when (which) {
                    0 -> showSettings()
                    1 -> showRules()
                    2 -> boardView.resetZoom()
                }
            }
            .show()
    }

    private fun showSettings() {
        val items = arrayOf(
            "Показывать запретные поля для чёрных",
            "Номера ходов на камнях",
            "Ставить камень по подтверждению («Поставить»)",
            "Запретный ход чёрных = поражение",
            "Подсказка о выборе цвета после 3-го хода"
        )
        val checked = booleanArrayOf(showForbidden, showNumbers, confirmMoves, strictFouls, colorChoiceHint)
        AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setMultiChoiceItems(items, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Готово") { _, _ ->
                showForbidden = checked[0]
                showNumbers = checked[1]
                confirmMoves = checked[2]
                strictFouls = checked[3]
                colorChoiceHint = checked[4]
                saveSettings()
                applySettings()
                message = null
                boardView.clearSelection()
                refresh()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRules() {
        AlertDialog.Builder(this)
            .setTitle("Правила рендзю")
            .setMessage(RULES_TEXT)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    // ---------- настройки ----------

    private fun loadSettings() {
        showForbidden = prefs.getBoolean(KEY_SHOW_FORBIDDEN, true)
        showNumbers = prefs.getBoolean(KEY_SHOW_NUMBERS, false)
        confirmMoves = prefs.getBoolean(KEY_CONFIRM, true)
        strictFouls = prefs.getBoolean(KEY_STRICT, false)
        colorChoiceHint = prefs.getBoolean(KEY_COLOR_HINT, true)
    }

    private fun saveSettings() {
        prefs.edit()
            .putBoolean(KEY_SHOW_FORBIDDEN, showForbidden)
            .putBoolean(KEY_SHOW_NUMBERS, showNumbers)
            .putBoolean(KEY_CONFIRM, confirmMoves)
            .putBoolean(KEY_STRICT, strictFouls)
            .putBoolean(KEY_COLOR_HINT, colorChoiceHint)
            .apply()
    }

    private fun applySettings() {
        boardView.showForbidden = showForbidden
        boardView.showNumbers = showNumbers
        boardView.confirmMoves = confirmMoves
        game.strictFouls = strictFouls
    }

    private companion object {
        const val KEY_MOVES = "moves"
        const val KEY_SHOW_FORBIDDEN = "showForbidden"
        const val KEY_SHOW_NUMBERS = "showNumbers"
        const val KEY_CONFIRM = "confirmMoves"
        const val KEY_STRICT = "strictFouls"
        const val KEY_COLOR_HINT = "colorHint"

        val BG_COLOR = 0xFF1E2A38.toInt()
        val BAR_COLOR = 0xFF17212C.toInt()
        val MUTED_COLOR = 0xFF9FB3C8.toInt()
        val WARNING_COLOR = 0xFFFF8A80.toInt()
        val PRIMARY_COLOR = 0xFF2A6FDB.toInt()
        val SECONDARY_COLOR = 0xFF2D3E52.toInt()

        const val RULES_TEXT =
            "Играют двое на доске 19×19. Чёрные ходят первыми, дальше ходы по очереди. " +
                "Камни ставятся на пересечения линий и не двигаются.\n\n" +
                "ПОБЕДА\n" +
                "Чёрные побеждают, выстроив ровно пять камней в ряд (по горизонтали, вертикали или " +
                "диагонали). Шесть и более для чёрных — не победа, а нарушение. " +
                "Белые побеждают пятью и более камнями в ряд.\n\n" +
                "ОТКРЫТИЕ\n" +
                "1-й ход чёрных — строго в центр (ставится сам). " +
                "2-й ход белых — на соседнее с центром пересечение (зона 3×3). " +
                "3-й ход чёрных — внутри квадрата 5×5 вокруг центра. " +
                "Эти три хода делает один игрок, после чего второй игрок выбирает цвет " +
                "(можно поменяться). Дальше — без ограничений.\n\n" +
                "ЗАПРЕТЫ ДЛЯ ЧЁРНЫХ (красные крестики)\n" +
                "• Длинный ряд — шесть и более камней подряд.\n" +
                "• «Четыре–четыре» — ход, после которого возникают сразу две четвёрки " +
                "(четвёрка — четыре камня, которые одним ходом дополняются до пяти).\n" +
                "• «Три–три» — ход, после которого возникают сразу две открытые тройки " +
                "(тройка — три камня, которые одним ходом превращаются в открытую четвёрку, " +
                "то есть можно завершить пятёркой с любого конца).\n\n" +
                "ОСОБЕННОСТИ\n" +
                "• Ход, дающий ровно пять в ряд, — всегда победа, даже если он выглядит запретным.\n" +
                "• «Четыре–три» разрешено. «Четыре–три–три» запрещено.\n" +
                "• Тройка не считается, если превратить её в открытую четвёрку можно только " +
                "запрещённым ходом.\n" +
                "• Белым ничего не запрещено; если чёрные сделали запрещённый ход, " +
                "они проигрывают.\n\n" +
                "УПРАВЛЕНИЕ\n" +
                "Коснитесь пересечения, чтобы выбрать его, затем нажмите «Поставить» " +
                "(или коснитесь ещё раз). Щипок — масштаб, перетаскивание — сдвиг доски. " +
                "В настройках можно включить мгновенную постановку камня и режим " +
                "«запретный ход = поражение»."
    }
}
