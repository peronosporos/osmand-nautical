package net.osmand.plus.views.mapwidgets.widgets;

import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.views.mapwidgets.WidgetType;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.layers.base.OsmandMapLayer;
import net.osmand.plus.plugins.nautical.NauticalPlugin;
import net.osmand.plus.plugins.nautical.engine.MarineState;
import android.view.View;

public class MarineTextWidget extends TextInfoWidget {

    public MarineTextWidget(MapActivity activity, WidgetType type, String id, WidgetsPanel panel) {
        super(activity, type, id, panel);
        setText("---", "m"); // Initial state
    }

        @Override
    public void updateInfo(OsmandMapLayer.DrawSettings drawSettings) {
        MarineState state = NauticalPlugin.Companion.getEngine().getCurrentState();

        if (state != null) {
            // Try accessing via 'widgetType' if 'type' or 'getType()' fail
            if (this.widgetType == WidgetType.NAUTICAL_DEPTH) {
                Double depth = state.getDepthBelowTransducer();
                setText(depth != null ? String.format("%.1f", depth) : "---", "m");
            } else if (this.widgetType == WidgetType.NAUTICAL_WIND) {
                Double wind = state.getWindSpeedTrue();
                setText(wind != null ? String.format("%.1f", wind) : "---", "kn");
            }
        }
    }

    // Standard override - usually not needed if you use updateInfo(DrawSettings)
    @Override
    public void updateInfo(View view, OsmandMapLayer.DrawSettings drawSettings) {
        updateInfo(drawSettings);
    }
}