package com.garmin.android.apps.connectiq.sample.comm

import android.content.Context
import android.content.Intent
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val sharedPreferences = context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)
        val jwtToken = sharedPreferences.getString("jwt", null)
        val refreshToken = sharedPreferences.getString("refreshToken", null)

        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Aggiungi l'header con il token JWT
        if (!jwtToken.isNullOrEmpty()) {
            requestBuilder.addHeader("x-auth-token", jwtToken)
        }

        val response = chain.proceed(requestBuilder.build())

        // Se il token è scaduto (401), tentiamo il refresh
        if (response.code == 401 && !refreshToken.isNullOrEmpty()) {
            Log.e("AuthInterceptor", "❌ Token scaduto. Tentativo di refresh token...")

            val newToken = refreshAccessToken(refreshToken)
            return if (newToken != null) {
                // ✅ Ritenta la richiesta con il nuovo token
                val newRequest = requestBuilder
                    .header("x-auth-token", newToken)
                    .build()
                chain.proceed(newRequest)
            } else {
                // ❌ Se anche il refresh fallisce, effettua il logout
                forceLogout()
                response
            }
        }

        return response
    }

    private fun refreshAccessToken(refreshToken: String): String? {
        val refreshUrl = "https://d3a-dev.atlantica.it/api/v2/authenticate/refreshtoken/$refreshToken"

        val request = Request.Builder()
            .url(refreshUrl)
            .get()
            .build()

        return try {
            val response = OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "")
                val newJwt = json.getString("jwt")

                // ✅ Salva il nuovo token
                val sharedPreferences = context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putString("jwt", newJwt)
                    apply()
                }

                Log.d("AuthInterceptor", "🔄 Nuovo token aggiornato con successo!")
                newJwt
            } else {
                Log.e("AuthInterceptor", "❌ Refresh token fallito: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e("AuthInterceptor", "❌ Errore nel refresh token", e)
            null
        }
    }

    private fun forceLogout() {
        Log.e("AuthInterceptor", "❌ Logout forzato: token scaduto e refresh fallito.")

        val sharedPreferences = context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            remove("jwt")
            remove("refreshToken")
            apply()
        }

        val intent = Intent(context, com.garmin.android.apps.connectiq.sample.comm.activities.LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}
