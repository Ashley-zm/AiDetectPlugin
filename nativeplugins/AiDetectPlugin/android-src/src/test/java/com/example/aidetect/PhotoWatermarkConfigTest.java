package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoWatermarkConfigTest {
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
