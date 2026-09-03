package com.example.whiteboardifp.models

data class StrokeModel(
    val points: MutableList<PointModel> = mutableListOf(),
    val color: String = "#111827",
    val width: Float = 5f
)