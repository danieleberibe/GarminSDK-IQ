package com.garmin.android.apps.connectiq.sample.comm

import com.garmin.android.apps.connectiq.sample.comm.models.LoginRequest
import com.garmin.android.apps.connectiq.sample.comm.models.LoginResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {
    @POST("v2/authenticate")
    @Headers("Content-Type: application/json")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

}
