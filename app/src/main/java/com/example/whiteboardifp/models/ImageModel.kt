package com.example.whiteboardifp.models

data class ImageModel(
    var uri: String,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var scale: Float = 1f,
    var rotation: Float = 0f

)