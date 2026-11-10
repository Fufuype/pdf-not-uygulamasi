package com.zerolag.pdfnotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

class PdfRenderEngine(private val context: Context) {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pageCount: Int = 0

    fun openPdf(uri: Uri): Int {
        close()
        fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
        pdfRenderer = PdfRenderer(fileDescriptor!!).apply {
            pageCount = this.pageCount
        }
        return pageCount
    }

    fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap? {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) return null

        currentPage?.close()
        currentPage = pdfRenderer?.openPage(pageIndex)

        // Use ARGB_8888 for high quality, hardware-accelerated rendering
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) // PDF backgrounds are transparent by default

        currentPage?.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    fun exportAnnotatedPdf(originalBitmap: Bitmap, annotatedBitmap: Bitmap, outputFile: File) {
        // In a full implementation, this would write back to a PDF structure.
        // For ultra-lightweight offline constraints, we export the flattened page as a high-res PNG.
        val outCanvas = Canvas(originalBitmap)
        outCanvas.drawBitmap(annotatedBitmap, 0f, 0f, null)
        
        FileOutputStream(outputFile).use { out ->
            originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun close() {
        currentPage?.close()
        pdfRenderer?.close()
        fileDescriptor?.close()
        
        currentPage = null
        pdfRenderer = null
        fileDescriptor = null
    }
}
