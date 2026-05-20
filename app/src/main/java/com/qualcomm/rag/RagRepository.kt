package com.qualcomm.rag

import android.content.Context
import android.net.Uri
import com.qualcomm.rag.data.ChunkRecord
import com.qualcomm.rag.data.SearchResult
import com.qualcomm.rag.data.VectorStore
import com.qualcomm.rag.data.VectorStore.Companion.floatArrayToByteArray
import com.qualcomm.rag.ml.BgeM3Embedder
import com.qualcomm.rag.ml.PdfExtractor
import com.qualcomm.rag.ml.TextChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class RagRepository(context: Context) {

    val embedder = BgeM3Embedder(context)
    private val db = VectorStore(context)

    private var currentDocId: String? = null

    suspend fun indexDocument(
        uri: Uri,
        name: String,
        context: Context,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.IO) {

        val docId = fileHash(uri, context)

        if (currentDocId != null) db.deleteDocument(currentDocId!!)
        currentDocId = docId

        onStatus("Reading PDF pages…")
        val pages  = PdfExtractor.extractPages(uri, context)
        val chunks = TextChunker.chunk(pages)

        val records = mutableListOf<ChunkRecord>()
        for (i in chunks.indices) {
            onStatus("Embedding chunk ${i + 1} / ${chunks.size}…")
            val embedding = embedder.embed(chunks[i].text)
            records += ChunkRecord(
                documentId   = docId,
                documentName = name,
                pageNumber   = chunks[i].pageNumber,
                chunkText    = chunks[i].text,
                embedding    = floatArrayToByteArray(embedding)
            )
        }

        onStatus("Saving to database…")
        db.insertChunks(records)
    }

    suspend fun search(query: String, topK: Int = 5): List<SearchResult> {
        val docId = currentDocId ?: return emptyList()
        val embedding = embedder.embed(query)
        return db.search(docId, embedding, topK)
    }

    // SHA-256 of the file content, truncated to 16 hex chars — used as DB document ID
    private suspend fun fileHash(uri: Uri, context: Context): String =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            val stream = openStream(uri, context)
            if (stream != null) {
                val buf = ByteArray(8192)
                stream.use { s ->
                    var n = s.read(buf)
                    while (n != -1) {
                        digest.update(buf, 0, n)
                        n = s.read(buf)
                    }
                }
            }
            val bytes = digest.digest()
            val sb = StringBuilder()
            for (b in bytes) sb.append("%02x".format(b))
            sb.toString().take(16)
        }

    private fun openStream(uri: Uri, context: Context): InputStream? =
        if (uri.scheme == "file")
            File(uri.path!!).inputStream()
        else
            context.contentResolver.openInputStream(uri)
}
