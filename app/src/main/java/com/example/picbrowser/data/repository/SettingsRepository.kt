package com.example.picbrowser.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.picbrowser.data.model.CustomDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {

    private val favoritesKey = stringPreferencesKey("favorite_image_ids")
    private val customDirectoriesKey = stringPreferencesKey("custom_directories")
    private val portraitColumnsKey = stringPreferencesKey("portrait_columns")
    private val landscapeColumnsKey = stringPreferencesKey("landscape_columns")

    // 收藏夹相关（保持原有功能）
    val favoriteIds: Flow<Set<Long>> = context.dataStore.data
        .map { preferences ->
            preferences[favoritesKey]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    // 自定义目录相关
    val customDirectories: Flow<List<CustomDirectory>> = context.dataStore.data
        .map { preferences ->
            parseCustomDirectories(preferences[customDirectoriesKey])
        }

    private fun parseCustomDirectories(jsonString: String?): List<CustomDirectory> {
        if (jsonString.isNullOrEmpty()) return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            (0 until jsonArray.length()).map { i ->
                val json = jsonArray.getJSONObject(i)
                CustomDirectory(
                    id = json.getLong("id"),
                    path = json.getString("path"),
                    name = json.getString("name"),
                    coverUri = json.optString("coverUri")?.takeIf { it.isNotEmpty() }?.let { android.net.Uri.parse(it) },
                    imageCount = json.optInt("imageCount", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeCustomDirectories(directories: List<CustomDirectory>): String {
        val jsonArray = JSONArray()
        directories.forEach { dir ->
            val json = JSONObject().apply {
                put("id", dir.id)
                put("path", dir.path)
                put("name", dir.name)
                put("coverUri", dir.coverUri?.toString() ?: "")
                put("imageCount", dir.imageCount)
            }
            jsonArray.put(json)
        }
        return jsonArray.toString()
    }

    suspend fun addCustomDirectory(directory: CustomDirectory) {
        context.dataStore.edit { preferences ->
            val current = parseCustomDirectories(preferences[customDirectoriesKey]).toMutableList()
            if (current.none { it.path == directory.path }) {
                current.add(directory)
                preferences[customDirectoriesKey] = serializeCustomDirectories(current)
            }
        }
    }

    suspend fun removeCustomDirectory(directoryId: Long) {
        context.dataStore.edit { preferences ->
            val current = parseCustomDirectories(preferences[customDirectoriesKey])
                .filterNot { it.id == directoryId }
            preferences[customDirectoriesKey] = serializeCustomDirectories(current)
        }
    }

    suspend fun updateCustomDirectory(directory: CustomDirectory) {
        context.dataStore.edit { preferences ->
            val current = parseCustomDirectories(preferences[customDirectoriesKey]).toMutableList()
            val index = current.indexOfFirst { it.id == directory.id }
            if (index >= 0) {
                current[index] = directory
                preferences[customDirectoriesKey] = serializeCustomDirectories(current)
            }
        }
    }

    // 收藏夹方法（保持原有功能）
    suspend fun toggleFavorite(imageId: Long) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[favoritesKey]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()

            if (currentFavorites.contains(imageId)) {
                currentFavorites.remove(imageId)
            } else {
                currentFavorites.add(imageId)
            }

            preferences[favoritesKey] = currentFavorites.joinToString(",")
        }
    }

    suspend fun addFavorite(imageId: Long) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[favoritesKey]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()

            currentFavorites.add(imageId)
            preferences[favoritesKey] = currentFavorites.joinToString(",")
        }
    }

    suspend fun removeFavorite(imageId: Long) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[favoritesKey]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()

            currentFavorites.remove(imageId)
            preferences[favoritesKey] = currentFavorites.joinToString(",")
        }
    }

    fun isFavoriteFlow(imageId: Long): Flow<Boolean> {
        return favoriteIds.map { it.contains(imageId) }
    }

    fun getFavoriteIdsSync(): Set<Long> {
        return runBlocking {
            context.dataStore.data
                .first()
                .let { preferences ->
                    preferences[favoritesKey]
                        ?.split(",")
                        ?.mapNotNull { it.toLongOrNull() }
                        ?.toSet()
                        ?: emptySet()
                }
        }
    }

    // 列数设置相关
    suspend fun getPortraitColumns(): Int {
        return context.dataStore.data.first()[portraitColumnsKey]?.toIntOrNull() ?: 3
    }

    suspend fun getLandscapeColumns(): Int {
        return context.dataStore.data.first()[landscapeColumnsKey]?.toIntOrNull() ?: 5
    }

    suspend fun savePortraitColumns(columns: Int) {
        context.dataStore.edit { preferences ->
            preferences[portraitColumnsKey] = columns.toString()
        }
    }

    suspend fun saveLandscapeColumns(columns: Int) {
        context.dataStore.edit { preferences ->
            preferences[landscapeColumnsKey] = columns.toString()
        }
    }
}