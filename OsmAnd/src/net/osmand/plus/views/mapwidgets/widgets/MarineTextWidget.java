package net.osmand.plus.views.mapwidgets.widgets;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.views.mapwidgets.WidgetType;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.layers.base.OsmandMapLayer;
import net.osmand.plus.plugins.nautical.NauticalPlugin;
import net.osmand.plus.plugins.nautical.engine.MarineState;
import net.osmand.plus.settings.backend.OsmandSettings;

public class MarineTextWidget extends TextInfoWidget {

    public MarineTextWidget(@NonNull MapActivity mapActivity, @NonNull WidgetType widgetType,
                            @Nullable String customId, @Nullable WidgetsPanel panel) {
        super(mapActivity, widgetType, customId, panel);
        setText("---", "m");
    }

    @Override
    protected void updateInfo(@NonNull View view, @Nullable OsmandMapLayer.DrawSettings drawSettings) {
        OsmandSettings settings = mapActivity.getApp().getSettings();
        MarineState state = NauticalPlugin.Companion.getEngine().getCurrentState();

        if (state != null) {
            // POINT 8: Smart Units
            String metricSystem = String.valueOf(settings.METRIC_SYSTEM.get());

            if (this.widgetType == WidgetType.NAUTICAL_DEPTH) {
                Double depth = state.getDepthBelowTransducer();
                String unit = "m";

                if (depth != null) {
                    if (metricSystem.contains("FEET") || metricSystem.contains("YARDS")) {
                        depth = depth * 3.28084; // Meters to Feet
                        unit = "ft";
                    }
                    setText(String.format("%.1f", depth), unit);
                } else {
                    setText("---", unit);
                }

            } else if (this.widgetType == WidgetType.NAUTICAL_WIND) {
                Double windKnots = state.getWindSpeedTrue();
                String unit = "kn";

                if (windKnots != null) {
                    if (metricSystem.contains("KILOMETERS")) {
                        windKnots = windKnots * 1.852; // Knots to km/h
                        unit = "km/h";
                    } else if (metricSystem.contains("MILES")) {
                        windKnots = windKnots * 1.15078; // Knots to mph
                        unit = "mph";
                    }
                    setText(String.format("%.1f", windKnots), unit);
                } else {
                    setText("---", unit);
                }
            }
        }
    }
}