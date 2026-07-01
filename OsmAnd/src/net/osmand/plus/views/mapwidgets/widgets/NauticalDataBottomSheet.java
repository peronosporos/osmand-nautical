package net.osmand.plus.views.mapwidgets.widgets;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // This solves your error
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
        NauticalGraphView graph = view.findViewById(R.id.graph_view);

        if (graph != null && type != null) {
            if (type == WidgetType.NAUTICAL_DEPTH) {
                graph.setData(NauticalPlugin.Companion.getEngine().getDepthHistory());
            } else if (type == WidgetType.NAUTICAL_WIND) {
                graph.setData(NauticalPlugin.Companion.getEngine().getWindHistory());
            }
        }
    }
}