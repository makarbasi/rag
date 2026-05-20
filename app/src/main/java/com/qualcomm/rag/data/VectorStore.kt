package com.qualcomm.rag.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class ChunkRecord(
    val id: Long = 0,
    val documentId: String,
    val documentName: String,
    val pageNumber: Int,
    val chunkText: String,
    val embedding: ByteArray
)

data class SearchResult(
    val chunkText: String,
    val pageNumber: Int,
    val documentName: String,
    val score: Float
)

private class RagDbHelper(context: Context) :
    SQLiteOpenHelper(context, "rag_vector_store.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chunks (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                document_id  TEXT    NOT NULL,
                doc_name     TEXT    NOT NULL,
                page_number  INTEGER NOT NULL,
                chunk_text   TEXT    NOT NULL,
                embedding    BLOB    NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_doc ON chunks(document_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS chunks")
        onCreate(db)
    }
}

// SQLite vector store. Embeddings stored as little-endian float32 blobs.
// Search is brute-force cosine similarity.
class VectorStore(context: Context) {

    private val helper = RagDbHelper(context)

    suspend fun insertChunks(chunks: List<ChunkRecord>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues()
            for (c in chunks) {
                cv.put("document_id", c.documentId)
                cv.put("doc_name",    c.documentName)
                cv.put("page_number", c.pageNumber)
                cv.put("chunk_text",  c.chunkText)
                cv.put("embedding",   c.embedding)
                db.insert("chunks", null, cv)
                cv.clear()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("chunks", "document_id = ?", arrayOf(docId))
    }

    suspend fun search(
        docId: String,
        queryEmbedding: FloatArray,
        topK: Int = 5
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Load all chunks for this document
        val pageNumbers  = mutableListOf<Int>()
        val texts        = mutableListOf<String>()
        val embeddings   = mutableListOf<ByteArray>()
        var docName      = ""

        val cursor = helper.readableDatabase.rawQuery(
            "SELECT page_number, chunk_text, doc_name, embedding FROM chunks WHERE document_id = ?",
            arrayOf(docId)
        )
        cursor.use {
            while (it.moveToNext()) {
                pageNumbers += it.getInt(0)
                texts       += it.getString(1)
                if (docName.isEmpty()) docName = it.getString(2)
                embeddings  += it.getBlob(3)
            }
        }

        // Score each chunk and return the top K
        val results = mutableListOf<SearchResult>()
        for (i in texts.indices) {
            val vec   = byteArrayToFloatArray(embeddings[i])
            val score = cosineSimilarity(queryEmbedding, vec)
            results += SearchResult(texts[i], pageNumbers[i], docName, score)
        }
        results.sortByDescending { it.score }
        results.take(topK)
    }

    companion object {

        fun floatArrayToByteArray(floats: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in floats) buf.putFloat(f)
            return buf.array()
        }

        fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
            val buf    = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val result = FloatArray(bytes.size / 4)
            for (i in result.indices) result[i] = buf.float
            return result
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot.coerceIn(-1f, 1f)
        }

        fun normalize(v: FloatArray): FloatArray {
            var norm = 0f
            for (x in v) norm += x * x
            norm = sqrt(norm)
            if (norm > 0f) {
                for (i in v.indices) v[i] /= norm
            }
            return v
        }
    }
}
