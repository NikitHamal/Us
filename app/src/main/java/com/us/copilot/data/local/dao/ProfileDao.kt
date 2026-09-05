package com.us.copilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.us.copilot.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles WHERE owner = :owner AND isActive = 1 LIMIT 1")
    fun observeActive(owner: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE owner = :owner AND isActive = 1 LIMIT 1")
    suspend fun getActive(owner: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE isActive = 1")
    fun observeAllActive(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE owner = :owner ORDER BY version DESC")
    fun observeHistory(owner: String): Flow<List<ProfileEntity>>

    @Query("SELECT COALESCE(MAX(version), 0) FROM profiles WHERE owner = :owner")
    suspend fun maxVersion(owner: String): Int

    @Insert
    suspend fun insert(entity: ProfileEntity): Long

    @Query("UPDATE profiles SET isActive = 0 WHERE owner = :owner")
    suspend fun deactivateAll(owner: String)

    @Query("UPDATE profiles SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?

    @Transaction
    suspend fun insertNewVersion(entity: ProfileEntity): Long {
        deactivateAll(entity.owner)
        val nextVersion = maxVersion(entity.owner) + 1
        return insert(entity.copy(version = nextVersion, isActive = true))
    }

    @Transaction
    suspend fun restore(id: Long) {
        val target = byId(id) ?: return
        deactivateAll(target.owner)
        activate(id)
    }

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
