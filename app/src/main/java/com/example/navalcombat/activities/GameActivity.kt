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
import com.example.navalcombat.model.CellState // Импортируем из game.model
import com.example.navalcombat.model.Ship // Импортируем класс Ship
import com.example.navalcombat.model.ShipToPlace // Импортируем из game.model (хотя он не нужен в GameActivity)
// import com.example.navalcombat.utils.dpToPx // Если создавали utils пакет, импортируйте оттуда
import java.io.Serializable
import java.util.Stack // Импортируем Stack для поиска кораблей
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
    // --- ИЗМЕНЕНО: Удалена кнопка Назад ---
    // private lateinit var buttonUndoLastShot: Button
    // --- Конец изменений ---
    private lateinit var buttonStartBattle: Button // Кнопка Начать бой (скрыта во время игры)
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

    private val gridSize = 10 // <-- Значение должно быть 10

    private val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")
    private val shipSizes = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1) // Размеры для расстановки

    // --- ИЗМЕНЕНО: Удалено состояние для отмены хода ---
    // private var savedOpponentBoardState: Array<Array<CellState>>? = null
    // private var savedOpponentShipsState: MutableList<Ship>? = null
    // private var wasPlayerTurnBeforeShot: Boolean = true
    // --- Конец изменений ---


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
        // --- ИЗМЕНЕНО: Удален поиск кнопки Назад ---
        // private lateinit var buttonUndoLastShot: Button // <-- Удалена декларация выше
        // buttonUndoLastShot = findViewById(R.id.buttonUndoLastShot) // <-- Удален поиск
        // --- Конец изменений ---
        buttonStartBattle = findViewById(R.id.buttonStartBattle) // Кнопка Начать бой (скрыта во время игры)
        // Log.d("GameActivity", "onCreate: Buttons found: Undo=${buttonUndoLastShot!=null}, StartBattle=${buttonStartBattle!=null}") // Удален лог
        // --- Конец findViewById ---

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayoutGame) // Используем LinearLayout как корневой в этом макете
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


        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize
        opponentGridView.rowCount = gridSize // Убедимся, что оба GridLayout настроены
        opponentGridView.columnCount = gridSize // Убедимся, что оба GridLayout настроены
        Log.d("GameActivity", "onCreate: GridLayouts row/col count set.")

        createLabels() // <-- Вызывается ОДИН РАЗ для создания всех меток
        Log.d("GameActivity", "onCreate: Labels created.")


        // --- Получаем расстановку игрока из Intent и создаем объекты Ship ---
        val receivedPlayerBoard = intent.getSerializableExtra("playerBoard") as? Array<Array<CellState>>
        if (receivedPlayerBoard != null) {
            playerBoard = receivedPlayerBoard // Устанавливаем доску игрока из Intent
            playerShips = findShipsOnBoard(playerBoard) // Находим и создаем объекты Ship на основе полученной доски
            Log.d("GameActivity", "onCreate: Player board received from Intent. Found ${playerShips.size} ships.")
            setupGame() // Запускаем остальную настройку игры (расстановка компа)
        } else {
            // Fallback: Если доска не получена (не должно происходить при запуске из SetupActivity)
            Log.e("GameActivity", "onCreate: Player board NOT received from Intent! Using random placement for player.")
            Toast.makeText(this, "Ошибка получения расстановки. Случайная расстановка.", Toast.LENGTH_LONG).show()
            playerBoard = createEmptyBoard()
            playerShips = placeShipsRandomlyAndCreateObjects(playerBoard) // Случайная расстановка и создание Ship
            Log.d("GameActivity", "onCreate: Fallback random placement for player. Found ${playerShips.size} ships.")
            setupGame() // Запускаем остальную настройку
        }
        // --- Конец получения данных из Intent ---

        // --- Создаем View ячеек для обоих полей ОДИН РАЗ ---
        createGridCells(opponentGridView, opponentBoard, true) // <-- Вызывается ОДИН РАЗ для противника
        createGridCells(playerGridView, playerBoard, false) // <-- Вызывается ОДИН РАЗ для игрока
        Log.d("GameActivity", "onCreate: Grid cells created for both boards.")


        // Изначально показываем поле противника и обновляем его UI
        showBoard(true) // true = показать поле противника. Это также вызовет updateStatusText и updateGridCells
        Log.d("GameActivity", "onCreate: Initial board set to opponent.")

        // --- ИЗМЕНЕНО: Удален слушатель кнопки Назад и активация ---
        // buttonUndoLastShot.setOnClickListener { undoLastPlayerShot() }
        // buttonUndoLastShot.isEnabled = false
        // --- Конец изменений ---

        Log.d("GameActivity", "onCreate: Finished.")
    }

    // --- Методы игры ---

    // Настраивает новую игру: расстановка кораблей противника
    private fun setupGame() {
        Log.d("GameActivity", "setupGame: Starting.")
        // playerBoard и playerShips уже установлены в onCreate
        opponentBoard = createEmptyBoard() // Создаем пустую доску для противника

        isPlayerTurn = true // Игрок всегда начинает
        isGameOver = false

        opponentShips = placeShipsRandomlyAndCreateObjects(opponentBoard) // Случайная расстановка и создание Ship
        Log.d("GameActivity", "setupGame: Opponent ships placed. Found ${opponentShips.size} ships.")

        Log.d("GameActivity", "setupGame: Finished.")
    }


    // Создает пустую доску gridSize x gridSize, заполненную CellState.EMPTY
    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    // --- Метод для НАХОЖДЕНИЯ кораблей на логической доске и СОЗДАНИЯ объектов Ship ---
    // Используется для доски игрока, полученной из SetupActivity, и для восстановления Ship объектов противника
    // Обновлен для правильного подсчета попаданий при восстановлении из доски
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
    private fun placeShipsRandomlyAndCreateObjects(board: Array<Array<CellState>>): MutableList<Ship> {
        Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Starting.")
        val ships = mutableListOf<Ship>()
        val random = Random(System.currentTimeMillis())

        val shipSizesCopy = shipSizes.toMutableList() // <-- shipSizes определена как свойство класса

        // Очищаем доску перед расстановкой
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                board[r][c] = CellState.EMPTY
            }
        }

        for (size in shipSizesCopy) {
            var placed = false
            var attempts = 0
            val maxAttempts = 1000

            while (!placed && attempts < maxAttempts) {
                val row = random.nextInt(gridSize)
                val col = random.nextInt(gridSize)
                val isHorizontal = random.nextBoolean()

                val tempBoard = Array(gridSize) { r -> Array(gridSize) { c -> board[r][c] } }
                val shipCells = mutableListOf<Pair<Int, Int>>()

                var canPlace = true
                for (i in 0 until size) {
                    val r = if (isHorizontal) row else row + i
                    val c = if (isHorizontal) col + i else col

                    if (r < 0 || r >= gridSize || c < 0 || c >= gridSize) { canPlace = false; break }

                    for (sr in (r - 1)..(r + 1)) {
                        for (sc in (c - 1)..(c + 1)) {
                            if (sr >= 0 && sr < gridSize && sc >= 0 && sc < gridSize) {
                                if (tempBoard[sr][sc] == CellState.SHIP) { canPlace = false; break }
                            }
                        }
                        if (!canPlace) break
                    }
                    if (!canPlace) break

                    tempBoard[r][c] = CellState.SHIP
                    shipCells.add(Pair(r, c))
                }

                if (canPlace) {
                    for (cell in shipCells) {
                        board[cell.first][cell.second] = CellState.SHIP
                    }
                    ships.add(Ship(size, shipCells)) // Создаем объект Ship с 0 попаданий
                    placed = true
                }
                attempts++
            }
            if (!placed) {
                Log.e("GameActivity", "placeShipsRandomlyAndCreateObjects: Failed to place ship $size randomly after $maxAttempts attempts.")
            }
        }
        Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Finished. Created ${ships.size} ships.")
        return ships
    }


    // Проверяет, можно ли разместить корабль (используется в random placement)
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

    // Размещает корабль на логической доске (используется в random placement)
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

        Log.d("GameActivity", "createGridCells: Before filling, cellReferences.size = ${cellReferences.size}")

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
                    Log.e("GameActivity", "FATAL ERROR: Index outside bounds of cellReferences during creation (should not happen): isOpponent=$isOpponent, row=$row, col=$col.")
                    throw IndexOutOfBoundsException("Cell references array size mismatch during creation for isOpponent=$isOpponent")
                }
                // --- Конец заполнения массива ---

                // updateCellView(cellView, board[row][col], isOpponent) // <-- НЕ ВЫЗЫВАЕМ ЗДЕСЬ!

                // Обработка клика (только для поля противника)
                if (isOpponent) {
                    cellView.setOnClickListener {
                        if (!isGameOver && isPlayerTurn) { // Проверяем ход и конец игры
                            // --- ИЗМЕНЕНО: Удален вызов сохранения состояния ---
                            // saveStateBeforePlayerShot() // <-- Удален вызов сохранения
                            // --- Конец изменения ---
                            handlePlayerShot(row, col)
                        } else {
                            // Если не ход игрока или игра окончена, но клик по полю противника
                            Log.d("GameActivity", "Click on opponent board ignored. isPlayerTurn=$isPlayerTurn, isGameOver=$isGameOver")
                        }
                    }
                } else {
                    cellView.isClickable = false // Поле игрока не кликабельно
                }

                grid.addView(cellView) // Добавляем View в GridLayout
            }
        }
        Log.d("GameActivity", "createGridCells: Finished filling grid with views for isOpponent=$isOpponent.")
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

                    // --- ВАЖНО: Проверяем, является ли ячейка частью ПОТОПЛЕННОГО корабля ---
                    // Определяем фактическое состояние для отображения (например, HIT vs SUNK)
                    var actualState = state
                    // Проверяем на sunk только если базовое состояние SHIP, HIT или SUNK
                    if (actualState == CellState.SHIP || actualState == CellState.HIT || actualState == CellState.SUNK) {
                        // Ищем корабль, которому принадлежит эта ячейка с координатами [row, col]
                        val ship = currentShips.find { it.cells.contains(Pair(row, col)) }
                        if (ship?.isSunk() == true) {
                            // Если корабль найден и он потоплен, меняем состояние для отображения
                            actualState = CellState.SUNK
                        } else if (actualState == CellState.SUNK) {
                            // Если базовое состояние было SUNK, но корабль по логике НЕ SUNK (например, после отмены)
                            // то фактическое состояние должно быть HIT, если ячейка действительно была HIT
                            // Проверяем, является ли ячейка частью какого-либо корабля И была ли она HIT на доске
                            // !!! ИСПРАВЛЕНО: Используем currentBoard для проверки HIT/SUNK
                            val partOfHitShip = currentShips.any { s -> s.cells.contains(Pair(row, col)) } && (currentBoard[row][col] == CellState.HIT || currentBoard[row][col] == CellState.SUNK) // <-- ИСПРАВЛЕНО: Используем currentBoard
                            if(partOfHitShip) {
                                actualState = CellState.HIT // Если это часть раненого корабля на доске
                            } else {
                                // Иначе это ошибка в логике состояния доски/кораблей
                                actualState = CellState.EMPTY // Показываем как воду, чтобы не вводить в заблуждение
                                Log.e("GameActivity", "updateGridCells: Found unexpected CellState.SUNK at $row,$col but ship is not SUNK and board state is not HIT/SUNK. Board state logic error.")
                            }
                        }
                    }
                    // --- Конец проверки на потопление ---

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
        Log.d("GameActivity", "placeMissMarksAroundSunkShips: Starting for isOpponentBoard=$isOpponentBoard.")
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
                                        // Обновляем логическую доску только если это пустая вода
                                        if (board[r][c] == CellState.EMPTY) { // Двойная проверка
                                            board[r][c] = CellState.MISS // Помечаем на логической доске как промах
                                            // Обновляем UI ячейки (используем флаг isOpponentBoard из updateGridCells)
                                            updateCellView(cellView, CellState.MISS, isOpponentBoard)
                                        }
                                    } ?: run {
                                        Log.e("GameActivity", "View ячейки отсутствует в cellViews по адресу: isOpponentBoard=$isOpponentBoard, row=$r, col=$c during placing miss marks.")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Log.d("GameActivity", "placeMissMarksAroundSunkShips: Finished.")
    }


    // Обновляет внешний вид одной ячейки (TextView) в зависимости от ее логического состояния
    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return

        cellView.text = ""
        cellView.setTextColor(ContextCompat.getColor(this, android.R.color.transparent))

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
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
            CellState.MISS -> {
                cellView.setBackgroundResource(R.drawable.cell_water) // Фон воды
                cellView.text = "•" // Текст "точка"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark)) // КРАСНЫЙ текст точки
            }
            CellState.SUNK -> {
                cellView.setBackgroundResource(R.drawable.cell_sunk) // Черный фон
                cellView.text = "X" // Текст "X"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark)) // Красный текст "X"
            }
        }
        cellView.gravity = Gravity.CENTER
    }

    // Создает и добавляет TextView для меток координат (А-К и 1-10)
    private fun createLabels() {
        val labelColor = ContextCompat.getColor(this, R.color.purple_700)
        val labelTextSize = 14f

        opponentColLabelsLayout.removeAllViews() // Для противника
        opponentRowLabelsLayout.removeAllViews() // Для противника
        playerColLabelsLayout.removeAllViews() // Для игрока
        playerRowLabelsLayout.removeAllViews() // Для игрока

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
            opponentBoardContainer.visibility = View.GONE // Исправлен порядок скрытия/показа
        }
        // Обновляем статус текст и UI поля после переключения
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
                val hitShip = opponentShips.find { it.cells.contains(Pair(row, col)) }
                hitShip?.let { ship ->
                    ship.hits++

                    opponentBoard[row][col] = CellState.HIT

                    if (ship.isSunk()) {
                        statusTextView.text = "Убил!"
                        Log.d("GameActivity", "handlePlayerShot: Sunk a ${ship.size}-палубник!")
                        for (cell in ship.cells) {
                            opponentBoard[cell.first][cell.second] = CellState.SUNK
                        }
                        // Точки вокруг будут расставлены в updateGridCells
                    } else {
                        statusTextView.text = "Ранил!"
                        Log.d("GameActivity", "handlePlayerShot: Hit a ${ship.size}-палубник!")
                    }

                    updateGridCells() // <-- Вызываем обновление UI ПОЛЯ ПРОТИВНИКА

                    checkGameOver()

                    if (!isGameOver) {
                        Log.d("GameActivity", "handlePlayerShot: Player hit, turn continues.")
                    }

                } ?: run {
                    Log.e("GameActivity", "handlePlayerShot: Hit but target ship not found in opponentShips list!")
                    Toast.makeText(this, "Ошибка игры: Попал, но не нашел корабль!", Toast.LENGTH_SHORT).show()
                    opponentBoard[row][col] = CellState.HIT // Все равно помечаем как попадание
                    updateGridCells() // Обновляем UI
                }


            } else { // Промах
                Log.d("GameActivity", "handlePlayerShot: Miss.")
                opponentBoard[row][col] = CellState.MISS
                statusTextView.text = "Промах!"
                updateGridCells() // <-- Вызываем обновление UI после промаха

                isPlayerTurn = false // Переход хода к компьютеру

                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(false) // <-- Переключаем на поле игрока
                    computerTurn() // Компьютер делает свой ход
                }, 1000) // Задержка перед переключением и ходом компьютера
            }
        } else { // Игра окончена
            Log.d("GameActivity", "Click on opponent board ignored. game is over.")
        }
    }

    // Реализует ход компьютера
    private fun computerTurn() {
        Log.d("GameActivity", "computerTurn: Starting. isPlayerTurn=$isPlayerTurn")
        if (isGameOver || isPlayerTurn) { // Проверяем ход и конец игры
            Log.d("GameActivity", "computerTurn: Skipping turn, game over or not computer's turn.")
            return
        }

        statusTextView.text = "Ход компьютера..." // Этот статус уже установлен в showBoard(false)

        var row: Int
        var col: Int
        var isValidShot: Boolean
        val random = Random(System.currentTimeMillis())

        do {
            row = random.nextInt(gridSize)
            col = random.nextInt(gridSize)
            val state = playerBoard[row][col]
            isValidShot = state != CellState.HIT && state != CellState.MISS && state != CellState.SUNK
        } while (!isValidShot)
        Log.d("GameActivity", "computerTurn: Shot at $row,$col.")


        Handler(Looper.getMainLooper()).postDelayed({
            val targetState = playerBoard[row][col]

            if (targetState == CellState.SHIP) {
                Log.d("GameActivity", "computerTurn: Hit!")

                val hitShip = playerShips.find { it.cells.contains(Pair(row, col)) }
                hitShip?.let { ship ->
                    ship.hits++

                    playerBoard[row][col] = CellState.HIT

                    if (ship.isSunk()) {
                        statusTextView.text = "Ваш корабль ${ship.size} потоплен!"
                        Log.d("GameActivity", "computerTurn: Sunk player's ${ship.size}-палубник!")
                        for (cell in ship.cells) {
                            playerBoard[cell.first][cell.second] = CellState.SUNK
                        }
                        // Точки вокруг будут расставлены в updateGridCells
                    } else {
                        statusTextView.text = "Компьютер попал в ваш корабль ${ship.size}!"
                        Log.d("GameActivity", "computerTurn: Hit player's ${ship.size}-палубник!")
                    }

                    updateGridCells() // <-- Обновляем UI ПОЛЯ ИГРОКА

                    checkGameOver()

                    if (!isGameOver) {
                        Log.d("GameActivity", "computerTurn: Computer hit, turn continues.")
                        Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000)
                    } else {
                        // Игра окончена
                    }

                } ?: run {
                    Log.e("GameActivity", "computerTurn: Hit but target ship not found in playerShips list!")
                    Toast.makeText(this, "Ошибка игры: Компьютер попал, но не нашел корабль!", Toast.LENGTH_SHORT).show()
                    playerBoard[row][col] = CellState.HIT
                    updateGridCells()
                }


            } else { // Промах компьютера!
                Log.d("GameActivity", "computerTurn: Miss.")
                playerBoard[row][col] = CellState.MISS
                statusTextView.text = "Компьютер промахнулся!"
                updateGridCells() // <-- Обновляем UI ПОЛЯ ИГРОКА

                isPlayerTurn = true // Переход хода к игроку

                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(true) // <-- Переключаем на поле противника
                }, 1000) // Задержка перед переключением обратно
            }
            Log.d("GameActivity", "computerTurn: Delayed action finished.")
        }, 1000)
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
        // updateStatusText() вызывается в showBoard.
        if (isGameOver) {
            Log.d("GameActivity", "checkGameOver: Game is over.")
        }
    }

    // Обновляет текст в TextView статуса игры
    private fun updateStatusText() {
        if (isGameOver) return
        statusTextView.text = if (isPlayerTurn) "Ваш ход" else "Ход компьютера..."
        Log.d("GameActivity", "updateStatusText: Status set to '${statusTextView.text}'")
    }

    // --- ИЗМЕНЕНО: Удалена логика отмены хода ---
    // private fun saveStateBeforePlayerShot() { ... }
    // private fun undoLastPlayerShot() { ... }
    // --- Конец изменений ---


    // TODO: Добавить метод для сохранения результата игры в Room Database
    // private fun saveGameResult(winnerIsPlayer: Boolean) { ... }
}

// --- Extension функция для конвертации dp в px ---
// Эта функция ДОЛЖНА БЫТЬ ВНЕ КЛАССА GameActivity.
// Если у вас есть отдельный файл утилит, поместите ее туда и импортируйте.
/*
import android.content.res.Resources // Импортируем Resources здесь, если функция тут

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