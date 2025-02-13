package com.garmin.android.apps.connectiq.sample.comm

import android.content.Context
import com.garmin.android.apps.connectiq.sample.comm.AuthInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var retrofit: Retrofit? = null

    fun getInstance(context: Context): Retrofit {
        if (retrofit == null) {
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context))  // 👈 Aggiunto Interceptor per il token
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl("https://d3a-dev.atlantica.it/api/")  // 🔥 Assicurati che finisca con "/"
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    fun create(context: Context): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://d3a-dev.atlantica.it/api/")  // 🔥 Assicurati che finisca con "/"
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
