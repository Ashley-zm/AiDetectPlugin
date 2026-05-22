package com.example.aidetect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DetectOverlayView extends View {

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF labelBackground = new RectF();
    private List<DetectionBox> boxes = Collections.emptyList();

    public DetectOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(2.5F));
        boxPaint.setColor(0xFF22C55E);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(dp(13F));

        labelBackgroundPaint.setStyle(Paint.Style.FILL);
        labelBackgroundPaint.setColor(0xCC111827);
    }

    public void setResults(@Nullable List<DetectionBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            this.boxes = Collections.emptyList();
        } else {
            this.boxes = new ArrayList<>(boxes);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (DetectionBox box : boxes) {
            canvas.drawRect(box.left, box.top, box.right, box.bottom, boxPaint);
            drawLabel(canvas, box);
        }
    }

    private void drawLabel(Canvas canvas, DetectionBox box) {
        String label = String.format(Locale.US, "%s %.2f", box.label, box.score);
        float paddingH = dp(6F);
        float paddingV = dp(4F);
        float textWidth = textPaint.measureText(label);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;
        float labelLeft = box.left;
        float labelTop = Math.max(0F, box.top - textHeight - paddingV * 2F);
        float labelRight = Math.min(getWidth(), labelLeft + textWidth + paddingH * 2F);
        float labelBottom = labelTop + textHeight + paddingV * 2F;

        labelBackground.set(labelLeft, labelTop, labelRight, labelBottom);
        canvas.drawRect(labelBackground, labelBackgroundPaint);
        canvas.drawText(label, labelLeft + paddingH, labelBottom - paddingV - metrics.descent, textPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
