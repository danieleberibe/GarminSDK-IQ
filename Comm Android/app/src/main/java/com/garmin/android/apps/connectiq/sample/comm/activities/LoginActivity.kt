package com.garmin.android.apps.connectiq.sample.comm.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.app.Activity
import com.garmin.android.apps.connectiq.sample.comm.ApiService
import com.garmin.android.apps.connectiq.sample.comm.R
import com.garmin.android.apps.connectiq.sample.comm.models.LoginRequest
import com.garmin.android.apps.connectiq.sample.comm.models.LoginResponse
import com.garmin.android.apps.connectiq.sample.comm.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se l'utente è già loggato, vai direttamente alla MainActivity
        if (isUserLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()  // Chiudi la LoginActivity per non tornare indietro con il "Back"
            return
        }

        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.username)
        val password = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.login_button)

        loginButton.setOnClickListener {
            performLogin(email.text.toString(), password.text.toString())
        }
    }

    private fun performLogin(email: String, password: String) {
        val retrofit = RetrofitClient.getInstance(this)  // 👈 Usa getInstance() per ottenere Retrofit
        val apiService = retrofit.create(ApiService::class.java)

        val call = apiService.login(LoginRequest(email, password))
        call.enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    loginResponse?.let {
                        // ✅ Salva il JWT e il refreshToken
                        saveTokens(it.jwt, it.refreshToken)

                        // ✅ Vai alla MainActivity
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    Toast.makeText(applicationContext, "Credenziali non valide", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Errore di rete", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun saveTokens(jwt: String, refreshToken: String?) {
        val sharedPreferences = getSharedPreferences("AuthPrefs", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("jwt", jwt)
            putString("refreshToken", refreshToken)
            apply()  // Importante per assicurare il salvataggio
        }
        // ✅ LOG per verificare che il token venga salvato
        Log.d("LoginActivity", "🔑 JWT Salvato: $jwt")
        Log.d("LoginActivity", "🔄 Refresh Token Salvato: ${refreshToken ?: "Nessun refresh token ricevuto"}")
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPreferences = getSharedPreferences("AuthPrefs", MODE_PRIVATE)
        val jwt = sharedPreferences.getString("jwt", null)
        return !jwt.isNullOrEmpty()
    }



}
