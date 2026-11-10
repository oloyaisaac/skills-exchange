package com.example.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/** Type converters for Room to handle complex types that Room doesn't support natively. */
public class TypeConverters {

    @TypeConverter
    public fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    public fun toStringList(value: String): List<String> {
        val type: Type = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, type) ?: emptyList()
    }
}