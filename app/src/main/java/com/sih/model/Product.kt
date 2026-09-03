package com.sih.model

data class Product(
    val id: String,
    val name: String,
    val manufacturer: String,
    val mrp: String,
    val netQuantity: String,
    val consumerCare: String,
    val totalInspections: Int,
    val totalViolations: Int,
    val riskLevel: RiskLevel
)
