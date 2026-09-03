package com.example.whiteboardifp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.whiteboardifp.databinding.ActivityMainBinding
import com.example.whiteboardifp.models.ImageModel
import com.example.whiteboardifp.models.ShapeType
import com.example.whiteboardifp.models.WhiteboardModel
import com.example.whiteboardifp.services.WhiteboardStorage
import com.example.whiteboardifp.viewmodels.Tool
import com.example.whiteboardifp.viewmodels.WhiteboardViewModel
import com.example.whiteboardifp.views.WhiteboardCanvasView
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var canvasView: WhiteboardCanvasView

    private lateinit var storage: WhiteboardStorage

    private val viewModel: WhiteboardViewModel by viewModels()

    private var cameraUri: Uri? = null


    // ============================================================
    // COLORS
    // ============================================================

    private val colors = listOf(
        "#111827",
        "#EF4444",
        "#2563EB",
        "#16A34A",
        "#F59E0B",
        "#9333EA",
        "#EC4899",
        "#14B8A6"
    )


    // ============================================================
    // GALLERY
    // ============================================================

    private val galleryLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri != null) {
                handleSelectedImage(uri)
            }
        }


    // ============================================================
    // CAMERA
    // ============================================================

    private val cameraLauncher =
        registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success: Boolean ->

            if (!success) {

                Toast.makeText(
                    this,
                    "Photo capture cancelled.",
                    Toast.LENGTH_SHORT
                ).show()

                return@registerForActivityResult
            }

            val capturedUri = cameraUri

            if (capturedUri == null) {

                Toast.makeText(
                    this,
                    "Camera image URI is unavailable.",
                    Toast.LENGTH_LONG
                ).show()

                return@registerForActivityResult
            }

            handleSelectedImage(capturedUri)
        }


    // ============================================================
    // CAMERA PERMISSION
    // ============================================================

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->

            if (granted) {

                openCamera()

            } else {

                Toast.makeText(
                    this,
                    "Camera permission is required.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        storage =
            WhiteboardStorage(this)

        setupCanvas()

        setupColors()

        setupTools()

        setupActions()

        observeBoard()
    }


    // ============================================================
    // CANVAS SETUP
    // ============================================================

    private fun setupCanvas() {

        canvasView = binding.canvasView

        canvasView.viewModel = viewModel

        /*
         * Wait until the Canvas View has been measured.
         */
        canvasView.post {

            updateCanvasSize()
        }

        /*
         * Also listen for size changes.
         *
         * This is useful when the screen rotates or when
         * the layout changes.
         */
        canvasView.addOnLayoutChangeListener { _, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom ->

            val width = right - left
            val height = bottom - top

            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop

            if (
                width > 0 &&
                height > 0 &&
                (width != oldWidth || height != oldHeight)
            ) {

                updateCanvasSize()
            }
        }
    }


    // ============================================================
    // UPDATE CANVAS SIZE
    // ============================================================

    /**
     * Synchronizes the actual WhiteboardCanvasView dimensions
     * with the WhiteboardViewModel.
     *
     * This is the main fix for:
     *
     * "Canvas size is invalid"
     */
    private fun updateCanvasSize(): Boolean {

        if (!::canvasView.isInitialized) {
            return false
        }

        val width =
            canvasView.measuredWidth

        val height =
            canvasView.measuredHeight

        if (width <= 0 || height <= 0) {
            return false
        }

        viewModel.setCanvasSize(
            width,
            height
        )

        return true
    }


    // ============================================================
    // OBSERVE BOARD
    // ============================================================

    private fun observeBoard() {

        lifecycleScope.launch {

            viewModel.board.collect {

                canvasView.invalidate()
            }
        }
    }


    // ============================================================
    // COLORS
    // ============================================================

    private fun setupColors() {

        binding.colorContainer.removeAllViews()

        colors.forEach { hex ->

            val button =
                TextView(this).apply {

                    text = "●"

                    textSize = 28f

                    gravity = Gravity.CENTER

                    setTextColor(
                        Color.parseColor(hex)
                    )

                    setBackgroundColor(
                        Color.TRANSPARENT
                    )

                    includeFontPadding = false

                    contentDescription =
                        "Color $hex"

                    setPadding(
                        dpToPx(2),
                        0,
                        dpToPx(2),
                        0
                    )

                    setOnClickListener {

                        viewModel.color = hex

                        /*
                         * Selecting a color changes
                         * eraser back to pen.
                         */
                        if (viewModel.tool == Tool.ERASER) {

                            viewModel.tool =
                                Tool.PEN
                        }

                        updateToolSelection()

                        canvasView.invalidate()
                    }
                }

            val size =
                dpToPx(48)

            val params =
                LinearLayout.LayoutParams(
                    size,
                    size
                )

            params.setMargins(
                dpToPx(3),
                0,
                dpToPx(3),
                0
            )

            binding.colorContainer.addView(
                button,
                params
            )
        }
    }


    // ============================================================
    // TOOLS
    // ============================================================

    private fun setupTools() {

        // --------------------------------------------------------
        // SELECT
        // --------------------------------------------------------

        binding.selectButton.setOnClickListener {

            viewModel.tool =
                Tool.SELECT

            canvasView.clearSelection()

            updateToolSelection()

            canvasView.invalidate()
        }


        // --------------------------------------------------------
        // PEN
        // --------------------------------------------------------

        binding.penButton.setOnClickListener {

            viewModel.tool =
                Tool.PEN

            canvasView.clearSelection()

            updateToolSelection()

            canvasView.invalidate()
        }


        // --------------------------------------------------------
        // ERASER
        // --------------------------------------------------------

        binding.eraserButton.setOnClickListener {

            viewModel.tool =
                Tool.ERASER

            canvasView.clearSelection()

            updateToolSelection()

            canvasView.invalidate()
        }


        // --------------------------------------------------------
        // TEXT
        // --------------------------------------------------------

        binding.textButton.setOnClickListener {

            viewModel.tool =
                Tool.TEXT

            canvasView.clearSelection()

            updateToolSelection()

            canvasView.invalidate()
        }


        // --------------------------------------------------------
        // SHAPE
        // --------------------------------------------------------

        binding.shapeButton.setOnClickListener {

            showShapeChooser()
        }


        // --------------------------------------------------------
        // DELETE
        // --------------------------------------------------------

        binding.deleteButton.setOnClickListener {

            canvasView.deleteSelected()

            canvasView.invalidate()
        }


        // --------------------------------------------------------
        // STROKE SIZE
        // --------------------------------------------------------

        binding.strokeSeekBar.setOnSeekBarChangeListener(

            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    viewModel.strokeWidth =
                        progress
                            .coerceAtLeast(1)
                            .toFloat()
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

        if (binding.strokeSeekBar.progress < 1) {

            binding.strokeSeekBar.progress = 1
        }

        updateToolSelection()
    }


    // ============================================================
    // TOOL SELECTION UI
    // ============================================================

    private fun updateToolSelection() {

        setToolButtonNormal(
            binding.selectButton
        )

        setToolButtonNormal(
            binding.penButton
        )

        setToolButtonNormal(
            binding.eraserButton
        )

        setToolButtonNormal(
            binding.textButton
        )

        setToolButtonNormal(
            binding.shapeButton
        )

        when (viewModel.tool) {

            Tool.SELECT -> {

                setToolButtonSelected(
                    binding.selectButton
                )
            }

            Tool.PEN -> {

                setToolButtonSelected(
                    binding.penButton
                )
            }

            Tool.ERASER -> {

                setToolButtonSelected(
                    binding.eraserButton
                )
            }

            Tool.TEXT -> {

                setToolButtonSelected(
                    binding.textButton
                )
            }

            Tool.SHAPE -> {

                setToolButtonSelected(
                    binding.shapeButton
                )
            }

            else -> {
                // No special UI.
            }
        }
    }


    private fun setToolButtonNormal(
        button: ImageButton
    ) {

        button.setBackgroundResource(
            R.drawable.bg_tool_icon
        )

        button.imageTintList =
            ContextCompat.getColorStateList(
                this,
                android.R.color.white
            )
    }


    private fun setToolButtonSelected(
        button: ImageButton
    ) {

        button.setBackgroundResource(
            R.drawable.bg_tool_selected
        )

        button.imageTintList =
            ContextCompat.getColorStateList(
                this,
                android.R.color.white
            )
    }


    // ============================================================
    // ACTIONS
    // ============================================================

    private fun setupActions() {

        // --------------------------------------------------------
        // UNDO
        // --------------------------------------------------------

        binding.undoButton.setOnClickListener {

            viewModel.undo()

            canvasView.invalidate()
        }


        // --------------------------------------------------------
        // REDO
        // --------------------------------------------------------

        binding.redoButton.setOnClickListener {

            viewModel.redo()

            canvasView.invalidate()
        }


        // --------------------------------------------------------
        // SAVE
        // --------------------------------------------------------

        binding.saveButton.setOnClickListener {

            saveBoard()
        }


        // --------------------------------------------------------
        // LOAD
        // --------------------------------------------------------

        binding.loadButton.setOnClickListener {

            showLoadDialog()
        }


        // --------------------------------------------------------
        // ADD IMAGE
        // --------------------------------------------------------

        binding.exportButton.setOnClickListener {

            showImageSourceDialog()
        }
    }


    // ============================================================
    // SHAPE CHOOSER
    // ============================================================

    private fun showShapeChooser() {

        val names =
            arrayOf(
                "Rectangle",
                "Circle",
                "Line",
                "Polygon"
            )

        val shapeTypes =
            ShapeType.values()

        AlertDialog.Builder(this)
            .setTitle("Choose Shape")
            .setItems(names) { _, which ->

                if (
                    which >= 0 &&
                    which < shapeTypes.size
                ) {

                    viewModel.shapeType =
                        shapeTypes[which]

                    viewModel.tool =
                        Tool.SHAPE

                    canvasView.clearSelection()

                    updateToolSelection()

                    canvasView.invalidate()
                }
            }
            .show()
    }


    // ============================================================
    // IMAGE SOURCE
    // ============================================================

    private fun showImageSourceDialog() {

        AlertDialog.Builder(this)
            .setTitle("Add Image")
            .setItems(
                arrayOf(
                    "Gallery",
                    "Camera"
                )
            ) { _, which ->

                when (which) {

                    0 -> openGallery()

                    1 -> checkCameraPermission()
                }
            }
            .show()
    }


    // ============================================================
    // GALLERY
    // ============================================================

    private fun openGallery() {

        galleryLauncher.launch(
            "image/*"
        )
    }


    // ============================================================
    // CAMERA PERMISSION
    // ============================================================

    private fun checkCameraPermission() {

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {

            openCamera()

        } else {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    // ============================================================
    // CAMERA
    // ============================================================

    private fun openCamera() {

        try {

            val imageFile =
                File.createTempFile(
                    "whiteboard_camera_",
                    ".jpg",
                    cacheDir
                )

            val uri =
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    imageFile
                )

            cameraUri = uri

            cameraLauncher.launch(uri)

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Unable to open camera: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // ============================================================
    // HANDLE IMAGE
    // ============================================================

    private fun handleSelectedImage(
        uri: Uri
    ) {

        try {

            /*
             * Try to keep permission for gallery images.
             */
            try {

                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

            } catch (_: Exception) {

                /*
                 * GetContent does not always provide
                 * persistable permission.
                 */
            }


            // ----------------------------------------------------
            // IMAGE DIMENSIONS
            // ----------------------------------------------------

            val dimensions =
                getImageDimensions(uri)

            if (dimensions == null) {

                Toast.makeText(
                    this,
                    "Unable to read image.",
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            val originalWidth =
                dimensions.first.toFloat()

            val originalHeight =
                dimensions.second.toFloat()

            if (
                originalWidth <= 0f ||
                originalHeight <= 0f
            ) {

                Toast.makeText(
                    this,
                    "Invalid image dimensions.",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            // ----------------------------------------------------
            // MAKE SURE CANVAS IS READY
            // ----------------------------------------------------

            if (!updateCanvasSize()) {

                Toast.makeText(
                    this,
                    "Canvas is not ready. Please try again.",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            val canvasWidth =
                canvasView.measuredWidth.toFloat()

            val canvasHeight =
                canvasView.measuredHeight.toFloat()


            // ----------------------------------------------------
            // MAX IMAGE SIZE
            // ----------------------------------------------------

            val maxWidth =
                canvasWidth * 0.60f

            val maxHeight =
                canvasHeight * 0.50f

            if (
                maxWidth <= 0f ||
                maxHeight <= 0f
            ) {

                Toast.makeText(
                    this,
                    "Canvas size is invalid.",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            // ----------------------------------------------------
            // SCALE IMAGE
            // ----------------------------------------------------

            val scale =
                min(
                    maxWidth / originalWidth,
                    maxHeight / originalHeight
                ).coerceAtMost(1f)


            val width =
                (originalWidth * scale)
                    .coerceAtLeast(100f)


            val height =
                (originalHeight * scale)
                    .coerceAtLeast(100f)


            // ----------------------------------------------------
            // CENTER IMAGE
            // ----------------------------------------------------

            val x =
                (canvasWidth - width) / 2f

            val y =
                (canvasHeight - height) / 2f


            // ----------------------------------------------------
            // ADD IMAGE
            // ----------------------------------------------------

            viewModel.addImage(

                ImageModel(
                    uri = uri.toString(),
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    scale = 1f
                )
            )


            // ----------------------------------------------------
            // SELECT TOOL
            // ----------------------------------------------------

            viewModel.tool =
                Tool.SELECT

            updateToolSelection()

            canvasView.invalidate()


            Toast.makeText(
                this,
                "Image added. Select it to move or resize.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Unable to add image: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // ============================================================
    // IMAGE DIMENSIONS
    // ============================================================

    private fun getImageDimensions(
        uri: Uri
    ): Pair<Int, Int>? {

        return try {

            val options =
                BitmapFactory.Options().apply {

                    inJustDecodeBounds = true
                }

            contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    BitmapFactory.decodeStream(
                        inputStream,
                        null,
                        options
                    )
                }

            if (
                options.outWidth <= 0 ||
                options.outHeight <= 0
            ) {

                null

            } else {

                Pair(
                    options.outWidth,
                    options.outHeight
                )
            }

        } catch (e: Exception) {

            e.printStackTrace()

            null
        }
    }


    // ============================================================
    // SAVE BOARD
    // ============================================================

    private fun saveBoard() {

        try {

            /*
             * ----------------------------------------------------
             * IMPORTANT FIX
             * ----------------------------------------------------
             *
             * Before getting the board from ViewModel, make sure
             * the ViewModel knows the actual Canvas dimensions.
             */
            if (!updateCanvasSize()) {

                Toast.makeText(
                    this,
                    "Canvas is not ready. Please wait a moment and try again.",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            /*
             * Get the latest board AFTER setting canvas size.
             */
            val board =
                viewModel.board.value


            /*
             * Validate the board.
             */
            val validation =
                validateBoard(board)

            if (!validation.first) {

                Toast.makeText(
                    this,
                    validation.second,
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            /*
             * Save JSON.
             */
            val jsonFile =
                storage.save(board)


            /*
             * Save PNG.
             */
            val pngFile =
                saveCanvasAsPng()


            Toast.makeText(
                this,
                "Saved successfully\n" +
                        "JSON: ${jsonFile.name}\n" +
                        "PNG: ${pngFile.name}",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Save failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // ============================================================
    // VALIDATE BOARD
    // ============================================================

    private fun validateBoard(
        board: WhiteboardModel
    ): Pair<Boolean, String> {


        // --------------------------------------------------------
        // CANVAS
        // --------------------------------------------------------

        if (
            board.canvasWidth <= 0 ||
            board.canvasHeight <= 0
        ) {

            return Pair(
                false,
                "Canvas size is invalid: " +
                        "${board.canvasWidth} x " +
                        "${board.canvasHeight}"
            )
        }


        // --------------------------------------------------------
        // STROKES
        // --------------------------------------------------------

        for (stroke in board.strokes) {

            if (stroke.points.isEmpty()) {

                return Pair(
                    false,
                    "One or more strokes are invalid."
                )
            }

            for (point in stroke.points) {

                if (
                    !point.x.isFinite() ||
                    !point.y.isFinite()
                ) {

                    return Pair(
                        false,
                        "One or more stroke points are invalid."
                    )
                }
            }
        }


        // --------------------------------------------------------
        // TEXT
        // --------------------------------------------------------

        for (textModel in board.texts) {

            if (textModel.text.isBlank()) {

                return Pair(
                    false,
                    "Text cannot be empty."
                )
            }

            if (
                textModel.size <= 0f ||
                !textModel.size.isFinite()
            ) {

                return Pair(
                    false,
                    "Text size must be greater than zero."
                )
            }

            if (
                textModel.width <= 0f ||
                textModel.height <= 0f
            ) {

                return Pair(
                    false,
                    "Text dimensions are invalid."
                )
            }

            if (
                !textModel.width.isFinite() ||
                !textModel.height.isFinite()
            ) {

                return Pair(
                    false,
                    "Text dimensions are invalid."
                )
            }

            if (
                !textModel.x.isFinite() ||
                !textModel.y.isFinite()
            ) {

                return Pair(
                    false,
                    "Text position is invalid."
                )
            }
        }



        // IMAGES


        for (image in board.images) {

            if (image.uri.isBlank()) {


                return Pair(
                    false,
                    "One or more images have an invalid URI."
                )
            }

            if (
                image.width <= 0f ||
                image.height <= 0f ||
                image.scale <= 0f
            ) {

                return Pair(
                    false,
                    "One or more images are invalid."
                )
            }

            if (
                !image.x.isFinite() ||
                !image.y.isFinite() ||
                !image.width.isFinite() ||
                !image.height.isFinite() ||
                !image.scale.isFinite()
            ) {

                return Pair(
                    false,
                    "One or more image positions are invalid."
                )
            }
        }



        // SHAPES


        for (shape in board.shapes) {

            if (
                !shape.left.isFinite() ||
                !shape.top.isFinite() ||
                !shape.right.isFinite() ||
                !shape.bottom.isFinite()
            ) {

                return Pair(
                    false,
                    "One or more shapes have invalid coordinates."
                )
            }

            if (
                shape.strokeWidth <= 0f ||
                !shape.strokeWidth.isFinite()
            ) {

                return Pair(
                    false,
                    "One or more shapes have invalid stroke width."
                )
            }
        }


        return Pair(
            true,
            "Valid"
        )
    }



    // SAVE CANVAS AS PNG


    private fun saveCanvasAsPng(): File {

        val width =
            canvasView.measuredWidth

        val height =
            canvasView.measuredHeight


        if (
            width <= 0 ||
            height <= 0
        ) {

            throw IllegalStateException(
                "Canvas has invalid dimensions: " +
                        "${width} x ${height}"
            )
        }


        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )


        try {

            val canvas =
                Canvas(bitmap)


            canvas.drawColor(
                Color.WHITE
            )


            canvasView.draw(canvas)


            val name =
                "whiteboard_${
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date())
                }.png"


            val file =
                File(
                    filesDir,
                    name
                )


            FileOutputStream(file).use { output ->

                val compressed =
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                    )

                if (!compressed) {

                    throw IllegalStateException(
                        "Unable to compress canvas as PNG."
                    )
                }
            }


            return file

        } finally {

            if (!bitmap.isRecycled) {

                bitmap.recycle()
            }
        }
    }


    // LOAD DIALOG

    private fun showLoadDialog() {

        try {

            val files =
                storage.listFiles()


            if (files.isEmpty()) {

                Toast.makeText(
                    this,
                    "No saved whiteboards found.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }


            val names =
                files
                    .map { file ->
                        file.name
                    }
                    .toTypedArray()


            AlertDialog.Builder(this)
                .setTitle("Load Whiteboard")
                .setItems(names) { _, which ->

                    if (
                        which < 0 ||
                        which >= names.size
                    ) {

                        return@setItems
                    }

                    loadBoard(
                        names[which]
                    )
                }
                .show()

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Unable to list saved whiteboards: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // LOAD BOARD

    private fun loadBoard(
        fileName: String
    ) {

        try {

            val board =
                storage.load(fileName)


            viewModel.replaceBoard(board)


            canvasView.post {

                updateCanvasSize()


                canvasView.clearSelection()


                viewModel.tool =
                    Tool.SELECT


                updateToolSelection()


                canvasView.invalidate()
            }


            Toast.makeText(
                this,
                "Loaded $fileName",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Unable to load whiteboard: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }



    private fun dpToPx(
        dp: Int
    ): Int {

        return (
                dp *
                        resources
                            .displayMetrics
                            .density
                ).toInt()
    }
}