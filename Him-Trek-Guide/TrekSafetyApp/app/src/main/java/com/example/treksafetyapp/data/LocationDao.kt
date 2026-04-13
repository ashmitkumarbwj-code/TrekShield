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

    @Query("DELETE FROM location_log")
    suspend fun clearLocations()
}
