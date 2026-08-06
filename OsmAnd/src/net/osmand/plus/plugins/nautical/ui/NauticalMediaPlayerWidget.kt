package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState

class NauticalMediaPlayerWidget @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val titleTxt: TextView
    private val artistTxt: TextView
    private val prevBtn: ImageButton
    private val playBtn: ImageButton
    private val nextBtn: ImageButton

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.nautical_media_hud, this, true)
        titleTxt = findViewById(R.id.media_title)
        artistTxt = findViewById(R.id.media_artist)
        prevBtn = findViewById(R.id.media_prev)
        playBtn = findViewById(R.id.media_play)
        nextBtn = findViewById(R.id.media_next)
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        
        playBtn.setOnClickListener { dispatchMedia("TOGGLE") }
        prevBtn.setOnClickListener { dispatchMedia("PREV") }
        nextBtn.setOnClickListener { dispatchMedia("NEXT") }
    }

    private fun dispatchMedia(cmd: String) {
        val plugin = NauticalPlugin.getInstance() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value
        
        if (caps?.hasFusionStereo == true) {
            val control = NauticalPlugin.engine?.controlManager
            when (cmd) {
                "TOGGLE" -> {
                    val state = NauticalPlugin.engine?.getCurrentState()
                    val next = if (state?.mediaInfo?.playbackState == "playing") "PAUSE" else "PLAY"
                    control?.sendMediaCommand(next)
                }
                "PREV" -> control?.sendMediaCommand("SKIP_PREV")
                "NEXT" -> control?.sendMediaCommand("SKIP_NEXT")
            }
        } else {
            NauticalPlugin.engine?.dispatchCommand("MEDIA:$cmd")
        }
    }

    fun updateState(state: MarineState) {
        val info = state.mediaInfo
        if (info != null) {
            titleTxt.text = info.title ?: "Unknown Title"
            artistTxt.text = info.artist ?: "Unknown Artist"
            
            val icon = if (info.playbackState == "playing") R.drawable.ic_pause else R.drawable.ic_play_dark
            playBtn.setImageResource(icon)
        } else {
            titleTxt.text = context.getString(R.string.nautical_no_media)
            artistTxt.text = ""
        }
    }

    override fun setCompactMode(enabled: Boolean) { }
    override fun isEmergency(): Boolean = false
}
