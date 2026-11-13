package com.example.prismdiff.feature.studio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismdiff.core.decoder.CalibrationSampleGenerator
import com.example.prismdiff.core.decoder.DecodeOutcome
import com.example.prismdiff.core.decoder.ExifDiffEngine
import com.example.prismdiff.core.decoder.TilePyramidDecoder
import com.example.prismdiff.core.model.AlignmentResult
import com.example.prismdiff.core.model.AnnotationItem
import com.example.prismdiff.core.model.AppLaunchScreen
import com.example.prismdiff.core.model.AppPreferences
import com.example.prismdiff.core.model.AppThemeMode
import com.example.prismdiff.core.model.BlendOperator
import com.example.prismdiff.core.model.BurstCluster
import com.example.prismdiff.core.model.CanvasBackdropMode
import com.example.prismdiff.core.model.ColorChannelIsolation
import com.example.prismdiff.core.model.ComparisonMetricsBundle
import com.example.prismdiff.core.model.ComparisonMode
import com.example.prismdiff.core.model.ComparisonRegion
import com.example.prismdiff.core.model.CullingDecision
import com.example.prismdiff.core.model.CustomShaderDefinition
import com.example.prismdiff.core.model.DiagnosticOverlay
import com.example.prismdiff.core.model.DiffPalette
import com.example.prismdiff.core.model.DifferenceVisualMode
import com.example.prismdiff.core.model.ExifDiffEntry
import com.example.prismdiff.core.model.HistogramData
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.core.model.ImageCullingState
import com.example.prismdiff.core.model.LayerSlotConfig
import com.example.prismdiff.core.model.LoupeState
import com.example.prismdiff.core.model.MasterViewportTransform
import com.example.prismdiff.core.model.MultiSlotLayout
import com.example.prismdiff.core.model.NormalizationAdjustments
import com.example.prismdiff.core.model.PixelSample
import com.example.prismdiff.core.model.RelativeSlotOffset
import com.example.prismdiff.core.model.ScaleSyncMode
import com.example.prismdiff.core.model.SortOrder
import com.example.prismdiff.core.model.SplitOrientation
import com.example.prismdiff.core.model.StudioDockVisibility
import com.example.prismdiff.core.model.SyncFlags
import com.example.prismdiff.core.model.WorkspacePreset
import com.example.prismdiff.core.shader.PixelMathFormulaEvaluator
import com.example.prismdiff.core.shader.TileComparisonRenderer
import com.example.prismdiff.core.storage.ComparisonExporter
import com.example.prismdiff.core.storage.CullingSessionEntity
import com.example.prismdiff.core.storage.MediaIngestionRepository
import com.example.prismdiff.core.storage.PrismDiffDatabase
import com.example.prismdiff.core.storage.StudioSessionRepository
import com.example.prismdiff.core.storage.UserPreferencesRepository
import com.example.prismdiff.core.storage.WorkspacePresetManager
import com.example.prismdiff.core.vision.AlignmentEngine
import com.example.prismdiff.core.vision.BurstClusteringEngine
import com.example.prismdiff.core.vision.EloTournamentEngine
import com.example.prismdiff.core.vision.ImageMetricsEngine
import com.example.prismdiff.domain.viewport.ViewportSynchronizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.min

@Immutable
data class DeletionRequestPreview(
    val targets: List<ImageAsset>,
    val keptCount: Int,
    val totalBytes: Long,
    val permanentDelete: Boolean,
    val label: String,
)

@Immutable
data class PrismDiffUiState(
    val isLoading: Boolean = true,
    val preferences: AppPreferences = AppPreferences(),
    val preferencesLoaded: Boolean = false,
    val assets: List<ImageAsset> = emptyList(),
    val filteredAssets: List<ImageAsset> = emptyList(),
    val burstClusters: List<BurstCluster> = emptyList(),
    val sortOrder: SortOrder = SortOrder.EXIF_DATE_DESC,
    val searchQuery: String = "",
    val browserMultiSelectedIds: Set<String> = emptySet(),
    val activeSlotIds: List<String> = emptyList(),
    val pinnedReferenceId: String? = null,
    val masterViewport: MasterViewportTransform = MasterViewportTransform(),
    val slotOffsets: Map<Int, RelativeSlotOffset> = emptyMap(),
    val syncFlags: SyncFlags = SyncFlags(),
    val anchorCalibrationSlot: Int? = null,
    val comparisonMode: ComparisonMode = ComparisonMode.SIDE_BY_SIDE,
    val splitOrientation: SplitOrientation = SplitOrientation.AUTO,
    val multiSlotLayout: MultiSlotLayout = MultiSlotLayout.AUTO,
    val dockVisibility: StudioDockVisibility = StudioDockVisibility.COMPACT,
    val splitRatio: Float = 0.5f,
    val curtainRatio: Float = 0.5f,
    val curtainAngleDeg: Float = 0f,
    val onionAlpha: Float = 0.5f,
    val blendOperator: BlendOperator = BlendOperator.NORMAL,
    val layerConfigs: List<LayerSlotConfig> = listOf(LayerSlotConfig(0), LayerSlotConfig(1)),
    val blinkFrequencyHz: Float = 2.0f,
    val blinkPlaying: Boolean = true,
    val blinkShowingA: Boolean = true,
    val blinkHoldPeekA: Boolean = false,
    val differenceMode: DifferenceVisualMode = DifferenceVisualMode.ABSOLUTE_BOOST,
    val diffPalette: DiffPalette = DiffPalette.MAGENTA,
    val diffBoost: Float = 8f,
    val diffThreshold: Float = 0.03f,
    val diagnosticOverlay: DiagnosticOverlay = DiagnosticOverlay.NONE,
    val channelIsolation: ColorChannelIsolation = ColorChannelIsolation.ALL_RGB,
    val elaQuality: Int = 85,
    val elaScale: Float = 18f,
    val bitPlaneIndex: Int = 0,
    val normalizationA: NormalizationAdjustments = NormalizationAdjustments(),
    val normalizationB: NormalizationAdjustments = NormalizationAdjustments(),
    val normalizationHistory: List<Pair<NormalizationAdjustments, NormalizationAdjustments>> = emptyList(),
    val loupeState: LoupeState = LoupeState(),
    val regions: List<ComparisonRegion> = listOf(
        ComparisonRegion("roi_center", "Center Subject", normLeft = 0.35f, normTop = 0.35f, normRight = 0.65f, normBottom = 0.65f),
        ComparisonRegion("roi_corner", "Edge Detail", normLeft = 0.08f, normTop = 0.08f, normRight = 0.38f, normBottom = 0.38f),
    ),
    val activeRegionId: String? = null,
    val annotations: List<AnnotationItem> = emptyList(),
    val showRuleOfThirds: Boolean = false,
    val showAlignmentOverlay: Boolean = false,
    val lightsOutImmersive: Boolean = false,
    val slideshowPlaying: Boolean = false,
    val kenBurnsActive: Boolean = false,
    val customShader: CustomShaderDefinition = PixelMathFormulaEvaluator.builtInTemplates.first(),
    val customParam1: Float = PixelMathFormulaEvaluator.builtInTemplates.first().param1Default,
    val customParam2: Float = PixelMathFormulaEvaluator.builtInTemplates.first().param2Default,
    val customParam3: Float = PixelMathFormulaEvaluator.builtInTemplates.first().param3Default,
    val customShaderError: String? = null,
    val metricsBundle: ComparisonMetricsBundle? = null,
    val metricsComputing: Boolean = false,
    val histogramA: HistogramData? = null,
    val histogramB: HistogramData? = null,
    val alignmentResult: AlignmentResult? = null,
    val exifDiffRows: List<ExifDiffEntry> = emptyList(),
    val pixelProbeA: PixelSample? = null,
    val pixelProbeB: PixelSample? = null,
    val decisions: Map<String, ImageCullingState> = emptyMap(),
    val tournamentPair: Pair<String, String>? = null,
    val presets: List<WorkspacePreset> = WorkspacePresetManager.builtInPresets,
    val sessions: List<CullingSessionEntity> = emptyList(),
    val pendingDeletion: DeletionRequestPreview? = null,
    val exportedShareUri: Uri? = null,
    val exportedMimeType: String = "image/png",
    val statusMessage: String? = null,
) {
    val activeSlotAssets: List<ImageAsset>
        get() = activeSlotIds.mapNotNull { id -> assets.find { it.id == id } }

    val slotA: ImageAsset? get() = activeSlotAssets.getOrNull(0)
    val slotB: ImageAsset? get() = activeSlotAssets.getOrNull(1)
}

class PrismDiffViewModel(
    private val appContext: Context,
) : ViewModel() {

    val decoder = TilePyramidDecoder(appContext)
    private val prefsRepo = UserPreferencesRepository(appContext)
    private val ingestionRepo = MediaIngestionRepository(appContext)
    private val sessionRepo = StudioSessionRepository(PrismDiffDatabase.getInstance(appContext).studioDao())
    private val exporter = ComparisonExporter(appContext)

    private val _state = MutableStateFlow(PrismDiffUiState())
    val state: StateFlow<PrismDiffUiState> = _state.asStateFlow()

    private val _derivedPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val derivedPreviewBitmap: StateFlow<Bitmap?> = _derivedPreviewBitmap.asStateFlow()

    private var strobeJob: Job? = null
    private var slideshowJob: Job? = null
    private var analysisJob: Job? = null

    init {
        observePersistence()
        loadInitialSamplesAndSavedFolder()
    }

    private fun observePersistence() {
        viewModelScope.launch {
            combine(
                prefsRepo.preferences,
                sessionRepo.decisionsMap,
                sessionRepo.customPresets,
                sessionRepo.sessions,
            ) { prefs, decisions, customPresets, sessions ->
                Quad(prefs, decisions, customPresets, sessions)
            }.collect { (prefs, decisions, customPresets, sessions) ->
                _state.update { curr ->
                    curr.copy(
                        preferences = prefs,
                        preferencesLoaded = true,
                        decisions = decisions,
                        presets = WorkspacePresetManager.builtInPresets + customPresets,
                        sessions = sessions,
                    )
                }
            }
        }
    }

    private fun loadInitialSamplesAndSavedFolder() {
        viewModelScope.launch {
            val samplePack = CalibrationSampleGenerator.ensureCalibrationSamples(appContext)
            val initialAssets = MediaIngestionRepository.sortAssets(samplePack.assets, _state.value.sortOrder)
            val defaultIds = initialAssets.take(2).map { it.id }
            _state.update { curr ->
                val activeIds = if (curr.activeSlotIds.isEmpty()) defaultIds else curr.activeSlotIds
                curr.copy(
                    isLoading = false,
                    assets = initialAssets,
                    filteredAssets = applySearchAndSort(initialAssets, curr.searchQuery, curr.sortOrder),
                    burstClusters = samplePack.clusters,
                    activeSlotIds = activeIds,
                )
            }
            refreshPairAnalysisAndExif()
        }
    }

    fun handleIncomingShareIntent(intent: Intent?) {
        val uris = ingestionRepo.extractSharedUris(intent)
        if (uris.isNotEmpty()) {
            ingestPickedUris(uris, folderLabel = "Shared")
        }
    }

    fun ingestPickedUris(uris: List<Uri>, folderLabel: String = "Picker", openInStudio: Boolean = true) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, statusMessage = "Inspecting ${uris.size} photo(s)…") }
            val ingested = ingestionRepo.ingestUris(uris, folderLabel)
            val enriched = enrichDHashes(ingested)
            mergeNewAssets(enriched, openInStudio)
        }
    }

    fun openSafFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            prefsRepo.setSavedFolderTreeUri(treeUri.toString())
            _state.update { it.copy(isLoading = true, statusMessage = "Scanning folder…") }
            val folderAssets = ingestionRepo.listSafFolderImages(treeUri)
            val enriched = enrichDHashes(folderAssets)
            mergeNewAssets(enriched, openInStudio = false)
        }
    }

    private suspend fun enrichDHashes(input: List<ImageAsset>): List<ImageAsset> = withContext(Dispatchers.Default) {
        input.map { asset ->
            if (asset.dHash != null) asset
            else {
                val thumb = decoder.decodeThumbnail(asset, 96)
                if (thumb is DecodeOutcome.Success) {
                    val bmp = thumb.data
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    val hash = BurstClusteringEngine.computeDHash(px, bmp.width, bmp.height)
                    asset.copy(dHash = hash)
                } else asset
            }
        }
    }

    private fun mergeNewAssets(newAssets: List<ImageAsset>, openInStudio: Boolean) {
        if (newAssets.isEmpty()) {
            _state.update { it.copy(isLoading = false, statusMessage = "No readable images found.") }
            return
        }
        _state.update { curr ->
            val merged = (newAssets + curr.assets).distinctBy { it.id }
            val sorted = applySearchAndSort(merged, curr.searchQuery, curr.sortOrder)
            val clusters = BurstClusteringEngine.clusterAssets(merged)
            val slotIds = if (openInStudio && newAssets.size >= 2) {
                newAssets.take(4).map { it.id }
            } else if (openInStudio && newAssets.size == 1 && curr.slotA != null) {
                listOf(curr.slotA!!.id, newAssets[0].id)
            } else curr.activeSlotIds

            curr.copy(
                isLoading = false,
                assets = merged,
                filteredAssets = sorted,
                burstClusters = clusters,
                activeSlotIds = slotIds,
                masterViewport = MasterViewportTransform(),
                slotOffsets = emptyMap(),
                statusMessage = "Loaded ${newAssets.size} image(s)",
            )
        }
        refreshPairAnalysisAndExif()
        saveCurrentSession("Active Comparison")
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { prefsRepo.setDefaultSortOrder(order) }
        _state.update { curr ->
            curr.copy(
                sortOrder = order,
                filteredAssets = applySearchAndSort(curr.assets, curr.searchQuery, order),
            )
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { curr ->
            curr.copy(
                searchQuery = query,
                filteredAssets = applySearchAndSort(curr.assets, query, curr.sortOrder),
            )
        }
    }

    private fun applySearchAndSort(assets: List<ImageAsset>, query: String, order: SortOrder): List<ImageAsset> {
        val filtered = if (query.isBlank()) assets else {
            val q = query.trim().lowercase()
            assets.filter {
                it.displayName.lowercase().contains(q) ||
                    it.folderName.lowercase().contains(q) ||
                    (it.exifSnapshot.cameraModel?.lowercase()?.contains(q) == true)
            }
        }
        return MediaIngestionRepository.sortAssets(filtered, order)
    }

    fun toggleBrowserSelection(assetId: String) {
        _state.update { curr ->
            val next = if (assetId in curr.browserMultiSelectedIds) {
                curr.browserMultiSelectedIds - assetId
            } else {
                curr.browserMultiSelectedIds + assetId
            }
            curr.copy(browserMultiSelectedIds = next)
        }
    }

    fun invertBrowserSelection() {
        _state.update { curr ->
            val allIds = curr.filteredAssets.map { it.id }.toSet()
            curr.copy(browserMultiSelectedIds = allIds - curr.browserMultiSelectedIds)
        }
    }

    fun compareSelectedFromBrowser() {
        val curr = _state.value
        val selected = curr.filteredAssets.filter { it.id in curr.browserMultiSelectedIds }
        val chosen = if (selected.size >= 2) selected else curr.filteredAssets.take(2)
        if (chosen.isNotEmpty()) {
            loadAssetsIntoStudio(chosen.map { it.id })
        }
    }

    fun loadClusterIntoStudio(cluster: BurstCluster) {
        loadAssetsIntoStudio(cluster.assets.map { it.id })
    }

    fun loadPairAtAsset(asset: ImageAsset) {
        val list = _state.value.filteredAssets
        val idx = list.indexOfFirst { it.id == asset.id }.coerceAtLeast(0)
        val partner = list.getOrNull(idx + 1) ?: list.getOrNull(idx - 1) ?: asset
        loadAssetsIntoStudio(listOf(asset.id, partner.id))
    }

    fun loadAssetsIntoStudio(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        val normalized = if (assetIds.size == 1) listOf(assetIds[0], assetIds[0]) else assetIds
        _state.update {
            it.copy(
                activeSlotIds = normalized,
                masterViewport = MasterViewportTransform(),
                slotOffsets = emptyMap(),
                alignmentResult = null,
                anchorCalibrationSlot = null,
            )
        }
        refreshPairAnalysisAndExif()
        saveCurrentSession("Comparison (${normalized.size})")
    }

    fun assignAssetToSlot(slotIndex: Int, assetId: String) {
        _state.update { curr ->
            val mutable = curr.activeSlotIds.toMutableList()
            while (mutable.size <= slotIndex) mutable.add(assetId)
            mutable[slotIndex] = assetId
            curr.copy(activeSlotIds = mutable, alignmentResult = null)
        }
        refreshPairAnalysisAndExif()
    }

    fun swapSlotsAB() {
        _state.update { curr ->
            val list = curr.activeSlotIds.toMutableList()
            if (list.size >= 2) {
                val tmp = list[0]
                list[0] = list[1]
                list[1] = tmp
            }
            val off0 = curr.slotOffsets[0]
            val off1 = curr.slotOffsets[1]
            val nextOffsets = curr.slotOffsets.toMutableMap()
            if (off1 != null) nextOffsets[0] = off1 else nextOffsets.remove(0)
            if (off0 != null) nextOffsets[1] = off0 else nextOffsets.remove(1)
            curr.copy(activeSlotIds = list, slotOffsets = nextOffsets, alignmentResult = null)
        }
        refreshPairAnalysisAndExif()
    }

    fun addSlotAsset(assetId: String? = null) {
        _state.update { curr ->
            if (curr.activeSlotIds.size >= 6) {
                return@update curr.copy(statusMessage = "Maximum 6 comparison slots active")
            }
            val candidateId = assetId
                ?: curr.filteredAssets.firstOrNull { it.id !in curr.activeSlotIds }?.id
                ?: curr.filteredAssets.firstOrNull()?.id
                ?: return@update curr
            curr.copy(
                activeSlotIds = curr.activeSlotIds + candidateId,
                statusMessage = "Added Slot ${'A' + curr.activeSlotIds.size}",
            )
        }
    }

    fun removeSlotAt(slotIndex: Int) {
        _state.update { curr ->
            if (curr.activeSlotIds.size <= 2 || slotIndex !in curr.activeSlotIds.indices) {
                return@update curr
            }
            val nextIds = curr.activeSlotIds.filterIndexed { i, _ -> i != slotIndex }
            val remappedOffsets = mutableMapOf<Int, RelativeSlotOffset>()
            var writeIdx = 0
            for (readIdx in curr.activeSlotIds.indices) {
                if (readIdx == slotIndex) continue
                curr.slotOffsets[readIdx]?.let { remappedOffsets[writeIdx] = it }
                writeIdx++
            }
            curr.copy(
                activeSlotIds = nextIds,
                slotOffsets = remappedOffsets,
                anchorCalibrationSlot = null,
                alignmentResult = null,
            )
        }
        refreshPairAnalysisAndExif()
    }

    fun moveSlot(fromIndex: Int, toIndex: Int) {
        _state.update { curr ->
            val size = curr.activeSlotIds.size
            if (fromIndex !in 0 until size || toIndex !in 0 until size || fromIndex == toIndex) {
                return@update curr
            }
            val mutableIds = curr.activeSlotIds.toMutableList()
            val movedId = mutableIds.removeAt(fromIndex)
            mutableIds.add(toIndex, movedId)

            val orderedOffsets = (0 until size).map { curr.slotOffsets[it] }.toMutableList()
            val movedOffset = orderedOffsets.removeAt(fromIndex)
            orderedOffsets.add(toIndex, movedOffset)
            val nextOffsets = buildMap {
                orderedOffsets.forEachIndexed { i, off ->
                    if (off != null) put(i, off)
                }
            }
            curr.copy(
                activeSlotIds = mutableIds,
                slotOffsets = nextOffsets,
                alignmentResult = null,
            )
        }
        refreshPairAnalysisAndExif()
    }

    fun setMultiSlotLayout(layout: MultiSlotLayout) {
        _state.update { it.copy(multiSlotLayout = layout) }
    }

    fun cycleMultiSlotLayout() {
        _state.update { curr ->
            val entries = MultiSlotLayout.entries
            val next = entries[(entries.indexOf(curr.multiSlotLayout) + 1) % entries.size]
            curr.copy(multiSlotLayout = next, statusMessage = "Placement: ${next.name.lowercase().replace('_', ' ')}")
        }
    }

    fun setDockVisibility(visibility: StudioDockVisibility) {
        _state.update {
            it.copy(
                dockVisibility = visibility,
                lightsOutImmersive = visibility == StudioDockVisibility.HIDDEN,
            )
        }
    }

    // --- King-of-the-Hill Culling & Burst Stepping ---
    fun togglePinReferenceSlotA() {
        _state.update { curr ->
            val slotAId = curr.slotA?.id
            val nextPin = if (curr.pinnedReferenceId == slotAId) null else slotAId
            curr.copy(pinnedReferenceId = nextPin)
        }
    }

    fun promoteSlotBToReference() {
        _state.update { curr ->
            val b = curr.slotB ?: return@update curr
            val updated = curr.activeSlotIds.toMutableList()
            if (updated.size >= 2) {
                updated[0] = b.id
            }
            // Advance Slot B to next candidate in library
            val lib = curr.filteredAssets
            val idxB = lib.indexOfFirst { it.id == b.id }
            val nextCandidate = lib.drop(idxB + 1).firstOrNull { it.id != b.id } ?: curr.slotA
            if (nextCandidate != null && updated.size >= 2) {
                updated[1] = nextCandidate.id
            }
            curr.copy(
                activeSlotIds = updated,
                pinnedReferenceId = b.id,
                statusMessage = "Promoted ${b.displayName} to Reference A",
            )
        }
        refreshPairAnalysisAndExif()
    }

    fun stepSlotB(delta: Int) {
        val curr = _state.value
        val lib = curr.filteredAssets
        if (lib.size < 2) return
        val currentBId = curr.slotB?.id ?: return
        val idx = lib.indexOfFirst { it.id == currentBId }.coerceAtLeast(0)
        val nextIdx = (idx + delta + lib.size) % lib.size
        val nextAsset = lib[nextIdx]
        assignAssetToSlot(1, nextAsset.id)
    }

    // --- SSOT Viewport & Alignment Controls ---
    fun panByScreenDelta(slotIndex: Int, dxPx: Float, dyPx: Float, containerW: Float, containerH: Float) {
        _state.update { curr ->
            val ref = curr.slotA ?: return@update curr
            val calibrating = curr.anchorCalibrationSlot == slotIndex
            if (calibrating || !curr.syncFlags.syncPan) {
                val target = curr.activeSlotAssets.getOrNull(slotIndex) ?: ref
                val prevOffset = curr.slotOffsets[slotIndex] ?: RelativeSlotOffset()
                val nextOffset = ViewportSynchronizer.panSlotOffsetByScreenDelta(
                    offset = prevOffset,
                    master = curr.masterViewport,
                    dxPx = dxPx,
                    dyPx = dyPx,
                    imageWidth = target.width.coerceAtLeast(100),
                    imageHeight = target.height.coerceAtLeast(100),
                    containerWidthPx = containerW,
                    containerHeightPx = containerH,
                    calibrateRelativeDelta = calibrating,
                )
                curr.copy(slotOffsets = curr.slotOffsets + (slotIndex to nextOffset))
            } else {
                val nextMaster = ViewportSynchronizer.panMasterByScreenDelta(
                    master = curr.masterViewport,
                    dxPx = dxPx,
                    dyPx = dyPx,
                    referenceWidth = ref.width.coerceAtLeast(100),
                    referenceHeight = ref.height.coerceAtLeast(100),
                    containerWidthPx = containerW,
                    containerHeightPx = containerH,
                )
                curr.copy(masterViewport = nextMaster)
            }
        }
    }

    fun zoomByFactor(slotIndex: Int, zoomRatio: Float) {
        _state.update { curr ->
            val calibrating = curr.anchorCalibrationSlot == slotIndex
            if (calibrating) {
                val prev = curr.slotOffsets[slotIndex] ?: RelativeSlotOffset()
                val next = prev.copy(deltaZoom = (prev.deltaZoom * zoomRatio).coerceIn(0.25f, 4f))
                curr.copy(slotOffsets = curr.slotOffsets + (slotIndex to next))
            } else if (!curr.syncFlags.syncZoom) {
                val prev = curr.slotOffsets[slotIndex] ?: RelativeSlotOffset()
                val next = prev.copy(independentZoom = (prev.independentZoom * zoomRatio).coerceIn(ViewportSynchronizer.MIN_ZOOM, ViewportSynchronizer.MAX_ZOOM))
                curr.copy(slotOffsets = curr.slotOffsets + (slotIndex to next))
            } else {
                curr.copy(masterViewport = ViewportSynchronizer.zoomMaster(curr.masterViewport, zoomRatio))
            }
        }
    }

    fun setMasterZoomExact(zoom: Float) {
        _state.update { curr ->
            curr.copy(
                masterViewport = curr.masterViewport.copy(
                    zoomFactor = zoom.coerceIn(ViewportSynchronizer.MIN_ZOOM, ViewportSynchronizer.MAX_ZOOM),
                ),
            )
        }
    }

    fun setZoomPhysicalOneToOne(containerW: Float, containerH: Float) {
        val ref = _state.value.slotA ?: return
        val z = ViewportSynchronizer.zoomForPhysicalOneToOne(ref.width, ref.height, containerW, containerH)
        _state.update {
            it.copy(
                masterViewport = it.masterViewport.copy(
                    zoomFactor = z,
                    scaleSyncMode = ScaleSyncMode.PHYSICAL_1_TO_1,
                ),
            )
        }
    }

    fun handleDoubleTapZoom(containerW: Float, containerH: Float) {
        val curr = _state.value
        val ref = curr.slotA ?: return
        if (curr.masterViewport.zoomFactor > 1.25f) {
            _state.update {
                it.copy(
                    masterViewport = it.masterViewport.copy(
                        centerU = 0.5f,
                        centerV = 0.5f,
                        zoomFactor = 1f,
                    ),
                )
            }
        } else {
            val oneToOne = ViewportSynchronizer.zoomForPhysicalOneToOne(ref.width, ref.height, containerW, containerH)
            val targetZoom = kotlin.math.max(3.0f, oneToOne).coerceAtMost(ViewportSynchronizer.MAX_ZOOM)
            _state.update {
                it.copy(
                    masterViewport = it.masterViewport.copy(zoomFactor = targetZoom),
                )
            }
        }
    }

    fun rotateSlotOrMaster(slotIndex: Int, deltaDeg: Float) {
        _state.update { curr ->
            if (curr.anchorCalibrationSlot == slotIndex || !curr.syncFlags.syncRotation) {
                val prev = curr.slotOffsets[slotIndex] ?: RelativeSlotOffset()
                val next = if (curr.anchorCalibrationSlot == slotIndex) {
                    prev.copy(deltaRotationDeg = ViewportSynchronizer.normalizeAngle(prev.deltaRotationDeg + deltaDeg))
                } else {
                    prev.copy(independentRotationDeg = ViewportSynchronizer.normalizeAngle(prev.independentRotationDeg + deltaDeg))
                }
                curr.copy(slotOffsets = curr.slotOffsets + (slotIndex to next))
            } else {
                curr.copy(
                    masterViewport = curr.masterViewport.copy(
                        rotationDeg = ViewportSynchronizer.normalizeAngle(curr.masterViewport.rotationDeg + deltaDeg),
                    ),
                )
            }
        }
    }

    fun flipSlot(slotIndex: Int, horizontal: Boolean) {
        _state.update { curr ->
            val prev = curr.slotOffsets[slotIndex] ?: RelativeSlotOffset()
            val next = if (horizontal) prev.copy(flipHorizontal = !prev.flipHorizontal)
            else prev.copy(flipVertical = !prev.flipVertical)
            curr.copy(slotOffsets = curr.slotOffsets + (slotIndex to next))
        }
    }

    fun resetSlotOffset(slotIndex: Int) {
        _state.update { curr ->
            curr.copy(
                slotOffsets = curr.slotOffsets - slotIndex,
                anchorCalibrationSlot = if (curr.anchorCalibrationSlot == slotIndex) null else curr.anchorCalibrationSlot,
            )
        }
    }

    fun resetViewportAndOffsets() {
        _state.update {
            it.copy(
                masterViewport = ViewportSynchronizer.resetMaster(),
                slotOffsets = emptyMap(),
                anchorCalibrationSlot = null,
                alignmentResult = null,
                splitRatio = 0.5f,
                curtainRatio = 0.5f,
                pixelProbeA = null,
                pixelProbeB = null,
            )
        }
    }

    fun toggleSyncFlag(zoom: Boolean? = null, pan: Boolean? = null, rotation: Boolean? = null, crop: Boolean? = null) {
        _state.update { curr ->
            curr.copy(
                syncFlags = curr.syncFlags.copy(
                    syncZoom = zoom ?: curr.syncFlags.syncZoom,
                    syncPan = pan ?: curr.syncFlags.syncPan,
                    syncRotation = rotation ?: curr.syncFlags.syncRotation,
                    syncCrop = crop ?: curr.syncFlags.syncCrop,
                ),
            )
        }
    }

    fun setScaleSyncMode(mode: ScaleSyncMode) {
        _state.update { it.copy(masterViewport = it.masterViewport.copy(scaleSyncMode = mode)) }
    }

    fun toggleAnchorCalibration(slotIndex: Int = 1) {
        _state.update { curr ->
            val next = if (curr.anchorCalibrationSlot == slotIndex) null else slotIndex
            curr.copy(anchorCalibrationSlot = next)
        }
    }

    fun runAutoAlignScene() {
        val assetA = _state.value.slotA ?: return
        val assetB = _state.value.slotB ?: return
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = "Running feature & phase registration…") }
            val outA = decoder.decodeWorkingBuffer(assetA, 256)
            val outB = decoder.decodeWorkingBuffer(assetB, 256)
            if (outA is DecodeOutcome.Success && outB is DecodeOutcome.Success) {
                val result = withContext(Dispatchers.Default) {
                    val w = min(outA.data.width, outB.data.width)
                    val h = min(outA.data.height, outB.data.height)
                    val bmpA = if (outA.data.width == w && outA.data.height == h) outA.data else Bitmap.createScaledBitmap(outA.data, w, h, true)
                    val bmpB = if (outB.data.width == w && outB.data.height == h) outB.data else Bitmap.createScaledBitmap(outB.data, w, h, true)
                    val pxA = IntArray(w * h)
                    val pxB = IntArray(w * h)
                    bmpA.getPixels(pxA, 0, w, 0, 0, w, h)
                    bmpB.getPixels(pxB, 0, w, 0, 0, w, h)
                    AlignmentEngine.autoAlign(pxA, pxB, w, h, allowRotationAndScale = true)
                }
                _state.update { curr ->
                    curr.copy(
                        alignmentResult = result,
                        slotOffsets = curr.slotOffsets + (1 to result.offset),
                        statusMessage = result.summaryNote,
                    )
                }
                refreshPairAnalysisAndExif()
            }
        }
    }

    fun undoAlignment() {
        _state.update {
            it.copy(
                alignmentResult = null,
                slotOffsets = it.slotOffsets - 1,
                statusMessage = "Alignment reset",
            )
        }
        refreshPairAnalysisAndExif()
    }

    // --- Comparison Modes, Overlays & Shader Lab ---
    fun setComparisonMode(mode: ComparisonMode) {
        _state.update { it.copy(comparisonMode = mode) }
        manageBlinkTimer(mode == ComparisonMode.BLINK_STROBE)
        refreshDerivedComparisonBitmap()
    }

    fun setSplitOrientation(orientation: SplitOrientation) {
        _state.update { it.copy(splitOrientation = orientation) }
    }

    fun updateSplitRatio(ratio: Float) {
        _state.update { it.copy(splitRatio = ratio.coerceIn(0.15f, 0.85f)) }
    }

    fun updateCurtain(ratio: Float? = null, angleDeg: Float? = null) {
        _state.update { curr ->
            curr.copy(
                curtainRatio = (ratio ?: curr.curtainRatio).coerceIn(0.02f, 0.98f),
                curtainAngleDeg = angleDeg?.let { ViewportSynchronizer.normalizeAngle(it) } ?: curr.curtainAngleDeg,
            )
        }
    }

    fun updateCurtainByDelta(deltaRatio: Float) {
        _state.update { curr ->
            curr.copy(
                curtainRatio = (curr.curtainRatio + deltaRatio).coerceIn(0.02f, 0.98f),
            )
        }
    }

    fun updateOnionAndBlend(alpha: Float? = null, blend: BlendOperator? = null) {
        _state.update { curr ->
            curr.copy(
                onionAlpha = (alpha ?: curr.onionAlpha).coerceIn(0f, 1f),
                blendOperator = blend ?: curr.blendOperator,
            )
        }
    }

    fun updateBlinkStrobe(hz: Float? = null, playing: Boolean? = null, holdPeekA: Boolean? = null) {
        _state.update { curr ->
            curr.copy(
                blinkFrequencyHz = (hz ?: curr.blinkFrequencyHz).coerceIn(0.5f, 10f),
                blinkPlaying = playing ?: curr.blinkPlaying,
                blinkHoldPeekA = holdPeekA ?: curr.blinkHoldPeekA,
            )
        }
        manageBlinkTimer(_state.value.comparisonMode == ComparisonMode.BLINK_STROBE)
    }

    private fun manageBlinkTimer(active: Boolean) {
        strobeJob?.cancel()
        if (!active) return
        strobeJob = viewModelScope.launch {
            while (true) {
                val curr = _state.value
                val hz = curr.blinkFrequencyHz.coerceIn(0.5f, 10f)
                val halfPeriodMs = (500f / hz).toLong().coerceAtLeast(50L)
                delay(halfPeriodMs)
                if (curr.blinkPlaying && !curr.blinkHoldPeekA) {
                    _state.update { it.copy(blinkShowingA = !it.blinkShowingA) }
                }
            }
        }
    }

    fun updateDifferenceSettings(
        mode: DifferenceVisualMode? = null,
        palette: DiffPalette? = null,
        boost: Float? = null,
        threshold: Float? = null,
    ) {
        _state.update { curr ->
            curr.copy(
                differenceMode = mode ?: curr.differenceMode,
                diffPalette = palette ?: curr.diffPalette,
                diffBoost = (boost ?: curr.diffBoost).coerceIn(1f, 50f),
                diffThreshold = (threshold ?: curr.diffThreshold).coerceIn(0f, 0.6f),
            )
        }
        refreshDerivedComparisonBitmap()
    }

    fun updateDiagnostics(
        overlay: DiagnosticOverlay? = null,
        channel: ColorChannelIsolation? = null,
        elaQuality: Int? = null,
        elaScale: Float? = null,
        bitPlaneIndex: Int? = null,
    ) {
        _state.update { curr ->
            curr.copy(
                diagnosticOverlay = overlay ?: curr.diagnosticOverlay,
                channelIsolation = channel ?: curr.channelIsolation,
                elaQuality = (elaQuality ?: curr.elaQuality).coerceIn(1, 100),
                elaScale = (elaScale ?: curr.elaScale).coerceIn(1f, 100f),
                bitPlaneIndex = (bitPlaneIndex ?: curr.bitPlaneIndex).coerceIn(0, 7),
            )
        }
    }

    fun updateNormalization(slotA: NormalizationAdjustments? = null, slotB: NormalizationAdjustments? = null) {
        _state.update { curr ->
            val prev = curr.normalizationA to curr.normalizationB
            curr.copy(
                normalizationA = slotA ?: curr.normalizationA,
                normalizationB = slotB ?: curr.normalizationB,
                normalizationHistory = (curr.normalizationHistory + prev).takeLast(20),
            )
        }
    }

    fun undoNormalization() {
        _state.update { curr ->
            val last = curr.normalizationHistory.lastOrNull() ?: return@update curr
            curr.copy(
                normalizationA = last.first,
                normalizationB = last.second,
                normalizationHistory = curr.normalizationHistory.dropLast(1),
            )
        }
    }

    fun resetNormalization() {
        _state.update {
            it.copy(
                normalizationA = NormalizationAdjustments(),
                normalizationB = NormalizationAdjustments(),
            )
        }
    }

    fun updateLoupe(normX: Float? = null, normY: Float? = null, mag: Float? = null, docked: Boolean? = null) {
        _state.update { curr ->
            curr.copy(
                loupeState = curr.loupeState.copy(
                    normX = (normX ?: curr.loupeState.normX).coerceIn(0.05f, 0.95f),
                    normY = (normY ?: curr.loupeState.normY).coerceIn(0.05f, 0.95f),
                    magnification = (mag ?: curr.loupeState.magnification).coerceIn(4f, 32f),
                    isDocked = docked ?: curr.loupeState.isDocked,
                ),
            )
        }
    }

    fun selectRegion(regionId: String?) {
        _state.update { it.copy(activeRegionId = regionId) }
        refreshPairAnalysisAndExif()
    }

    fun addAnnotation(item: AnnotationItem) {
        _state.update { it.copy(annotations = it.annotations + item) }
    }

    fun clearAnnotations() {
        _state.update { it.copy(annotations = emptyList()) }
    }

    fun toggleRuleOfThirds() {
        _state.update { it.copy(showRuleOfThirds = !it.showRuleOfThirds) }
    }

    fun toggleAlignmentOverlay() {
        _state.update { it.copy(showAlignmentOverlay = !it.showAlignmentOverlay) }
    }

    fun toggleLightsOut() {
        _state.update { curr ->
            val nextHidden = !curr.lightsOutImmersive
            curr.copy(
                lightsOutImmersive = nextHidden,
                dockVisibility = if (nextHidden) StudioDockVisibility.HIDDEN else StudioDockVisibility.COMPACT,
            )
        }
    }

    fun clearPixelProbe() {
        _state.update { it.copy(pixelProbeA = null, pixelProbeB = null) }
    }

    fun toggleSlideshow(kenBurns: Boolean = false) {
        val next = !_state.value.slideshowPlaying
        _state.update { it.copy(slideshowPlaying = next, kenBurnsActive = kenBurns && next) }
        slideshowJob?.cancel()
        if (next) {
            slideshowJob = viewModelScope.launch {
                val points = listOf(0.5f to 0.5f, 0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f)
                var stepIdx = 0
                while (true) {
                    delay(2400L)
                    if (kenBurns) {
                        val pt = points[stepIdx % points.size]
                        _state.update {
                            it.copy(
                                masterViewport = it.masterViewport.copy(
                                    centerU = pt.first,
                                    centerV = pt.second,
                                    zoomFactor = 3.5f,
                                ),
                            )
                        }
                    } else {
                        stepSlotB(1)
                    }
                    stepIdx++
                }
            }
        }
    }

    fun updateCustomShader(
        definition: CustomShaderDefinition? = null,
        expression: String? = null,
        p1: Float? = null,
        p2: Float? = null,
        p3: Float? = null,
    ) {
        val curr = _state.value
        val targetDef = definition ?: curr.customShader
        val finalExpr = expression ?: targetDef.expression
        val validation = PixelMathFormulaEvaluator.validate(finalExpr)
        _state.update {
            it.copy(
                customShader = targetDef.copy(expression = finalExpr),
                customParam1 = p1 ?: if (definition != null) definition.param1Default else it.customParam1,
                customParam2 = p2 ?: if (definition != null) definition.param2Default else it.customParam2,
                customParam3 = p3 ?: if (definition != null) definition.param3Default else it.customParam3,
                customShaderError = validation.exceptionOrNull()?.message,
            )
        }
        if (validation.isSuccess && _state.value.comparisonMode == ComparisonMode.CUSTOM_SHADER) {
            refreshDerivedComparisonBitmap()
        }
    }

    fun samplePixelAt(normU: Float, normV: Float) {
        val a = _state.value.slotA ?: return
        val b = _state.value.slotB ?: return
        viewModelScope.launch {
            val sA = decoder.samplePixelAt(a, normU, normV)
            val sB = decoder.samplePixelAt(b, normU, normV)
            _state.update { it.copy(pixelProbeA = sA, pixelProbeB = sB) }
        }
    }

    fun refreshPairAnalysisAndExif() {
        val curr = _state.value
        val a = curr.slotA ?: return
        val b = curr.slotB ?: a
        val diffRows = ExifDiffEngine.compareMetadata(a, b, curr.preferences.maskGpsByDefault)
        _state.update { it.copy(exifDiffRows = diffRows, metricsComputing = true) }

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            val outA = decoder.decodeWorkingBuffer(a, 256)
            val outB = decoder.decodeWorkingBuffer(b, 256)
            if (outA is DecodeOutcome.Success && outB is DecodeOutcome.Success) {
                val region = curr.regions.find { it.id == curr.activeRegionId }
                val result = withContext(Dispatchers.Default) {
                    val w = min(outA.data.width, outB.data.width)
                    val h = min(outA.data.height, outB.data.height)
                    val sA = if (outA.data.width == w && outA.data.height == h) outA.data else Bitmap.createScaledBitmap(outA.data, w, h, true)
                    val sB = if (outB.data.width == w && outB.data.height == h) outB.data else Bitmap.createScaledBitmap(outB.data, w, h, true)

                    val rx0 = if (region != null) (region.normLeft * w).toInt().coerceIn(0, w - 8) else 0
                    val ry0 = if (region != null) (region.normTop * h).toInt().coerceIn(0, h - 8) else 0
                    val rw = if (region != null) ((region.normRight - region.normLeft) * w).toInt().coerceIn(8, w - rx0) else w
                    val rh = if (region != null) ((region.normBottom - region.normTop) * h).toInt().coerceIn(8, h - ry0) else h

                    val pxA = IntArray(rw * rh)
                    val pxB = IntArray(rw * rh)
                    sA.getPixels(pxA, 0, rw, rx0, ry0, rw, rh)
                    sB.getPixels(pxB, 0, rw, rx0, ry0, rw, rh)
                    if (sA !== outA.data) sA.recycle()
                    if (sB !== outB.data) sB.recycle()

                    val scopeName = region?.name ?: "Working Frame"
                    val metrics = ImageMetricsEngine.compareBuffers(
                        pixelsA = pxA,
                        pixelsB = pxB,
                        width = rw,
                        height = rh,
                        thresholdFraction = curr.diffThreshold,
                        scopeLabel = scopeName,
                        alignmentApplied = curr.alignmentResult != null,
                        colorNormalized = !curr.normalizationA.isIdentity || !curr.normalizationB.isIdentity,
                    )
                    val histA = ImageMetricsEngine.computeHistogram(pxA, scopeName)
                    val histB = ImageMetricsEngine.computeHistogram(pxB, scopeName)
                    Triple(metrics, histA, histB)
                }
                _state.update {
                    it.copy(
                        metricsBundle = result.first,
                        histogramA = result.second,
                        histogramB = result.third,
                        metricsComputing = false,
                    )
                }
                refreshDerivedComparisonBitmap()
            } else {
                _state.update { it.copy(metricsComputing = false) }
            }
        }
    }

    private fun refreshDerivedComparisonBitmap() {
        val curr = _state.value
        val mode = curr.comparisonMode
        if (mode != ComparisonMode.DIFFERENCE_MAP && mode != ComparisonMode.CUSTOM_SHADER) return
        val a = curr.slotA ?: return
        val b = curr.slotB ?: a
        viewModelScope.launch {
            val outA = decoder.decodeWorkingBuffer(a, 512)
            val outB = decoder.decodeWorkingBuffer(b, 512)
            if (outA is DecodeOutcome.Success && outB is DecodeOutcome.Success) {
                val bmp = if (mode == ComparisonMode.CUSTOM_SHADER) {
                    TileComparisonRenderer.renderCustomFormulaBitmap(
                        bitmapA = outA.data,
                        bitmapB = outB.data,
                        expression = curr.customShader.expression,
                        p1 = curr.customParam1,
                        p2 = curr.customParam2,
                        p3 = curr.customParam3,
                    ).getOrNull()
                } else {
                    TileComparisonRenderer.renderDifferenceBitmap(
                        bitmapA = outA.data,
                        bitmapB = outB.data,
                        mode = curr.differenceMode,
                        palette = curr.diffPalette,
                        boostFactor = curr.diffBoost,
                        thresholdFraction = curr.diffThreshold,
                    )
                }
                _derivedPreviewBitmap.value = bmp
            }
        }
    }

    // --- Culling, Elo Tournament & Safe Deletion ---
    fun setCullingDecision(assetId: String, decision: CullingDecision) {
        val existing = _state.value.decisions[assetId] ?: ImageCullingState(assetId)
        val updated = existing.copy(decision = decision)
        viewModelScope.launch { sessionRepo.saveDecision(updated) }
    }

    fun setAssetStars(assetId: String, stars: Int) {
        val existing = _state.value.decisions[assetId] ?: ImageCullingState(assetId)
        val updated = existing.copy(stars = stars.coerceIn(0, 5))
        viewModelScope.launch { sessionRepo.saveDecision(updated) }
    }

    fun startOrAdvanceTournament(winnerId: String? = null, loserId: String? = null) {
        viewModelScope.launch {
            val curr = _state.value
            val updatedMap = curr.decisions.toMutableMap()
            if (winnerId != null && loserId != null) {
                val wState = updatedMap[winnerId] ?: ImageCullingState(winnerId)
                val lState = updatedMap[loserId] ?: ImageCullingState(loserId)
                val (nw, nl) = EloTournamentEngine.recordMatch(wState, lState)
                updatedMap[winnerId] = nw.copy(decision = CullingDecision.KEEP)
                updatedMap[loserId] = nl
                sessionRepo.saveDecisions(listOf(updatedMap[winnerId]!!, updatedMap[loserId]!!))
            }
            val ids = curr.filteredAssets.map { it.id }
            val nextPair = EloTournamentEngine.suggestNextPair(ids, updatedMap)
            _state.update { it.copy(tournamentPair = nextPair) }
            if (nextPair != null) {
                loadAssetsIntoStudio(listOf(nextPair.first, nextPair.second))
            }
        }
    }

    fun requestTrashOrDeleteUnselected(permanentDelete: Boolean) {
        val curr = _state.value
        val keptIds = curr.decisions.filterValues {
            it.decision == CullingDecision.KEEP || it.decision == CullingDecision.FAVORITE
        }.keys
        val targets = curr.filteredAssets.filter { it.id !in keptIds && !it.isSampleAsset }
        if (targets.isEmpty()) {
            _state.update { it.copy(statusMessage = "No non-sample unselected device files to remove.") }
            return
        }
        _state.update {
            it.copy(
                pendingDeletion = DeletionRequestPreview(
                    targets = targets,
                    keptCount = keptIds.size,
                    totalBytes = targets.sumOf { a -> a.fileSizeBytes },
                    permanentDelete = permanentDelete,
                    label = "Delete Unselected",
                ),
            )
        }
    }

    fun requestTrashOrDeleteSelected(permanentDelete: Boolean) {
        val curr = _state.value
        val targets = curr.filteredAssets.filter { it.id in curr.browserMultiSelectedIds && !it.isSampleAsset }
        if (targets.isEmpty()) {
            _state.update { it.copy(statusMessage = "Select non-sample device photos first.") }
            return
        }
        _state.update {
            it.copy(
                pendingDeletion = DeletionRequestPreview(
                    targets = targets,
                    keptCount = curr.filteredAssets.size - targets.size,
                    totalBytes = targets.sumOf { a -> a.fileSizeBytes },
                    permanentDelete = permanentDelete,
                    label = "Delete Selected",
                ),
            )
        }
    }

    fun cancelDeletionPreview() {
        _state.update { it.copy(pendingDeletion = null) }
    }

    fun executeConfirmedDeletion(launchSystemRequest: (PendingIntent) -> Unit) {
        val preview = _state.value.pendingDeletion ?: return
        _state.update { it.copy(pendingDeletion = null) }
        val systemPending = ingestionRepo.createSystemTrashOrDeleteIntent(preview.targets, preview.permanentDelete)
        if (systemPending != null) {
            launchSystemRequest(systemPending)
        } else {
            viewModelScope.launch {
                val removed = ingestionRepo.deleteDirectSafOrCacheAssets(preview.targets)
                val removedIds = preview.targets.map { it.id }.toSet()
                _state.update { curr ->
                    val remaining = curr.assets.filterNot { it.id in removedIds }
                    curr.copy(
                        assets = remaining,
                        filteredAssets = applySearchAndSort(remaining, curr.searchQuery, curr.sortOrder),
                        browserMultiSelectedIds = curr.browserMultiSelectedIds - removedIds,
                        statusMessage = "Removed $removed file(s).",
                    )
                }
            }
        }
    }

    fun onSystemTrashOrDeleteCompleted(succeeded: Boolean) {
        if (succeeded) {
            _state.update { it.copy(statusMessage = "System removal confirmed.") }
        } else {
            _state.update { it.copy(statusMessage = "Removal cancelled.") }
        }
    }

    // --- Export & Presets & Preferences ---
    fun exportComparisonCollage() {
        val curr = _state.value
        val a = curr.slotA ?: return
        val b = curr.slotB ?: a
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = "Rendering Before/After PNG collage…") }
            val outA = decoder.decodeWorkingBuffer(a, 1024)
            val outB = decoder.decodeWorkingBuffer(b, 1024)
            if (outA is DecodeOutcome.Success && outB is DecodeOutcome.Success) {
                val uri = exporter.exportSideBySideCollagePng(
                    bitmapA = outA.data,
                    bitmapB = outB.data,
                    assetA = a,
                    assetB = b,
                    hudTemplate = curr.preferences.hudTemplate,
                    annotations = curr.annotations,
                )
                _state.update { it.copy(exportedShareUri = uri, exportedMimeType = "image/png", statusMessage = "Collage ready to share") }
            }
        }
    }

    fun exportAnimatedWipeGif() {
        val curr = _state.value
        val a = curr.slotA ?: return
        val b = curr.slotB ?: a
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = "Encoding animated wipe GIF…") }
            val outA = decoder.decodeWorkingBuffer(a, 512)
            val outB = decoder.decodeWorkingBuffer(b, 512)
            if (outA is DecodeOutcome.Success && outB is DecodeOutcome.Success) {
                val uri = exporter.exportAnimatedWipeGif(outA.data, outB.data)
                _state.update { it.copy(exportedShareUri = uri, exportedMimeType = "image/gif", statusMessage = "Animated GIF ready") }
            }
        }
    }

    fun exportJsonReport() {
        val curr = _state.value
        val a = curr.slotA ?: return
        val b = curr.slotB ?: a
        viewModelScope.launch {
            val preset = curr.presets.find { it.id == curr.preferences.activePresetId }
            val uri = exporter.exportJsonReport(a, b, curr.metricsBundle, preset)
            _state.update { it.copy(exportedShareUri = uri, exportedMimeType = "application/json", statusMessage = "JSON report ready") }
        }
    }

    fun consumeExportedShareUri() {
        _state.update { it.copy(exportedShareUri = null) }
    }

    fun applyPreset(preset: WorkspacePreset) {
        viewModelScope.launch {
            prefsRepo.setActivePresetId(preset.id)
            prefsRepo.setCanvasBackdrop(preset.backdropMode)
            prefsRepo.setHudTemplate(preset.hudTemplate)
        }
        _state.update { curr ->
            curr.copy(
                comparisonMode = preset.comparisonMode,
                splitOrientation = preset.splitOrientation,
                masterViewport = curr.masterViewport.copy(scaleSyncMode = preset.scaleSyncMode),
                syncFlags = preset.syncFlags,
                blendOperator = preset.blendOperator,
                differenceMode = preset.differenceMode,
                diffPalette = preset.diffPalette,
                diagnosticOverlay = preset.diagnosticOverlay,
                channelIsolation = preset.channelIsolation,
                blinkFrequencyHz = preset.strobeFrequencyHz,
                customShader = preset.customShader ?: curr.customShader,
                statusMessage = "Applied preset: ${preset.name}",
            )
        }
        manageBlinkTimer(preset.comparisonMode == ComparisonMode.BLINK_STROBE)
        refreshDerivedComparisonBitmap()
    }

    fun saveCurrentAsPreset(name: String, description: String) {
        val curr = _state.value
        val preset = WorkspacePreset(
            id = "custom_${System.currentTimeMillis()}",
            name = name.ifBlank { "Custom Workspace" },
            description = description.ifBlank { "User-configured studio layout" },
            isBuiltIn = false,
            comparisonMode = curr.comparisonMode,
            splitOrientation = curr.splitOrientation,
            scaleSyncMode = curr.masterViewport.scaleSyncMode,
            syncFlags = curr.syncFlags,
            backdropMode = curr.preferences.canvasBackdrop,
            blendOperator = curr.blendOperator,
            differenceMode = curr.differenceMode,
            diffPalette = curr.diffPalette,
            diagnosticOverlay = curr.diagnosticOverlay,
            channelIsolation = curr.channelIsolation,
            strobeFrequencyHz = curr.blinkFrequencyHz,
            hudTemplate = curr.preferences.hudTemplate,
            customShader = if (curr.comparisonMode == ComparisonMode.CUSTOM_SHADER) curr.customShader else null,
        )
        viewModelScope.launch { sessionRepo.savePreset(preset) }
        _state.update { it.copy(statusMessage = "Saved preset '${preset.name}'") }
    }

    fun importPresetJson(rawJson: String) {
        WorkspacePresetManager.importFromJson(rawJson)
            .onSuccess { preset ->
                viewModelScope.launch { sessionRepo.savePreset(preset) }
                applyPreset(preset)
            }
            .onFailure { err ->
                _state.update { it.copy(statusMessage = "Invalid preset JSON: ${err.message}") }
            }
    }

    fun exportPresetJson(preset: WorkspacePreset): String = WorkspacePresetManager.exportToJson(preset)

    fun saveCurrentSession(title: String) {
        val curr = _state.value
        val ids = curr.activeSlotIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            sessionRepo.saveSession(
                id = "last_session",
                title = title,
                assetUris = ids,
                slotAIndex = 0,
                slotBIndex = 1,
                modeName = curr.comparisonMode.name,
                presetId = curr.preferences.activePresetId,
            )
        }
    }

    fun resumeSession(session: CullingSessionEntity) {
        val uris = runCatching {
            Json.decodeFromString<List<String>>(session.assetUrisJson)
        }.getOrDefault(emptyList())
        if (uris.isNotEmpty()) {
            loadAssetsIntoStudio(uris)
            runCatching { ComparisonMode.valueOf(session.comparisonModeName) }.getOrNull()?.let {
                setComparisonMode(it)
            }
        }
    }

    // --- Preferences Updates ---
    fun setLaunchScreen(screen: AppLaunchScreen) = viewModelScope.launch { prefsRepo.setLaunchScreen(screen) }
    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { prefsRepo.setDynamicColor(enabled) }
    fun setCanvasBackdrop(mode: CanvasBackdropMode) = viewModelScope.launch { prefsRepo.setCanvasBackdrop(mode) }
    fun setMaskGps(masked: Boolean) = viewModelScope.launch {
        prefsRepo.setMaskGps(masked)
        refreshPairAnalysisAndExif()
    }
    fun setVolumeKeyNavigation(enabled: Boolean) = viewModelScope.launch { prefsRepo.setVolumeKeyNavigation(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { prefsRepo.setKeepScreenOn(enabled) }
    fun setPhotosensitiveGuard(enabled: Boolean) = viewModelScope.launch { prefsRepo.setPhotosensitiveGuard(enabled) }
    fun setShowPixelGrid(enabled: Boolean) = viewModelScope.launch { prefsRepo.setShowPixelGrid(enabled) }
    fun setHudTemplate(template: String) = viewModelScope.launch { prefsRepo.setHudTemplate(template) }
    fun clearStatusMessage() { _state.update { it.copy(statusMessage = null) } }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
