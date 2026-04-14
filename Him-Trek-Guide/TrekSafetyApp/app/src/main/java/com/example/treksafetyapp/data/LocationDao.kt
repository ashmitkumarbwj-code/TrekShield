package com.example.treksafetyapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(location: LocationEntity)

    @Query("SELECT * FROM location_log ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastKnownLocation(): LocationEntity?

    @Query("SELECT * FROM location_log WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLocations(): List<LocationEntity>

    @Query("UPDATE location_log SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("DELETE FROM location_log")
    suspend fun clearLocations()
}
