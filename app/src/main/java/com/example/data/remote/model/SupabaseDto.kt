package com.example.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserKeysDto(
    @SerialName("user_id")
    val userId: String,
    val salt: String,
    val kdf: String = "PBKDF2WithHmacSHA256",
    @SerialName("kdf_params")
    val kdfParams: JsonElement,
    @SerialName("wrapped_dek")
    val wrappedDek: String,
    @SerialName("dek_nonce")
    val dekNonce: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class RemoteCredentialDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    @SerialName("app_id")
    val appId: String? = null,
    @SerialName("encrypted_data")
    val encryptedData: String,
    val nonce: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null
)
