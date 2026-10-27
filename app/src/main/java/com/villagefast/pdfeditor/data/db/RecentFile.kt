package com.villagefast.pdfeditor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey val path: String,
    val name: String,
    val lastAccessed: Long = System.currentTimeMillis()
)
