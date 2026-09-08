package com.sih.network.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sih.network.dto.DecisionRequestDto
import com.sih.network.dto.LoginRequestDto
import com.sih.repository.InspectionRepository
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

class LocalBackendInterceptor(private val context: Context) : Interceptor {

    private val gson = Gson()
    private val TAG = "LocalBackendInterceptor"

    init {
        LocalBackendServer.start(context)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimStart('/')
        val method = request.method
        val host = request.url.host

        // Check if online mode is active and target is not localhost
        val isRemoteTarget = host != "localhost" && host != "127.0.0.1" && !com.sih.network.ApiClient.isOfflineMode()
        if (isRemoteTarget) {
            try {
                val remoteResp = chain.proceed(request)
                if (remoteResp.isSuccessful || remoteResp.code == 401 || remoteResp.code == 403 || remoteResp.code == 422) {
                    return remoteResp
                }
                Log.w(TAG, "Remote server returned ${remoteResp.code}, falling back to on-device autonomous mode")
            } catch (e: Exception) {
                Log.w(TAG, "Remote server unreachable (${e.message}), falling back to on-device autonomous mode")
            }
        }

        Log.d(TAG, "Intercepting API request on-device: $method /$path")

        return try {
            handleLocalRequest(request, path, method)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling local request for /$path", e)
            createJsonResponse(request, 500, "{\"error\": \"Local processing failed: ${e.message}\"}")
        }
    }

    private fun handleLocalRequest(request: okhttp3.Request, path: String, method: String): Response {
        // 1. AUTH: LOGIN
        if (path.endsWith("auth/login") && method == "POST") {
            val email = try {
                val bodyStr = readRequestBody(request)
                val reqDto = gson.fromJson(bodyStr, LoginRequestDto::class.java)
                reqDto.email
            } catch (e: Exception) {
                ""
            }
            val tokenDto = LocalBackendServer.login(email)
            return if (tokenDto != null) {
                createJsonResponse(request, 200, gson.toJson(tokenDto))
            } else {
                createJsonResponse(request, 401, "{\"error\": \"Invalid officer credentials\"}")
            }
        }

        // 2. AUTH: ME
        if (path.endsWith("auth/me") && method == "GET") {
            val tokenManager = com.sih.network.TokenManager(context)
            val currentEmail = tokenManager.getUserEmail()
            val user = LocalBackendServer.getDatabase().authenticateOfficer(currentEmail)
                ?: com.sih.network.dto.UserOutDto(
                    id = tokenManager.getUserId(),
                    email = currentEmail,
                    fullName = tokenManager.getUserName(),
                    role = tokenManager.getUserRole()
                )
            return createJsonResponse(request, 200, gson.toJson(user))
        }

        // 3. INSPECTIONS: SUBMIT DECISION
        // e.g. "api/inspections/101/decision" or "inspections/101/decision"
        val decisionRegex = Regex(".*/?inspections/(\\d+)/decision$")
        val decisionMatch = decisionRegex.matchEntire(path)
        if (decisionMatch != null && method == "POST") {
            val id = decisionMatch.groupValues[1].toInt()
            val bodyStr = readRequestBody(request)
            val decisionDto = try {
                gson.fromJson(bodyStr, DecisionRequestDto::class.java)
            } catch (e: Exception) {
                DecisionRequestDto("COMPLIANT", null)
            }
            val updated = LocalBackendServer.submitDecision(id, decisionDto.decision, decisionDto.reason)
            return createJsonResponse(request, 200, gson.toJson(updated))
        }

        // 4. INSPECTIONS: GET FULL DETAIL
        // e.g. "api/inspections/101/full" or "inspections/101/full"
        val fullRegex = Regex(".*/?inspections/(\\d+)/full$")
        val fullMatch = fullRegex.matchEntire(path)
        if (fullMatch != null && method == "GET") {
            val id = fullMatch.groupValues[1].toInt()
            val full = LocalBackendServer.getInspectionFull(id)
            return if (full != null) {
                createJsonResponse(request, 200, gson.toJson(full))
            } else {
                createJsonResponse(request, 404, "{\"error\": \"Inspection not found\"}")
            }
        }

        // 5. INSPECTIONS: GET SUMMARY DETAIL
        // e.g. "api/inspections/101"
        val singleRegex = Regex(".*/?inspections/(\\d+)$")
        val singleMatch = singleRegex.matchEntire(path)
        if (singleMatch != null && method == "GET") {
            val id = singleMatch.groupValues[1].toInt()
            val detail = LocalBackendServer.getInspection(id)
            return if (detail != null) {
                createJsonResponse(request, 200, gson.toJson(detail))
            } else {
                createJsonResponse(request, 404, "{\"error\": \"Inspection not found\"}")
            }
        }

        // 6. INSPECTIONS: LIST
        if ((path == "api/inspections" || path == "inspections") && method == "GET") {
            val list = LocalBackendServer.listInspections()
            return createJsonResponse(request, 200, gson.toJson(list))
        }

        // 7. INSPECT: RUN INSPECTION
        if ((path.endsWith("inspect")) && method == "POST") {
            val current = InspectionRepository.currentInspection
            return if (current != null) {
                LocalBackendServer.saveInspection(current)
                createJsonResponse(request, 200, gson.toJson(current))
            } else {
                createJsonResponse(request, 200, "{}")
            }
        }

        // 8. PRODUCTS: LIST
        if ((path == "api/products" || path == "products") && method == "GET") {
            val products = LocalBackendServer.listProducts()
            return createJsonResponse(request, 200, gson.toJson(products))
        }

        // 9. PRODUCTS: HISTORY
        // e.g. "api/products/101/history"
        val prodHistoryRegex = Regex(".*/?products/(\\d+)/history$")
        val prodMatch = prodHistoryRegex.matchEntire(path)
        if (prodMatch != null && method == "GET") {
            val id = prodMatch.groupValues[1].toInt()
            val history = LocalBackendServer.getProductHistory(id)
            return createJsonResponse(request, 200, gson.toJson(history))
        }

        // 10. REPORTS: PDF STREAM
        val reportPdfRegex = Regex(".*/?reports/(\\d+)$")
        val reportPdfMatch = reportPdfRegex.matchEntire(path)
        if (reportPdfMatch != null && method == "GET") {
            val id = reportPdfMatch.groupValues[1].toInt()
            val pdfBytes = LocalBackendServer.generateReportPdfBytes(id)
            return if (pdfBytes != null) {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pdfBytes.toResponseBody("application/pdf".toMediaTypeOrNull()))
                    .build()
            } else {
                createJsonResponse(request, 404, "{\"error\": \"Report generation failed\"}")
            }
        }

        // Fallback for any other API route
        return createJsonResponse(request, 200, "{}")
    }

    private fun createJsonResponse(request: okhttp3.Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(json.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
    }

    private fun readRequestBody(request: okhttp3.Request): String {
        val copy = request.newBuilder().build()
        val buffer = Buffer()
        copy.body?.writeTo(buffer)
        return buffer.readUtf8()
    }
}
