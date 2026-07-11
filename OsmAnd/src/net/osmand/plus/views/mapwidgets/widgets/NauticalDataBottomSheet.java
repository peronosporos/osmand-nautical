package net.osmand.plus.views.mapwidgets.widgets;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import net.osmand.plus.R;
import net.osmand.plus.plugins.nautical.NauticalPlugin;
import net.osmand.plus.views.mapwidgets.WidgetType;
import java.util.Locale;

import net.osmand.plus.plugins.nautical.engine.MarineState;

public class NauticalDataBottomSheet extends BottomSheetDialogFragment {

    private WidgetType type;
    private NauticalGraphView graph;
    private View dot;
    private TextView statusView;
    private kotlin.jvm.functions.Function1<MarineState, kotlin.Unit> myListener;

    public static NauticalDataBottomSheet newInstance(WidgetType type) {
        NauticalDataBottomSheet fragment = new NauticalDataBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable("widget_type", type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = (WidgetType) getArguments().getSerializable("widget_type");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_nautical_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusView = view.findViewById(R.id.tv_pilot_status);
        TextView titleView = view.findViewById(R.id.graph_title);
        graph = view.findViewById(R.id.graph_view);
        dot = view.findViewById(R.id.connection_dot);

        if (type == null) {
            dismiss();
            return;
        }

        if (titleView != null) {
            if (type == WidgetType.NAUTICAL_DEPTH) {
                titleView.setText(getString(R.string.nautical_title_depth));
            } else if (type == WidgetType.NAUTICAL_WIND) {
                titleView.setText(getString(R.string.nautical_title_wind));
            } else {
                titleView.setText(getString(R.string.nautical_title_telemetry));
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        myListener = state -> {
            if (!isAdded() || getContext() == null) {
                return kotlin.Unit.INSTANCE;
            }

            final boolean isConnected = NauticalPlugin.Companion.getAutopilot() != null
                    && NauticalPlugin.Companion.getAutopilot().isConnected();
            final boolean isStale = NauticalPlugin.Companion.getEngine() != null
                    && NauticalPlugin.Companion.getEngine().isDataStale();
            state.getAutopilotState();
            final String mode = state.getAutopilotState();

            View view = getView();
            if (view != null) {
                view.post(() -> {
                    // 4. Final attachment check inside the UI thread
                    if (!isAdded() || getContext() == null) return;

                    if (dot != null) {
                        if (!isConnected) dot.setBackgroundColor(Color.RED);
                        else if (isStale) dot.setBackgroundColor(Color.YELLOW);
                        else dot.setBackgroundColor(Color.GREEN);
                    }

                    if (statusView != null) {
                        statusView.setText(getString(R.string.nautical_active_mode, mode.toUpperCase(Locale.US)));

                        if (mode.equalsIgnoreCase("auto")) {
                            statusView.setBackgroundColor(Color.parseColor("#E3F2FD"));
                        } else if (mode.equalsIgnoreCase("emergency") || mode.equalsIgnoreCase("stop")) {
                            statusView.setBackgroundColor(Color.parseColor("#FFCDD2"));
                        } else {
                            statusView.setBackgroundColor(Color.parseColor("#EEEEEE"));
                        }
                    }

                    if (graph != null) {
                        updateGraphData();
                    }
                });
            }
            return kotlin.Unit.INSTANCE;
        };

        if (NauticalPlugin.Companion.getEngine() != null) {
            NauticalPlugin.Companion.getEngine().registerListener(myListener);
        }
    }

    @Override
    public void onStop() {
        if (myListener != null && NauticalPlugin.Companion.getEngine() != null) {
            NauticalPlugin.Companion.getEngine().unregisterListener(myListener);
            myListener = null;
        }
        super.onStop();
    }

    private void updateGraphData() {
        var engine = NauticalPlugin.Companion.getEngine();
        if (engine == null || graph == null) {
            return;
        }

        if (type == WidgetType.NAUTICAL_DEPTH) {
            graph.setData(engine.getDepthHistory(), "m");
        } else if (type == WidgetType.NAUTICAL_WIND) {
            graph.setData(engine.getWindHistory(), "kn");
        }
    }
}