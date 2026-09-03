package com.sih.model

data class Violation(
    val id: String,
    val type: String,
    val detectedText: String,
    val confidence: Float,
    val reason: String,
    val ruleReference: String,
    val imagePath: String,
    val boundingBox: BoundingBox? = null
)

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
