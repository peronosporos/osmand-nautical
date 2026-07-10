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
import net.osmand.plus.plugins.nautical.engine.AutopilotController;
import net.osmand.plus.plugins.nautical.engine.SignalKEngine;
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

public class MarineTextWidget extends TextInfoWidget implements ISupportWidgetResizing {

    private final OsmandPreference<WidgetSize> widgetSizePref;
    private View statusDot;
    private String lastDisplayedText = "";

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

        SignalKEngine engine = NauticalPlugin.getEngine();
        if (engine != null) {
            engine.registerListener(state -> {
                mapActivity.runOnUiThread(() -> {
                    View v = getView();
                    updateInfo(v, null);
                });
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    @NonNull
    @Override
    public OsmandPreference<WidgetSize> getWidgetSizePref() {
        return widgetSizePref;
    }

    @Nullable
    public Class<? extends net.osmand.plus.settings.fragments.BaseSettingsFragment> getSettingsFragment() {
        return null;
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

        if (view instanceof ViewGroup parentView) {

            // 2. Create the dot programmatically ONLY if it doesn't exist
            // We use 'findViewWithTag' to ensure we never add it twice
            if (parentView.findViewWithTag("status_dot") == null) {
                statusDot = new View(mapActivity);
                statusDot.setTag("status_dot");

                int size = (int) (8 * mapActivity.getResources().getDisplayMetrics().density);
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(size, size);
                dotParams.gravity = Gravity.TOP | Gravity.END;
                dotParams.setMargins(0, 8, 8, 0);

                statusDot.setLayoutParams(dotParams);
                parentView.addView(statusDot);
            } else {
                statusDot = parentView.findViewWithTag("status_dot");
            }
        }

        view.setOnClickListener(v -> {
            if (mapActivity != null && !mapActivity.isFinishing()) {
                NauticalDataBottomSheet dialog = NauticalDataBottomSheet.newInstance(this.widgetType);
                dialog.show(mapActivity.getSupportFragmentManager(), "nautical_graph");
            }
        });
    }

    @Override
    protected void updateInfo(@NonNull View view, @Nullable OsmandMapLayer.DrawSettings drawSettings) {
        super.updateInfo(view, drawSettings);

        // 1. Get references
        SignalKEngine engine = NauticalPlugin.getEngine();
        AutopilotController autopilot = NauticalPlugin.getAutopilot();

        // 2. SAFE EXIT: If engine is null, stop here.
        // Do NOT use Objects.requireNonNull.
        if (engine == null || autopilot == null) return;

        // 3. Now it is safe to access the state
        MarineState state = engine.getCurrentState();
        boolean isConnected = autopilot.isConnected();
        boolean isStale = engine.isDataStale();

        // 4. Update status dot color
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

        // 5. Update UI
        // Only set "OFF" if the system has been disconnected for more than 1 second
        String offStatus = mapActivity.getString(R.string.nautical_status_off);

        if (!isConnected || state == null) {
            if (!lastDisplayedText.equals("OFF")) {
                setText(offStatus, "---");
                lastDisplayedText = "OFF";
            }
        } else {
            OsmandSettings settings = mapActivity.getApp().getSettings();
            String metricSystem = String.valueOf(settings.METRIC_SYSTEM.get());

            if (this.widgetType == WidgetType.NAUTICAL_DEPTH) {
                handleDepthUpdate(state, metricSystem);
            } else if (this.widgetType == WidgetType.NAUTICAL_WIND) {
                handleWindUpdate(state, metricSystem);
            } else if (this.widgetType == WidgetType.NAUTICAL_PILOT) {
                handlePilotUpdate(state);
            }
        }
    }

    private void handlePilotUpdate(MarineState state) {
        String mode = state.getAutopilotMode();
        if (!lastDisplayedText.equals(mode)) {
            setText(mode.toUpperCase(Locale.US), "");
            lastDisplayedText = mode;
        }
    }

    private void handleDepthUpdate(MarineState state, String metricSystem) {
        Double depth = state.getDepthBelowTransducer();
        if (depth != null) {
            String unit = (metricSystem.contains("FEET") || metricSystem.contains("YARDS")) ? "ft" : "m";
            if (unit.equals("ft")) depth *= 3.28084;
            String valueText = String.format(Locale.US, "%.1f", depth);
            if (!lastDisplayedText.equals(valueText)) {
                setText(valueText, unit);
                lastDisplayedText = valueText;
            }
        } else if (!lastDisplayedText.equals("---")) {
            setText("---", "m");
            lastDisplayedText = "---";
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
            String valueText = String.format(Locale.US, "%.1f", wind);
            if (!lastDisplayedText.equals(valueText)) {
                setText(valueText, unit);
                lastDisplayedText = valueText;
            }
        } else if (!lastDisplayedText.equals("---")) {
            setText("---", "kn");
            lastDisplayedText = "---";
        }
    }
}