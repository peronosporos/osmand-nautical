package net.osmand.plus.plugins.nautical.hazard.ui

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexUiState

class NavtexHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var urgentMessage: NavtexMessage? = null
    private var onMessageClick: ((NavtexMessage) -> Unit)? = null
    private val log = PlatformUtil.getLog(NavtexHudView::class.java)

    init {
        LayoutInflater.from(context).inflate(R.layout.navtex_urgent_hud, this, true)
        setupListeners()
        isVisible = false
    }

    private fun setupListeners() {
        findViewById<android.view.View>(R.id.navtex_hud_container).setOnClickListener {
            urgentMessage?.let { onMessageClick?.invoke(it) }
        }
    }

    fun setOnMessageClickListener(listener: (NavtexMessage) -> Unit) {
        this.onMessageClick = listener
    }

    fun updateState(state: NavtexUiState) {
        // Find the first urgent message to display in HUD
        val urgent = state.messages.firstOrNull { it.isUrgent }
        
        // Trigger alert only if it's a new urgent message
        if (urgent != null && (urgentMessage == null || urgent.id != urgentMessage?.id)) {
            triggerAlert()
        }

        this.urgentMessage = urgent
        isVisible = urgent != null
    }

    private fun triggerAlert() {
        try {
            // Play notification sound
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context, notification)
            r?.play()

            // Vibrate for 500ms
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(500)
            }
        } catch (e: Exception) {
            log.error("NAVTEX HUD alert error: ${e.message}", e)
            isVisible = false
            urgentMessage = null
        }
    }
}
