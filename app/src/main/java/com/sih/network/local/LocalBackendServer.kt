package com.sih.network.local

import android.content.Context
import android.util.Log
import com.sih.data.local.LocalDatabase
import com.sih.network.dto.FullInspectionResponse
import com.sih.network.dto.InspectionDetailOutDto
import com.sih.network.dto.ProductDto
import com.sih.network.dto.ProductHistoryResponse
import com.sih.network.dto.TokenResponseDto
import com.sih.network.dto.UserOutDto
import com.sih.util.ReportGenerator
import java.io.ByteArrayOutputStream
import java.util.UUID

object LocalBackendServer {

    private const val TAG = "LocalBackendServer"
    private var isRunning = false
    private var appContext: Context? = null
    private var localDb: LocalDatabase? = null

    fun start(context: Context) {
        if (!isRunning) {
            appContext = context.applicationContext
            localDb = LocalDatabase.getInstance(context.applicationContext)
            isRunning = true
            Log.i(TAG, "NyayaDrishti On-Device Autonomous Backend Server started successfully.")
        }
    }

    fun isRunning(): Boolean = isRunning

    fun getDatabase(): LocalDatabase {
        return localDb ?: throw IllegalStateException("LocalBackendServer not started. Call LocalBackendServer.start(context).")
    }

    fun login(email: String): TokenResponseDto? {
        val db = getDatabase()
        val user = db.authenticateOfficer(email) ?: return null
        val token = "local_jwt_" + UUID.randomUUID().toString().replace("-", "")
        return TokenResponseDto(
            accessToken = token,
            tokenType = "bearer",
            user = user
        )
    }

    fun listInspections(skip: Int = 0, limit: Int = 100): List<InspectionDetailOutDto> {
        return getDatabase().getInspectionDetails(limit)
    }

    fun getInspectionCount(): Int {
        return try {
            getDatabase().getInspectionCount()
        } catch (e: Exception) {
            0
        }
    }

    fun getInspection(id: Int): InspectionDetailOutDto? {
        val full = getDatabase().getInspectionById(id) ?: return null
        return InspectionDetailOutDto(
            id = full.inspectionId ?: id,
            productId = full.product?.id ?: id,
            status = full.compliance?.status,
            complianceStatus = full.compliance?.status,
            riskLevel = full.risk?.level,
            overallConfidence = full.confidence?.overall,
            createdAt = full.createdAt,
            declarations = full.declarations ?: emptyList(),
            violations = full.violations ?: emptyList()
        )
    }

    fun getInspectionFull(id: Int): FullInspectionResponse? {
        return getDatabase().getInspectionById(id)
    }

    fun submitDecision(id: Int, decision: String, reason: String?): FullInspectionResponse? {
        val db = getDatabase()
        db.saveDecision(id, decision, reason)
        return db.getInspectionById(id)
    }

    fun getProductHistory(productId: Int): ProductHistoryResponse {
        return getDatabase().getProductHistory(productId)
    }

    fun listProducts(skip: Int = 0, limit: Int = 100): List<ProductDto> {
        val inspections = getDatabase().getAllInspections(limit)
        return inspections.mapNotNull { it.product }.distinctBy { it.id ?: it.name }
    }

    fun saveInspection(response: FullInspectionResponse): Long {
        return getDatabase().insertOrUpdateInspection(response)
    }

    fun generateReportPdfBytes(inspectionId: Int): ByteArray? {
        val context = appContext ?: return null
        val full = getDatabase().getInspectionById(inspectionId)
        val stream = ByteArrayOutputStream()
        val success = ReportGenerator.writeReportToStream(context, full, inspectionId.toString(), stream)
        return if (success) stream.toByteArray() else null
    }
}
