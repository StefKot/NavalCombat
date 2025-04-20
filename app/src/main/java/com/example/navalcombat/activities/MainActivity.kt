package com.example.navalcombat.activities // Убедитесь, что пакет ваш

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

// TODO: Класс GameResultEntity уже определен в data.db.entity, удалить это дублирование, если оно есть.
/*
data class GameResult(
    val timestamp: Long,
    val winner: String, // "Игрок" или "Компьютер"
    val playerWon: Boolean
)
*/

class MainActivity : AppCompatActivity() {

    private lateinit var winStreakTextView: TextView
    private lateinit var newGameButton: Button
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var emptyHistoryTextView: TextView
    private lateinit var historyAdapter: SimpleHistoryAdapter // Объявляем адаптер

    // Получаем DAO для работы с историей игр
    private lateinit var gameResultDao: com.example.navalcombat.data.GameResultDao

    // TODO: Список для хранения истории больше не нужен здесь, т.к. адаптер работает с Flow
    // private var gameHistoryList = mutableListOf<GameResultEntity>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate: Started")
        setContentView(R.layout.activity_main) // Убедитесь, что здесь указан ваш макет activity_main.xml

        // Находим View по ID (эти ID должны быть в макете activity_main.xml)
        winStreakTextView = findViewById(R.id.textViewWinStreak)
        newGameButton = findViewById(R.id.buttonNewGame)
        historyRecyclerView = findViewById(R.id.recyclerViewHistory)
        emptyHistoryTextView = findViewById(R.id.textViewEmptyHistory)

        // --- ДОБАВЛЕНО: Инициализация gameResultDao ---
        val db = AppDatabase.getDatabase(this) // Получаем экземпляр базы данных
        gameResultDao = db.gameResultDao() // Получаем DAO из базы данных
        Log.d("MainActivity", "onCreate: Room DAO initialized.")
        // ---

        // Настройка RecyclerView
        setupRecyclerView()
        Log.d("MainActivity", "onCreate: RecyclerView setup finished.")

        // --- ИЗМЕНЕНО: Загрузка данных из базы данных с использованием Flow и repeatOnLifecycle ---
        observeGameHistory() // Этот метод теперь запускает наблюдение за Flow
        // loadDummyGameHistory() // Удалить или закомментировать фейковую загрузку
        // ---

        // Настройка кнопки "Новая Игра"
        newGameButton.setOnClickListener {
            Log.d("MainActivity", "New Game button clicked.")
            val intent = Intent(this, SetupActivity::class.java) // Переходим в SetupActivity
            startActivity(intent) // Запускаем SetupActivity
        }
        Log.d("MainActivity", "onCreate: New Game button listener set.")

        // TODO: Возможно, добавить кнопку "Очистить историю" для тестирования
    }

    // Метод для настройки RecyclerView
    private fun setupRecyclerView() {
        // Создаем адаптер. Изначально передаем пустой список, данные придут из Flow.
        historyAdapter = SimpleHistoryAdapter(emptyList()) // Передаем пустой список
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
        // Важно: setupRecyclerView вызывается только один раз в onCreate.
        // Обновление данных происходит через updateData в адаптере, вызываемую из Flow.
    }

    // --- ДОБАВЛЕНО/ИЗМЕНЕНО: Метод для наблюдения за историей из БД с помощью Flow и repeatOnLifecycle ---
    private fun observeGameHistory() {
        Log.d("MainActivity", "observeGameHistory: Starting Flow observation.")
        // Запускаем корутину в скоупе жизненного цикла Activity
        lifecycleScope.launch {
            // repeatOnLifecycle(Lifecycle.State.STARTED) запустит блок внутри, когда Activity
            // находится в состоянии STARTED или выше, и автоматически отменит блок, когда Activity
            // перейдет в состояние STOPPED. Это гарантирует, что сбор Flow не будет выполняться,
            // когда UI не виден.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("MainActivity", "observeGameHistory: Lifecycle is STARTED, collecting Flow.")
                // Собираем данные из Flow. Этот блок будет выполняться каждый раз, когда данные меняются
                // --- ИСПРАВЛЕНО: Явно указали тип параметра, синтаксис ПРАВИЛЬНЫЙ ---
                gameResultDao.getAllGameResults().collect { historyList: List<GameResultEntity> -> // <-- ЯВНО УКАЗАЛИ ТИП ПАРАМЕТРА. ЭТО ПРАВИЛЬНО!
                    // Обновляем данные в адаптере RecyclerView (выполняется на Main Thread)
                    historyAdapter.updateData(historyList) // Вызываем метод адаптера для обновления данных

                    // Считаем серию побед на основе актуальных данных
                    val maxStreak = calculateMaxWinStreak(historyList)
                    winStreakTextView.text = "Максимальная серия побед: $maxStreak" // Обновляем TextView серии

                    // Показываем/скрываем сообщение о пустой истории
                    updateHistoryVisibility(historyList.isEmpty()) // Передаем флаг, пуст ли список
                }
                // Когда repeatOnLifecycle отменяется (Activity переходит в STOPPED),
                // выполнение приостанавливается здесь, пока Activity не вернется в STARTED.
                Log.d("MainActivity", "observeGameHistory: Lifecycle is STOPPED, Flow collection paused.")
            }
            // Когда корутина launch завершается (например, Activity DESTROYED),
            // выполнение продолжается здесь, но lifecycleScope уже неактивен.
            Log.d("MainActivity", "observeGameHistory: LifecycleScope finished.")
        }
    }
    // --- Конец наблюдения за историей ---


    // TODO: Удалить или закомментировать loadDummyGameHistory
    /*
    private fun loadDummyGameHistory() {
        Log.d("MainActivity", "loadDummyGameHistory: Loading dummy data.")
        // Фейковая загрузка для демонстрации UI
        val dummyList = mutableListOf<GameResultEntity>() // Используем GameResultEntity для согласованности
        dummyList.add(GameResultEntity(timestamp = Date().time - 86400000 * 2, winner = "Игрок", playerWon = true)) // 2 дня назад, игрок выиграл
        dummyList.add(GameResultEntity(timestamp = Date().time - 86400000, winner = "Компьютер", playerWon = false)) // 1 день назад, комп выиграл
        dummyList.add(GameResultEntity(timestamp = Date().time - 3600000, winner = "Игрок", playerWon = true)) // 1 час назад, игрок выиграл
        dummyList.add(GameResultEntity(timestamp = Date().time - 600000, winner = "Игрок", playerWon = true)) // 10 минут назад, игрок выиграл

        dummyList.sortByDescending { it.timestamp } // Сортируем от новых к старым

        historyAdapter.updateData(dummyList) // Обновляем адаптер фейковыми данными

        val maxStreak = calculateMaxWinStreak(dummyList) // Считаем серию побед на основе фейковых данных
        winStreakTextView.text = "Максимальная серия побед: $maxStreak (фейк)" // Обновляем TextView серии

        updateHistoryVisibility(dummyList.isEmpty()) // Показываем/скрываем
        Log.d("MainActivity", "loadDummyGameHistory: Dummy data loaded.")
    }
    */


    // Метод для расчета максимальной последовательной серии побед Игрока
    // Принимает List<GameResultEntity>
    private fun calculateMaxWinStreak(history: List<GameResultEntity>): Int {
        var maxStreak = 0
        var currentStreak = 0
        // Идем по истории от самой старой записи к самой новой
        // Flow из Room (Query с ORDER BY DESC) возвращает от новых к старым.
        // Для расчета серии побед от старых к новым, нужно инвертировать список.
        val sortedHistory = history.sortedBy { it.timestamp } // Сортируем по возрастанию timestamp
        for (result in sortedHistory) {
            if (result.playerWon) {
                currentStreak++ // Если игрок выиграл, увеличиваем текущую серию
            } else {
                // Если игрок проиграл
                maxStreak = maxOf(maxStreak, currentStreak) // Запоминаем максимум
                currentStreak = 0 // Сбрасываем текущую серию
            }
        }
        // После цикла нужно еще раз сравнить с текущей серией, на случай, если она была в самом конце списка
        maxStreak = maxOf(maxStreak, currentStreak)
        return maxStreak
    }


    // Метод для управления видимостью RecyclerView и сообщения о пустой истории
    private fun updateHistoryVisibility(isHistoryEmpty: Boolean) {
        if (isHistoryEmpty) {
            historyRecyclerView.visibility = View.GONE // Скрыть список RecyclerView
            emptyHistoryTextView.visibility = View.VISIBLE // Показать TextView "История пока пуста"
            Log.d("MainActivity", "updateHistoryVisibility: History is empty, showing empty message.")
        } else {
            historyRecyclerView.visibility = View.VISIBLE // Показать список RecyclerView
            emptyHistoryTextView.visibility = View.GONE // Скрыть TextView "История пока пуста"
            Log.d("MainActivity", "updateHistoryVisibility: History is not empty, showing RecyclerView.")
        }
    }

    // --- Простой адаптер для RecyclerView (для отображения записей истории) ---
    // Этот класс можно вынести в отдельный файл SimpleHistoryAdapter.kt в пакете adapters
    // Адаптер теперь работает со списком GameResultEntity
    // Добавлен метод updateData для обновления списка из Flow
    class SimpleHistoryAdapter(private var items: List<GameResultEntity>) : // items - mutable list
        RecyclerView.Adapter<SimpleHistoryAdapter.ViewHolder>() {

        // Форматтер даты для красивого отображения
        private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

        // ViewHolder: хранит ссылки на View внутри одного элемента списка
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // Убедитесь, что этот ID historyItemText есть в simple_history_item.xml
            val historyText: TextView = view.findViewById(R.id.historyItemText)
            // Если у вас в simple_history_item есть другие View, их тоже нужно здесь найти
            // val otherTextView: TextView = view.findViewById(R.id.otherTextView)
        }

        // Создает новый ViewHolder (вызывается, когда RecyclerView нужен новый View для элемента)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Надуваем (инфлейтим) макет simple_history_item.xml для одного элемента списка
            // Убедитесь, что R.layout.simple_history_item - правильное имя вашего макета элемента списка
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.simple_history_item, parent, false)
            return ViewHolder(view)
        }

        // Привязывает данные из списка к View в ViewHolder (вызывается для каждого видимого элемента)
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position] // Получаем данные для текущей позиции
            val dateStr = dateFormat.format(Date(item.timestamp)) // Форматируем timestamp в строку даты/времени
            // Формируем и устанавливаем текст для TextView
            holder.historyText.text = "$dateStr - Победитель: ${item.winner}"
            // TODO: Можно добавить цвет текста или иконку в зависимости от победителя
            // holder.historyText.setTextColor(...)
        }

        // Возвращает общее количество элементов в списке данных
        override fun getItemCount(): Int {
            return items.size
        }

        // --- ДОБАВЛЕНО: Метод для обновления данных в адаптере ---
        // Этот метод вызывается из Flow.collect
        fun updateData(newItems: List<GameResultEntity>) {
            // TODO: Для лучшей производительности при больших списках использовать DiffUtil
            // DiffUtil позволяет обновлять список более эффективно, анимируя изменения.
            // Для начала, простая замена списка и notifyDataSetChanged() достаточна.
            items = newItems // Заменяем старый список новым
            notifyDataSetChanged() // Уведомляем RecyclerView об изменениях
        }
        // ---
    }
    // --- Конец адаптера ---


    // TODO: Можно переопределить onResume() чтобы обновлять историю при возвращении из игры/расстановки
    //       С Flow и repeatOnLifecycle это происходит автоматически.
    //       onResume() полезно, если бы вы не использовали Flow, а просто загружали данные один раз.
    //       Сейчас observeGameHistory() в onCreate в паре с repeatOnLifecycle(STARTED) и Flow из Room
    //       обеспечивают автоматическое обновление при возвращении в Activity (переход из STOPPED в STARTED).
    /*
    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume: Started.")
        // Flow уже наблюдает за изменениями, повторный вызов collect не нужен.
        // Но если бы вы не использовали Flow, а использовали список, вам нужно обновить список здесь:
        // lifecycleScope.launch {
        //     val historyList = gameResultDao.getAllGameResultsList() // Получаем данные
        //     historyAdapter.updateData(historyList) // Обновляем адаптер
        //     updateHistoryVisibility(historyList.isEmpty()) // Обновляем видимость
        //     winStreakTextView.text = "Максимальная серия побед: ${calculateMaxWinStreak(historyList)}" // Обновляем серию
        // }
    }
     */
    /*
   override fun onPause() {
       super.onPause()
       Log.d("MainActivity", "onPause: Started.")
       // repeatOnLifecycle(STARTED) автоматически приостанавливает сбор Flow при переходе в STOPPED
   }
    */
    /*
   override fun onDestroy() {
       super.onDestroy()
        Log.d("MainActivity", "onDestroy: Started.")
       // lifecycleScope автоматически отменяет все корутины при DESTROYED
   }
    */
}