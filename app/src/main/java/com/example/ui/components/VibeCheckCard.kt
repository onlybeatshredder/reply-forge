package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ForgeOrange
import com.example.ui.theme.ForgePurple

data class VibeAnalysis(
    val sentiment: String,
    val interestScore: Int,
    val recommendedTone: String,
    val strategyTip: String
)

fun analyzeContextVibe(text: String): VibeAnalysis {
    val lower = text.lowercase()
    return when {
        lower.contains("hey") || lower.contains("haha") || lower.contains("lol") || lower.contains("😊") || lower.contains("u up") ->
            VibeAnalysis(
                sentiment = "Playful & Casual 😊",
                interestScore = 85,
                recommendedTone = "Flirty or Confident",
                strategyTip = "Match their energy with a playful, witty response!"
            )
        lower.contains("meeting") || lower.contains("project") || lower.contains("client") || lower.contains("asap") || lower.contains("email") ->
            VibeAnalysis(
                sentiment = "Professional & Formal 💼",
                interestScore = 90,
                recommendedTone = "Polite or Professional",
                strategyTip = "Keep it concise, clear, and action-oriented."
            )
        lower.contains("why") || lower.contains("whatever") || lower.contains("fine") || lower.contains("k.") || lower.contains("sure") ->
            VibeAnalysis(
                sentiment = "Guarded / Mild Passive Aggression ⚠️",
                interestScore = 60,
                recommendedTone = "Savage or Confident",
                strategyTip = "Stand your ground with high confidence or clever humor!"
            )
        else ->
            VibeAnalysis(
                sentiment = "Friendly & Open Conversation ✨",
                interestScore = 80,
                recommendedTone = "Funny or Confident",
                strategyTip = "Ask a playful follow-up question to keep the conversation flowing."
            )
    }
}

@Composable
fun VibeCheckCard(
    contextText: String
) {
    if (contextText.length < 5) return

    val analysis = analyzeContextVibe(contextText)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(ForgeOrange, ForgePurple)),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ForgeOrange, ForgePurple))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Vibe Check & Analyzer 🧠",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ForgeOrange)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Vibe: ${analysis.interestScore}%",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DETECTED MOOD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = analysis.sentiment,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BEST RESPONSE TONE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = analysis.recommendedTone,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = ForgeOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 Strategy: ${analysis.strategyTip}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
