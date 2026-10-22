package com.example.aidetect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Runs only after snapshot inference. Never overwrites the inference/source image. */
public final class PhotoWatermarker {
    private static final String TAG = "AiDetectPlugin";
    private PhotoWatermarker() { }

    /** Also used before inference when watermarking is enabled, so image orientation agrees. */
    static Bitmap decodeOriented(String path, boolean mutable) throws IOException {
        int orientation = new ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inMutable = mutable;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IOException("Photo decode failed");
        try {
            Matrix matrix = PhotoCompressor.orientationMatrix(orientation);
            if (!matrix.isIdentity()) {
                Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (oriented != bitmap) bitmap.recycle();
                bitmap = oriented;
            }
            if (mutable && !bitmap.isMutable()) {
                Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                if (copy == null) throw new IOException("Cannot allocate watermark bitmap");
                bitmap.recycle();
                bitmap = copy;
            }
            return bitmap;
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            if (!bitmap.isRecycled()) bitmap.recycle();
            throw error;
        }
    }

    public static PhotoCompressor.Result process(PhotoCompressor.Result source,
            PhotoWatermarkConfig config, PhotoCompressionConfig compression, PhotoCaptureTime captureTime) {
        if (!config.enabled) return source.withWatermark(source.file, source.width, source.height,
                compression.targetBytes, "disabled", captureTime);
        Bitmap bitmap = null;
        File pending = null;
        try {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Watermark interrupted");
            bitmap = decodeOriented(source.file.getAbsolutePath(), true);
            String watermarkText = drawWatermark(bitmap, captureTime.time, config);
            pending = File.createTempFile("detect_watermarked_", ".tmp", source.file.getParentFile());
            int quality = compression.enabled ? compression.quality : 95;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Watermark interrupted");
                try (FileOutputStream output = new FileOutputStream(pending)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        throw new IOException("Watermark JPEG encode failed");
                    }
                }
                if (!compression.enabled || pending.length() <= compression.targetBytes
                        || quality <= compression.minQuality) break;
                quality = Math.max(compression.minQuality, quality - 5);
            }
            BitmapFactory.Options verified = new BitmapFactory.Options();
            verified.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(pending.getAbsolutePath(), verified);
            if (pending.length() == 0 || verified.outWidth != bitmap.getWidth()
                    || verified.outHeight != bitmap.getHeight()) throw new IOException("Invalid watermark output");
            BitmapFactory.Options pixels = new BitmapFactory.Options();
            pixels.inSampleSize = 1;
            while (Math.max(verified.outWidth, verified.outHeight) / pixels.inSampleSize > 256) pixels.inSampleSize *= 2;
            Bitmap check = BitmapFactory.decodeFile(pending.getAbsolutePath(), pixels);
            if (check == null) throw new IOException("Watermark output cannot decode");
            check.recycle();
            if (Thread.currentThread().isInterrupted()) throw new IOException("Watermark interrupted");
            File output = new File(pending.getAbsolutePath().replace(".tmp", ".jpg"));
            // Allocate result metadata while the fallback source is still intact.
            PhotoCompressor.Result result;
            if (!pending.renameTo(output)) throw new IOException("Cannot finalize watermark output");
            try {
                result = source.withWatermark(output, verified.outWidth, verified.outHeight,
                        compression.targetBytes, "applied", captureTime, watermarkText);
            } catch (RuntimeException | OutOfMemoryError error) {
                if (!output.delete()) Log.w(TAG, "Could not remove uncommitted watermark output: " + output);
                throw error;
            }
            // The compressed intermediate has already been inferred; retain the camera original
            // according to compression's existing policy. Track intermediates even if deletion fails.
            if (!source.file.equals(source.original)) {
                try {
                    if (!source.file.delete()) Log.w(TAG, "Could not remove watermark intermediate: " + source.file);
                } catch (SecurityException error) { Log.w(TAG, "Watermark intermediate retained", error); }
            }
            return result;
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            Log.w(TAG, "Watermark failed; using pre-watermark photo", error);
            return source.withWatermark(source.file, source.width, source.height,
                    compression.targetBytes, "failed", captureTime);
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (pending != null && pending.exists() && !pending.delete()) Log.w(TAG, "Incomplete watermark file: " + pending);
        }
    }

    private static String drawWatermark(Bitmap bitmap, String text, PhotoWatermarkConfig config) {
        float shortEdge = Math.min(bitmap.getWidth(), bitmap.getHeight());
        float margin = shortEdge * 0.025F;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setAlpha(204); // 80% opacity, shared by timestamp and optional fields.
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextSize(shortEdge * 0.035F);
        float availableWidth = bitmap.getWidth() - margin * 2;
        boolean custom = !config.address.isEmpty() || !config.person.isEmpty();
        if (custom) availableWidth = Math.min(availableWidth, bitmap.getWidth() * 0.7F);
        if (paint.measureText(text) > availableWidth) {
            paint.setTextSize(paint.getTextSize() * availableWidth / paint.measureText(text));
        }
        paint.setShadowLayer(Math.max(1F, shortEdge * 0.002F), 0, shortEdge * 0.001F, Color.BLACK);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        Canvas canvas = new Canvas(bitmap);
        if (!custom) {
            // Keep the timestamp-only position and sizing.
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(text, bitmap.getWidth() - margin,
                    bitmap.getHeight() - margin - metrics.bottom, paint);
            return text;
        }
        List<String> lines = PhotoWatermarkText.lines(config, text, availableWidth, paint::measureText);
        float lineHeight = (metrics.bottom - metrics.top) * 1.2F;
        float y = bitmap.getHeight() - margin - metrics.bottom - (lines.size() - 1) * lineHeight;
        paint.setTextAlign(Paint.Align.RIGHT);
        StringBuilder drawn = new StringBuilder();
        for (String line : lines) {
            canvas.drawText(line, bitmap.getWidth() - margin, y, paint);
            if (drawn.length() > 0) drawn.append('\n');
            drawn.append(line);
            y += lineHeight;
        }
        return drawn.toString();
    }
}
