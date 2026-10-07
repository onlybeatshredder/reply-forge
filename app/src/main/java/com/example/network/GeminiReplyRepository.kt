package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.model.RelationshipContext
import com.example.model.ReplyOption
import com.example.model.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiReplyRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateReplies(
        contextText: String,
        selectedTones: List<Tone>,
        relationship: RelationshipContext,
        customInstruction: String = "",
        avoidText: String = ""
    ): List<ReplyOption> = withContext(Dispatchers.IO) {
        val finalTones = if (selectedTones.size == 1) selectedTones else fillToThreeTones(selectedTones)

        // 1. Try Agnes AI API if key is present
        val agnesKey = try { BuildConfig.AGNES_AI_API_KEY } catch (e: Exception) { "" }
        if (!agnesKey.isNullOrBlank() && agnesKey != "MY_AGNES_KEY") {
            val agnesReplies = tryAgnesAiApi(contextText, finalTones, relationship, customInstruction, avoidText, agnesKey)
            if (agnesReplies.isNotEmpty()) {
                Log.d("ReplyRepository", "Successfully generated replies via Agnes AI Engine ⚡")
                return@withContext agnesReplies
            }
        }

        // 2. Try Gemini API as primary/secondary
        val geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (!geminiKey.isNullOrBlank() && geminiKey != "MY_GEMINI_API_KEY") {
            val geminiReplies = tryGeminiApi(contextText, finalTones, relationship, customInstruction, avoidText, geminiKey)
            if (geminiReplies.isNotEmpty()) {
                Log.d("ReplyRepository", "Successfully generated replies via Gemini API ⚡")
                return@withContext geminiReplies
            }
        }

        // 3. Fallback to Local Smart Generation Engine
        Log.w("ReplyRepository", "Using local smart reply generator fallback.")
        return@withContext generateLocalFallbackReplies(contextText, finalTones, relationship, avoidText)
    }

    private fun tryAgnesAiApi(
        contextText: String,
        finalTones: List<Tone>,
        relationship: RelationshipContext,
        customInstruction: String,
        avoidText: String,
        apiKey: String
    ): List<ReplyOption> {
        try {
            val avoidClause = if (avoidText.isNotBlank()) "\nIMPORTANT: Do NOT use or repeat this previous answer: '$avoidText'. Generate a completely fresh, creative alternative wording!" else ""
            val prompt = """
                You are ReplyForge, an elite AI engine specialized in generating optimized text response options based on raw user inputs (pasted text messages, emails, or transcribed image OCR).
                
                Input Text: "$contextText"
                Relationship Context: "${relationship.displayName}"
                Custom Instructions: "$customInstruction"$avoidClause
                Requested Tones: ${finalTones.map { it.key }}
                
                Respond ONLY with a JSON object in this exact format:
                {
                  "replies": [
                    { "tone": "${finalTones.getOrNull(0)?.key ?: "funny"}", "text": "<1-2 sentence response option>" }${if (finalTones.size > 1) ",\n                    { \"tone\": \"${finalTones.getOrNull(1)?.key ?: "confident"}\", \"text\": \"<1-2 sentence response option>\" }" else ""}${if (finalTones.size > 2) ",\n                    { \"tone\": \"${finalTones.getOrNull(2)?.key ?: "polite"}\", \"text\": \"<1-2 sentence response option>\" }" else ""}
                  ]
                }
            """.trimIndent()

            val jsonPayload = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are ReplyForge AI. Output strictly valid JSON matching the schema.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", if (avoidText.isNotBlank()) 0.95 else 0.7)
            }

            // Agnes AI OpenAI-compatible endpoint or fallback endpoint
            val urlsToTry = listOf(
                "https://api.agnes.ai/v1/chat/completions",
                "https://api.openai.com/v1/chat/completions"
            )

            for (url in urlsToTry) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val bodyStr = response.body?.string()

                    if (response.isSuccessful && !bodyStr.isNullOrBlank()) {
                        val root = JSONObject(bodyStr)
                        val choices = root.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val content = choices.getJSONObject(0).getJSONObject("message").optString("content", "")
                            val cleanJson = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                            val parsed = parseReplyOptionsJson(cleanJson, finalTones, contextText)
                            if (parsed.isNotEmpty()) return parsed
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GeminiReplyRepository", "Agnes AI attempt error on $url", e)
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiReplyRepository", "Failed calling Agnes AI", e)
        }
        return emptyList()
    }

    private fun tryGeminiApi(
        contextText: String,
        finalTones: List<Tone>,
        relationship: RelationshipContext,
        customInstruction: String,
        avoidText: String,
        apiKey: String
    ): List<ReplyOption> {
        try {
            val avoidClause = if (avoidText.isNotBlank()) "\nIMPORTANT: Do NOT use or repeat this previous answer: '$avoidText'. Generate a completely fresh, creative alternative wording!" else ""
            val systemPrompt = """
                You are ReplyForge, an elite AI engine generating optimized text responses.
                Context: "$contextText"
                Relationship: "${relationship.displayName}"
                Tones: ${finalTones.map { it.key }}$avoidClause
                
                Respond strictly with JSON:
                {
                  "replies": [
                    { "tone": "${finalTones.getOrNull(0)?.key ?: "funny"}", "text": "<text>" }${if (finalTones.size > 1) ",\n                    { \"tone\": \"${finalTones.getOrNull(1)?.key ?: "confident"}\", \"text\": \"<text>\" }" else ""}${if (finalTones.size > 2) ",\n                    { \"tone\": \"${finalTones.getOrNull(2)?.key ?: "polite"}\", \"text\": \"<text>\" }" else ""}
                  ]
                }
            """.trimIndent()

            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemPrompt))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", if (avoidText.isNotBlank()) 0.95 else 0.8)
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val root = JSONObject(responseBody)
                val candidates = root.optJSONArray("candidates") ?: return emptyList()
                val candidate = candidates.optJSONObject(0) ?: return emptyList()
                val content = candidate.optJSONObject("content") ?: return emptyList()
                val parts = content.optJSONArray("parts") ?: return emptyList()
                val textPart = parts.optJSONObject(0)?.optString("text") ?: return emptyList()

                val cleanJson = textPart.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                return parseReplyOptionsJson(cleanJson, finalTones, contextText)
            }
        } catch (e: Exception) {
            Log.e("GeminiReplyRepository", "Failed Gemini API call", e)
        }
        return emptyList()
    }

    private fun parseReplyOptionsJson(
        jsonString: String,
        targetTones: List<Tone>,
        contextSnippet: String
    ): List<ReplyOption> {
        val list = mutableListOf<ReplyOption>()
        try {
            var sanitized = jsonString.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val firstBrace = sanitized.indexOf('{')
            val lastBrace = sanitized.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                sanitized = sanitized.substring(firstBrace, lastBrace + 1)
            }

            val root = JSONObject(sanitized)
            val array = root.optJSONArray("replies") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val toneKey = item.optString("tone", "funny")
                val text = item.optString("text", "")
                if (text.isNotBlank()) {
                    list.add(
                        ReplyOption(
                            tone = Tone.fromKey(toneKey),
                            text = text,
                            contextSnippet = contextSnippet.take(80)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiReplyRepository", "Parse error", e)
        }
        return list
    }

    private fun fillToThreeTones(selected: List<Tone>): List<Tone> {
        val result = selected.distinct().toMutableList()
        val defaults = listOf(Tone.FUNNY, Tone.CONFIDENT, Tone.POLITE)
        for (defaultTone in defaults) {
            if (result.size >= 3) break
            if (!result.contains(defaultTone)) {
                result.add(defaultTone)
            }
        }
        return result.take(3)
    }

    private fun generateLocalFallbackReplies(
        context: String,
        tones: List<Tone>,
        relationship: RelationshipContext,
        avoidText: String = ""
    ): List<ReplyOption> {
        val cleanContext = context.trim()
        val lowerContext = cleanContext.lowercase()

        return tones.map { tone ->
            val optionsList = when (tone) {
                Tone.FUNNY -> listOf(
                    "I was going to give a clever response, but my coffee hasn't kicked in yet!",
                    "Tell them I'm currently stuck in a montage of trying to find the perfect outfit.",
                    "All good, I guess I can forgive you if you make it up to me with coffee.",
                    "I'm currently stuck in traffic on my way to world domination.",
                    "My battery is at 1%, but my dedication to responding is at 100%!",
                    "If I don't reply in 5 minutes, send a search and rescue team."
                )
                Tone.CONFIDENT -> listOf(
                    "Got it. Let's touch base once you're ready.",
                    "No worries at all. Hope you got everything handled.",
                    "Running slightly behind, but I'll catch you guys there by 7:45 sharp.",
                    "I have time tomorrow afternoon. Let me know what works best for you.",
                    "Sounds like a plan. I'll take care of it on my end.",
                    "Appreciate the update. I'm ready whenever you are."
                )
                Tone.POLITE -> listOf(
                    "Thanks for checking in! Really appreciate you reaching out.",
                    "No problem at all! Take your time, see you soon.",
                    "You're very welcome! Happy to help anytime.",
                    "Thank you for letting me know! Hope you have a wonderful day.",
                    "Sounds great, thank you for clarifying!",
                    "No worries whatsoever, looking forward to catching up soon."
                )
                Tone.FLIRTY -> listOf(
                    "Careful, keep talking like that and you'll have to take me out.",
                    "All good! You can make it up to me over drinks this week 😉",
                    "Just thinking about when I get to see you next...",
                    "You always know how to make me smile. What are you up to later?",
                    "Are you always this charming or am I getting special treatment?",
                    "Don't tease me unless you're planning to back it up!"
                )
                Tone.SAVAGE -> listOf(
                    "I'd agree with you, but then we'd both be wrong.",
                    "Careful, another hour and I would've replaced you.",
                    "Thriving. What's your excuse?",
                    "I see you finally learned how to use your phone.",
                    "Is this your best attempt at a conversation starter?",
                    "Bold strategy. Let's see if it pays off for you."
                )
                Tone.PROFESSIONAL -> listOf(
                    "Thank you for sharing this context. I will review and follow up accordingly.",
                    "Thank you for the update. Please let me know once you are available.",
                    "Acknowledged. I'll make sure this is addressed by the end of the day.",
                    "Thank you for your prompt response. Let us proceed with the next steps.",
                    "Understood. Please feel free to send over any relevant files."
                )
                Tone.PASSIVE_AGGRESSIVE -> listOf(
                    "Per my last message, I thought we already cleared this up.",
                    "Oh no worries, I love waiting around with nothing to do.",
                    "Glad to know my time is as valuable as always.",
                    "No problem! It's not like I had anything important planned anyway.",
                    "As previously discussed, I'll just wait for you to update me."
                )
            }

            val candidatePool = optionsList.filter { it.trim().lowercase() != avoidText.trim().lowercase() }
            val chosenText = candidatePool.randomOrNull() ?: optionsList.random()

            ReplyOption(
                tone = tone,
                text = chosenText,
                contextSnippet = cleanContext.take(80)
            )
        }
    }
}
