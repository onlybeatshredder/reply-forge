package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ForgeBlue
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeFlameRed
import com.example.ui.theme.ForgeOrange
import com.example.ui.theme.ForgePink
import com.example.ui.theme.ForgePurple

enum class Tone(
    val key: String,
    val displayName: String,
    val description: String,
    val emoji: String,
    val badgeColor: Color,
    val isPro: Boolean = false
) {
    FUNNY("funny", "Funny", "Witty banter & light humor", "😂", ForgeOrange, isPro = false),
    CONFIDENT("confident", "Confident", "Direct, high-value & concise", "💪", ForgeBlue, isPro = false),
    POLITE("polite", "Polite", "Warm, respectful & smooth", "🤝", ForgeEmerald, isPro = false),
    FLIRTY("flirty", "Flirty", "Playful teasing & charming", "😉", ForgePink, isPro = true),
    SAVAGE("savage", "Savage", "Sharp, blunt comeback", "🔥", ForgeFlameRed, isPro = true),
    PROFESSIONAL("professional", "Professional", "Polished, formal & clear", "💼", ForgePurple, isPro = false),
    PASSIVE_AGGRESSIVE("passive_aggressive", "Passive Aggressive", "Subtle shade & dry sarcasm", "👀", Color(0xFFF97316), isPro = false);

    companion object {
        fun fromKey(key: String): Tone {
            return entries.find { it.key.lowercase() == key.lowercase().replace(" ", "_") } ?: FUNNY
        }
    }
}

enum class RelationshipContext(
    val key: String,
    val displayName: String,
    val icon: String
) {
    MATCH("match", "Dating Match / Crush", "💘"),
    EX("ex", "Ex / Flame", "💔"),
    BOSS("boss", "Boss / Supervisor", "👔"),
    FRIEND("friend", "Friend / Buddy", "🥳"),
    COWORKER("coworker", "Co-worker", "💼"),
    CLIENT("client", "Client / Customer", "🤝"),
    FAMILY("family", "Family Member", "🏡"),
    CUSTOM("custom", "General", "💬");

    companion object {
        fun fromKey(key: String): RelationshipContext {
            return entries.find { it.key.lowercase() == key.lowercase() } ?: CUSTOM
        }
    }
}

data class ReplyOption(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tone: Tone,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val contextSnippet: String = "",
    val isFavorite: Boolean = false
)

data class GenerationState(
    val contextText: String = "",
    val relationshipContext: RelationshipContext = RelationshipContext.CUSTOM,
    val selectedTones: List<Tone> = listOf(Tone.FUNNY, Tone.CONFIDENT, Tone.POLITE),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentReplies: List<ReplyOption> = emptyList(),
    val ocrProcessing: Boolean = false,
    val dailyQuotaRemaining: Int = 5,
    val isProUser: Boolean = false,
    val customInstruction: String = ""
)
