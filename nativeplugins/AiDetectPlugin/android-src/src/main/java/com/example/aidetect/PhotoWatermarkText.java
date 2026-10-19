package com.example.aidetect;

import java.util.ArrayList;
import java.util.List;

/** Bounded text layout; measures with the actual drawing font in production. */
final class PhotoWatermarkText {
    interface Measure { float width(String text); }

    static List<String> lines(PhotoWatermarkConfig config, String time, float width, Measure measure) {
        List<String> lines = new ArrayList<>();
        if (!config.address.isEmpty()) wrap("地址：" + config.address, width, 3, measure, lines);
        if (!config.person.isEmpty()) lines.add(ellipsize("人员：" + config.person, width, measure));
        lines.add(time);
        return lines;
    }

    private static void wrap(String text, float width, int maximum, Measure measure, List<String> lines) {
        for (int line = 0; line < maximum && !text.isEmpty(); line++) {
            if (line == maximum - 1 || measure.width(text) <= width) {
                lines.add(ellipsize(text, width, measure));
                return;
            }
            int end = 0;
            while (end < text.length()) {
                int next = text.offsetByCodePoints(end, 1);
                if (measure.width(text.substring(0, next)) > width) break;
                end = next;
            }
            if (end == 0) { lines.add(ellipsize(text, width, measure)); return; }
            lines.add(text.substring(0, end).trim());
            text = text.substring(end).trim();
        }
    }

    private static String ellipsize(String text, float width, Measure measure) {
        if (measure.width(text) <= width) return text;
        if (measure.width("…") > width) return "";
        int end = text.length();
        while (end > 0 && measure.width(text.substring(0, end).trim() + "…") > width) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end).trim() + "…";
    }
}
