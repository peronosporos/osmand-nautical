package net.osmand.plus.views.mapwidgets.widgets;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

public class NauticalGraphView extends View {
    private List<Double> data;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NauticalGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.CYAN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
    }

    public void setData(List<Double> data) {
        this.data = data;
        invalidate(); // Redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (data == null || data.size() < 2) return;

        float width = getWidth();
        float height = getHeight();
        float stepX = width / (data.size() - 1);

        double max = 0;
        for (Double d : data) if (d > max) max = d;
        if (max == 0) max = 1;

        Path path = new Path();
        for (int i = 0; i < data.size(); i++) {
            float x = i * stepX;
            float y = height - (float) ((data.get(i) / max) * height);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }
}