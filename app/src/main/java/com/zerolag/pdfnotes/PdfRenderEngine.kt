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
        val renderer = PdfRenderer(fileDescriptor!!)
        pdfRenderer = renderer
        pageCount = renderer.pageCount
        return pageCount
    }

    fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap? {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) return null

        currentPage?.close()
        currentPage = pdfRenderer?.openPage(pageIndex)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        currentPage?.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    fun exportAnnotatedPdf(originalBitmap: Bitmap, annotatedBitmap: Bitmap, outputFile: File) {
        val resultBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(resultBitmap)
        outCanvas.drawBitmap(originalBitmap, 0f, 0f, null)
        outCanvas.drawBitmap(annotatedBitmap, 0f, 0f, null)
        
        FileOutputStream(outputFile).use { out ->
            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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
