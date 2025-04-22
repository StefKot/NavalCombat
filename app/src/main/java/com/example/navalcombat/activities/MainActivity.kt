package com.example.navalcombat.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log // Импортируем Log для отладки
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.navalcombat.R // Убедитесь, что R импортирован правильно
import com.example.navalcombat.data.AppDatabase // Импортируем класс базы данных
import com.example.navalcombat.data.GameResultEntity // Импортируем сущность
import kotlinx.coroutines.flow.collect // Collect теперь не нужен явно
import kotlinx.coroutines.launch // Импортируем launch для корутины
import androidx.lifecycle.lifecycleScope // Импортируем lifecycleScope (lifecycle-runtime-ktx)
import androidx.lifecycle.Lifecycle // Импортируем Lifecycle (lifecycle-common-ktx)
import androidx.lifecycle.repeatOnLifecycle // Импортируем repeatOnLifecycle (lifecycle-runtime-ktx)
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var winStreakTextView: TextView
    private lateinit var newGameButton: Button
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var emptyHistoryTextView: TextView
    private lateinit var historyAdapter: SimpleHistoryAdapter // Объявляем адаптер

    private lateinit var gameResultDao: com.example.navalcombat.data.GameResultDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate: Started")
        setContentView(R.layout.activity_main)

        winStreakTextView = findViewById(R.id.textViewWinStreak)
        newGameButton = findViewById(R.id.buttonNewGame)
        historyRecyclerView = findViewById(R.id.recyclerViewHistory)
        emptyHistoryTextView = findViewById(R.id.textViewEmptyHistory)

        val db = AppDatabase.getDatabase(this)
        gameResultDao = db.gameResultDao()
        Log.d("MainActivity", "onCreate: Room DAO initialized.")

        setupRecyclerView()
        Log.d("MainActivity", "onCreate: RecyclerView setup finished.")

        observeGameHistory()

        newGameButton.setOnClickListener {
            Log.d("MainActivity", "New Game button clicked.")
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
        }
        Log.d("MainActivity", "onCreate: New Game button listener set.")
    }

    private fun setupRecyclerView() {
        historyAdapter = SimpleHistoryAdapter(emptyList())
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
    }

    private fun observeGameHistory() {
        Log.d("MainActivity", "observeGameHistory: Starting Flow observation.")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("MainActivity", "observeGameHistory: Lifecycle is STARTED, collecting Flow.")
                gameResultDao.getAllGameResults().collect { historyList: List<GameResultEntity> ->
                    historyAdapter.updateData(historyList)

                    val maxStreak = calculateMaxWinStreak(historyList)
                    winStreakTextView.text = "Максимальная серия побед: $maxStreak"

                    updateHistoryVisibility(historyList.isEmpty())
                }
                Log.d("MainActivity", "observeGameHistory: Lifecycle is STOPPED, Flow collection paused.")
            }
            Log.d("MainActivity", "observeGameHistory: LifecycleScope finished.")
        }
    }

    private fun calculateMaxWinStreak(history: List<GameResultEntity>): Int {
        var maxStreak = 0
        var currentStreak = 0

        val sortedHistory = history.sortedBy { it.timestamp }
        for (result in sortedHistory) {
            if (result.playerWon) {
                currentStreak++
            } else {
                maxStreak = maxOf(maxStreak, currentStreak)
                currentStreak = 0
            }
        }
        maxStreak = maxOf(maxStreak, currentStreak)
        return maxStreak
    }

    private fun updateHistoryVisibility(isHistoryEmpty: Boolean) {
        if (isHistoryEmpty) {
            historyRecyclerView.visibility = View.GONE
            emptyHistoryTextView.visibility = View.VISIBLE
            Log.d("MainActivity", "updateHistoryVisibility: History is empty, showing empty message.")
        } else {
            historyRecyclerView.visibility = View.VISIBLE
            emptyHistoryTextView.visibility = View.GONE
            Log.d("MainActivity", "updateHistoryVisibility: History is not empty, showing RecyclerView.")
        }
    }

    class SimpleHistoryAdapter(private var items: List<GameResultEntity>) :
        RecyclerView.Adapter<SimpleHistoryAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val historyText: TextView = view.findViewById(R.id.historyItemText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.simple_history_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val dateStr = dateFormat.format(Date(item.timestamp))
            holder.historyText.text = "$dateStr - Победитель: ${item.winner}"
        }

        override fun getItemCount(): Int {
            return items.size
        }

        fun updateData(newItems: List<GameResultEntity>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}