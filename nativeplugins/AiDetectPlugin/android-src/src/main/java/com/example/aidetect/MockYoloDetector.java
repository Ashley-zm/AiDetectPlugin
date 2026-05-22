package com.example.aidetect;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MockYoloDetector implements VisionModel {

    private static final String LABEL_PERSON = "person";
    private static final String LABEL_TARGET = "target";

    private final Random random = new Random();
    private DetectConfig config;
    private boolean initialized = false;

    @Override
    public void init(Context context, DetectConfig config) {
        this.config = config;
        this.initialized = true;
    }

    @Override
    public VisionResult infer(Bitmap bitmap) {
        if (!initialized) {
            throw new IllegalStateException("MockYoloDetector is not initialized");
        }

        int safeWidth = Math.max(1, bitmap == null ? config.inputSize : bitmap.getWidth());
        int safeHeight = Math.max(1, bitmap == null ? config.inputSize : bitmap.getHeight());
        return createMockResult(config, safeWidth, safeHeight, random);
    }

    @Override
    public void release() {
        initialized = false;
        config = null;
    }

    static VisionResult createMockResult(DetectConfig config, int width, int height, Random random) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int count = 1 + random.nextInt(2);
        List<DetectionBox> boxes = new ArrayList<>(count);
        boolean hasTarget = false;

        for (int i = 0; i < count; i++) {
            boolean target = random.nextBoolean();
            String label = target ? LABEL_TARGET : LABEL_PERSON;
            int classId = target ? 1 : 0;
            float score = 0.6F + random.nextFloat() * 0.35F;

            float boxWidth = safeWidth * (0.22F + random.nextFloat() * 0.22F);
            float boxHeight = safeHeight * (0.18F + random.nextFloat() * 0.28F);
            float left = random.nextFloat() * Math.max(1F, safeWidth - boxWidth);
            float top = random.nextFloat() * Math.max(1F, safeHeight - boxHeight);
            float right = Math.min(safeWidth - 1F, left + boxWidth);
            float bottom = Math.min(safeHeight - 1F, top + boxHeight);

            if (target) {
                hasTarget = true;
            }

            boxes.add(new DetectionBox(classId, label, score, left, top, right, bottom));
        }

        return new VisionResult(
                true,
                config.modelType,
                config.engine,
                hasTarget,
                boxes,
                System.currentTimeMillis()
        );
    }
}
