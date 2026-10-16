package com.example.aidetect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ExifInterface;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.zip.CRC32;

/** Synthetic image checks executed by PhotoCompressionInstrumentation on an Android device. */
final class PhotoWatermarkChecks {
    static void run(File directory) throws Exception {
        JSONObject options = new JSONObject();
        options.put("enabled", true);
        PhotoWatermarkConfig watermark = PhotoWatermarkConfig.fromJson(options);
        PhotoCompressionConfig off = PhotoCompressionConfig.fromJson(null);
        PhotoCaptureTime capture = new PhotoCaptureTime(0, TimeZone.getTimeZone("Asia/Shanghai"));
        File original = photo(directory, 800, 600);
        long checksum = checksum(original);
        PhotoCompressor.Result source = PhotoCompressor.process(original, off);
        PhotoCompressor.Result disabled = PhotoWatermarker.process(source, PhotoWatermarkConfig.fromJson(null), off, capture);
        check(disabled.file.equals(original) && checksum(original) == checksum && "disabled".equals(disabled.watermarkStatus), "disabled preserves bytes");
        PhotoCompressor.Result applied = PhotoWatermarker.process(source, watermark, off, capture);
        check("applied".equals(applied.watermarkStatus) && !applied.file.equals(original), "watermark without compression");
        check(checksum(original) == checksum && applied.width == 800 && applied.height == 600, "original and dimensions preserved");
        Bitmap image = BitmapFactory.decodeFile(applied.file.getAbsolutePath());
        check(image != null, "watermark output decodes");
        int white = 0;
        for (int y = 540; y < 590; y++) for (int x = 300; x < 790; x++) {
            int pixel = image.getPixel(x, y);
            if (Color.red(pixel) > 210 && Color.green(pixel) > 210 && Color.blue(pixel) > 210) white++;
        }
        image.recycle();
        check(white > 30, "timestamp pixels in lower right");
        JSONObject metadata = new JSONObject();
        applied.putInto(metadata);
        check(metadata.getLongValue("fileSize") == applied.file.length(), "final file size");
        check(metadata.getBooleanValue("compressionTargetMet") == (applied.file.length() <= off.targetBytes), "final soft target");
        check("1970-01-01 08:00:00".equals(metadata.getString("watermarkText")) && metadata.getLongValue("captureTimestamp") == 0, "frozen capture time");
        CapturedPhoto item = new CapturedPhoto(1, original.getAbsolutePath(), "pass", "", 0, "", "", "photo_only", 100, "later").withImageInfo(applied);
        JSONObject multi = JsonUtils.multiSnapshotResult(Arrays.asList(item));
        check(applied.file.getAbsolutePath().equals(multi.getString("imagePath")), "multi top-level final path");
        JSONObject child = multi.getJSONArray("images").getJSONObject(0);
        check(item.path.equals(child.getString("path")) && "applied".equals(child.getString("watermarkStatus")), "multi item metadata");

        options.put("maxLongEdge", 640);
        for (boolean keep : new boolean[]{true, false}) {
            options.put("keepOriginal", keep);
            PhotoCompressionConfig compression = PhotoCompressionConfig.fromJson(options);
            File input = photo(directory, 1200, 800);
            PhotoCompressor.Result compressed = PhotoCompressor.process(input, compression);
            PhotoCompressor.Result result = PhotoWatermarker.process(compressed, watermark, compression, capture);
            check("applied".equals(result.watermarkStatus) && result.width == 640, "compression plus watermark");
            check(input.exists() == keep && result.originalRetained == keep, "retention policy");
            check(!compressed.file.exists() && result.file.exists(), "intermediate replaced after valid output");
        }
        File small = photo(directory, 320, 200);
        PhotoCompressionConfig compression = PhotoCompressionConfig.fromJson(options);
        PhotoCompressor.Result skipped = PhotoCompressor.process(small, compression);
        check("skipped".equals(skipped.status), "small compression skip");
        check("applied".equals(PhotoWatermarker.process(skipped, watermark, compression, capture).watermarkStatus), "skip still watermarks");
        for (int orientation = 2; orientation <= 8; orientation++) {
            File input = photo(directory, 800, 600);
            ExifInterface exif = new ExifInterface(input.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
            exif.saveAttributes();
            PhotoCompressor.Result result = PhotoWatermarker.process(PhotoCompressor.process(input, off), watermark, off, capture);
            check("applied".equals(result.watermarkStatus) && result.width == (orientation >= 5 ? 600 : 800), "EXIF output dimensions " + orientation);
            Bitmap before = PhotoWatermarker.decodeOriented(input.getAbsolutePath(), false);
            Bitmap after = BitmapFactory.decodeFile(result.file.getAbsolutePath());
            check(before.getWidth() == after.getWidth() && before.getHeight() == after.getHeight(), "inference orientation " + orientation);
            int[] expected = {0, 0, Color.GREEN, Color.BLUE, Color.BLUE, Color.RED, Color.BLUE, Color.BLUE, Color.GREEN};
            int actual = after.getPixel(after.getWidth() / 4, after.getHeight() / 4);
            check(Math.abs(Color.red(actual) - Color.red(expected[orientation])) < 45
                    && Math.abs(Color.green(actual) - Color.green(expected[orientation])) < 45
                    && Math.abs(Color.blue(actual) - Color.blue(expected[orientation])) < 45, "EXIF pixels " + orientation);
            before.recycle(); after.recycle();
        }
        File invalid = File.createTempFile("watermark-invalid", ".jpg", directory);
        PhotoCompressor.Result failed = PhotoWatermarker.process(PhotoCompressor.process(invalid, off), watermark, off, capture);
        check("failed".equals(failed.watermarkStatus) && failed.file.equals(invalid) && invalid.exists(), "failure fallback");
        Thread.currentThread().interrupt();
        try {
            PhotoCompressor.Result interrupted = PhotoWatermarker.process(source, watermark, off, capture);
            check("failed".equals(interrupted.watermarkStatus) && original.exists(), "interruption preserves source");
        } finally { Thread.interrupted(); }
        for (File file : directory.listFiles()) check(!file.getName().endsWith(".tmp"), "watermark temporary cleanup");
    }

    private static File photo(File directory, int width, int height) throws Exception {
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.BLUE);
        for (int y = 0; y < height / 2; y++) for (int x = 0; x < width; x++) image.setPixel(x, y, x < width / 2 ? Color.RED : Color.GREEN);
        File file = File.createTempFile("watermark-source", ".jpg", directory);
        try (FileOutputStream output = new FileOutputStream(file)) { check(image.compress(Bitmap.CompressFormat.JPEG, 100, output), "fixture encode"); }
        finally { image.recycle(); }
        return file;
    }

    private static long checksum(File file) throws Exception {
        CRC32 crc = new CRC32();
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = stream.read(bytes)) != -1) crc.update(bytes, 0, count);
        }
        return crc.getValue();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
