package com.example.navalcombat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_history") // Имя таблицы в БД
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) // Автоматически генерируемый первичный ключ
    val id: Long = 0, // Значение 0 означает, что Room сгенерирует ID

    val timestamp: Long, // Время игры (можно хранить как Long - миллисекунды с 1970 года)
    val winner: String, // "Игрок" или "Компьютер"
    val playerWon: Boolean // Удобный флаг для расчета серии побед

    // Можно добавить другие поля, если нужно (например, свой тип кораблей игрока, сложность AI и т.д.)
    // val playerSetup: String? = null // Сериализованная расстановка игрока (опционально)
    // val computerDifficulty: Int = 1 // Сложность AI (опционально)
)