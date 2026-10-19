package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoWatermarkConfigTest {
    @Test public void optionalFieldsIgnoreMissingNullAndNonStringValues() {
        assertEquals("", PhotoWatermarkConfig.fromJson(null).address);
        JSONObject json = new JSONObject();
        json.put("address", 123);
        json.put("person", new JSONObject());
        assertEquals("", PhotoWatermarkConfig.fromJson(json).address);
        assertEquals("", PhotoWatermarkConfig.fromJson(json).person);
        json.put("address", null);
        json.put("person", " \t\r\n\u3000");
        assertEquals("", PhotoWatermarkConfig.fromJson(json).address);
        assertEquals("", PhotoWatermarkConfig.fromJson(json).person);
    }

    @Test public void customTextIsNormalizedAndFrozen() {
        JSONObject json = new JSONObject();
        json.put("address", "  上海\n\r\t浦东\u3000 园区  ");
        json.put("person", " 张三  李四 ");
        PhotoWatermarkConfig config = PhotoWatermarkConfig.fromJson(json);
        json.put("address", "changed");
        assertEquals("上海 浦东 园区", config.address);
        assertEquals("张三 李四", config.person);
        assertFalse(config.enabled);
    }

    @Test public void limitsCountUnicodeCodePointsWithoutSplittingSurrogates() {
        JSONObject json = new JSONObject();
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 101; i++) longText.append("\uD83D\uDE00");
        json.put("address", longText.toString());
        json.put("person", longText.toString());
        PhotoWatermarkConfig config = PhotoWatermarkConfig.fromJson(json);
        assertEquals(100, config.address.codePointCount(0, config.address.length()));
        assertEquals(30, config.person.codePointCount(0, config.person.length()));
        assertTrue(config.address.endsWith("\uD83D\uDE00…"));
        assertTrue(config.person.endsWith("\uD83D\uDE00…"));
    }

    @Test public void watermarkIsOptInAndIndependentOfCompression() {
        assertFalse(PhotoWatermarkConfig.fromJson(null).enabled);
        assertFalse(PhotoWatermarkConfig.fromJson(new JSONObject()).enabled);
        JSONObject json = new JSONObject();
        json.put("enabled", true);
        assertTrue(PhotoWatermarkConfig.fromJson(json).enabled);
        assertFalse(PhotoCompressionConfig.fromJson(null).enabled);
        json.put("enabled", false);
        assertFalse(PhotoWatermarkConfig.fromJson(json).enabled);
    }

    @Test public void captureTimeUsesExplicitZoneAndSurvivesLaterZoneChanges() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        PhotoCaptureTime capture = new PhotoCaptureTime(0L, zone);
        zone.setRawOffset(0);
        assertEquals(0L, capture.timestamp);
        assertEquals("1970-01-01 08:00:00", capture.time);
        assertEquals("Asia/Shanghai", capture.timeZone);
        assertEquals("1970-01-01 00:00:00", new PhotoCaptureTime(0L, TimeZone.getTimeZone("UTC")).time);
    }

    @Test public void separateShotsKeepTheirOwnTimeAcrossMidnight() {
        TimeZone zone = TimeZone.getTimeZone("UTC");
        assertEquals("1970-01-01 23:59:59", new PhotoCaptureTime(86399000L, zone).time);
        assertEquals("1970-01-02 00:00:00", new PhotoCaptureTime(86400000L, zone).time);
    }
}
