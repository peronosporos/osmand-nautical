package net.osmand.plus.views.mapwidgets.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NauticalGraphView extends View {
    private List<Double> data = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String unit = "";
    private final Path graphPath = new Path();

    public NauticalGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

        // Line Paint (The Data)
        linePaint.setColor(Color.CYAN);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(8f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        // Text Paint
        textPaint.setColor(isNight ? Color.WHITE : Color.BLACK);
        textPaint.setTextSize(32f);

        // Grid Paint
        gridPaint.setColor(Color.DKGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
    }

    public void setData(List<Double> newData, String unit) {
        synchronized (this) {
            this.data = new ArrayList<>(newData);
            this.unit = unit;
        }
        postInvalidate();
    }



    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        synchronized (this) {
            if (data == null || data.isEmpty()) {
                canvas.drawText("No Data", 20f, getHeight() / 2f, textPaint);
                return;
            }

            if (data.size() < 2) {
                canvas.drawText("Buffering...", 20f, getHeight() / 2f, textPaint);
                return;
            }

            float width = getWidth();
            float height = getHeight();
            float padding = 60f;
            float graphH = height - (padding * 2);

            // Find Range for scaling
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            for (Double d : data) {
                if (d < min) min = d;
                if (d > max) max = d;
            }
            double range = (max - min) == 0 ? 1.0 : (max - min);
            double paddedMin = min - (range * 0.1);
            double paddedRange = range * 1.2;

            // 1. Draw Grid Lines
            canvas.drawLine(0, padding, width, padding, gridPaint); // Top
            canvas.drawLine(0, height - padding, width, height - padding, gridPaint); // Bottom

            // 2. Draw Axis Labels
            canvas.drawText(String.format(Locale.US, "%.1f", max), 5f, padding - 10f, textPaint);
            canvas.drawText(String.format(Locale.US, "%.1f", min), 5f, height - padding + 35f, textPaint);
            canvas.drawText(unit, width - 80f, height - 10f, textPaint);

            // 3. Draw Smoothed Data Curve
            graphPath.reset();
            for (int i = 0; i < data.size(); i++) {
                float x = (i * width) / (data.size() - 1);
                float y = (float) (height - padding - ((data.get(i) - paddedMin) / paddedRange) * graphH);

                if (i == 0) {
                    graphPath.moveTo(x, y);
                } else {
                    float prevX = ((i - 1) * width) / (data.size() - 1);
                    float prevY = (float) (height - padding - ((data.get(i - 1) - paddedMin) / paddedRange) * graphH);
                    // Use quadratic Bezier for smooth curves
                    graphPath.quadTo(prevX, prevY, x, y);
                }
            }
            canvas.drawPath(graphPath, linePaint);
        }
    }
}