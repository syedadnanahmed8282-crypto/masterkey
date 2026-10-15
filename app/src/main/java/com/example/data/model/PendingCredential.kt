package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for credentials captured during Android Autofill [onSaveRequest].
 *
 * Design Rationale:
 * Android background services cannot display interactive BiometricPrompts. To safely
 * persist new logins captured by the Autofill service without storing plaintext passwords
 * on disk, passwords in this pending queue are encrypted using an asymmetric Keystore key
 * (where the public encryption key operates without user auth). When the user opens
 * MasterKey and authenticates, the password is decrypted, moved to the primary vault
 * (encrypted with the primary hardware-backed AES-256-GCM key), and purged from this queue.
 */
@Entity(tableName = "pending_credentials")
data class PendingCredential(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "app_id")
    val appId: String? = null,
    val username: String,
    @ColumnInfo(name = "encrypted_password", typeAffinity = ColumnInfo.BLOB)
    val encryptedPassword: ByteArray,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PendingCredential

        if (id != other.id) return false
        if (title != other.title) return false
        if (appId != other.appId) return false
        if (username != other.username) return false
        if (!encryptedPassword.contentEquals(other.encryptedPassword)) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (appId?.hashCode() ?: 0)
        result = 31 * result + username.hashCode()
        result = 31 * result + encryptedPassword.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
