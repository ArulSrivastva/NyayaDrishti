package com.sih.model

data class User(
    val id: String,
    val name: String,
    val department: String,
    val region: String,
    val profileImageUrl: String? = null
)
