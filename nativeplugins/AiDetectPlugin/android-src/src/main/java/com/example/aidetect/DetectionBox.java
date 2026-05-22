package com.example.aidetect;

public class DetectionBox {

    public final int classId;
    public final String label;
    public final float score;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public DetectionBox(
            int classId,
            String label,
            float score,
            float left,
            float top,
            float right,
            float bottom
    ) {
        this.classId = classId;
        this.label = label;
        this.score = score;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
