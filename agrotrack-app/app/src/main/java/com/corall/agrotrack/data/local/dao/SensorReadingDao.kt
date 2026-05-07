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

    // Última lectura por sensor (snapshot del dashboard)
    @Query("""
        SELECT * FROM sensor_readings
        WHERE gatewayId = :gatewayId
        GROUP BY sensorId
        HAVING receivedAt = MAX(receivedAt)
        ORDER BY sensorId
    """)
    fun observeLatestByGateway(gatewayId: Int): Flow<List<SensorReadingEntity>>

    @Query("SELECT * FROM sensor_readings WHERE sensorId = :sensorId ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getHistory(sensorId: Int, limit: Int = 500): List<SensorReadingEntity>

    // Purga lecturas con más de 7 días para controlar el tamaño de la BD
    @Query("DELETE FROM sensor_readings WHERE receivedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
