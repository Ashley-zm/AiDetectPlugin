package com.example.aidetect;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class LabelUtils {

    /**
     * Canonical labels.txt format: one label per line, with the zero-based line
     * number used as classId. The legacy "classId: label" format remains
     * readable for external target models.
     */
    private LabelUtils() {
    }

    public static Map<Integer, String> loadLabels(Context context, String labelPath) throws Exception {
        Map<Integer, String> labels = new HashMap<>();
        if (labelPath == null || labelPath.trim().length() == 0) {
            return labels;
        }

        InputStream inputStream = context.getAssets().open(labelPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        try {
            String line;
            int fallbackId = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }

                ParsedLabel parsedLabel = parseLine(line, fallbackId);
                labels.put(parsedLabel.classId, parsedLabel.label);
                fallbackId++;
            }
        } finally {
            reader.close();
        }
        return labels;
    }

    private static ParsedLabel parseLine(String line, int fallbackId) {
        int colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
            String idText = line.substring(0, colonIndex).trim();
            String label = line.substring(colonIndex + 1).trim();
            try {
                return new ParsedLabel(Integer.parseInt(idText), label.length() == 0 ? "class_" + idText : label);
            } catch (NumberFormatException ignored) {
                return new ParsedLabel(fallbackId, line);
            }
        }

        return new ParsedLabel(fallbackId, line);
    }

    private static final class ParsedLabel {
        final int classId;
        final String label;

        ParsedLabel(int classId, String label) {
            this.classId = classId;
            this.label = label;
        }
    }
}
