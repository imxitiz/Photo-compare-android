package com.example.prismdiff.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.prismdiff.core.model.AppLaunchScreen
import com.example.prismdiff.core.model.AppPreferences
import com.example.prismdiff.core.model.AppThemeMode
import com.example.prismdiff.core.model.CanvasBackdropMode
import com.example.prismdiff.core.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prismDiffDataStore: DataStore<Preferences> by preferencesDataStore(name = "prismdiff_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LAUNCH_SCREEN = stringPreferencesKey("launch_screen")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val CANVAS_BACKDROP = stringPreferencesKey("canvas_backdrop")
        val CUSTOM_BACKDROP_ARGB = longPreferencesKey("custom_backdrop_argb")
        val MASK_GPS = booleanPreferencesKey("mask_gps_by_default")
        val VOLUME_KEY_NAV = booleanPreferencesKey("volume_key_nav")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PHOTOSENSITIVE_GUARD = booleanPreferencesKey("photosensitive_guard")
        val PIXEL_GRID_400 = booleanPreferencesKey("pixel_grid_400")
        val HUD_TEMPLATE = stringPreferencesKey("hud_template")
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val SAVED_SAF_TREE_URI = stringPreferencesKey("saved_saf_tree_uri")
    }

    val preferences: Flow<AppPreferences> = context.prismDiffDataStore.data.map { prefs ->
        AppPreferences(
            launchScreen = enumOrDefault(prefs[Keys.LAUNCH_SCREEN], AppLaunchScreen.STUDIO_HOME),
            themeMode = enumOrDefault(prefs[Keys.THEME_MODE], AppThemeMode.OLED_DARK),
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
            canvasBackdrop = enumOrDefault(prefs[Keys.CANVAS_BACKDROP], CanvasBackdropMode.OLED_BLACK),
            customBackdropArgb = prefs[Keys.CUSTOM_BACKDROP_ARGB] ?: 0xFF1A1D24L,
            maskGpsByDefault = prefs[Keys.MASK_GPS] ?: true,
            volumeKeyNavigation = prefs[Keys.VOLUME_KEY_NAV] ?: false,
            keepScreenOnInStudio = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            photosensitiveGuardEnabled = prefs[Keys.PHOTOSENSITIVE_GUARD] ?: true,
            showPixelGridAbove400 = prefs[Keys.PIXEL_GRID_400] ?: true,
            hudTemplate = prefs[Keys.HUD_TEMPLATE] ?: "{FileName} • {Aperture} {Shutter} ISO{ISO} • {SizeMB}",
            activePresetId = prefs[Keys.ACTIVE_PRESET_ID] ?: "quick_compare",
            defaultSortOrder = enumOrDefault(prefs[Keys.DEFAULT_SORT], SortOrder.EXIF_DATE_DESC),
        )
    }

    val savedFolderTreeUri: Flow<String?> = context.prismDiffDataStore.data.map { it[Keys.SAVED_SAF_TREE_URI] }

    suspend fun setLaunchScreen(screen: AppLaunchScreen) {
        context.prismDiffDataStore.edit { it[Keys.LAUNCH_SCREEN] = screen.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.prismDiffDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.prismDiffDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCanvasBackdrop(mode: CanvasBackdropMode) {
        context.prismDiffDataStore.edit { it[Keys.CANVAS_BACKDROP] = mode.name }
    }

    suspend fun setCustomBackdropArgb(argb: Long) {
        context.prismDiffDataStore.edit { it[Keys.CUSTOM_BACKDROP_ARGB] = argb }
    }

    suspend fun setMaskGps(masked: Boolean) {
        context.prismDiffDataStore.edit { it[Keys.MASK_GPS] = masked }
    }

    suspend fun setVolumeKeyNavigation(enabled: Boolean) {
        context.prismDiffDataStore.edit { it[Keys.VOLUME_KEY_NAV] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.prismDiffDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setPhotosensitiveGuard(enabled: Boolean) {
        context.prismDiffDataStore.edit { it[Keys.PHOTOSENSITIVE_GUARD] = enabled }
    }

    suspend fun setShowPixelGrid(enabled: Boolean) {
        context.prismDiffDataStore.edit { it[Keys.PIXEL_GRID_400] = enabled }
    }

    suspend fun setHudTemplate(template: String) {
        context.prismDiffDataStore.edit { it[Keys.HUD_TEMPLATE] = template }
    }

    suspend fun setActivePresetId(presetId: String) {
        context.prismDiffDataStore.edit { it[Keys.ACTIVE_PRESET_ID] = presetId }
    }

    suspend fun setDefaultSortOrder(sortOrder: SortOrder) {
        context.prismDiffDataStore.edit { it[Keys.DEFAULT_SORT] = sortOrder.name }
    }

    suspend fun setSavedFolderTreeUri(uri: String?) {
        context.prismDiffDataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.SAVED_SAF_TREE_URI)
            else prefs[Keys.SAVED_SAF_TREE_URI] = uri
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
