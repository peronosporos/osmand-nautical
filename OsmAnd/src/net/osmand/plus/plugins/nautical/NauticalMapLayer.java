package net.osmand.plus.plugins.nautical;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import androidx.annotation.NonNull;
import java.util.List;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.plugins.nautical.engine.SignalKEngine;
import net.osmand.plus.views.layers.base.OsmandMapLayer;


public class NauticalMapLayer extends OsmandMapLayer {

    private RotatedTileBox lastKnownTileBox;
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PointF lastPressPoint = new PointF();

    public NauticalMapLayer(@NonNull Context context) {
        super(context);
        trailPaint.setColor(Color.MAGENTA);
        trailPaint.setStrokeWidth(10f);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public boolean drawInScreenPixels() {
        return true;
    }



    @Override
    public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
        this.lastKnownTileBox = tileBox;

        // Use the static getter to avoid private access errors
        SignalKEngine engine = NauticalPlugin.getEngine();

        // Perform null-check to satisfy the compiler and avoid crashes
        if (engine != null) {
            List<kotlin.Pair<Double, Double>> history = engine.getTrajectory();
            int size = history.size();
            if (size >= 2) {
                for (int i = 0; i < size - 1; i++) {
                    kotlin.Pair<Double, Double> p1 = history.get(i);
                    kotlin.Pair<Double, Double> p2 = history.get(i + 1);

                    // Calculate opacity: Newer points (higher index) are more opaque
                    int alpha = (int) (((float) i / (size - 1)) * 255);
                    trailPaint.setAlpha(alpha);

                    float x1 = tileBox.getPixXFromLatLon(p1.getFirst(), p1.getSecond());
                    float y1 = tileBox.getPixYFromLatLon(p1.getFirst(), p1.getSecond());
                    float x2 = tileBox.getPixXFromLatLon(p2.getFirst(), p2.getSecond());
                    float y2 = tileBox.getPixYFromLatLon(p2.getFirst(), p2.getSecond());

                    canvas.drawLine(x1, y1, x2, y2, trailPaint);
                }
            }
        }

    }

    public net.osmand.data.RotatedTileBox getTileBox() {
        return lastKnownTileBox;
    }

    @Override
    public boolean onSingleTap(@NonNull PointF point, @NonNull RotatedTileBox tileBox) {
        this.lastPressPoint.set(point);
        return false;
    }

    @Override
    public boolean onLongPressEvent(@NonNull PointF point, @NonNull RotatedTileBox tileBox) {
        float distance = (float) Math.hypot(point.x - lastPressPoint.x, point.y - lastPressPoint.y);
        if (distance > 20f) return false;

        SignalKEngine engine = NauticalPlugin.getEngine();
        if (engine != null) {
            engine.clearRoute();
        }

        double lat = tileBox.getLatFromPixel(point.x, point.y);
        double lon = tileBox.getLonFromPixel(point.x, point.y);
        NauticalPlugin.sendWaypoint(lat, lon);
        return true;
    }

    @Override
    public void destroyLayer() {
        super.destroyLayer();
    }
}