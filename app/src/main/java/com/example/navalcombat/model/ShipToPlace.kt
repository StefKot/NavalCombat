package com.example.navalcombat.model

import java.io.Serializable // Импортируем Serializable

// Класс для представления корабля во время расстановки.
// Serializable нужен, если вы планируете передавать этот объект напрямую через Intent.
// В данном случае мы передаем доску (Array<Array<CellState>>),
// а CellState является Serializable как Enum, так что это может быть не строго необходимо,
// но полезно для ясности структуры данных игры.
data class ShipToPlace(val size: Int, var isHorizontal: Boolean = true) : Serializable