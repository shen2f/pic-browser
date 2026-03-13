package com.example.picbrowser.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {

    private val favoritesKey = stringPreferencesKey("favorite_image_ids")

    val favoriteIds: Flow<Set<Long>> = context.dataStore.data
        .map { preferences ->
            preferences[favoritesKey]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
        }

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
}
