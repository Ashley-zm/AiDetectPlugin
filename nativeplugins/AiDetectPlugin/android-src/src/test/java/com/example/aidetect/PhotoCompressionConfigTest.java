package com.example.aidetect;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoCompressionConfigTest {
    @Test public void omittedConfigPreservesLegacyBehavior() {
        PhotoCompressionConfig config = PhotoCompressionConfig.fromJson(null);
        assertFalse(config.enabled);
        assertTrue(config.keepOriginal);
        assertEquals(2048, config.maxLongEdge);
        assertEquals(1048576L, config.targetBytes);
        assertEquals(85, config.quality);
        assertEquals(70, config.minQuality);
    }

    @Test public void originalDeletionRequiresExplicitOptOut() {
        JSONObject json = new JSONObject();
        json.put("enabled", true);
        assertTrue(PhotoCompressionConfig.fromJson(json).keepOriginal);
        json.put("keepOriginal", false);
        assertFalse(PhotoCompressionConfig.fromJson(json).keepOriginal);
    }

    @Test public void invalidRangesCannotCauseUnboundedAllocationOrEncoding() {
        JSONObject json = new JSONObject();
        json.put("maxLongEdge", Integer.MAX_VALUE);
        json.put("targetSizeKB", Integer.MAX_VALUE);
        json.put("quality", -1);
        json.put("minQuality", 100);
        PhotoCompressionConfig config = PhotoCompressionConfig.fromJson(json);
        assertEquals(4096, config.maxLongEdge);
        assertEquals(20971520L, config.targetBytes);
        assertEquals(1, config.quality);
        assertEquals(1, config.minQuality);
        json.put("maxLongEdge", -100);
        json.put("targetSizeKB", 0);
        config = PhotoCompressionConfig.fromJson(json);
        assertEquals(640, config.maxLongEdge);
        assertEquals(102400L, config.targetBytes);
    }

    @Test public void bridgeStringNumbersAndMalformedValuesAreHandled() {
        JSONObject json = new JSONObject();
        json.put("enabled", true);
        json.put("maxLongEdge", "1600");
        json.put("quality", "invalid");
        json.put("minQuality", "90");
        PhotoCompressionConfig config = PhotoCompressionConfig.fromJson(json);
        assertTrue(config.enabled);
        assertEquals(1600, config.maxLongEdge);
        assertEquals(85, config.quality);
        assertEquals(85, config.minQuality);
    }
}
