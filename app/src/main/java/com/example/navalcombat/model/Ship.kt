package com.example.navalcombat.model

import java.io.Serializable

data class Ship(
    val size: Int, // Размер корабля (например, 4, 3, 2, 1)
    val cells: List<Pair<Int, Int>>, // Список координат (строка, столбец), которые занимает корабль
    var hits: Int = 0 // Количество попаданий по этому кораблю
) : Serializable {
    // Проверяет, потоплен ли корабль
    fun isSunk(): Boolean {
        return hits >= size
    }
}