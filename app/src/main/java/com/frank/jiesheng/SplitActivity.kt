package com.frank.jiesheng

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.frank.jiesheng.databinding.ActivitySplitBinding
import com.frank.jiesheng.databinding.ItemSplitPointBinding
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplitActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplitBinding
    private val viewModel: SplitViewModel by viewModels()
    private lateinit var metadataReader: DocumentMetadataReader
    private lateinit var splitEngine: AudioSplitEngine
    private var temporaryOutput: File? = null
    private var destinationTree: Uri? = null
    private var sourceUri: Uri? = null
    private var exportSegments: List<SplitSegment> = emptyList()
    private var exportNames: List<String> = emptyList()
    private var currentSegmentIndex = 0
    private val createdDocuments = mutableListOf<Uri>()
    private var copyJob: kotlinx.coroutines.Job? = null
    private var sessionToken = 0L
    private var sessionStopping = false

    private val openSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readSource(uri)
    }

    private val openDestination = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            viewModel.cancelDestinationChoice()
        } else {
            beginSplit(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        metadataReader = DocumentMetadataReader(applicationContext)
        splitEngine = Media3AudioSplitEngine(applicationContext)

        repeat(MIN_CUT_POINTS) { addCutPointRow() }
        binding.backButton.setOnClickListener { handleBack() }
        binding.chooseSplitSourceButton.setOnClickListener {
            openSource.launch(arrayOf("audio/*"))
        }
        binding.addCutPointButton.setOnClickListener { addCutPointRow() }
        binding.splitExportButton.setOnClickListener { requestDestination() }
        binding.cancelSplitButton.setOnClickListener { stopExport(reason = null) }
        onBackPressedDispatcher.addCallback(this) { handleBack() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            splitEngine.cancel()
            temporaryOutput?.delete()
        }
        super.onDestroy()
    }

    private fun readSource(uri: Uri) {
        if (!viewModel.beginSourceReading()) return
        lifecycleScope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    metadataReader.read(uri, SourceType.AUDIO)
                }
                viewModel.finishSourceReading(source)
            } catch (error: Exception) {
                val reason = error.message ?: getString(R.string.read_failed)
                viewModel.failSourceReading(reason)
                Toast.makeText(this@SplitActivity, reason, Toast.LENGTH_LONG).show()
                viewModel.reset()
            }
        }
    }

    private fun requestDestination() {
        clearFieldErrors()
        when (val result = viewModel.startExport(cutPointTexts())) {
            is SplitPlanResult.Valid -> openDestination.launch(null)
            is SplitPlanResult.Invalid -> showValidation(result)
        }
    }

    private fun beginSplit(treeUri: Uri) {
        val state = viewModel.state.value
        val source = state.source ?: run {
            viewModel.cancelDestinationChoice()
            return
        }
        if (!viewModel.beginSplit()) return

        sessionToken += 1
        sessionStopping = false
        destinationTree = treeUri
        sourceUri = source.uri.toUri()
        exportSegments = state.segments
        exportNames = SplitExportNames.forSource(source.name, exportSegments.size)
        currentSegmentIndex = 0
        synchronized(createdDocuments) { createdDocuments.clear() }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exportNextSegment(sessionToken)
    }

    private fun exportNextSegment(token: Long) {
        if (token != sessionToken || sessionStopping) return
        if (currentSegmentIndex >= exportSegments.size) {
            completeExport(token)
            return
        }
        val input = sourceUri ?: return stopExport(getString(R.string.read_failed))
        val output = File(cacheDir, "jiesheng-split-$token-$currentSegmentIndex.m4a").apply { delete() }
        temporaryOutput = output
        splitEngine.split(
            input,
            exportSegments[currentSegmentIndex],
            output,
            object : SplitListener {
                override fun onProgress(percent: Int) {
                    if (token == sessionToken && !sessionStopping) {
                        viewModel.updateProgress(
                            completedSegments = currentSegmentIndex,
                            totalSegments = exportSegments.size,
                            currentPercent = percent,
                        )
                    }
                }

                override fun onCompleted() {
                    copyCurrentSegment(token)
                }

                override fun onError(error: Throwable) {
                    stopExport(error.message ?: getString(R.string.read_failed))
                }
            },
        )
    }

    private fun copyCurrentSegment(token: Long) {
        val source = temporaryOutput ?: return stopExport(getString(R.string.split_write_failed))
        val name = exportNames.getOrNull(currentSegmentIndex)
            ?: return stopExport(getString(R.string.split_write_failed))
        copyJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val destination = createOutputDocument(name)
                    synchronized(createdDocuments) { createdDocuments += destination }
                    contentResolver.openOutputStream(destination, "w").use { output ->
                        if (output == null) throw IOException(getString(R.string.split_write_failed))
                        source.inputStream().use { input -> input.copyTo(output) }
                    }
                }
                if (token != sessionToken || sessionStopping) return@launch
                source.delete()
                temporaryOutput = null
                currentSegmentIndex += 1
                viewModel.updateProgress(
                    completedSegments = currentSegmentIndex,
                    totalSegments = exportSegments.size,
                    currentPercent = 0,
                )
                copyJob = null
                exportNextSegment(token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                copyJob = null
                if (token == sessionToken && !sessionStopping) {
                    stopExport(error.message ?: getString(R.string.split_write_failed))
                }
            }
        }
    }

    private fun createOutputDocument(name: String): Uri {
        val tree = destinationTree ?: throw IOException(getString(R.string.split_create_failed))
        val root = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        return DocumentsContract.createDocument(contentResolver, root, "audio/mp4", name)
            ?: throw IOException(getString(R.string.split_create_failed))
    }

    private fun completeExport(token: Long) {
        if (token != sessionToken || sessionStopping) return
        temporaryOutput?.delete()
        temporaryOutput = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val count = exportSegments.size
        synchronized(createdDocuments) { createdDocuments.clear() }
        clearExportSession()
        viewModel.finishSplit(count)
        Toast.makeText(this, getString(R.string.split_complete, count), Toast.LENGTH_LONG).show()
        viewModel.reset()
    }

    private fun stopExport(reason: String?) {
        if (sessionStopping) return
        sessionStopping = true
        sessionToken += 1
        splitEngine.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val activeCopy = copyJob
        copyJob = null
        activeCopy?.cancel()
        lifecycleScope.launch {
            activeCopy?.join()
            temporaryOutput?.delete()
            temporaryOutput = null
            val retainedCount = withContext(Dispatchers.IO) { rollbackCreatedDocuments() }
            clearExportSession()
            if (reason == null) {
                viewModel.reset()
            } else {
                val detail = if (retainedCount == 0) {
                    reason
                } else {
                    getString(R.string.split_retained_files, reason, retainedCount)
                }
                viewModel.failSplit(detail)
                Toast.makeText(
                    this@SplitActivity,
                    getString(R.string.split_failed, detail),
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.reset()
            }
            sessionStopping = false
        }
    }

    private fun rollbackCreatedDocuments(): Int {
        val documents = synchronized(createdDocuments) {
            createdDocuments.toList().also { createdDocuments.clear() }
        }
        return documents.count { uri ->
            try {
                !DocumentsContract.deleteDocument(contentResolver, uri)
            } catch (_: Exception) {
                true
            }
        }
    }

    private fun clearExportSession() {
        destinationTree = null
        sourceUri = null
        exportSegments = emptyList()
        exportNames = emptyList()
        currentSegmentIndex = 0
    }

    private fun addCutPointRow() {
        if (binding.cutPointList.childCount >= MAX_CUT_POINTS) return
        val row = ItemSplitPointBinding.inflate(layoutInflater, binding.cutPointList, false)
        row.cutPointInput.doAfterTextChanged {
            row.cutPointInput.error = null
            updateExportAvailability()
        }
        row.removeCutPointButton.setOnClickListener {
            if (binding.cutPointList.childCount > MIN_CUT_POINTS &&
                viewModel.state.value.areEditsEnabled
            ) {
                binding.cutPointList.removeView(row.root)
                updateCutPointRows()
            }
        }
        binding.cutPointList.addView(row.root)
        updateCutPointRows()
    }

    private fun updateCutPointRows() {
        val editable = viewModel.state.value.areEditsEnabled
        val count = binding.cutPointList.childCount
        repeat(count) { index ->
            val row = ItemSplitPointBinding.bind(binding.cutPointList.getChildAt(index))
            row.cutPointNumberText.text = getString(R.string.cut_point_number, index + 1)
            row.cutPointInput.isEnabled = editable
            row.removeCutPointButton.isEnabled = editable && count > MIN_CUT_POINTS
            row.removeCutPointButton.visibility = if (count > MIN_CUT_POINTS) View.VISIBLE else View.INVISIBLE
        }
        binding.addCutPointButton.isEnabled = editable && count < MAX_CUT_POINTS
        updateExportAvailability()
    }

    private fun cutPointTexts(): List<String> =
        (0 until binding.cutPointList.childCount).map { index ->
            ItemSplitPointBinding.bind(binding.cutPointList.getChildAt(index))
                .cutPointInput.text.toString().trim()
        }

    private fun updateExportAvailability() {
        if (!::binding.isInitialized) return
        val state = viewModel.state.value
        binding.splitExportButton.isEnabled = state.isExportEnabled &&
            state.source?.let { SplitPlan.create(it.durationMs, cutPointTexts()) is SplitPlanResult.Valid } == true
    }

    private fun clearFieldErrors() {
        repeat(binding.cutPointList.childCount) { index ->
            ItemSplitPointBinding.bind(binding.cutPointList.getChildAt(index)).cutPointInput.error = null
        }
    }

    private fun showValidation(result: SplitPlanResult.Invalid) {
        result.fieldErrors.forEach { (index, message) ->
            val row = binding.cutPointList.getChildAt(index) ?: return@forEach
            ItemSplitPointBinding.bind(row).cutPointInput.error = message
        }
        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
    }

    private fun render(state: SplitUiState) {
        val source = state.source
        binding.splitSourceNameText.text = source?.name ?: getString(R.string.split_source_empty)
        binding.splitSourceDurationText.visibility = if (source == null) View.GONE else View.VISIBLE
        binding.splitSourceDurationText.text = source?.let {
            getString(R.string.split_source_duration, DurationText.format(it.durationMs))
        }.orEmpty()
        binding.chooseSplitSourceButton.text = if (source == null) {
            getString(R.string.choose_split_source)
        } else {
            getString(R.string.change_split_source)
        }
        binding.chooseSplitSourceButton.isEnabled = state.areEditsEnabled
        binding.backButton.isEnabled = state.phase !is SplitPhase.Splitting
        updateCutPointRows()

        when (val phase = state.phase) {
            SplitPhase.Idle -> binding.splitProgressGroup.visibility = View.GONE
            SplitPhase.ReadingSource -> showIndeterminateProgress(R.string.reading_split_source)
            SplitPhase.ChoosingDestination -> showIndeterminateProgress(R.string.choosing_split_folder)
            is SplitPhase.Splitting -> {
                binding.splitProgressGroup.visibility = View.VISIBLE
                binding.splitStatusText.text = getString(R.string.splitting_progress, phase.progress)
                binding.splitProgressBar.isIndeterminate = false
                binding.splitProgressBar.progress = phase.progress
                binding.cancelSplitButton.visibility = View.VISIBLE
            }
            is SplitPhase.Completed -> binding.splitProgressGroup.visibility = View.GONE
            is SplitPhase.Failed -> binding.splitProgressGroup.visibility = View.GONE
        }
    }

    private fun showIndeterminateProgress(status: Int) {
        binding.splitProgressGroup.visibility = View.VISIBLE
        binding.splitStatusText.setText(status)
        binding.splitProgressBar.isIndeterminate = true
        binding.cancelSplitButton.visibility = View.GONE
    }

    private fun handleBack() {
        when (viewModel.state.value.phase) {
            is SplitPhase.Splitting -> stopExport(reason = null)
            SplitPhase.ChoosingDestination -> viewModel.cancelDestinationChoice()
            else -> finish()
        }
    }

    private companion object {
        const val MIN_CUT_POINTS = 2
        const val MAX_CUT_POINTS = 20
    }
}
