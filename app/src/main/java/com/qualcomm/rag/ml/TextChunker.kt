package com.qualcomm.rag.ml

// One page of text extracted from a PDF
data class PdfPage(val pageNumber: Int, val text: String)

// A chunk ready to embed, with its source page
data class TextChunk(val text: String, val pageNumber: Int)

object TextChunker {

    // Splits pages into overlapping character windows (500 chars, 50 overlap)
    fun chunk(pages: List<PdfPage>, chunkSize: Int = 500, overlap: Int = 50): List<TextChunk> {
        val result = mutableListOf<TextChunk>()
        for (page in pages) {
            val clean = page.text.trim()
                .replace(Regex("\\s{3,}"), "\n\n")
                .replace(Regex("[ \\t]+"), " ")
            if (clean.isEmpty()) continue

            var start = 0
            while (start < clean.length) {
                val end   = minOf(start + chunkSize, clean.length)
                val chunk = clean.substring(start, end).trim()
                if (chunk.isNotBlank()) result += TextChunk(chunk, page.pageNumber)
                if (end == clean.length) break
                start = end - overlap
            }
        }
        return result
    }
}
