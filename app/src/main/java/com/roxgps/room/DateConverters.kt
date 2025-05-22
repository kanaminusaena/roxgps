package com.roxgps.room

import androidx.room.TypeConverter
import java.util.Date

class DateConverters {
    @TypeConverter // Mengubah Long (timestamp) menjadi Date
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter // Mengubah Date menjadi Long (timestamp)
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}