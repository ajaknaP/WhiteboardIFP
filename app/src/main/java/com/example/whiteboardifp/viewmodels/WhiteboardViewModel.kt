package com.example.whiteboardifp.viewmodels

import androidx.lifecycle.ViewModel
import com.example.whiteboardifp.models.ImageModel
import com.example.whiteboardifp.models.PointModel
import com.example.whiteboardifp.models.ShapeModel
import com.example.whiteboardifp.models.ShapeType
import com.example.whiteboardifp.models.StrokeModel
import com.example.whiteboardifp.models.TextModel
import com.example.whiteboardifp.models.WhiteboardModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt



class WhiteboardViewModel : ViewModel() {

    private val _board =
        MutableStateFlow(
            WhiteboardModel()
        )

    val board: StateFlow<WhiteboardModel> =
        _board.asStateFlow()

    private val undoStack =
        ArrayDeque<WhiteboardModel>()

    private val redoStack =
        ArrayDeque<WhiteboardModel>()

    // Temporary state used by drag/pinch/rotate gestures.
    // It is committed only when the gesture actually changes the board.
    private var editStartBoard: WhiteboardModel? = null

    var tool: Tool = Tool.PEN

    var color: String = "#111827"

    var strokeWidth: Float = 5f

    var shapeType: ShapeType =
        ShapeType.RECTANGLE


    // COPY


    private fun copyBoard(
        source: WhiteboardModel
    ): WhiteboardModel {

        return source.copy(

            strokes =
                source.strokes.map { stroke ->

                    stroke.copy(
                        points =
                            stroke.points
                                .toMutableList()
                    )

                }.toMutableList(),

            shapes =
                source.shapes
                    .map { it.copy() }
                    .toMutableList(),

            texts =
                source.texts
                    .map { it.copy() }
                    .toMutableList(),

            images =
                source.images
                    .map { it.copy() }
                    .toMutableList()
        )
    }


    // HISTORY


    private fun snapshot() {

        undoStack.addLast(
            copyBoard(_board.value)
        )

        if (undoStack.size > 50) {
            undoStack.removeFirst()
        }

        redoStack.clear()
    }

    fun beginEdit() {
        if (editStartBoard == null) {
            editStartBoard = copyBoard(_board.value)
        }
    }

    fun commitEdit() {
        val before = editStartBoard ?: return
        editStartBoard = null

        val after = copyBoard(_board.value)
        if (before != after) {
            undoStack.addLast(before)
            if (undoStack.size > 50) {
                undoStack.removeFirst()
            }
            redoStack.clear()
        }
    }

    fun cancelEdit() {
        editStartBoard = null
    }

    // CANVAS SIZE


    fun setCanvasSize(
        width: Int,
        height: Int
    ) {

        if (width <= 0 || height <= 0) {
            return
        }

        if (
            _board.value.canvasWidth == width &&
            _board.value.canvasHeight == height
        ) {
            return
        }

        _board.value =
            _board.value.copy(
                canvasWidth = width,
                canvasHeight = height
            )
    }

    // STROKE


    fun addStroke(
        points: List<PointModel>
    ) {

        if (points.size < 2) {
            return
        }

        snapshot()

        val stroke =
            StrokeModel(
                points =
                    points.toMutableList(),

                color = color,

                width =
                    strokeWidth
                        .coerceIn(
                            1f,
                            100f
                        )
            )

        val newStrokes =
            _board.value.strokes
                .toMutableList()

        newStrokes.add(stroke)

        _board.value =
            _board.value.copy(
                strokes = newStrokes
            )
    }


    // SHAPE


    fun addShape(
        shape: ShapeModel
    ) {

        snapshot()

        val shapes =
            _board.value.shapes
                .toMutableList()

        shapes.add(shape)

        _board.value =
            _board.value.copy(
                shapes = shapes
            )
    }

    /**
     * Updates a shape without creating an undo snapshot.
     * WhiteboardCanvasView calls beginEdit() once when a drag starts.
     */
    fun updateShape(
        index: Int,
        shape: ShapeModel
    ) {
        if (index !in _board.value.shapes.indices) {
            return
        }

        val shapes =
            _board.value.shapes
                .map { it.copy() }
                .toMutableList()

        shapes[index] = shape.copy()

        _board.value =
            _board.value.copy(
                shapes = shapes
            )
    }


    // TEXT


    fun addText(
        text: TextModel
    ) {

        if (text.text.isBlank()) {
            return
        }

        snapshot()

        val texts =
            _board.value.texts
                .toMutableList()

        texts.add(text)

        _board.value =
            _board.value.copy(
                texts = texts
            )
    }

    fun updateText(
        index: Int,
        text: TextModel
    ) {

        if (
            index !in
            _board.value.texts.indices
        ) {
            return
        }

        val texts =
            _board.value.texts
                .map { it.copy() }
                .toMutableList()

        texts[index] =
            text.copy()

        _board.value =
            _board.value.copy(
                texts = texts
            )
    }

    // IMAGE

    fun addImage(
        image: ImageModel
    ) {

        if (
            image.uri.isBlank() ||
            image.width <= 0f ||
            image.height <= 0f
        ) {
            return
        }

        snapshot()

        val images =
            _board.value.images
                .toMutableList()

        images.add(image)

        _board.value =
            _board.value.copy(
                images = images
            )
    }

    fun updateImage(
        index: Int,
        image: ImageModel
    ) {

        if (
            index !in
            _board.value.images.indices
        ) {
            return
        }

        val images =
            _board.value.images
                .map { it.copy() }
                .toMutableList()

        images[index] =
            image.copy()

        _board.value =
            _board.value.copy(
                images = images
            )
    }


    // DELETE


    fun deleteStroke(
        index: Int
    ) {

        if (
            index !in
            _board.value.strokes.indices
        ) {
            return
        }

        snapshot()

        val strokes =
            _board.value.strokes
                .toMutableList()

        strokes.removeAt(index)

        _board.value =
            _board.value.copy(
                strokes = strokes
            )
    }

    fun deleteShape(
        index: Int
    ) {

        if (
            index !in
            _board.value.shapes.indices
        ) {
            return
        }

        snapshot()

        val shapes =
            _board.value.shapes
                .toMutableList()

        shapes.removeAt(index)

        _board.value =
            _board.value.copy(
                shapes = shapes
            )
    }

    fun deleteText(
        index: Int
    ) {

        if (
            index !in
            _board.value.texts.indices
        ) {
            return
        }

        snapshot()

        val texts =
            _board.value.texts
                .toMutableList()

        texts.removeAt(index)

        _board.value =
            _board.value.copy(
                texts = texts
            )
    }

    fun deleteImage(
        index: Int
    ) {

        if (
            index !in
            _board.value.images.indices
        ) {
            return
        }

        snapshot()

        val images =
            _board.value.images
                .toMutableList()

        images.removeAt(index)

        _board.value =
            _board.value.copy(
                images = images
            )
    }


    // ERASER

    /**
     * Erases content around the supplied point.
     *
     * Stroke erasing is segment-based rather than point-based, so fast
     * finger movement cannot jump over a stroke between two stored points.
     * History is managed by beginErase()/endErase() in the Canvas, so one
     * continuous eraser gesture is one undo operation.
     */
    fun eraseAt(
        x: Float,
        y: Float,
        radius: Float = 30f
    ) {

        val board = _board.value
        val safeRadius = radius.coerceAtLeast(1f)
        var changed = false

        val newStrokes = mutableListOf<StrokeModel>()

        board.strokes.forEach { stroke ->

            if (stroke.points.isEmpty()) {
                return@forEach
            }

            if (stroke.points.size == 1) {
                val point = stroke.points[0]

                if (distance(point.x, point.y, x, y) <= safeRadius) {
                    changed = true
                } else {
                    newStrokes.add(
                        stroke.copy(
                            points = mutableListOf(point.copy())
                        )
                    )
                }
                return@forEach
            }

            var currentPart = mutableListOf<PointModel>()

            fun flushCurrentPart() {
                if (currentPart.size >= 2) {
                    newStrokes.add(
                        stroke.copy(
                            points =
                                currentPart.map { it.copy() }.toMutableList()
                        )
                    )
                }
                currentPart = mutableListOf()
            }

            val points = stroke.points

            for (i in 0 until points.lastIndex) {
                val p1 = points[i]
                val p2 = points[i + 1]

                val hit =
                    distancePointToSegment(
                        x, y,
                        p1.x, p1.y,
                        p2.x, p2.y
                    ) <= safeRadius

                if (hit) {
                    changed = true
                    flushCurrentPart()
                } else {
                    if (currentPart.isEmpty()) {
                        currentPart.add(p1.copy())
                    }
                    currentPart.add(p2.copy())
                }
            }

            flushCurrentPart()
        }

        // Eraser removes selectable objects completely when touched.
        val newShapes = board.shapes.toMutableList()
        val shapeIndex =
            board.shapes.indexOfLast { shape ->
                val left = minOf(shape.left, shape.right)
                val right = maxOf(shape.left, shape.right)
                val top = minOf(shape.top, shape.bottom)
                val bottom = maxOf(shape.top, shape.bottom)

                x >= left - safeRadius &&
                        x <= right + safeRadius &&
                        y >= top - safeRadius &&
                        y <= bottom + safeRadius
            }

        if (shapeIndex >= 0) {
            newShapes.removeAt(shapeIndex)
            changed = true
        }

        val newImages = board.images.toMutableList()
        val imageIndex =
            board.images.indexOfLast { image ->
                val imageWidth = image.width * image.scale
                val imageHeight = image.height * image.scale

                x >= image.x - safeRadius &&
                        x <= image.x + imageWidth + safeRadius &&
                        y >= image.y - safeRadius &&
                        y <= image.y + imageHeight + safeRadius
            }

        if (imageIndex >= 0) {
            newImages.removeAt(imageIndex)
            changed = true
        }

        val newTexts = board.texts.toMutableList()
        val textIndex =
            board.texts.indexOfLast { text ->
                x >= text.x - safeRadius &&
                        x <= text.x + text.width + safeRadius &&
                        y >= text.y - text.size - safeRadius &&
                        y <= text.y + text.height + safeRadius
            }

        if (textIndex >= 0) {
            newTexts.removeAt(textIndex)
            changed = true
        }

        if (changed) {
            _board.value =
                board.copy(
                    strokes = newStrokes.toMutableList(),
                    shapes = newShapes,
                    texts = newTexts,
                    images = newImages
                )
        }
    }

    private fun distancePointToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {

        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0f && dy == 0f) {
            return distance(px, py, x1, y1)
        }

        val lengthSquared = dx * dx + dy * dy

        var t =
            ((px - x1) * dx + (py - y1) * dy) / lengthSquared

        t = t.coerceIn(0f, 1f)

        val closestX = x1 + t * dx
        val closestY = y1 + t * dy

        return distance(px, py, closestX, closestY)
    }

    private var erasingSession = false

    /** Starts one undoable eraser gesture. */
    fun beginErase() {
        if (!erasingSession) {
            snapshot()
            erasingSession = true
        }
    }

    /** Ends one undoable eraser gesture. */
    fun endErase() {
        erasingSession = false
    }

    // UNDO

    fun undo() {

        // A toolbar click must act immediately. Any unfinished gesture is
        // discarded so it cannot interfere with the history stacks.
        editStartBoard = null

        if (undoStack.isEmpty()) {
            return
        }

        redoStack.addLast(
            copyBoard(_board.value)
        )

        _board.value =
            undoStack.removeLast()
    }


    // REDO


    fun redo() {

        // A toolbar click must act immediately.
        editStartBoard = null

        if (redoStack.isEmpty()) {
            return
        }

        undoStack.addLast(
            copyBoard(_board.value)
        )

        _board.value =
            redoStack.removeLast()
    }


    // REPLACE BOARD

    fun replaceBoard(
        board: WhiteboardModel
    ) {

        snapshot()

        _board.value =
            copyBoard(board)
    }


    // DISTANCE


    private fun distance(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {

        val dx = ax - bx
        val dy = ay - by

        return sqrt(
            dx * dx +
                    dy * dy
        )
    }
}
