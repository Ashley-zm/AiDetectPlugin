package com.example.aidetect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Processes saved photos with configurable source retention. Never used for realtime frames. */
public final class PhotoCompressor {
    private static final String TAG = "AiDetectPlugin";
    private PhotoCompressor() { }

    public static final class Result {
        public final File file;
        public final File original;
        public final long originalSize;
        public final long fileSize;
        public final int width;
        public final int height;
        public final String status;
        public final boolean targetMet;
        public final boolean originalRetained;

        private Result(File file, File original, long originalSize, int width, int height,
                       String status, long targetBytes) {
            this.file = file;
            this.original = original;
            this.originalRetained = original.exists();
            this.originalSize = originalSize;
            this.fileSize = file.length();
            this.width = width;
            this.height = height;
            this.status = status;
            this.targetMet = fileSize <= targetBytes;
        }

        public void putInto(JSONObject json) {
            json.put("fileSize", fileSize);
            json.put("originalSize", originalSize);
            json.put("width", width);
            json.put("height", height);
            json.put("compressed", "compressed".equals(status));
            json.put("compressionStatus", status);
            json.put("compressionTargetMet", targetMet);
            json.put("originalPath", originalRetained ? original.getAbsolutePath() : "");
            json.put("originalRetained", originalRetained);
        }
    }

    public static Result process(File source, PhotoCompressionConfig config) {
        long originalSize = source.length();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        Result fallback = new Result(source, source, originalSize, Math.max(0, bounds.outWidth),
                Math.max(0, bounds.outHeight), config.enabled ? "failed" : "disabled", config.targetBytes);
        if (!config.enabled) return fallback;
        File pending = null;
        Bitmap bitmap = null;
        try {
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Invalid photo bounds");
            int orientation = new ExifInterface(source.getAbsolutePath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            boolean needsOrientation = orientation >= 2 && orientation <= 8;
            if (originalSize <= config.targetBytes
                    && Math.max(bounds.outWidth, bounds.outHeight) <= config.maxLongEdge && !needsOrientation) {
                return new Result(source, source, originalSize, bounds.outWidth, bounds.outHeight,
                        "skipped", config.targetBytes);
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (decode.inSampleSize * 2) >= config.maxLongEdge) {
                decode.inSampleSize *= 2;
            }
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), decode);
            if (bitmap == null) throw new IOException("Photo decode failed");
            float scale = Math.min(1F, config.maxLongEdge / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
            if (scale < 1F) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }
            Matrix matrix = orientationMatrix(orientation);
            if (!matrix.isIdentity()) {
                Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (oriented != bitmap) bitmap.recycle();
                bitmap = oriented;
            }
            pending = File.createTempFile("detect_compressed_", ".tmp", source.getParentFile());
            int quality = config.quality;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Photo processing interrupted");
                try (FileOutputStream stream = new FileOutputStream(pending)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) throw new IOException("JPEG encode failed");
                }
                if (pending.length() <= config.targetBytes || quality == config.minQuality) break;
                quality = Math.max(config.minQuality, quality - 5);
            }
            if (pending.length() <= 0) throw new IOException("Empty JPEG output");
            BitmapFactory.Options verified = new BitmapFactory.Options();
            verified.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(pending.getAbsolutePath(), verified);
            if (verified.outWidth != bitmap.getWidth() || verified.outHeight != bitmap.getHeight()) {
                throw new IOException("JPEG dimensions failed validation");
            }
            if (pending.length() >= originalSize && !needsOrientation
                    && Math.max(bounds.outWidth, bounds.outHeight) <= config.maxLongEdge) {
                return new Result(source, source, originalSize, bounds.outWidth, bounds.outHeight, "skipped", config.targetBytes);
            }
            // Decode pixels as well as bounds before allowing disposal of the source.
            BitmapFactory.Options checkOptions = new BitmapFactory.Options();
            checkOptions.inSampleSize = 1;
            while (Math.max(verified.outWidth, verified.outHeight) / checkOptions.inSampleSize > 256) {
                checkOptions.inSampleSize *= 2;
            }
            Bitmap checkBitmap = BitmapFactory.decodeFile(pending.getAbsolutePath(), checkOptions);
            if (checkBitmap == null) throw new IOException("JPEG pixel validation failed");
            checkBitmap.recycle();
            File output = new File(pending.getAbsolutePath().replace(".tmp", ".jpg"));
            if (!pending.renameTo(output)) throw new IOException("Cannot finalize JPEG output");
            if (!config.keepOriginal) {
                try {
                    if (!source.delete()) Log.w(TAG, "Original retained: could not delete " + source);
                } catch (SecurityException error) {
                    Log.w(TAG, "Original retained: deletion denied", error);
                }
            }
            return new Result(output, source, originalSize, verified.outWidth, verified.outHeight,
                    "compressed", config.targetBytes);
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            Log.w(TAG, "Photo compression failed; using original photo", error);
            return fallback;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (pending != null && pending.exists() && !pending.delete()) {
                Log.w(TAG, "Could not delete incomplete compression file: " + pending);
            }
        }
    }

    private static Matrix orientationMatrix(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case 2: matrix.setScale(-1, 1); break;
            case 3: matrix.setRotate(180); break;
            case 4: matrix.setScale(1, -1); break;
            case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case 6: matrix.setRotate(90); break;
            case 7: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case 8: matrix.setRotate(-90); break;
            default: break;
        }
        return matrix;
    }
}
