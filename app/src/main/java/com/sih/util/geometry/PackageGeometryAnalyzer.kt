package com.sih.util.geometry

import android.graphics.Bitmap
import com.sih.model.DeclarationCandidate
import com.sih.model.EvidenceRecord
import com.sih.model.PackageGeometryResult
import com.sih.model.PackageType
import com.sih.model.PdpEstimate

object PackageGeometryAnalyzer {

    fun classifyPackageType(bitmap: Bitmap): PackageGeometryResult {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val ratio = width / height

        val (type, confidence, multiImage) = when {
            ratio in 0.8f..1.2f -> Triple(PackageType.BOX_CARTON, 0.75f, false)
            ratio > 1.2f -> Triple(PackageType.POUCH, 0.70f, false)
            ratio < 0.5f -> Triple(PackageType.BOTTLE, 0.65f, true) // Includes CAN
            ratio > 2.0f -> Triple(PackageType.BLISTER_PACK, 0.60f, false)
            else -> Triple(PackageType.OTHER, 0.50f, false)
        }
        
        val warnings = if (multiImage) listOf("Multiple images required for full surface coverage (curved package)") else emptyList()
        val pdpEst = estimatePdpRegion(bitmap, type)

        return PackageGeometryResult(
            packageType = type,
            confidence = confidence,
            estimatedPdpBounds = pdpEst.normalizedBounds,
            pdpConfidence = pdpEst.confidence,
            pdpEstimate = pdpEst,
            surfaceAssociations = emptyMap(),
            isMultiImageRequired = multiImage,
            warnings = warnings
        )
    }

    fun estimatePdpRegion(bitmap: Bitmap, packageType: PackageType): PdpEstimate {
        val (bounds, confidence) = when (packageType) {
            PackageType.BOX_CARTON -> listOf(0.05f, 0.05f, 0.90f, 0.90f) to 0.80f
            PackageType.POUCH -> listOf(0.10f, 0.10f, 0.80f, 0.80f) to 0.70f
            PackageType.BOTTLE, PackageType.CAN, PackageType.CYLINDER -> listOf(0.15f, 0.10f, 0.70f, 0.80f) to 0.55f
            PackageType.BLISTER_PACK -> listOf(0.10f, 0.15f, 0.80f, 0.70f) to 0.50f
            else -> listOf(0.10f, 0.10f, 0.90f, 0.90f) to 0.50f
        }

        return PdpEstimate(
            normalizedBounds = bounds,
            confidence = confidence,
            isAiEstimated = true,
            disclaimer = "AI-estimated PDP region; physical measurement required for definitive verification."
        )
    }

    fun associateDeclarationsToSurfaces(evidenceRecords: List<EvidenceRecord>, declarations: List<DeclarationCandidate>): Map<String, String> {
        val mapping = mutableMapOf<String, String>()
        
        if (evidenceRecords.size <= 1) {
            for (dec in declarations) {
                dec.sourceEvidenceId?.let { mapping[it] = "FRONT" }
            }
        } else {
            val evidenceIds = evidenceRecords.map { it.evidenceId }
            val first = evidenceIds.first()
            val last = evidenceIds.last()
            
            for (dec in declarations) {
                val eid = dec.sourceEvidenceId
                if (eid != null) {
                    when (eid) {
                        first -> mapping[eid] = "FRONT"
                        last -> mapping[eid] = "BACK"
                        else -> mapping[eid] = "SIDE"
                    }
                }
            }
        }
        
        return mapping
    }
}
