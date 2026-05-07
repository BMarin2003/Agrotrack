package com.corall.agrotrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.corall.agrotrack.data.local.dao.AlertDao
import com.corall.agrotrack.data.local.dao.SensorReadingDao
import com.corall.agrotrack.data.local.entity.AlertEntity
import com.corall.agrotrack.data.local.entity.SensorReadingEntity

@Database(
    entities  = [SensorReadingEntity::class, AlertEntity::class],
    version   = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sensorReadingDao(): SensorReadingDao
    abstract fun alertDao(): AlertDao
}
