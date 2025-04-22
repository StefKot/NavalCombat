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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.navalcombat.data.AppDatabase
import com.example.navalcombat.data.GameResultDao
import com.example.navalcombat.data.GameResultEntity

import java.io.Serializable
import java.util.Stack
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var opponentGridView: GridLayout
    private lateinit var playerGridView: GridLayout
    private lateinit var opponentBoardContainer: ConstraintLayout
    private lateinit var playerBoardContainer: ConstraintLayout
    private lateinit var opponentColLabelsLayout: LinearLayout
    private lateinit var opponentRowLabelsLayout: LinearLayout
    private lateinit var playerColLabelsLayout: LinearLayout
    private lateinit var playerRowLabelsLayout: LinearLayout

    private lateinit var gameOverButtonContainer: LinearLayout
    private lateinit var buttonPlayAgain: Button
    private lateinit var buttonToMenu: Button

    private lateinit var opponentCellViews: Array<Array<TextView?>>
    private lateinit var playerCellViews: Array<Array<TextView?>>

    private var playerBoard = createEmptyBoard()
    private var opponentBoard = createEmptyBoard()

    private var playerShips = mutableListOf<Ship>()
    private var opponentShips = mutableListOf<Ship>()

    private var isPlayerTurn = true
    private var isGameOver = false

    private val gridSize = 10

    private val shipSizes =
        listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1)

    private lateinit var gameResultDao: GameResultDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("GameActivity", "onCreate: Started.")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_game)

        statusTextView = findViewById(R.id.textViewGameStatus)
        opponentGridView = findViewById(R.id.opponentGrid)
        playerGridView = findViewById(R.id.playerGrid)
        opponentBoardContainer = findViewById(R.id.opponentBoardContainer)
        playerBoardContainer = findViewById(R.id.playerBoardContainer)
        opponentColLabelsLayout = findViewById(R.id.opponentColLabels)
        opponentRowLabelsLayout = findViewById(R.id.opponentRowLabels)
        playerColLabelsLayout = findViewById(R.id.playerColLabels)
        playerRowLabelsLayout = findViewById(R.id.playerRowLabels)

        gameOverButtonContainer = findViewById(R.id.gameOverButtonContainer)
        buttonPlayAgain = findViewById(R.id.buttonPlayAgain)
        buttonToMenu = findViewById(R.id.buttonToMenu)

        buttonPlayAgain.setOnClickListener {
            Log.d("GameActivity", "Play Again button clicked. Restarting SetupActivity.")
            val intent = Intent(this, SetupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        buttonToMenu.setOnClickListener {
            Log.d("GameActivity", "To Menu button clicked. Going back to MainActivity.")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayoutGame)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBarsInsets =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBarsInsets.top, bottom = systemBarsInsets.bottom)
            insets
        }

        gameResultDao = AppDatabase.getDatabase(applicationContext).gameResultDao()
        Log.d("GameActivity", "onCreate: Room Database and DAO initialized.")

        Log.d("GameActivity", "onCreate: Initializing CellViews arrays with size $gridSize")
        opponentCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        playerCellViews = Array(gridSize) { arrayOfNulls<TextView>(gridSize) }
        Log.d("GameActivity", "onCreate: CellViews arrays initialized.")

        opponentGridView.rowCount = gridSize
        opponentGridView.columnCount = gridSize
        playerGridView.rowCount = gridSize
        playerGridView.columnCount = gridSize
        Log.d("GameActivity", "onCreate: GridLayouts row/col count set.")

        createLabels()
        Log.d("GameActivity", "onCreate: Labels created.")

        val receivedPlayerBoard =
            intent.getSerializableExtra("playerBoard") as? Array<Array<CellState>>
        if (receivedPlayerBoard != null) {
            playerBoard = receivedPlayerBoard
            playerShips =
                findShipsOnBoard(playerBoard)
            Log.d(
                "GameActivity",
                "onCreate: Player board received from Intent. Found ${playerShips.size} ships."
            )
        } else {
            Log.e(
                "GameActivity",
                "onCreate: Player board NOT received from Intent! Using random placement for player."
            )
            Toast.makeText(
                this,
                "Ошибка получения расстановки. Случайная расстановка.",
                Toast.LENGTH_LONG
            ).show()
            playerBoard = createEmptyBoard()
            playerShips =
                placeShipsRandomlyAndCreateObjects(playerBoard)
            Log.d(
                "GameActivity",
                "onCreate: Fallback random placement for player. Found ${playerShips.size} ships."
            )
        }

        setupGame()

        createGridCells(
            opponentGridView,
            opponentBoard,
            true
        )
        createGridCells(playerGridView, playerBoard, false)
        Log.d("GameActivity", "onCreate: Grid cells created for both boards.")

        showBoard(true)
        Log.d("GameActivity", "onCreate: Initial board set to opponent.")

        Log.d("GameActivity", "onCreate: Finished.")
    }

    private fun setupGame() {
        Log.d("GameActivity", "setupGame: Starting.")

        opponentBoard = createEmptyBoard()
        opponentShips =
            placeShipsRandomlyAndCreateObjects(opponentBoard)
        Log.d(
            "GameActivity",
            "setupGame: Opponent ships placed. Found ${opponentShips.size} ships."
        )

        isPlayerTurn = true
        isGameOver = false

        Log.d("GameActivity", "setupGame: Finished.")
    }


    private fun createEmptyBoard(): Array<Array<CellState>> {
        return Array(gridSize) { Array(gridSize) { CellState.EMPTY } }
    }

    private fun findShipsOnBoard(board: Array<Array<CellState>>): MutableList<Ship> {
        Log.d("GameActivity", "findShipsOnBoard: Starting.")
        val ships = mutableListOf<Ship>()
        val visitedCells = Array(gridSize) { BooleanArray(gridSize) }

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                if ((board[row][col] == CellState.SHIP || board[row][col] == CellState.HIT || board[row][col] == CellState.SUNK)
                    && !visitedCells[row][col]
                ) {

                    val shipCells = mutableListOf<Pair<Int, Int>>()
                    val stack = Stack<Pair<Int, Int>>()

                    stack.push(Pair(row, col))
                    visitedCells[row][col] = true

                    var currentHits = 0

                    while (stack.isNotEmpty()) {
                        val currentCell = stack.pop()
                        shipCells.add(currentCell)

                        if (board[currentCell.first][currentCell.second] == CellState.HIT || board[currentCell.first][currentCell.second] == CellState.SUNK) {
                            currentHits++
                        }

                        val neighbors = listOf(
                            Pair(currentCell.first - 1, currentCell.second),
                            Pair(currentCell.first + 1, currentCell.second),
                            Pair(currentCell.first, currentCell.second - 1),
                            Pair(currentCell.first, currentCell.second + 1)
                        )

                        for (neighbor in neighbors) {
                            val r = neighbor.first
                            val c = neighbor.second

                            if (r >= 0 && r < gridSize && c >= 0 && c < gridSize &&
                                (board[r][c] == CellState.SHIP || board[r][c] == CellState.HIT || board[r][c] == CellState.SUNK)
                                && !visitedCells[r][c]
                            ) {
                                stack.push(Pair(r, c))
                                visitedCells[r][c] = true
                            }
                        }
                    }

                    val shipSize = shipCells.size
                    if (shipSize > 0) {
                        ships.add(Ship(shipSize, shipCells.toList(), currentHits))
                    }
                }
            }
        }
        Log.d("GameActivity", "findShipsOnBoard: Finished. Found ${ships.size} ships.")
        return ships
    }

    private fun placeShipsRandomlyAndCreateObjects(board: Array<Array<CellState>>): MutableList<Ship> {
        Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Starting.")
        val ships = mutableListOf<Ship>()
        val random =
            Random(System.currentTimeMillis())

        val shipSizesCopy = shipSizes.toMutableList()

        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                board[r][c] = CellState.EMPTY
            }
        }
        Log.d("GameActivity", "placeShipsRandomlyAndCreateObjects: Board cleared.")

        for (size in shipSizesCopy) {
            var placed = false
            var attempts = 0
            val maxAttempts = 5000

            Log.d(
                "GameActivity",
                "placeShipsRandomlyAndCreateObjects: Attempting to place ship of size $size..."
            )

            while (!placed && attempts < maxAttempts) {
                val row = random.nextInt(gridSize)
                val col = random.nextInt(gridSize)
                val isHorizontal = random.nextBoolean()

                var canPlace = true
                val potentialShipCells =
                    mutableListOf<Pair<Int, Int>>()

                val checkRowStart = row - 1
                val checkRowEnd =
                    if (isHorizontal) row + 1 else row + size
                val checkColStart = col - 1
                val checkColEnd =
                    if (isHorizontal) col + size else col + 1

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
                    for (rCheck in checkRowStart..checkRowEnd) {
                        for (cCheck in checkColStart..checkColEnd) {
                            if (rCheck >= 0 && rCheck < gridSize && cCheck >= 0 && cCheck < gridSize) {
                                if (board[rCheck][cCheck] != CellState.EMPTY) {
                                    canPlace = false
                                    break
                                }
                            }
                        }
                        if (!canPlace) break
                    }

                    if (canPlace) {
                        for (i in 0 until size) {
                            val shipRow = if (isHorizontal) row else row + i
                            val shipCol = if (isHorizontal) col + i else col
                            potentialShipCells.add(Pair(shipRow, shipCol))
                        }
                        if (potentialShipCells.size != size) {
                            Log.e(
                                "GameActivity",
                                "Internal logic error: potentialShipCells size mismatch after successful placement check."
                            )
                            canPlace =
                                false
                        }
                    }

                } else {
                    canPlace = false
                }

                if (canPlace) {
                    for (cell in potentialShipCells) {
                        board[cell.first][cell.second] = CellState.SHIP
                    }
                    ships.add(Ship(size, potentialShipCells))
                    placed = true
                    Log.d(
                        "GameActivity",
                        "placeShipsRandomlyAndCreateObjects: Successfully placed ship size $size at ($row,$col) isHorizontal=$isHorizontal."
                    )
                } else {
                    attempts++
                }
            }
            if (!placed) {
                Log.e(
                    "GameActivity",
                    "placeShipsRandomlyAndCreateObjects: FATAL ERROR: Failed to place ship $size randomly after $maxAttempts attempts."
                )
                Toast.makeText(
                    this,
                    "Ошибка при расстановке кораблей компьютера. Игра невозможна.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return mutableListOf()
            }
        }
        Log.d(
            "GameActivity",
            "placeShipsRandomlyAndCreateObjects: Finished placing ships. Created ${ships.size} ships."
        )
        if (ships.size != shipSizes.size) {
            Log.e(
                "GameActivity",
                "placeShipsRandomlyAndCreateObjects: ERROR: Placed ${ships.size} ships, expected ${shipSizes.size}!"
            )
            Toast.makeText(
                this,
                "Ошибка при расстановке кораблей компьютера. Не все корабли размещены.",
                Toast.LENGTH_LONG
            ).show()
        }
        return ships
    }

    private fun createGridCells(
        grid: GridLayout,
        board: Array<Array<CellState>>,
        isOpponent: Boolean
    ) {
        Log.d("GameActivity", "createGridCells: Starting for isOpponent=$isOpponent.")
        grid.removeAllViews()
        val cellReferences = if (isOpponent) opponentCellViews else playerCellViews

        if (cellReferences.isEmpty() || gridSize == 0 || cellReferences.size != gridSize || (gridSize > 0 && cellReferences[0] == null) || (gridSize > 0 && cellReferences[0]!!.size != gridSize)) {
            Log.e(
                "GameActivity",
                "createGridCells: FATAL ERROR: cellReferences array size mismatch or null when called. isOpponent=$isOpponent, gridSize=$gridSize, array size=${cellReferences.size}"
            )
            throw IllegalStateException("Cell references array size mismatch or null when createGridCells is called for isOpponent=$isOpponent")
        }


        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cellView =
                    LayoutInflater.from(this).inflate(R.layout.grid_cell, grid, false) as TextView

                val params = GridLayout.LayoutParams()
                params.width = 0
                params.height = 0
                params.rowSpec = GridLayout.spec(row, 1, 1f)
                params.columnSpec = GridLayout.spec(col, 1, 1f)
                cellView.layoutParams = params

                if (row < cellReferences.size && col < (cellReferences.getOrNull(row)?.size ?: 0)) {
                    cellReferences[row][col] = cellView
                } else {
                    Log.e(
                        "GameActivity",
                        "FATAL ERROR: Index outside bounds of cellReferences during creation: isOpponent=$isOpponent, row=$row, col=$col."
                    )
                    throw IndexOutOfBoundsException("Cell references array size mismatch during creation for isOpponent=$isOpponent")
                }

                if (isOpponent) {
                    cellView.setOnClickListener {
                        if (!isGameOver && isPlayerTurn) {
                            handlePlayerShot(row, col)
                        } else {
                            Log.d("GameActivity", "Click on opponent board ignored. isPlayerTurn=$isPlayerTurn, isGameOver=$isGameOver")
                        }
                    }
                } else {
                    cellView.isClickable = false
                }

                grid.addView(cellView)
            }
        }
        Log.d(
            "GameActivity",
            "createGridCells: Finished filling grid with views for isOpponent=$isOpponent. Total views: ${grid.childCount}"
        )
    }

    private fun updateGridCells() {
        Log.d("GameActivity", "updateGridCells: Starting.")
        val isOpponentBoardVisible = opponentBoardContainer.visibility == View.VISIBLE
        Log.d("GameActivity", "updateGridCells: isOpponentBoardVisible = $isOpponentBoardVisible")

        val currentBoard = if (isOpponentBoardVisible) opponentBoard else playerBoard
        val cellViews = if (isOpponentBoardVisible) opponentCellViews else playerCellViews
        val currentShips =
            if (isOpponentBoardVisible) opponentShips else playerShips
        val isOpponent = isOpponentBoardVisible

        if (cellViews.isEmpty() || gridSize == 0 || cellViews.size != gridSize || (gridSize > 0 && cellViews[0] == null) || (gridSize > 0 && cellViews[0]!!.size != gridSize)) {
            Log.e(
                "GameActivity",
                "updateGridCells: cellViews is empty, size mismatch or null. isOpponentVisible=$isOpponentBoardVisible. Skipping UI update."
            )
            return
        }

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                cellViews.getOrNull(row)?.getOrNull(col)?.let { cellView ->
                    val state = currentBoard[row][col]

                    var actualState = state
                    if (actualState == CellState.SHIP || actualState == CellState.HIT || actualState == CellState.SUNK) {
                        val ship = currentShips.find { it.cells.contains(Pair(row, col)) }
                        if (ship?.isSunk() == true) {
                            actualState = CellState.SUNK
                        } else if (actualState == CellState.SUNK) {
                            if (currentBoard[row][col] == CellState.HIT || currentBoard[row][col] == CellState.SUNK) {
                                actualState =
                                    CellState.HIT
                            } else {
                                actualState = CellState.EMPTY
                                Log.e(
                                    "GameActivity",
                                    "updateGridCells: Found unexpected CellState.SUNK at $row,$col. Board state/Ship object mismatch."
                                )
                            }
                        }
                    }
                    updateCellView(cellView, actualState, isOpponent)
                } ?: run {
                    Log.e(
                        "GameActivity",
                        "View ячейки отсутствует в cellViews по адресу: isOpponentVisible=$isOpponentBoardVisible, row=$row, col=$col. Skipping updateCellView."
                    )
                }
            }
        }
        placeMissMarksAroundSunkShips(
            currentBoard,
            cellViews,
            currentShips,
            isOpponent
        )

        Log.d("GameActivity", "updateGridCells: Finished updating UI for visible board.")
    }

    private fun placeMissMarksAroundSunkShips(
        board: Array<Array<CellState>>,
        cellViews: Array<Array<TextView?>>,
        ships: List<Ship>,
        isOpponentBoard: Boolean
    ) {
        for (ship in ships) {
            if (ship.isSunk()) {
                for (cell in ship.cells) {
                    val shipRow = cell.first
                    val shipCol = cell.second

                    for (r in (shipRow - 1)..(shipRow + 1)) {
                        for (c in (shipCol - 1)..(shipCol + 1)) {
                            if (r >= 0 && r < gridSize && c >= 0 && c < gridSize && !ship.cells.contains(
                                    Pair(r, c)
                                )
                            ) {
                                if (board[r][c] == CellState.EMPTY) {
                                    cellViews.getOrNull(r)?.getOrNull(c)?.let { cellView ->
                                        board[r][c] =
                                            CellState.MISS
                                        updateCellView(cellView, CellState.MISS, isOpponentBoard)
                                    } ?: run {
                                        Log.e(
                                            "GameActivity",
                                            "View ячейки отсутствует в cellViews по адресу: isOpponentBoard=$isOpponentBoard, row=$r, col=$c during placing miss marks."
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun updateCellView(cellView: TextView?, state: CellState, isOpponent: Boolean) {
        cellView ?: return

        cellView.text = ""
        cellView.setTextColor(
            ContextCompat.getColor(
                this,
                android.R.color.transparent
            )
        )
        cellView.setTypeface(null, Typeface.NORMAL)

        when (state) {
            CellState.EMPTY -> cellView.setBackgroundResource(R.drawable.cell_water)
            CellState.SHIP -> {
                if (isOpponent) {
                    cellView.setBackgroundResource(R.drawable.cell_water)
                } else {
                    cellView.setBackgroundResource(R.drawable.cell_ship_player)
                }
            }

            CellState.HIT -> {
                cellView.setBackgroundResource(R.drawable.cell_hit)
                cellView.text = "X"
                cellView.setTextColor(
                    ContextCompat.getColor(
                        this,
                        android.R.color.white
                    )
                ) // Белый текст для X
                cellView.setTypeface(null, Typeface.BOLD)
            }

            CellState.MISS -> {
                cellView.setBackgroundResource(R.drawable.cell_water)
                cellView.text = "•"
                cellView.setTextColor(
                    ContextCompat.getColor(
                        this,
                        android.R.color.holo_red_dark
                    )
                )
                cellView.setTypeface(null, Typeface.BOLD)
            }

            CellState.SUNK -> {
                cellView.setBackgroundResource(R.drawable.cell_sunk)
                cellView.text = "X"
                cellView.setTextColor(
                    ContextCompat.getColor(
                        this,
                        android.R.color.holo_red_dark
                    )
                )
                cellView.setTypeface(null, Typeface.BOLD)
            }
        }
        cellView.gravity = Gravity.CENTER
    }

    private fun createLabels() {
        val labelColor = ContextCompat.getColor(this, R.color.purple_700)
        val labelTextSize = 14f

        opponentColLabelsLayout.removeAllViews()
        opponentRowLabelsLayout.removeAllViews()
        playerColLabelsLayout.removeAllViews()
        playerRowLabelsLayout.removeAllViews()

        val columnLabels = arrayOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

        for (i in 0 until gridSize) {
            val opponentColLabel = TextView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { gravity = Gravity.CENTER }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerColLabel = TextView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { gravity = Gravity.CENTER }
                text = columnLabels[i]
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            opponentColLabelsLayout.addView(opponentColLabel)
            playerColLabelsLayout.addView(playerColLabel)
        }

        for (i in 0 until gridSize) {
            val opponentRowLabel = TextView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f)
                        .apply { gravity = Gravity.CENTER_VERTICAL }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            val playerRowLabel = TextView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f)
                        .apply { gravity = Gravity.CENTER_VERTICAL }
                text = (i + 1).toString()
                textSize = labelTextSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(labelColor)
            }
            opponentRowLabelsLayout.addView(opponentRowLabel)
            playerRowLabelsLayout.addView(playerRowLabel)
        }
    }

    private fun showBoard(showOpponent: Boolean) {
        showGameOverButtons()
        Log.d(
            "GameActivity",
            "showBoard: Switching board to " + if (showOpponent) "Opponent" else "Player"
        )
        if (showOpponent) {
            opponentBoardContainer.visibility = View.VISIBLE
            playerBoardContainer.visibility = View.GONE
        } else {
            playerBoardContainer.visibility = View.VISIBLE
            opponentBoardContainer.visibility = View.GONE
        }
        updateStatusText()
        updateGridCells()
        Log.d("GameActivity", "showBoard: Switching finished, updateGridCells called.")
    }

    private fun handlePlayerShot(row: Int, col: Int) {
        Log.d(
            "GameActivity",
            "handlePlayerShot: Player shot at $row,$col. isPlayerTurn=$isPlayerTurn"
        )
        if (!isGameOver && isPlayerTurn) {

            val cellState = opponentBoard[row][col]

            if (cellState == CellState.HIT || cellState == CellState.MISS || cellState == CellState.SUNK) {
                statusTextView.text = "Сюда уже стреляли, выберите другую клетку!"
                Log.d("GameActivity", "handlePlayerShot: Already shot there.")
                return
            }

            if (cellState == CellState.SHIP) {
                Log.d("GameActivity", "handlePlayerShot: Hit!")
                val hitShip = opponentShips.find { it.cells.contains(Pair(row, col)) }

                if (hitShip != null) {
                    hitShip.hits++
                    opponentBoard[row][col] = CellState.HIT

                    if (hitShip.isSunk()) {
                        statusTextView.text =
                            "Убил ${hitShip.size}-палубник!"
                        Log.d("GameActivity", "handlePlayerShot: Sunk a ${hitShip.size}-палубник!")
                        for (cell in hitShip.cells) {
                            opponentBoard[cell.first][cell.second] = CellState.SUNK
                        }
                    } else {
                        statusTextView.text = "Ранил!"
                        Log.d("GameActivity", "handlePlayerShot: Hit a ${hitShip.size}-палубник!")
                    }

                    updateGridCells()

                    checkGameOver()

                    if (!isGameOver) {
                        Log.d("GameActivity", "handlePlayerShot: Player hit/sunk, turn continues.")
                    } else {
                        Log.d("GameActivity", "handlePlayerShot: Game is over after player's move.")
                    }

                } else {
                    Log.e(
                        "GameActivity",
                        "handlePlayerShot: Hit at $row,$col but target ship not found in opponentShips list!"
                    )
                    Toast.makeText(
                        this,
                        "Ошибка игры: Попал, но не нашел корабль!",
                        Toast.LENGTH_SHORT
                    ).show()
                    opponentBoard[row][col] =
                        CellState.HIT
                    updateGridCells()
                    statusTextView.text =
                        "Ошибка: Неизвестное попадание!"
                    isPlayerTurn = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        showBoard(false)
                        computerTurn()
                    }, 1000)
                }

            } else {
                Log.d("GameActivity", "handlePlayerShot: Miss.")
                opponentBoard[row][col] = CellState.MISS
                statusTextView.text = "Промах!"
                updateGridCells()

                isPlayerTurn = false

                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(false)
                    computerTurn()
                }, 1000)
            }
        } else {
            Log.d("GameActivity", "Click on opponent board ignored. game is over or not player's turn.")
        }
    }

    private fun computerTurn() {
        Log.d("GameActivity", "computerTurn: Starting. isPlayerTurn=$isPlayerTurn")
        if (isGameOver || isPlayerTurn) {
            Log.d(
                "GameActivity",
                "computerTurn: Skipping turn. isGameOver=$isGameOver, isPlayerTurn=$isPlayerTurn"
            )
            return
        }

        statusTextView.text =
            "Ход компьютера..."

        var row: Int
        var col: Int
        var isValidShot: Boolean
        val random = Random(System.currentTimeMillis())

        do {
            row = random.nextInt(gridSize)
            col = random.nextInt(gridSize)
            val state = playerBoard[row][col]
            isValidShot = state == CellState.EMPTY || state == CellState.SHIP
        } while (!isValidShot)
        Log.d("GameActivity", "computerTurn: Shot at $row,$col.")

        Handler(Looper.getMainLooper()).postDelayed({
            val targetState = playerBoard[row][col]

            if (targetState == CellState.SHIP) {
                Log.d("GameActivity", "computerTurn: Hit!")

                val hitShip = playerShips.find { it.cells.contains(Pair(row, col)) }

                if (hitShip != null) {
                    hitShip.hits++
                    playerBoard[row][col] = CellState.HIT

                    if (hitShip.isSunk()) {
                        statusTextView.text = "Ваш корабль ${hitShip.size} потоплен!"
                        Log.d(
                            "GameActivity",
                            "computerTurn: Sunk player's ${hitShip.size}-палубник!"
                        )
                        for (cell in hitShip.cells) {
                            playerBoard[cell.first][cell.second] = CellState.SUNK
                        }
                    } else {
                        statusTextView.text = "Компьютер попал в ваш корабль ${hitShip.size}!"
                        Log.d(
                            "GameActivity",
                            "computerTurn: Hit player's ${hitShip.size}-палубник!"
                        )
                    }

                    updateGridCells()

                    checkGameOver()

                    if (!isGameOver) {
                        Log.d(
                            "GameActivity",
                            "computerTurn: Computer hit/sunk, turn continues. Next computer turn scheduled."
                        )
                        Handler(Looper.getMainLooper()).postDelayed(
                            { computerTurn() },
                            1000
                        )
                    } else {
                        Log.d("GameActivity", "computerTurn: Game is over after computer's move.")
                    }

                } else {
                    Log.e(
                        "GameActivity",
                        "computerTurn: Hit at $row,$col but target ship not found in playerShips list!"
                    )
                    Toast.makeText(
                        this,
                        "Ошибка игры: Компьютер попал, но не нашел корабль!",
                        Toast.LENGTH_SHORT
                    ).show()
                    playerBoard[row][col] = CellState.HIT
                    updateGridCells()
                    statusTextView.text =
                        "Ошибка: Компьютер попал (неизвестно куда)!"
                    isPlayerTurn = true
                    Handler(Looper.getMainLooper()).postDelayed(
                        { showBoard(true) },
                        1000
                    )
                }


            } else {
                Log.d("GameActivity", "computerTurn: Miss.")
                playerBoard[row][col] = CellState.MISS
                statusTextView.text = "Компьютер промахнулся!"
                updateGridCells()

                isPlayerTurn = true

                Handler(Looper.getMainLooper()).postDelayed({
                    showBoard(true)
                }, 1000)
            }
            Log.d("GameActivity", "computerTurn: Delayed action finished.")
        }, 1000)
        Log.d("GameActivity", "computerTurn: Finished, delayed shot scheduled.")
    }

    private fun checkGameOver() {
        Log.d("GameActivity", "checkGameOver: Checking game over.")
        val allPlayerShipsSunk = playerShips.all { it.isSunk() }
        val allOpponentShipsSunk = opponentShips.all { it.isSunk() }

        if (allOpponentShipsSunk) {
            statusTextView.text = "Поздравляем! Вы победили!"
            isGameOver = true
            Log.d("GameActivity", "checkGameOver: Player WINS!")
            saveGameResult(winnerIsPlayer = true)
        } else if (allPlayerShipsSunk) {
            statusTextView.text = "К сожалению, вы проиграли. Компьютер победил!"
            isGameOver = true
            Log.d("GameActivity", "checkGameOver: Computer WINS!")
            saveGameResult(winnerIsPlayer = false)
        }
        if (isGameOver) {
            Log.d("GameActivity", "checkGameOver: Game is over.")
        }
    }

    private fun showGameOverButtons() {
        Log.d("GameActivity", "showGameOverButtons: Ensuring game over buttons are visible.")
        gameOverButtonContainer.visibility = View.VISIBLE
    }

    private fun saveGameResult(winnerIsPlayer: Boolean) {
        Log.d("GameActivity", "saveGameResult: Saving game result. Player won: $winnerIsPlayer")
        val result = GameResultEntity(
            timestamp = System.currentTimeMillis(),
            winner = if (winnerIsPlayer) "Игрок" else "Компьютер",
            playerWon = winnerIsPlayer
        )

        lifecycleScope.launch {
            try {
                val resultId = gameResultDao.insertGameResult(result)
                Log.d("GameActivity", "saveGameResult: Game result saved with ID: $resultId")
            } catch (e: Exception) {
                Log.e("GameActivity", "saveGameResult: Failed to save game result", e)
                Toast.makeText(
                    this@GameActivity,
                    "Ошибка сохранения результата игры.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateStatusText() {
        if (isGameOver) {
            Log.d("GameActivity", "updateStatusText: Game is over, not updating status text.")
            return
        }
        statusTextView.text = if (isPlayerTurn) "Ваш ход" else "Ход компьютера..."
        Log.d("GameActivity", "updateStatusText: Status set to '${statusTextView.text}'")
    }
}