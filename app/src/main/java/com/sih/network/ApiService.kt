package com.sih.network

import com.sih.network.dto.DecisionRequestDto
import com.sih.network.dto.FullInspectionResponse
import com.sih.network.dto.InspectionDetailOutDto
import com.sih.network.dto.LoginRequestDto
import com.sih.network.dto.ProductDto
import com.sih.network.dto.ProductHistoryResponse
import com.sih.network.dto.RegisterRequestDto
import com.sih.network.dto.ReportResponseDto
import com.sih.network.dto.TokenResponseDto
import com.sih.network.dto.UserOutDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<TokenResponseDto>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<UserOutDto>

    @GET("auth/me")
    suspend fun getMe(): Response<UserOutDto>

    @Multipart
    @POST("inspect")
    suspend fun inspect(
        @Part file: MultipartBody.Part,
        @Part("product_name") productName: RequestBody? = null,
        @Part("ocr_engine") ocrEngine: RequestBody? = null
    ): Response<FullInspectionResponse>

    @GET("inspections")
    suspend fun listInspections(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100
    ): Response<List<InspectionDetailOutDto>>

    @GET("inspections/{id}")
    suspend fun getInspection(@Path("id") id: Int): Response<InspectionDetailOutDto>

    @GET("inspections/{id}/full")
    suspend fun getInspectionFull(@Path("id") id: Int): Response<FullInspectionResponse>

    @POST("inspections/{id}/decision")
    suspend fun submitDecision(
        @Path("id") id: Int,
        @Body body: DecisionRequestDto
    ): Response<FullInspectionResponse>

    @GET("products")
    suspend fun listProducts(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100
    ): Response<List<ProductDto>>

    @GET("products/{id}/history")
    suspend fun getProductHistory(@Path("id") id: Int): Response<ProductHistoryResponse>

    @POST("reports/{id}")
    suspend fun createReport(@Path("id") id: Int): Response<ReportResponseDto>

    @GET("reports/{id}")
    suspend fun getReportPdf(@Path("id") id: Int): Response<ResponseBody>

    @GET("reports/file/{filename}")
    suspend fun downloadReportFile(@Path("filename") filename: String): Response<ResponseBody>
}
