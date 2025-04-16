package com.example.navalcombat.activities // Замените на ваш пакет

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.navalcombat.R // Убедитесь, что R импортирован
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random // Для случайных чисел Kotlin

// Состояния ячейки
enum class CellState {
    EMPTY, // Пусто
    SHIP,  // Корабль (не виден противнику)
    HIT,   // Попадание
    MISS,  // Промах
    SUNK   // Потоплен (можно добавить для визуала, пока не используется)
}

class GameActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var opponentGridView: RecyclerView
    private lateinit var playerGridView: RecyclerView

    private lateinit var opponentAdapter: GridAdapter
    private lateinit var playerAdapter: GridAdapter

    // Размеры поля
    private val gridSize = 10

    // Игровые поля (массив массивов с состояниями)
    private var playerBoard = createEmptyBoard()
    private var opponentBoard = createEmptyBoard() // Компьютер тоже использует CellState

    // Счетчик оставшихся "палуб" кораблей для определения победы
    // Стандартный флот: 1x4, 2x3, 3x2, 4x1 = 20 палуб
    private var playerShipsCells = 20
    private var opponentShipCells = 20

    // Состояние игры
    private var isPlayerTurn = true
    private var isGameOver = false

    // Размеры кораблей для расстановки
    private val shipSizes = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        statusTextView = findViewById(R.id.textViewGameStatus)
        opponentGridView = findViewById(R.id.opponentGrid)
        playerGridView = findViewById(R.id.playerGrid)

        setupGame()
        setupAdapters()
        updateStatusText()
    }

    // --- Настройка игры ---

    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    private fun setupGame() {
        // Сброс состояний
        playerBoard = createEmptyBoard()
        opponentBoard = createEmptyBoard()
        playerShipsCells = shipSizes.sum() // Сумма длин кораблей
        opponentShipCells = shipSizes.sum()
        isPlayerTurn = true
        isGameOver = false

        // Расстановка кораблей (пока случайная для обоих)
        placeShipsRandomly(playerBoard)
        placeShipsRandomly(opponentBoard)
    }

    private fun setupAdapters() {
        // Адаптер для поля противника (передаем данные и обработчик клика)
        opponentAdapter = GridAdapter(opponentBoard, true) { row, col ->
            handlePlayerShot(row, col)
        }
        opponentGridView.layoutManager = GridLayoutManager(this, gridSize)
        opponentGridView.adapter = opponentAdapter

        // Адаптер для поля игрока (без обработчика клика)
        playerAdapter = GridAdapter(playerBoard, false, null)
        playerGridView.layoutManager = GridLayoutManager(this, gridSize)
        playerGridView.adapter = playerAdapter
    }

    // --- Логика расстановки кораблей (упрощенная) ---

    private fun placeShipsRandomly(board: Array<Array<CellState>>) {
        for (size in shipSizes) {
            var placed = false
            while (!placed) {
                val row = Random.nextInt(gridSize)
                val col = Random.nextInt(gridSize)
                val isHorizontal = Random.nextBoolean()

                if (canPlaceShip(board, row, col, size, isHorizontal)) {
                    placeShip(board, row, col, size, isHorizontal)
                    placed = true
                }
            }
        }
    }

    // Проверяет, можно ли разместить корабль (в пределах поля и без наложения)
    private fun canPlaceShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean): Boolean {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col

            // 1. Проверка выхода за границы
            if (currentRow >= gridSize || currentCol >= gridSize) {
                return false
            }
            // 2. Проверка наложения на другие корабли (и соседние клетки, упрощенно)
            for (r in (currentRow - 1)..(currentRow + 1)) {
                for (c in (currentCol - 1)..(currentCol + 1)) {
                    if (r in 0 until gridSize && c in 0 until gridSize) {
                        if (board[r][c] == CellState.SHIP) {
                            return false // Занято или слишком близко
                        }
                    }
                }
            }
        }
        return true
    }

    // Размещает корабль на поле
    private fun placeShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean) {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            board[currentRow][currentCol] = CellState.SHIP
        }
    }

    // --- Логика ходов ---

    private fun handlePlayerShot(row: Int, col: Int) {
        if (!isPlayerTurn || isGameOver) return // Не ход игрока или игра закончена

        val cellState = opponentBoard[row][col]

        // Не стрелять в уже обстрелянные клетки
        if (cellState == CellState.HIT || cellState == CellState.MISS) {
            statusTextView.text = "Сюда уже стреляли!"
            return
        }

        if (cellState == CellState.SHIP) {
            // Попадание
            opponentBoard[row][col] = CellState.HIT
            opponentShipCells--
            statusTextView.text = "Попадание!"
            opponentAdapter.notifyItemChanged(row * gridSize + col)
            checkGameOver()
            // Игрок ходит снова (isPlayerTurn остается true)

        } else {
            // Промах
            opponentBoard[row][col] = CellState.MISS
            statusTextView.text = "Промах!"
            opponentAdapter.notifyItemChanged(row * gridSize + col)
            isPlayerTurn = false
            // Запускаем ход компьютера с небольшой задержкой
            Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000) // 1 секунда
        }
        updateStatusText() // Обновить текст статуса (чей ход)
    }

    private fun computerTurn() {
        if (isGameOver) return

        statusTextView.text = "Ход компьютера..."

        var row: Int
        var col: Int
        var isValidShot = false

        // ИИ компьютера: простой случайный выстрел в нестреляную клетку
        do {
            row = Random.nextInt(gridSize)
            col = Random.nextInt(gridSize)
            val cellState = playerBoard[row][col]
            if (cellState != CellState.HIT && cellState != CellState.MISS) {
                isValidShot = true
            }
        } while (!isValidShot)

        val targetState = playerBoard[row][col]

        // Добавляем задержку перед отображением результата хода компьютера
        Handler(Looper.getMainLooper()).postDelayed({
            if (targetState == CellState.SHIP) {
                // Попадание компьютера
                playerBoard[row][col] = CellState.HIT
                playerShipsCells--
                statusTextView.text = "Компьютер попал!"
                playerAdapter.notifyItemChanged(row * gridSize + col)
                checkGameOver()
                if (!isGameOver) {
                    // Компьютер ходит снова (после небольшой задержки)
                    Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000)
                }

            } else {
                // Промах компьютера
                playerBoard[row][col] = CellState.MISS
                statusTextView.text = "Компьютер промахнулся!"
                playerAdapter.notifyItemChanged(row * gridSize + col)
                isPlayerTurn = true // Переход хода игроку
                updateStatusText()
            }
        }, 1000) // 1 секунда задержки для отображения выстрела
    }

    // --- Проверка конца игры и статус ---

    private fun checkGameOver() {
        if (opponentShipCells <= 0) {
            statusTextView.text = "Вы победили!"
            isGameOver = true
            // TODO: Сохранить результат игры (например, передать в MainActivity)
        } else if (playerShipsCells <= 0) {
            statusTextView.text = "Компьютер победил!"
            isGameOver = true
            // TODO: Сохранить результат игры
        }
    }

    private fun updateStatusText() {
        if (isGameOver) return // Сообщение о победе/поражении уже установлено

        if (isPlayerTurn) {
            // Если статус не "Попадание!", показываем "Ваш ход"
            if (statusTextView.text != "Попадание!") {
                statusTextView.text = "Ваш ход"
            }
        } else {
            // Если статус не "Компьютер попал!", показываем "Ход компьютера..."
            if (statusTextView.text != "Компьютер попал!") {
                statusTextView.text = "Ход компьютера..."
            }
        }
    }

    // --- Адаптер для RecyclerView ---
    inner class GridAdapter(
        private val board: Array<Array<CellState>>,
        private val isOpponentBoard: Boolean, // true - поле противника, false - поле игрока
        private val onCellClick: ((row: Int, col: Int) -> Unit)? // Лямбда для клика
    ) : RecyclerView.Adapter<GridAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cellText: TextView = view.findViewById(R.id.cellTextView) // Находим TextView в grid_cell.xml
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.grid_cell, parent, false) // Используем наш макет ячейки

            // Устанавливаем размер ячейки, чтобы 10 поместились в ширину
            val displayMetrics = parent.context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val parentPadding = (parent as RecyclerView).paddingLeft + parent.paddingRight // Учитываем паддинг RecyclerView
            val cellSize = (screenWidth - parentPadding) / gridSize
            view.layoutParams.height = cellSize // Делаем ячейку квадратной

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = position / gridSize
            val col = position % gridSize
            val cellState = board[row][col]

            // Сбрасываем текст и фон по умолчанию
            holder.cellText.text = ""
            holder.cellText.setBackgroundResource(R.drawable.cell_border) // Сброс на обычную рамку
            holder.cellText.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.transparent)) // Прозрачный фон по умолчанию

            // Настраиваем вид ячейки в зависимости от состояния и чье это поле
            when (cellState) {
                CellState.EMPTY -> {
                    // Ничего не показываем (или фон воды)
                    holder.cellText.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.design_default_color_primary_variant)) // Синий фон
                }
                CellState.SHIP -> {
                    if (!isOpponentBoard) { // Показываем свои корабли
                        holder.cellText.setBackgroundColor(Color.GRAY)
                    } else {
                        // Корабли противника не видны
                        holder.cellText.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.design_default_color_primary_variant)) // Синий фон
                    }
                }
                CellState.HIT -> {
                    holder.cellText.text = "X"
                    holder.cellText.setBackgroundColor(Color.RED) // Попадание - красный фон
                    holder.cellText.setTextColor(Color.WHITE)
                }
                CellState.MISS -> {
                    holder.cellText.text = "•" // Точка для промаха
                    holder.cellText.setBackgroundColor(Color.LTGRAY) // Промах - светло-серый
                    holder.cellText.setTextColor(Color.DKGRAY)
                }
                CellState.SUNK -> { // Если решите использовать
                    holder.cellText.text = "X"
                    holder.cellText.setBackgroundColor(Color.BLACK)
                    holder.cellText.setTextColor(Color.RED)
                }
            }

            // Устанавливаем обработчик клика только для поля противника и если игра не закончена
            if (isOpponentBoard && onCellClick != null) {
                holder.itemView.setOnClickListener {
                    if (!isGameOver && isPlayerTurn) { // Кликаем только в свой ход
                        onCellClick.invoke(row, col)
                    }
                }
            } else {
                holder.itemView.setOnClickListener(null) // Убираем клик для поля игрока
            }
        }

        override fun getItemCount(): Int = gridSize * gridSize // Всего 100 ячеек
    }
}