package com.example.whiteboardifp.models

data class WhiteboardModel(
    val version: Int = 2,
    val canvasWidth: Int = 0,
    val canvasHeight: Int = 0,

    val strokes: MutableList<StrokeModel> = mutableListOf(),

    val shapes: MutableList<ShapeModel> = mutableListOf(),

    val texts: MutableList<TextModel> = mutableListOf(),

    val images: MutableList<ImageModel> = mutableListOf()
)