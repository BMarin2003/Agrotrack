package com.corall.agrotrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corall.agrotrack.data.local.entity.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<AlertEntity>)

    @Query("SELECT * FROM alerts WHERE resolved = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<AlertEntity>>

    @Query("UPDATE alerts SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: Long)
}
