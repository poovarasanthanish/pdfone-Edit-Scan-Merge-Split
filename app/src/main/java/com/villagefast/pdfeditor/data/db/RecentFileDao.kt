package com.villagefast.pdfeditor.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastAccessed DESC LIMIT 10")
    fun getRecentFiles(): Flow<List<RecentFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recentFile: RecentFile)

    @Delete
    suspend fun delete(recentFile: RecentFile)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}
