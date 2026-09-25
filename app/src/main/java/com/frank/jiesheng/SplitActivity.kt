package com.frank.jiesheng

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.frank.jiesheng.databinding.ActivitySplitBinding
import com.frank.jiesheng.databinding.ItemSplitPointBinding
import kotlinx.coroutines.launch

class SplitActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplitBinding
    private val viewModel: SplitViewModel by viewModels()
    private val openSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readSource(applicationContext, uri)
    }

    private val openDestination = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            viewModel.cancelDestinationChoice()
        } else {
            viewModel.exportTo(applicationContext, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.pointTexts.forEach { addCutPointRow(it, persist = false) }
        binding.backButton.setOnClickListener { handleBack() }
        binding.chooseSplitSourceButton.setOnClickListener {
            openSource.launch(arrayOf("audio/*"))
        }
        binding.addCutPointButton.setOnClickListener { addCutPointRow() }
        binding.splitExportButton.setOnClickListener { requestDestination() }
        binding.cancelSplitButton.setOnClickListener { viewModel.stopExport() }
        onBackPressedDispatcher.addCallback(this) { handleBack() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
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

    private fun addCutPointRow(text: String = "", persist: Boolean = true) {
        if (binding.cutPointList.childCount >= MAX_CUT_POINTS) return
        val row = ItemSplitPointBinding.inflate(layoutInflater, binding.cutPointList, false)
        if (resources.configuration.fontScale > 1.2f) {
            row.root.orientation = android.widget.LinearLayout.VERTICAL
            row.cutPointNumberText.layoutParams.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            row.cutPointInput.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        row.cutPointInput.isSaveEnabled = false
        row.cutPointInput.setText(text)
        row.cutPointInput.doAfterTextChanged {
            row.cutPointInput.error = null
            viewModel.updatePoints(cutPointTexts())
            updateExportAvailability()
        }
        row.removeCutPointButton.setOnClickListener {
            if (binding.cutPointList.childCount > MIN_CUT_POINTS &&
                viewModel.state.value.areEditsEnabled
            ) {
                binding.cutPointList.removeView(row.root)
                viewModel.updatePoints(cutPointTexts())
                updateCutPointRows()
            }
        }
        binding.cutPointList.addView(row.root)
        if (persist) viewModel.updatePoints(cutPointTexts())
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
        binding.splitExportButton.isEnabled = state.isExportEnabled
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
        val busy = state.phase is SplitPhase.Splitting || state.phase == SplitPhase.Stopping
        if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.splitResultText.text = state.notice
        binding.splitResultText.visibility = if (state.notice == null) View.GONE else View.VISIBLE
        binding.cancelSplitButton.isEnabled = state.phase is SplitPhase.Splitting
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
        binding.backButton.isEnabled = !busy
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
            SplitPhase.Stopping -> showIndeterminateProgress(R.string.stopping_export)
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
            is SplitPhase.Splitting -> viewModel.stopExport()
            SplitPhase.Stopping -> Unit
            SplitPhase.ChoosingDestination -> viewModel.cancelDestinationChoice()
            else -> finish()
        }
    }

    private companion object {
        const val MIN_CUT_POINTS = 2
        const val MAX_CUT_POINTS = 20
    }
}
