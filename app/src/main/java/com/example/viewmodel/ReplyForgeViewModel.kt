package com.example.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.UserPreferences
import com.example.db.AppDatabase
import com.example.db.ReplyEntity
import com.example.model.GenerationState
import com.example.model.RelationshipContext
import com.example.model.ReplyOption
import com.example.model.Tone
import com.example.network.GeminiReplyRepository
import com.example.network.StripePaymentManager
import com.example.network.StripePaymentResult
import com.example.util.OcrHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReplyForgeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.replyDao()
    private val prefs = UserPreferences(application)
    private val repository = GeminiReplyRepository()
    private val stripeManager = StripePaymentManager()

    private val _uiState = MutableStateFlow(
        GenerationState(
            dailyQuotaRemaining = prefs.getRemainingDailyQuota(),
            isProUser = prefs.isProUser,
            customInstruction = prefs.customInstructions
        )
    )
    val uiState: StateFlow<GenerationState> = _uiState.asStateFlow()

    val historyList: StateFlow<List<ReplyOption>> = dao.getAllReplies().map { entities ->
        entities.map { entity ->
            ReplyOption(
                id = entity.id,
                tone = Tone.fromKey(entity.toneKey),
                text = entity.text,
                timestamp = entity.timestamp,
                contextSnippet = entity.contextSnippet,
                isFavorite = entity.isFavorite
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteList: StateFlow<List<ReplyOption>> = dao.getFavoriteReplies().map { entities ->
        entities.map { entity ->
            ReplyOption(
                id = entity.id,
                tone = Tone.fromKey(entity.toneKey),
                text = entity.text,
                timestamp = entity.timestamp,
                contextSnippet = entity.contextSnippet,
                isFavorite = entity.isFavorite
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showProPaywall = MutableStateFlow(false)
    val showReferralSheet = MutableStateFlow(false)
    val showCreatorDialog = MutableStateFlow(false)
    val toastMessage = MutableStateFlow<String?>(null)
    val isPaymentProcessing = MutableStateFlow(false)
    val detectedClipboardText = MutableStateFlow<String?>(null)

    val referralCode: String get() = prefs.referralCode
    val referralCount: Int get() = prefs.referralCount
    val bonusQuota: Int get() = prefs.bonusQuota

    fun checkClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val primaryClip = clipboard.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val clipText = primaryClip.getItemAt(0).text?.toString()?.trim()
                if (!clipText.isNullOrBlank() && clipText != _uiState.value.contextText.trim() && clipText.length > 3) {
                    detectedClipboardText.value = clipText
                } else {
                    detectedClipboardText.value = null
                }
            }
        } catch (e: Exception) {
            // Ignore clipboard errors
        }
    }

    fun applyDetectedClipboardText() {
        detectedClipboardText.value?.let { clip ->
            onContextTextChanged(clip)
            toastMessage.value = "Pasted from clipboard! 📋"
            detectedClipboardText.value = null
        }
    }

    fun dismissClipboardChip() {
        detectedClipboardText.value = null
    }

    fun onContextTextChanged(newText: String) {
        _uiState.update { it.copy(contextText = newText, errorMessage = null) }
    }

    fun onRelationshipContextChanged(context: RelationshipContext) {
        _uiState.update { it.copy(relationshipContext = context) }
    }

    fun toggleToneSelection(tone: Tone) {
        val current = _uiState.value.selectedTones.toMutableList()
        val isProUser = _uiState.value.isProUser

        // Check paywall for Pro tones (flirty & savage)
        if (tone.isPro && !isProUser) {
            showProPaywall.value = true
            return
        }

        if (current.contains(tone)) {
            if (current.size > 1) {
                current.remove(tone)
            }
        } else {
            if (current.size < 3) {
                current.add(tone)
            } else {
                current[2] = tone
            }
        }
        _uiState.update { it.copy(selectedTones = current) }
    }

    fun processImageOcr(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(ocrProcessing = true) }
            val extracted = OcrHelper.extractTextFromImageUri(context, uri)
            _uiState.update {
                it.copy(
                    ocrProcessing = false,
                    contextText = if (extracted.isNotBlank()) extracted else it.contextText
                )
            }
            if (extracted.isNotBlank()) {
                toastMessage.value = "Text extracted from screenshot! ✨"
            } else {
                toastMessage.value = "Couldn't read text from image. Try pasting manually."
            }
        }
    }

    fun generateReplies() {
        val state = _uiState.value
        val contextText = state.contextText.trim()

        if (contextText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter or scan a text message first.") }
            return
        }

        if (!state.isProUser && state.dailyQuotaRemaining <= 0) {
            showProPaywall.value = true
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val generated = repository.generateReplies(
                contextText = contextText,
                selectedTones = state.selectedTones,
                relationship = state.relationshipContext,
                customInstruction = state.customInstruction
            )

            prefs.incrementDailyUsage()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentReplies = generated,
                    dailyQuotaRemaining = prefs.getRemainingDailyQuota()
                )
            }

            val entities = generated.map {
                ReplyEntity(
                    id = it.id,
                    toneKey = it.tone.key,
                    text = it.text,
                    timestamp = it.timestamp,
                    contextSnippet = it.contextSnippet,
                    isFavorite = false
                )
            }
            dao.insertReplies(entities)
        }
    }

    fun regenerateSingleReply(option: ReplyOption) {
        viewModelScope.launch {
            // Show inline loading indicator text for card
            val placeholderList = _uiState.value.currentReplies.map {
                if (it.id == option.id) it.copy(text = "Rerolling AI response option...") else it
            }
            _uiState.update { it.copy(currentReplies = placeholderList) }

            val singleList = repository.generateReplies(
                contextText = _uiState.value.contextText,
                selectedTones = listOf(option.tone),
                relationship = _uiState.value.relationshipContext,
                customInstruction = _uiState.value.customInstruction,
                avoidText = option.text
            )
            val newReply = singleList.find { it.tone == option.tone } ?: singleList.firstOrNull()
            if (newReply != null) {
                val updatedReplies = _uiState.value.currentReplies.map {
                    if (it.id == option.id) newReply.copy(id = option.id) else it
                }
                _uiState.update { it.copy(currentReplies = updatedReplies) }

                dao.insertReply(
                    ReplyEntity(
                        id = option.id,
                        toneKey = newReply.tone.key,
                        text = newReply.text,
                        timestamp = System.currentTimeMillis(),
                        contextSnippet = newReply.contextSnippet
                    )
                )
                toastMessage.value = "Rerolled ${option.tone.displayName} response! ⚡"
            } else {
                toastMessage.value = "Couldn't reroll option. Try again."
            }
        }
    }

    fun clearCurrentGeneratedOptions() {
        _uiState.update { it.copy(currentReplies = emptyList()) }
        toastMessage.value = "Cleared all generated options 🧹"
    }

    fun toggleFavorite(option: ReplyOption) {
        viewModelScope.launch {
            val newFavStatus = !option.isFavorite
            dao.setFavorite(option.id, newFavStatus)
            val updated = _uiState.value.currentReplies.map {
                if (it.id == option.id) it.copy(isFavorite = newFavStatus) else it
            }
            _uiState.update { it.copy(currentReplies = updated) }
            toastMessage.value = if (newFavStatus) "Added to Favorites ⭐" else "Removed from Favorites"
        }
    }

    fun updateReplyText(id: String, newText: String) {
        viewModelScope.launch {
            val updated = _uiState.value.currentReplies.map {
                if (it.id == id) it.copy(text = newText) else it
            }
            _uiState.update { it.copy(currentReplies = updated) }

            val option = updated.find { it.id == id }
            if (option != null) {
                dao.insertReply(
                    ReplyEntity(
                        id = option.id,
                        toneKey = option.tone.key,
                        text = newText,
                        timestamp = option.timestamp,
                        contextSnippet = option.contextSnippet,
                        isFavorite = option.isFavorite
                    )
                )
            }
        }
    }

    fun processStripeCheckout(
        cardNumber: String,
        expMonth: String,
        expYear: String,
        cvc: String,
        amountInCents: Int = 299,
        planTitle: String = "Pro Pass",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            isPaymentProcessing.value = true
            val result = stripeManager.createAndConfirmPaymentIntent(
                cardNumber = cardNumber,
                expMonth = expMonth,
                expYear = expYear,
                cvc = cvc,
                amountInCents = amountInCents
            )
            isPaymentProcessing.value = false

            when (result) {
                is StripePaymentResult.Success -> {
                    upgradeToPro(planTitle)
                    onSuccess()
                }
                is StripePaymentResult.Error -> {
                    onError(result.message)
                }
            }
        }
    }

    fun upgradeToPro(planTitle: String = "Pro Pass") {
        prefs.isProUser = true
        prefs.proPlanTier = planTitle
        _uiState.update {
            it.copy(
                isProUser = true,
                dailyQuotaRemaining = 999
            )
        }
        showProPaywall.value = false
        toastMessage.value = "Welcome to $planTitle! All tones & 100% ad-free experience unlocked. ⚡"
    }

    fun grantRewardedAdBonus() {
        prefs.bonusQuota = prefs.bonusQuota + 3
        _uiState.update {
            it.copy(dailyQuotaRemaining = prefs.getRemainingDailyQuota())
        }
        toastMessage.value = "AdMob Video Reward Claimed! +3 Extra AI Response Generations Unlocked 🎁"
    }

    fun redeemReferralCode(code: String) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            toastMessage.value = "Please enter a valid referral code."
            return
        }

        if (cleanCode == prefs.referralCode) {
            toastMessage.value = "You cannot redeem your own referral code!"
            return
        }

        if (prefs.hasRedeemedReferral) {
            toastMessage.value = "You have already redeemed a referral code."
            return
        }

        // Grant 10 bonus generations
        prefs.hasRedeemedReferral = true
        prefs.bonusQuota = prefs.bonusQuota + 10
        prefs.referralCount = prefs.referralCount + 1

        _uiState.update {
            it.copy(dailyQuotaRemaining = prefs.getRemainingDailyQuota())
        }

        showReferralSheet.value = false
        toastMessage.value = "Referral code redeemed! +10 Bonus AI Generations Unlocked! 🎁"
    }

    fun updateCustomInstruction(instruction: String) {
        prefs.customInstructions = instruction
        _uiState.update { it.copy(customInstruction = instruction) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
            toastMessage.value = "History cleared"
        }
    }

    fun clearToast() {
        toastMessage.value = null
    }
}
