package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity for persisted credentials.
 * Sensitive data (username, password, notes) is encrypted into a single JSON blob
 * stored in [encryptedData] using AES-256-GCM with a fresh random [nonce].
 *
 * In Phase 3, [syncId] corresponds to the remote Supabase UUID primary key,
 * [serverUpdatedAt] is used for last-write-wins sync, and [isPendingSync] tracks offline mutations.
 */
@Entity(tableName = "credentials")
data class Credential(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id")
    val userId: String? = null,
    val title: String,
    @ColumnInfo(name = "app_id")
    val appId: String? = null,
    @ColumnInfo(name = "encrypted_data", typeAffinity = ColumnInfo.BLOB)
    val encryptedData: ByteArray,
    @ColumnInfo(name = "nonce", typeAffinity = ColumnInfo.BLOB)
    val nonce: ByteArray,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: String? = null,
    @ColumnInfo(name = "is_pending_sync")
    val isPendingSync: Boolean = false,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "encryption_version")
    val encryptionVersion: Int = 2 // 1 = Phase 1 local Keystore, 2 = Phase 3 DEK
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Credential

        if (id != other.id) return false
        if (syncId != other.syncId) return false
        if (userId != other.userId) return false
        if (title != other.title) return false
        if (appId != other.appId) return false
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (serverUpdatedAt != other.serverUpdatedAt) return false
        if (isPendingSync != other.isPendingSync) return false
        if (isDeleted != other.isDeleted) return false
        if (encryptionVersion != other.encryptionVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + syncId.hashCode()
        result = 31 * result + (userId?.hashCode() ?: 0)
        result = 31 * result + title.hashCode()
        result = 31 * result + (appId?.hashCode() ?: 0)
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (serverUpdatedAt?.hashCode() ?: 0)
        result = 31 * result + isPendingSync.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + encryptionVersion.hashCode()
        return result
    }
}

/**
 * In-memory decrypted representation used by screens and ViewModels.
 */
data class DecryptedCredential(
    val id: Long = 0,
    val syncId: String = "",
    val title: String,
    val appId: String? = null,
    val username: String,
    val password: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPendingSync: Boolean = false
)

/**
 * Secret payload encrypted together as one JSON blob.
 */
data class SecretPayload(
    val username: String,
    val password: String,
    val notes: String
)
