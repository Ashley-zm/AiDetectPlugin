package com.example.aidetect;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ExifInterface;
import android.os.Bundle;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Device test runner requiring no UI, camera permission, models or test framework. */
public class PhotoCompressionInstrumentation extends Instrumentation {
    private File directory;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            directory = new File(getContext().getCacheDir(), "compression-tests-" + System.nanoTime());
            check(directory.mkdirs(), "test directory");
            runChecks();
            PhotoWatermarkChecks.run(directory);
            result.putString("stream", "PASS: compression and watermark; disabled, skipped, resize, retention, EXIF 2-8, time, pixels, metadata, failure, interruption, temporary cleanup\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (directory != null) {
                File[] files = directory.listFiles();
                if (files != null) for (File file : files) file.delete();
                directory.delete();
            }
        }
    }

    private void runChecks() throws Exception {
        JSONObject options = new JSONObject();
        options.put("enabled", true);
        options.put("maxLongEdge", 640);
        PhotoCompressionConfig enabled = PhotoCompressionConfig.fromJson(options);
        File large = photo(1200, 800, false);
        long originalLength = large.length();
        PhotoCompressor.Result disabled = PhotoCompressor.process(large, PhotoCompressionConfig.fromJson(null));
        check(disabled.file.equals(large) && "disabled".equals(disabled.status), "disabled passthrough");
        PhotoCompressor.Result resized = PhotoCompressor.process(large, enabled);
        check("compressed".equals(resized.status) && resized.width == 640 && resized.height == 427, "aspect ratio resize");
        check(large.exists() && large.length() == originalLength && !large.equals(resized.file), "source preserved");
        JSONObject metadata = new JSONObject();
        resized.putInto(metadata);
        check(metadata.getLongValue("fileSize") == resized.file.length(), "metadata size");
        check(metadata.getString("originalPath").equals(large.getAbsolutePath()), "metadata original path");
        options.put("keepOriginal", false);
        PhotoCompressionConfig discardOriginal = PhotoCompressionConfig.fromJson(options);
        File disposable = photo(1200, 800, false);
        PhotoCompressor.Result discarded = PhotoCompressor.process(disposable, discardOriginal);
        check(discarded.file.exists() && !disposable.exists() && !discarded.originalRetained, "explicit discard after valid output");
        JSONObject discardedMetadata = new JSONObject();
        discarded.putInto(discardedMetadata);
        check("".equals(discardedMetadata.getString("originalPath")), "no stale original path");
        File passthrough = photo(320, 200, false);
        check(PhotoCompressor.process(passthrough, discardOriginal).file.equals(passthrough)
                && passthrough.exists(), "skip never deletes sole image");
        File bad = File.createTempFile("bad", ".jpg", directory);
        check("failed".equals(PhotoCompressor.process(bad, discardOriginal).status) && bad.exists(), "failure preserves source");
        options.put("keepOriginal", true);
        File small = photo(320, 200, false);
        PhotoCompressor.Result skipped = PhotoCompressor.process(small, enabled);
        check("skipped".equals(skipped.status) && skipped.file.equals(small), "small image skipped");
        for (int orientation = 2; orientation <= 8; orientation++) {
            File rotated = photo(320, 200, false);
            ExifInterface exif = new ExifInterface(rotated.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
            exif.saveAttributes();
            PhotoCompressor.Result corrected = PhotoCompressor.process(rotated, enabled);
            check("compressed".equals(corrected.status), "orientation processed " + orientation);
            check(corrected.width == (orientation >= 5 ? 200 : 320), "orientation width " + orientation);
            Bitmap bitmap = BitmapFactory.decodeFile(corrected.file.getAbsolutePath());
            check(bitmap != null, "output decodes");
            // Original quadrants: red, green / blue, yellow. Expected upper-left after EXIF.
            int[] expected = {0, 0, Color.GREEN, Color.YELLOW, Color.BLUE, Color.RED, Color.BLUE, Color.YELLOW, Color.GREEN};
            int actual = bitmap.getPixel(bitmap.getWidth() / 4, bitmap.getHeight() / 4);
            int color = expected[orientation];
            check(Math.abs(Color.red(actual) - Color.red(color)) < 45
                    && Math.abs(Color.green(actual) - Color.green(color)) < 45
                    && Math.abs(Color.blue(actual) - Color.blue(color)) < 45, "orientation pixels " + orientation);
            bitmap.recycle();
        }
        options.put("targetSizeKB", 100);
        options.put("quality", 100);
        options.put("minQuality", 100);
        options.put("maxLongEdge", 1024);
        PhotoCompressor.Result soft = PhotoCompressor.process(photo(1200, 1200, true), PhotoCompressionConfig.fromJson(options));
        check("compressed".equals(soft.status) && !soft.targetMet, "quality floor permits soft target miss");
        File invalid = File.createTempFile("invalid", ".jpg", directory);
        PhotoCompressor.Result failed = PhotoCompressor.process(invalid, enabled);
        check("failed".equals(failed.status) && failed.file.equals(invalid), "failure fallback");
        for (File file : directory.listFiles()) check(!file.getName().endsWith(".tmp"), "no temporary output leaks");
    }

    private File photo(int width, int height, boolean noise) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Random random = new Random(42);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) row[x] = noise ? Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                    : (y < height / 2 ? (x < width / 2 ? Color.RED : Color.GREEN) : (x < width / 2 ? Color.BLUE : Color.YELLOW));
            bitmap.setPixels(row, 0, width, 0, y, width, 1);
        }
        File file = File.createTempFile("source", ".jpg", directory);
        try (FileOutputStream output = new FileOutputStream(file)) { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output), "fixture encode"); }
        finally { bitmap.recycle(); }
        return file;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
