package com.goforer.phogal.di.module

import android.app.Application
import android.content.Context
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.goforer.phogal.BuildConfig
import com.goforer.phogal.data.datasource.network.NetworkError
import com.goforer.phogal.data.datasource.network.NetworkErrorHandler
import com.goforer.phogal.data.datasource.network.adapter.factory.NullOnEmptyConverterFactory
import com.goforer.phogal.data.datasource.network.api.RestAPI
import com.orhanobut.logger.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val TIMEOUT_READ = 60L
    private const val TIMEOUT_CONNECT = 60L
    private const val TIMEOUT_WRITE = 60L

    @Singleton
    @Provides
    fun provideAppContext(application: Application): Context = application.applicationContext

    /**
     * Configures and provides a [Json] instance for the `retrofit2-kotlinx-serialization-converter`.
     *
     * - `ignoreUnknownKeys = true` ensures the app ignores undefined fields from the backend.
     * - `coerceInputValues = true` coerces invalid/null inputs into declared default values.
     * - `isLenient = true` allows for non-strict JSON (e.g., unquoted keys).
     */
    @Singleton
    @Provides
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Singleton
    @Provides
    fun provideNetworkErrorHandler(json: Json) = NetworkErrorHandler(json)

    @Singleton
    @Provides
    fun providePersistentCookieJar(context: Context) =
        PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(context))

    @Provides
    @Singleton
    fun provideOkHttpClient(
        interceptor: Interceptor,
        cookieJar: PersistentCookieJar
    ): OkHttpClient {
        return OkHttpClient.Builder().apply {
            cookieJar(cookieJar)
            connectTimeout(TIMEOUT_CONNECT, TimeUnit.SECONDS)
            readTimeout(TIMEOUT_READ, TimeUnit.SECONDS)
            writeTimeout(TIMEOUT_WRITE, TimeUnit.SECONDS)

            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor { message ->
                    if (isJSONValid(message)) {
                        Logger.json(message)
                    } else {
                        Timber.d(message)
                    }
                }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(loggingInterceptor)
            }

            addInterceptor(interceptor)
        }.build()
    }

    @Provides
    @Singleton
    fun provideRequestInterceptor(
        json: Json
    ): Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        // Append standard headers and Auth to every outgoing request.
        val authenticatedRequest = originalRequest.newBuilder().apply {
            header("Accept", "application/json")
            header("Accept-Version", "v1")
            header("Authorization", "Client-ID ${BuildConfig.clientId}")
            header("mobileplatform", "android")
            header("versioncode", "${BuildConfig.VERSION_CODE}")
        }.build()

        val response = chain.proceed(authenticatedRequest)
        val body = response.body
        val bodyContentType = body.contentType()
        var bodyString = body.string()

        if (!response.isSuccessful) {
            try {
                when (response.code) {
                    NetworkError.ERROR_SERVICE_UNPROCESSABLE_ENTITY -> {
                        val networkError = json.decodeFromString<NetworkError>(bodyString)
                        // Prepend the URL to the error message for debugging purposes.
                        networkError.detail.firstOrNull()?.let { errorBody ->
                            errorBody.msg = "${originalRequest.url.encodedPath}\n${errorBody.msg}"
                            bodyString = json.encodeToString(networkError)
                        }
                    }
                    NetworkError.ERROR_SERVICE_BAD_GATEWAY, NetworkError.ERROR_SERVICE_UNAVAILABLE -> {
                        Timber.w("Service unavailable or bad gateway: ${response.code}")
                    }
                    else -> Timber.d("Unhandled HTTP error: ${response.code}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to process error response body")
            }
        }

        // Re-wrap the body because body.string() consumes the stream.
        response.newBuilder()
            .body(bodyString.toByteArray().toResponseBody(bodyContentType))
            .build()
    }

    /**
     * Builds the Retrofit instance that drives [RestAPI].
     */
    @Singleton
    @Provides
    fun provideRestAPI(json: Json, okHttpClient: OkHttpClient): RestAPI {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.apiServer)
            .addConverterFactory(NullOnEmptyConverterFactory())
            .addConverterFactory(json.asConverterFactory(contentType))
            .client(okHttpClient)
            .build()
            .create(RestAPI::class.java)
    }

    private fun isJSONValid(json: String): Boolean {
        return try {
            JSONObject(json)
            true
        } catch (_: JSONException) {
            try {
                JSONArray(json)
                true
            } catch (_: JSONException) {
                false
            }
        }
    }
}