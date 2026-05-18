package com.qualcomm.rag.ml

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One page worth of raw text extracted from a PDF. */
data class PdfPage(val pageNumber: Int, val text: String)

/**
 * Extracts text from a PDF [Uri] using Apache PDFBox-Android.
 * All work runs on [Dispatchers.IO].
 */
object PdfExtractor {

    /** Must be called once before any extraction (initialises PDFBox font resources). */
    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Opens the PDF at [uri] and returns one [PdfPage] per page.
     * Pages with no extractable text are silently skipped.
     */
    suspend fun extractPages(uri: Uri, context: Context): List<PdfPage> =
        withContext(Dispatchers.IO) {
            val pages = mutableListOf<PdfPage>()

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val stripper = PDFTextStripper()
                PDDocument.load(stream).use { doc ->
                    val total = doc.numberOfPages
                    for (page in 1..total) {
                        stripper.startPage = page
                        stripper.endPage   = page
                        val text = stripper.getText(doc).trim()
                        if (text.isNotBlank()) {
                            pages += PdfPage(page, text)
                        }
                    }
                }
            }

            pages
        }
}
