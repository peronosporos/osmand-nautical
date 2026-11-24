package net.osmand.plus.plugins.nautical.ui.cockpit

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * The Cockpit shell: a single full-screen surface that mounts one cockpit pane at a
 * time (Helmsman instruments today; Helm/Nav/VHF/Watchkeeper panes migrate here from
 * the HUD view system step by step — see docs/nautical/ARCHITECTURE.md).
 *
 * The shell owns: screen-on behaviour, the pane selector, pane lifecycle bracketing
 * [CockpitScreen.onStart]/[CockpitScreen.onStop], emergency visibility (an active MOB
 * always takes over the top strip with maximum contrast), and exit handling.
 */
class CockpitShellActivity : AppCompatActivity() {

    private val app: OsmandApplication get() = application as OsmandApplication

    private lateinit var registry: CockpitScreenRegistry
    private lateinit var paneHost: FrameLayout
    private lateinit var mobBanner: TextView

    private var currentPane: CockpitScreen? = null
    private var mobBannerShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        registry = CockpitScreenRegistry.createDefault(app)

        val night = NauticalPlugin.isNightVision(app)
        val bg = if (night) 0xFF101010.toInt() else 0xFFF5F5F5.toInt()
        val fg = if (night) 0xFFDDDDDD.toInt() else 0xFF202020.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        // MOB emergency strip: hidden by default, shown with maximum contrast on isMobActive.
        mobBanner = TextView(this).apply {
            setText(R.string.nautical_cockpit_mob_banner)
            setTextColor(Color.BLACK)
            setBackgroundColor(0xFFFF1744.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            val h = (14 * resources.displayMetrics.density).toInt()
            setPadding(h / 2, h / 2, h / 2, h / 2)
            visibility = android.view.View.GONE
        }
        root.addView(
            mobBanner,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        paneHost = FrameLayout(this)
        root.addView(
            paneHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // Pane selector: one button per registered concern, declaration order.
        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        val available = registry.available()
        available.forEach { id ->
            val button = Button(this).apply {
                text = paneName(id)
                setTextColor(fg)
                setBackgroundColor(if (night) 0xFF303030.toInt() else 0xFFDDDDDD.toInt())
                isAllCaps = false
                setOnClickListener { mount(id) }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val m = (6 * resources.displayMetrics.density).toInt()
            lp.setMargins(m, m, m, m)
            selector.addView(button, lp)
        }
        root.addView(
            selector,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        // Emergency observation: MOB must take over the cockpit regardless of active pane.
        lifecycleScope.launch {
            val engine = NauticalPlugin.engine
            engine?.marineStateFlow?.collect { state ->
                if (state.isMobActive) showMobBanner() else hideMobBanner()
            }
        }

        if (available.isNotEmpty()) {
            mount(available.first())
        }
    }

    override fun onDestroy() {
        currentPane?.onStop()
        currentPane = null
        super.onDestroy()
    }

    private fun mount(id: CockpitScreenId) {
        val pane = registry.pane(id) ?: return
        currentPane?.onStop()
        currentPane = null
        paneHost.removeAllViews()
        val view = pane.onCreateView(this, paneHost)
        paneHost.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        currentPane = pane
        pane.onStart()
    }

    private fun paneName(id: CockpitScreenId): String = when (id) {
        CockpitScreenId.INSTRUMENTS -> app.getString(R.string.nautical_cockpit_pane_instruments)
        CockpitScreenId.HELM -> app.getString(R.string.nautical_cockpit_pane_helm)
        CockpitScreenId.NAV -> app.getString(R.string.nautical_cockpit_pane_nav)
        CockpitScreenId.VHF -> app.getString(R.string.nautical_cockpit_pane_vhf)
        CockpitScreenId.WATCHKEEPER -> app.getString(R.string.nautical_cockpit_pane_watchkeeper)
        CockpitScreenId.EMERGENCY -> app.getString(R.string.nautical_cockpit_pane_emergency)
    }

    private fun showMobBanner() {
        if (!mobBannerShown) {
            mobBannerShown = true
            mobBanner.visibility = android.view.View.VISIBLE
        }
    }

    private fun hideMobBanner() {
        if (mobBannerShown) {
            mobBannerShown = false
            mobBanner.visibility = android.view.View.GONE
        }
    }
}
