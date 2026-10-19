package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoWatermarkTextTest {
    private static final PhotoWatermarkText.Measure MONO = text -> text.codePointCount(0, text.length());
    private static final String TIME = "2026-10-19 12:00:00";

    private PhotoWatermarkConfig config(String address, String person) {
        JSONObject json = new JSONObject();
        json.put("address", address);
        json.put("person", person);
        return PhotoWatermarkConfig.fromJson(json);
    }

    @Test public void emptyOptionsRetainTimestampOnly() {
        assertEquals(Arrays.asList(TIME), PhotoWatermarkText.lines(config("", ""), TIME, 20, MONO));
    }

    @Test public void independentFieldsOmitEmptyLabelsAndKeepTimeLast() {
        assertEquals(Arrays.asList("地址：上海", TIME), PhotoWatermarkText.lines(config("上海", null), TIME, 20, MONO));
        assertEquals(Arrays.asList("人员：张三", TIME), PhotoWatermarkText.lines(config(null, "张三"), TIME, 20, MONO));
        assertEquals(Arrays.asList("地址：上海", "人员：张三", TIME), PhotoWatermarkText.lines(config("上海", "张三"), TIME, 20, MONO));
    }

    @Test public void longTextIsBoundedAndEllipsizedWithoutTruncatingTimestamp() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 100; i++) text.append('中');
        List<String> lines = PhotoWatermarkText.lines(config(text.toString(), text.toString()), TIME, 20, MONO);
        assertEquals(5, lines.size());
        assertTrue(lines.get(2).endsWith("…"));
        assertTrue(lines.get(3).endsWith("…"));
        assertEquals(TIME, lines.get(4));
        for (String line : lines) assertTrue(MONO.width(line) <= 20);
    }

    @Test public void wrappingPreservesUnicodeCharactersAndExactFit() {
        String emoji = "\uD83D\uDE00";
        List<String> lines = PhotoWatermarkText.lines(config(emoji + emoji + emoji, ""), TIME, 5, MONO);
        assertEquals("地址：" + emoji + emoji, lines.get(0));
        assertEquals(emoji, lines.get(1));
        assertEquals(Arrays.asList("地址：上海", TIME), PhotoWatermarkText.lines(config("上海", ""), TIME, 5, MONO));
    }
}
