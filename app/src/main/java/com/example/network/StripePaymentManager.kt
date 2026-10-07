package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class StripePaymentResult {
    data class Success(val paymentIntentId: String, val amountPaid: String) : StripePaymentResult()
    data class Error(val message: String) : StripePaymentResult()
}

class StripePaymentManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun createAndConfirmPaymentIntent(
        cardNumber: String,
        expMonth: String,
        expYear: String,
        cvc: String,
        amountInCents: Int = 299, // $2.99 USD
        currency: String = "usd"
    ): StripePaymentResult = withContext(Dispatchers.IO) {
        val secretKey = try { BuildConfig.STRIPE_SECRET_KEY } catch (e: Exception) { "" }

        if (secretKey.isBlank()) {
            return@withContext StripePaymentResult.Error("Stripe Secret Key missing.")
        }

        try {
            val cleanCard = cardNumber.replace(" ", "").trim()

            // Step 1: Create PaymentMethod directly using Secret Key (server-side API call)
            val pmForm = FormBody.Builder()
                .add("type", "card")
                .add("card[number]", cleanCard)
                .add("card[exp_month]", expMonth)
                .add("card[exp_year]", expYear)
                .add("card[cvc]", cvc)
                .build()

            val pmRequest = Request.Builder()
                .url("https://api.stripe.com/v1/payment_methods")
                .addHeader("Authorization", "Bearer $secretKey")
                .post(pmForm)
                .build()

            val pmResponse = client.newCall(pmRequest).execute()
            val pmResponseBody = pmResponse.body?.string()

            if (pmResponse.isSuccessful && !pmResponseBody.isNullOrBlank()) {
                val pmJson = JSONObject(pmResponseBody)
                val paymentMethodId = pmJson.optString("id")

                if (paymentMethodId.isNotBlank()) {
                    // Step 2: Create & Confirm PaymentIntent
                    val piForm = FormBody.Builder()
                        .add("amount", amountInCents.toString())
                        .add("currency", currency)
                        .add("payment_method", paymentMethodId)
                        .add("confirm", "true")
                        .add("description", "ReplyForge Pro 1-Month Subscription")
                        .add("automatic_payment_methods[enabled]", "true")
                        .add("automatic_payment_methods[allow_redirects]", "never")
                        .build()

                    val piRequest = Request.Builder()
                        .url("https://api.stripe.com/v1/payment_intents")
                        .addHeader("Authorization", "Bearer $secretKey")
                        .post(piForm)
                        .build()

                    val piResponse = client.newCall(piRequest).execute()
                    val piResponseBody = piResponse.body?.string()

                    if (piResponse.isSuccessful && !piResponseBody.isNullOrBlank()) {
                        val piJson = JSONObject(piResponseBody)
                        val status = piJson.optString("status")
                        val id = piJson.optString("id")

                        if (status == "succeeded" || status == "requires_capture") {
                            return@withContext StripePaymentResult.Success(
                                paymentIntentId = id,
                                amountPaid = "$${amountInCents / 100.00}"
                            )
                        }
                    }
                }
            }

            // Step 3: Direct Charge Fallback using Secret Key
            // Do NOT include "source" parameter when passing raw card[...] details to Stripe
            val chargeForm = FormBody.Builder()
                .add("amount", amountInCents.toString())
                .add("currency", currency)
                .add("card[number]", cleanCard)
                .add("card[exp_month]", expMonth)
                .add("card[exp_year]", expYear)
                .add("card[cvc]", cvc)
                .add("description", "ReplyForge Pro Subscription")
                .build()

            val chargeRequest = Request.Builder()
                .url("https://api.stripe.com/v1/charges")
                .addHeader("Authorization", "Bearer $secretKey")
                .post(chargeForm)
                .build()

            val chargeResponse = client.newCall(chargeRequest).execute()
            val chargeResponseBody = chargeResponse.body?.string()

            if (chargeResponse.isSuccessful && !chargeResponseBody.isNullOrBlank()) {
                val chargeJson = JSONObject(chargeResponseBody)
                val paid = chargeJson.optBoolean("paid", false)
                val status = chargeJson.optString("status")
                val chargeId = chargeJson.optString("id")

                if (paid || status == "succeeded") {
                    return@withContext StripePaymentResult.Success(
                        paymentIntentId = chargeId,
                        amountPaid = "$${amountInCents / 100.00}"
                    )
                }
            } else if (!chargeResponseBody.isNullOrBlank()) {
                val errJson = JSONObject(chargeResponseBody)
                val errMsg = errJson.optJSONObject("error")?.optString("message")
                if (!errMsg.isNullOrBlank()) {
                    return@withContext StripePaymentResult.Error(errMsg)
                }
            }

            return@withContext StripePaymentResult.Error("Payment authorization failed. Please verify card details.")

        } catch (e: Exception) {
            Log.e("StripePaymentManager", "Stripe payment error", e)
            return@withContext StripePaymentResult.Error(e.localizedMessage ?: "Network error during Stripe transaction.")
        }
    }
}
