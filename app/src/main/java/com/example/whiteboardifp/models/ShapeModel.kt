package com.example.whiteboardifp.models


data class ShapeModel(
    val type: ShapeType,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: String,
    val strokeWidth: Float,
    val sides: Int = 5,
    val rotation: Float = 0f

)