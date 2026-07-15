package net.osmand.plus.views.mapwidgets.configure.settings;

import android.widget.TextView;
import androidx.annotation.NonNull;
import net.osmand.plus.R;
import net.osmand.plus.views.mapwidgets.WidgetType;

public class NauticalWidgetSettingsFragment extends BaseSimpleWidgetInfoFragment {

    @NonNull
    @Override
    public WidgetType getWidget() {
        return widgetInfo.widget.getWidgetType();
    }

    @Override
    protected void setupMainContent(@NonNull android.view.ViewGroup container) {
        super.setupMainContent(container);

        TextView tvDesc = view.findViewById(R.id.widget_description);
        if (tvDesc != null) {
            WidgetType widgetType = widgetInfo.widget.getWidgetType();
            if (widgetType.descId != 0) {
                tvDesc.setText(widgetType.descId);
            } else {
                tvDesc.setText(R.string.nautical_group_desc);
            }
        }

        if (getWidget() == WidgetType.NAUTICAL_PILOT) {
            net.osmand.plus.helpers.AndroidUiHelper.updateVisibility(view.findViewById(R.id.main_container), true);
            setupPilotExtraInfo(container);
        }
    }

    private void setupPilotExtraInfo(@NonNull android.view.ViewGroup container) {
        TextView tv = new TextView(getContext());
        float xte = settings.NAUTICAL_XTE_THRESHOLD.get();
        tv.setText(String.format(java.util.Locale.US, "Current Off Course Threshold: %.2f NM\nConfigure this in Nautical Plugin Settings.", xte));
        tv.setPadding(64, 32, 64, 32);
        container.addView(tv);
    }
}
