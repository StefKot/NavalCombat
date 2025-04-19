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
    private lateinit var textViewSelectedShipInfo: TextView // <-- НОВЫЙ UI элемент
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

        Log.d("SetupActivity", "onCreate: Started")

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
        textViewSelectedShipInfo = findViewById(R.id.textViewSelectedShipInfo) // <-- Находим НОВЫЙ UI элемент
        Log.d("SetupActivity", "onCreate: Buttons found: Rotate=${buttonRotateShip!=null}, Random=${buttonRandomPlace!=null}, Clear=${buttonClearBoard!=null}")
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
        Log.d("SetupActivity", "onCreate: Initializing playerCellViews array with size $gridSize")
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) } // <-- Правильное место
        Log.d("SetupActivity", "onCreate: playerCellViews array initialized.")
        // --- Конец инициализации ---


        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize

        createLabels() // Создает метки
        Log.d("SetupActivity", "onCreate: Labels created.")

        createGridCells() // <-- Вызывается здесь ОДИН РАЗ для создания View ячеек
        Log.d("SetupActivity", "onCreate: Grid cells created.")

        setupNewPlacement() // <-- Вызывается здесь для начала процесса расстановки (сбрасывает логику и вызывает updateUI)
        Log.d("SetupActivity", "onCreate: Setup new placement initiated.")


        // --- Устанавливаем слушатели для кнопок ---
        buttonRotateShip.setOnClickListener {
            Log.d("SetupActivity", "Rotate button clicked")
            selectedShip?.let { ship ->
                ship.isHorizontal = !ship.isHorizontal // Меняем ориентацию
                // Обновляем индикатор выбранного корабля
                updateSelectedShipInfoUI()
                Toast.makeText(this, "Выбранный корабль (${ship.size}) повернут.", Toast.LENGTH_SHORT).show()
                // TODO: При наведении на поле, показать повернутое превью
            } ?: run {
                Toast.makeText(this, "Сначала выберите корабль для поворота", Toast.LENGTH_SHORT).show()
            }
        }

        buttonRandomPlace.setOnClickListener {
            Log.d("SetupActivity", "Random button clicked")
            setupRandomly()
            Toast.makeText(this, "Корабли расставлены случайно", Toast.LENGTH_SHORT).show()
        }

        buttonClearBoard.setOnClickListener {
            Log.d("SetupActivity", "Clear button clicked")
            clearBoard()
            Toast.makeText(this, "Поле очищено", Toast.LENGTH_SHORT).show()
        }

        buttonStartBattle.setOnClickListener {
            Log.d("SetupActivity", "Start Battle button clicked")
            if (shipsToPlace.isEmpty()) {
                startBattle()
            } else {
                Toast.makeText(this, "Расставьте все корабли!", Toast.LENGTH_SHORT).show()
            }
        }
        Log.d("SetupActivity", "onCreate: Button listeners set.")
        // --- Конец слушателей кнопок ---

        Log.d("SetupActivity", "onCreate: Finished.")
    }

    // --- Методы для логики расстановки ---

    // Начинает процесс новой ручной расстановки
    private fun setupNewPlacement() {
        Log.d("SetupActivity", "setupNewPlacement: Starting.")
        clearBoard() // clearBoard вызывает updateGridCells и updateShipsListUI
        textViewGameStatus.text = "Выберите корабль снизу и кликните на поле"
        // Скрываем индикатор выбранного корабля в начале
        updateSelectedShipInfoUI()
        Log.d("SetupActivity", "setupNewPlacement: Finished.")
    }


    // Создает пустую доску gridSize x gridSize, заполненную CellState.EMPTY
    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    // Очищает логическую доску игрока и сбрасывает список кораблей на изначальный
    private fun clearBoard() {
        Log.d("SetupActivity", "clearBoard: Starting.")
        playerBoard = createEmptyBoard()
        shipsToPlace.clear()
        shipsToPlace.addAll( // Добавляем полный набор кораблей
            listOf(
                ShipToPlace(4),
                ShipToPlace(3), ShipToPlace(3),
                ShipToPlace(2), ShipToPlace(2), ShipToPlace(2),
                ShipToPlace(1), ShipToPlace(1), ShipToPlace(1), ShipToPlace(1)
            )
        )
        selectedShip = null

        Log.d("SetupActivity", "clearBoard: Before updateGridCells.")
        updateGridCells() // <-- Вызываем обновление UI поля после очистки логической доски
        Log.d("SetupActivity", "clearBoard: After updateGridCells.")

        Log.d("SetupActivity", "clearBoard: Before updateShipsListUI.")
        updateShipsListUI() // Обновляем UI списка кораблей (здесь он будет заполнен)
        Log.d("SetupActivity", "clearBoard: After updateShipsListUI.")

        buttonStartBattle.isEnabled = false // Кнопка "Начать Бой" снова неактивна
        textViewGameStatus.text = "Выберите корабль снизу и кликните на поле" // Сбрасываем статус/инструкцию
        updateSelectedShipInfoUI() // Скрываем индикатор
        Log.d("SetupActivity", "clearBoard: Finished.")
    }

    // Выполняет случайную расстановку всех кораблей на доске игрока
    private fun setupRandomly() {
        Log.d("SetupActivity", "setupRandomly: Starting.")
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
                Log.w("SetupActivity", "setupRandomly: Failed to place all ships.")
            }
        }

        shipsToPlace.clear() // После случайной расстановки список оставшихся пуст
        selectedShip = null // Сбрасываем выбранный корабль

        Log.d("SetupActivity", "setupRandomly: Before updateGridCells.")
        updateGridCells() // <-- Вызываем обновление UI поля после случайной расстановки логической доски
        Log.d("SetupActivity", "setupRandomly: After updateGridCells.")

        Log.d("SetupActivity", "setupRandomly: Before updateShipsListUI.")
        updateShipsListUI() // Обновляем UI списка кораблей (он станет пустым)
        Log.d("SetupActivity", "setupRandomly: After updateShipsListUI.")

        buttonStartBattle.isEnabled = true
        textViewGameStatus.text = "Корабли расставлены случайно! Готов к бою!"
        updateSelectedShipInfoUI() // Скрываем индикатор
        Log.d("SetupActivity", "setupRandomly: Finished.")
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
        Log.d("SetupActivity", "tryPlaceSelectedShip: Clicked on row $row, col $col")
        selectedShip?.let { ship ->
            Log.d("SetupActivity", "tryPlaceSelectedShip: Ship ${ship.size} selected. Attempting to place.")
            if (canPlaceShip(playerBoard, row, col, ship.size, ship.isHorizontal)) {
                Log.d("SetupActivity", "tryPlaceSelectedShip: Placement is valid.")
                placeShip(playerBoard, row, col, ship.size, ship.isHorizontal)

                // --- ИСПРАВЛЕНО: Удаление только ОДНОГО корабля из списка ---
                // Находим первый корабль в списке shipsToPlace с таким же размером
                val shipToRemove = shipsToPlace.find { it.size == ship.size }

                if (shipToRemove != null) {
                    val removed = shipsToPlace.remove(shipToRemove) // Удаляем найденный корабль

                    if (removed) {
                        Log.d("SetupActivity", "tryPlaceSelectedShip: Ship removed from list.")
                    } else {
                        // Этого не должно произойти, если find сработал
                        Log.e("SetupActivity", "tryPlaceSelectedShip: Failed to remove found ship ${ship.size} from list.")
                        Toast.makeText(this, "Ошибка при обновлении списка кораблей!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Этого точно не должно произойти, если корабль был выбран из shipsToPlace
                    Log.e("SetupActivity", "tryPlaceSelectedShip: FATAL ERROR: Selected ship ${ship.size} not found in shipsToPlace list!")
                    Toast.makeText(this, "Критическая ошибка расстановки!", Toast.LENGTH_LONG).show()
                    // Можно даже выбросить исключение, чтобы понять, почему это происходит
                    // throw IllegalStateException("Selected ship not found in list after canPlaceShip succeeded")
                }
                // --- Конец исправления удаления ---


                selectedShip = null // Сбрасываем выбранный корабль после успешной расстановки

                Log.d("SetupActivity", "tryPlaceSelectedShip: Before updateGridCells.")
                updateGridCells() // <-- Вызываем обновление UI поля
                Log.d("SetupActivity", "tryPlaceSelectedShip: After updateGridCells.")

                Log.d("SetupActivity", "tryPlaceSelectedShip: Before updateShipsListUI.")
                updateShipsListUI() // Обновляем UI списка оставшихся кораблей
                Log.d("SetupActivity", "tryPlaceSelectedShip: After updateShipsListUI.")

                updateSelectedShipInfoUI() // Скрываем индикатор

                if (shipsToPlace.isEmpty()) {
                    buttonStartBattle.isEnabled = true
                    textViewGameStatus.text = "Все корабли расставлены! Готов к бою!"
                    Toast.makeText(this, "Все корабли расставлены! Нажмите 'Начать Бой'.", Toast.LENGTH_LONG).show()
                    Log.d("SetupActivity", "tryPlaceSelectedShip: All ships placed.")
                } else {
                    textViewGameStatus.text = "Поставлен ${ship.size}-палубный корабль. Осталось: ${shipsToPlace.size} кораблей всего" // Уточним текст
                    Log.d("SetupActivity", "tryPlaceSelectedShip: Ship placed. ${shipsToPlace.size} remaining.")
                }

            } else {
                Log.d("SetupActivity", "tryPlaceSelectedShip: Placement is invalid.")
                textViewGameStatus.text = "Нельзя поставить корабль сюда!"
                Toast.makeText(this, "Нельзя поставить корабль сюда!", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.d("SetupActivity", "tryPlaceSelectedShip: No ship selected.")
            textViewGameStatus.text = "Выберите корабль для расстановки"
            Toast.makeText(this, "Выберите корабль для расстановки", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Работа с UI ---

    // Создает View ячеек для GridLayout и сохраняет их ссылки в playerCellViews.
    // Вызывается ОДИН РАЗ в onCreate.
    private fun createGridCells() {
        Log.d("SetupActivity", "createGridCells: Starting.")
        playerGridView.removeAllViews()
        // --- ИСПРАВЛЕНО: Удалена повторная инициализация playerCellViews ---
        // playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) } // <-- ЭТА СТРОКА УДАЛЕНА ИЗ ЭТОГО МЕТОДА
        // --- ОТЛАДКА: Проверяем playerCellViews перед заполнением ---
        Log.d("SetupActivity", "createGridCells: Before filling, playerCellViews.size = ${playerCellViews.size}")
        if (gridSize > 0 && playerCellViews.isNotEmpty() && playerCellViews[0] != null) {
            Log.d("SetupActivity", "createGridCells: Before filling, playerCellViews[0].size = ${playerCellViews[0].size}")
        } else {
            Log.d("SetupActivity", "createGridCells: Before filling, playerCellViews array seems empty or null. Cannot proceed with filling.")
            if (playerCellViews.isEmpty() || gridSize == 0) { // Добавлена проверка на gridSize == 0 на всякий случай
                Log.e("SetupActivity", "FATAL ERROR: playerCellViews is empty or gridSize is 0 when createGridCells is called.")
                // throw IllegalStateException("playerCellViews array is empty or gridSize is 0 when createGridCells is called") // Можно раскомментировать для явного краша
                return // Выходим, чтобы избежать краша на добавлении View
            }
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
                // Используем безопасную проверку для записи в массив
                if (row < playerCellViews.size && col < (playerCellViews.getOrNull(row)?.size ?: 0)) {
                    playerCellViews[row][col] = cellView // <-- Заполняем массив ссылкой
                } else {
                    Log.e("SetupActivity", "FATAL ERROR: Index outside bounds of playerCellViews during creation: row=$row, col=$col. Array size = ${playerCellViews.size}, inner size = ${playerCellViews.getOrNull(row)?.size}")
                    // Крашнем явно, чтобы поймать это
                    throw IndexOutOfBoundsException("playerCellViews array size mismatch during creation") // <-- Раскомментировано для явного краша
                }
                // --- Конец заполнения массива ---

                // updateCellView(cellView, playerBoard[row][col], false) // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ!
                //     updateCellView должен быть вызван ИЗ updateGridCells

                // Обработка клика по ячейке поля
                cellView.setOnClickListener {
                    tryPlaceSelectedShip(row, col)
                }

                playerGridView.addView(cellView) // Добавляем View в GridLayout
            }
        }
        Log.d("SetupActivity", "createGridCells: Finished filling grid with views.")
        // updateGridCells() // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ!
    }

    // Обновляет внешний вид ВСЕХ ячеек на поле игрока на основе текущего состояния playerBoard
    // Вызывается после каждого изменения playerBoard (clear, place, random)
    private fun updateGridCells() {
        Log.d("SetupActivity", "updateGridCells: Starting.")
        // --- ОТЛАДКА: Проверяем playerCellViews в updateGridCells ---
        Log.d("SetupActivity", "updateGridCells: playerCellViews.size = ${playerCellViews.size}")
        // Проверяем, что массив проинициализирован и не пуст перед попыткой доступа
        if (playerCellViews.isEmpty() || gridSize == 0 || playerCellViews[0] == null) {
            Log.e("SetupActivity", "updateGridCells: playerCellViews is empty, gridSize is 0, or inner array is null. Skipping UI update.")
            return // Выходим, если массив пуст или некорректен
        }
        if (playerCellViews.size != gridSize || playerCellViews[0]!!.size != gridSize) {
            Log.e("SetupActivity", "updateGridCells: playerCellViews size mismatch! Expected ${gridSize}x${gridSize}, got ${playerCellViews.size}x${playerCellViews[0]?.size}. Skipping UI update.")
            return // Выходим, если размер массива некорректен
        }
        // ---

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // --- ВАЖНО: Теперь мы используем УЖЕ СУЩЕСТВУЮЩИЙ массив playerCellViews ---
                // Используем безопасный доступ getOrNull
                playerCellViews.getOrNull(row)?.getOrNull(col)?.let { cellView ->
                    val state = playerBoard[row][col] // Состояние из логической доски
                    updateCellView(cellView, state, false) // <-- ВЫЗЫВАЕМ updateCellView ЗДЕСЬ
                } ?: run {
                    Log.e("SetupActivity", "View ячейки отсутствует в playerCellViews по адресу: row=$row, col=$col. Skipping updateCellView.")
                }
            }
        }
        Log.d("SetupActivity", "updateGridCells: Finished updating UI.")
    }

    // Обновляет внешний вид одной ячейки (TextView) в зависимости от ее логического состояния
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

    // Создает и добавляет TextView для меток координат (А-К и 1-10) вокруг сетки
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

    // Обновляет UI список кораблей, которые осталось расставить
    private fun updateShipsListUI() {
        Log.d("SetupActivity", "updateShipsListUI: Starting.")
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
            Log.d("SetupActivity", "updateShipsListUI: Ships list is empty, added 'All ships placed' message.")
        } else {
            for ((size, ships) in shipsBySize) {
                val shipCount = ships.size
                val shipText = "${size}x$shipCount"
                Log.d("SetupActivity", "updateShipsListUI: Adding $shipText to list.")

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
                        Log.d("SetupActivity", "Ship text $size clicked.")
                        val shipToSelect = shipsToPlace.find { it.size == size }
                        if (shipToSelect != null) {
                            Log.d("SetupActivity", "updateShipsListUI: Ship $size selected.")
                            selectedShip = shipToSelect // Устанавливаем выбранный корабль

                            updateShipsListUI() // Перерисовываем список, чтобы обновить подсветку
                            updateSelectedShipInfoUI() // Обновляем индикатор

                            Toast.makeText(this@SetupActivity, "Выбран корабль размером ${size}", Toast.LENGTH_SHORT).show()

                        } else {
                            Log.e("SetupActivity", "updateShipsListUI: Failed to find ship $size in list upon click.")
                            Toast.makeText(this@SetupActivity, "Ошибка: Не найден корабль для выбора!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                layoutShipsList.addView(shipTextView)
            }
        }
        buttonStartBattle.isEnabled = shipsToPlace.isEmpty()
        Log.d("SetupActivity", "updateShipsListUI: Finished. buttonStartBattle.isEnabled = ${buttonStartBattle.isEnabled}")
    }

    // Обновляет UI индикатор выбранного корабля
    private fun updateSelectedShipInfoUI() {
        Log.d("SetupActivity", "updateSelectedShipInfoUI: Starting. selectedShip = $selectedShip")
        selectedShip?.let { ship ->
            val orientation = if (ship.isHorizontal) "Гор." else "Вер."
            textViewSelectedShipInfo.text = "Выбран: ${ship.size}-палубный (${orientation})"
            textViewSelectedShipInfo.visibility = View.VISIBLE // Показываем индикатор
            Log.d("SetupActivity", "updateSelectedShipInfoUI: Showing info: ${textViewSelectedShipInfo.text}")
        } ?: run {
            // Если корабль не выбран, скрываем индикатор
            textViewSelectedShipInfo.text = "" // Очищаем текст
            textViewSelectedShipInfo.visibility = View.INVISIBLE // Скрываем (оставляет место) или View.GONE (не оставляет)
            Log.d("SetupActivity", "updateSelectedShipInfoUI: No ship selected, hiding info.")
        }
        // Проверка: если все корабли расставлены, индикатор тоже должен быть скрыт
        if (shipsToPlace.isEmpty() && selectedShip == null) {
            textViewSelectedShipInfo.visibility = View.INVISIBLE // Или GONE
        }
    }


    // --- Запуск боя ---

    private fun startBattle() {
        Log.d("SetupActivity", "startBattle: Starting.")
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("playerBoard", playerBoard as Serializable)
        startActivity(intent)
        finish()
        Log.d("SetupActivity", "startBattle: Finished, launching GameActivity.")
    }
}
