package com.us.copilot.data.local.db

import androidx.room.TypeConverter

/**
 * Room converters. Lists are stored with a unit-separator delimiter so user text containing commas
 * is never split incorrectly. Embeddings are stored as comma-joined floats.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        value.orEmpty().filter { it.isNotBlank() }.joinToString(DELIMITER)

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.split(DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()

    companion object {
        const val DELIMITER = "\u001F"

        fun encodeEmbedding(vector: FloatArray?): String? =
            vector?.joinToString(",") { it.toString() }

        fun decodeEmbedding(value: String?): FloatArray? {
            if (value.isNullOrBlank()) return null
            return value.split(',').mapNotNull { it.toFloatOrNull() }.toFloatArray()
                .takeIf { it.isNotEmpty() }
        }

        fun encodeList(values: List<String>): String =
            values.filter { it.isNotBlank() }.joinToString(DELIMITER)

        fun decodeList(value: String?): List<String> =
            value?.split(DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()
    }
}
