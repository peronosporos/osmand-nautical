package net.osmand.plus.views.mapwidgets.widgets;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.views.mapwidgets.WidgetType;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.layers.base.OsmandMapLayer;
import net.osmand.plus.plugins.nautical.NauticalPlugin;
import net.osmand.plus.plugins.nautical.engine.MarineState;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.views.mapwidgets.widgetinterfaces.ISupportWidgetResizing;
import net.osmand.plus.settings.backend.preferences.OsmandPreference;
import net.osmand.plus.settings.backend.preferences.EnumStringPreference;
import net.osmand.plus.settings.enums.WidgetSize;

public class MarineTextWidget extends TextInfoWidget implements ISupportWidgetResizing {

    private final OsmandPreference<WidgetSize> widgetSizePref;

    public MarineTextWidget(@NonNull MapActivity mapActivity, @NonNull WidgetType widgetType,
                            @Nullable String customId, @Nullable WidgetsPanel panel) {
        super(mapActivity, widgetType, customId, panel);
        setText("---", "m");

        this.widgetSizePref = new EnumStringPreference<>(
                mapActivity.getApp().getSettings(),
                "nautical_widget_size_" + widgetType.name(),
                WidgetSize.MEDIUM,
                WidgetSize.values()
        ).makeProfile();
    }

    // --- ISupportWidgetResizing Implementation ---

    @NonNull
    @Override
    public OsmandPreference<WidgetSize> getWidgetSizePref() {
        return widgetSizePref;
    }

    @Override
    public void recreateView() {
        if (getView() != null) {
            setupView(getView());
            updateInfo(getView(), null);
        }
    }

    @Override
    public boolean allowResize() {
        return true;
    }

    // --- Lifecycle Methods ---

    @Override
    protected void setupView(@NonNull View view) {
        super.setupView(view);

        // Force height update
        int heightInDp = 60;
        int heightInPixels = (int) (heightInDp * mapActivity.getResources().getDisplayMetrics().density);

        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.height = heightInPixels;
            view.setLayoutParams(params);
            view.requestLayout();
        }

        view.setOnClickListener(v -> {
            NauticalDataBottomSheet dialog = NauticalDataBottomSheet.newInstance(this.widgetType);
            dialog.show(mapActivity.getSupportFragmentManager(), "nautical_graph");
        });
    }

    @Override
    protected void updateInfo(@NonNull View view, @Nullable OsmandMapLayer.DrawSettings drawSettings) {
        super.updateInfo(view, drawSettings);

        MarineState state = NauticalPlugin.Companion.getEngine().getCurrentState();
        boolean isConnected = NauticalPlugin.Companion.getAutopilot().isConnected();

        // Visual feedback: Dim the widget if disconnected
        view.setAlpha(isConnected ? 1.0f : 0.5f);

        if (!isConnected || state == null) {
            // Instead of "OFF"
            setText(mapActivity.getString(R.string.nautical_status_off), "---");
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
}