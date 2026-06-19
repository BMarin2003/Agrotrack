package com.corall.agrotrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corall.agrotrack.data.local.entity.SensorReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: SensorReadingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<SensorReadingEntity>)

    @Query("""
        SELECT sr.* FROM sensor_readings sr
        INNER JOIN (
            SELECT sensorId, MAX(rowid) AS maxRowId
            FROM sensor_readings
            WHERE gatewayId = :gatewayId
            GROUP BY sensorId
        ) latest ON sr.rowid = latest.maxRowId
        ORDER BY sr.receivedAt DESC
    """)
    fun observeLatestByGateway(gatewayId: Int): Flow<List<SensorReadingEntity>>

    @Query("SELECT * FROM sensor_readings WHERE sensorId = :sensorId ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getLatestBySensor(sensorId: Int): SensorReadingEntity?

    @Query("DELETE FROM sensor_readings WHERE receivedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}