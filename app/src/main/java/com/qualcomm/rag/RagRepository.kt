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

    // ID of the currently indexed document (null = nothing indexed yet)
    private var currentDocId: String? = null

    // Index a PDF. Always clears the previous document first.
    suspend fun indexDocument(
        uri: Uri,
        name: String,
        context: Context,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.IO) {

        val docId = fileHash(uri, context)

        // Delete whatever was indexed before
        currentDocId?.let { db.deleteDocument(it) }
        currentDocId = docId

        onStatus("Reading PDF pages…")
        val pages  = PdfExtractor.extractPages(uri, context)
        val chunks = TextChunker.chunk(pages)

        val records = mutableListOf<ChunkRecord>()
        chunks.forEachIndexed { i, chunk ->
            onStatus("Embedding chunk ${i + 1} / ${chunks.size}…")
            val embedding = embedder.embed(chunk.text)
            records += ChunkRecord(
                documentId   = docId,
                documentName = name,
                pageNumber   = chunk.pageNumber,
                chunkText    = chunk.text,
                embedding    = floatArrayToByteArray(embedding)
            )
        }

        onStatus("Saving to database…")
        db.insertChunks(records)
    }

    // Search the current document. Returns empty list if nothing indexed yet.
    suspend fun search(query: String, topK: Int = 5): List<SearchResult> {
        val docId = currentDocId ?: return emptyList()
        val embedding = embedder.embed(query)
        return db.search(docId, embedding, topK)
    }

    // Makes a short unique ID from the file content so we can find it in the DB later
    private suspend fun fileHash(uri: Uri, context: Context): String =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            openStream(uri, context)?.use { stream ->
                val buf = ByteArray(8192); var n: Int
                while (stream.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        }

    // Opens the file from either a file:// path or a content:// URI
    private fun openStream(uri: Uri, context: Context): InputStream? =
        if (uri.scheme == "file")
            File(uri.path!!).inputStream()
        else
            context.contentResolver.openInputStream(uri)
}
