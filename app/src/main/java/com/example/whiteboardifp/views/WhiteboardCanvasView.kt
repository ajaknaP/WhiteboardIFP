package com.example.whiteboardifp.views

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.whiteboardifp.models.ImageModel
import com.example.whiteboardifp.models.PointModel
import com.example.whiteboardifp.models.ShapeModel
import com.example.whiteboardifp.models.ShapeType
import com.example.whiteboardifp.models.StrokeModel
import com.example.whiteboardifp.models.TextModel
import com.example.whiteboardifp.viewmodels.Tool
import com.example.whiteboardifp.viewmodels.WhiteboardViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.hypot
import android.graphics.PointF


class WhiteboardCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // VIEW MODEL


    var viewModel: WhiteboardViewModel? = null
        set(value) {
            field = value
            invalidate()
        }

    // PAINTS

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.NORMAL
        )
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        pathEffect = DashPathEffect(
            floatArrayOf(
                dp(6f),
                dp(4f)
            ),
            0f
        )
    }

    // =========================================================
    // PEN STATE
    // =========================================================

    private var currentPath: Path? = null

    private val currentStrokePoints =
        mutableListOf<PointModel>()

    // =========================================================
    // SHAPE PREVIEW
    // =========================================================

    private var shapeStartX = 0f
    private var shapeStartY = 0f

    private var shapeEndX = 0f
    private var shapeEndY = 0f

    // SELECTION


    private enum class SelectionType {
        NONE,
        TEXT,
        IMAGE,
        SHAPE
    }

    private var selectionType =
        SelectionType.NONE

    private var selectedIndex = -1

    // GESTURE


    private enum class GestureMode {
        NONE,
        DRAG,
        PINCH
    }

    private var gestureMode =
        GestureMode.NONE

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var isPinching = false
    private var scaleInProgress = false

    // TWO-FINGER ROTATION
    private var lastRotationAngle = 0f
    private var rotationInProgress = false

    // ROBUST TWO-FINGER SHAPE TRANSFORM
    private var shapeGestureActive = false
    private var lastTwoFingerDistance = 0f
    private var lastTwoFingerAngle = 0f
    private var lastTwoFingerCenterX = 0f
    private var lastTwoFingerCenterY = 0f

    // SMOOTH ERASER
    private var lastEraseX = 0f
    private var lastEraseY = 0f
    private var hasLastErasePoint = false


    // TEXT SCALE LIMITS
    // ========================================================

    private companion object {
        const val MIN_TEXT_SIZE = 2f
        const val MAX_TEXT_SIZE = 1000f

        const val MIN_IMAGE_SCALE = 0.05f
        const val MAX_IMAGE_SCALE = 20f
    }


    // IMAGE CACHE


    private val bitmapCache =
        mutableMapOf<String, Bitmap?>()

    // INIT


    init {
        isFocusable = true
        isClickable = true
    }

    // SCALE GESTURE DETECTOR


    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScaleBegin(
                    detector: ScaleGestureDetector
                ): Boolean {

                    val vm = viewModel ?: return false

                    if (vm.tool != Tool.SELECT) return false

                    if (
                        selectionType != SelectionType.TEXT &&
                        selectionType != SelectionType.IMAGE
                    ) {
                        return false
                    }

                    gestureMode = GestureMode.PINCH
                    isPinching = true
                    scaleInProgress = true

                    parent?.requestDisallowInterceptTouchEvent(true)
                    startTransformHistory()

                    return true
                }

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {

                    if (!scaleInProgress) return false

                    val factor = detector.scaleFactor

                    if (
                        factor.isNaN() ||
                        factor.isInfinite() ||
                        factor <= 0f
                    ) {
                        return false
                    }

                    scaleSelectedObject(factor)
                    invalidate()
                    return true
                }

                override fun onScaleEnd(
                    detector: ScaleGestureDetector
                ) {
                    scaleInProgress = false
                    invalidate()
                }
            }
        )

    // DRAW


    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val vm =
            viewModel
                ?: return

        val board =
            vm.board.value


        // BACKGROUND

        canvas.drawColor(Color.WHITE)


        // IMAGES


        board.images.forEachIndexed { index, image ->

            drawImage(
                canvas,
                image
            )

            if (
                vm.tool == Tool.SELECT &&
                selectionType == SelectionType.IMAGE &&
                selectedIndex == index
            ) {
                drawImageSelection(canvas, image)
            }
        }

        // STROKES

        board.strokes.forEach { stroke ->

            drawStroke(
                canvas,
                stroke
            )
        }


        // SHAPES

        board.shapes.forEachIndexed { index, shape ->

            drawShape(canvas, shape)

            if (
                vm.tool == Tool.SELECT &&
                selectionType == SelectionType.SHAPE &&
                selectedIndex == index
            ) {
                drawShapeSelection(canvas, shape)
            }
        }


        // TEXT


        board.texts.forEachIndexed { index, text ->

            drawText(
                canvas,
                text
            )

            if (
                vm.tool == Tool.SELECT &&
                selectionType ==
                SelectionType.TEXT &&
                selectedIndex == index
            ) {

                drawTextSelection(
                    canvas,
                    text
                )
            }
        }

        // CURRENT PEN


        currentPath?.let { path ->

            strokePaint.color =
                parseColorSafely(
                    vm.color,
                    Color.BLACK
                )

            strokePaint.strokeWidth =
                vm.strokeWidth.coerceIn(
                    1f,
                    100f
                )

            canvas.drawPath(
                path,
                strokePaint
            )
        }


        // SHAPE PREVIEW


        if (
            vm.tool == Tool.SHAPE &&
            gestureMode == GestureMode.DRAG
        ) {

            drawShapePreview(
                canvas,
                vm.shapeType
            )
        }
    }

    // DRAW STROKE


    private fun drawStroke(
        canvas: Canvas,
        stroke: StrokeModel
    ) {

        if (stroke.points.isEmpty()) {
            return
        }

        strokePaint.color =
            parseColorSafely(
                stroke.color,
                Color.BLACK
            )

        strokePaint.strokeWidth =
            stroke.width.coerceIn(
                1f,
                100f
            )

        val path =
            Path()

        val first =
            stroke.points.first()

        path.moveTo(
            first.x,
            first.y
        )

        for (i in 1 until stroke.points.size) {

            val point =
                stroke.points[i]

            path.lineTo(
                point.x,
                point.y
            )
        }

        canvas.drawPath(
            path,
            strokePaint
        )
    }


    // DRAW IMAGE


    private fun drawImage(
        canvas: Canvas,
        image: ImageModel
    ) {

        val bitmap = getBitmap(image.uri) ?: return

        val width = image.width * image.scale
        val height = image.height * image.scale

        if (width <= 0f || height <= 0f) return

        val destination =
            RectF(
                image.x,
                image.y,
                image.x + width,
                image.y + height
            )

        val centerX = destination.centerX()
        val centerY = destination.centerY()

        canvas.save()
        canvas.rotate(image.rotation, centerX, centerY)
        canvas.drawBitmap(
            bitmap,
            null,
            destination,
            imagePaint
        )
        canvas.restore()
    }

    // =========================================================
    // GET BITMAP
    // =========================================================

    private fun getBitmap(
        uriString: String
    ): Bitmap? {

        if (
            bitmapCache.containsKey(
                uriString
            )
        ) {
            return bitmapCache[uriString]
        }

        val bitmap =
            try {

                val uri =
                    Uri.parse(uriString)

                context.contentResolver
                    .openInputStream(uri)
                    ?.use { inputStream ->

                        BitmapFactory
                            .decodeStream(
                                inputStream
                            )
                    }

            } catch (
                exception: Exception
            ) {

                null
            }

        bitmapCache[uriString] =
            bitmap

        return bitmap
    }

    // =========================================================
    // DRAW SHAPE
    // =========================================================

    private fun drawShape(
        canvas: Canvas,
        shape: ShapeModel
    ) {

        shapePaint.color =
            parseColorSafely(
                shape.color,
                Color.BLACK
            )

        shapePaint.strokeWidth =
            shape.strokeWidth.coerceIn(
                1f,
                100f
            )

        val left =
            min(
                shape.left,
                shape.right
            )

        val top =
            min(
                shape.top,
                shape.bottom
            )

        val right =
            max(
                shape.left,
                shape.right
            )

        val bottom =
            max(
                shape.top,
                shape.bottom
            )

        val rect =
            RectF(
                left,
                top,
                right,
                bottom
            )

        canvas.save()
        canvas.rotate(
            shape.rotation,
            rect.centerX(),
            rect.centerY()
        )

        when (shape.type) {

            ShapeType.RECTANGLE -> {

                canvas.drawRect(
                    rect,
                    shapePaint
                )
            }

            ShapeType.CIRCLE -> {

                val radius =
                    min(
                        rect.width(),
                        rect.height()
                    ) / 2f

                if (radius > 0f) {

                    canvas.drawCircle(
                        rect.centerX(),
                        rect.centerY(),
                        radius,
                        shapePaint
                    )
                }
            }

            ShapeType.LINE -> {

                canvas.drawLine(
                    shape.left,
                    shape.top,
                    shape.right,
                    shape.bottom,
                    shapePaint
                )
            }

            ShapeType.POLYGON -> {

                drawPolygon(
                    canvas,
                    rect,
                    shapePaint,
                    shape.sides
                )
            }
        }

        canvas.restore()
    }


    // DRAW SHAPE PREVIEW


    private fun drawShapePreview(
        canvas: Canvas,
        type: ShapeType
    ) {

        val vm =
            viewModel
                ?: return

        shapePaint.color =
            parseColorSafely(
                vm.color,
                Color.BLACK
            )

        shapePaint.strokeWidth =
            vm.strokeWidth.coerceIn(
                1f,
                100f
            )

        val left =
            min(
                shapeStartX,
                shapeEndX
            )

        val top =
            min(
                shapeStartY,
                shapeEndY
            )

        val right =
            max(
                shapeStartX,
                shapeEndX
            )

        val bottom =
            max(
                shapeStartY,
                shapeEndY
            )

        val rect =
            RectF(
                left,
                top,
                right,
                bottom
            )

        when (type) {

            ShapeType.RECTANGLE -> {

                canvas.drawRect(
                    rect,
                    shapePaint
                )
            }

            ShapeType.CIRCLE -> {

                val radius =
                    min(
                        rect.width(),
                        rect.height()
                    ) / 2f

                canvas.drawCircle(
                    rect.centerX(),
                    rect.centerY(),
                    radius,
                    shapePaint
                )
            }

            ShapeType.LINE -> {

                canvas.drawLine(
                    shapeStartX,
                    shapeStartY,
                    shapeEndX,
                    shapeEndY,
                    shapePaint
                )
            }

            ShapeType.POLYGON -> {

                drawPolygon(
                    canvas,
                    rect,
                    shapePaint,
                    6
                )
            }
        }
    }

    // DRAW POLYGON


    private fun drawPolygon(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
        sides: Int
    ) {

        val safeSides =
            sides.coerceAtLeast(3)

        val centerX =
            rect.centerX()

        val centerY =
            rect.centerY()

        val radius =
            min(
                rect.width(),
                rect.height()
            ) / 2f

        if (radius <= 0f) {
            return
        }

        val path =
            Path()

        for (i in 0 until safeSides) {

            val angle =
                -Math.PI / 2.0 +
                        i *
                        2.0 *
                        Math.PI /
                        safeSides

            val pointX =
                centerX +
                        radius *
                        cos(angle).toFloat()

            val pointY =
                centerY +
                        radius *
                        sin(angle).toFloat()

            if (i == 0) {

                path.moveTo(
                    pointX,
                    pointY
                )

            } else {

                path.lineTo(
                    pointX,
                    pointY
                )
            }
        }

        path.close()

        canvas.drawPath(
            path,
            paint
        )
    }

    // DRAW TEXT

    private fun drawText(
        canvas: Canvas,
        text: TextModel
    ) {

        textPaint.color =
            parseColorSafely(text.color, Color.BLACK)

        textPaint.textSize =
            text.size.coerceIn(
                MIN_TEXT_SIZE,
                MAX_TEXT_SIZE
            )

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )

        val lines = text.text.split("\n")
        val metrics = textPaint.fontMetrics
        val lineHeight =
            (metrics.descent - metrics.ascent) * 1.15f

        val bounds = getTextBounds(text)
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        canvas.save()
        canvas.rotate(text.rotation, centerX, centerY)

        lines.forEachIndexed { index, line ->
            val baseline =
                text.y + index * lineHeight

            canvas.drawText(
                line,
                text.x,
                baseline,
                textPaint
            )
        }

        canvas.restore()
    }

    // MEASURE TEXT


    private fun measureTextDimensions(
        value: String,
        size: Float
    ): Pair<Float, Float> {

        textPaint.textSize =
            size.coerceIn(
                MIN_TEXT_SIZE,
                MAX_TEXT_SIZE
            )

        val lines =
            value.split("\n")

        var longestWidth =
            0f

        lines.forEach { line ->

            longestWidth =
                max(
                    longestWidth,
                    textPaint.measureText(
                        line
                    )
                )
        }

        val metrics =
            textPaint.fontMetrics

        val lineHeight =
            (
                    metrics.descent -
                            metrics.ascent
                    ) * 1.15f

        val calculatedWidth =
            longestWidth +
                    dp(20f)

        val calculatedHeight =
            lineHeight *
                    lines.size +
                    dp(12f)

        return Pair(
            max(
                dp(10f),
                calculatedWidth
            ),
            max(
                dp(10f),
                calculatedHeight
            )
        )
    }


    // TEXT BOUNDS

    private fun getTextBounds(
        text: TextModel
    ): RectF {

        textPaint.textSize =
            text.size.coerceIn(
                MIN_TEXT_SIZE,
                MAX_TEXT_SIZE
            )

        val lines =
            text.text.split("\n")

        val metrics =
            textPaint.fontMetrics

        val lineHeight =
            (
                    metrics.descent -
                            metrics.ascent
                    ) * 1.15f

        var longestWidth =
            0f

        lines.forEach { line ->

            longestWidth =
                max(
                    longestWidth,
                    textPaint.measureText(
                        line
                    )
                )
        }

        val measuredWidth =
            longestWidth +
                    dp(20f)

        val measuredHeight =
            lineHeight *
                    lines.size +
                    dp(12f)

        val actualWidth =
            max(
                measuredWidth,
                text.width
            )

        val actualHeight =
            max(
                measuredHeight,
                text.height
            )

        val left =
            text.x -
                    dp(5f)

        val top =
            text.y +
                    metrics.ascent -
                    dp(5f)

        val right =
            text.x +
                    actualWidth +
                    dp(5f)

        val calculatedBottom =
            text.y +
                    (
                            lines.size - 1
                            ) *
                    lineHeight +
                    metrics.descent +
                    dp(5f)

        val minimumBottom =
            top +
                    actualHeight

        val bottom =
            max(
                calculatedBottom,
                minimumBottom
            )

        return RectF(
            left,
            top,
            right,
            bottom
        )
    }


    // TEXT SELECTION


    private fun drawTextSelection(
        canvas: Canvas,
        text: TextModel
    ) {
        val bounds = getTextBounds(text)

        canvas.save()
        canvas.rotate(
            text.rotation,
            bounds.centerX(),
            bounds.centerY()
        )
        canvas.drawRect(bounds, selectionPaint)
        canvas.restore()
    }

    // IMAGE SELECTION
    private fun drawImageSelection(
        canvas: Canvas,
        image: ImageModel
    ) {
        val imageWidth = image.width * image.scale
        val imageHeight = image.height * image.scale

        val rect =
            RectF(
                image.x,
                image.y,
                image.x + imageWidth,
                image.y + imageHeight
            )

        canvas.save()
        canvas.rotate(
            image.rotation,
            rect.centerX(),
            rect.centerY()
        )
        canvas.drawRect(rect, selectionPaint)
        canvas.restore()
    }

    // SHAPE SELECTION
    private fun drawShapeSelection(
        canvas: Canvas,
        shape: ShapeModel
    ) {
        val left = min(shape.left, shape.right)
        val top = min(shape.top, shape.bottom)
        val right = max(shape.left, shape.right)
        val bottom = max(shape.top, shape.bottom)

        val rect = RectF(
            left - dp(8f),
            top - dp(8f),
            right + dp(8f),
            bottom + dp(8f)
        )

        canvas.save()
        canvas.rotate(
            shape.rotation,
            rect.centerX(),
            rect.centerY()
        )
        canvas.drawRect(
            rect,
            selectionPaint
        )
        canvas.restore()
    }

    // TOUCH EVENT


    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        val vm = viewModel ?: return true

        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                parent?.requestDisallowInterceptTouchEvent(true)

                lastTouchX = event.x
                lastTouchY = event.y

                isPinching = false
                scaleInProgress = false
                rotationInProgress = false
                gestureMode = GestureMode.NONE

                when (vm.tool) {

                    Tool.SELECT -> {
                        if (selectObject(event.x, event.y)) {
                            gestureMode = GestureMode.DRAG
                            startTransformHistory()
                        } else {
                            clearSelection()
                        }
                    }

                    Tool.PEN -> {
                        gestureMode = GestureMode.DRAG
                        currentPath = Path().apply {
                            moveTo(event.x, event.y)
                        }
                        currentStrokePoints.clear()
                        currentStrokePoints.add(
                            PointModel(event.x, event.y)
                        )
                    }

                    Tool.ERASER -> {
                        gestureMode = GestureMode.DRAG
                        vm.beginErase()
                        hasLastErasePoint = true
                        lastEraseX = event.x
                        lastEraseY = event.y
                        vm.eraseAt(
                            event.x,
                            event.y,
                            eraserSize()
                        )
                    }

                    Tool.TEXT -> {
                        showTextDialog(event.x, event.y)
                    }

                    Tool.SHAPE -> {
                        gestureMode = GestureMode.DRAG
                        shapeStartX = event.x
                        shapeStartY = event.y
                        shapeEndX = event.x
                        shapeEndY = event.y
                    }
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {

                parent?.requestDisallowInterceptTouchEvent(true)

                if (
                    vm.tool == Tool.SELECT &&
                    selectionType == SelectionType.SHAPE &&
                    selectedIndex in vm.board.value.shapes.indices &&
                    event.pointerCount >= 2
                ) {
                    shapeGestureActive = true
                    isPinching = true
                    rotationInProgress = true
                    scaleInProgress = false
                    gestureMode = GestureMode.PINCH

                    lastTwoFingerDistance =
                        distanceBetween(event)
                    lastTwoFingerAngle =
                        angleBetweenPointers(event)
                    lastTwoFingerCenterX =
                        centerXOfPointers(event)
                    lastTwoFingerCenterY =
                        centerYOfPointers(event)

                    startTransformHistory()
                    return true
                }

                if (
                    vm.tool == Tool.SELECT &&
                    (selectionType == SelectionType.TEXT ||
                            selectionType == SelectionType.IMAGE) &&
                    event.pointerCount >= 2
                ) {
                    lastRotationAngle =
                        angleBetweenPointers(event)
                    rotationInProgress = true
                    isPinching = true
                    gestureMode = GestureMode.PINCH
                    startTransformHistory()
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (
                    event.pointerCount >= 2 &&
                    vm.tool == Tool.SELECT &&
                    selectionType == SelectionType.SHAPE &&
                    shapeGestureActive
                ) {
                    handleShapeTwoFingerTransform(event)
                    invalidate()
                    return true
                }

                if (
                    event.pointerCount >= 2 &&
                    vm.tool == Tool.SELECT &&
                    (selectionType == SelectionType.TEXT ||
                            selectionType == SelectionType.IMAGE)
                ) {
                    handleRotation(event)
                    invalidate()
                    return true
                }

                if (
                    isPinching ||
                    scaleInProgress ||
                    rotationInProgress ||
                    gestureMode == GestureMode.PINCH
                ) {
                    return true
                }

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                when (vm.tool) {

                    Tool.SELECT -> {
                        if (
                            gestureMode == GestureMode.DRAG &&
                            selectionType != SelectionType.NONE &&
                            selectedIndex >= 0
                        ) {
                            moveSelectedObject(dx, dy)
                        }
                    }

                    Tool.PEN -> {
                        currentPath?.lineTo(
                            event.x,
                            event.y
                        )
                        currentStrokePoints.add(
                            PointModel(event.x, event.y)
                        )
                    }

                    Tool.ERASER -> {
                        eraseBetween(
                            lastEraseX,
                            lastEraseY,
                            event.x,
                            event.y
                        )
                        lastEraseX = event.x
                        lastEraseY = event.y
                        hasLastErasePoint = true
                    }

                    Tool.SHAPE -> {
                        shapeEndX = event.x
                        shapeEndY = event.y
                    }

                    Tool.TEXT -> Unit
                }

                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {

                if (event.pointerCount - 1 <= 1) {
                    isPinching = false
                    scaleInProgress = false
                    rotationInProgress = false
                    shapeGestureActive = false
                    gestureMode = GestureMode.NONE
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {

                parent?.requestDisallowInterceptTouchEvent(false)

                when (vm.tool) {

                    Tool.PEN -> {
                        if (currentStrokePoints.size >= 2) {
                            vm.addStroke(
                                currentStrokePoints.toList()
                            )
                        }
                        currentPath = null
                        currentStrokePoints.clear()
                    }

                    Tool.ERASER -> {
                        vm.endErase()
                        hasLastErasePoint = false
                    }

                    Tool.SHAPE -> {
                        val left = min(shapeStartX, shapeEndX)
                        val top = min(shapeStartY, shapeEndY)
                        val right = max(shapeStartX, shapeEndX)
                        val bottom = max(shapeStartY, shapeEndY)

                        if (
                            abs(right - left) > 2f ||
                            abs(bottom - top) > 2f
                        ) {
                            val shape =
                                if (vm.shapeType == ShapeType.LINE) {
                                    ShapeModel(
                                        type = vm.shapeType,
                                        left = shapeStartX,
                                        top = shapeStartY,
                                        right = shapeEndX,
                                        bottom = shapeEndY,
                                        color = vm.color,
                                        strokeWidth = vm.strokeWidth,
                                        sides = 6
                                    )
                                } else {
                                    ShapeModel(
                                        type = vm.shapeType,
                                        left = left,
                                        top = top,
                                        right = right,
                                        bottom = bottom,
                                        color = vm.color,
                                        strokeWidth = vm.strokeWidth,
                                        sides = 6
                                    )
                                }

                            vm.addShape(shape)
                        }
                    }

                    else -> Unit
                }

                isPinching = false
                scaleInProgress = false
                rotationInProgress = false
                shapeGestureActive = false
                gestureMode = GestureMode.NONE
                finishTransformHistory()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {

                parent?.requestDisallowInterceptTouchEvent(false)

                if (vm.tool == Tool.ERASER) {
                    vm.endErase()
                }

                currentPath = null
                currentStrokePoints.clear()
                hasLastErasePoint = false
                isPinching = false
                scaleInProgress = false
                rotationInProgress = false
                shapeGestureActive = false
                gestureMode = GestureMode.NONE
                viewModel?.cancelEdit()
                transformHistoryStarted = false
                invalidate()
                return true
            }
        }

        return true
    }

    private fun distanceBetween(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    private fun centerXOfPointers(event: MotionEvent): Float {
        return (event.getX(0) + event.getX(1)) / 2f
    }

    private fun centerYOfPointers(event: MotionEvent): Float {
        return (event.getY(0) + event.getY(1)) / 2f
    }

    private fun angleBetweenPointers(event: MotionEvent): Float {
        return angleBetween(
            event.getX(0), event.getY(0),
            event.getX(1), event.getY(1)
        )
    }

    private fun handleShapeTwoFingerTransform(event: MotionEvent) {
        if (event.pointerCount < 2) return

        val vm = viewModel ?: return
        val index = selectedIndex
        val old = vm.board.value.shapes.getOrNull(index) ?: return

        val currentDistance = distanceBetween(event)
        val currentAngle = angleBetweenPointers(event)
        val currentCenterX = centerXOfPointers(event)
        val currentCenterY = centerYOfPointers(event)

        if (lastTwoFingerDistance <= 0f || currentDistance <= 0f) {
            lastTwoFingerDistance = currentDistance
            lastTwoFingerAngle = currentAngle
            lastTwoFingerCenterX = currentCenterX
            lastTwoFingerCenterY = currentCenterY
            return
        }

        val factor = (currentDistance / lastTwoFingerDistance)
            .coerceIn(0.02f, 50f)
        val angleDelta = normalizeAngleDelta(currentAngle - lastTwoFingerAngle)
        val moveX = currentCenterX - lastTwoFingerCenterX
        val moveY = currentCenterY - lastTwoFingerCenterY

        val centerX = (old.left + old.right) / 2f + moveX
        val centerY = (old.top + old.bottom) / 2f + moveY
        val halfW = abs(old.right - old.left) / 2f
        val halfH = abs(old.bottom - old.top) / 2f

        val maxHalf = max(width.toFloat(), height.toFloat()).coerceAtLeast(dp(100f)) * 20f
        val newHalfW = (halfW * factor).coerceIn(dp(2f), maxHalf)
        val newHalfH = (halfH * factor).coerceIn(dp(2f), maxHalf)

        vm.updateShape(
            index,
            old.copy(
                left = centerX - newHalfW,
                top = centerY - newHalfH,
                right = centerX + newHalfW,
                bottom = centerY + newHalfH,
                rotation = normalizeRotation(old.rotation + angleDelta)
            )
        )

        lastTwoFingerDistance = currentDistance
        lastTwoFingerAngle = currentAngle
        lastTwoFingerCenterX = currentCenterX
        lastTwoFingerCenterY = currentCenterY
    }

    private fun angleBetween(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        return Math.toDegrees(
            atan2(
                (y2 - y1).toDouble(),
                (x2 - x1).toDouble()
            )
        ).toFloat()
    }

    private fun normalizeAngleDelta(delta: Float): Float {
        var value = delta
        while (value > 180f) value -= 360f
        while (value < -180f) value += 360f
        return value
    }

    private fun normalizeRotation(value: Float): Float {
        return (value % 360f + 360f) % 360f
    }

    private fun handleRotation(event: MotionEvent) {
        if (event.pointerCount < 2) return

        val x1 = event.getX(0)
        val y1 = event.getY(0)
        val x2 = event.getX(1)
        val y2 = event.getY(1)

        val currentAngle =
            angleBetween(x1, y1, x2, y2)

        val delta =
            normalizeAngleDelta(
                currentAngle - lastRotationAngle
            )

        lastRotationAngle = currentAngle

        val vm = viewModel ?: return

        when (selectionType) {

            SelectionType.TEXT -> {
                val text =
                    vm.board.value.texts
                        .getOrNull(selectedIndex)
                        ?: return

                vm.updateText(
                    selectedIndex,
                    text.copy(
                        rotation =
                            normalizeRotation(
                                text.rotation + delta
                            )
                    )
                )
            }

            SelectionType.IMAGE -> {
                val image =
                    vm.board.value.images
                        .getOrNull(selectedIndex)
                        ?: return

                vm.updateImage(
                    selectedIndex,
                    image.copy(
                        rotation =
                            normalizeRotation(
                                image.rotation + delta
                            )
                    )
                )
            }

            SelectionType.SHAPE -> {
                val shape =
                    vm.board.value.shapes
                        .getOrNull(selectedIndex)
                        ?: return

                vm.updateShape(
                    selectedIndex,
                    shape.copy(
                        rotation =
                            normalizeRotation(
                                shape.rotation + delta
                            )
                    )
                )
            }

            SelectionType.NONE -> Unit
        }
    }

    // SELECT OBJECT


    private fun selectObject(
        x: Float,
        y: Float
    ): Boolean {

        val vm = viewModel ?: return false
        val board = vm.board.value

        // Text is visually on top.
        for (index in board.texts.indices.reversed()) {
            val text = board.texts[index]
            if (isPointInsideRotatedText(text, x, y)) {
                selectionType = SelectionType.TEXT
                selectedIndex = index
                invalidate()
                return true
            }
        }

        // Image.
        for (index in board.images.indices.reversed()) {
            val image = board.images[index]
            if (isPointInsideRotatedImage(image, x, y)) {
                selectionType = SelectionType.IMAGE
                selectedIndex = index
                invalidate()
                return true
            }
        }

        // Shape.
        for (index in board.shapes.indices.reversed()) {
            val shape = board.shapes[index]
            if (isPointInsideShape(shape, x, y)) {
                selectionType = SelectionType.SHAPE
                selectedIndex = index
                invalidate()
                return true
            }
        }

        return false
    }

    private fun isPointInsideRotatedText(
        text: TextModel,
        x: Float,
        y: Float
    ): Boolean {
        val bounds = getTextBounds(text)
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val local = rotatePoint(
            x, y, centerX, centerY, -text.rotation
        )
        return bounds.contains(local.x, local.y)
    }

    private fun isPointInsideRotatedImage(
        image: ImageModel,
        x: Float,
        y: Float
    ): Boolean {
        val w = image.width * image.scale
        val h = image.height * image.scale
        val rect = RectF(
            image.x,
            image.y,
            image.x + w,
            image.y + h
        )
        val local = rotatePoint(
            x,
            y,
            rect.centerX(),
            rect.centerY(),
            -image.rotation
        )
        return rect.contains(local.x, local.y)
    }

    private fun isPointInsideShape(
        shape: ShapeModel,
        x: Float,
        y: Float
    ): Boolean {
        val padding = dp(20f)
        val left = min(shape.left, shape.right)
        val right = max(shape.left, shape.right)
        val top = min(shape.top, shape.bottom)
        val bottom = max(shape.top, shape.bottom)
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f

        // Convert the touch point into the shape's unrotated coordinate space.
        val local = rotatePoint(
            x,
            y,
            centerX,
            centerY,
            -shape.rotation
        )

        if (shape.type == ShapeType.LINE) {
            return distancePointToSegment(
                local.x, local.y,
                shape.left, shape.top,
                shape.right, shape.bottom
            ) <= padding
        }

        return local.x >= left - padding &&
                local.x <= right + padding &&
                local.y >= top - padding &&
                local.y <= bottom + padding
    }

    private fun rotatePoint(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        degrees: Float
    ): PointF {
        val radians = Math.toRadians(degrees.toDouble())
        val cosValue = cos(radians).toFloat()
        val sinValue = sin(radians).toFloat()
        val dx = x - centerX
        val dy = y - centerY

        return PointF(
            centerX + dx * cosValue - dy * sinValue,
            centerY + dx * sinValue + dy * cosValue
        )
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
            return hypot(px - x1, py - y1)
        }

        val lengthSquared = dx * dx + dy * dy
        var t =
            ((px - x1) * dx + (py - y1) * dy) / lengthSquared
        t = t.coerceIn(0f, 1f)

        val closestX = x1 + t * dx
        val closestY = y1 + t * dy

        return hypot(px - closestX, py - closestY)
    }

    // MOVE SELECTED OBJECT


    private fun moveSelectedObject(
        dx: Float,
        dy: Float
    ) {

        val vm = viewModel ?: return
        val board = vm.board.value
        if (selectedIndex < 0) return

        when (selectionType) {

            SelectionType.TEXT -> {
                val old = board.texts.getOrNull(selectedIndex) ?: return
                vm.updateText(
                    selectedIndex,
                    old.copy(
                        x = old.x + dx,
                        y = old.y + dy
                    )
                )
            }

            SelectionType.IMAGE -> {
                val old = board.images.getOrNull(selectedIndex) ?: return
                vm.updateImage(
                    selectedIndex,
                    old.copy(
                        x = old.x + dx,
                        y = old.y + dy
                    )
                )
            }

            SelectionType.SHAPE -> {
                val old = board.shapes.getOrNull(selectedIndex) ?: return
                vm.updateShape(
                    selectedIndex,
                    old.copy(
                        left = old.left + dx,
                        top = old.top + dy,
                        right = old.right + dx,
                        bottom = old.bottom + dy
                    )
                )
            }

            SelectionType.NONE -> Unit
        }
    }

    // SCALE SELECTED OBJECT

    private fun scaleSelectedObject(
        factor: Float
    ) {

        if (
            factor <= 0f ||
            factor.isNaN() ||
            factor.isInfinite()
        ) {
            return
        }

        when (selectionType) {

            SelectionType.TEXT -> {

                scaleSelectedText(
                    factor
                )
            }

            SelectionType.IMAGE -> {

                scaleSelectedImage(
                    factor
                )
            }

            SelectionType.SHAPE -> {
                scaleSelectedShape(factor)
            }

            SelectionType.NONE -> {
                // Nothing.
            }
        }
    }

    // SCALE TEXT


    private fun scaleSelectedText(
        factor: Float
    ) {

        val vm =
            viewModel
                ?: return

        val board =
            vm.board.value

        if (
            selectedIndex !in
            board.texts.indices
        ) {
            return
        }

        val old =
            board.texts[selectedIndex]

        if (old.size <= 0f) {
            return
        }

        val newSize =
            (
                    old.size * factor
                    ).coerceIn(
                    MIN_TEXT_SIZE,
                    MAX_TEXT_SIZE
                )

        if (
            abs(
                newSize -
                        old.size
            ) < 0.001f
        ) {
            return
        }


        val oldDimensions =
            measureTextDimensions(
                old.text,
                old.size
            )

        val newDimensions =
            measureTextDimensions(
                old.text,
                newSize
            )

        val oldWidth =
            oldDimensions.first

        val oldHeight =
            oldDimensions.second

        val newWidth =
            newDimensions.first

        val newHeight =
            newDimensions.second

        val oldLeft =
            old.x

        val oldTop =
            old.y +
                    textPaint.apply {
                        textSize =
                            old.size.coerceIn(
                                MIN_TEXT_SIZE,
                                MAX_TEXT_SIZE
                            )
                    }.fontMetrics.ascent

        val oldCenterX =
            oldLeft +
                    oldWidth / 2f

        val oldCenterY =
            oldTop +
                    oldHeight / 2f

        val newLeft =
            oldCenterX -
                    newWidth / 2f

        val newTop =
            oldCenterY -
                    newHeight / 2f

        textPaint.textSize =
            newSize

        val newMetrics =
            textPaint.fontMetrics

        val newBaseline =
            newTop -
                    newMetrics.ascent

        val updated =
            old.copy(
                x = newLeft,
                y = newBaseline,
                size = newSize,
                width = newWidth,
                height = newHeight
            )

        vm.updateText(
            selectedIndex,
            updated
        )
    }

    // SCALE IMAGE


    private fun scaleSelectedImage(
        factor: Float
    ) {

        val vm =
            viewModel
                ?: return

        val board =
            vm.board.value

        if (
            selectedIndex !in
            board.images.indices
        ) {
            return
        }

        val old =
            board.images[selectedIndex]

        val oldWidth =
            old.width *
                    old.scale

        val oldHeight =
            old.height *
                    old.scale

        val oldCenterX =
            old.x +
                    oldWidth / 2f

        val oldCenterY =
            old.y +
                    oldHeight / 2f

        val newScale =
            (
                    old.scale * factor
                    ).coerceIn(
                    MIN_IMAGE_SCALE,
                    MAX_IMAGE_SCALE
                )

        if (
            abs(
                newScale -
                        old.scale
            ) < 0.0001f
        ) {
            return
        }

        val newWidth =
            old.width *
                    newScale

        val newHeight =
            old.height *
                    newScale

        val newX =
            oldCenterX -
                    newWidth / 2f

        val newY =
            oldCenterY -
                    newHeight / 2f

        vm.updateImage(
            selectedIndex,
            old.copy(
                x = newX,
                y = newY,
                scale = newScale
            )
        )
    }

    // SCALE SHAPE

    private fun scaleSelectedShape(
        factor: Float
    ) {
        val vm = viewModel ?: return
        val old = vm.board.value.shapes.getOrNull(selectedIndex) ?: return

        val safeFactor = factor.coerceIn(0.05f, 20f)
        val centerX = (old.left + old.right) / 2f
        val centerY = (old.top + old.bottom) / 2f
        val halfWidth = abs(old.right - old.left) / 2f
        val halfHeight = abs(old.bottom - old.top) / 2f

        val maxHalfWidth = max(width.toFloat(), dp(100f)) * 10f
        val maxHalfHeight = max(height.toFloat(), dp(100f)) * 10f

        val newHalfWidth = (halfWidth * safeFactor)
            .coerceIn(dp(2f), maxHalfWidth)
        val newHalfHeight = (halfHeight * safeFactor)
            .coerceIn(dp(2f), maxHalfHeight)

        vm.updateShape(
            selectedIndex,
            old.copy(
                left = centerX - newHalfWidth,
                top = centerY - newHalfHeight,
                right = centerX + newHalfWidth,
                bottom = centerY + newHalfHeight
            )
        )
    }

    // HISTORY

    private var transformHistoryStarted =
        false

    private fun startTransformHistory() {

        if (transformHistoryStarted) {
            return
        }

        transformHistoryStarted = true

        viewModel?.beginEdit()
    }

    private fun finishTransformHistory() {

        if (!transformHistoryStarted) {
            return
        }

        transformHistoryStarted = false
        viewModel?.commitEdit()
    }


    // ERASER

    private fun eraserSize(): Float {
        val vm = viewModel ?: return dp(24f)

        return max(
            dp(24f),
            vm.strokeWidth * 4f
        ).coerceAtMost(dp(120f))
    }

    private fun eraseBetween(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ) {
        val vm = viewModel ?: return

        val radius = eraserSize()
        val distance = hypot(x2 - x1, y2 - y1)
        val step = max(dp(4f), radius / 3f)
        val count = max(1, kotlin.math.ceil(distance / step).toInt())

        for (i in 0..count) {
            val t = i.toFloat() / count.toFloat()
            val x = x1 + (x2 - x1) * t
            val y = y1 + (y2 - y1) * t
            vm.eraseAt(x, y, radius)
        }
    }

    // CLEAR SELECTION


    fun clearSelection() {

        selectionType =
            SelectionType.NONE

        selectedIndex = -1

        invalidate()
    }


    // SELECT IMAGE


    private fun selectImage(
        index: Int
    ) {

        val vm =
            viewModel
                ?: return

        if (
            index !in
            vm.board.value.images.indices
        ) {
            return
        }

        selectionType =
            SelectionType.IMAGE

        selectedIndex =
            index

        vm.tool =
            Tool.SELECT

        invalidate()
    }


    // SELECT TEXT


    private fun selectText(
        index: Int
    ) {

        val vm =
            viewModel
                ?: return

        if (
            index !in
            vm.board.value.texts.indices
        ) {
            return
        }

        selectionType =
            SelectionType.TEXT

        selectedIndex =
            index

        vm.tool =
            Tool.SELECT

        invalidate()
    }


    // DELETE SELECTED


    fun deleteSelected() {

        val vm =
            viewModel
                ?: return

        when (selectionType) {

            SelectionType.TEXT -> {

                if (
                    selectedIndex in
                    vm.board.value.texts.indices
                ) {

                    vm.deleteText(
                        selectedIndex
                    )
                }
            }

            SelectionType.IMAGE -> {

                if (
                    selectedIndex in
                    vm.board.value.images.indices
                ) {

                    vm.deleteImage(
                        selectedIndex
                    )
                }
            }

            SelectionType.SHAPE -> {

                if (
                    selectedIndex in
                    vm.board.value.shapes.indices
                ) {

                    vm.deleteShape(
                        selectedIndex
                    )
                }
            }

            SelectionType.NONE -> {
                return
            }
        }

        clearSelection()
    }


    // PRELOAD IMAGE


    fun preloadImage(
        uri: String
    ) {

        getBitmap(uri)

        invalidate()
    }


    // SHOW TEXT DIALOG


    private fun showTextDialog(
        x: Float,
        y: Float
    ) {

        val vm =
            viewModel
                ?: return

        val container =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20f).toInt(),
                    dp(10f).toInt(),
                    dp(20f).toInt(),
                    dp(10f).toInt()
                )
            }

        val editText =
            EditText(context).apply {

                hint =
                    "Enter text"

                minLines = 3

                gravity =
                    Gravity.TOP

                inputType =
                    InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE

                setSingleLine(false)
            }

        container.addView(
            editText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(120f).toInt()
            )
        )

        val sizeLabel =
            TextView(context).apply {

                text =
                    "Text size"

                textSize =
                    14f

                setPadding(
                    0,
                    dp(10f).toInt(),
                    0,
                    dp(4f).toInt()
                )
            }

        container.addView(
            sizeLabel
        )

        val seekBar =
            SeekBar(context).apply {

                max = 100

                progress = 34
            }

        container.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50f).toInt()
            )
        )

        val currentSizeLabel =
            TextView(context).apply {

                text =
                    "Size: 34"

                textSize =
                    13f
            }

        container.addView(
            currentSizeLabel
        )

        seekBar.setOnSeekBarChangeListener(
            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    val size =
                        progress.coerceAtLeast(
                            2
                        )

                    currentSizeLabel.text =
                        "Size: $size"
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }
            }
        )

        val dialog =
            AlertDialog.Builder(context)
                .setTitle("Add Text")
                .setView(container)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Add",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val value =
                    editText.text
                        .toString()
                        .trim()

                if (value.isEmpty()) {

                    editText.error =
                        "Enter text"

                    return@setOnClickListener
                }

                val size =
                    seekBar.progress
                        .coerceAtLeast(2)
                        .toFloat()

                val dimensions =
                    measureTextDimensions(
                        value,
                        size
                    )

                val textWidth =
                    dimensions.first

                val textHeight =
                    dimensions.second

                textPaint.textSize =
                    size

                val metrics =
                    textPaint.fontMetrics

                val baseline =
                    y -
                            metrics.ascent

                val text =
                    TextModel(
                        text = value,

                        x = x,

                        y = baseline,

                        color = vm.color,

                        size = size,

                        width = textWidth,

                        height = textHeight
                    )

                vm.addText(
                    text
                )

                vm.tool =
                    Tool.SELECT

                selectionType =
                    SelectionType.TEXT

                selectedIndex =
                    vm.board.value.texts.size - 1

                dialog.dismiss()

                invalidate()
            }
        }

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams
                .SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        dialog.show()

        editText.requestFocus()

        editText.postDelayed({

            val inputMethodManager =
                context.getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

            inputMethodManager.showSoftInput(
                editText,
                InputMethodManager.SHOW_IMPLICIT
            )

        }, 200)
    }


    // COLOR


    private fun parseColorSafely(
        value: String?,
        fallback: Int
    ): Int {

        if (value.isNullOrBlank()) {
            return fallback
        }

        return try {

            value.toColorInt()

        } catch (
            exception: Exception
        ) {

            fallback
        }
    }


    // DP


    private fun dp(
        value: Float
    ): Float {

        return value *
                resources
                    .displayMetrics
                    .density
    }

    // DETACHED


    override fun onDetachedFromWindow() {

        bitmapCache.values
            .filterNotNull()
            .forEach { bitmap ->

                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

        bitmapCache.clear()

        super.onDetachedFromWindow()
    }
}
