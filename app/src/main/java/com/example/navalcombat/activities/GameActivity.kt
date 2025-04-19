package com.example.navalcombat.activities // Убедитесь, что пакет соответствует вашему проекту

import android.graphics.Color
import android.graphics.Typeface // Импортируем Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity // Импортируем Gravity
import android.view.LayoutInflater
import android.view.View // Импортируем View
import android.widget.LinearLayout // Импортируем LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout // Импортируем ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.gridlayout.widget.GridLayout
import com.example.navalcombat.R // Убедитесь, что пакет соответствует вашему проекту
import java.util.*
import kotlin.random.Random

// Состояния ячейки
enum class CellState {
    EMPTY, SHIP, HIT, MISS, SUNK
}

class GameActivity : AppCompatActivity() {

    // --- UI элементы ---
    private lateinit var statusTextView: TextView
    private lateinit var opponentGridView: GridLayout
    private lateinit var playerGridView: GridLayout

    // Контейнеры полей для переключения видимости
    private lateinit var opponentBoardContainer: ConstraintLayout
    private lateinit var playerBoardContainer: ConstraintLayout

    // LinearLayout для меток координат
    private lateinit var opponentColLabelsLayout: LinearLayout
    private lateinit var opponentRowLabelsLayout: LinearLayout
    private lateinit var playerColLabelsLayout: LinearLayout
    private lateinit var playerRowLabelsLayout: LinearLayout
    // --- Конец UI элементов ---


    // --- Логическая модель данных и состояние игры ---
    // Хранение ссылок на View ячеек (для быстрого доступа к UI)
    private lateinit var opponentCellViews: Array<Array<TextView?>>
    private lateinit var playerCellViews: Array<Array<TextView?>>

    // Размеры поля и метки
    private val gridSize = 10
    private val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

    // Игровые поля (логическая модель доски 10x10)
    private var playerBoard = createEmptyBoard()
    private var opponentBoard = createEmptyBoard()

    // Счетчик оставшихся "палуб" у каждого игрока (для определения конца игры)
    private var playerShipsCells = 0
    private var opponentShipCells = 0

    // Состояние игры
    private var isPlayerTurn = true
    private var isGameOver = false

    // Размеры кораблей для расстановки (стандартные правила)
    private val shipSizes = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1) // Сумма = 20 клеток
    // --- Конец логической модели и состояния ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Настройка окна для работы с системными вставками (статус бар, навигационный бар) ---
        WindowCompat.setDecorFitsSystemWindows(window, false) // Позволяет рисовать под системными барами
        // ---

        setContentView(R.layout.activity_game) // Загружаем макет activity_game.xml

        // --- Находим UI элементы по их ID ---
        statusTextView = findViewById(R.id.textViewGameStatus)
        opponentGridView = findViewById(R.id.opponentGrid)
        playerGridView = findViewById(R.id.playerGrid)
        // Находим контейнеры полей
        opponentBoardContainer = findViewById(R.id.opponentBoardContainer)
        playerBoardContainer = findViewById(R.id.playerBoardContainer)
        // Находим Layout для меток
        opponentColLabelsLayout = findViewById(R.id.opponentColLabels)
        opponentRowLabelsLayout = findViewById(R.id.opponentRowLabels)
        playerColLabelsLayout = findViewById(R.id.playerColLabels)
        playerRowLabelsLayout = findViewById(R.id.playerRowLabels)
        // --- Конец findViewById ---


        // --- Применение padding'а на основе системных вставок к корневому Layout ---
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayoutGame) // Находим корневой Layout по его ID
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            // Получаем системные вставки
            val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Применяем верхнюю и нижнюю вставки как padding к корневому Layout
            view.updatePadding(
                top = systemBarsInsets.top,
                bottom = systemBarsInsets.bottom
            )
            insets // Возвращаем вставки, чтобы они были доступны другим View
        }
        // --- Конец обработки вставок ---


        // Инициализация массивов для хранения ссылок на View ячеек
        // Выполняется здесь, после того как gridSize и View найдены/доступны
        opponentCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }

        // Настраиваем параметры GridLayout (количество строк и столбцов)
        opponentGridView.rowCount = gridSize
        opponentGridView.columnCount = gridSize
        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize

        // Создаем и добавляем TextView для меток координат (А-К и 1-10)
        createLabels()

        // Начинаем новую игру: сброс логики, расстановка кораблей, создание View ячеек
        setupGame()

        // Изначально показываем поле противника (т.к. игрок ходит первым)
        showBoard(true) // true = показать поле противника
    }

    // --- Методы игры ---

    // Создает пустую доску gridSize x gridSize
    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    // Настраивает новую игру: сброс логики, расстановка кораблей, создание/обновление View ячеек
    private fun setupGame() {
        // Сбрасываем логические доски
        playerBoard = createEmptyBoard()
        opponentBoard = createEmptyBoard()

        // Сбрасываем счетчики палуб
        playerShipsCells = shipSizes.sum()
        opponentShipCells = shipSizes.sum()

        // Сбрасываем состояние игры
        isPlayerTurn = true
        isGameOver = false

        // Расставляем корабли случайным образом для обоих игроков
        // TODO: Если вы делаете ручную расстановку игрока, эту строку нужно будет изменить/убрать
        placeShipsRandomly(playerBoard)
        placeShipsRandomly(opponentBoard)

        // Обновляем UI сетки: удаляем старые View и создаем новые с новой расстановкой
        // Проверяем, что GridLayout'ы были успешно найдены findViewById перед использованием
        if (::opponentGridView.isInitialized) {
            createGridCells(opponentGridView, opponentBoard, true) // Создаем ячейки для поля противника
        }
        if (::playerGridView.isInitialized) {
            createGridCells(playerGridView, playerBoard, false)  // Создаем ячейки для поля игрока
        }

        // Убедимся, что показано поле противника в начале новой игры
        // showBoard(true) вызывается в конце onCreate
        updateStatusText() // Обновим статус текст в соответствии с isPlayerTurn
    }

    // Расставляет корабли на доске в случайных позициях
    private fun placeShipsRandomly(board: Array<Array<CellState>>) {
        val random = Random(System.currentTimeMillis()) // Инициализируем Random для случайности
        for (size in shipSizes) {
            var placed = false
            while (!placed) {
                val row = random.nextInt(gridSize)
                val col = random.nextInt(gridSize)
                val isHorizontal = random.nextBoolean()
                if (canPlaceShip(board, row, col, size, isHorizontal)) {
                    placeShip(board, row, col, size, isHorizontal)
                    placed = true
                }
            }
        }
    }

    // Проверяет, можно ли разместить корабль заданного размера в данной позиции без пересечений
    private fun canPlaceShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean): Boolean {
        // Проверяем каждую клетку корабля и соседние (включая диагональные)
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col

            // Проверяем выход за границы поля
            if (currentRow >= gridSize || currentCol >= gridSize) return false

            // Проверяем саму клетку и 8 соседних
            for (r in (currentRow - 1)..(currentRow + 1)) {
                for (c in (currentCol - 1)..(currentCol + 1)) {
                    // Проверяем, что соседняя клетка в пределах поля
                    if (r in 0 until gridSize && c in 0 until gridSize) {
                        // Если в соседней или текущей клетке уже есть корабль, разместить нельзя
                        if (board[r][c] == CellState.SHIP) return false
                    }
                }
            }
        }
        return true // Если все проверки прошли, разместить можно
    }

    // Размещает корабль на доске
    private fun placeShip(board: Array<Array<CellState>>, row: Int, col: Int, size: Int, isHorizontal: Boolean) {
        for (i in 0 until size) {
            val currentRow = if (isHorizontal) row else row + i
            val currentCol = if (isHorizontal) col + i else col
            board[currentRow][currentCol] = CellState.SHIP // Помечаем клетки как SHIP
        }
    }


    // --- Работа с UI сетки (GridLayout и View ячеек) ---

    // Создает TextView для каждой ячейки сетки, настраивает их и добавляет в GridLayout
    // isOpponent: true для поля противника, false для поля игрока
    private fun createGridCells(grid: GridLayout, board: Array<Array<CellState>>, isOpponent: Boolean) {
        grid.removeAllViews() // Очищаем GridLayout от предыдущих View (если они были)
        val cellReferences = if (isOpponent) opponentCellViews else playerCellViews // Выбираем нужный массив ссылок

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Создаем View для одной ячейки из макета grid_cell.xml
                val cellView = LayoutInflater.from(this).inflate(R.layout.grid_cell, grid, false) as TextView

                // Настраиваем параметры расположения ячейки в GridLayout
                val params = GridLayout.LayoutParams()
                params.width = 0 // Ширина 0dp + вес 1f = равномерное распределение по ширине
                params.height = 0 // Высота 0dp + вес 1f = равномерное распределение по высоте
                params.rowSpec = GridLayout.spec(row, 1, 1f) // Расположение в строке с весом
                params.columnSpec = GridLayout.spec(col, 1, 1f) // Расположение в столбце с весом
                // Удалены маржины, чтобы граница рисовалась вплотную Drawable'ом
                // Если хотите тонкие линии МЕЖДУ ячейками, верните params.setMargins(1, 1, 1, 1)
                cellView.layoutParams = params

                // Сохраняем ссылку на созданную View в соответствующем массиве
                cellReferences[row][col] = cellView

                // Устанавливаем начальный внешний вид ячейки в соответствии с ее состоянием
                updateCellView(cellView, board[row][col], isOpponent)

                // Добавляем обработчик кликов только для поля противника (для выстрелов игрока)
                if (isOpponent) {
                    cellView.setOnClickListener {
                        // Обрабатываем выстрел, только если сейчас ход игрока и игра не окончена
                        if (!isGameOver && isPlayerTurn) {
                            handlePlayerShot(row, col)
                        }
                    }
                } else {
                    // Поле игрока не должно быть кликабельным для выстрелов
                    cellView.isClickable = false
                }

                grid.addView(cellView) // Добавляем созданную View ячейки в GridLayout
            }
        }
    }

    // Создает и добавляет TextView для меток координат (А-К и 1-10) вокруг сеток
    // Создает и добавляет TextView для меток координат (А-К и 1-10) вокруг сеток
    private fun createLabels() {
        // Используйте цвет из ресурсов (убедитесь, что R.color.purple_700 существует или замените на свой цвет)
        val labelColor = ContextCompat.getColor(this, R.color.purple_700) // Убедитесь, что цвет существует
        val labelTextSize = 14f // Размер текста меток

        // Очищаем Layout'ы меток перед добавлением новых (на случай перезапуска Activity или setupGame если бы вызывалось несколько раз)
        opponentColLabelsLayout.removeAllViews()
        opponentRowLabelsLayout.removeAllViews()
        playerColLabelsLayout.removeAllViews()
        playerRowLabelsLayout.removeAllViews()

        // Метки столбцов (А-К)
        for (i in 0 until gridSize) {
            // --- ИСПРАВЛЕНО: Создаем ДВА РАЗНЫХ TextView для меток столбцов ---
            val opponentColLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.CENTER
                }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerColLabel = TextView(this).apply { // Создаем второй TextView для поля игрока
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

            // Добавляем каждый TextView в свой уникальный родительский Layout
            opponentColLabelsLayout.addView(opponentColLabel)
            playerColLabelsLayout.addView(playerColLabel) // Добавляем метку в Layout поля игрока
        }

        // Метки строк (1-10)
        for (i in 0 until gridSize) {
            // --- ИСПРАВЛЕНО: Создаем ДВА РАЗНЫХ TextView для меток строк ---
            val opponentRowLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerRowLabel = TextView(this).apply { // Создаем второй TextView для поля игрока
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

            // Добавляем каждый TextView в свой уникальный родительский Layout
            opponentRowLabelsLayout.addView(opponentRowLabel)
            playerRowLabelsLayout.addView(playerRowLabel) // Добавляем метку в Layout поля игрока
        }
    }


    // Обновляет внешний вид одной ячейки (TextView) в зависимости от ее логического состояния
    // Использует Drawables для фона и устанавливает текст/цвет текста
    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return // Если View нет, ничего не делаем

        cellView.text = "" // Сбрасываем текст
        cellView.setTextColor(ContextCompat.getColor(this, android.R.color.transparent)) // Сбрасываем цвет текста

        // Устанавливаем Drawable в качестве фона и, если нужно, текст и его цвет
        when (state) {
            CellState.EMPTY -> cellView.setBackgroundResource(R.drawable.cell_water) // Синий фон с белой рамкой
            CellState.SHIP -> {
                if (!isOpponent) {
                    // Свои корабли видны (серый фон с белой рамкой)
                    cellView.setBackgroundResource(R.drawable.cell_ship_player)
                } else {
                    // Корабли противника скрыты под водой (синий фон с белой рамкой)
                    cellView.setBackgroundResource(R.drawable.cell_water)
                }
            }
            CellState.HIT -> {
                cellView.setBackgroundResource(R.drawable.cell_hit) // Красный фон с белой рамкой
                cellView.text = "X" // Текст "X"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.white)) // Белый текст
            }
            CellState.MISS -> {
                cellView.setBackgroundResource(R.drawable.cell_water) // Фон воды (синий с белой рамкой)
                cellView.text = "•" // Текст "точка"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark)) // КРАСНЫЙ текст точки
            }
            CellState.SUNK -> {
                // Визуализация потопленного корабля (черный фон с белой рамкой, красный "X")
                cellView.setBackgroundResource(R.drawable.cell_sunk)
                cellView.text = "X" // Текст "X"
                cellView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark)) // Красный текст "X"
                // TODO: При необходимости добавить логику обводки промахами вокруг потопленного корабля
            }
        }
        cellView.gravity = Gravity.CENTER // Центрируем любой текст внутри ячейки
    }

    // Переключает видимость между полем противника и полем игрока
    // showOpponent: true - показать поле противника, false - показать поле игрока
    private fun showBoard(showOpponent: Boolean) {
        if (showOpponent) {
            opponentBoardContainer.visibility = View.VISIBLE // Показать контейнер противника
            playerBoardContainer.visibility = View.GONE    // Скрыть контейнер игрока
        } else {
            opponentBoardContainer.visibility = View.GONE    // Скрыть контейнер противника
            playerBoardContainer.visibility = View.VISIBLE // Показать контейнер игрока
        }
        // Обновляем статус текст после переключения поля
        updateStatusText()
    }

    // Обрабатывает выстрел игрока по клетке (row, col) на поле противника
    private fun handlePlayerShot(row: Int, col: Int) {
        // Проверяем, ход ли игрока и не окончена ли игра
        if (!isPlayerTurn || isGameOver) return

        // Получаем логическое состояние и View ячейки противника
        val cellState = opponentBoard[row][col]
        val currentCellView = opponentCellViews[row][col]

        // Если в эту клетку уже стреляли, сообщаем и игрок ходит снова
        if (cellState == CellState.HIT || cellState == CellState.MISS) {
            statusTextView.text = "Сюда уже стреляли, выберите другую клетку!"
            return // Игрок не теряет ход, поле остается видимым
        }

        // Обрабатываем попадание или промах
        if (cellState == CellState.SHIP) {
            // Попадание!
            opponentBoard[row][col] = CellState.HIT // Обновляем логическую доску
            opponentShipCells-- // Уменьшаем счетчик палуб противника
            statusTextView.text = "Попадание!"
            updateCellView(currentCellView, CellState.HIT, true) // Обновляем UI ячейки

            // TODO: Добавить логику проверки, потоплен ли корабль после этого попадания

            checkGameOver() // Проверяем, не окончена ли игра после попадания
            // Если игра не окончена, игрок ходит снова. Поле противника остается видимым.
            if (!isGameOver) {
                // Опционально: небольшая задержка, чтобы игрок увидел результат выстрела перед следующим ходом
                // Handler(Looper.getMainLooper()).postDelayed({ updateStatusText() }, 500)
                // Не обновляем статус здесь, т.к. ход не меняется
            }

        } else { // Промах!
            opponentBoard[row][col] = CellState.MISS // Обновляем логическую доску
            statusTextView.text = "Промах!"
            updateCellView(currentCellView, CellState.MISS, true) // Обновляем UI ячейки

            isPlayerTurn = false // Переход хода к компьютеру
            // showBoard(false) // Показать поле игрока - это сделаем после задержки

            // Запускаем ход компьютера с небольшой задержкой после промаха игрока
            Handler(Looper.getMainLooper()).postDelayed({
                showBoard(false) // Показать поле игрока перед ходом компьютера
                computerTurn() // Компьютер делает свой ход
            }, 1000) // Задержка перед переключением поля и ходом компьютера
        }
        // updateStatusText() вызывается в showBoard() при смене поля
    }

    // Реализует ход компьютера (очень простой AI)
    private fun computerTurn() {
        // Убедимся, что это ход компьютера и игра не окончена
        if (isGameOver || isPlayerTurn) return

        // Статус "Ход компьютера..." уже установлен в showBoard(false)

        var row: Int
        var col: Int
        var isValidShot: Boolean // Флаг для проверки, что клетка еще не атакована
        val random = Random(System.currentTimeMillis())

        // Компьютер выбирает случайную клетку на поле игрока, в которую еще не стрелял
        do {
            row = random.nextInt(gridSize)
            col = random.nextInt(gridSize)
            val state = playerBoard[row][col]
            isValidShot = state != CellState.HIT && state != CellState.MISS // Выбираем только EMPTY или SHIP
        } while (!isValidShot) // Повторяем, пока не найдем допустимую клетку

        // Получаем состояние выбранной клетки на логической доске игрока и соответствующую View
        val targetState = playerBoard[row][col]
        val targetCellView = playerCellViews[row][col]

        // Задержка для имитации "раздумий" компьютера перед выстрелом
        Handler(Looper.getMainLooper()).postDelayed({
            // Выполняем действия после задержки
            if (targetState == CellState.SHIP) { // Попадание компьютера!
                playerBoard[row][col] = CellState.HIT // Обновляем логическую доску игрока
                playerShipsCells-- // Уменьшаем счетчик палуб игрока
                statusTextView.text = "Компьютер попал по вашему кораблю!"
                updateCellView(targetCellView, CellState.HIT, false) // Обновляем View ячейки игрока

                // TODO: Добавить логику проверки, потоплен ли корабль игрока

                checkGameOver() // Проверяем конец игры

                if (!isGameOver) {
                    // Если игра не окончена, компьютер ходит снова (т.к. попал).
                    // Поле игрока остается видимым. Запускаем его следующий ход с задержкой.
                    Handler(Looper.getMainLooper()).postDelayed({ computerTurn() }, 1000) // Задержка перед следующим ходом компьютера
                }
            } else { // Промах компьютера!
                playerBoard[row][col] = CellState.MISS // Обновляем логическую доску игрока
                statusTextView.text = "Компьютер промахнулся!"
                updateCellView(targetCellView, CellState.MISS, false) // Обновляем View ячейки игрока

                isPlayerTurn = true // Переход хода к игроку
                // showBoard(true) // Показать поле противника - сделаем после задержки

                // Переключаем на поле противника для хода игрока с задержкой
                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(true) // Показать поле противника
                }, 1000) // Задержка перед переключением обратно на поле противника
            }
        }, 1000) // Задержка перед выстрелом компьютера
    }


    // Проверяет условия завершения игры (у кого закончились корабли)
    private fun checkGameOver() {
        if (opponentShipCells <= 0) {
            statusTextView.text = "Поздравляем! Вы победили!"
            isGameOver = true
            // TODO: Сохранить результат игры в базу данных (победа игрока)
            // TODO: Возможно, показать диалог о победе и предложить новую игру/вернуться в меню
        } else if (playerShipsCells <= 0) {
            statusTextView.text = "К сожалению, вы проиграли. Компьютер победил!"
            isGameOver = true
            // TODO: Сохранить результат игры в базу данных (победа компьютера)
            // TODO: Возможно, показать диалог о поражении и предложить новую игру/вернуться в меню
        }
        // Если игра окончена, поля остаются на том, на каком они были в момент окончания.
        // updateStatusText() вызывается в конце хода или при переключении поля.
    }

    // Обновляет текст в TextView статуса игры в зависимости от текущего хода
    // (Метки полей теперь управляются видимостью контейнеров)
    private fun updateStatusText() {
        if (isGameOver) return // Не обновляем, если игра окончена
        // Текст статуса просто показывает, чей ход. Метка поля видна благодаря showBoard().
        statusTextView.text = if (isPlayerTurn) "Ваш ход" else "Ход компьютера..."
    }

    // TODO: Добавить метод для сохранения результата игры в Room Database
    // private fun saveGameResult(winnerIsPlayer: Boolean) { ... } // Нужен доступ к Dao и запуск в другом потоке
}