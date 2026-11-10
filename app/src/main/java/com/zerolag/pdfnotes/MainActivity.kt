package com.zerolag.pdfnotes

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var pdfEngine: PdfRenderEngine
    private var drawingCanvasView: DrawingCanvasView? = null

    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { loadPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfEngine = PdfRenderEngine(this)

        setContent {
            MaterialTheme {
                ZeroLagAppUI()
            }
        }
    }

    @Composable
    fun ZeroLagAppUI() {
        var currentMode by remember { mutableStateOf(ToolMode.PEN) }

        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
            
            AndroidView(
                factory = { context ->
                    DrawingCanvasView(context).also { drawingCanvasView = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .background(Color(0xFF2D2D2D), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarButton("Open PDF", Color.White) {
                    openPdfLauncher.launch(arrayOf("application/pdf"))
                }
                
                VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.Gray)

                ToolbarButton("Pen", if (currentMode == ToolMode.PEN) Color.Cyan else Color.White) {
                    currentMode = ToolMode.PEN
                    drawingCanvasView?.currentMode = ToolMode.PEN
                    drawingCanvasView?.strokeColor = AndroidColor.BLACK
                }
                ToolbarButton("Highlight", if (currentMode == ToolMode.HIGHLIGHTER) Color.Cyan else Color.White) {
                    currentMode = ToolMode.HIGHLIGHTER
                    drawingCanvasView?.currentMode = ToolMode.HIGHLIGHTER
                    drawingCanvasView?.strokeColor = AndroidColor.YELLOW
                }
                ToolbarButton("Eraser", if (currentMode == ToolMode.ERASER_STROKE) Color.Cyan else Color.White) {
                    currentMode = ToolMode.ERASER_STROKE
                    drawingCanvasView?.currentMode = ToolMode.ERASER_STROKE
                }
                ToolbarButton("Lasso", if (currentMode == ToolMode.LASSO) Color.Cyan else Color.White) {
                    currentMode = ToolMode.LASSO
                    drawingCanvasView?.currentMode = ToolMode.LASSO
                }

                VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.Gray)

                ToolbarButton("Undo", Color.White) { drawingCanvasView?.undo() }
                ToolbarButton("Redo", Color.White) { drawingCanvasView?.redo() }
                
                VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.Gray)

                ToolbarButton("Export", Color.Green) { exportPdf() }
            }
        }
    }

    @Composable
    fun ToolbarButton(text: String, color: Color, onClick: () -> Unit) {
        TextButton(onClick = onClick) {
            Text(text = text, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }

    private fun loadPdf(uri: Uri) {
        val pageCount = pdfEngine.openPdf(uri)
        if (pageCount > 0) {
            drawingCanvasView?.post {
                val width = drawingCanvasView?.width ?: 1080
                val height = drawingCanvasView?.height ?: 1920
                val bitmap = pdfEngine.renderPage(0, width, height)
                drawingCanvasView?.pdfBackground = bitmap
                drawingCanvasView?.clearCanvas()
            }
        }
    }

    private fun exportPdf() {
        val canvasView = drawingCanvasView ?: return
        val bg = canvasView.pdfBackground ?: return
        val annotated = canvasView.getCanvasBitmap()
        
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outFile = File(downloadsDir, "Annotated_${System.currentTimeMillis()}.png")
        
        pdfEngine.exportAnnotatedPdf(bg, annotated, outFile)
        Toast.makeText(this, "Exported to Downloads!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfEngine.close()
    }
}
