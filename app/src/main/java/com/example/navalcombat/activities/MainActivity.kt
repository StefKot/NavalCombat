package com.example.navalcombat.activities // Убедитесь, что пакет ваш

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.navalcombat.R // Убедитесь, что R импортирован правильно
import java.text.SimpleDateFormat
import java.util.* // Для Date и SimpleDateFormat

// Класс для данных игры (простой)
data class GameResult(
    val timestamp: Long,
    val winner: String, // "Игрок" или "Компьютер"
    val playerWon: Boolean
)

class MainActivity : AppCompatActivity() {

    // Объявляем View элементы
    private lateinit var winStreakTextView: TextView
    private lateinit var newGameButton: Button
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var emptyHistoryTextView: TextView
    private lateinit var historyAdapter: SimpleHistoryAdapter // Объявляем адаптер

    // Список для хранения истории
    private var gameHistoryList = mutableListOf<GameResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Убедитесь, что здесь указан ваш макет с ConstraintLayout
        setContentView(R.layout.activity_main)

        // Находим View по ID (эти ID есть в макете с ConstraintLayout)
        winStreakTextView = findViewById(R.id.textViewWinStreak)
        newGameButton = findViewById(R.id.buttonNewGame)
        historyRecyclerView = findViewById(R.id.recyclerViewHistory)
        emptyHistoryTextView = findViewById(R.id.textViewEmptyHistory)

        // Настройка RecyclerView
        setupRecyclerView()

        // Загрузка данных (пока фейковых)
        loadDummyGameHistory()

        // Настройка кнопки "Новая Игра"
        newGameButton.setOnClickListener {
            // Убедитесь, что GameActivity существует и правильно указана
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        // Создаем адаптер, передавая ему наш список
        historyAdapter = SimpleHistoryAdapter(gameHistoryList)
        // Устанавливаем LayoutManager (как будут располагаться элементы - вертикально)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        // Назначаем адаптер нашему RecyclerView
        historyRecyclerView.adapter = historyAdapter
    }

    private fun loadDummyGameHistory() {
        // Просто добавим несколько записей для примера
        gameHistoryList.clear() // Очищаем старые данные, если они были
        gameHistoryList.add(GameResult(Date().time - 86400000 * 2, "Игрок", true))
        gameHistoryList.add(GameResult(Date().time - 86400000, "Компьютер", false))
        gameHistoryList.add(GameResult(Date().time - 3600000, "Игрок", true))
        gameHistoryList.add(GameResult(Date().time - 600000, "Игрок", true))

        // Сортируем от новых к старым (если нужно)
        gameHistoryList.sortByDescending { it.timestamp }

        // Обновляем данные в адаптере
        historyAdapter.notifyDataSetChanged() // Говорим RecyclerView обновить отображение

        // Считаем серию побед
        val maxStreak = calculateMaxWinStreak(gameHistoryList)
        winStreakTextView.text = "Максимальная серия побед: $maxStreak"

        // Показываем/скрываем сообщение о пустой истории
        updateHistoryVisibility()
    }

    private fun calculateMaxWinStreak(history: List<GameResult>): Int {
        var maxStreak = 0
        var currentStreak = 0
        // Идем по истории от самой старой к самой новой
        for (result in history.reversed()) { // reversed() - чтобы идти от старых к новым
            if (result.playerWon) {
                currentStreak++
            } else {
                maxStreak = maxOf(maxStreak, currentStreak) // Запоминаем максимум
                currentStreak = 0 // Сбрасываем текущую серию
            }
        }
        maxStreak = maxOf(maxStreak, currentStreak) // Проверяем серию в конце списка
        return maxStreak
    }

    private fun updateHistoryVisibility() {
        if (gameHistoryList.isEmpty()) {
            historyRecyclerView.visibility = View.GONE // Скрыть список
            emptyHistoryTextView.visibility = View.VISIBLE // Показать текст "пусто"
        } else {
            historyRecyclerView.visibility = View.VISIBLE // Показать список
            emptyHistoryTextView.visibility = View.GONE // Скрыть текст "пусто"
        }
    }

    // --- Простой адаптер для RecyclerView ---
    // Убедитесь, что R.layout.simple_history_item существует и
    // содержит TextView с ID R.id.historyItemText
    class SimpleHistoryAdapter(private val items: List<GameResult>) :
        RecyclerView.Adapter<SimpleHistoryAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // Убедитесь, что этот ID есть в simple_history_item.xml
            val historyText: TextView = view.findViewById(R.id.historyItemText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Убедитесь, что simple_history_item - правильное имя вашего макета
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
    }
    // --- Конец адаптера ---
}