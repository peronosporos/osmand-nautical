package net.osmand.plus.views.mapwidgets.widgets;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

import java.util.Locale;
import java.util.Objects;

public class MarineTextWidget extends TextInfoWidget implements ISupportWidgetResizing {

    private final OsmandPreference<WidgetSize> widgetSizePref;
    private View statusDot;

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

        Objects.requireNonNull(NauticalPlugin.Companion.getEngine()).registerListener(state -> {
            mapActivity.runOnUiThread(() -> {
                View v = getView();
                updateInfo(v, null);
            });
            return kotlin.Unit.INSTANCE;
        });
    }

    @NonNull
    @Override
    public OsmandPreference<WidgetSize> getWidgetSizePref() {
        return widgetSizePref;
    }

    @Override
    public void recreateView() {
        View v = getView();
        setupView(v);
        updateInfo(v, null);
    }

    @Override
    public boolean allowResize() {
        return true;
    }

    @Override
    protected void setupView(@NonNull View view) {
        super.setupView(view);

        // Initialize and add Status Dot
        if (statusDot == null && view instanceof ViewGroup) {
            statusDot = new View(mapActivity);
            int size = (int) (8 * mapActivity.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(size, size);
            dotParams.gravity = Gravity.TOP | Gravity.END;
            dotParams.setMargins(0, 8, 8, 0);
            statusDot.setLayoutParams(dotParams);
            ((ViewGroup) view).addView(statusDot);
        }

        view.setOnClickListener(v -> {
            NauticalDataBottomSheet dialog = NauticalDataBottomSheet.newInstance(this.widgetType);
            dialog.show(mapActivity.getSupportFragmentManager(), "nautical_graph");
        });
    }

    @Override
    protected void updateInfo(@NonNull View view, @Nullable OsmandMapLayer.DrawSettings drawSettings) {
        super.updateInfo(view, drawSettings);

        MarineState state = Objects.requireNonNull(NauticalPlugin.Companion.getEngine()).getCurrentState();
        boolean isConnected = Objects.requireNonNull(NauticalPlugin.Companion.getAutopilot()).isConnected();
        boolean isStale = NauticalPlugin.Companion.getEngine().isDataStale();

        // Update status dot color
        if (statusDot != null) {
            if (!isConnected) {
                statusDot.setBackgroundColor(Color.RED);
            } else if (isStale) {
                statusDot.setBackgroundColor(Color.YELLOW);
            } else {
                statusDot.setBackgroundColor(Color.GREEN);
            }
        }

        view.setAlpha(isConnected ? 1.0f : 0.5f);

        if (!isConnected || state == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), "---");
        } else {
            OsmandSettings settings = mapActivity.getApp().getSettings();
            String metricSystem = String.valueOf(settings.METRIC_SYSTEM.get());

            if (this.widgetType == WidgetType.NAUTICAL_DEPTH) {
                handleDepthUpdate(state, metricSystem);
            } else if (this.widgetType == WidgetType.NAUTICAL_WIND) {
                handleWindUpdate(state, metricSystem);
            }
        }
    }

    private void handleDepthUpdate(MarineState state, String metricSystem) {
        Double depth = state.getDepthBelowTransducer();
        if (depth != null) {
            String unit = (metricSystem.contains("FEET") || metricSystem.contains("YARDS")) ? "ft" : "m";
            if (unit.equals("ft")) depth *= 3.28084;
            setText(String.format(Locale.US, "%.1f", depth), unit);
        } else {
            setText("---", "m");
        }
    }

    private void handleWindUpdate(MarineState state, String metricSystem) {
        Double wind = state.getWindSpeedTrue();
        if (wind != null) {
            String unit = "kn";
            if (metricSystem.contains("KILOMETERS")) {
                wind *= 1.852; unit = "km/h";
            } else if (metricSystem.contains("MILES")) {
                wind *= 1.15078; unit = "mph";
            }
            setText(String.format(Locale.US, "%.1f", wind), unit);
        } else {
            setText("---", "kn");
        }
    }
}