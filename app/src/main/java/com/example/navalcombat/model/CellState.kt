package com.example.navalcombat.model

// Состояния ячейки игрового поля
enum class CellState {
    EMPTY,      // Пустая вода
    SHIP,       // Часть корабля
    HIT,        // Попадание (в корабль)
    MISS,       // Промах (вода)
    SUNK        // Корабль потоплен (можно использовать для визуализации)
}