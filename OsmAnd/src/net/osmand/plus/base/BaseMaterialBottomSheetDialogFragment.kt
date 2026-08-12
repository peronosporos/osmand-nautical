package net.osmand.plus.base

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.dialog.IOsmAndFragment
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.settings.enums.ThemeUsageContext
import net.osmand.plus.plugins.nautical.NauticalPlugin
import android.graphics.Paint
import net.osmand.plus.utils.InsetsUtils
import net.osmand.plus.utils.UiUtilities

open class BaseMaterialBottomSheetDialogFragment :
    BottomSheetDialogFragment(), IOsmAndFragment, ISupportInsets {

    private lateinit var _app: OsmandApplication
    @get:JvmName("getOsmandApp")
    protected var app: OsmandApplication
        get() = _app
        set(value) { _app = value }

    protected lateinit var settings: OsmandSettings

    private lateinit var _appMode: ApplicationMode
    @get:JvmName("getOsmandAppMode")
    @set:JvmName("setOsmandAppMode")
    protected var appMode: ApplicationMode
        get() = _appMode
        set(value) { _appMode = value }

    private lateinit var _iconsCache: UiUtilities
    @get:JvmName("getOsmandIconsCache")
    protected var iconsCache: UiUtilities
        get() = _iconsCache
        set(value) { _iconsCache = value }

    protected var nightMode: Boolean = false

    private var lastRootInsets: WindowInsetsCompat? = null

    override fun getTheme(): Int =
        if (nightMode) R.style.OsmandMaterialDarkTheme else R.style.OsmandMaterialLightTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _app = requireActivity().application as OsmandApplication
        settings = _app.getSettings()
        _iconsCache = _app.uiUtilities
        _appMode = restoreAppMode(_app, null, savedInstanceState, arguments)
        updateNightMode()
    }

    override fun getApp(): OsmandApplication = _app
    override fun getAppMode(): ApplicationMode = _appMode
    override fun setAppMode(appMode: ApplicationMode) { _appMode = appMode }
    override fun getIconsCache(): UiUtilities = _iconsCache

    protected fun updateNightMode() {
        nightMode = resolveNightMode()
    }

    override fun onStart() {
        super.onStart()

        val dialog = getDialog()
        if (dialog != null && dialog.window != null && InsetsUtils.isEdgeToEdgeSupported()) {
            dialog.window!!.setNavigationBarContrastEnforced(false)
            InsetsUtils.processNavigationBarColor(this, dialog)

            if (Build.VERSION.SDK_INT >= 36) {
                //WindowCompat.enableEdgeToEdge(window);
            } else {
                WindowCompat.setDecorFitsSystemWindows(dialog.window!!, false)
            }
        }
        
        // ITEM 2: Extend night vision coverage to BottomSheets (Bug #2)
        if (dialog != null && dialog.window != null && NauticalPlugin.isNightVision(app)) {
            val decorView = dialog.window!!.decorView
            val paint = Paint().apply {
                colorFilter = NauticalPlugin.NIGHT_VISION_FILTER
            }
            decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dialog = getDialog()
        if (dialog != null && dialog.window != null && InsetsUtils.isEdgeToEdgeSupported()) {
            InsetsUtils.processInsets(this, dialog.window!!.decorView, view)
            dialog.window!!.setNavigationBarContrastEnforced(false)
        } else {
            InsetsUtils.processInsets(this, view, null)
        }
    }

    override fun getThemedInflater(): LayoutInflater {
        return layoutInflater
    }

    override fun getThemeUsageContext(): ThemeUsageContext {
        return ThemeUsageContext.valueOf(isUsedOnMap())
    }

    protected open fun isUsedOnMap(): Boolean {
        return false
    }

    override fun onApplyInsets(insets: WindowInsetsCompat) {
    }

    override fun getLastRootInsets(): WindowInsetsCompat? {
        return lastRootInsets
    }

    override fun setLastRootInsets(rootInsets: WindowInsetsCompat) {
        lastRootInsets = rootInsets;
    }
}