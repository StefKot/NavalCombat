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
import com.example.navalcombat.model.CellState // Импортируем из game.model
import com.example.navalcombat.model.Ship // Импортируем класс Ship (для создания нового списка кораблей при отмене)
import com.example.navalcombat.model.ShipToPlace // Импортируем из game.model
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
    private lateinit var buttonUndoPlacement: Button // <-- НОВАЯ кнопка Отменить
    private lateinit var layoutShipsList: LinearLayout
    private lateinit var textViewSetupTitle: TextView
    private lateinit var textViewGameStatus: TextView
    private lateinit var textViewShipsToPlace: TextView
    private lateinit var textViewSelectedShipInfo: TextView
    // --- Конец UI элементов ---

    // --- Логическая модель данных и состояние расстановки ---
    private lateinit var playerCellViews: Array<Array<TextView?>>

    private var playerBoard = createEmptyBoard()

    private var shipsToPlace = mutableListOf<ShipToPlace>()

    private var selectedShip: ShipToPlace? = null

    private val gridSize = 10

    private val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

    // --- Состояние для отмены последнего размещенного корабля ---
    private var savedPlayerBoardState: Array<Array<CellState>>? = null // Копия доски перед последним размещением
    private var savedShipsToPlaceState: MutableList<ShipToPlace>? = null // Копия списка кораблей перед последним размещением
    private var lastPlacedShipSize: Int? = null // Размер последнего успешно размещенного корабля
    private var lastPlacedShipIsHorizontal: Boolean? = null // Ориентация последнего успешно размещенного корабля
    // --- Конец состояния отмены ---


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
        buttonUndoPlacement = findViewById(R.id.buttonUndoPlacement) // <-- Находим кнопку Отменить
        layoutShipsList = findViewById(R.id.layoutShipsList)
        textViewSetupTitle = findViewById(R.id.textViewSetupTitle)
        textViewGameStatus = findViewById(R.id.textViewGameStatus)
        textViewShipsToPlace = findViewById(R.id.textViewShipsToPlace)
        textViewSelectedShipInfo = findViewById(R.id.textViewSelectedShipInfo)
        Log.d("SetupActivity", "onCreate: Buttons found: Rotate=${buttonRotateShip!=null}, Random=${buttonRandomPlace!=null}, Clear=${buttonClearBoard!=null}, Undo=${buttonUndoPlacement!=null}")
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

        // Инициализация массива View ячеек
        Log.d("SetupActivity", "onCreate: Initializing playerCellViews array with size $gridSize")
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        Log.d("SetupActivity", "onCreate: playerCellViews array initialized.")

        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize

        createLabels()
        Log.d("SetupActivity", "onCreate: Labels created.")

        createGridCells() // <-- Вызывается ОДИН РАЗ для создания View ячеек
        Log.d("SetupActivity", "onCreate: Grid cells created.")

        setupNewPlacement() // <-- Начинает процесс расстановки
        Log.d("SetupActivity", "onCreate: Setup new placement initiated.")


        // --- Устанавливаем слушатели для кнопок ---
        buttonRotateShip.setOnClickListener {
            Log.d("SetupActivity", "Rotate button clicked")
            selectedShip?.let { ship ->
                ship.isHorizontal = !ship.isHorizontal
                updateSelectedShipInfoUI() // Обновляем индикатор выбранного корабля
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
            clearBoard() // Этот метод сбрасывает состояние отмены
            Toast.makeText(this, "Поле очищено", Toast.LENGTH_SHORT).show()
        }

        // <-- НОВЫЙ СЛУШАТЕЛЬ ДЛЯ КНОПКИ ОТМЕНЫ -->
        buttonUndoPlacement.setOnClickListener {
            Log.d("SetupActivity", "Undo Placement button clicked")
            undoLastPlacement() // Вызываем метод отмены
        }
        // <-- Конец НОВОГО СЛУШАТЕЛЯ -->


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

        // Изначально кнопка Отменить неактивна
        buttonUndoPlacement.isEnabled = false

        Log.d("SetupActivity", "onCreate: Finished.")
    }

    // --- Методы для логики расстановки ---

    // Начинает процесс новой ручной расстановки
    private fun setupNewPlacement() {
        Log.d("SetupActivity", "setupNewPlacement: Starting.")
        clearBoard() // clearBoard сбрасывает состояние отмены
        textViewGameStatus.text = "Выберите корабль снизу и кликните на поле"
        updateSelectedShipInfoUI() // Скрываем индикатор выбранного корабля
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

        // --- Сбрасываем состояние для отмены ---
        savedPlayerBoardState = null
        savedShipsToPlaceState = null
        lastPlacedShipSize = null
        lastPlacedShipIsHorizontal = null
        buttonUndoPlacement.isEnabled = false // Кнопка Отменить неактивна
        // --- Конец сброса состояния отмены ---

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
            ShipToPlace(4), ShipToPlace(3), ShipToPlace(3),
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

        // --- При случайной расстановке не сохраняем состояние для отмены (или сбрасываем его) ---
        savedPlayerBoardState = null
        savedShipsToPlaceState = null
        lastPlacedShipSize = null
        lastPlacedShipIsHorizontal = null
        buttonUndoPlacement.isEnabled = false // Кнопка Отменить неактивна
        // --- Конец сброса состояния отмены ---


        shipsToPlace.clear() // После случайной расстановки список оставшихся пуст
        selectedShip = null // Сбрасываем выбранный корабль

        Log.d("SetupActivity", "setupRandomly: Before updateGridCells.")
        updateGridCells() // <-- Вызываем обновление UI поля
        Log.d("SetupActivity", "setupRandomly: After updateGridCells.")

        Log.d("SetupActivity", "setupRandomly: Before updateShipsListUI.")
        updateShipsListUI() // Обновляем UI списка кораблей (он станет пустым)
        Log.d("SetupActivity", "setupRandomly: After updateShipsListUI.")

        buttonStartBattle.isEnabled = true
        textViewGameStatus.text = "Корабли расставлены случайно! Готов к бою!"
        updateSelectedShipInfoUI() // Скрываем индикатор
        Log.d("SetupActivity", "setupRandomly: Finished.")
    }


    // Проверяет, можно ли разместить корабль (учитывает зоны 3x3)
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

    // Размещает корабль на логической доске
    private fun placeShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean) {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            if (currentRow >= 0 && currentRow < gridSize && currentCol >= 0 && currentCol < gridSize) {
                board[currentRow][currentCol] = CellState.SHIP
            }
        }
    }

    // Попытка разместить ВЫБРАННЫЙ корабль в указанных координатах
    private fun tryPlaceSelectedShip(row: Int, col: Int) {
        Log.d("SetupActivity", "tryPlaceSelectedShip: Clicked on row $row, col $col")
        selectedShip?.let { ship ->
            Log.d("SetupActivity", "tryPlaceSelectedShip: Ship ${ship.size} selected. Attempting to place.")
            if (canPlaceShip(playerBoard, row, col, ship.size, ship.isHorizontal)) {
                Log.d("SetupActivity", "tryPlaceSelectedShip: Placement is valid.")

                // --- ДОБАВЛЕНО: Сохраняем состояние перед успешным размещением ---
                saveStateBeforePlacement() // <-- Сохраняем доску и список кораблей
                // --- Конец добавлено ---

                placeShip(playerBoard, row, col, ship.size, ship.isHorizontal) // Размещаем на доске

                // --- ИСПРАВЛЕНО: Удаление только ОДНОГО корабля из списка ---
                val shipToRemove = shipsToPlace.find { it.size == ship.size }
                if (shipToRemove != null) {
                    val removed = shipsToPlace.remove(shipToRemove)
                    if (removed) {
                        Log.d("SetupActivity", "tryPlaceSelectedShip: Ship removed from list.")
                        // --- ДОБАВЛЕНО: Запоминаем размер и ориентацию размещенного корабля ---
                        lastPlacedShipSize = ship.size
                        lastPlacedShipIsHorizontal = ship.isHorizontal
                        // --- Конец добавлено ---

                    } else {
                        Log.e("SetupActivity", "tryPlaceSelectedShip: Failed to remove found ship ${ship.size} from list.")
                        Toast.makeText(this, "Ошибка при обновлении списка кораблей!", Toast.LENGTH_SHORT).show()
                        // В этом случае состояние для отмены может быть некорректным!
                        // Можно сбросить состояние отмены или просто оставить его как есть.
                        // Для простоты, оставим состояние как есть, но лог покажет проблему.
                    }
                } else {
                    Log.e("SetupActivity", "tryPlaceSelectedShip: FATAL ERROR: Selected ship ${ship.size} not found in shipsToPlace list!")
                    Toast.makeText(this, "Критическая ошибка расстановки!", Toast.LENGTH_LONG).show()
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

                // --- ДОБАВЛЕНО: Активируем кнопку Отменить ---
                buttonUndoPlacement.isEnabled = true
                // --- Конец добавлено ---


                if (shipsToPlace.isEmpty()) {
                    buttonStartBattle.isEnabled = true
                    textViewGameStatus.text = "Все корабли расставлены! Готов к бою!"
                    Toast.makeText(this, "Все корабли расставлены! Нажмите 'Начать Бой'.", Toast.LENGTH_LONG).show()
                    Log.d("SetupActivity", "tryPlaceSelectedShip: All ships placed.")
                } else {
                    textViewGameStatus.text = "Поставлен ${ship.size}-палубный корабль. Осталось: ${shipsToPlace.size} кораблей всего"
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
        // playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) } // <-- ЭТА СТРОКА УДАЛЕНА ИЗ ЭТОГО МЕТОДА
        Log.d("SetupActivity", "createGridCells: Before filling, playerCellViews.size = ${playerCellViews.size}")

        if (playerCellViews.isEmpty() || gridSize == 0 || playerCellViews.size != gridSize || (gridSize > 0 && playerCellViews[0] == null) || (gridSize > 0 && playerCellViews[0]!!.size != gridSize)) {
            Log.e("SetupActivity", "createGridCells: FATAL ERROR: playerCellViews array size mismatch or null when called. gridSize=$gridSize, array size=${playerCellViews.size}")
            throw IllegalStateException("playerCellViews array size mismatch or null when createGridCells is called")
        }


        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cellView = LayoutInflater.from(this).inflate(R.layout.grid_cell, playerGridView, false) as TextView

                val params = GridLayout.LayoutParams()
                params.width = 0
                params.height = 0
                params.rowSpec = GridLayout.spec(row, 1, 1f)
                params.columnSpec = GridLayout.spec(col, 1, 1f)
                cellView.layoutParams = params

                if (row < playerCellViews.size && col < (playerCellViews.getOrNull(row)?.size ?: 0)) {
                    playerCellViews[row][col] = cellView // <-- Заполняем массив ссылкой
                } else {
                    Log.e("SetupActivity", "FATAL ERROR: Index outside bounds of playerCellViews during creation: row=$row, col=$col. Array size = ${playerCellViews.size}, inner size = ${playerCellViews.getOrNull(row)?.size}")
                    throw IndexOutOfBoundsException("playerCellViews array size mismatch during creation")
                }

                // updateCellView(cellView, playerBoard[row][col], false) // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ!

                cellView.setOnClickListener {
                    tryPlaceSelectedShip(row, col)
                }

                playerGridView.addView(cellView)
            }
        }
        Log.d("SetupActivity", "createGridCells: Finished filling grid with views.")
    }

    // Обновляет внешний вид ВСЕХ ячеек на поле игрока
    private fun updateGridCells() {
        Log.d("SetupActivity", "updateGridCells: Starting.")
        Log.d("SetupActivity", "updateGridCells: playerCellViews.size = ${playerCellViews.size}")

        if (playerCellViews.isEmpty() || gridSize == 0 || playerCellViews.size != gridSize || (gridSize > 0 && playerCellViews[0] == null) || (gridSize > 0 && playerCellViews[0]!!.size != gridSize)) {
            Log.e("SetupActivity", "updateGridCells: playerCellViews is empty, size mismatch or null. Skipping UI update.")
            return
        }

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                playerCellViews.getOrNull(row)?.getOrNull(col)?.let { cellView ->
                    val state = playerBoard[row][col]
                    updateCellView(cellView, state, false) // false - это поле игрока
                } ?: run {
                    Log.e("SetupActivity", "View ячейки отсутствует в playerCellViews по адресу: row=$row, col=$col. Skipping updateCellView.")
                }
            }
        }
        Log.d("SetupActivity", "updateGridCells: Finished updating UI.")
    }


    // Обновляет внешний вид одной ячейки (TextView)
    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return

        cellView.text = ""
        cellView.setTextColor(ContextCompat.getColor(this, android.R.color.transparent))

        when (state) {
            CellState.EMPTY -> cellView.setBackgroundResource(R.drawable.cell_water)
            CellState.SHIP -> {
                if (isOpponent) {
                    cellView.setBackgroundResource(R.drawable.cell_water)
                } else {
                    cellView.setBackgroundResource(R.drawable.cell_ship_player) // Показать корабль игрока (серый)
                }
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

    // Создает и добавляет TextView для меток координат (А-К и 1-10)
    private fun createLabels() {
        val labelColor = ContextCompat.getColor(this, R.color.purple_700)
        val labelTextSize = 14f

        playerColLabelsLayout.removeAllViews()
        playerRowLabelsLayout.removeAllViews()

        val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

        for (i in 0 until gridSize) {
            val colLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER }
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
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL }
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
                            selectedShip = shipToSelect

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

    // --- Логика отмены размещения ---

    // Сохраняет состояние доски и списка кораблей перед успешным размещением
    private fun saveStateBeforePlacement() {
        Log.d("SetupActivity", "saveStateBeforePlacement: Saving state.")
        // Создаем глубокую копию доски
        savedPlayerBoardState = Array(gridSize) { r -> Array(gridSize) { c -> playerBoard[r][c] } }
        // Создаем глубокую копию списка кораблей shipsToPlace
        savedShipsToPlaceState = shipsToPlace.map { ship ->
            // Важно создать новый объект ShipToPlace с копией данных
            ShipToPlace(ship.size, ship.isHorizontal)
        }.toMutableList() // Преобразуем в изменяемый список
        // Не сохраняем lastPlacedShipSize/IsHorizontal здесь, они будут установлены ПОСЛЕ успешного размещения

        // Кнопка Отменить будет активирована после успешного размещения в tryPlaceSelectedShip
        // buttonUndoPlacement.isEnabled = true

        Log.d("SetupActivity", "saveStateBeforePlacement: State saved.")
    }

    // Отменяет последнее успешно выполненное размещение корабля
    private fun undoLastPlacement() {
        Log.d("SetupActivity", "undoLastPlacement: Starting.")
        // Проверяем, есть ли сохраненное состояние
        if (savedPlayerBoardState != null && savedShipsToPlaceState != null && lastPlacedShipSize != null) {
            Log.d("SetupActivity", "undoLastPlacement: Restoring state.")
            // Восстанавливаем доску
            playerBoard = Array(gridSize) { r -> Array(gridSize) { c -> savedPlayerBoardState!![r][c] } }
            // Восстанавливаем список кораблей shipsToPlace
            shipsToPlace = savedShipsToPlaceState!!.map { ship ->
                ShipToPlace(ship.size, ship.isHorizontal)
            }.toMutableList()

            // Сбрасываем информацию о последнем размещенном корабле и сохраненное состояние
            lastPlacedShipSize = null
            lastPlacedShipIsHorizontal = null // Сбрасываем ориентацию
            savedPlayerBoardState = null
            savedShipsToPlaceState = null

            // TODO: Можно восстановить выбранный корабль, который был выбран ПЕРЕД последним размещением
            selectedShip = null // Сбрасываем выбранный корабль

            // Обновляем UI поля и списка кораблей
            updateGridCells()
            updateShipsListUI()
            updateSelectedShipInfoUI() // Скрываем индикатор выбранного корабля

            // Кнопка Начать Бой становится неактивной, т.к. мы отменили расстановку
            buttonStartBattle.isEnabled = false

            // Кнопка Отменить становится неактивной, т.к. отменено только последнее действие
            buttonUndoPlacement.isEnabled = false

            textViewGameStatus.text = "Последнее размещение отменено."
            Toast.makeText(this, "Последнее размещение отменено.", Toast.LENGTH_SHORT).show()
            Log.d("SetupActivity", "undoLastPlacement: State restored.")

        } else {
            Log.w("SetupActivity", "undoLastPlacement: No state to restore.")
            // Если нечего отменять, убедимся, что кнопка неактивна
            buttonUndoPlacement.isEnabled = false
            Toast.makeText(this, "Нет действий для отмены.", Toast.LENGTH_SHORT).show()
        }
    }
    // --- Конец добавлено ---

}
// --- Extension функция для конвертации dp в px ---
// Эта функция ДОЛЖНА БЫТЬ ВНЕ КЛАССА SetupActivity.
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