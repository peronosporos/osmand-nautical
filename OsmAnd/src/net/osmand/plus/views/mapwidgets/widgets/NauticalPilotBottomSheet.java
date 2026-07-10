package net.osmand.plus.views.mapwidgets.widgets;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import net.osmand.plus.R;
import net.osmand.plus.plugins.nautical.NauticalPlugin;
import net.osmand.plus.plugins.nautical.engine.AutopilotController;
import net.osmand.plus.plugins.nautical.engine.MarineState;
import net.osmand.plus.plugins.nautical.engine.SignalKEngine;
import java.util.Locale;
import java.util.Objects;

public class NauticalPilotBottomSheet extends DialogFragment {



    public static NauticalPilotBottomSheet newInstance() {
        return new NauticalPilotBottomSheet();
    }



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.BottomSheet_Dialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_nautical_pilot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        SignalKEngine engine = NauticalPlugin.getEngine();
        AutopilotController autopilot = NauticalPlugin.getAutopilot();

        if (engine == null || autopilot == null) {
            dismiss();
            return;
        }

        TextView statusView = view.findViewById(R.id.tv_pilot_status);


        // Register listener safely
        // Using placeholder resource (ensure this exists in strings.xml)
        kotlin.jvm.functions.Function1<MarineState, kotlin.Unit> myListener = state -> {
            if (!isAdded() || statusView == null) return kotlin.Unit.INSTANCE;

            statusView.post(() -> {
                String mode = state.getAutopilotState();
                // Using placeholder resource (ensure this exists in strings.xml)
                statusView.setText(getString(R.string.nautical_pilot_status_active, mode.toUpperCase(Locale.US)));

                if (mode.equalsIgnoreCase("auto")) {
                    statusView.setBackgroundColor(Color.parseColor("#E3F2FD"));
                } else if (mode.equalsIgnoreCase("emergency") || mode.equalsIgnoreCase("stop")) {
                    statusView.setBackgroundColor(Color.parseColor("#FFCDD2"));
                } else {
                    statusView.setBackgroundColor(Color.parseColor("#EEEEEE"));
                }
            });
            return kotlin.Unit.INSTANCE;
        };
        engine.registerListener(myListener);

        // 3. Button Click Listeners
        View btnAuto = view.findViewById(R.id.btn_auto);
        if (btnAuto != null) {
            btnAuto.setOnClickListener(v -> {
                Objects.requireNonNull(NauticalPlugin.getAutopilot()).setAutopilotMode("auto");
                dismiss();
            });
        }

        View btnStop = view.findViewById(R.id.btn_emergency_stop);
        if (btnStop != null) {
            btnStop.setOnClickListener(v -> {
                Objects.requireNonNull(NauticalPlugin.getAutopilot()).stopNavigation();
                Toast.makeText(getContext(), getString(R.string.nautical_emergency_stop_executed), Toast.LENGTH_SHORT).show();
                dismiss();
            });
        }
    }
}