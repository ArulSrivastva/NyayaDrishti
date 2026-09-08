package com.sih.repository

import android.content.Context
import android.net.Uri
import com.sih.model.BoundingBox
import com.sih.model.ComplianceStatus
import com.sih.model.Inspection
import com.sih.model.Product
import com.sih.model.RiskLevel
import com.sih.model.User
import com.sih.model.Violation
import com.sih.network.ApiClient
import com.sih.network.dto.DecisionRequestDto
import com.sih.network.dto.FullInspectionResponse
import com.sih.network.dto.InspectionDetailOutDto
import com.sih.network.dto.LoginRequestDto
import com.sih.network.dto.ProductHistoryResponse
import com.sih.util.OnDeviceAiEngine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.sih.network.local.LocalBackendServer

object InspectionRepository {

    var currentInspection: FullInspectionResponse? = null
    var currentInspectionId: Int? = null
    var currentQualityResult: com.sih.network.dto.ImageQualityResult? = null
    var activeImageUris: List<Uri> = emptyList()
    var activeCommodityName: String? = null

    // 7-System Upgrade Draft & Provenance State
    var currentDraft: com.sih.model.InspectionDraft? = null
    var currentEstablishmentName: String? = null
    var currentInspectionType: String? = null
    var currentLocation: String? = null
    var currentNumberOfPackages: String? = null
    var currentRemarks: String? = null

    fun saveDraft(context: Context, draft: com.sih.model.InspectionDraft) {
        currentDraft = draft
        com.sih.data.local.LocalDatabase.getInstance(context).saveDraft(draft)
    }

    fun getDraft(context: Context, inspectionId: String): com.sih.model.InspectionDraft? {
        return com.sih.data.local.LocalDatabase.getInstance(context).getDraft(inspectionId)
    }

    fun getActiveDrafts(context: Context): List<com.sih.model.InspectionDraft> {
        return com.sih.data.local.LocalDatabase.getInstance(context).getActiveDrafts()
    }

    fun updateDraftState(context: Context, inspectionId: String, state: com.sih.model.DraftState, jsonData: String) {
        currentDraft = currentDraft?.copy(state = state, lastUpdated = LocalDateTime.now().toString())
        com.sih.data.local.LocalDatabase.getInstance(context).updateDraftState(inspectionId, state, jsonData)
    }

    fun abandonDraft(context: Context, inspectionId: String) {
        val draft = getDraft(context, inspectionId)
        if (draft != null) {
            val updated = draft.copy(state = com.sih.model.DraftState.ABANDONED, lastUpdated = LocalDateTime.now().toString())
            saveDraft(context, updated)
        }
        com.sih.data.local.LocalDatabase.getInstance(context).updateDraftState(inspectionId, com.sih.model.DraftState.ABANDONED, "")
        if (currentDraft?.inspectionId == inspectionId) {
            currentDraft = null
        }
    }

    fun completeDraft(context: Context, inspectionId: String) {
        val draft = getDraft(context, inspectionId)
        if (draft != null) {
            val updated = draft.copy(state = com.sih.model.DraftState.COMPLETED, lastUpdated = LocalDateTime.now().toString())
            saveDraft(context, updated)
        }
        com.sih.data.local.LocalDatabase.getInstance(context).updateDraftState(inspectionId, com.sih.model.DraftState.COMPLETED, "")
        if (currentDraft?.inspectionId == inspectionId) {
            currentDraft = null
        }
    }

    fun clearCurrentInspection() {
        currentInspection = null
        currentInspectionId = null
        currentQualityResult = null
        currentDraft = null
    }

    fun resetSession() {
        clearCurrentInspection()
        activeImageUris = emptyList()
        activeCommodityName = null
        currentEstablishmentName = null
        currentInspectionType = null
        currentLocation = null
        currentNumberOfPackages = null
        currentRemarks = null
    }

    suspend fun login(email: String, pass: String): Result<User> {
        return try {
            val response = ApiClient.getService().login(LoginRequestDto(email, pass))
            if (response.isSuccessful && response.body() != null) {
                val tokenDto = response.body()!!
                ApiClient.getTokenManager()?.saveToken(tokenDto.accessToken)
                ApiClient.getTokenManager()?.saveUser(
                    tokenDto.user.id,
                    tokenDto.user.email,
                    tokenDto.user.fullName,
                    tokenDto.user.role
                )
                Result.success(
                    User(
                        id = tokenDto.user.id.toString(),
                        name = tokenDto.user.fullName,
                        department = "Legal Metrology Dept.",
                        region = "Enforcement Zone"
                    )
                )
            } else {
                val localToken = LocalBackendServer.login(email)
                if (localToken != null) {
                    ApiClient.getTokenManager()?.saveToken(localToken.accessToken)
                    ApiClient.getTokenManager()?.saveUser(
                        localToken.user.id,
                        localToken.user.email,
                        localToken.user.fullName,
                        localToken.user.role
                    )
                    Result.success(
                        User(
                            id = localToken.user.id.toString(),
                            name = localToken.user.fullName,
                            department = "Legal Metrology Dept.",
                            region = "Enforcement Zone"
                        )
                    )
                } else {
                    Result.failure(Exception("Invalid officer credentials"))
                }
            }
        } catch (e: Exception) {
            val localToken = LocalBackendServer.login(email)
            if (localToken != null) {
                ApiClient.getTokenManager()?.saveToken(localToken.accessToken)
                ApiClient.getTokenManager()?.saveUser(
                    localToken.user.id,
                    localToken.user.email,
                    localToken.user.fullName,
                    localToken.user.role
                )
                Result.success(
                    User(
                        id = localToken.user.id.toString(),
                        name = localToken.user.fullName,
                        department = "Legal Metrology Dept.",
                        region = "Enforcement Zone"
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun runInspection(context: Context, imageUri: Uri, productName: String? = null): Result<FullInspectionResponse> {
        return runInspection(context, listOf(imageUri), productName)
    }

    suspend fun runInspection(context: Context, imageUris: List<Uri>, productName: String? = null): Result<FullInspectionResponse> {
        return try {
            // First run on-device ML Kit OCR & rule engine across all captured image sides with quality context and officer ground-truth commodity
            val localResult = OnDeviceAiEngine.processImagesLocally(context, imageUris, currentQualityResult, productName)
            currentInspection = localResult
            currentInspectionId = localResult.inspectionId

            // Immediately persist to on-device database
            LocalBackendServer.saveInspection(localResult)

            Result.success(localResult)
        } catch (e: Exception) {
            android.util.Log.e("InspectionRepository", "Inspect exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getRecentInspections(): List<Inspection> {
        val localList = try {
            LocalBackendServer.getDatabase().getAllInspections().map { mapToInspection(it) }
        } catch (e: Exception) {
            emptyList()
        }

        return try {
            val response = ApiClient.getService().listInspections(skip = 0, limit = 50)
            if (response.isSuccessful && response.body() != null) {
                val remoteList = response.body()!!.map { mapToInspection(it) }
                // Merge: local inspections always take priority by ID
                (localList + remoteList).distinctBy { it.id }.sortedByDescending { it.date }
            } else {
                localList
            }
        } catch (e: Exception) {
            localList
        }
    }

    suspend fun getInspectionFull(id: Int): FullInspectionResponse? {
        return try {
            val response = ApiClient.getService().getInspectionFull(id)
            if (response.isSuccessful && response.body() != null) {
                currentInspection = response.body()
                currentInspectionId = id
                response.body()
            } else {
                val local = LocalBackendServer.getInspectionFull(id)
                currentInspection = local
                currentInspectionId = id
                local
            }
        } catch (e: Exception) {
            val local = LocalBackendServer.getInspectionFull(id)
            currentInspection = local
            currentInspectionId = id
            local
        }
    }

    suspend fun submitDecision(id: Int, decision: String, reason: String?): Result<FullInspectionResponse> {
        return try {
            val response = ApiClient.getService().submitDecision(id, DecisionRequestDto(decision, reason))
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                currentInspection = result
                Result.success(result)
            } else {
                val updated = LocalBackendServer.submitDecision(id, decision, reason)
                if (updated != null) {
                    currentInspection = updated
                    Result.success(updated)
                } else {
                    Result.failure(Exception("Decision failed"))
                }
            }
        } catch (e: Exception) {
            val updated = LocalBackendServer.submitDecision(id, decision, reason)
            if (updated != null) {
                currentInspection = updated
                Result.success(updated)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getProductHistory(productId: Int): ProductHistoryResponse? {
        return try {
            val response = ApiClient.getService().getProductHistory(productId)
            if (response.isSuccessful && response.body() != null) {
                response.body()
            } else {
                LocalBackendServer.getProductHistory(productId)
            }
        } catch (e: Exception) {
            LocalBackendServer.getProductHistory(productId)
        }
    }

    fun createFallbackInspection(context: Context, imageUri: Uri): FullInspectionResponse {
        val imagePath = try {
            uriToFile(context, imageUri).absolutePath
        } catch (e: Exception) {
            imageUri.toString()
        }

        val fallback = FullInspectionResponse(
            inspectionId = System.currentTimeMillis().toInt().let { if (it < 0) -it else it },
            product = com.sih.network.dto.ProductDto(
                id = null,
                name = "Scanned Commodity Package",
                manufacturer = null,
                netQuantity = null,
                mrp = null
            ),
            declarations = emptyList(),
            violations = emptyList(),
            evidence = emptyList(),
            risk = com.sih.network.dto.RiskDto("LOW", "No rule violations detected."),
            confidence = com.sih.network.dto.ConfidenceDto(0.0f, "UNPROCESSED"),
            compliance = com.sih.network.dto.ComplianceDto("REVIEW"),
            inspector = com.sih.network.dto.InspectorDto(null, null, null),
            imagePath = imagePath,
            createdAt = java.time.LocalDateTime.now().toString()
        )
        currentInspection = fallback
        currentInspectionId = fallback.inspectionId
        return fallback
    }

    fun mapToInspection(dto: InspectionDetailOutDto): Inspection {
        val status = when (dto.complianceStatus?.uppercase() ?: dto.status?.uppercase()) {
            "PASS", "COMPLIANT" -> ComplianceStatus.COMPLIANT
            "FAIL", "NON_COMPLIANT" -> ComplianceStatus.NON_COMPLIANT
            else -> ComplianceStatus.NEEDS_REVIEW
        }
        val risk = when (dto.riskLevel?.uppercase()) {
            "HIGH" -> RiskLevel.HIGH
            "MEDIUM" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val date = parseDate(dto.createdAt)
        val name = dto.productName ?: dto.product?.name ?: "Inspection #${dto.id}"
        val vCount = if (dto.violations.isNotEmpty()) dto.violations.size else 0
        return Inspection(
            id = dto.id.toString(),
            productName = name,
            date = date,
            status = status,
            violationCount = vCount,
            riskLevel = risk,
            productId = (dto.productId ?: dto.id).toString()
        )
    }

    fun mapToInspection(dto: FullInspectionResponse): Inspection {
        val status = when (dto.compliance?.status?.uppercase()) {
            "PASS", "COMPLIANT" -> ComplianceStatus.COMPLIANT
            "FAIL", "NON_COMPLIANT" -> ComplianceStatus.NON_COMPLIANT
            else -> ComplianceStatus.NEEDS_REVIEW
        }
        val risk = when (dto.risk?.level?.uppercase()) {
            "HIGH" -> RiskLevel.HIGH
            "MEDIUM" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val date = parseDate(dto.createdAt)
        return Inspection(
            id = (dto.inspectionId ?: 1).toString(),
            productName = dto.product?.name ?: "Unknown Commodity",
            date = date,
            status = status,
            violationCount = dto.violations?.size ?: 0,
            riskLevel = risk,
            productId = (dto.product?.id ?: 1).toString()
        )
    }

    fun mapToViolations(dto: FullInspectionResponse): List<Violation> {
        val list = mutableListOf<Violation>()
        val violations = dto.violations ?: emptyList()
        val evidences = dto.evidence ?: dto.evidencesList ?: emptyList()

        violations.forEachIndexed { index, v ->
            val ev = evidences.firstOrNull { it.violationId == v.id } ?: evidences.getOrNull(index)
            val bboxList = ev?.bbox
            val bbox = if (bboxList != null && bboxList.size >= 4) {
                BoundingBox(bboxList[0], bboxList[1], bboxList[2] - bboxList[0], bboxList[3] - bboxList[1])
            } else {
                null
            }
            val mediaUrl = ApiClient.getFullMediaUrl(ev?.imagePath ?: dto.imagePath) ?: ""

            list.add(
                Violation(
                    id = (v.id ?: (index + 1)).toString(),
                    type = v.type ?: v.ruleId ?: "Declaration Violation",
                    detectedText = v.description ?: "Rule Violation",
                    confidence = v.confidence ?: 0.90f,
                    reason = v.description ?: "Legal metrology non-compliance",
                    ruleReference = v.ruleId ?: "Rule 6",
                    imagePath = mediaUrl,
                    boundingBox = bbox
                )
            )
        }
        return list
    }

    fun mapToProduct(dto: FullInspectionResponse): Product {
        val risk = when (dto.risk?.level?.uppercase()) {
            "HIGH" -> RiskLevel.HIGH
            "MEDIUM" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val p = dto.product
        return Product(
            id = (p?.id ?: 1).toString(),
            name = p?.name ?: "Package Item",
            manufacturer = p?.manufacturer ?: "Manufacturer Not Found",
            mrp = p?.mrp ?: "Not Found",
            netQuantity = p?.netQuantity ?: "Not Found",
            consumerCare = "Consumer Care Details Available",
            totalInspections = 1,
            totalViolations = dto.violations?.size ?: 0,
            riskLevel = risk
        )
    }

    private fun uriToFile(context: Context, uri: Uri): File {
        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        
        // If it's a file:// scheme or direct path
        if (uri.scheme == "file" || uri.scheme == null) {
            val sourcePath = uri.path
            if (!sourcePath.isNullOrBlank()) {
                val sourceFile = File(sourcePath)
                if (sourceFile.exists() && sourceFile.length() > 0) {
                    sourceFile.copyTo(file, overwrite = true)
                    return file
                }
            }
        }

        // Otherwise try ContentResolver
        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InspectionRepository", "Failed to copy URI to file: ${e.message}", e)
        }
        return file
    }

    private fun parseDate(dateStr: String?): LocalDateTime {
        if (dateStr == null) return LocalDateTime.now()
        return try {
            LocalDateTime.parse(dateStr.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }
}
