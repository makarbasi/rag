package com.qualcomm.rag.ml

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfExtractor {

    suspend fun extractPages(uri: Uri, context: Context): List<PdfPage> =
        withContext(Dispatchers.IO) {
            val pages    = mutableListOf<PdfPage>()
            val stripper = PDFTextStripper()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    for (page in 1..doc.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage   = page
                        val text = stripper.getText(doc).trim()
                        if (text.isNotBlank()) pages += PdfPage(page, text)
                    }
                }
            }
            pages
        }
}
