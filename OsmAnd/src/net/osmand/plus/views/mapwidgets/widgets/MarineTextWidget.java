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
import net.osmand.plus.plugins.nautical.NauticalActionBottomSheet;

public class MarineTextWidget extends TextInfoWidget {

    public MarineTextWidget(@NonNull MapActivity mapActivity, @NonNull WidgetType widgetType,
                            @Nullable String customId, @Nullable WidgetsPanel panel) {
        super(mapActivity, widgetType, customId, panel);
        setText("---", "m");
    }

    @Override
    protected void updateInfo(@NonNull View view, @Nullable OsmandMapLayer.DrawSettings drawSettings) {
        super.updateInfo(view, drawSettings);

        MarineState state = NauticalPlugin.Companion.getEngine().getCurrentState();

        // 2c: Fallback UI (Critical for UX so user knows connection is lost)
        if (state == null) {
            setText("OFF", "---");
            return;
        }

        OsmandSettings settings = mapActivity.getApp().getSettings();
        String metricSystem = String.valueOf(settings.METRIC_SYSTEM.get());

        if (this.widgetType == WidgetType.NAUTICAL_DEPTH) {
            Double depth = state.getDepthBelowTransducer();
            if (depth != null) {
                String unit = "m";
                if (metricSystem.contains("FEET") || metricSystem.contains("YARDS")) {
                    depth = depth * 3.28084;
                    unit = "ft";
                }
                setText(String.format("%.1f", depth), unit);
            } else {
                setText("---", "m");
            }
        } else if (this.widgetType == WidgetType.NAUTICAL_WIND) {
            Double windKnots = state.getWindSpeedTrue();
            if (windKnots != null) {
                String unit = "kn";
                if (metricSystem.contains("KILOMETERS")) {
                    windKnots = windKnots * 1.852;
                    unit = "km/h";
                } else if (metricSystem.contains("MILES")) {
                    windKnots = windKnots * 1.15078;
                    unit = "mph";
                }
                setText(String.format("%.1f", windKnots), unit);
            } else {
                setText("---", "kn");
            }
        }
    }

    @Override
    protected void setupView(@NonNull View view) {
        super.setupView(view);

        view.setOnClickListener(v -> {
            NauticalActionBottomSheet dialog = NauticalActionBottomSheet.newInstance(
                    mapActivity.getMapView().getLatitude(),
                    mapActivity.getMapView().getLongitude()
            );
            dialog.show(mapActivity.getSupportFragmentManager(), NauticalActionBottomSheet.TAG);
        });
    }
}