package com.sih.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import com.sih.network.dto.FullInspectionResponse
import com.sih.network.dto.InspectionDetailOutDto
import com.sih.network.dto.ProductDto
import com.sih.network.dto.ProductHistoryResponse
import com.sih.network.dto.UserOutDto
import com.sih.model.CaptureSource
import com.sih.model.DraftState
import com.sih.model.EvidenceRecord
import com.sih.model.InspectionDraft
import com.sih.model.InspectionSignOff
import com.sih.model.ViolationEvidence
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val gson = Gson()

    companion object {
        private const val DATABASE_NAME = "lmcs_mobile.db"
        private const val DATABASE_VERSION = 2
        private const val TAG = "LocalDatabase"

        private const val CREATE_EVIDENCE_RECORDS = """
            CREATE TABLE evidence_records (
                evidence_id TEXT PRIMARY KEY,
                inspection_id TEXT NOT NULL,
                officer_id INTEGER,
                original_filename TEXT,
                sha256 TEXT NOT NULL,
                capture_timestamp TEXT NOT NULL,
                device_model TEXT,
                image_width INTEGER,
                image_height INTEGER,
                file_size_bytes INTEGER,
                gps_latitude REAL,
                gps_longitude REAL,
                gps_accuracy REAL,
                capture_source TEXT NOT NULL,
                quality_status TEXT,
                ocr_status TEXT
            )
        """

        private const val CREATE_VIOLATION_EVIDENCE = """
            CREATE TABLE violation_evidence (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                evidence_id TEXT NOT NULL,
                original_hash TEXT NOT NULL,
                crop_hash TEXT,
                bounding_box TEXT,
                ocr_text TEXT,
                normalized_text TEXT,
                rule_id TEXT,
                violation_reason TEXT,
                confidence REAL
            )
        """

        private const val CREATE_INSPECTION_DRAFTS = """
            CREATE TABLE inspection_drafts (
                id TEXT PRIMARY KEY,
                officer_id INTEGER,
                state TEXT NOT NULL,
                json_data TEXT NOT NULL,
                last_updated TEXT NOT NULL
            )
        """

        private const val CREATE_SIGN_OFF = """
            CREATE TABLE inspection_sign_off (
                inspection_id TEXT PRIMARY KEY,
                json_data TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        """

        @Volatile
        private var instance: LocalDatabase? = null

        fun getInstance(context: Context): LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: LocalDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE inspections (
                id INTEGER PRIMARY KEY,
                product_id INTEGER,
                product_name TEXT,
                manufacturer TEXT,
                status TEXT,
                risk_level TEXT,
                violation_count INTEGER,
                confidence REAL,
                created_at TEXT,
                json_data TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE decisions (
                inspection_id INTEGER PRIMARY KEY,
                decision TEXT,
                reason TEXT,
                updated_at TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE officers (
                id INTEGER PRIMARY KEY,
                email TEXT UNIQUE,
                name TEXT,
                role TEXT
            )
            """.trimIndent()
        )

        db.execSQL(CREATE_EVIDENCE_RECORDS)
        db.execSQL(CREATE_VIOLATION_EVIDENCE)
        db.execSQL(CREATE_INSPECTION_DRAFTS)
        db.execSQL(CREATE_SIGN_OFF)

        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Create new tables for 7-system upgrade
            db.execSQL(CREATE_EVIDENCE_RECORDS)
            db.execSQL(CREATE_VIOLATION_EVIDENCE)
            db.execSQL(CREATE_INSPECTION_DRAFTS)
            db.execSQL(CREATE_SIGN_OFF)
        }
    }

    // =========================================================================
    // INSPECTIONS
    // =========================================================================

    fun insertOrUpdateInspection(inspection: FullInspectionResponse): Long {
        val db = writableDatabase
        val id = inspection.inspectionId ?: (System.currentTimeMillis() % 100000).toInt()
        val values = ContentValues().apply {
            put("id", id)
            put("product_id", inspection.product?.id ?: id)
            put("product_name", inspection.product?.name ?: "Scanned Commodity Package")
            put("manufacturer", inspection.product?.manufacturer ?: "Not Detected")
            put("status", inspection.compliance?.status ?: "REVIEW")
            put("risk_level", inspection.risk?.level ?: "LOW")
            put("violation_count", inspection.violations?.size ?: 0)
            put("confidence", inspection.confidence?.overall ?: 0.85f)
            put("created_at", inspection.createdAt ?: LocalDateTime.now().toString())
            put("json_data", gson.toJson(inspection))
        }
        val result = db.insertWithOnConflict("inspections", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.d(TAG, "Saved inspection #$id (${inspection.product?.name}) to local DB (row: $result)")
        return result
    }

    fun getAllInspections(limit: Int = 100): List<FullInspectionResponse> {
        val list = mutableListOf<FullInspectionResponse>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT json_data FROM inspections ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                val json = it.getString(0)
                try {
                    val item = gson.fromJson(json, FullInspectionResponse::class.java)
                    list.add(item)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing inspection JSON", e)
                }
            }
        }
        return list
    }

    fun getInspectionCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM inspections", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun getInspectionDetails(limit: Int = 100): List<InspectionDetailOutDto> {
        val list = mutableListOf<InspectionDetailOutDto>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, product_id, product_name, status, risk_level, violation_count, confidence, created_at FROM inspections ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getInt(0)
                val prodId = it.getInt(1)
                val status = it.getString(3)
                val risk = it.getString(4)
                val conf = it.getFloat(6)
                val created = it.getString(7)

                list.add(
                    InspectionDetailOutDto(
                        id = id,
                        productId = prodId,
                        status = status,
                        complianceStatus = status,
                        riskLevel = risk,
                        overallConfidence = conf,
                        createdAt = created
                    )
                )
            }
        }
        return list
    }

    fun getInspectionById(id: Int): FullInspectionResponse? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT json_data FROM inspections WHERE id = ?", arrayOf(id.toString()))
        cursor.use {
            if (it.moveToNext()) {
                val json = it.getString(0)
                return try {
                    gson.fromJson(json, FullInspectionResponse::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    fun saveDecision(inspectionId: Int, decision: String, reason: String?): Boolean {
        val db = writableDatabase
        val now = LocalDateTime.now().toString()
        val values = ContentValues().apply {
            put("inspection_id", inspectionId)
            put("decision", decision)
            put("reason", reason ?: "")
            put("updated_at", now)
        }
        val res = db.insertWithOnConflict("decisions", null, values, SQLiteDatabase.CONFLICT_REPLACE)

        // Also update inspection JSON with inspector decision
        val existing = getInspectionById(inspectionId)
        if (existing != null) {
            val updated = existing.copy(
                inspector = com.sih.network.dto.InspectorDto(
                    id = 1,
                    decision = decision,
                    reason = reason
                )
            )
            insertOrUpdateInspection(updated)
        }

        return res != -1L
    }

    fun getProductHistory(productId: Int): ProductHistoryResponse {
        val all = getAllInspections(200)
        val matches = all.filter { (it.product?.id ?: it.inspectionId) == productId }
        val prodName = matches.firstOrNull()?.product?.name ?: "Product #$productId"
        val violationsCount = matches.sumOf { it.violations?.size ?: 0 }
        val highRisk = matches.any { it.risk?.level?.uppercase() == "HIGH" }

        return ProductHistoryResponse(
            productId = productId,
            productName = prodName,
            totalInspections = matches.size,
            totalViolations = violationsCount,
            riskLevel = if (highRisk) "HIGH" else "LOW",
            riskReason = if (highRisk) "Multiple non-compliances recorded across scans" else "Standard compliance",
            history = matches
        )
    }

    // =========================================================================
    // OFFICERS / AUTH
    // =========================================================================

    fun authenticateOfficer(email: String): UserOutDto {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, email, name, role FROM officers WHERE email = ? LIMIT 1", arrayOf(email))
        cursor.use {
            if (it.moveToNext()) {
                return UserOutDto(
                    id = it.getInt(0),
                    email = it.getString(1),
                    fullName = it.getString(2),
                    role = it.getString(3)
                )
            }
        }
        // Fallback default officer
        return UserOutDto(
            id = 1,
            email = email,
            fullName = "Inspector Rajesh Sharma",
            role = "Enforcement Officer"
        )
    }

    // =========================================================================
    // SEED DATA
    // =========================================================================

    private fun seedInitialData(db: SQLiteDatabase) {
        // Seed Officers
        db.execSQL("INSERT OR REPLACE INTO officers (id, email, name, role) VALUES (1, 'admin@lmcs.gov.in', 'Senior Inspector Rajesh Sharma', 'Senior Enforcement Officer')")
        db.execSQL("INSERT OR REPLACE INTO officers (id, email, name, role) VALUES (2, 'inspector@lmcs.gov.in', 'Inspector Rajesh Sharma', 'Field Enforcement Officer')")

        // Seed Realistic Past Legal Metrology Inspections
        val sampleInspections = listOf(
            FullInspectionResponse(
                inspectionId = 101,
                product = ProductDto(
                    id = 101,
                    name = "Tata Salt Vacuum Evaporated Iodized 1kg",
                    manufacturer = "Tata Consumer Products Ltd., 1 Bishop Lefroy Road, Kolkata 700020",
                    packer = "Tata Consumer Products Ltd.",
                    netQuantity = "1 kg",
                    mrp = "₹28.00 (incl. of all taxes)"
                ),
                declarations = listOf(
                    com.sih.network.dto.DeclarationDto(1, "commodity", "Tata Salt Vacuum Evaporated Iodized 1kg", 0.95f, null, true),
                    com.sih.network.dto.DeclarationDto(2, "manufacturer", "Tata Consumer Products Ltd., Kolkata 700020", 0.92f, null, true),
                    com.sih.network.dto.DeclarationDto(3, "net_quantity", "1 kg", 0.96f, null, true),
                    com.sih.network.dto.DeclarationDto(4, "mrp", "₹28.00", 0.94f, null, true),
                    com.sih.network.dto.DeclarationDto(5, "date", "08/2026", 0.90f, null, true),
                    com.sih.network.dto.DeclarationDto(6, "consumer_care", "1800 108 4488 / care@tataconsumer.com", 0.91f, null, true)
                ),
                violations = emptyList(),
                risk = com.sih.network.dto.RiskDto("LOW", "Package complies with Legal Metrology (Packaged Commodities) Rules, 2011."),
                confidence = com.sih.network.dto.ConfidenceDto(0.95f, "VERIFIED"),
                compliance = com.sih.network.dto.ComplianceDto("PASS"),
                createdAt = LocalDateTime.now().minusDays(3).toString()
            ),
            FullInspectionResponse(
                inspectionId = 102,
                product = ProductDto(
                    id = 102,
                    name = "Fortune Sunlite Refined Sunflower Oil 1L Pouch",
                    manufacturer = "Adani Wilmar Limited, Fortune House, Near Navrangpura Rly Crossing, Ahmedabad 380009",
                    packer = "Adani Wilmar Limited",
                    netQuantity = "1 L (910 g)",
                    mrp = "₹145.00 (incl. of all taxes)"
                ),
                declarations = listOf(
                    com.sih.network.dto.DeclarationDto(1, "commodity", "Fortune Sunlite Refined Sunflower Oil 1L", 0.90f, null, true),
                    com.sih.network.dto.DeclarationDto(2, "manufacturer", "Adani Wilmar Limited, Ahmedabad", 0.88f, null, true),
                    com.sih.network.dto.DeclarationDto(3, "net_quantity", "1 L", 0.91f, null, true),
                    com.sih.network.dto.DeclarationDto(4, "mrp", "₹145.00", 0.89f, null, true),
                    com.sih.network.dto.DeclarationDto(5, "date", "07/2026", 0.86f, null, true),
                    com.sih.network.dto.DeclarationDto(6, "consumer_care", "Missing Toll Free", 0.40f, null, false)
                ),
                violations = listOf(
                    com.sih.network.dto.ViolationDto(
                        id = 1,
                        ruleId = "Rule 6(1)(g)",
                        type = "Consumer Care Non-Compliance",
                        description = "Mandatory consumer grievance contact phone number missing on principal display panel.",
                        severity = "HIGH",
                        confidence = 0.92f
                    ),
                    com.sih.network.dto.ViolationDto(
                        id = 2,
                        ruleId = "Rule 6(11)",
                        type = "Unit Sale Price Missing",
                        description = "Unit sale price (₹/ml or ₹/g) not displayed alongside retail price.",
                        severity = "MEDIUM",
                        confidence = 0.88f
                    )
                ),
                risk = com.sih.network.dto.RiskDto("HIGH", "2 statutory violations under Legal Metrology Rules detected."),
                confidence = com.sih.network.dto.ConfidenceDto(0.89f, "FLAGGED"),
                compliance = com.sih.network.dto.ComplianceDto("FAIL"),
                createdAt = LocalDateTime.now().minusDays(2).toString()
            ),
            FullInspectionResponse(
                inspectionId = 103,
                product = ProductDto(
                    id = 103,
                    name = "Aashirvaad Superior MP Sharbati Atta 5kg",
                    manufacturer = "ITC Limited, 37 J.L. Nehru Road, Kolkata 700071",
                    packer = "ITC Limited",
                    netQuantity = "5 kg",
                    mrp = "₹320.00"
                ),
                declarations = listOf(
                    com.sih.network.dto.DeclarationDto(1, "commodity", "Aashirvaad Superior MP Sharbati Atta 5kg", 0.96f, null, true),
                    com.sih.network.dto.DeclarationDto(2, "manufacturer", "ITC Limited, Kolkata 700071", 0.94f, null, true),
                    com.sih.network.dto.DeclarationDto(3, "net_quantity", "5 kg", 0.97f, null, true),
                    com.sih.network.dto.DeclarationDto(4, "mrp", "₹320.00", 0.95f, null, true),
                    com.sih.network.dto.DeclarationDto(5, "date", "09/2026", 0.92f, null, true),
                    com.sih.network.dto.DeclarationDto(6, "consumer_care", "1800 425 4444 / itccares@itc.in", 0.94f, null, true)
                ),
                violations = emptyList(),
                risk = com.sih.network.dto.RiskDto("LOW", "Package fully conforms to mandatory Legal Metrology declarations."),
                confidence = com.sih.network.dto.ConfidenceDto(0.96f, "VERIFIED"),
                compliance = com.sih.network.dto.ComplianceDto("PASS"),
                createdAt = LocalDateTime.now().minusDays(1).toString()
            )
        )

        for (insp in sampleInspections) {
            val id = insp.inspectionId ?: 100
            val values = ContentValues().apply {
                put("id", id)
                put("product_id", insp.product?.id ?: id)
                put("product_name", insp.product?.name)
                put("manufacturer", insp.product?.manufacturer)
                put("status", insp.compliance?.status)
                put("risk_level", insp.risk?.level)
                put("violation_count", insp.violations?.size ?: 0)
                put("confidence", insp.confidence?.overall ?: 0.9f)
                put("created_at", insp.createdAt)
                put("json_data", gson.toJson(insp))
            }
            db.insert("inspections", null, values)
        }
    }

    // =========================================================================
    // 7-SYSTEM UPGRADE CRUD
    // =========================================================================

    fun insertEvidenceRecord(record: EvidenceRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("evidence_id", record.evidenceId)
            put("inspection_id", record.inspectionId)
            put("officer_id", record.officerId)
            put("original_filename", record.originalFilename)
            put("sha256", record.sha256)
            put("capture_timestamp", record.captureTimestamp.toString())
            put("device_model", record.deviceModel)
            put("image_width", record.imageWidth)
            put("image_height", record.imageHeight)
            put("file_size_bytes", record.fileSizeBytes)
            put("gps_latitude", record.gpsLatitude)
            put("gps_longitude", record.gpsLongitude)
            put("gps_accuracy", record.gpsAccuracy)
            put("capture_source", record.captureSource.name)
            put("quality_status", record.qualityStatus)
            put("ocr_status", record.ocrStatus)
        }
        return db.insertWithOnConflict("evidence_records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getEvidenceRecords(inspectionId: String): List<EvidenceRecord> {
        val db = readableDatabase
        val list = mutableListOf<EvidenceRecord>()
        val cursor = db.rawQuery("SELECT * FROM evidence_records WHERE inspection_id = ?", arrayOf(inspectionId))
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    EvidenceRecord(
                        evidenceId = it.getString(it.getColumnIndexOrThrow("evidence_id")),
                        inspectionId = it.getString(it.getColumnIndexOrThrow("inspection_id")),
                        officerId = it.getInt(it.getColumnIndexOrThrow("officer_id")),
                        originalFilename = it.getString(it.getColumnIndexOrThrow("original_filename")),
                        sha256 = it.getString(it.getColumnIndexOrThrow("sha256")),
                        captureTimestamp = it.getString(it.getColumnIndexOrThrow("capture_timestamp")),
                        deviceModel = it.getString(it.getColumnIndexOrThrow("device_model")),
                        imageWidth = it.getInt(it.getColumnIndexOrThrow("image_width")),
                        imageHeight = it.getInt(it.getColumnIndexOrThrow("image_height")),
                        fileSizeBytes = it.getLong(it.getColumnIndexOrThrow("file_size_bytes")),
                        gpsLatitude = it.getDouble(it.getColumnIndexOrThrow("gps_latitude")),
                        gpsLongitude = it.getDouble(it.getColumnIndexOrThrow("gps_longitude")),
                        gpsAccuracy = it.getFloat(it.getColumnIndexOrThrow("gps_accuracy")),
                        captureSource = CaptureSource.valueOf(it.getString(it.getColumnIndexOrThrow("capture_source"))),
                        qualityStatus = it.getString(it.getColumnIndexOrThrow("quality_status")),
                        ocrStatus = it.getString(it.getColumnIndexOrThrow("ocr_status"))
                    )
                )
            }
        }
        return list
    }

    fun getEvidenceRecord(evidenceId: String): EvidenceRecord? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM evidence_records WHERE evidence_id = ?", arrayOf(evidenceId))
        cursor.use {
            if (it.moveToNext()) {
                return EvidenceRecord(
                    evidenceId = it.getString(it.getColumnIndexOrThrow("evidence_id")),
                    inspectionId = it.getString(it.getColumnIndexOrThrow("inspection_id")),
                    officerId = it.getInt(it.getColumnIndexOrThrow("officer_id")),
                    originalFilename = it.getString(it.getColumnIndexOrThrow("original_filename")),
                    sha256 = it.getString(it.getColumnIndexOrThrow("sha256")),
                    captureTimestamp = it.getString(it.getColumnIndexOrThrow("capture_timestamp")),
                    deviceModel = it.getString(it.getColumnIndexOrThrow("device_model")),
                    imageWidth = it.getInt(it.getColumnIndexOrThrow("image_width")),
                    imageHeight = it.getInt(it.getColumnIndexOrThrow("image_height")),
                    fileSizeBytes = it.getLong(it.getColumnIndexOrThrow("file_size_bytes")),
                    gpsLatitude = it.getDouble(it.getColumnIndexOrThrow("gps_latitude")),
                    gpsLongitude = it.getDouble(it.getColumnIndexOrThrow("gps_longitude")),
                    gpsAccuracy = it.getFloat(it.getColumnIndexOrThrow("gps_accuracy")),
                    captureSource = CaptureSource.valueOf(it.getString(it.getColumnIndexOrThrow("capture_source"))),
                    qualityStatus = it.getString(it.getColumnIndexOrThrow("quality_status")),
                    ocrStatus = it.getString(it.getColumnIndexOrThrow("ocr_status"))
                )
            }
        }
        return null
    }

    fun updateEvidenceOcrStatus(evidenceId: String, status: String) {
        val db = writableDatabase
        val values = ContentValues().apply { put("ocr_status", status) }
        db.update("evidence_records", values, "evidence_id = ?", arrayOf(evidenceId))
    }

    fun updateEvidenceQualityStatus(evidenceId: String, status: String) {
        val db = writableDatabase
        val values = ContentValues().apply { put("quality_status", status) }
        db.update("evidence_records", values, "evidence_id = ?", arrayOf(evidenceId))
    }

    fun insertViolationEvidence(ve: ViolationEvidence): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("evidence_id", ve.evidenceId)
            put("original_hash", ve.originalHash)
            put("crop_hash", ve.cropHash)
            put("bounding_box", gson.toJson(ve.boundingBox))
            put("ocr_text", ve.ocrText)
            put("normalized_text", ve.normalizedText)
            put("rule_id", ve.ruleId)
            put("violation_reason", ve.violationReason)
            put("confidence", ve.confidence)
        }
        return db.insert("violation_evidence", null, values)
    }

    fun getViolationEvidence(evidenceId: String): List<ViolationEvidence> {
        val db = readableDatabase
        val list = mutableListOf<ViolationEvidence>()
        val cursor = db.rawQuery("SELECT * FROM violation_evidence WHERE evidence_id = ?", arrayOf(evidenceId))
        cursor.use {
            while (it.moveToNext()) {
                val bboxJson = it.getString(it.getColumnIndexOrThrow("bounding_box"))
                val bboxType = object : com.google.gson.reflect.TypeToken<List<Float>>() {}.type
                list.add(
                    ViolationEvidence(
                        evidenceId = it.getString(it.getColumnIndexOrThrow("evidence_id")),
                        originalHash = it.getString(it.getColumnIndexOrThrow("original_hash")),
                        cropHash = it.getString(it.getColumnIndexOrThrow("crop_hash")),
                        boundingBox = if (bboxJson != null) gson.fromJson(bboxJson, bboxType) else null,
                        ocrText = it.getString(it.getColumnIndexOrThrow("ocr_text")),
                        normalizedText = it.getString(it.getColumnIndexOrThrow("normalized_text")),
                        ruleId = it.getString(it.getColumnIndexOrThrow("rule_id")),
                        violationReason = it.getString(it.getColumnIndexOrThrow("violation_reason")),
                        confidence = it.getFloat(it.getColumnIndexOrThrow("confidence"))
                    )
                )
            }
        }
        return list
    }

    fun saveDraft(draft: InspectionDraft): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", draft.inspectionId)
            put("officer_id", draft.officerId)
            put("state", draft.state.name)
            put("json_data", gson.toJson(draft))
            put("last_updated", draft.lastUpdated)
        }
        return db.insertWithOnConflict("inspection_drafts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getDraft(inspectionId: String): InspectionDraft? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT json_data FROM inspection_drafts WHERE id = ?", arrayOf(inspectionId))
        cursor.use {
            if (it.moveToNext()) {
                val json = it.getString(0)
                return try {
                    gson.fromJson(json, InspectionDraft::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    fun getActiveDrafts(): List<InspectionDraft> {
        val db = readableDatabase
        val list = mutableListOf<InspectionDraft>()
        val cursor = db.rawQuery(
            "SELECT json_data FROM inspection_drafts WHERE state NOT IN ('COMPLETED', 'ABANDONED') ORDER BY last_updated DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                try {
                    list.add(gson.fromJson(it.getString(0), InspectionDraft::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing active draft JSON", e)
                }
            }
        }
        return list
    }

    fun deleteDraft(inspectionId: String): Boolean {
        val db = writableDatabase
        return db.delete("inspection_drafts", "id = ?", arrayOf(inspectionId)) > 0
    }

    fun updateDraftState(inspectionId: String, state: DraftState, jsonData: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("state", state.name)
            put("json_data", jsonData)
            put("last_updated", LocalDateTime.now().toString())
        }
        db.update("inspection_drafts", values, "id = ?", arrayOf(inspectionId))
    }

    fun saveSignOff(inspectionId: String, signOff: InspectionSignOff): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("inspection_id", inspectionId)
            put("json_data", gson.toJson(signOff))
            put("created_at", signOff.officerTimestamp)
        }
        return db.insertWithOnConflict("inspection_sign_off", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSignOff(inspectionId: String): InspectionSignOff? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT json_data FROM inspection_sign_off WHERE inspection_id = ?", arrayOf(inspectionId))
        cursor.use {
            if (it.moveToNext()) {
                val json = it.getString(0)
                return try {
                    gson.fromJson(json, InspectionSignOff::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }
}
