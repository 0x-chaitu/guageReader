package com.example.guageReader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

/** Overlay where face bounds are drawn. */
public class RectOverlay extends View {

    private Bitmap bitmap;
    private final Paint paint;

    public RectOverlay(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        this.paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(ContextCompat.getColor(context, android.R.color.black));
        paint.setStrokeWidth(10f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 20, 0, null);
        }
    }

    public void drawBounds(
             Bitmap bitmap
    ) {
        this.bitmap = bitmap;
        invalidate();
    }
}
