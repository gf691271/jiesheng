package com.frank.jiesheng

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.frank.jiesheng.databinding.ActivityMainBinding
import com.frank.jiesheng.databinding.ItemAudioBinding
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var metadataReader: DocumentMetadataReader
    private lateinit var mergeEngine: AudioMergeEngine
    private var temporaryOutput: File? = null
    private var targetUri: Uri? = null
    private var targetName: String = ""

    private val requestMusicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchMusicLibrary()
        } else {
            Toast.makeText(this, R.string.music_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val musicLibraryResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = result.data
                ?.getStringArrayListExtra(MediaLibraryActivity.EXTRA_SELECTED_MEDIA_URIS)
                .orEmpty()
                .map(Uri::parse)
            readSelectedDocuments(uris, SourceType.AUDIO)
        }
    }

    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS),
    ) { uris ->
        readSelectedDocuments(uris, SourceType.VIDEO)
    }

    private val openAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        uris -> readSelectedDocuments(uris, SourceType.AUDIO)
    }

    private val createOutput = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4"),
    ) { uri ->
        if (uri == null) {
            viewModel.cancelExport()
        } else {
            beginMerge(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        metadataReader = DocumentMetadataReader(applicationContext)
        mergeEngine = Media3AudioMergeEngine(applicationContext)

        binding.musicLibraryButton.setOnClickListener { openMusicLibrary() }
        binding.galleryButton.setOnClickListener {
            pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.folderButton.setOnClickListener { openAudio.launch(arrayOf("audio/*")) }
        binding.mergeButton.setOnClickListener {
            viewModel.startExport()
            targetName = ExportNames.m4a(Instant.now(), ZoneId.systemDefault())
            createOutput.launch(targetName)
        }
        binding.cancelButton.setOnClickListener { cancelMerge() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collectLatest(::render) }
                launch {
                    viewModel.messages.collectLatest { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing && viewModel.state.value.phase is MergePhase.Merging) cancelMerge()
        super.onDestroy()
    }

    private fun openMusicLibrary() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            launchMusicLibrary()
        } else {
            requestMusicPermission.launch(permission)
        }
    }

    private fun launchMusicLibrary() {
        musicLibraryResult.launch(
            Intent(this, MediaLibraryActivity::class.java).putExtra(
                MediaLibraryActivity.EXTRA_REMAINING_CAPACITY,
                MAX_ITEMS - viewModel.state.value.queue.items.size,
            ),
        )
    }

    private fun readSelectedDocuments(uris: List<Uri>, sourceType: SourceType) {
        if (uris.isEmpty()) return
        val existing = viewModel.state.value.queue.items.map { it.uri }.toSet()
        val newUris = uris.distinctBy(Uri::toString).filterNot { it.toString() in existing }
        if (existing.size + newUris.size > MAX_ITEMS) {
            Toast.makeText(this, R.string.limit_message, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val selected = withContext(Dispatchers.IO) {
                    newUris.map { metadataReader.read(it, sourceType) }
                }
                viewModel.addAll(selected)
            } catch (_: NoAudioTrackException) {
                Toast.makeText(this@MainActivity, R.string.video_has_no_audio, Toast.LENGTH_LONG)
                    .show()
            } catch (_: UnreadableAudioException) {
                Toast.makeText(this@MainActivity, R.string.read_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun beginMerge(destination: Uri) {
        targetUri = destination
        temporaryOutput = File(cacheDir, "jiesheng-${System.nanoTime()}.m4a")
        viewModel.beginMerge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mergeEngine.merge(
            viewModel.state.value.queue.items.map { it.uri.toUri() },
            temporaryOutput!!,
            object : MergeListener {
                override fun onProgress(percent: Int) {
                    viewModel.updateProgress(percent)
                }

                override fun onCompleted() {
                    copyMergedFile()
                }

                override fun onError(error: Throwable) {
                    failMerge(error.message ?: getString(R.string.read_failed))
                }
            },
        )
    }

    private fun copyMergedFile() {
        val source = temporaryOutput ?: return
        val destination = targetUri ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(destination, "w").use { output ->
                        requireNotNull(output) { "Output stream unavailable" }
                        source.inputStream().use { input -> input.copyTo(output) }
                    }
                }
                viewModel.finishExport(targetName)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.export_complete, targetName),
                    Toast.LENGTH_LONG,
                ).show()
                clearMergeSession()
                viewModel.cancelExport()
            } catch (_: Exception) {
                failMerge(getString(R.string.write_failed))
            }
        }
    }

    private fun failMerge(reason: String) {
        deleteTargetDocument()
        viewModel.failExport(reason)
        Toast.makeText(
            this,
            getString(R.string.export_failed, reason),
            Toast.LENGTH_LONG,
        ).show()
        clearMergeSession()
        viewModel.cancelExport()
    }

    private fun cancelMerge() {
        mergeEngine.cancel()
        deleteTargetDocument()
        clearMergeSession()
        viewModel.cancelExport()
    }

    private fun clearMergeSession() {
        temporaryOutput?.delete()
        temporaryOutput = null
        targetUri = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun deleteTargetDocument() {
        val uri = targetUri ?: return
        try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (_: Exception) {
            // Some document providers do not support deletion.
        }
    }

    private fun render(state: MainUiState) {
        val idle = state.phase == MergePhase.Idle
        binding.emptyState.visibility = if (state.queue.items.isEmpty()) View.VISIBLE else View.GONE
        val sourcesEnabled = idle && state.queue.items.size < MAX_ITEMS
        binding.musicLibraryButton.isEnabled = sourcesEnabled
        binding.galleryButton.isEnabled = sourcesEnabled
        binding.folderButton.isEnabled = sourcesEnabled
        binding.mergeButton.isEnabled = state.isMergeEnabled
        binding.audioList.removeAllViews()
        state.queue.items.forEachIndexed { index, audio ->
            val row = ItemAudioBinding.inflate(layoutInflater, binding.audioList, false)
            row.orderText.text = getString(R.string.order_number, index + 1)
            row.nameText.text = audio.name
            row.detailText.text = AudioText.detail(audio)
            row.modifiedText.text = AudioText.modified(audio.lastModifiedEpochMs, ZoneId.systemDefault())
            row.moveUpButton.isEnabled = idle && index > 0
            row.moveDownButton.isEnabled = idle && index < state.queue.items.lastIndex
            row.removeButton.isEnabled = idle
            row.moveUpButton.setOnClickListener { viewModel.moveUp(index) }
            row.moveDownButton.setOnClickListener { viewModel.moveDown(index) }
            row.removeButton.setOnClickListener { viewModel.remove(audio.uri) }
            binding.audioList.addView(row.root)
        }

        when (val phase = state.phase) {
            MergePhase.Idle -> binding.progressGroup.visibility = View.GONE
            MergePhase.ChoosingDestination -> {
                binding.progressGroup.visibility = View.VISIBLE
                binding.statusText.setText(R.string.choosing_destination)
                binding.progressBar.isIndeterminate = true
                binding.cancelButton.visibility = View.GONE
            }
            is MergePhase.Merging -> {
                binding.progressGroup.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.merging_progress, phase.progress)
                binding.progressBar.isIndeterminate = false
                binding.progressBar.progress = phase.progress
                binding.cancelButton.visibility = View.VISIBLE
            }
            is MergePhase.Completed -> binding.progressGroup.visibility = View.GONE
            is MergePhase.Failed -> binding.progressGroup.visibility = View.GONE
        }
    }

    private companion object {
        const val MAX_ITEMS = 20
    }
}
