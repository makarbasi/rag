package com.qualcomm.rag.ml

/**
 * A piece of text ready to be embedded, with its source page.
 */
data class TextChunk(
    val text: String,
    val pageNumber: Int,
    val chunkIndex: Int   // 0-based index within the page
)

/**
 * Splits raw PDF page text into overlapping fixed-size character chunks.
 *
 * Default strategy: 500-character windows with 50-character overlap.
 * This is simple, fast, and works well for BGE-M3's generous 8192-token limit.
 */
object TextChunker {

    /**
     * @param pages        Ordered list of (pageNumber, pageText) pairs.
     * @param chunkSize    Target character count per chunk.
     * @param overlap      How many characters to carry over from the previous chunk.
     */
    fun chunk(
        pages: List<PdfPage>,
        chunkSize: Int = 500,
        overlap: Int   = 50
    ): List<TextChunk> {
        val result = mutableListOf<TextChunk>()

        for (page in pages) {
            val text = page.text.trim()
            if (text.isEmpty()) continue

            // Normalise whitespace: collapse multiple newlines/spaces
            val clean = text.replace(Regex("\\s{3,}"), "\n\n")
                            .replace(Regex("[ \\t]+"), " ")

            var start      = 0
            var chunkIndex = 0

            while (start < clean.length) {
                val end  = minOf(start + chunkSize, clean.length)
                val chunk = clean.substring(start, end).trim()

                if (chunk.isNotBlank()) {
                    result += TextChunk(
                        text       = chunk,
                        pageNumber = page.pageNumber,
                        chunkIndex = chunkIndex++
                    )
                }

                if (end == clean.length) break
                start = end - overlap
            }
        }

        return result
    }
}
