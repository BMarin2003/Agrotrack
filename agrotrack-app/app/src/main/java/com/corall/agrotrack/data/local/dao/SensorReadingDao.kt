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
        SELECT * FROM sensor_readings
        WHERE gatewayId = :gatewayId
          AND receivedAt = (
              SELECT MAX(s2.receivedAt) FROM sensor_readings s2
              WHERE s2.sensorId = sensor_readings.sensorId
                AND s2.gatewayId = :gatewayId
          )
        ORDER BY receivedAt DESC
    """)
    fun observeLatestByGateway(gatewayId: Int): Flow<List<SensorReadingEntity>>

    @Query("SELECT * FROM sensor_readings WHERE sensorId = :sensorId ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getLatestBySensor(sensorId: Int): SensorReadingEntity?

    @Query("DELETE FROM sensor_readings WHERE receivedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}