package com.frank.jiesheng

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.frank.jiesheng.databinding.ActivityMainBinding
import com.frank.jiesheng.databinding.ItemAudioBinding
import java.time.ZoneId
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

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

    private val openVideos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
            viewModel.exportTo(applicationContext, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (resources.configuration.fontScale > 1.2f) {
            binding.sourceButtons.orientation = android.widget.LinearLayout.VERTICAL
            listOf(binding.musicLibraryButton, binding.galleryButton, binding.folderButton).forEach {
                it.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
        }

        binding.musicLibraryButton.setOnClickListener { openMusicLibrary() }
        binding.galleryButton.setOnClickListener { openVideos.launch(arrayOf("video/*")) }
        binding.folderButton.setOnClickListener { openAudio.launch(arrayOf("audio/*")) }
        binding.splitAudioButton.setOnClickListener {
            startActivity(Intent(this, SplitActivity::class.java))
        }
        binding.mergeButton.setOnClickListener {
            if (viewModel.startExport()) {
                createOutput.launch(viewModel.targetName)
            }
        }
        binding.cancelButton.setOnClickListener { viewModel.stopExport() }
        onBackPressedDispatcher.addCallback(this@MainActivity) {
            when (viewModel.state.value.phase) {
                is MergePhase.Merging -> viewModel.stopExport()
                MergePhase.Stopping -> Unit
                else -> finish()
            }
        }

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
        viewModel.readDocuments(applicationContext, uris, sourceType)
    }

    private fun render(state: MainUiState) {
        val busy = state.phase is MergePhase.Merging || state.phase == MergePhase.Stopping
        if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.resultText.text = state.notice
        binding.resultText.visibility = if (state.notice == null) View.GONE else View.VISIBLE
        binding.splitAudioButton.isEnabled = state.areSourcesEnabled
        binding.cancelButton.isEnabled = state.phase is MergePhase.Merging
        val editable = state.areQueueEditsEnabled
        binding.emptyState.visibility = if (state.queue.items.isEmpty()) View.VISIBLE else View.GONE
        binding.bindSourceAvailability(state)
        binding.mergeButton.isEnabled = state.isMergeEnabled
        binding.audioList.removeAllViews()
        state.queue.items.forEachIndexed { index, audio ->
            val row = ItemAudioBinding.inflate(layoutInflater, binding.audioList, false)
            row.orderText.text = getString(R.string.order_number, index + 1)
            row.bindMetadata(audio, ZoneId.systemDefault())
            row.moveUpButton.isEnabled = editable && index > 0
            row.moveDownButton.isEnabled = editable && index < state.queue.items.lastIndex
            row.removeButton.isEnabled = editable
            row.moveUpButton.setOnClickListener { viewModel.moveUp(index) }
            row.moveDownButton.setOnClickListener { viewModel.moveDown(index) }
            row.removeButton.setOnClickListener { viewModel.remove(audio.uri) }
            binding.audioList.addView(row.root)
        }

        when (val phase = state.phase) {
            MergePhase.Idle -> binding.progressGroup.visibility = View.GONE
            MergePhase.ReadingSources -> {
                binding.progressGroup.visibility = View.VISIBLE
                binding.statusText.setText(R.string.reading_sources)
                binding.progressBar.isIndeterminate = true
                binding.cancelButton.visibility = View.GONE
            }
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
            MergePhase.Stopping -> {
                binding.progressGroup.visibility = View.VISIBLE
                binding.statusText.setText(R.string.stopping_export)
                binding.progressBar.isIndeterminate = true
                binding.cancelButton.visibility = View.GONE
            }
            is MergePhase.Completed -> binding.progressGroup.visibility = View.GONE
            is MergePhase.Failed -> binding.progressGroup.visibility = View.GONE
        }
    }

    private companion object {
        const val MAX_ITEMS = 20
    }
}

internal fun ActivityMainBinding.bindSourceAvailability(state: MainUiState) {
    musicLibraryButton.isEnabled = state.areSourcesEnabled
    galleryButton.isEnabled = state.areSourcesEnabled
    folderButton.isEnabled = state.areSourcesEnabled
}

internal fun ItemAudioBinding.bindMetadata(item: SelectedAudio, zoneId: ZoneId) {
    nameText.text = item.name
    detailText.text = AudioText.detail(item)
    modifiedText.text = AudioText.modified(item.lastModifiedEpochMs, zoneId)
}
