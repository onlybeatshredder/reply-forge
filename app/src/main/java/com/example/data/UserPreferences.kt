package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("reply_forge_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRO_USER = "is_pro_user"
        private const val KEY_DAILY_COUNT = "daily_count"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_CUSTOM_INSTRUCTIONS = "custom_instructions"
        private const val KEY_REFERRAL_CODE = "referral_code"
        private const val KEY_REFERRAL_COUNT = "referral_count"
        private const val KEY_BONUS_QUOTA = "bonus_quota"
        private const val KEY_REDEEMED_REFERRAL = "redeemed_referral"
        private const val KEY_PRO_PLAN_TIER = "pro_plan_tier"
        private const val DAILY_FREE_LIMIT = 5
    }

    var isProUser: Boolean
        get() = prefs.getBoolean(KEY_PRO_USER, false)
        set(value) = prefs.edit().putBoolean(KEY_PRO_USER, value).apply()

    var proPlanTier: String
        get() = prefs.getString(KEY_PRO_PLAN_TIER, "Free Tier") ?: "Free Tier"
        set(value) = prefs.edit().putString(KEY_PRO_PLAN_TIER, value).apply()

    var customInstructions: String
        get() = prefs.getString(KEY_CUSTOM_INSTRUCTIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_INSTRUCTIONS, value).apply()

    val referralCode: String
        get() {
            var code = prefs.getString(KEY_REFERRAL_CODE, null)
            if (code.isNullOrBlank()) {
                code = "FORGE-" + (1000..9999).random()
                prefs.edit().putString(KEY_REFERRAL_CODE, code).apply()
            }
            return code
        }

    var referralCount: Int
        get() = prefs.getInt(KEY_REFERRAL_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_REFERRAL_COUNT, value).apply()

    var bonusQuota: Int
        get() = prefs.getInt(KEY_BONUS_QUOTA, 0)
        set(value) = prefs.edit().putInt(KEY_BONUS_QUOTA, value).apply()

    var hasRedeemedReferral: Boolean
        get() = prefs.getBoolean(KEY_REDEEMED_REFERRAL, false)
        set(value) = prefs.edit().putBoolean(KEY_REDEEMED_REFERRAL, value).apply()

    fun getRemainingDailyQuota(): Int {
        if (isProUser) return 999
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        if (today != lastDate) {
            // Reset for new day
            prefs.edit().putString(KEY_LAST_DATE, today).putInt(KEY_DAILY_COUNT, 0).apply()
            return DAILY_FREE_LIMIT + bonusQuota
        }
        val count = prefs.getInt(KEY_DAILY_COUNT, 0)
        return (DAILY_FREE_LIMIT + bonusQuota - count).coerceAtLeast(0)
    }

    fun incrementDailyUsage() {
        if (isProUser) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val currentCount = if (today == lastDate) prefs.getInt(KEY_DAILY_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_LAST_DATE, today)
            .putInt(KEY_DAILY_COUNT, currentCount + 1)
            .apply()
    }
}
