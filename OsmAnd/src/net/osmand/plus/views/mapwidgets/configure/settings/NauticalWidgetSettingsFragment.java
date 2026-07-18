package net.osmand.plus.views.mapwidgets.configure.settings;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import net.osmand.plus.R;
import net.osmand.plus.views.mapwidgets.WidgetType;
import java.util.Locale;

public class NauticalWidgetSettingsFragment extends BaseSimpleWidgetInfoFragment {

    private ViewGroup itemsContainer;

    @NonNull
    @Override
    public WidgetType getWidget() {
        return widgetInfo.widget.getWidgetType();
    }

    @Override
    protected void setupMainContent(@NonNull ViewGroup container) {
        super.setupMainContent(container);

        TextView tvDesc = view.findViewById(R.id.widget_description);
        if (tvDesc != null) {
            WidgetType widgetType = getWidget();
            if (widgetType.descId != 0) {
                tvDesc.setText(widgetType.descId);
            } else {
                tvDesc.setText(R.string.nautical_group_desc);
            }
        }

        getThemedInflater().inflate(R.layout.map_marker_side_widget_settings_fragment, container);
        itemsContainer = view.findViewById(R.id.items_container);

        if (getWidget() == WidgetType.NAUTICAL_PILOT) {
            setupPilotExtraInfo();
        }
    }

    private void setupPilotExtraInfo() {
        TextView tv = new TextView(getContext());
        float xte = settings.NAUTICAL_XTE_THRESHOLD.get();
        tv.setText(String.format(Locale.US, "Current Off Course Threshold: %.2f NM\nConfigure this in Nautical Plugin Settings.", xte));
        tv.setPadding(64, 32, 64, 32);
        itemsContainer.addView(tv);
    }
}
