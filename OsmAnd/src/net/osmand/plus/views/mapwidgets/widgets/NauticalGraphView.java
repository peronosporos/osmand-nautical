package net.osmand.plus.views.mapwidgets.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
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
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pulseRadius = 10f;
    private ValueAnimator pulseAnimator;

    public NauticalGraphView(Context context) {
        super(context);
        setWillNotDraw(false);
        init(context);
    }

    public NauticalGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        init(context);
    }

    public NauticalGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        init(context);
    }

    private void init(Context context) {

        // Resolve theme colors manually to avoid 'styleable' and 'UiUtilities' errors
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        int textColor = typedValue.data;

        context.getTheme().resolveAttribute(android.R.attr.colorForeground, typedValue, true);
        int gridColor = typedValue.data;

        // Grid Paint
        gridPaint.setColor(gridColor);
        gridPaint.setAlpha(100);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setAntiAlias(true);

        // Text Paint
        textPaint.setColor(textColor);
        textPaint.setTextSize(32f);
        textPaint.setAntiAlias(true);

        // Line Paint
        linePaint.setColor(Color.CYAN);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(8f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setAntiAlias(true);

        // Dot Paint
        dotPaint.setColor(Color.WHITE);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        // Pulse animation
        pulseAnimator = ValueAnimator.ofFloat(10f, 20f);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.addUpdateListener(animation -> {
            pulseRadius = (float) animation.getAnimatedValue();
            postInvalidate();
        });
        pulseAnimator.start();
    }

    public void setData(List<Double> newData, String unit) {
        synchronized (this) {
            this.data = new ArrayList<>();
            for (Double val : newData) {
                if (val != null && val > 0 && val < 1000) {
                    this.data.add(val);
                }
            }
            this.unit = unit;
        }
        postInvalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulseAnimator != null) pulseAnimator.cancel();
        super.onDetachedFromWindow();
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

            // 2. Draw Data Path (Smoothed with Cubic Bezier if you want, or just lineTo)
            graphPath.reset();
            for (int i = 0; i < data.size(); i++) {
                float x = width - ((data.size() - 1 - i) * width) / (data.size() - 1);
                float y = (float) (height - padding - ((data.get(i) - paddedMin) / paddedRange) * graphH);

                if (i == 0) graphPath.moveTo(x, y);
                else graphPath.lineTo(x, y);
            }
            canvas.drawPath(graphPath, linePaint);

            // 3. Draw Axis Labels (AFTER drawing path so they are on top)
            // Y-Axis (Values) - Align Right, positioned just to the left of the padding line
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.US, "%.1f", max), padding - 5f, padding + 10f, textPaint);
            canvas.drawText(String.format(Locale.US, "%.1f", min), padding - 5f, height - padding + 5f, textPaint);

            // X-Axis (Time) - Align Center to the grid lines
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("-6m", padding, height - 10f, textPaint);
            canvas.drawText("Now", width - padding, height - 10f, textPaint);

            // Unit Label - Top right
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(unit, width - 5f, padding - 10f, textPaint);

            // 4. Draw Current Value Indicator
            if (!data.isEmpty()) {
                // Always at the right edge
                float lastY = (float) (height - padding - ((data.get(data.size() - 1) - paddedMin) / paddedRange) * graphH);

                // Draw vertical "Now" marker
                gridPaint.setStrokeWidth(3f);
                canvas.drawLine(width, padding, width, height - padding, gridPaint);

                // Draw Pulsing Dot
                canvas.drawCircle(width, lastY, pulseRadius, dotPaint);

                // Draw text value next to the current point
                textPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(String.format(Locale.US, "%.1f", data.get(data.size() - 1)), width - 15f, lastY - 15f, textPaint);
            }

        }
    }
}