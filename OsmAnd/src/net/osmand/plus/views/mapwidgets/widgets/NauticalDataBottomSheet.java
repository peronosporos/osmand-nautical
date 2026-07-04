package net.osmand.plus.views.mapwidgets.widgets;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import net.osmand.plus.R;
import net.osmand.plus.plugins.nautical.NauticalPlugin;
import net.osmand.plus.views.mapwidgets.WidgetType;

public class NauticalDataBottomSheet extends BottomSheetDialogFragment {
    private WidgetType type;

    public static NauticalDataBottomSheet newInstance(WidgetType type) {
        NauticalDataBottomSheet fragment = new NauticalDataBottomSheet();
        fragment.type = type;
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.nautical_data_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView titleView = view.findViewById(R.id.graph_title);
        NauticalGraphView graph = view.findViewById(R.id.graph_view);

        if (graph != null && type != null) {
            if (type == WidgetType.NAUTICAL_DEPTH) {
                titleView.setText("Depth History");
                graph.setData(NauticalPlugin.Companion.getEngine().getDepthHistory(), "m");
            } else if (type == WidgetType.NAUTICAL_WIND) {
                titleView.setText("Wind History");
                graph.setData(NauticalPlugin.Companion.getEngine().getWindHistory(), "kn");
            } else {
                titleView.setText("Telemetry History");
            }
        }
    }
}