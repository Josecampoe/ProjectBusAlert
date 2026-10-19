package com.busalert.shared.domain

data class Stop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

data class Route(
    val id: String,
    val name: String,
    val stops: List<Stop>,
    val color: String? = null
)
