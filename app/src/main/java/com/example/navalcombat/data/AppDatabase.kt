package com.example.navalcombat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.navalcombat.data.GameResultDao
import com.example.navalcombat.data.GameResultEntity

@Database(entities = [GameResultEntity::class], version = 1, exportSchema = false) // Объявляем сущности и версию
abstract class AppDatabase : RoomDatabase() {

    // Абстрактный метод для получения DAO
    abstract fun gameResultDao(): GameResultDao

    companion object {
        // Singleton предотвращает создание нескольких экземпляров базы данных одновременно
        @Volatile // Помечаем, чтобы запись в INSTANCE была видна всем потокам немедленно
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Если INSTANCE уже существует, возвращаем его
            return INSTANCE ?: synchronized(this) { // Синхронизированный блок для потокобезопасного создания
                // Если INSTANCE еще не существует внутри синхронизированного блока, создаем его
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Используем applicationContext, чтобы избежать утечек памяти
                    AppDatabase::class.java,
                    "naval_combat_db" // Имя файла базы данных
                )
                    // .fallbackToDestructiveMigration() // Опционально: при изменении схемы удалять старую БД. Для продакшена нужны миграции.
                    // .createFromAsset("database/myapp.db") // Опционально: если у вас есть предопределенная БД
                    .build()
                INSTANCE = instance // Присваиваем созданный экземпляр INSTANCE
                instance // Возвращаем экземпляр
            }
        }
    }
}