package com.frank.jiesheng

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.frank.jiesheng.databinding.ActivityMediaLibraryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaLibraryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaLibraryBinding
    private var folders: List<AudioFolder> = emptyList()
    private var currentFolder: AudioFolder? = null
    private val selectedUris = linkedSetOf<String>()
    private val remainingCapacity by lazy {
        intent.getIntExtra(EXTRA_REMAINING_CAPACITY, DEFAULT_CAPACITY).coerceAtLeast(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.confirmButton.setOnClickListener { confirmSelection() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentFolder == null) finish() else showFolders()
                }
            },
        )

        lifecycleScope.launch {
            folders = withContext(Dispatchers.IO) {
                MediaLibraryRepository(contentResolver).load()
            }
            showFolders()
        }
    }

    private fun showFolders() {
        currentFolder = null
        binding.titleText.setText(R.string.music_library)
        binding.confirmButton.visibility = View.GONE
        binding.mediaList.choiceMode = android.widget.ListView.CHOICE_MODE_NONE
        binding.mediaList.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            folders.map { folder ->
                getString(
                    R.string.media_folder_summary,
                    folder.name,
                    folder.path,
                    folder.items.size,
                )
            },
        )
        binding.mediaList.setOnItemClickListener { _, _, position, _ ->
            showFolder(folders[position])
        }
    }

    private fun showFolder(folder: AudioFolder) {
        currentFolder = folder
        binding.titleText.text = folder.name
        binding.confirmButton.visibility = View.VISIBLE
        binding.mediaList.choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
        binding.mediaList.adapter = ArrayAdapter(
            this,
            R.layout.item_media_library,
            R.id.mediaItemText,
            folder.items.map { it.name },
        )
        folder.items.forEachIndexed { index, item ->
            binding.mediaList.setItemChecked(index, item.uri in selectedUris)
        }
        binding.mediaList.setOnItemClickListener { _, _, position, _ ->
            val item = folder.items[position]
            if (item.uri in selectedUris) {
                selectedUris.remove(item.uri)
            } else if (selectedUris.size < remainingCapacity) {
                selectedUris.add(item.uri)
            } else {
                binding.mediaList.setItemChecked(position, false)
                Toast.makeText(
                    this,
                    getString(R.string.media_selection_limit, remainingCapacity),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun confirmSelection() {
        setResult(
            Activity.RESULT_OK,
            Intent().putStringArrayListExtra(
                EXTRA_SELECTED_MEDIA_URIS,
                ArrayList(selectedUris),
            ),
        )
        finish()
    }

    companion object {
        const val EXTRA_REMAINING_CAPACITY = "remaining_capacity"
        const val EXTRA_SELECTED_MEDIA_URIS = "selected_media_uris"
        private const val DEFAULT_CAPACITY = 20
    }
}
