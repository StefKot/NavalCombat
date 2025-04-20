package com.example.navalcombat.activities

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.example.navalcombat.R
import com.example.navalcombat.model.CellState
import com.example.navalcombat.model.Ship
import java.io.Serializable
import java.util.Stack
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
    // Кнопка Начать бой (найдена, но не используется в процессе игры)
    private lateinit var buttonStartBattle: Button
    // --- Конец UI элементов ---

    // --- Логическая модель данных и состояние игры ---
    private lateinit var opponentCellViews: Array<Array<TextView?>>
    private lateinit var playerCellViews: Array<Array<TextView?>>

    private var playerBoard = createEmptyBoard() // Будет заполнена из Intent
    private var opponentBoard = createEmptyBoard() // Будет заполнена случайно

    private var playerShips = mutableListOf<Ship>()
    private var opponentShips = mutableListOf<Ship>()

    private var isPlayerTurn = true
    private var isGameOver = false

    private val gridSize = 10 // <-- Размер поля 10x10

    private val shipSizes = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1) // Стандартные размеры кораблей для расстановки

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
        buttonStartBattle = findViewById(R.id.buttonStartBattle)
        // --- Конец findViewById ---

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayoutGame)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBarsInsets.top, bottom = systemBarsInsets.bottom)
            insets
        }

        // Инициализация массивов View ячеек
        Log.d("GameActivity", "onCreate: Initializing CellViews arrays with size $gridSize")
        opponentCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        Log.d("GameActivity", "onCreate: CellViews arrays initialized.")

        // Убедимся, что GridLayouts настроены ДО создания ячеек
        opponentGridView.rowCount = gridSize
        opponentGridView.columnCount = gridSize
        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize
        Log.d("GameActivity", "onCreate: GridLayouts row/col count set.")

        createLabels() // <-- Вызывается ОДИН РАЗ для создания всех меток
        Log.d("GameActivity", "onCreate: Labels created.")

        // --- Получаем расстановку игрока из Intent и создаем объекты Ship ---
        val receivedPlayerBoard = intent.getSerializableExtra("playerBoard") as? Array<Array<CellState>>
        if (receivedPlayerBoard != null) {
            playerBoard = receivedPlayerBoard // Устанавливаем доску игрока из Intent
            playerShips = findShipsOnBoard(playerBoard) // Находим и создаем объекты Ship на основе полученной доски
            Log.d("GameActivity", "onCreate: Player board received from Intent. Found ${playerShips.size} ships.")
        } else {
            // Fallback: Если доска не получена (не должно происходить при запуске из SetupActivity)
            Log.e("GameActivity", "onCreate: Player board NOT received from Intent! Using random placement for player.")
            Toast.makeText(this, "Ошибка получения расстановки. Случайная расстановка.", Toast.LENGTH_LONG).show()
            playerBoard = createEmptyBoard()
            playerShips = placeShipsRandomlyAndCreateObjects(playerBoard) // Случайная расстановка и создание Ship
            Log.d("GameActivity", "onCreate: Fallback random placement for player. Found ${playerShips.size} ships.")
        }
        // --- Конец получения данных из Intent ---

        setupGame() // Запускаем остальную настройку игры (расстановка компа)

        // --- Создаем View ячеек для обоих полей ОДИН РАЗ ---
        createGridCells(opponentGridView, opponentBoard, true) // <-- Вызывается ОДИН РАЗ для противника
        createGridCells(playerGridView, playerBoard, false) // <-- Вызывается ОДИН РАЗ для игрока
        Log.d("GameActivity", "onCreate: Grid cells created for both boards.")

        // Изначально показываем поле противника и обновляем его UI
        // showBoard() вызовет updateStatusText(), который установит "Ваш ход"
        showBoard(true) // true = показать поле противника.
        Log.d("GameActivity", "onCreate: Initial board set to opponent.")

        Log.d("GameActivity", "onCreate: Finished.")
    }

    // --- Методы игры ---

    // Настраивает новую игру: расстановка кораблей противника
    private fun setupGame() {
        Log.d("GameActivity", "setupGame: Starting.")
        // playerBoard и playerShips уже установлены в onCreate

        opponentBoard = createEmptyBoard() // Создаем пустую доску для противника
        // --- ИСПРАВЛЕНО: Используем обновленный метод расстановки ---
        opponentShips = placeShipsRandomlyAndCreateObjects(opponentBoard) // Случайная расстановка и создание Ship
        Log.d("GameActivity", "setupGame: Opponent ships placed. Found ${opponentShips.size} ships.")
        // --- Конец исправления ---

        isPlayerTurn = true // Игрок всегда начинает
        isGameOver = false

        Log.d("GameActivity", "setupGame: Finished.")
    }


    // Создает пустую доску gridSize x gridSize, заполненную CellState.EMPTY
    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    // --- Метод для НАХОЖДЕНИЯ кораблей на логической доске и СОЗДАНИЯ объектов Ship ---
    // Используется для доски игрока, полученной из SetupActivity
    private fun findShipsOnBoard(board: Array<Array<CellState>>): MutableList<Ship> {
        Log.d("GameActivity", "findShipsOnBoard: Starting.")
        val ships = mutableListOf<Ship>()
        val visitedCells = Array(gridSize) { BooleanArray(gridSize) }

        // Проходим по всей доске
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Если нашли часть корабля, которую еще не посещали И это часть корабля в любом состоянии
                if ((board[row][col] == CellState.SHIP || board[row][col] == CellState.HIT || board[row][col] == CellState.SUNK)
                    && !visitedCells[row][col]) {

                    val shipCells = mutableListOf<Pair<Int, Int>>()
                    val stack = Stack<Pair<Int, Int>>()

                    stack.push(Pair(row, col))
                    visitedCells[row][col] = true // Помечаем начальную ячейку как посещенную

                    var currentHits = 0 // Счетчик попаданий для этого корабля

                    while (stack.isNotEmpty()) {
                        val currentCell = stack.pop()
                        shipCells.add(currentCell)

                        // Если текущая ячейка - HIT или SUNK, увеличиваем счетчик попаданий
                        if (board[currentCell.first][currentCell.second] == CellState.HIT || board[currentCell.first][currentCell.second] == CellState.SUNK) {
                            currentHits++
                        }

                        // Проверяем 4 соседние ячейки (верх, низ, лево, право)
                        val neighbors = listOf(
                            Pair(currentCell.first - 1, currentCell.second),
                            Pair(currentCell.first + 1, currentCell.second),
                            Pair(currentCell.first, currentCell.second - 1),
                            Pair(currentCell.first, currentCell.second + 1)
                        )

                        for (neighbor in neighbors) {
                            val r = neighbor.first
                            val c = neighbor.second

                            // Если соседняя ячейка в пределах доски, содержит ЧАСТЬ КОРАБЛЯ (в любом состоянии)
                            // и еще не посещена
                            if (r >= 0 && r < gridSize && c >= 0 && c < gridSize &&
                                (board[r][c] == CellState.SHIP || board[r][c] == CellState.HIT || board[r][c] == CellState.SUNK)
                                && !visitedCells[r][c]) {
                                stack.push(Pair(r, c))
                                visitedCells[r][c] = true
                            }
                        }
                    }

                    // После нахождения всех ячеек корабля, определяем его размер и количество попаданий
                    val shipSize = shipCells.size
                    if (shipSize > 0) {
                        // Создаем объект Ship с найденным размером, ячейками и количеством попаданий
                        ships.add(Ship(shipSize, shipCells.toList(), currentHits))
                    }
                }
            }
        }
        Log.d("GameActivity", "findShipsOnBoard: Finished. Found ${ships.size} ships.")
        return ships // Возвращаем список найденных Ship объектов
    }


    // --- Случайная расстановка кораблей и СОЗДАНИЕ объектов Ship ---
    // Используется как для игрока (fallback), так и для противника
    // ИСПРАВЛЕНО: Улучшена логика проверки окружения при размещении
    private fun placeShipsRandomlyAndCreateObjects(board: Array<Array<CellState>>): MutableList<Ship> {
        Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Starting.")
        val ships = mutableListOf<Ship>()
        val random = Random(System.currentTimeMillis()) // Используем System.currentTimeMillis() для разной расстановки

        val shipSizesCopy = shipSizes.toMutableList() // Копируем размеры кораблей для расстановки

        // Очищаем доску перед расстановкой
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                board[r][c] = CellState.EMPTY
            }
        }
        Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Board cleared.")

        for (size in shipSizesCopy) {
            var placed = false
            var attempts = 0
            val maxAttempts = 5000 // Увеличим количество попыток

            Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Attempting to place ship of size $size...")

            while (!placed && attempts < maxAttempts) {
                val row = random.nextInt(gridSize)
                val col = random.nextInt(gridSize)
                val isHorizontal = random.nextBoolean()

                var canPlace = true
                val potentialShipCells = mutableListOf<Pair<Int, Int>>() // Сохраняем клетки корабля для добавления в ships

                // --- Проверка возможности размещения корабля и его окружения ---
                // Корабль горизонтальный: занимает (row, col) до (row, col + size - 1)
                // Корабль вертикальный: занимает (row, col) до (row + size - 1, col)

                // Определяем границы прямоугольника 3x3 вокруг потенциального корабля
                val checkRowStart = row - 1
                val checkRowEnd = if (isHorizontal) row + 1 else row + size // Включая строку ниже самой нижней палубы
                val checkColStart = col - 1
                val checkColEnd = if (isHorizontal) col + size else col + 1 // Включая столбец правее самой правой палубы

                // Сначала быстрая проверка, что сам корабль полностью внутри поля
                var shipInBounds = true
                for (i in 0 until size) {
                    val shipRow = if (isHorizontal) row else row + i
                    val shipCol = if (isHorizontal) col + i else col
                    if (shipRow < 0 || shipRow >= gridSize || shipCol < 0 || shipCol >= gridSize) {
                        shipInBounds = false
                        break
                    }
                }

                if (shipInBounds) {
                    // Проверяем каждую клетку в расширенном прямоугольнике (корабль + 1 клетка вокруг)
                    // Каждая такая клетка в пределах поля должна быть CellState.EMPTY на доске.
                    for (rCheck in checkRowStart..checkRowEnd) {
                        for (cCheck in checkColStart..checkColEnd) {
                            // Убедимся, что проверяемая клетка в пределах поля
                            if (rCheck >= 0 && rCheck < gridSize && cCheck >= 0 && cCheck < gridSize) {
                                // Если клетка на доске НЕ ПУСТАЯ (т.е. занята другим кораблем, промахом и т.д.),
                                // значит здесь нельзя разместить корабль.
                                if (board[rCheck][cCheck] != CellState.EMPTY) {
                                    canPlace = false
                                    break // Выходим из цикла по столбцам
                                }
                            }
                            // Если клетка за границами, это не мешает размещению корабля,
                            // если сам корабль находится в пределах (что проверено shipInBounds).
                        }
                        if (!canPlace) break // Выходим из цикла по строкам
                    }

                    // Если дошли сюда и canPlace все еще true, значит окружение свободно.
                    // Заполняем potentialShipCells
                    if (canPlace) {
                        for (i in 0 until size) {
                            val shipRow = if (isHorizontal) row else row + i
                            val shipCol = if (isHorizontal) col + i else col
                            potentialShipCells.add(Pair(shipRow, shipCol))
                        }
                        // Двойная проверка: убедимся, что собрали нужное количество клеток
                        if (potentialShipCells.size != size) {
                            // Это не должно происходить, если shipInBounds был true
                            Log.e("GameActivity", "Internal logic error: potentialShipCells size mismatch after successful placement check.")
                            canPlace = false // Считаем, что размещение не удалось из-за внутренней ошибки
                        }
                    }

                } else {
                    // Корабль сам по себе выходит за границы поля, не можем его разместить
                    canPlace = false
                }
                // --- Конец проверки ---


                // Если проверка успешна (canPlace == true)
                if (canPlace) {
                    // Размещаем корабль на РЕАЛЬНОЙ доске
                    for (cell in potentialShipCells) {
                        board[cell.first][cell.second] = CellState.SHIP
                    }
                    ships.add(Ship(size, potentialShipCells)) // Создаем объект Ship с 0 попаданий
                    placed = true // Корабль успешно размещен, выходим из while
                    Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Successfully placed ship size $size at ($row,$col) isHorizontal=$isHorizontal.")
                } else {
                    attempts++
                    // Опционально: логировать неудачные попытки для отладки
                    // Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Cannot place ship size $size at ($row,$col) isHorizontal=$isHorizontal. Attempt $attempts. Reason: shipInBounds=$shipInBounds, canPlaceAfterCheck=$canPlace")
                }
            }
            if (!placed) {
                Log.e("GameActivity", "placeShipsRandomlyAndCreateObjects: FATAL ERROR: Failed to place ship $size randomly after $maxAttempts attempts.")
                Toast.makeText(this, "Ошибка при расстановке кораблей компьютера. Игра невозможна.", Toast.LENGTH_LONG).show()
                finish() // Завершаем Activity, так как игра не может начаться
                return mutableListOf() // Возвращаем пустой список или генерируем исключение
            }
        }
        Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Finished placing ships. Created ${ships.size} ships.")
        if (ships.size != shipSizes.size) {
            Log.e("GameActivity", "placeShipsRandomlyAndCreateObjects: ERROR: Placed ${ships.size} ships, expected ${shipSizes.size}!")
            Toast.makeText(this, "Ошибка при расстановке кораблей компьютера. Не все корабли размещены.", Toast.LENGTH_LONG).show()
        }
        return ships
    }


    // --- Работа с UI ---

    // Создает View ячеек для GridLayout и сохраняет их ссылки в массивах.
    // Вызывается ОДИН РАЗ в onCreate для каждого поля.
    private fun createGridCells(grid: GridLayout, board: Array<Array<CellState>>, isOpponent: Boolean) {
        Log.d("GameActivity", "createGridCells: Starting for isOpponent=$isOpponent.")
        grid.removeAllViews() // Очищаем, если вдруг там что-то было
        val cellReferences = if (isOpponent) opponentCellViews else playerCellViews

        // Проверяем, что массив cellReferences проинициализирован с правильным размером
        if (cellReferences.isEmpty() || gridSize == 0 || cellReferences.size != gridSize || (gridSize > 0 && cellReferences[0] == null) || (gridSize > 0 && cellReferences[0]!!.size != gridSize)) {
            Log.e("GameActivity", "createGridCells: FATAL ERROR: cellReferences array size mismatch or null when called. isOpponent=$isOpponent, gridSize=$gridSize, array size=${cellReferences.size}")
            throw IllegalStateException("Cell references array size mismatch or null when createGridCells is called for isOpponent=$isOpponent")
        }


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
                if (row < cellReferences.size && col < (cellReferences.getOrNull(row)?.size ?: 0)) {
                    cellReferences[row][col] = cellView // <-- Сохраняем ссылку
                } else {
                    // Это должно быть обнаружено предыдущей проверкой, но на всякий случай
                    Log.e("GameActivity", "FATAL ERROR: Index outside bounds of cellReferences during creation: isOpponent=$isOpponent, row=$row, col=$col.")
                    throw IndexOutOfBoundsException("Cell references array size mismatch during creation for isOpponent=$isOpponent")
                }
                // --- Конец заполнения массива ---

                // Обработка клика (только для поля противника)
                if (isOpponent) {
                    cellView.setOnClickListener {
                        if (!isGameOver && isPlayerTurn) { // Проверяем ход и конец игры
                            handlePlayerShot(row, col)
                        } else {
                            // Если не ход игрока или игра окончена, но клик по полю противника
                            // Log.d("GameActivity", "Click on opponent board ignored. isPlayerTurn=$isPlayerTurn, isGameOver=$isGameOver")
                        }
                    }
                } else {
                    cellView.isClickable = false // Поле игрока не кликабельно
                }

                grid.addView(cellView) // Добавляем View в GridLayout
            }
        }
        Log.d("GameActivity", "createGridCells: Finished filling grid with views for isOpponent=$isOpponent. Total views: ${grid.childCount}")
    }


    // Обновляет внешний вид ВСЕХ ячеек на ПОЛЕ, которое сейчас ВИДИМО.
    // Вызывается после showBoard() и после каждого выстрела.
    private fun updateGridCells() {
        Log.d("GameActivity", "updateGridCells: Starting.")
        // Определяем, какое поле сейчас видимо
        val isOpponentBoardVisible = opponentBoardContainer.visibility == View.VISIBLE
        Log.d("GameActivity", "updateGridCells: isOpponentBoardVisible = $isOpponentBoardVisible")

        // Выбираем соответствующую логическую доску, массив View ячеек, список кораблей и флаг isOpponent
        val currentBoard = if (isOpponentBoardVisible) opponentBoard else playerBoard
        val cellViews = if (isOpponentBoardVisible) opponentCellViews else playerCellViews
        val currentShips = if (isOpponentBoardVisible) opponentShips else playerShips // Список кораблей для текущего поля
        val isOpponent = isOpponentBoardVisible // Флаг для updateCellView

        // Проверяем, что массив View ячеек проинициализирован и корректен
        if (cellViews.isEmpty() || gridSize == 0 || cellViews.size != gridSize || (gridSize > 0 && cellViews[0] == null) || (gridSize > 0 && cellViews[0]!!.size != gridSize)) {
            Log.e("GameActivity", "updateGridCells: cellViews is empty, size mismatch or null. isOpponentVisible=$isOpponentBoardVisible. Skipping UI update.")
            return // Выходим, если массив некорректен
        }

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Используем безопасный доступ getOrNull для получения View ячейки
                cellViews.getOrNull(row)?.getOrNull(col)?.let { cellView ->
                    val state = currentBoard[row][col] // Состояние из логической доски

                    // --- ВАЖНО: Определяем фактическое состояние для отображения (например, HIT vs SUNK) ---
                    var actualState = state
                    // Проверяем на sunk только если базовое состояние может быть частью корабля
                    if (actualState == CellState.SHIP || actualState == CellState.HIT || actualState == CellState.SUNK) {
                        // Ищем корабль, которому принадлежит эта ячейка с координатами [row, col]
                        val ship = currentShips.find { it.cells.contains(Pair(row, col)) }
                        if (ship?.isSunk() == true) {
                            // Если корабль найден и он потоплен, меняем состояние для отображения на SUNK
                            actualState = CellState.SUNK
                        } else if (actualState == CellState.SUNK) {
                            // Если логическое состояние SUNK, но корабль по объекту Ship еще не потоплен,
                            // это несоответствие. Отображаем как HIT, если клетка была HIT на доске.
                            // ИСПРАВЛЕНО: Проверка на HIT/SUNK должна быть по самой доске
                            if (currentBoard[row][col] == CellState.HIT || currentBoard[row][col] == CellState.SUNK) {
                                actualState = CellState.HIT // Если клетка была отмечена как HIT или SUNK на доске, но корабль не потоплен, показываем HIT
                            } else {
                                // Если логическое состояние SUNK, но на доске ни HIT, ни SUNK, и корабль не потоплен,
                                // это ошибка в логике. Отображаем как EMPTY, чтобы не вводить в заблуждение.
                                actualState = CellState.EMPTY
                                Log.e("GameActivity", "updateGridCells: Found unexpected CellState.SUNK at $row,$col. Board state/Ship object mismatch.")
                            }
                        }
                    }
                    // --- Конец определения фактического состояния ---

                    updateCellView(cellView, actualState, isOpponent)
                } ?: run {
                    Log.e("GameActivity", "View ячейки отсутствует в cellViews по адресу: isOpponentVisible=$isOpponentBoardVisible, row=$row, col=$col. Skipping updateCellView.")
                }
            }
        }
        // --- ДОБАВЛЕНО: Расставляем точки промаха вокруг ПОТОПЛЕННЫХ кораблей ---
        // Вызывается после того, как все ячейки ОБНОВЛЕНЫ до sunk или hit/miss
        // Применимо к любому полю, которое сейчас отображается
        placeMissMarksAroundSunkShips(currentBoard, cellViews, currentShips, isOpponent) // <-- Вызов
        // --- Конец добавлено ---

        Log.d("GameActivity", "updateGridCells: Finished updating UI for visible board.")
    }

    // --- Метод для расстановки точек промаха вокруг ПОТОПЛЕННЫХ кораблей ---
    // Вызывается из updateGridCells.
    private fun placeMissMarksAroundSunkShips(board: Array<Array<CellState>>, cellViews: Array<Array<TextView?>>, ships: List<Ship>, isOpponentBoard: Boolean) {
        // Log.d("GameActivity", "placeMissMarksAroundSunkShips: Starting for isOpponentBoard=$isOpponentBoard.")
        for (ship in ships) {
            if (ship.isSunk()) { // Если корабль потоплен
                for (cell in ship.cells) { // Проходим по всем ячейкам потопленного корабля
                    val shipRow = cell.first
                    val shipCol = cell.second

                    // Проверяем все ячейки вокруг (3x3)
                    for (r in (shipRow - 1)..(shipRow + 1)) {
                        for (c in (shipCol - 1)..(shipCol + 1)) {
                            // Убеждаемся, что ячейка в пределах поля и не является частью этого же потопленного корабля
                            if (r >= 0 && r < gridSize && c >= 0 && c < gridSize && !ship.cells.contains(Pair(r, c))) {
                                // Если ячейка ПУСТА (вода), помечаем ее как ПРОМАХ и обновляем UI
                                // Мы хотим помечать только те EMPTY клетки вокруг потопленного корабля,
                                // которые еще не были атакованы.
                                if (board[r][c] == CellState.EMPTY) {
                                    // Проверяем, что View ячейки существует
                                    cellViews.getOrNull(r)?.getOrNull(c)?.let { cellView ->
                                        // Обновляем логическую доску
                                        board[r][c] = CellState.MISS // Помечаем на логической доске как промах
                                        // Обновляем UI ячейки (используем флаг isOpponentBoard)
                                        updateCellView(cellView, CellState.MISS, isOpponentBoard)
                                    } ?: run {
                                        Log.e("GameActivity", "View ячейки отсутствует в cellViews по адресу: isOpponentBoard=$isOpponentBoard, row=$r, col=$c during placing miss marks.")
                                    }
                                }
                                // Если ячейка уже была MISS или HIT, ничего не делаем.
                            }
                        }
                    }
                }
            }
        }
        // Log.d("GameActivity", "placeMissMarksAroundSunkShips: Finished.")
    }


    // Обновляет внешний вид одной ячейки (TextView) в зависимости от ее логического состояния
    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return

        cellView.text = ""
        cellView.setTextColor(ContextCompat.getColor(this, android.R.color.transparent)) // Прозрачный текст по умолчанию
        cellView.setTypeface(null, Typeface.NORMAL) // Сброс стиля текста

        when (state) {
            CellState.EMPTY -> cellView.setBackgroundResource(R.drawable.cell_water)
            CellState.SHIP -> {
                if (isOpponent) {
                    cellView.setBackgroundResource(R.drawable.cell_water) // Скрыть корабль противника
                } else {
                    cellView.setBackgroundResource(R.drawable.cell_ship_player) // Показать корабль игрока (серый)
                }
            }
            CellState.HIT -> {
                cellView.setBackgroundResource(R.drawable.cell_hit) // Красный фон
                cellView.text = "X" // Текст "X"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.white)) // Белый текст для X
                cellView.setTypeface(null, Typeface.BOLD) // Жирный текст
            }
            CellState.MISS -> {
                cellView.setBackgroundResource(R.drawable.cell_water) // Фон воды
                cellView.text = "•" // Текст "точка"
                // Убедитесь, что у вас есть ресурс R.color.holo_red_dark_custom или используйте стандартный цвет
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark)) // Темно-красный текст точки
                cellView.setTypeface(null, Typeface.BOLD) // Жирный текст
            }
            CellState.SUNK -> {
                cellView.setBackgroundResource(R.drawable.cell_sunk) // Черный фон
                cellView.text = "X" // Текст "X"
                // Убедитесь, что у вас есть ресурс R.color.holo_red_dark_custom или используйте стандартный цвет
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark)) // Темно-красный текст "X"
                cellView.setTypeface(null, Typeface.BOLD) // Жирный текст
            }
        }
        cellView.gravity = Gravity.CENTER
    }

    // Создает и добавляет TextView для меток координат (А-К и 1-10)
    private fun createLabels() {
        val labelColor = ContextCompat.getColor(this, R.color.purple_700)
        val labelTextSize = 14f

        opponentColLabelsLayout.removeAllViews()
        opponentRowLabelsLayout.removeAllViews()
        playerColLabelsLayout.removeAllViews()
        playerRowLabelsLayout.removeAllViews()

        val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

        for (i in 0 until gridSize) {
            // Создаем НОВЫЙ TextView для противника и НОВЫЙ для игрока
            val opponentColLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerColLabel = TextView(this).apply { // TextView для поля игрока
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            opponentColLabelsLayout.addView(opponentColLabel) // Добавляем в Layout меток столбцов противника
            playerColLabelsLayout.addView(playerColLabel) // Добавляем в Layout меток столбцов игрока
        }

        for (i in 0 until gridSize) {
            // Создаем НОВЫЙ TextView для противника и НОВЫЙ для игрока
            val opponentRowLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerRowLabel = TextView(this).apply { // TextView для поля игрока
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            opponentRowLabelsLayout.addView(opponentRowLabel) // Добавляем в Layout меток строк противника
            playerRowLabelsLayout.addView(playerRowLabel) // Добавляем в Layout меток строк игрока
        }
    }

    // Переключает видимость между полем противника и полем игрока
    private fun showBoard(showOpponent: Boolean) {
        Log.d("GameActivity", "showBoard: Switching board to " + if(showOpponent) "Opponent" else "Player")
        if (showOpponent) {
            opponentBoardContainer.visibility = View.VISIBLE
            playerBoardContainer.visibility = View.GONE
        } else {
            playerBoardContainer.visibility = View.VISIBLE
            opponentBoardContainer.visibility = View.GONE
        }
        // Обновляем статус текст и UI поля после переключения
        // updateStatusText() здесь вызывается, чтобы установить "Ваш ход" или "Ход компьютера..."
        updateStatusText()
        updateGridCells() // <-- ВАЖНО: Обновляем UI после переключения поля, чтобы отобразить актуальное состояние
        Log.d("GameActivity", "showBoard: Switching finished, updateGridCells called.")
    }


    // Обрабатывает выстрел игрока по клетке (row, col) на поле противника
    private fun handlePlayerShot(row: Int, col: Int) {
        Log.d("GameActivity", "handlePlayerShot: Player shot at $row,$col. isPlayerTurn=$isPlayerTurn")
        if (!isGameOver && isPlayerTurn) { // Проверяем ход и конец игры

            val cellState = opponentBoard[row][col]

            if (cellState == CellState.HIT || cellState == CellState.MISS || cellState == CellState.SUNK) {
                statusTextView.text = "Сюда уже стреляли, выберите другую клетку!"
                Log.d("GameActivity", "handlePlayerShot: Already shot there.")
                return // Выходим, ход не засчитан
            }

            if (cellState == CellState.SHIP) {
                Log.d("GameActivity", "handlePlayerShot: Hit!")
                // Ищем корабль, которому принадлежит эта ячейка
                val hitShip = opponentShips.find { it.cells.contains(Pair(row, col)) }

                if (hitShip != null) {
                    hitShip.hits++
                    opponentBoard[row][col] = CellState.HIT // Обновляем состояние на доске

                    if (hitShip.isSunk()) {
                        statusTextView.text = "Убил ${hitShip.size}-палубник!" // Sets the correct text
                        Log.d("GameActivity", "handlePlayerShot: Sunk a ${hitShip.size}-палубник!")
                        // Обновляем все клетки потопленного корабля на доске на SUNK
                        for (cell in hitShip.cells) {
                            opponentBoard[cell.first][cell.second] = CellState.SUNK
                        }
                        // Точки вокруг будут расставлены в updateGridCells
                    } else {
                        statusTextView.text = "Ранил!" // Sets the correct text
                        Log.d("GameActivity", "handlePlayerShot: Hit a ${hitShip.size}-палубник!")
                    }

                    updateGridCells() // <-- Вызываем обновление UI ПОЛЯ ПРОТИВНИКА

                    checkGameOver() // Might set isGameOver to true

                    if (!isGameOver) {
                        // Если игра не окончена и игрок попал/убил, ход игрока продолжается
                        Log.d("GameActivity", "handlePlayerShot: Player hit/sunk, turn continues.")
                        // --- ИСПРАВЛЕНО: УДАЛЕН вызов updateStatusText() ЗДЕСЬ ---
                        // updateStatusText() // <-- ЭТО БЫЛА ПРОБЛЕМА
                        // --- Конец исправления ---
                        // Статус "Ранил!" или "Убил!" УЖЕ установлен выше и останется на экране до следующего действия
                    } else {
                        // Игра окончена, checkGameOver обработал статус победы
                        Log.d("GameActivity", "handlePlayerShot: Game is over after player's move.")
                    }

                } else {
                    // Этот случай не должен происходить, если логика расстановки и findShipsOnBoard корректны
                    Log.e("GameActivity", "handlePlayerShot: Hit at $row,$col but target ship not found in opponentShips list!")
                    Toast.makeText(this, "Ошибка игры: Попал, но не нашел корабль!", Toast.LENGTH_SHORT).show()
                    opponentBoard[row][col] = CellState.HIT // Все равно помечаем как попадание на доске
                    updateGridCells() // Обновляем UI
                    statusTextView.text = "Ошибка: Неизвестное попадание!" // Устанавливаем статус ошибки
                    // В случае ошибки, пусть ход перейдет к компьютеру, чтобы не зависнуть
                    isPlayerTurn = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        showBoard(false) // Переключаем на поле игрока (статус обновится на "Ход компьютера...")
                        computerTurn()
                    }, 1000)
                }

            } else { // Промах игрока!
                Log.d("GameActivity", "handlePlayerShot: Miss.")
                opponentBoard[row][col] = CellState.MISS
                statusTextView.text = "Промах!" // Sets the correct text
                updateGridCells() // <-- Вызываем обновление UI после промаха

                isPlayerTurn = false // Переход хода к компьютеру

                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(false) // <-- Переключаем на поле игрока (статус обновится на "Ход компьютера...")
                    // updateStatusText() // Нет необходимости, showBoard уже вызывает
                    computerTurn() // Компьютер делает свой ход
                }, 1000) // Задержка перед переключением и ходом компьютера (1 секунда)
            }
        } else { // Игра окончена или не ход игрока
            // Log.d("GameActivity", "Click on opponent board ignored. game is over or not player's turn.")
        }
    }

    // Реализует ход компьютера
    private fun computerTurn() {
        Log.d("GameActivity", "computerTurn: Starting. isPlayerTurn=$isPlayerTurn")
        if (isGameOver || isPlayerTurn) { // Проверяем, если игра окончена или сейчас не ход компьютера
            Log.d("GameActivity", "computerTurn: Skipping turn. isGameOver=$isGameOver, isPlayerTurn=$isPlayerTurn")
            return
        }

        statusTextView.text = "Ход компьютера..." // Этот статус уже установлен в showBoard(false) перед вызовом computerTurn

        var row: Int
        var col: Int
        var isValidShot: Boolean
        val random = Random(System.currentTimeMillis()) // Можно использовать Random() без аргумента

        // TODO: Реализовать более умный AI компьютера (поиск раненого корабля и т.д.)
        // Сейчас компьютер стреляет просто случайно в любую нестреляную клетку.
        do {
            row = random.nextInt(gridSize)
            col = random.nextInt(gridSize)
            val state = playerBoard[row][col]
            // Выстрел валиден, если клетка EMPTY или SHIP (не стреляли сюда раньше)
            isValidShot = state == CellState.EMPTY || state == CellState.SHIP
        } while (!isValidShot)
        Log.d("GameActivity", "computerTurn: Shot at $row,$col.")

        // Небольшая задержка для имитации "думающего" компьютера
        Handler(Looper.getMainLooper()).postDelayed({
            val targetState = playerBoard[row][col]

            if (targetState == CellState.SHIP) {
                Log.d("GameActivity", "computerTurn: Hit!")

                // Ищем корабль игрока, которому принадлежит эта ячейка
                val hitShip = playerShips.find { it.cells.contains(Pair(row, col)) }

                if (hitShip != null) {
                    hitShip.hits++
                    playerBoard[row][col] = CellState.HIT // Обновляем состояние на доске игрока

                    if (hitShip.isSunk()) {
                        statusTextView.text = "Ваш корабль ${hitShip.size} потоплен!"
                        Log.d("GameActivity", "computerTurn: Sunk player's ${hitShip.size}-палубник!")
                        // Обновляем все клетки потопленного корабля на доске на SUNK
                        for (cell in hitShip.cells) {
                            playerBoard[cell.first][cell.second] = CellState.SUNK
                        }
                        // Точки вокруг будут расставлены в updateGridCells
                    } else {
                        statusTextView.text = "Компьютер попал в ваш корабль ${hitShip.size}!"
                        Log.d("GameActivity", "computerTurn: Hit player's ${hitShip.size}-палубник!")
                    }

                    updateGridCells() // <-- Обновляем UI ПОЛЯ ИГРОКА

                    checkGameOver() // Might set isGameOver to true

                    if (!isGameOver) {
                        // Если игра не окончена и компьютер попал/убил, его ход продолжается
                        Log.d("GameActivity", "computerTurn: Computer hit/sunk, turn continues. Next computer turn scheduled.")
                        // Статус "Ваш корабль... потоплен!" или "Компьютер попал..." УЖЕ установлен выше
                        Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000) // Задержка перед следующим ходом компьютера
                    } else {
                        Log.d("GameActivity", "computerTurn: Game is over after computer's move.")
                        // Игра окончена, остаемся на поле игрока с итоговым статусом
                        // checkGameOver уже установил текст статуса
                    }

                } else {
                    // Этот случай не должен происходить
                    Log.e("GameActivity", "computerTurn: Hit at $row,$col but target ship not found in playerShips list!")
                    Toast.makeText(this, "Ошибка игры: Компьютер попал, но не нашел корабль!", Toast.LENGTH_SHORT).show()
                    playerBoard[row][col] = CellState.HIT
                    updateGridCells()
                    statusTextView.text = "Ошибка: Компьютер попал (неизвестно куда)!" // Устанавливаем статус ошибки
                    // Чтобы не зависнуть, передаем ход игроку
                    isPlayerTurn = true
                    Handler(Looper.getMainLooper()).postDelayed({ showBoard(true) }, 1000) // Переключаем на поле противника (статус обновится на "Ваш ход")
                }


            } else { // Промах компьютера!
                Log.d("GameActivity", "computerTurn: Miss.")
                playerBoard[row][col] = CellState.MISS
                statusTextView.text = "Компьютер промахнулся!" // Sets the correct text
                updateGridCells() // <-- Обновляем UI ПОЛЯ ИГРОКА

                isPlayerTurn = true // Переход хода к игроку

                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(true) // <-- Переключаем на поле противника (статус обновится на "Ваш ход")
                    // updateStatusText() // Нет необходимости, showBoard уже вызывает
                }, 1000) // Задержка перед переключением обратно (1 секунда)
            }
            Log.d("GameActivity", "computerTurn: Delayed action finished.")
        }, 1000) // Общая задержка перед выполнением выстрела компьютера (1 секунда)
        Log.d("GameActivity", "computerTurn: Finished, delayed shot scheduled.")
    }


    // Проверяет условия завершения игры
    private fun checkGameOver() {
        Log.d("GameActivity", "checkGameOver: Checking game over.")
        val allPlayerShipsSunk = playerShips.all { it.isSunk() }
        val allOpponentShipsSunk = opponentShips.all { it.isSunk() }

        if (allOpponentShipsSunk) {
            statusTextView.text = "Поздравляем! Вы победили!"
            isGameOver = true
            Log.d("GameActivity", "checkGameOver: Player WINS!")
            // TODO: Сохранить результат игры в базу данных (победа игрока)
            // TODO: Возможно, показать диалог о победе и предложить новую игру/вернуться в меню
        } else if (allPlayerShipsSunk) {
            statusTextView.text = "К сожалению, вы проиграли. Компьютер победил!"
            isGameOver = true
            Log.d("GameActivity", "checkGameOver: Computer WINS!")
            // TODO: Сохранить результат игры в базу данных (победа компьютера)
            // TODO: Возможно, показать диалог о поражении и предложить новую игру/вернуться в меню
        }
        // Если игра окончена, поле остается на том, на котором оно было в момент окончания.
        // updateStatusText() не вызывается, чтобы оставить итоговый статус.
        if (isGameOver) {
            Log.d("GameActivity", "checkGameOver: Game is over.")
        }
    }

    // Обновляет текст в TextView статуса игры (если игра не окончена)
    private fun updateStatusText() {
        if (isGameOver) {
            Log.d("GameActivity", "updateStatusText: Game is over, not updating status text.")
            return // Не обновляем статус, если игра окончена
        }
        statusTextView.text = if (isPlayerTurn) "Ваш ход" else "Ход компьютера..."
        Log.d("GameActivity", "updateStatusText: Status set to '${statusTextView.text}'")
    }

    // TODO: Добавить метод для сохранения результата игры в Room Database
    // private fun saveGameResult(winnerIsPlayer: Boolean) { ... }
}

// TODO: Если используются расширения dpToPx, убедитесь, что они объявлены в отдельном файле или здесь вне класса
// import android.content.res.Resources // Импорт, если dpToPx здесь
/*
fun Int.dpToPx(resources: Resources): Int {
    return (this * resources.displayMetrics.density).toInt()
}
*/
// Или из пакета утилит: import com.example.navalcombat.utils.dpToPx
// (Убедитесь, что пакет соответствует вашему)