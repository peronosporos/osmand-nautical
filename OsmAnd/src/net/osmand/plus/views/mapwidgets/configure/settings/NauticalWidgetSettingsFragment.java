package net.osmand.plus.views.mapwidgets.configure.settings;

import android.widget.TextView;
import androidx.annotation.NonNull;
import net.osmand.plus.R;
import net.osmand.plus.views.mapwidgets.WidgetType;

public class NauticalWidgetSettingsFragment extends BaseResizableWidgetSettingFragment {

    @NonNull
    @Override
    public WidgetType getWidget() {
        return widgetInfo.widget.getWidgetType();
    }

    // Instead of overriding setupInfo which seems to be causing the override error,
    // we use the standard setupMainContent override which is the intended
    // entry point for custom widget settings UI.
    @Override
    protected void setupMainContent(@NonNull android.view.ViewGroup container) {
        super.setupMainContent(container);

        // This is where we safely inject our description text
        // without touching the core fragments' setupInfo logic.
        TextView tvDesc = view.findViewById(R.id.widget_description);
        if (tvDesc != null) {
            WidgetType widgetType = getWidget();
            if (widgetType.descId != 0) {
                tvDesc.setText(widgetType.descId);
            } else {
                tvDesc.setText("Nautical Marine Data");
            }
        }
    }
}