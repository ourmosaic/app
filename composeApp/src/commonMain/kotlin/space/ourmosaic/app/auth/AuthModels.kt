package space.ourmosaic.app.auth

import kotlinx.serialization.Serializable
import space.ourmosaic.app.system.SystemResponse

@Serializable
data class LoginRequest(
    val username: String? = null,
    val email: String? = null,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class AuthenticationResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Int,
    val refreshTokenExpiresIn: Int
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class UserMeResponse(
    val id: String,
    val username: String,
    val email: String,
    val createdAt: String,
    val updatedAt: String,
    val isFederated: Boolean = false,
    val domain: String? = null,
    val isSystem: Boolean,
    val system: SystemResponse? = null
)
