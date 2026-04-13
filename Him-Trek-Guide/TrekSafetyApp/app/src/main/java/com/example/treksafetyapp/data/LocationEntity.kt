package com.example.treksafetyapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_log")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
