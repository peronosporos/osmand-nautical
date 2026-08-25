package net.osmand.plus.plugins.nautical.grib.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.grib.parser.TimeStepGrid
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.grib.repository.GribStatus
import net.osmand.plus.plugins.nautical.map.layers.OceanographicGribMapLayer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Floating GRIB Time-Scrubber widget allowing live scrubbing of GRIB forecast hours
 * (+0h, +3h, +6h, +12h, +24h, etc.) with Play/Pause animation loop.
 */
class GribTimeScrubberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val playPauseButton: ImageView
    private val prevStepButton: ImageView
    private val nextStepButton: ImageView
    private val closeButton: ImageView
    private val timeSeekBar: SeekBar
    private val timeLabelView: TextView
    private val statusBadgeView: TextView

    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null
    private var isPlaying = false

    private var timeSteps: List<TimeStepGrid> = emptyList()
    private var currentStepIndex = 0
    private var baseTimestamp = 0L

    private val dateFormat = SimpleDateFormat("EEE HH:mm 'UTC'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        val density = context.resources.displayMetrics.density

        // Root container styling
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        val padH = (12f * density).toInt()
        val padV = (8f * density).toInt()

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                val marginH = (10f * density).toInt()
                val marginV = (4f * density).toInt()
                setMargins(marginH, marginV, marginH, marginV)
            }
            layoutParams = lp

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(0xEE121820.toInt()) // Deep dark marine card
                setStroke((1.5f * density).toInt(), 0xFF0288D1.toInt()) // Subtle ocean blue border
            }
            background = shape
            setPadding(padH, padV, padH, padV)
            elevation = 6f * density
        }

        // Top Row: [Status Badge] + [Forecast Timestamp] + [Close Button]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        statusBadgeView = TextView(context).apply {
            text = "GRIB FORECAST"
            setTextColor(0xFF00E5FF.toInt())
            textSize = 10.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val badgePadH = (6f * density).toInt()
            val badgePadV = (2f * density).toInt()
            setPadding(badgePadH, badgePadV, badgePadH, badgePadV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f * density
                setColor(0x3300E5FF.toInt())
            }
        }
        topRow.addView(statusBadgeView)

        timeLabelView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = (8f * density).toInt()
            }
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            text = "Loading forecast..."
        }
        topRow.addView(timeLabelView)

        closeButton = ImageView(context).apply {
            val size = (24f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                leftMargin = (6f * density).toInt()
            }
            setImageResource(R.drawable.ic_action_remove_dark)
            setColorFilter(0xCCFFFFFF.toInt())
            setOnClickListener {
                stopPlayback()
                resetToLive()
                visibility = View.GONE
            }
        }
        topRow.addView(closeButton)
        cardLayout.addView(topRow)

        // Bottom Controls Row: [Prev] [Play/Pause] [Next] [SeekBar]
        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6f * density).toInt()
            }
        }

        // Prev Step Button (<)
        prevStepButton = ImageView(context).apply {
            val size = (32f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = (4f * density).toInt()
            }
            setImageResource(R.drawable.ic_action_arrow_left)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                stopPlayback()
                stepRelative(-1)
            }
        }
        controlsRow.addView(prevStepButton)

        // Play/Pause Button
        playPauseButton = ImageView(context).apply {
            val size = (36f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = (4f * density).toInt()
            }
            setImageResource(R.drawable.ic_action_play_dark)
            setColorFilter(0xFF00E5FF.toInt())
            setOnClickListener {
                togglePlayback()
            }
        }
        controlsRow.addView(playPauseButton)

        // Next Step Button (>)
        nextStepButton = ImageView(context).apply {
            val size = (32f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = (8f * density).toInt()
            }
            setImageResource(R.drawable.ic_action_arrow_right)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                stopPlayback()
                stepRelative(1)
            }
        }
        controlsRow.addView(nextStepButton)

        // Time Slider / SeekBar
        timeSeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            max = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        stopPlayback()
                        applyStep(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        controlsRow.addView(timeSeekBar)

        cardLayout.addView(controlsRow)
        addView(cardLayout)

        refreshGribData()
    }

    fun refreshGribData() {
        val repo = SailingDependencyContainer.gribRepository ?: return
        val grid = repo.gridData
        if (grid != null && grid.timeSteps.isNotEmpty()) {
            timeSteps = grid.timeSteps.sortedBy { it.timestamp }
            baseTimestamp = timeSteps.first().timestamp
            timeSeekBar.max = (timeSteps.size - 1).coerceAtLeast(0)
            applyStep(currentStepIndex.coerceIn(0, timeSeekBar.max))
        } else {
            timeLabelView.text = "No GRIB steps available"
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshGribData()
    }

    override fun onDetachedFromWindow() {
        stopPlayback()
        viewScope.cancel()
        super.onDetachedFromWindow()
    }

    fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        if (timeSteps.isEmpty()) return
        isPlaying = true
        playPauseButton.setImageResource(R.drawable.ic_action_pause_dark)
        playbackJob?.cancel()
        playbackJob = viewScope.launch {
            while (isActive && isPlaying) {
                delay(1500L) // 1.5 seconds per forecast frame
                val nextIdx = (currentStepIndex + 1) % timeSteps.size
                applyStep(nextIdx)
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        playPauseButton.setImageResource(R.drawable.ic_action_play_dark)
    }

    fun stepRelative(offset: Int) {
        if (timeSteps.isEmpty()) return
        val newIdx = (currentStepIndex + offset).coerceIn(0, timeSteps.size - 1)
        applyStep(newIdx)
    }

    private fun applyStep(index: Int) {
        if (timeSteps.isEmpty()) return
        currentStepIndex = index.coerceIn(0, timeSteps.size - 1)
        timeSeekBar.progress = currentStepIndex

        val step = timeSteps[currentStepIndex]
        val stepTime = step.timestamp
        val offsetHours = ((stepTime - baseTimestamp) / 3600000L).toInt()

        val formattedDate = dateFormat.format(Date(stepTime))
        val offsetLabel = if (offsetHours >= 0) "+${offsetHours}h" else "${offsetHours}h"
        timeLabelView.text = "$formattedDate ($offsetLabel)"

        // Update GRIB map layer selected timestamp
        val plugin = NauticalPlugin.getInstance()
        plugin?.layerManager?.oceanographicGribMapLayer?.selectedTimestamp = stepTime
    }

    private fun resetToLive() {
        val plugin = NauticalPlugin.getInstance()
        plugin?.layerManager?.oceanographicGribMapLayer?.selectedTimestamp = null
    }
}
