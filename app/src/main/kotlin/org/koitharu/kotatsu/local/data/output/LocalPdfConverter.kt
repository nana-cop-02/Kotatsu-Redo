package org.koitharu.kotatsu.local.data.output

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.util.ext.createParentDirs
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LocalPdfConverter {

    /**
     * Convert a PDF file into a CBZ archive. Calls [onProgress] with (currentPage, totalPages).
     * The original PDF will be moved into [backupDir] (directory created if missing) after success.
     */
    suspend fun convertPdfToCbz(
        pdfFile: File,
        cbzFile: File,
        backupDir: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) throw IOException("PDF file not found: ${pdfFile.path}")

        pdfFile.createParentDirs()
        cbzFile.parentFile?.createParentDirs()
        backupDir.mkdirs()

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var zos: ZipOutputStream? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            // Ensure temporary cbz is written atomically
            val tmp = File(cbzFile.parentFile, cbzFile.name + ".tmp")
            zos = ZipOutputStream(tmp.outputStream())

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                try {
                    val width = page.width
                    val height = page.height

                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val entryName = String.format("%04d.png", i + 1)
                    zos.putNextEntry(ZipEntry(entryName))
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, zos)
                    zos.closeEntry()

                    onProgress(i + 1, pageCount)
                    bmp.recycle()
                } finally {
                    page.close()
                }
            }

            zos.close()
            // Move tmp to final
            if (cbzFile.exists()) cbzFile.delete()
            if (!tmp.renameTo(cbzFile)) {
                // fallback: copy
                tmp.copyTo(cbzFile, overwrite = true)
                tmp.delete()
            }

            // Move original PDF to backup dir
            val backupFile = File(backupDir, pdfFile.name)
            if (backupFile.exists()) backupFile.delete()
            if (!pdfFile.renameTo(backupFile)) {
                // fallback copy
                FileInputStream(pdfFile).channel.use { src ->
                    backupFile.outputStream().channel.use { dst ->
                        src.transferTo(0, src.size(), dst)
                    }
                }
                pdfFile.delete()
            }
        } finally {
            try { zos?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}
