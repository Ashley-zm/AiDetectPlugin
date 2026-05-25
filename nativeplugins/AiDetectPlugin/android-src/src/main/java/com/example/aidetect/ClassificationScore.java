package com.example.aidetect;

public class ClassificationScore {

    public final int classId;
    public final String label;
    public final float score;

    public ClassificationScore(int classId, String label, float score) {
        this.classId = classId;
        this.label = label;
        this.score = score;
    }
}
