package com.example.xiaomaotai

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val nickname: String,
    val email: String? = null,
    val password: String? = null
)
