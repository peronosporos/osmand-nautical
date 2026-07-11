package net.osmand.plus.plugins.nautical;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.base.BottomSheetDialogFragment;

import java.util.Objects;

public class NauticalActionBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "NauticalActionBottomSheet";
    private static final String LAT_KEY = "target_lat";
    private static final String LON_KEY = "target_lon";

    // Updated to accept coordinates
    public static NauticalActionBottomSheet newInstance(double lat, double lon) {
        NauticalActionBottomSheet fragment = new NauticalActionBottomSheet();
        Bundle args = new Bundle();
        args.putDouble(LAT_KEY, lat);
        args.putDouble(LON_KEY, lon);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        LinearLayout mainView = new LinearLayout(getContext());
        mainView.setOrientation(LinearLayout.VERTICAL);
        mainView.setPadding(64, 64, 64, 64);

        double targetLat = getArguments() != null ? getArguments().getDouble(LAT_KEY) : 0.0;
        double targetLon = getArguments() != null ? getArguments().getDouble(LON_KEY) : 0.0;

        TextView header = new TextView(getContext());
        header.setText(String.format("Target: %.5f, %.5f", targetLat, targetLon));
        header.setTextSize(18);
        header.setPadding(0, 0, 0, 48);
        header.setTextColor(getResources().getColor(android.R.color.tab_indicator_text, requireContext().getTheme()));
        mainView.addView(header);

        // UI TEST: The Engage Button
        Button engageButton = new Button(getContext());
        engageButton.setText("ENGAGE AUTOPILOT");
        engageButton.setOnClickListener(v -> {
            // Trigger the network logic
            Objects.requireNonNull(NauticalPlugin.Companion.getAutopilot()).sendActiveWaypoint(targetLat, targetLon);
            Toast.makeText(getContext(), "Command Sent to SignalK", Toast.LENGTH_SHORT).show();
            dismiss(); // Close the menu
        });

        mainView.addView(engageButton);

        return mainView;
    }
}