package com.example.navalcombat.activities // Убедитесь, что пакет ваш

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout // <<< ИЗМЕНЕНО
import com.example.navalcombat.R
import java.util.*
import kotlin.random.Random

// Состояния ячейки (остается без изменений)
enum class CellState {
    EMPTY, SHIP, HIT, MISS, SUNK
}

class GameActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    // --- ИЗМЕНЕНО: Тип переменных для Grid ---
    private lateinit var opponentGridView: GridLayout
    private lateinit var playerGridView: GridLayout
    // ---

    // --- ДОБАВЛЕНО: Хранение ссылок на View ячеек ---
    private var opponentCellViews: Array<Array<TextView?>> = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
    private var playerCellViews: Array<Array<TextView?>> = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
    // ---

    // --- УДАЛЕНО: Переменные для адаптеров ---
    // private lateinit var opponentAdapter: GridAdapter
    // private lateinit var playerAdapter: GridAdapter
    // ---

    // Размеры поля
    private val gridSize = 10

    // Игровые поля (остается без изменений)
    private var playerBoard = createEmptyBoard()
    private var opponentBoard = createEmptyBoard()

    // Счетчик оставшихся "палуб" (остается без изменений)
    private var playerShipsCells = 20
    private var opponentShipCells = 20

    // Состояние игры (остается без изменений)
    private var isPlayerTurn = true
    private var isGameOver = false

    // Размеры кораблей (остается без изменений)
    private val shipSizes = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game) // Убедитесь, что используется макет с GridLayout

        statusTextView = findViewById(R.id.textViewGameStatus)
        // --- ИЗМЕНЕНО: findViewById для GridLayout ---
        opponentGridView = findViewById(R.id.opponentGrid)
        playerGridView = findViewById(R.id.playerGrid)
        // ---

        setupGame() // Расстановка кораблей на логических досках

        // --- ИЗМЕНЕНО: Создание ячеек вместо настройки адаптеров ---
        createGridCells(opponentGridView, opponentBoard, true) // true - это поле противника
        createGridCells(playerGridView, playerBoard, false) // false - это поле игрока
        // ---

        updateStatusText()
    }

    // --- Настройка игры (createEmptyBoard, setupGame, placeShipsRandomly, canPlaceShip, placeShip) ---
    // Эти методы остаются БЕЗ ИЗМЕНЕНИЙ, т.к. они работают с логической моделью доски (Array<Array<CellState>>)
    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    private fun setupGame() {
        playerBoard = createEmptyBoard()
        opponentBoard = createEmptyBoard()
        playerShipsCells = shipSizes.sum()
        opponentShipCells = shipSizes.sum()
        isPlayerTurn = true
        isGameOver = false
        placeShipsRandomly(playerBoard)
        placeShipsRandomly(opponentBoard)
        // Очищаем ссылки на старые View, если игра перезапускается
        opponentCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
    }

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

    private fun canPlaceShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean): Boolean {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            if (currentRow >= gridSize || currentCol >= gridSize) return false
            for (r in (currentRow - 1)..(currentRow + 1)) {
                for (c in (currentCol - 1)..(currentCol + 1)) {
                    if (r in 0 until gridSize && c in 0 until gridSize) {
                        if (board[r][c] == CellState.SHIP) return false
                    }
                }
            }
        }
        return true
    }

    private fun placeShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean) {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            board[currentRow][currentCol] = CellState.SHIP
        }
    }
    // --- Конец неизмененных методов настройки ---


    // --- УДАЛЕНО: Метод setupAdapters() ---


    // --- ДОБАВЛЕНО: Метод для создания и добавления ячеек в GridLayout ---
    private fun createGridCells(grid: GridLayout, board: Array<Array<CellState>>, isOpponent: Boolean) {
        grid.removeAllViews() // Очищаем предыдущие View, если были
        val cellReferences = if (isOpponent) opponentCellViews else playerCellViews

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cellView = LayoutInflater.from(this).inflate(R.layout.grid_cell, grid, false) as TextView

                val params = GridLayout.LayoutParams()
                params.width = 0
                params.height = 0
                // Важно: используем веса, чтобы ячейки заняли все пространство грида
                params.rowSpec = GridLayout.spec(row, 1, 1f)
                params.columnSpec = GridLayout.spec(col, 1, 1f)
                params.setMargins(1, 1, 1, 1) // Маленькие отступы для линий сетки
                cellView.layoutParams = params

                cellReferences[row][col] = cellView // Сохраняем ссылку

                updateCellView(cellView, board[row][col], isOpponent) // Устанавливаем начальный вид

                if (isOpponent) { // Клик только на поле противника
                    cellView.setOnClickListener {
                        if (!isGameOver && isPlayerTurn) { // Проверяем ход и конец игры
                            handlePlayerShot(row, col)
                        }
                    }
                } else {
                    cellView.isClickable = false // Явно делаем некликабельным поле игрока
                }

                grid.addView(cellView) // Добавляем ячейку в GridLayout
            }
        }
    }
    // ---


    // --- ДОБАВЛЕНО: Метод для обновления вида одной ячейки ---
    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return // Если View нет, выходим

        // Сброс
        cellView.text = ""
        // Можно убрать setBackgroundResource, если отступы создают сетку
        // cellView.setBackgroundResource(R.drawable.cell_border)
        cellView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))

        when (state) {
            CellState.EMPTY -> {
                cellView.setBackgroundColor(ContextCompat.getColor(this, R.color.design_default_color_primary_variant)) // Синий фон
            }
            CellState.SHIP -> {
                if (!isOpponent) { // Свои корабли
                    cellView.setBackgroundColor(Color.GRAY)
                } else { // Корабли противника не видны
                    cellView.setBackgroundColor(ContextCompat.getColor(this, R.color.design_default_color_primary_variant)) // Синий фон
                }
            }
            CellState.HIT -> {
                cellView.text = "X"
                cellView.setBackgroundColor(Color.RED)
                cellView.setTextColor(Color.WHITE)
            }
            CellState.MISS -> {
                cellView.text = "•"
                cellView.setBackgroundColor(Color.LTGRAY)
                cellView.setTextColor(Color.DKGRAY)
            }
            CellState.SUNK -> {
                cellView.text = "X"
                cellView.setBackgroundColor(Color.BLACK)
                cellView.setTextColor(Color.RED)
            }
        }
    }
    // ---


    // --- Логика ходов (ИЗМЕНЕНЫ вызовы обновления UI) ---

    private fun handlePlayerShot(row: Int, col: Int) {
        if (!isPlayerTurn || isGameOver) return

        val currentCellView = opponentCellViews[row][col] // Получаем View ячейки
        val cellState = opponentBoard[row][col]

        if (cellState == CellState.HIT || cellState == CellState.MISS) {
            statusTextView.text = "Сюда уже стреляли!"
            return
        }

        if (cellState == CellState.SHIP) {
            opponentBoard[row][col] = CellState.HIT
            opponentShipCells--
            statusTextView.text = "Попадание!"
            updateCellView(currentCellView, CellState.HIT, true) // Обновляем View
            checkGameOver()
            // Игрок ходит снова, isPlayerTurn = true
        } else { // Промах
            opponentBoard[row][col] = CellState.MISS
            statusTextView.text = "Промах!"
            updateCellView(currentCellView, CellState.MISS, true) // Обновляем View
            isPlayerTurn = false
            Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000)
        }
        // Обновляем статус только если не было попадания (т.к. игрок ходит снова)
        if (!isPlayerTurn) updateStatusText()
    }

    private fun computerTurn() {
        if (isGameOver) return

        statusTextView.text = "Ход компьютера..."

        var row: Int
        var col: Int
        var isValidShot: Boolean
        // Простой случайный выбор незатронутой клетки
        do {
            row = Random.nextInt(gridSize)
            col = Random.nextInt(gridSize)
            val state = playerBoard[row][col]
            isValidShot = state != CellState.HIT && state != CellState.MISS
        } while (!isValidShot)

        val targetState = playerBoard[row][col]
        val targetCellView = playerCellViews[row][col] // Получаем View ячейки игрока

        // Задержка для имитации "раздумий" и отображения
        Handler(Looper.getMainLooper()).postDelayed({
            if (targetState == CellState.SHIP) { // Попадание компьютера
                playerBoard[row][col] = CellState.HIT
                playerShipsCells--
                statusTextView.text = "Компьютер попал!"
                updateCellView(targetCellView, CellState.HIT, false) // Обновляем View
                checkGameOver()
                if (!isGameOver) {
                    // Компьютер ходит снова
                    Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000)
                }
            } else { // Промах компьютера
                playerBoard[row][col] = CellState.MISS
                statusTextView.text = "Компьютер промахнулся!"
                updateCellView(targetCellView, CellState.MISS, false) // Обновляем View
                isPlayerTurn = true
                updateStatusText() // Переход хода к игроку
            }
        }, 1000)
    }
    // --- Конец логики ходов ---


    // --- Проверка конца игры и статус (остаются без изменений) ---
    private fun checkGameOver() {
        if (opponentShipCells <= 0) {
            statusTextView.text = "Вы победили!"
            isGameOver = true
            // TODO: Сохранить результат
        } else if (playerShipsCells <= 0) {
            statusTextView.text = "Компьютер победил!"
            isGameOver = true
            // TODO: Сохранить результат
        }
    }

    private fun updateStatusText() {
        if (isGameOver) return
        statusTextView.text = if (isPlayerTurn) "Ваш ход" else "Ход компьютера..."
    }
    // ---


    // --- УДАЛЕНО: Внутренний класс GridAdapter ---

}