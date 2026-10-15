package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Credential
import com.example.data.model.PendingCredential
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {

    @Query("SELECT * FROM credentials WHERE is_deleted = 0 ORDER BY updated_at DESC")
    fun getAllCredentials(): Flow<List<Credential>>

    @Query("SELECT * FROM credentials WHERE is_deleted = 0")
    suspend fun getAllActiveCredentialsList(): List<Credential>

    @Query("SELECT * FROM credentials")
    suspend fun getAllCredentialsList(): List<Credential>

    @Query("SELECT * FROM credentials WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getCredentialById(id: Long): Credential?

    @Query("SELECT * FROM credentials WHERE sync_id = :syncId LIMIT 1")
    suspend fun getCredentialBySyncId(syncId: String): Credential?

    @Query("SELECT * FROM credentials WHERE is_pending_sync = 1")
    suspend fun getPendingSyncCredentials(): List<Credential>

    @Query("SELECT * FROM credentials WHERE (title LIKE '%' || :query || '%' OR app_id LIKE '%' || :query || '%') AND is_deleted = 0 ORDER BY title ASC")
    suspend fun searchCredentialsByTitleOrAppId(query: String): List<Credential>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: Credential): Long

    @Update
    suspend fun updateCredential(credential: Credential)

    @Query("UPDATE credentials SET is_deleted = 1, is_pending_sync = 1, updated_at = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteCredentialById(id: Long)

    @Query("DELETE FROM credentials WHERE sync_id = :syncId")
    suspend fun deleteCredentialBySyncId(syncId: String)

    @Query("DELETE FROM credentials")
    suspend fun deleteAllCredentials()

    // --- Pending Autofill Saves Queue ---

    @Query("SELECT * FROM pending_credentials ORDER BY created_at DESC")
    fun getPendingCredentialsStream(): Flow<List<PendingCredential>>

    @Query("SELECT * FROM pending_credentials ORDER BY created_at DESC")
    suspend fun getPendingCredentialsList(): List<PendingCredential>

    @Query("SELECT * FROM pending_credentials WHERE id = :id LIMIT 1")
    suspend fun getPendingCredentialById(id: Long): PendingCredential?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingCredential(pending: PendingCredential): Long

    @Query("DELETE FROM pending_credentials WHERE id = :id")
    suspend fun deletePendingCredentialById(id: Long)

    @Query("DELETE FROM pending_credentials")
    suspend fun deleteAllPendingCredentials()
}
