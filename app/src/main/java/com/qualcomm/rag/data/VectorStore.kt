package com.qualcomm.rag.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.qualcomm.rag.data.VectorStore.Companion.byteArrayToFloatArray
import com.qualcomm.rag.data.VectorStore.Companion.floatArrayToByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

// ── Domain models ──────────────────────────────────────────────────────────────

data class ChunkRecord(
    val id: Long = 0,
    val documentId: String,
    val documentName: String,
    val pageNumber: Int,
    val chunkText: String,
    val embedding: ByteArray
)

data class DocumentSummary(val documentId: String, val documentName: String)

data class SearchResult(
    val chunkText: String,
    val pageNumber: Int,
    val documentName: String,
    val score: Float
)

// ── SQLite helper ──────────────────────────────────────────────────────────────

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

// ── VectorStore ────────────────────────────────────────────────────────────────

/**
 * Raw-SQLite vector store.  No Room, no KSP — works with any Kotlin/AGP version.
 *
 * Embeddings are stored as little-endian IEEE-754 float32 byte arrays.
 * Search is brute-force cosine similarity (fast enough for hundreds of chunks).
 */
class VectorStore(context: Context) {

    private val helper = RagDbHelper(context)

    // ── write ──────────────────────────────────────────────────────────────────

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

    suspend fun countChunks(docId: String): Int = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM chunks WHERE document_id = ?", arrayOf(docId)
        )
        cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    suspend fun getAllDocuments(): List<DocumentSummary> = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT DISTINCT document_id, doc_name FROM chunks", null
        )
        val result = mutableListOf<DocumentSummary>()
        cursor.use {
            while (it.moveToNext()) {
                result += DocumentSummary(it.getString(0), it.getString(1))
            }
        }
        result
    }

    // ── search ────────────────────────────────────────────────────────────────

    suspend fun search(
        docId: String,
        queryEmbedding: FloatArray,
        topK: Int = 5
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val rows = withContext(Dispatchers.IO) {
            val db = helper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT page_number, chunk_text, doc_name, embedding FROM chunks WHERE document_id = ?",
                arrayOf(docId)
            )
            val list = mutableListOf<Triple<Int, String, ByteArray>>()
            cursor.use {
                while (it.moveToNext()) {
                    list += Triple(it.getInt(0), it.getString(1), it.getBlob(3))
                }
            }
            val docName = withContext(Dispatchers.IO) {
                db.rawQuery(
                    "SELECT doc_name FROM chunks WHERE document_id = ? LIMIT 1", arrayOf(docId)
                ).use { c -> if (c.moveToFirst()) c.getString(0) else "" }
            }
            Pair(list, docName)
        }

        val (chunks, docName) = rows
        chunks.map { (page, text, embBytes) ->
            val vec   = byteArrayToFloatArray(embBytes)
            val score = cosineSimilarity(queryEmbedding, vec)
            SearchResult(text, page, docName, score)
        }
        .sortedByDescending { it.score }
        .take(topK)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    companion object {

        fun floatArrayToByteArray(floats: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            floats.forEach { buf.putFloat(it) }
            return buf.array()
        }

        fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
            val buf    = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val result = FloatArray(bytes.size / 4)
            repeat(result.size) { result[it] = buf.float }
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
            if (norm > 0f) for (i in v.indices) v[i] /= norm
            return v
        }
    }
}
