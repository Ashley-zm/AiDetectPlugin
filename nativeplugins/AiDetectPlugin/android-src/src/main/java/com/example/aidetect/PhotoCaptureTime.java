package com.example.aidetect;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Freeze both device time and timezone when dispatching the shutter request. */
public final class PhotoCaptureTime {
    public final long timestamp;
    public final String time;
    public final String timeZone;

    public PhotoCaptureTime(long timestamp, TimeZone zone) {
        this.timestamp = timestamp;
        this.timeZone = zone.getID();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(zone);
        this.time = format.format(new Date(timestamp));
    }

    public static PhotoCaptureTime now() {
        return new PhotoCaptureTime(System.currentTimeMillis(), TimeZone.getDefault());
    }
}
