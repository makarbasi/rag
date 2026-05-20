package com.qualcomm.rag.ml

import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer

// Pure-Kotlin SentencePiece Unigram tokenizer for BGE-M3 (XLM-RoBERTa).
// Uses Viterbi over the vocab from tokenizer.json (index = token ID).
class XlmRoBertaTokenizer private constructor(
    private val tokenToId   : HashMap<String, Int>,
    private val tokenToScore: HashMap<String, Float>,
    private val maxTokenLen : Int,
    private val maxLength   : Int = 512
) {
    companion object {
        private const val BOS = 0; private const val EOS = 2; private const val UNK = 3
        private const val SPACE_CHAR = '\u2581'          // ▁  (Metaspace marker)
        private const val UNK_SCORE  = -1e10f            // penalty for unknown chars

        suspend fun load(stream: InputStream, maxLength: Int = 512): XlmRoBertaTokenizer =
            withContext(Dispatchers.IO) {
                val tokenToId    = HashMap<String, Int>(280_000)
                val tokenToScore = HashMap<String, Float>(280_000)
                var maxLen = 1

                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() == "model") {
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "vocab" -> {
                                        r.beginArray(); var id = 0
                                        while (r.hasNext()) {
                                            r.beginArray()
                                            val token = r.nextString()
                                            val score = r.nextDouble().toFloat()
                                            r.endArray()
                                            tokenToId[token]    = id
                                            tokenToScore[token] = score
                                            if (token.length > maxLen) maxLen = token.length
                                            id++
                                        }
                                        r.endArray()
                                    }
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                        } else r.skipValue()
                    }
                    r.endObject()
                }
                XlmRoBertaTokenizer(tokenToId, tokenToScore, maxLen, maxLength)
            }
    }

    // Encode text → (input_ids, attention_mask). Truncates to maxLength, adds BOS/EOS.
    fun encode(text: String): Pair<LongArray, LongArray> {
        val normalised = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
        val words = normalised.split(Regex("\\s+")).filter { it.isNotEmpty() }

        val ids = mutableListOf(BOS)
        for (word in words) {
            val prefixed = "$SPACE_CHAR$word"   // Metaspace: always prepend ▁
            for (tokenId in viterbi(prefixed)) {
                ids.add(tokenId)
                if (ids.size >= maxLength - 1) break
            }
            if (ids.size >= maxLength - 1) break
        }
        ids.add(EOS)

        val inputIds      = LongArray(ids.size)
        val attentionMask = LongArray(ids.size)
        for (i in ids.indices) {
            inputIds[i]      = ids[i].toLong()
            attentionMask[i] = 1L
        }
        return Pair(inputIds, attentionMask)
    }

    // Viterbi segmentation: dp[i] = best cumulative log-prob for text[0..i]
    private fun viterbi(word: String): List<Int> {
        val n = word.length
        if (n == 0) return emptyList()

        val dp   = FloatArray(n + 1)
        val back = IntArray(n + 1)
        for (i in dp.indices) dp[i] = Float.NEGATIVE_INFINITY
        for (i in back.indices) back[i] = -1
        dp[0] = 0f

        for (end in 1..n) {
            val startMin = (end - maxTokenLen).coerceAtLeast(0)
            for (start in startMin until end) {
                if (dp[start] == Float.NEGATIVE_INFINITY) continue
                val token = word.substring(start, end)
                val score = tokenToScore[token]
                if (score != null) {
                    val total = dp[start] + score
                    if (total > dp[end]) { dp[end] = total; back[end] = start }
                }
            }
            if (dp[end] == Float.NEGATIVE_INFINITY) {
                dp[end] = dp[end - 1] + UNK_SCORE
                back[end] = end - 1
            }
        }

        // Backtrack
        val result = mutableListOf<Int>()
        var pos = n
        while (pos > 0) {
            val start = back[pos]
            if (start < 0) break
            val token = word.substring(start, pos)
            val id    = tokenToId[token]
            result.add(if (id != null) id else UNK)
            pos = start
        }
        result.reverse()
        return result
    }

    fun close() { /* no resources to release */ }
}
