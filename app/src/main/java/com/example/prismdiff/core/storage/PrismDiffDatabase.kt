package com.example.prismdiff.core.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.prismdiff.core.model.ComparisonRegion
import com.example.prismdiff.core.model.CullingDecision
import com.example.prismdiff.core.model.ImageCullingState
import com.example.prismdiff.core.model.NormPoint
import com.example.prismdiff.core.model.RegionShape
import com.example.prismdiff.core.model.WorkspacePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "culling_sessions")
data class CullingSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val assetUrisJson: String,
    val activeSlotAIndex: Int,
    val activeSlotBIndex: Int,
    val comparisonModeName: String,
    val presetId: String,
)

@Entity(tableName = "image_decisions")
data class ImageDecisionEntity(
    @PrimaryKey val assetId: String,
    val decisionName: String,
    val stars: Int,
    val colorLabel: String?,
    val eloRating: Double,
    val comparisonsPlayed: Int,
    val note: String,
    val updatedAtMs: Long,
)

@Entity(tableName = "saved_presets")
data class SavedPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val schemaVersion: Int,
    val jsonPayload: String,
    val updatedAtMs: Long,
)

@Entity(tableName = "saved_regions")
data class SavedRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shapeName: String,
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val pointsJson: String,
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM culling_sessions ORDER BY updatedAtMs DESC")
    fun observeSessions(): Flow<List<CullingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: CullingSessionEntity)

    @Query("DELETE FROM culling_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM image_decisions")
    fun observeAllDecisions(): Flow<List<ImageDecisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDecision(decision: ImageDecisionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDecisions(decisions: List<ImageDecisionEntity>)

    @Query("SELECT * FROM saved_presets ORDER BY updatedAtMs DESC")
    fun observeCustomPresets(): Flow<List<SavedPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreset(preset: SavedPresetEntity)

    @Query("DELETE FROM saved_presets WHERE id = :id")
    suspend fun deletePreset(id: String)

    @Query("SELECT * FROM saved_regions")
    fun observeRegions(): Flow<List<SavedRegionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegion(region: SavedRegionEntity)

    @Query("DELETE FROM saved_regions WHERE id = :id")
    suspend fun deleteRegion(id: String)
}

@Database(
    entities = [
        CullingSessionEntity::class,
        ImageDecisionEntity::class,
        SavedPresetEntity::class,
        SavedRegionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PrismDiffDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao

    companion object {
        @Volatile
        private var INSTANCE: PrismDiffDatabase? = null

        fun getInstance(context: Context): PrismDiffDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrismDiffDatabase::class.java,
                    "prismdiff_studio.db",
                ).fallbackToDestructiveMigration(dropAllTables = false).build().also { INSTANCE = it }
            }
        }
    }
}

class StudioSessionRepository(
    private val dao: StudioDao,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    val sessions: Flow<List<CullingSessionEntity>> = dao.observeSessions()

    val decisionsMap: Flow<Map<String, ImageCullingState>> = dao.observeAllDecisions().map { rows ->
        rows.associate { row ->
            val dec = runCatching { CullingDecision.valueOf(row.decisionName) }
                .getOrDefault(CullingDecision.UNDECIDED)
            row.assetId to ImageCullingState(
                assetId = row.assetId,
                decision = dec,
                stars = row.stars,
                colorLabel = row.colorLabel,
                eloRating = row.eloRating,
                comparisonsPlayed = row.comparisonsPlayed,
                note = row.note,
            )
        }
    }

    val customPresets: Flow<List<WorkspacePreset>> = dao.observeCustomPresets().map { rows ->
        rows.mapNotNull { row ->
            runCatching { json.decodeFromString<WorkspacePreset>(row.jsonPayload) }.getOrNull()
        }
    }

    val regions: Flow<List<ComparisonRegion>> = dao.observeRegions().map { rows ->
        rows.map { r ->
            val shape = runCatching { RegionShape.valueOf(r.shapeName) }.getOrDefault(RegionShape.RECTANGLE)
            val pts = runCatching { json.decodeFromString<List<NormPoint>>(r.pointsJson) }.getOrDefault(emptyList())
            ComparisonRegion(
                id = r.id,
                name = r.name,
                shape = shape,
                normLeft = r.normLeft,
                normTop = r.normTop,
                normRight = r.normRight,
                normBottom = r.normBottom,
                points = pts,
            )
        }
    }

    suspend fun saveSession(
        id: String,
        title: String,
        assetUris: List<String>,
        slotAIndex: Int,
        slotBIndex: Int,
        modeName: String,
        presetId: String,
    ) {
        val now = System.currentTimeMillis()
        dao.upsertSession(
            CullingSessionEntity(
                id = id,
                title = title,
                createdAtMs = now,
                updatedAtMs = now,
                assetUrisJson = json.encodeToString(assetUris),
                activeSlotAIndex = slotAIndex,
                activeSlotBIndex = slotBIndex,
                comparisonModeName = modeName,
                presetId = presetId,
            ),
        )
    }

    suspend fun deleteSession(id: String) = dao.deleteSession(id)

    suspend fun saveDecision(state: ImageCullingState) {
        dao.upsertDecision(
            ImageDecisionEntity(
                assetId = state.assetId,
                decisionName = state.decision.name,
                stars = state.stars,
                colorLabel = state.colorLabel,
                eloRating = state.eloRating,
                comparisonsPlayed = state.comparisonsPlayed,
                note = state.note,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveDecisions(states: List<ImageCullingState>) {
        val now = System.currentTimeMillis()
        dao.upsertDecisions(
            states.map { state ->
                ImageDecisionEntity(
                    assetId = state.assetId,
                    decisionName = state.decision.name,
                    stars = state.stars,
                    colorLabel = state.colorLabel,
                    eloRating = state.eloRating,
                    comparisonsPlayed = state.comparisonsPlayed,
                    note = state.note,
                    updatedAtMs = now,
                )
            },
        )
    }

    suspend fun savePreset(preset: WorkspacePreset) {
        dao.upsertPreset(
            SavedPresetEntity(
                id = preset.id,
                name = preset.name,
                schemaVersion = preset.schemaVersion,
                jsonPayload = json.encodeToString(preset),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deletePreset(id: String) = dao.deletePreset(id)

    suspend fun saveRegion(region: ComparisonRegion) {
        dao.upsertRegion(
            SavedRegionEntity(
                id = region.id,
                name = region.name,
                shapeName = region.shape.name,
                normLeft = region.normLeft,
                normTop = region.normTop,
                normRight = region.normRight,
                normBottom = region.normBottom,
                pointsJson = json.encodeToString(region.points),
            ),
        )
    }

    suspend fun deleteRegion(id: String) = dao.deleteRegion(id)
}
