package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowEngine
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.utils.AndroidUtils

class WorkflowHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val title: TextView
    private val btnConfirm: Button
    private var workflowEngine: SailingWorkflowEngine? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_workflow_hud, this, true)
        title = findViewById(R.id.workflow_title)
        btnConfirm = findViewById(R.id.btn_workflow_confirm)
        
        btnConfirm.setOnClickListener {
            workflowEngine?.confirmPendingWorkflow(context as? MapActivity)
            isVisible = false
        }
        isVisible = false
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 2f else 8f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    fun setEngine(engine: SailingWorkflowEngine) {
        this.workflowEngine = engine
    }

    fun showProposal(state: SailingWorkflowState) {
        title.text = when (state) {
            SailingWorkflowState.CLOSE_QUARTERS -> context.getString(R.string.nautical_workflow_close_quarters)
            SailingWorkflowState.STATIONARY_ANCHORED -> context.getString(R.string.nautical_workflow_anchored)
            SailingWorkflowState.TACTICAL_PASSAGE -> context.getString(R.string.nautical_workflow_tactical)
        }
        isVisible = true
    }
}
