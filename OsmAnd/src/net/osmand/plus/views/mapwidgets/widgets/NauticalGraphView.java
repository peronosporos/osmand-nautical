package net.osmand.plus.views.mapwidgets.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.List;

public class NauticalGraphView extends View {
    private List<Double> data;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String unit = "";

    public NauticalGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Detect theme natively without relying on missing R.color or UiUtilities
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

        // Standard colors that are guaranteed to exist
        int primaryColor = isNight ? Color.WHITE : Color.BLACK;
        int activeColor = Color.CYAN;

        linePaint.setColor(activeColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        textPaint.setColor(primaryColor);
        textPaint.setTextSize(36f);
    }

    public void setData(List<Double> data, String unit) {
        this.data = data;
        this.unit = unit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (data == null || data.isEmpty()) {
            canvas.drawText("No Data", getWidth() / 4f, getHeight() / 2f, textPaint);
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float stepX = width / Math.max(data.size() - 1, 1);

        double max = 0.1;
        for (Double d : data) if (d > max) max = d;

        Path path = new Path();
        for (int i = 0; i < data.size(); i++) {
            float x = i * stepX;
            // Draw graph leaving space for labels
            float y = (height * 0.9f) - (float) ((data.get(i) / max) * height * 0.7f);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, linePaint);

        canvas.drawText(String.format("Max: %.1f %s", max, unit), 10f, 40f, textPaint);
    }
}