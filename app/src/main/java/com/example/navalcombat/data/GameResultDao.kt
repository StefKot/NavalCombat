package com.example.navalcombat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.navalcombat.data.GameResultEntity
import kotlinx.coroutines.flow.Flow // Если используете Flow с Room (рекомендуется)
import androidx.lifecycle.LiveData // Если используете LiveData с Room

@Dao // Помечаем как DAO
interface GameResultDao {

    // Метод для вставки одной записи игры в БД
    @Insert(onConflict = OnConflictStrategy.IGNORE) // Если запись с таким ID уже есть, игнорировать (с autoGenerate ID это маловероятно)
    suspend fun insertGameResult(result: GameResultEntity): Long // suspend - для асинхронной операции, возвращает ID вставленной строки

    // Метод для получения всех записей истории, отсортированных по времени (от новых к старым)
    @Query("SELECT * FROM game_history ORDER BY timestamp DESC")
    fun getAllGameResults(): Flow<List<GameResultEntity>> // Возвращает Flow, который будет автоматически обновляться при изменении данных

    // Если не используете Flow, можно использовать LiveData
    // fun getAllGameResultsLiveData(): LiveData<List<GameResultEntity>>

    // Или простой список (не будет автоматически обновляться)
    // suspend fun getAllGameResultsList(): List<GameResultEntity>

    // TODO: Добавьте другие запросы, если нужно (например, удалить все записи, получить записи за период и т.д.)
    @Query("DELETE FROM game_history")
    suspend fun deleteAllGameResults()
}