package io.appwrite

import android.content.Context
import com.franmontiel.persistentcookiejar.ClearableCookieJar
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.google.gson.Gson
import io.appwrite.exceptions.AppwriteException
import io.appwrite.extensions.JsonExtensions.fromJson
import io.appwrite.models.Error
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AppwriteClient(
    context: Context,
    var endPoint: String = "https://appwrite.io/v1",
    private var selfSigned: Boolean = false
) : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val job = Job()

    private lateinit var http: OkHttpClient

    private val headers: MutableMap<String, String>
    
    val config: MutableMap<String, String>

    val cookieJar: ClearableCookieJar = PersistentCookieJar(
        SetCookieCache(), 
        SharedPrefsCookiePersistor(context)
    )

    init {
        headers = mutableMapOf(
            "content-type" to "application/json",
            "x-sdk-version" to "appwrite:kotlin:0.0.1",            
            "X-Appwrite-Response-Format" to "0.7.0"
        )
        config = mutableMapOf()
        
        setSelfSigned(selfSigned)
    }

    /// Your project ID
    fun setProject(value: String): AppwriteClient {
        config["project"] = value
        addHeader("X-Appwrite-Project", value)
        return this
    }

    /// Your secret API key
    fun setKey(value: String): AppwriteClient {
        config["key"] = value
        addHeader("X-Appwrite-Key", value)
        return this
    }

    fun setLocale(value: String): AppwriteClient {
        config["locale"] = value
        addHeader("X-Appwrite-Locale", value)
        return this
    }

    fun setMode(value: String): AppwriteClient {
        config["mode"] = value
        addHeader("X-Appwrite-Mode", value)
        return this
    }

    fun setSelfSigned(status: Boolean): AppwriteClient {
        selfSigned = status

        val builder = OkHttpClient()
            .newBuilder()
            .cookieJar(cookieJar)
            .addInterceptor(HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BODY) })

        if (!selfSigned) {
            http = builder.build()
            return this
        }

        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    }
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )
            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })

            http = builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        return this
    }

    fun setEndpoint(endPoint: String): AppwriteClient {
        this.endPoint = endPoint
        return this
    }

    fun addHeader(key: String, value: String): AppwriteClient {
        headers[key] = value
        return this
    }

    @Throws(AppwriteException::class)
    suspend fun call(
        method: String, 
        path: String, 
        headers:  Map<String, String> = mapOf(), 
        params: Map<String, Any?> = mapOf()
    ): Response {
        val requestHeaders = this.headers.toHeaders().newBuilder()
            .addAll(headers.toHeaders())
            .build()

        val httpBuilder = (endPoint + path).toHttpUrl().newBuilder()

        if ("GET" == method) {
            params.forEach {
                when (it.value) {
                    null -> {
                        return@forEach
                    }
                    is List<*> -> {
                        httpBuilder.addQueryParameter(it.key + "[]", it.value.toString())
                    }
                    else -> {
                        httpBuilder.addQueryParameter(it.key, it.value.toString())
                    }
                }
            }
            val request = Request.Builder()
                .url(httpBuilder.build())
                .headers(requestHeaders)
                .get()
                .build()

            return awaitResponse(request)
        }

        val body = if (MultipartBody.FORM.toString() == headers["content-type"]) {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)

            params.forEach {
                when {
                    it.key == "file" -> {
                        val file = it.value as File
                        builder.addFormDataPart(it.key, file.name, file.asRequestBody())
                    }
                    it.value is List<*> -> {
                        val list = it.value as List<*>
                        for (index in list.indices) {
                            builder.addFormDataPart(
                                "${it.key}[]",
                                list[index].toString()
                            )
                        }
                    }
                    else -> {
                        builder.addFormDataPart(it.key, it.value.toString())
                    }
                }
            }
            builder.build()
        } else {
            Gson().toJson(params)
                .toRequestBody("application/json".toMediaType())
        }

        val request = Request.Builder()
            .url(httpBuilder.build())
            .headers(requestHeaders)
            .method(method, body)
            .build()

        return awaitResponse(request)
    }

    @Throws(AppwriteException::class)
    private suspend fun awaitResponse(request: Request) = suspendCancellableCoroutine<Response> {
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (it.isCancelled) {
                    return
                }
                it.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code >= 400) {
                    val err = response.body?.string()?.fromJson(Error::class.java)
                    it.cancel(AppwriteException(err?.message, err?.code, response.toString()))
                }
                it.resume(response)
            }
        })
    }
}