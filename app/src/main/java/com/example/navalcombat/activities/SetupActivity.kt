package com.example.navalcombat.activities // Убедитесь, что пакет соответствует вашему проекту

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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


fun Int.dpToPx(resources: Resources): Int {
    return (this * resources.displayMetrics.density).toInt()
}


class SetupActivity : AppCompatActivity() {

    // --- UI элементы из activity_setup.xml ---
    private lateinit var playerGridView: GridLayout
    private lateinit var playerColLabelsLayout: LinearLayout
    private lateinit var playerRowLabelsLayout: LinearLayout
    private lateinit var buttonStartBattle: Button
    private lateinit var buttonRotateShip: Button
    private lateinit var buttonRandomPlace: Button
    private lateinit var buttonClearBoard: Button
    private lateinit var layoutShipsList: LinearLayout
    private lateinit var textViewSetupTitle: TextView
    private lateinit var textViewGameStatus: TextView
    private lateinit var textViewShipsToPlace: TextView
    // --- Конец UI элементов ---

    // --- Логическая модель данных и состояние расстановки ---
    private lateinit var playerCellViews: Array<Array<TextView?>> // <-- Инициализируется ТОЛЬКО в onCreate

    private var playerBoard = createEmptyBoard()

    private var shipsToPlace = mutableListOf<ShipToPlace>()

    private var selectedShip: ShipToPlace? = null

    private val gridSize = 10 // <-- Значение должно быть 10

    private val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

    // --- Конец логической модели и состояния ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- ОТЛАДКА: Проверяем gridSize в начале onCreate ---
        Log.d("SetupActivity", "onCreate: gridSize = $gridSize") // Добавляем лог
        // ---

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_setup)

        // --- Находим UI элементы по их ID ---
        playerGridView = findViewById(R.id.playerGridSetup)
        playerColLabelsLayout = findViewById(R.id.playerColLabelsSetup)
        playerRowLabelsLayout = findViewById(R.id.playerRowLabelsSetup)
        buttonStartBattle = findViewById(R.id.buttonStartBattle)
        buttonRotateShip = findViewById(R.id.buttonRotateShip)
        buttonRandomPlace = findViewById(R.id.buttonRandomPlace)
        buttonClearBoard = findViewById(R.id.buttonClearBoard)
        layoutShipsList = findViewById(R.id.layoutShipsList)
        textViewSetupTitle = findViewById(R.id.textViewSetupTitle)
        textViewGameStatus = findViewById(R.id.textViewGameStatus)
        textViewShipsToPlace = findViewById(R.id.textViewShipsToPlace)
        // --- Конец findViewById ---

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayoutSetup)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBarsInsets.top,
                bottom = systemBarsInsets.bottom
            )
            insets
        }

        // --- ВАЖНО: Инициализация массива View ячеек ТОЛЬКО ЗДЕСЬ ---
        // --- ОТЛАДКА: Проверяем gridSize перед инициализацией ---
        Log.d("SetupActivity", "Before playerCellViews init: gridSize = $gridSize") // Лог перед инициализацией
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) } // <-- Правильное место
        // --- ОТЛАДКА: Проверяем размер массива после инициализации ---
        Log.d("SetupActivity", "After playerCellViews init: playerCellViews.size = ${playerCellViews.size}") // Лог после инициализации
        if (gridSize > 0) { // Дополнительная проверка, чтобы избежать краша, если gridSize все же 0
            Log.d("SetupActivity", "After playerCellViews init: playerCellViews[0].size = ${playerCellViews[0]?.size}") // Лог для внутреннего массива
        } else {
            Log.d("SetupActivity", "After playerCellViews init: gridSize is 0, inner array size check skipped.")
        }
        // --- Конец инициализации и отладки ---


        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize

        createLabels() // Создает метки

        // --- ВАЖНО: Перенесен вызов createGridCells ПОСЛЕ setupNewPlacement ---
        setupNewPlacement() // Начинает процесс расстановки (сбрасывает доску, список кораблей, вызывает updateGridCells, updateShipsListUI)

        // --- ОТЛАДКА: Проверяем playerCellViews перед createGridCells ---
        // На этом этапе updateGridCells УЖЕ БЫЛ ВЫЗВАН ИЗ setupNewPlacement/clearBoard.
        // Если updateGridCells падал, то до createGridCells мы можем не дойти.
        // Добавим проверку, чтобы убедиться, что playerCellViews еще НЕ ЗАПОЛНЕН после updateGridCells.
        Log.d("SetupActivity", "Before createGridCells (2nd spot): playerCellViews.size = ${playerCellViews.size}")
        if (gridSize > 0) {
            Log.d("SetupActivity", "Before createGridCells (2nd spot): playerCellViews[0]?.size = ${playerCellViews[0]?.size}")
        } else {
            Log.d("SetupActivity", "Before createGridCells (2nd spot): gridSize is 0, inner array check skipped.")
        }
        // ---

        createGridCells() // <-- ВТОРОЙ (и теперь правильный) вызов createGridCells.
        //     Этот вызов СОЗДАЕТ View ячеек и СОХРАНЯЕТ ССЫЛКИ в playerCellViews.
        //     Он также вызывает updateCellView для каждой ячейки (на основе пустой доски playerBoard),
        //     которая была сброшена в clearBoard().

        // --- updateGridCells вызывается из clearBoard() / setupRandomly()
        //    createGridCells вызывается ОДИН РАЗ в onCreate, чтобы создать View и сохранить их ссылки.
        //    updateGridCells вызывается после каждого изменения доски (clear, place, random)
        //    чтобы ОБНОВИТЬ внешний вид View, ссылки на которые уже есть.
        // ---

        // showBoard(true) - этот вызов убран из SetupActivity, т.к. поля не переключаются.
    }

    // --- Методы для логики расстановки ---

    private fun setupNewPlacement() {
        clearBoard() // clearBoard вызывает updateGridCells и updateShipsListUI
        // updateGridCells на этом этапе будет вызван ПЕРЕД createGridCells.
        // Он попытается обновить View, но playerCellViews еще пуст.
        // Это может вызвать ошибку, если updateGridCells не обработает пустой массив безопасно.
        // Добавим проверку в updateGridCells.
        textViewGameStatus.text = "Выберите корабль снизу и кликните на поле"
    }

    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    private fun clearBoard() {
        playerBoard = createEmptyBoard()
        shipsToPlace.clear()
        shipsToPlace.addAll(
            listOf(
                ShipToPlace(4),
                ShipToPlace(3), ShipToPlace(3),
                ShipToPlace(2), ShipToPlace(2), ShipToPlace(2),
                ShipToPlace(1), ShipToPlace(1), ShipToPlace(1), ShipToPlace(1)
            )
        )
        selectedShip = null

        // --- ОТЛАДКА: Проверяем playerCellViews перед updateGridCells ---
        Log.d("SetupActivity", "clearBoard: Before updateGridCells, playerCellViews.size = ${playerCellViews.size}")
        if (gridSize > 0 && playerCellViews.isNotEmpty() && playerCellViews[0] != null) {
            Log.d("SetupActivity", "clearBoard: Before updateGridCells, playerCellViews[0].size = ${playerCellViews[0].size}")
        } else {
            Log.d("SetupActivity", "clearBoard: Before updateGridCells, playerCellViews array seems empty or null.")
        }
        // ---

        updateGridCells() // <-- Вызываем обновление UI
        updateShipsListUI() // Обновляем UI списка кораблей
        buttonStartBattle.isEnabled = false
        textViewGameStatus.text = "Выберите корабль снизу и кликните на поле"
    }

    private fun setupRandomly() {
        clearBoard() // Начинаем с чистого поля (сбрасывает доску и список кораблей, вызывает updateGridCells)

        val random = Random(System.currentTimeMillis())

        val allShipsForRandom = mutableListOf(
            ShipToPlace(4),
            ShipToPlace(3), ShipToPlace(3),
            ShipToPlace(2), ShipToPlace(2), ShipToPlace(2),
            ShipToPlace(1), ShipToPlace(1), ShipToPlace(1), ShipToPlace(1)
        )

        for (ship in allShipsForRandom) {
            var placed = false
            var attempts = 0
            val maxAttempts = 1000

            while (!placed && attempts < maxAttempts) {
                val row = random.nextInt(gridSize)
                val col = random.nextInt(gridSize)
                val isHorizontal = random.nextBoolean()
                ship.isHorizontal = isHorizontal

                if (canPlaceShip(playerBoard, row, col, ship.size, ship.isHorizontal)) {
                    placeShip(playerBoard, row, col, ship.size, ship.isHorizontal)
                    placed = true
                }
                attempts++
            }
            if (!placed) {
                Toast.makeText(this, "Внимание: Не удалось разместить все корабли случайно!", Toast.LENGTH_LONG).show()
            }
        }

        shipsToPlace.clear()
        selectedShip = null

        updateGridCells() // <-- Вызываем обновление UI
        updateShipsListUI()
        buttonStartBattle.isEnabled = true
        textViewGameStatus.text = "Корабли расставлены случайно! Готов к бою!"
    }

    private fun canPlaceShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean): Boolean {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col

            if (currentRow < 0 || currentRow >= gridSize || currentCol < 0 || currentCol >= gridSize) {
                return false
            }

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

    private fun placeShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean) {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            if (currentRow >= 0 && currentRow < gridSize && currentCol >= 0 && currentCol < gridSize) {
                board[currentRow][currentCol] = CellState.SHIP
            }
        }
    }

    private fun tryPlaceSelectedShip(row: Int, col: Int) {
        selectedShip?.let { ship ->
            if (canPlaceShip(playerBoard, row, col, ship.size, ship.isHorizontal)) {
                placeShip(playerBoard, row, col, ship.size, ship.isHorizontal)

                val removed = shipsToPlace.removeIf { it.size == ship.size }

                if (!removed) {
                    Toast.makeText(this, "Ошибка: Не удалось найти корабль ${ship.size} в списке для удаления!", Toast.LENGTH_SHORT).show()
                }

                selectedShip = null

                updateGridCells() // <-- Вызываем обновление UI
                updateShipsListUI()

                if (shipsToPlace.isEmpty()) {
                    buttonStartBattle.isEnabled = true
                    textViewGameStatus.text = "Все корабли расставлены! Готов к бою!"
                    Toast.makeText(this, "Все корабли расставлены! Нажмите 'Начать Бой'.", Toast.LENGTH_LONG).show()
                } else {
                    textViewGameStatus.text = "Поставлен ${ship.size}-палубный корабль. Осталось: ${shipsToPlace.size}"
                }

            } else {
                textViewGameStatus.text = "Нельзя поставить корабль сюда!"
                Toast.makeText(this, "Нельзя поставить корабль сюда!", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            textViewGameStatus.text = "Выберите корабль для расстановки"
            Toast.makeText(this, "Выберите корабль для расстановки", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Работа с UI ---

    // Создает View ячеек для GridLayout и сохраняет их ссылки в playerCellViews.
    // Вызывается ОДИН РАЗ в onCreate.
    private fun createGridCells() {
        playerGridView.removeAllViews()
        // --- ИСПРАВЛЕНО: Удалена повторная инициализация playerCellViews ---
        // playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) } // <-- ЭТА СТРОКА УДАЛЕНА ИЗ ЭТОГО МЕТОДА
        // --- ОТЛАДКА: Проверяем playerCellViews перед заполнением ---
        Log.d("SetupActivity", "createGridCells: Before filling, playerCellViews.size = ${playerCellViews.size}")
        if (gridSize > 0 && playerCellViews.isNotEmpty() && playerCellViews[0] != null) {
            Log.d("SetupActivity", "createGridCells: Before filling, playerCellViews[0].size = ${playerCellViews[0].size}")
        } else {
            Log.d("SetupActivity", "createGridCells: Before filling, playerCellViews array seems empty or null.")
        }
        // ---

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cellView = LayoutInflater.from(this).inflate(R.layout.grid_cell, playerGridView, false) as TextView

                val params = GridLayout.LayoutParams()
                params.width = 0
                params.height = 0
                params.rowSpec = GridLayout.spec(row, 1, 1f)
                params.columnSpec = GridLayout.spec(col, 1, 1f)
                cellView.layoutParams = params

                // --- ВАЖНО: Теперь мы заполняем УЖЕ СУЩЕСТВУЮЩИЙ массив playerCellViews ---
                // Проверка на границы массива (она должна быть излишней, если циклы и gridSize правильные)
                if (row < playerCellViews.size && col < (playerCellViews.getOrNull(row)?.size ?: 0)) {
                    playerCellViews[row][col] = cellView // <-- Заполняем массив ссылкой
                } else {
                    Log.e("SetupActivity", "Индекс вне границ playerCellViews при создании ячеек: row=$row, col=$col. Array size = ${playerCellViews.size}, inner size = ${playerCellViews.getOrNull(row)?.size}")
                    // Если сюда попадаем, это серьезная ошибка логики размеров массивов или циклов
                    // throw IndexOutOfBoundsException("playerCellViews array size mismatch during creation") // Можно крашнуть явно
                }
                // --- Конец заполнения массива ---

                // updateCellView(cellView, playerBoard[row][col], false) // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ!
                //     updateCellView должен быть вызван ИЗ updateGridCells

                cellView.setOnClickListener {
                    tryPlaceSelectedShip(row, col)
                }

                playerGridView.addView(cellView)
            }
        }
        //updateGridCells() // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ! createGridCells только создает View.
        //    updateGridCells вызывается ОТДЕЛЬНО после изменения playerBoard
    }

    // Обновляет внешний вид ВСЕХ ячеек на поле игрока на основе текущего состояния playerBoard
    // Вызывается после каждого изменения playerBoard (clear, place, random)
    private fun updateGridCells() {
        // --- ОТЛАДКА: Проверяем playerCellViews в updateGridCells ---
        Log.d("SetupActivity", "updateGridCells: playerCellViews.size = ${playerCellViews.size}")
        if (gridSize > 0 && playerCellViews.isNotEmpty() && playerCellViews[0] != null) {
            Log.d("SetupActivity", "updateGridCells: playerCellViews[0].size = ${playerCellViews[0].size}")
        } else {
            Log.d("SetupActivity", "updateGridCells: playerCellViews array seems empty or null. Cannot update UI.")
            // Добавим проверку, чтобы избежать краша, если массив пуст
            if (playerCellViews.isEmpty()) {
                Log.e("SetupActivity", "updateGridCells: playerCellViews is empty. Skipping UI update.")
                return // Выходим, если массив пуст
            }
        }
        // ---

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // --- ВАЖНО: Теперь мы используем УЖЕ СУЩЕСТВУЮЩИЙ массив playerCellViews ---
                // Проверяем, что View существует (т.к. создается в createGridCells)
                // Добавлена дополнительная безопасная проверка доступа
                playerCellViews.getOrNull(row)?.getOrNull(col)?.let { cellView -> // <-- Используем элемент массива безопасно
                    val state = playerBoard[row][col]
                    updateCellView(cellView, state, false) // <-- ВЫЗЫВАЕМ updateCellView ЗДЕСЬ
                } ?: run {
                    Log.e("SetupActivity", "View ячейки отсутствует в playerCellViews по адресу: row=$row, col=$col. Skipping updateCellView.")
                }
            }
        }
    }

    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return

        cellView.text = ""
        cellView.setTextColor(ContextCompat.getColor(this, android.R.color.transparent))

        when (state) {
            CellState.EMPTY -> cellView.setBackgroundResource(R.drawable.cell_water)
            CellState.SHIP -> {
                cellView.setBackgroundResource(R.drawable.cell_ship_player)
            }
            CellState.HIT -> {
                cellView.setBackgroundResource(R.drawable.cell_hit)
                cellView.text = "X"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            CellState.MISS -> {
                cellView.setBackgroundResource(R.drawable.cell_water)
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

    private fun createLabels() {
        val labelColor = ContextCompat.getColor(this, R.color.purple_700)
        val labelTextSize = 14f

        playerColLabelsLayout.removeAllViews()
        playerRowLabelsLayout.removeAllViews()

        val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

        for (i in 0 until gridSize) {
            val colLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.CENTER
                }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            playerColLabelsLayout.addView(colLabel)
        }

        for (i in 0 until gridSize) {
            val rowLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            playerRowLabelsLayout.addView(rowLabel)
        }
    }

    private fun updateShipsListUI() {
        layoutShipsList.removeAllViews()

        val shipsBySize = shipsToPlace.groupBy { it.size }.toSortedMap(reverseOrder())

        if (shipsToPlace.isEmpty()) {
            val allPlacedTextView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                text = "Все корабли расставлены!"
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            }
            layoutShipsList.addView(allPlacedTextView)
        } else {
            for ((size, ships) in shipsBySize) {
                val shipCount = ships.size
                val shipText = "${size}x$shipCount"

                val shipTextView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = 16.dpToPx(resources)
                    }
                    text = shipText
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD

                    setTextColor(
                        if (selectedShip?.size == size) {
                            ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                        } else {
                            Color.BLACK
                        }
                    )

                    setOnClickListener {
                        val shipToSelect = shipsToPlace.find { it.size == size }
                        if (shipToSelect != null) {
                            selectedShip = shipToSelect

                            updateShipsListUI()

                            Toast.makeText(this@SetupActivity, "Выбран корабль размером ${size}", Toast.LENGTH_SHORT).show()

                        } else {
                            Toast.makeText(this@SetupActivity, "Ошибка: Не найден корабль для выбора!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                layoutShipsList.addView(shipTextView)
            }
        }
        buttonStartBattle.isEnabled = shipsToPlace.isEmpty()
    }


    // --- Запуск боя ---

    private fun startBattle() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("playerBoard", playerBoard as Serializable)
        startActivity(intent)
        finish()
    }
}