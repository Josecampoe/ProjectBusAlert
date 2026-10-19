package com.busalert.shared.domain

data class Stop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)
