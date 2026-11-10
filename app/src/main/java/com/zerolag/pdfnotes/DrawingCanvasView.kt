package com.zerolag.pdfnotes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

enum class ToolMode { PEN, HIGHLIGHTER, ERASER_STROKE, LASSO }

data class Stroke(
    val path: Path,
    val paint: Paint,
    val isHighlighter: Boolean = false
)

class DrawingCanvasView(context: Context) : View(context) {

    var currentMode: ToolMode = ToolMode.PEN
    var strokeColor: Int = Color.BLACK
    var baseStrokeWidth: Float = 5f

    private val strokes = mutableListOf<Stroke>()
    private val undoneStrokes = mutableListOf<Stroke>()
    
    private val currentPath = Path()
    private val currentPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val lassoPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    private val boundsRect = RectF()
    private val lassoRegion = Region()
    private val strokeRegion = Region()
    private val clipRegion = Region()

    private var lastX = 0f
    private var lastY = 0f
    private var isDrawing = false

    private var lastStylusTime = 0L
    private val STYLUS_REJECTION_WINDOW_MS = 400L
    private val TOUCH_TOLERANCE = 2f

    var pdfBackground: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val now = SystemClock.uptimeMillis()

        if (isStylus) {
            lastStylusTime = now
        } else {
            if (now - lastStylusTime < STYLUS_REJECTION_WINDOW_MS) return true 
            if (event.getTouchMajor(0) > 60f) return true 
        }

        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                undoneStrokes.clear()
                
                if (currentMode == ToolMode.ERASER_STROKE) {
                    eraseStrokeAt(event.x, event.y)
                    return true
                }

                currentPath.reset()
                currentPath.moveTo(event.x, event.y)
                lastX = event.x
                lastY = event.y

                setupCurrentPaint(event.pressure)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return true

                if (currentMode == ToolMode.ERASER_STROKE) {
                    eraseStrokeAt(event.x, event.y)
                    return true
                }

                val historySize = event.historySize
                for (i in 0 until historySize) {
                    processPoint(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalPressure(i))
                }
                processPoint(event.x, event.y, event.pressure)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDrawing) return true
                isDrawing = false

                if (currentMode == ToolMode.LASSO) {
                    processLassoSelection()
                } else if (currentMode != ToolMode.ERASER_STROKE) {
                    val finalPath = Path(currentPath)
                    val finalPaint = Paint(currentPaint)
                    strokes.add(Stroke(finalPath, finalPaint, currentMode == ToolMode.HIGHLIGHTER))
                }
                currentPath.reset()
                invalidate()
            }
        }
        return true
    }

    private fun processPoint(x: Float, y: Float, pressure: Float) {
        val dx = Math.abs(x - lastX)
        val dy = Math.abs(y - lastY)
        
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
            lastX = x
            lastY = y
            
            if (currentMode == ToolMode.PEN) {
                val pressureMultiplier = 0.5f + (pressure * 1.5f)
                currentPaint.strokeWidth = baseStrokeWidth * pressureMultiplier
            }
        }
    }

    private fun setupCurrentPaint(initialPressure: Float) {
        currentPaint.color = strokeColor
        currentPaint.alpha = 255
        currentPaint.xfermode = null

        when (currentMode) {
            ToolMode.PEN -> {
                currentPaint.strokeWidth = baseStrokeWidth * (0.5f + (initialPressure * 1.5f))
            }
            ToolMode.HIGHLIGHTER -> {
                currentPaint.strokeWidth = baseStrokeWidth * 4f
                currentPaint.alpha = 100
                currentPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            ToolMode.LASSO -> {
                currentPaint.set(lassoPaint)
            }
            else -> {}
        }
    }

    private fun eraseStrokeAt(x: Float, y: Float) {
        for (i in strokes.indices.reversed()) {
            val stroke = strokes[i]
            stroke.path.computeBounds(boundsRect, true)
            boundsRect.inset(-20f, -20f)
            if (boundsRect.contains(x, y)) {
                strokes.removeAt(i)
                invalidate()
                break
            }
        }
    }

    private fun processLassoSelection() {
        currentPath.computeBounds(boundsRect, true)
        clipRegion.set(boundsRect.left.toInt(), boundsRect.top.toInt(), boundsRect.right.toInt(), boundsRect.bottom.toInt())
        lassoRegion.setPath(currentPath, clipRegion)

        val iterator = strokes.iterator()
        while (iterator.hasNext()) {
            val stroke = iterator.next()
            stroke.path.computeBounds(boundsRect, true)
            clipRegion.set(boundsRect.left.toInt(), boundsRect.top.toInt(), boundsRect.right.toInt(), boundsRect.bottom.toInt())
            strokeRegion.setPath(stroke.path, clipRegion)
            
            if (!lassoRegion.op(strokeRegion, Region.Op.INTERSECT)) {
                iterator.remove()
            }
        }
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            undoneStrokes.add(strokes.removeLast())
            invalidate()
        }
    }

    fun redo() {
        if (undoneStrokes.isNotEmpty()) {
            strokes.add(undoneStrokes.removeLast())
            invalidate()
        }
    }

    fun clearCanvas() {
        strokes.clear()
        undoneStrokes.clear()
        invalidate()
    }

    fun getCanvasBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pdfBackground?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        for (i in 0 until strokes.size) {
            val stroke = strokes[i]
            canvas.drawPath(stroke.path, stroke.paint)
        }
        if (isDrawing) {
            canvas.drawPath(currentPath, currentPaint)
        }
    }
}
