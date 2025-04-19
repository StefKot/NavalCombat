package com.example.navalcombat.activities // Убедитесь, что пакет соответствует вашему проекту

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log // Импортируем Log для отладки
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.gridlayout.widget.GridLayout
import com.example.navalcombat.R // Убедитесь, что пакет соответствует вашему проекту
import com.example.navalcombat.model.ShipToPlace // Импортируем ShipToPlace
import com.example.navalcombat.model.CellState // Импортируем CellState
// import com.example.navalcombat.utils.dpToPx // Если создавали utils пакет, импортируйте оттуда
import java.io.Serializable
import kotlin.random.Random


class GameActivity : AppCompatActivity() {

    // --- UI элементы из activity_game.xml ---
    private lateinit var statusTextView: TextView
    private lateinit var opponentGridView: GridLayout
    private lateinit var playerGridView: GridLayout
    private lateinit var opponentBoardContainer: ConstraintLayout
    private lateinit var playerBoardContainer: ConstraintLayout
    private lateinit var opponentColLabelsLayout: LinearLayout
    private lateinit var opponentRowLabelsLayout: LinearLayout
    private lateinit var playerColLabelsLayout: LinearLayout
    private lateinit var playerRowLabelsLayout: LinearLayout
    // --- Конец UI элементов ---

    // --- Логическая модель данных и состояние игры ---
    private lateinit var opponentCellViews: Array<Array<TextView?>>
    private lateinit var playerCellViews: Array<Array<TextView?>>

    private var playerBoard = createEmptyBoard() // Будет заполнена из Intent
    private var opponentBoard = createEmptyBoard() // Будет заполнена случайно

    private var playerShipsCells = 0 // Подсчитывается из playerBoard
    private var opponentShipCells = 0

    private var isPlayerTurn = true
    private var isGameOver = false

    private val gridSize = 10
    private val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")
    private val shipSizes = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1) // 20 клеток всего

    // --- Конец логической модели и состояния ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("GameActivity", "onCreate: Started.")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_game)

        // --- Находим UI элементы по их ID ---
        statusTextView = findViewById(R.id.textViewGameStatus)
        opponentGridView = findViewById(R.id.opponentGrid)
        playerGridView = findViewById(R.id.playerGrid)
        opponentBoardContainer = findViewById(R.id.opponentBoardContainer)
        playerBoardContainer = findViewById(R.id.playerBoardContainer)
        opponentColLabelsLayout = findViewById(R.id.opponentColLabels)
        opponentRowLabelsLayout = findViewById(R.id.opponentRowLabels)
        playerColLabelsLayout = findViewById(R.id.playerColLabels)
        playerRowLabelsLayout = findViewById(R.id.playerRowLabels)
        // --- Конец findViewById ---

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayoutGame) // Используем LinearLayout как корневой в этом макете
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBarsInsets.top, bottom = systemBarsInsets.bottom)
            insets
        }

        // Инициализация массивов View ячеек (нужны здесь, т.к. createGridCells их заполняет)
        opponentCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        Log.d("GameActivity", "onCreate: CellViews arrays initialized.")


        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize
        opponentGridView.rowCount = gridSize
        opponentGridView.columnCount = gridSize
        Log.d("GameActivity", "onCreate: GridLayouts row/col count set.")

        createLabels() // Создает метки
        Log.d("GameActivity", "onCreate: Labels created.")


        // --- ИЗМЕНЕНО: Получаем расстановку игрока из Intent ---
        val receivedPlayerBoard = intent.getSerializableExtra("playerBoard") as? Array<Array<CellState>>
        if (receivedPlayerBoard != null) {
            playerBoard = receivedPlayerBoard // Устанавливаем доску игрока из Intent
            playerShipsCells = countShipCells(playerBoard) // Подсчет на основе полученной доски
            Log.d("GameActivity", "onCreate: Player board received from Intent. Player ships: $playerShipsCells")
        } else {
            // Fallback: Если доска не получена (не должно происходить)
            Log.e("GameActivity", "onCreate: Player board NOT received from Intent! Using random placement.")
            Toast.makeText(this, "Ошибка получения расстановки. Случайная расстановка.", Toast.LENGTH_LONG).show()
            playerBoard = createEmptyBoard()
            placeShipsRandomly(playerBoard) // Расставляем случайно для игрока
            playerShipsCells = countShipCells(playerBoard)
        }
        // --- Конец получения данных из Intent ---

        // --- Создаем View ячеек для обоих полей ОДИН РАЗ ---
        createGridCells(opponentGridView, opponentBoard, true) // Для противника
        createGridCells(playerGridView, playerBoard, false) // Для игрока
        Log.d("GameActivity", "onCreate: Grid cells created for both boards.")

        setupGame() // Запускаем остальную настройку игры (случайная расстановка компа)
        Log.d("GameActivity", "onCreate: setupGame finished.")


        // Изначально показываем поле противника и обновляем его UI
        showBoard(true) // true = показать поле противника. Это также вызовет updateStatusText
        Log.d("GameActivity", "onCreate: Initial board set to opponent.")

        // updateGridCells() // <-- Убрал этот вызов отсюда. updateGridCells теперь вызывается после showBoard.

        Log.d("GameActivity", "onCreate: Finished.")
    }

    // --- Методы игры ---

    // Настраивает новую игру: сброс логики, расстановка кораблей, создание/обновление View ячеек
    // В GameActivity вызывается ОДИН РАЗ в onCreate.
    private fun setupGame() {
        Log.d("GameActivity", "setupGame: Starting.")
        // playerBoard и playerShipsCells уже установлены в onCreate
        opponentBoard = createEmptyBoard() // Создаем пустую доску для противника
        opponentShipCells = shipSizes.sum() // Общее количество палуб компьютера

        isPlayerTurn = true // Игрок всегда начинает
        isGameOver = false

        // Расставляем корабли только для противника
        placeShipsRandomly(opponentBoard)
        Log.d("GameActivity", "setupGame: Opponent ships placed.")

        // Создание View ячеек больше не здесь, а в onCreate.
        // createGridCells(opponentGridView, opponentBoard, true)
        // createGridCells(playerGridView, playerBoard, false)

        // Обновление UI должно происходить после showBoard
        // updateStatusText() // Этот вызов убран отсюда, вызывается в showBoard
        Log.d("GameActivity", "setupGame: Finished.")
    }

    // Метод для подсчета палуб на доске (используется в onCreate)
    private fun countShipCells(board: Array<Array<CellState>>): Int {
        var count = 0
        for (row in board) {
            for (cell in row) {
                if (cell == CellState.SHIP) {
                    count++
                }
            }
        }
        return count
    }

    // Создает пустую доску gridSize x gridSize, заполненную CellState.EMPTY
    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    // Расставляет корабли на доске в случайных позициях (используется для противника)
    private fun placeShipsRandomly(board: Array<Array<CellState>>) {
        val random = Random(System.currentTimeMillis())
        val shipsToRandomlyPlace = mutableListOf(
            ShipToPlace(4), ShipToPlace(3), ShipToPlace(3), ShipToPlace(2), ShipToPlace(2), ShipToPlace(2), ShipToPlace(1), ShipToPlace(1), ShipToPlace(1), ShipToPlace(1)
        )

        for (ship in shipsToRandomlyPlace) {
            var placed = false
            var attempts = 0
            val maxAttempts = 1000

            while (!placed && attempts < maxAttempts) {
                val row = random.nextInt(gridSize)
                val col = random.nextInt(gridSize)
                val isHorizontal = random.nextBoolean()
                ship.isHorizontal = isHorizontal

                if (canPlaceShip(board, row, col, ship.size, ship.isHorizontal)) {
                    placeShip(board, row, col, ship.size, ship.isHorizontal)
                    placed = true
                }
                attempts++
            }
            if (!placed) {
                Log.e("GameActivity", "Failed to place ship ${ship.size} randomly for board.")
            }
        }
    }

    // Проверяет, можно ли разместить корабль (аналогичен методу из SetupActivity)
    private fun canPlaceShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean): Boolean {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            if (currentRow < 0 || currentRow >= gridSize || currentCol < 0 || currentCol >= gridSize) return false
            for (r in (currentRow - 1)..(currentRow + 1)) {
                for (c in (currentCol - 1)..(currentCol + 1)) {
                    if (r >= 0 && r < gridSize && c >= 0 && c < gridSize) {
                        if (board[r][c] == CellState.SHIP) return false
                    }
                }
            }
        }
        return true
    }

    // Размещает корабль на логической доске (аналогичен методу из SetupActivity)
    private fun placeShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean) {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            if (currentRow >= 0 && currentRow < gridSize && currentCol >= 0 && currentCol < gridSize) {
                board[currentRow][currentCol] = CellState.SHIP
            }
        }
    }


    // --- Работа с UI ---

    // Создает View ячеек для GridLayout и сохраняет их ссылки в массивах.
    // Вызывается ОДИН РАЗ в onCreate для каждого поля.
    private fun createGridCells(grid: GridLayout, board: Array<Array<CellState>>, isOpponent: Boolean) {
        Log.d("GameActivity", "createGridCells: Starting for isOpponent=$isOpponent.")
        grid.removeAllViews()
        val cellReferences = if (isOpponent) opponentCellViews else playerCellViews

        // --- ОТЛАДКА: Проверяем playerCellViews/opponentCellViews перед заполнением ---
        Log.d("GameActivity", "createGridCells: Before filling, cellReferences.size = ${cellReferences.size}")
        if (gridSize > 0 && cellReferences.isNotEmpty() && cellReferences[0] != null) {
            Log.d("GameActivity", "createGridCells: Before filling, cellReferences[0].size = ${cellReferences[0].size}")
        } else {
            // Этого не должно случиться после инициализации в onCreate
            Log.e("GameActivity", "createGridCells: FATAL ERROR: cellReferences array seems empty or null when called.")
            // Если сюда попадаем, значит, инициализация в onCreate не сработала. Крашнем явно.
            throw IllegalStateException("Cell references array is empty or null when createGridCells is called")
        }
        // ---

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cellView = LayoutInflater.from(this).inflate(R.layout.grid_cell, grid, false) as TextView

                val params = GridLayout.LayoutParams()
                params.width = 0
                params.height = 0
                params.rowSpec = GridLayout.spec(row, 1, 1f)
                params.columnSpec = GridLayout.spec(col, 1, 1f)
                cellView.layoutParams = params

                // --- ВАЖНО: Заполняем УЖЕ СУЩЕСТВУЮЩИЙ массив ссылок ---
                // Используем безопасную проверку для записи
                if (row < cellReferences.size && col < (cellReferences.getOrNull(row)?.size ?: 0)) {
                    cellReferences[row][col] = cellView // <-- Сохраняем ссылку
                } else {
                    Log.e("GameActivity", "FATAL ERROR: Index outside bounds of cellReferences during creation: isOpponent=$isOpponent, row=$row, col=$col. Array size = ${cellReferences.size}, inner size = ${cellReferences.getOrNull(row)?.size}")
                    throw IndexOutOfBoundsException("Cell references array size mismatch during creation")
                }
                // --- Конец заполнения массива ---

                // updateCellView(cellView, board[row][col], isOpponent) // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ!
                //     updateCellView вызывается ИЗ updateGridCells

                // Обработка клика (только для поля противника)
                if (isOpponent) {
                    cellView.setOnClickListener {
                        if (!isGameOver && isPlayerTurn) {
                            handlePlayerShot(row, col)
                        }
                    }
                } else {
                    cellView.isClickable = false // Поле игрока не кликабельно
                }

                grid.addView(cellView) // Добавляем View в GridLayout
            }
        }
        // updateGridCells() // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ!
        Log.d("GameActivity", "createGridCells: Finished filling grid with views for isOpponent=$isOpponent.")
    }


    // Обновляет внешний вид ВСЕХ ячеек на ПОЛЕ, которое сейчас ВИДИМО.
    // Вызывается после showBoard() и после каждого выстрела.
    private fun updateGridCells() {
        Log.d("GameActivity", "updateGridCells: Starting.")
        // Определяем, какое поле сейчас видимо
        val isOpponentBoardVisible = opponentBoardContainer.visibility == View.VISIBLE
        Log.d("GameActivity", "updateGridCells: isOpponentBoardVisible = $isOpponentBoardVisible")

        // Выбираем соответствующую логическую доску, массив View ячеек и флаг isOpponent для updateCellView
        val currentBoard = if (isOpponentBoardVisible) opponentBoard else playerBoard
        val cellViews = if (isOpponentBoardVisible) opponentCellViews else playerCellViews
        val isOpponent = isOpponentBoardVisible // Флаг для updateCellView

        // Проверяем, что массив проинициализирован и не пуст перед попыткой доступа
        if (cellViews.isEmpty() || gridSize == 0 || cellViews[0] == null) {
            Log.e("GameActivity", "updateGridCells: cellViews is empty, gridSize is 0, or inner array is null. Skipping UI update.")
            return // Выходим, если массив пуст или некорректен
        }
        if (cellViews.size != gridSize || cellViews[0]!!.size != gridSize) {
            Log.e("GameActivity", "updateGridCells: cellViews size mismatch! Expected ${gridSize}x${gridSize}, got ${cellViews.size}x${cellViews[0]?.size}. Skipping UI update.")
            return // Выходим, если размер массива некорректен
        }
        // ---

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Используем безопасный доступ getOrNull
                cellViews.getOrNull(row)?.getOrNull(col)?.let { cellView ->
                    val state = currentBoard[row][col] // Состояние из логической доски
                    // --- ВАЖНО: Вызываем updateCellView с правильным флагом isOpponent ---
                    updateCellView(cellView, state, isOpponent) // <-- ВЫЗЫВАЕМ updateCellView ЗДЕСЬ
                } ?: run {
                    Log.e("GameActivity", "View ячейки отсутствует в cellViews по адресу: isOpponentVisible=$isOpponentBoardVisible, row=$row, col=$col. Skipping updateCellView.")
                }
            }
        }
        Log.d("GameActivity", "updateGridCells: Finished updating UI for visible board.")
    }

    // Обновляет внешний вид одной ячейки (TextView) в зависимости от ее логического состояния
    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return

        cellView.text = ""
        cellView.setTextColor(ContextCompat.getColor(this, android.R.color.transparent))

        when (state) {
            CellState.EMPTY -> cellView.setBackgroundResource(R.drawable.cell_water)
            CellState.SHIP -> {
                // Если это поле противника (isOpponent=true), корабли скрыты (показываем воду)
                // Если это поле игрока (isOpponent=false), корабли видны (показываем серый фон)
                if (isOpponent) {
                    cellView.setBackgroundResource(R.drawable.cell_water) // Скрыть корабль
                } else {
                    cellView.setBackgroundResource(R.drawable.cell_ship_player) // Показать корабль
                }
            }
            CellState.HIT -> {
                cellView.setBackgroundResource(R.drawable.cell_hit)
                cellView.text = "X"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            CellState.MISS -> {
                cellView.setBackgroundResource(R.drawable.cell_water) // Промах всегда выглядит как точка на воде
                cellView.text = "•"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            CellState.SUNK -> {
                cellView.setBackgroundResource(R.drawable.cell_sunk)
                cellView.text = "X"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }
        cellView.gravity = Gravity.CENTER
    }

    // Создает и добавляет TextView для меток координат (А-К и 1-10) вокруг сеток
    // Вызывается ОДИН РАЗ в onCreate.
    private fun createLabels() {
        Log.d("GameActivity", "createLabels: Starting.")
        val labelColor = ContextCompat.getColor(this, R.color.purple_700)
        val labelTextSize = 14f

        // Очищаем Layout'ы меток перед добавлением новых (на всякий случай, при пересоздании Activity)
        opponentColLabelsLayout.removeAllViews()
        opponentRowLabelsLayout.removeAllViews()
        playerColLabelsLayout.removeAllViews()
        playerRowLabelsLayout.removeAllViews()

        val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

        // Метки столбцов (А-К)
        for (i in 0 until gridSize) {
            // --- ИСПРАВЛЕНО: Создаем ДВА РАЗНЫХ TextView для меток столбцов ---
            val opponentColLabel = TextView(this).apply { // TextView для поля противника
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.CENTER
                }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerColLabel = TextView(this).apply { // TextView для поля игрока
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.CENTER
                }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            // --- Конец исправлений для меток столбцов ---

            opponentColLabelsLayout.addView(opponentColLabel) // Добавляем в Layout меток столбцов противника
            playerColLabelsLayout.addView(playerColLabel) // Добавляем в Layout меток столбцов игрока
        }

        // Метки строк (1-10)
        for (i in 0 until gridSize) {
            // --- ИСПРАВЛЕНО: Создаем ДВА РАЗНЫХ TextView для меток строк ---
            val opponentRowLabel = TextView(this).apply { // TextView для поля противника
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerRowLabel = TextView(this).apply { // TextView для поля игрока
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            // --- Конец исправлений для меток строк ---

            opponentRowLabelsLayout.addView(opponentRowLabel) // Добавляем в Layout меток строк противника
            playerRowLabelsLayout.addView(playerRowLabel) // Добавляем в Layout меток строк игрока
        }
        Log.d("GameActivity", "createLabels: Finished.")
    }

    // Переключает видимость между полем противника и полем игрока
    // showOpponent: true - показать поле противника, false - показать поле игрока
    private fun showBoard(showOpponent: Boolean) {
        Log.d("GameActivity", "showBoard: Switching board to " + if(showOpponent) "Opponent" else "Player")
        if (showOpponent) {
            opponentBoardContainer.visibility = View.VISIBLE
            playerBoardContainer.visibility = View.GONE
        } else {
            opponentBoardContainer.visibility = View.GONE
            playerBoardContainer.visibility = View.VISIBLE
        }
        // Обновляем статус текст и UI поля после переключения
        updateStatusText()
        updateGridCells() // <-- ВАЖНО: Обновляем UI после переключения поля
        Log.d("GameActivity", "showBoard: Switching finished, updateGridCells called.")
    }


    // Обрабатывает выстрел игрока по клетке (row, col) на поле противника
    private fun handlePlayerShot(row: Int, col: Int) {
        Log.d("GameActivity", "handlePlayerShot: Player shot at $row,$col. isPlayerTurn=$isPlayerTurn")
        if (!isPlayerTurn || isGameOver) return

        val cellState = opponentBoard[row][col]
        val currentCellView = opponentCellViews[row][col]

        if (cellState == CellState.HIT || cellState == CellState.MISS) {
            statusTextView.text = "Сюда уже стреляли, выберите другую клетку!"
            Log.d("GameActivity", "handlePlayerShot: Already shot there.")
            return
        }

        if (cellState == CellState.SHIP) {
            Log.d("GameActivity", "handlePlayerShot: Hit!")
            opponentBoard[row][col] = CellState.HIT
            opponentShipCells--
            statusTextView.text = "Попадание!"
            // Обновляем только попавшую клетку на поле противника
            updateCellView(currentCellView, CellState.HIT, true) // isOpponent=true

            checkGameOver()
            if (!isGameOver) {
                // Игрок ходит снова, остаемся на поле противника. UI уже обновлен.
                Log.d("GameActivity", "handlePlayerShot: Player hit, turn continues.")
                // updateGridCells() // Нет необходимости обновлять всю сетку, только одну ячейку поменяли
            }

        } else { // Промах
            Log.d("GameActivity", "handlePlayerShot: Miss.")
            opponentBoard[row][col] = CellState.MISS
            statusTextView.text = "Промах!"
            // Обновляем только промахнувшуюся клетку на поле противника
            updateCellView(currentCellView, CellState.MISS, true) // isOpponent=true

            isPlayerTurn = false // Переход хода к компьютеру

            // Переключаем на поле игрока и запускаем ход компьютера с задержкой
            Handler(Looper.getMainLooper()).postDelayed({
                showBoard(false) // <-- Переключаем на поле игрока (это вызовет updateGridCells)
                computerTurn() // Компьютер делает свой ход
            }, 1000) // Задержка перед переключением и ходом компьютера
        }
    }

    // Реализует ход компьютера
    private fun computerTurn() {
        Log.d("GameActivity", "computerTurn: Starting. isPlayerTurn=$isPlayerTurn")
        if (isGameOver || isPlayerTurn) return

        statusTextView.text = "Ход компьютера..." // Этот статус уже должен быть установлен в showBoard(false)

        var row: Int
        var col: Int
        var isValidShot: Boolean
        val random = Random(System.currentTimeMillis())

        // Простой случайный выбор незатронутой клетки на поле игрока
        do {
            row = random.nextInt(gridSize)
            col = random.nextInt(gridSize)
            val state = playerBoard[row][col]
            isValidShot = state != CellState.HIT && state != CellState.MISS
        } while (!isValidShot)
        Log.d("GameActivity", "computerTurn: Shot at $row,$col.")


        // Задержка для имитации "раздумий" и отображения
        Handler(Looper.getMainLooper()).postDelayed({
            // Выполняем действия после задержки
            val targetState = playerBoard[row][col] // Состояние клетки игрока
            val targetCellView = playerCellViews[row][col] // View клетки игрока

            if (targetState == CellState.SHIP) { // Попадание компьютера!
                Log.d("GameActivity", "computerTurn: Hit!")
                playerBoard[row][col] = CellState.HIT // Обновляем логическую доску игрока
                playerShipsCells-- // Уменьшаем счетчик палуб игрока
                statusTextView.text = "Компьютер попал по вашему кораблю!"
                // Обновляем только попавшую клетку на поле игрока
                updateCellView(targetCellView, CellState.HIT, false) // isOpponent=false

                // TODO: Добавить логику проверки, потоплен ли корабль игрока

                checkGameOver() // Проверяем конец игры

                if (!isGameOver) {
                    // Компьютер ходит снова (т.к. попал). Поле игрока остается видимым.
                    Log.d("GameActivity", "computerTurn: Computer hit, turn continues.")
                    // updateGridCells() // Нет необходимости обновлять всю сетку, только одну ячейку
                    Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000) // Задержка перед следующим ходом компьютера
                }
            } else { // Промах компьютера!
                Log.d("GameActivity", "computerTurn: Miss.")
                playerBoard[row][col] = CellState.MISS // Обновляем логическую доску игрока
                statusTextView.text = "Компьютер промахнулся!"
                // Обновляем только промахнувшуюся клетку на поле игрока
                updateCellView(targetCellView, CellState.MISS, false) // isOpponent=false

                isPlayerTurn = true // Переход хода к игроку

                // Переключаем на поле противника для хода игрока с задержкой
                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(true) // <-- Переключаем на поле противника (это вызовет updateGridCells)
                }, 1000) // Задержка перед переключением обратно
            }
            Log.d("GameActivity", "computerTurn: Delayed action finished.")
        }, 1000) // Задержка перед выстрелом компьютера
        Log.d("GameActivity", "computerTurn: Finished, delayed shot scheduled.")
    }


    // Проверяет условия завершения игры (у кого закончились корабли)
    private fun checkGameOver() {
        Log.d("GameActivity", "checkGameOver: Player ships left: $playerShipsCells, Opponent ships left: $opponentShipCells")
        if (opponentShipCells <= 0) {
            statusTextView.text = "Поздравляем! Вы победили!"
            isGameOver = true
            Log.d("GameActivity", "checkGameOver: Player WINS!")
            // TODO: Сохранить результат игры в базу данных (победа игрока)
            // TODO: Возможно, показать диалог о победе и предложить новую игру/вернуться в меню
        } else if (playerShipsCells <= 0) {
            statusTextView.text = "К сожалению, вы проиграли. Компьютер победил!"
            isGameOver = true
            Log.d("GameActivity", "checkGameOver: Computer WINS!")
            // TODO: Сохранить результат игры в базу данных (победа компьютера)
            // TODO: Возможно, показать диалог о поражении и предложить новую игру/вернуться в меню
        }
        // Если игра окончена, поля остаются на том, на каком они были в момент окончания.
        // updateStatusText() вызывается в showBoard.
    }

    // Обновляет текст в TextView статуса игры в зависимости от текущего хода
    // (Метки полей теперь управляются видимостью контейнеров)
    private fun updateStatusText() {
        if (isGameOver) return // Не обновляем, если игра окончена
        // Текст статуса просто показывает, чей ход. Метка поля видна благодаря showBoard().
        statusTextView.text = if (isPlayerTurn) "Ваш ход" else "Ход компьютера..."
        Log.d("GameActivity", "updateStatusText: Status set to '${statusTextView.text}'")
    }

    // TODO: Добавить метод для сохранения результата игры в Room Database
    // private fun saveGameResult(winnerIsPlayer: Boolean) { ... }
}

// --- Extension функция для конвертации dp в px ---
// Эта функция ДОЛЖНА БЫТЬ ВНЕ КЛАССА GameActivity.
// Если у вас есть отдельный файл утилит, поместите ее туда и импортируйте.
/*
import android.content.res.Resources

fun Int.dpToPx(resources: Resources): Int {
    return (this * resources.displayMetrics.density).toInt()
}
*/
// Убедитесь, что импорт Resources в начале файла есть, если функция тут.
// Если вы используете версию из utils пакета, убедитесь в правильном импорте:
// import com.example.navalcombat.utils.dpToPx // Пример импорта из utils
// В данном коде я предполагаю, что вы используете импорт из utils
// Если вы используете раскомментированную версию, убедитесь, что resources доступен.
// В этом случае удобнее сделать extension функцию для Context или View.
// Например:
/*
import android.view.View // Импорт View
fun View.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
// Тогда в updateShipsListUI вызывать так: 16.dpToPx()
*/
// Но оставим текущий вариант с передачей resources, убедитесь, что импорт utils.dpToPx правильный.

// Если вы не используете utils пакет, раскомментируйте эту версию:
/*
import android.content.res.Resources
fun Int.dpToPx(resources: Resources): Int {
    return (this * resources.displayMetrics.density).toInt()
}
*/
// И убедитесь, что импорт Resources в начале файла есть.