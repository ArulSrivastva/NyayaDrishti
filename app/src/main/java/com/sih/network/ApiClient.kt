package com.sih.network

import android.content.Context
import com.sih.network.local.LocalBackendInterceptor
import com.sih.network.local.LocalBackendServer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl = "http://localhost:8080/"
    private var tokenManager: TokenManager? = null
    private var apiService: ApiService? = null
    private var appContext: Context? = null

    fun init(context: Context, customBaseUrl: String? = null) {
        appContext = context.applicationContext
        LocalBackendServer.start(context.applicationContext)

        if (customBaseUrl != null && customBaseUrl.isNotBlank()) {
            baseUrl = if (customBaseUrl.endsWith("/")) customBaseUrl else "$customBaseUrl/"
        }
        tokenManager = TokenManager(context.applicationContext)
        buildService()
    }

    fun setBaseUrl(newUrl: String) {
        if (newUrl.isNotBlank()) {
            baseUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
            buildService()
        }
    }

    private var offlineMode: Boolean = true

    fun setOfflineMode(enabled: Boolean) {
        offlineMode = enabled
    }

    fun isOfflineMode(): Boolean = offlineMode

    fun getBaseUrl(): String = baseUrl

    fun getTokenManager(): TokenManager? = tokenManager

    fun getService(): ApiService {
        return apiService ?: throw IllegalStateException("ApiClient not initialized. Call ApiClient.init(context) first.")
    }

    fun getFullMediaUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("file://") || path.startsWith("/")) {
            return if (path.startsWith("file://")) path else "file://$path"
        }
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$baseUrl$cleanPath"
    }

    private fun buildService() {
        val tm = tokenManager ?: throw IllegalStateException("TokenManager is null")
        val ctx = appContext ?: throw IllegalStateException("Context is null")

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tm))
            .addInterceptor(LocalBackendInterceptor(ctx))
            .addInterceptor(logging)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}
