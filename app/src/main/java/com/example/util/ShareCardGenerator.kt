package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.model.ReplyOption
import java.io.File
import java.io.FileOutputStream

object ShareCardGenerator {

    fun shareReplyAsCard(context: Context, reply: ReplyOption) {
        try {
            val width = 1080
            val height = 1350 // Modern social 4:5 card aspect ratio
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Draw Gradient Background
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#0F172A"), // Slate 900
                        Color.parseColor("#1E1B4B"), // Dark Indigo
                        Color.parseColor("#312E81")  // Deep Violet
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. Draw Subtle Flame Decorative Accent at Top Left
            val flamePaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 400f, 400f,
                    Color.parseColor("#F59E0B"), Color.parseColor("#EF4444"),
                    Shader.TileMode.CLAMP
                )
                alpha = 60
            }
            canvas.drawCircle(100f, 100f, 300f, flamePaint)

            // 3. Draw Card Container Box
            val cardMargin = 80f
            val cardRect = RectF(cardMargin, cardMargin + 100f, width - cardMargin, height - 160f)
            val cardBgPaint = Paint().apply {
                color = Color.parseColor("#161E2E")
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint().apply {
                color = Color.parseColor("#2E3D58")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(cardRect, 40f, 40f, cardBgPaint)
            canvas.drawRoundRect(cardRect, 40f, 40f, cardBorderPaint)

            // 4. Header Badge (Tone Tag)
            val badgeX = cardMargin + 60f
            val badgeY = cardMargin + 170f
            val badgeText = "${reply.tone.emoji} ${reply.tone.displayName.uppercase()} REPLY"

            val badgeBgPaint = Paint().apply {
                color = when (reply.tone.key) {
                    "savage" -> Color.parseColor("#450A0A")
                    "flirty" -> Color.parseColor("#500724")
                    "funny" -> Color.parseColor("#451A03")
                    "confident" -> Color.parseColor("#172554")
                    "polite" -> Color.parseColor("#064E3B")
                    else -> Color.parseColor("#2E1065")
                }
            }
            val badgeTextPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val textWidth = badgeTextPaint.measureText(badgeText)
            val badgeRect = RectF(badgeX, badgeY - 40f, badgeX + textWidth + 60f, badgeY + 25f)
            canvas.drawRoundRect(badgeRect, 20f, 20f, badgeBgPaint)
            canvas.drawText(badgeText, badgeX + 30f, badgeY + 10f, badgeTextPaint)

            // 5. Context Snippet Label if present
            var currentY = badgeY + 120f
            if (reply.contextSnippet.isNotBlank()) {
                val contextLabelPaint = TextPaint().apply {
                    color = Color.parseColor("#94A3B8")
                    textSize = 32f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    isAntiAlias = true
                }
                val contextQuote = "“${reply.contextSnippet.take(100)}”"
                val contextLayout = StaticLayout.Builder
                    .obtain(contextQuote, 0, contextQuote.length, contextLabelPaint, (width - (cardMargin * 2) - 120).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .build()

                canvas.save()
                canvas.translate(cardMargin + 60f, currentY)
                contextLayout.draw(canvas)
                canvas.restore()

                currentY += contextLayout.height + 60f
            }

            // 6. Main Generated Reply Text
            val replyTextPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 52f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val replyLayout = StaticLayout.Builder
                .obtain(reply.text, 0, reply.text.length, replyTextPaint, (width - (cardMargin * 2) - 120).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.3f)
                .build()

            canvas.save()
            canvas.translate(cardMargin + 60f, currentY)
            replyLayout.draw(canvas)
            canvas.restore()

            // 7. Top App Logo & Branding Badge
            val logoX = width - cardMargin - 320f
            val logoY = cardMargin + 170f
            val logoBgPaint = Paint().apply {
                shader = LinearGradient(
                    logoX, logoY - 40f, logoX + 260f, logoY + 30f,
                    Color.parseColor("#F59E0B"), Color.parseColor("#EF4444"),
                    Shader.TileMode.CLAMP
                )
            }
            val logoTextPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val logoRect = RectF(logoX, logoY - 40f, logoX + 260f, logoY + 30f)
            canvas.drawRoundRect(logoRect, 20f, 20f, logoBgPaint)
            canvas.drawText("REPLYFORGE", logoX + 30f, logoY + 8f, logoTextPaint)

            // 8. Footer Watermark Banner ("FORGED WITH REPLYFORGE AI ⚡")
            val footerBannerRect = RectF(cardMargin + 40f, height - 220f, width - cardMargin - 40f, height - 100f)
            val footerBannerPaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            val footerBannerBorder = Paint().apply {
                color = Color.parseColor("#F59E0B")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(footerBannerRect, 24f, 24f, footerBannerPaint)
            canvas.drawRoundRect(footerBannerRect, 24f, 24f, footerBannerBorder)

            val footerPaint = TextPaint().apply {
                color = Color.parseColor("#F59E0B")
                textSize = 38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val footerText = "FORGED WITH REPLYFORGE AI ⚡"
            val footerWidth = footerPaint.measureText(footerText)
            canvas.drawText(footerText, (width - footerWidth) / 2f, height - 165f, footerPaint)

            val footerSubPaint = TextPaint().apply {
                color = Color.parseColor("#94A3B8")
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            val footerSubText = "Get on Google Play • Created by Robert JIron (Beatshredder)"
            val footerSubWidth = footerSubPaint.measureText(footerSubText)
            canvas.drawText(footerSubText, (width - footerSubWidth) / 2f, height - 125f, footerSubPaint)

            // 9. Save Bitmap to Cache and Share via FileProvider Intent
            val imagesDir = File(context.cacheDir, "images")
            imagesDir.mkdirs()
            val imageFile = File(imagesDir, "reply_forge_card_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val imageUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val promoText = "\"${reply.text}\"\n\n⚡ Forged with ReplyForge AI — Created by Robert JIron (Beatshredder)\nDownload ReplyForge on Google Play!"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, promoText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Reply Card")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback plain text share if bitmap fails
            val plainShare = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "\"${reply.text}\"\n\n⚡ Forged with ReplyForge AI (${reply.tone.displayName})\nCreated by Robert JIron (Beatshredder)\nDownload on Google Play!")
            }
            val chooser = Intent.createChooser(plainShare, "Share Reply")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
