package com.garmin.android.apps.connectiq.sample.comm.models

data class LoginResponse(
    val jwt: String,
    val email: String,
    val lang: String,
    val refreshToken: String
)
