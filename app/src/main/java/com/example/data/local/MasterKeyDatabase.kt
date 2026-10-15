package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Credential
import com.example.data.model.PendingCredential

@Database(
    entities = [Credential::class, PendingCredential::class],
    version = 3,
    exportSchema = false
)
abstract class MasterKeyDatabase : RoomDatabase() {

    abstract fun credentialDao(): CredentialDao

    companion object {
        @Volatile
        private var INSTANCE: MasterKeyDatabase? = null

        fun getDatabase(context: Context): MasterKeyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MasterKeyDatabase::class.java,
                    "masterkey_vault.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
