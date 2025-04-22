package com.example.navalcombat.model

import java.io.Serializable

data class Ship(
    val size: Int,
    val cells: List<Pair<Int, Int>>,
    var hits: Int = 0
) : Serializable {
    fun isSunk(): Boolean {
        return hits >= size
    }
}