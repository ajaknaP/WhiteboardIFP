package com.example.whiteboardifp.models

data class TextModel(
    var text: String,
    var x: Float,
    var y: Float,
    var color: String,
    var size: Float,
    var width: Float = 420f,
    var height: Float = 100f,
    var rotation: Float = 0f

)