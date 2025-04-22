package com.example.navalcombat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.navalcombat.data.GameResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameResultDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGameResult(result: GameResultEntity): Long

    @Query("SELECT * FROM game_history ORDER BY timestamp DESC")
    fun getAllGameResults(): Flow<List<GameResultEntity>>

    @Query("DELETE FROM game_history")
    suspend fun deleteAllGameResults()
}