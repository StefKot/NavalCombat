package com.example.navalcombat.model

import java.io.Serializable

data class ShipToPlace(val size: Int, var isHorizontal: Boolean = true) : Serializable