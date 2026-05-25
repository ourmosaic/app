package space.ourmosaic.app.network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import space.ourmosaic.app.auth.AuthService

class NetworkClient(private val authService: AuthService) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(SSE)
    }

    suspend fun authenticatedRequest(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        block: HttpRequestBuilder.() -> Unit = {}
    ): io.ktor.client.statement.HttpResponse {
        val federation = authService.getFederation() ?: throw Exception("No federation")
        val fullUrl = if (url.startsWith("http")) url else "https://$federation$url"
        
        val response = client.request(fullUrl) {
            this.method = method
            val token = authService.getAccessToken()
            if (token != null) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            block()
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            val refreshResult = authService.refreshToken()
            if (refreshResult.isSuccess) {
                return authenticatedRequest(url, method, block)
            }
        }
        
        return response
    }
}
