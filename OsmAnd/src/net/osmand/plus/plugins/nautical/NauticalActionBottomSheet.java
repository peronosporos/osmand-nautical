package net.osmand.plus.plugins.nautical;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.base.BottomSheetDialogFragment;

public class NauticalActionBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "NauticalActionBottomSheet";
    private static final String ACTION_PAYLOAD_KEY = "action_payload_key";

    public static NauticalActionBottomSheet newInstance(String actionPayload) {
        NauticalActionBottomSheet fragment = new NauticalActionBottomSheet();
        Bundle args = new Bundle();
        args.putString(ACTION_PAYLOAD_KEY, actionPayload);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        LinearLayout mainView = new LinearLayout(getContext());
        mainView.setOrientation(LinearLayout.VERTICAL);
        mainView.setPadding(48, 48, 48, 48);

        // Removed hardcoded background colors. The BottomSheetDialogFragment
        // parent class automatically handles Day/Night background theming.

        String payload = getArguments() != null ? getArguments().getString(ACTION_PAYLOAD_KEY) : "Unknown Action";

        TextView header = new TextView(getContext());
        header.setText("Execute Action: " + payload);
        header.setTextSize(18);

        // Using standard Android default text color which adapts to themes
        header.setTextColor(getResources().getColor(android.R.color.tab_indicator_text, getContext().getTheme()));

        mainView.addView(header);

        return mainView;
    }
}