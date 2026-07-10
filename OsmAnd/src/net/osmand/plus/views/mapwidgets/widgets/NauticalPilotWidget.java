package net.osmand.plus.views.mapwidgets.widgets;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.plugins.nautical.NauticalPlugin;
import net.osmand.plus.plugins.nautical.engine.AutopilotController;
import net.osmand.plus.plugins.nautical.engine.MarineState;
import net.osmand.plus.plugins.nautical.engine.SignalKEngine;
import net.osmand.plus.views.mapwidgets.WidgetType;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.mapwidgets.widgetinterfaces.ISupportWidgetResizing;
import net.osmand.plus.settings.backend.preferences.OsmandPreference;
import net.osmand.plus.settings.enums.WidgetSize;
import net.osmand.plus.settings.backend.preferences.EnumStringPreference;
import net.osmand.plus.views.layers.base.OsmandMapLayer;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.PopupWindow;
import android.widget.ProgressBar;

import java.util.Objects;

public class NauticalPilotWidget extends TextInfoWidget implements ISupportWidgetResizing {

    private final OsmandPreference<WidgetSize> widgetSizePref;
    private View statusDot;
    private android.widget.ImageView statusIconView;
    private ProgressBar progressBar;
    private final kotlin.jvm.functions.Function1<MarineState, kotlin.Unit> myListener;
    private GestureDetector gestureDetector;
    private final android.os.Handler holdHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int holdProgress = 0;


    public NauticalPilotWidget(@NonNull MapActivity mapActivity, @NonNull WidgetType widgetType,
                               @Nullable String customId, @Nullable WidgetsPanel panel) {
        super(mapActivity, widgetType, customId, panel);

        this.widgetSizePref = new EnumStringPreference<>(
                mapActivity.getApp().getSettings(),
                "nautical_pilot_widget_size",
                WidgetSize.MEDIUM,
                WidgetSize.values()
        ).makeProfile();

        this.myListener = state -> {
            mapActivity.runOnUiThread(() -> {
                View v = getView();
                updateInfo(v, null);
            });
            return kotlin.Unit.INSTANCE;
        };

    }

    private void setIcon(int iconResId) {
        if (statusIconView != null) {
            // Using ContextCompat is the standard Android way and works perfectly
            // for all drawables without needing the IconsCache helper.
            statusIconView.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(mapActivity, iconResId));
        }
    }

    // --- Command Handling ---
    @SuppressWarnings("SameParameterValue")
    private void triggerCommand(String command) {
        if ("STOP".equals(command)) {
            executeStopCommand();
        } else if ("TACK".equals(command)) {
            showTacticalGate();
        } else {
            executeRoutineCommand(command);
        }
    }

    private void executeStopCommand() {
        if (Objects.requireNonNull(NauticalPlugin.Companion.getAutopilot()).isConnected()) {
            View view = getView();
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            NauticalPlugin.Companion.getAutopilot().stopNavigation();
            Toast.makeText(mapActivity, "EMERGENCY STOP EXECUTED", Toast.LENGTH_LONG).show();
        }
    }
    private void showTacticalGate() {
        View popupView = View.inflate(mapActivity, R.layout.nautical_confirm_popup, null);
        PopupWindow popup = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        android.os.Handler dismissHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable dismissRunnable = () -> {
            if (popup.isShowing()) popup.dismiss();
        };
        dismissHandler.postDelayed(dismissRunnable, 3000);

        popupView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            dismissHandler.removeCallbacks(dismissRunnable); // Stop the countdown
            executeRoutineCommand("TACK_EXECUTED");
            popup.dismiss();
        });

        popup.showAsDropDown(getView(), 0, -getView().getHeight());
    }


    private void executeRoutineCommand(String command) {
        Toast.makeText(mapActivity, "Command: " + command, Toast.LENGTH_SHORT).show();
    }




    // --- Standard Widget Lifecycle ---
    @NonNull
    @Override
    public OsmandPreference<WidgetSize> getWidgetSizePref() {
        return widgetSizePref;
    }

    @Override
    public boolean allowResize() {
        return true;
    }

    @Override
    public void recreateView() {
        View v = getView();
        setupView(v);
        updateInfo(v, null);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void setupView(@NonNull View view) {
        super.setupView(view);

        view.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                SignalKEngine engine = NauticalPlugin.getEngine();
                if (engine != null) {
                    engine.unregisterListener(myListener);
                    engine.registerListener(myListener);
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                Objects.requireNonNull(NauticalPlugin.getEngine()).unregisterListener(myListener);
                holdHandler.removeCallbacksAndMessages(null);
            }
        });

        gestureDetector = new GestureDetector(mapActivity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                NauticalPilotBottomSheet sheet = NauticalPilotBottomSheet.newInstance();
                sheet.show(mapActivity.getSupportFragmentManager(), "pilot_control");
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                // 1. Show the bar
                progressBar.setVisibility(View.VISIBLE);

                // 2. Start the "Confirm" loop
                holdProgress = 0;
                holdHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        holdProgress += 10;
                        progressBar.setProgress(holdProgress);

                        if (holdProgress >= 100) {
                            // SUCCESS: Execute Command
                            progressBar.setVisibility(View.GONE);
                            triggerCommand("STOP");
                        } else {
                            // Continue loop
                            holdHandler.postDelayed(this, 100);
                        }
                    }
                }, 100);
            }
        });

        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                // User lifted finger: Stop the progress and hide bar
                holdHandler.removeCallbacksAndMessages(null);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
            }
            return gestureDetector.onTouchEvent(event);
        });

        if (statusDot == null && view instanceof ViewGroup) {
            statusDot = new View(mapActivity);
            int size = (int) (8 * mapActivity.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(size, size);
            dotParams.gravity = Gravity.TOP | Gravity.END;
            dotParams.setMargins(0, 8, 8, 0);
            statusDot.setLayoutParams(dotParams);
            ((ViewGroup) view).addView(statusDot);
        }

        if (statusIconView == null && view instanceof ViewGroup) {
            statusIconView = new android.widget.ImageView(mapActivity);
            int size = (int) (32 * mapActivity.getResources().getDisplayMetrics().density); // Professional 32dp size
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(size, size);
            iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            iconParams.setMargins(16, 0, 0, 0);
            statusIconView.setLayoutParams(iconParams);
            ((ViewGroup) view).addView(statusIconView);
        }

        if (progressBar == null && view instanceof ViewGroup) {
            progressBar = new ProgressBar(mapActivity, null, android.R.attr.progressBarStyleHorizontal);
            // Position it at the bottom of the widget
            FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(4 * mapActivity.getResources().getDisplayMetrics().density));
            pbParams.gravity = Gravity.BOTTOM;
            progressBar.setLayoutParams(pbParams);
            progressBar.setVisibility(View.GONE); // Hidden by default
            ((ViewGroup) view).addView(progressBar);
        }
    }

    @SuppressLint("DefaultLocale")
    @Override
    protected void updateInfo(@NonNull View view, @Nullable OsmandMapLayer.DrawSettings drawSettings) {
        super.updateInfo(view, drawSettings);

        SignalKEngine engine = NauticalPlugin.getEngine();
        AutopilotController autopilot = NauticalPlugin.getAutopilot();

        if (engine == null || autopilot == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), "AP");
            return;
        }

        MarineState state = Objects.requireNonNull(NauticalPlugin.getEngine()).getCurrentState();
        boolean isConnected = NauticalPlugin.getAutopilot().isConnected();
        boolean isStale = NauticalPlugin.getEngine().isDataStale();

        if (statusDot != null) {
            if (!isConnected) statusDot.setBackgroundColor(mapActivity.getColor(R.color.nautical_status_red));
            else if (isStale) statusDot.setBackgroundColor(mapActivity.getColor(R.color.nautical_status_yellow));
            else statusDot.setBackgroundColor(mapActivity.getColor(R.color.nautical_status_green));
        }

        view.setVisibility(View.VISIBLE);
        view.setAlpha(isConnected ? 1.0f : 0.3f);
        view.setBackgroundResource(isConnected ? R.drawable.widget_nautical_blue_border : R.drawable.widget_nautical_grey_static);

        if (!isConnected || state == null) {
            setText(mapActivity.getString(R.string.nautical_status_off), "AP");
            return;
        }

        Double crossTrackError = state.getCrossTrackError();
        double xte = (crossTrackError != null) ? crossTrackError : 0.0;
        boolean isOffCourse = Math.abs(xte) > 0.1;

        if (isOffCourse) {
            view.setBackgroundResource(R.drawable.widget_nautical_alert_red);
            setText("XTE", String.format("%.2f NM", xte));
        } else {
            String apState = state.getAutopilotState().toLowerCase();
            switch (apState) {
                case "auto":
                case "wind":
                    view.setBackgroundResource(R.drawable.widget_nautical_blue_border);
                    setIcon(R.drawable.ic_action_play_dark);
                    setText(null, null);
                    break;
                case "emergency":
                case "stop":
                    view.setBackgroundResource(R.drawable.widget_nautical_red_pulse);
                    setIcon(R.drawable.ic_action_stop);
                    setText(mapActivity.getString(R.string.nautical_emg_stop),
                            mapActivity.getString(R.string.nautical_stop_label));
                    break;
                default:
                    view.setBackgroundResource(R.drawable.widget_nautical_grey_static);
                    setIcon(R.drawable.ic_pause);
                    setText(null, null);
                    break;
            }
        }
    }
}