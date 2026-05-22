package com.example.aidetect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class ImageProxyBitmapConverter {

    private byte[] nv21Buffer;
    private byte[] yRowBuffer;
    private final ByteArrayOutputStream jpegOutputStream = new ByteArrayOutputStream(512 * 1024);
    private final Matrix rotationMatrix = new Matrix();

    public Bitmap toBitmap(@NonNull ImageProxy imageProxy) throws DetectException {
        try {
            if (imageProxy.getFormat() != ImageFormat.YUV_420_888) {
                throw new IllegalArgumentException("Unsupported ImageProxy format: " + imageProxy.getFormat());
            }

            byte[] nv21 = yuv420ToNv21(imageProxy);
            YuvImage yuvImage = new YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    null
            );

            jpegOutputStream.reset();
            boolean compressed = yuvImage.compressToJpeg(
                    new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                    88,
                    jpegOutputStream
            );
            if (!compressed) {
                throw new IllegalStateException("YUV compressToJpeg returned false");
            }

            byte[] jpegBytes = jpegOutputStream.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
            if (bitmap == null) {
                throw new IllegalStateException("BitmapFactory.decodeByteArray returned null");
            }

            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            if (rotationDegrees == 0) {
                return bitmap;
            }

            rotationMatrix.reset();
            rotationMatrix.postRotate(rotationDegrees);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    rotationMatrix,
                    true
            );
            bitmap.recycle();
            return rotated;
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.IMAGE_CONVERT_FAILED,
                    "ImageProxy 转 Bitmap 失败：" + throwable.getMessage(),
                    throwable
            );
        }
    }

    private byte[] yuv420ToNv21(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy yPlane = imageProxy.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = imageProxy.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = imageProxy.getPlanes()[2];

        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        int ySize = width * height;
        int requiredSize = ySize + (ySize / 2);
        if (nv21Buffer == null || nv21Buffer.length < requiredSize) {
            nv21Buffer = new byte[requiredSize];
        }

        copyYPlane(yPlane.getBuffer(), nv21Buffer, width, height, yPlane.getRowStride());
        copyUvPlanes(
                uPlane.getBuffer(),
                vPlane.getBuffer(),
                nv21Buffer,
                width,
                height,
                uPlane.getRowStride(),
                uPlane.getPixelStride(),
                vPlane.getRowStride(),
                vPlane.getPixelStride(),
                ySize
        );

        return nv21Buffer;
    }

    private void copyYPlane(ByteBuffer yBuffer, byte[] output, int width, int height, int rowStride) {
        yBuffer.rewind();
        int outputOffset = 0;
        if (yRowBuffer == null || yRowBuffer.length < rowStride) {
            yRowBuffer = new byte[rowStride];
        }

        for (int rowIndex = 0; rowIndex < height; rowIndex++) {
            int length = Math.min(rowStride, yBuffer.remaining());
            yBuffer.get(yRowBuffer, 0, length);
            System.arraycopy(yRowBuffer, 0, output, outputOffset, width);
            outputOffset += width;
        }
    }

    private static void copyUvPlanes(
            ByteBuffer uBuffer,
            ByteBuffer vBuffer,
            byte[] output,
            int width,
            int height,
            int uRowStride,
            int uPixelStride,
            int vRowStride,
            int vPixelStride,
            int outputOffset
    ) {
        uBuffer.rewind();
        vBuffer.rewind();
        int chromaHeight = height / 2;
        int chromaWidth = width / 2;

        for (int row = 0; row < chromaHeight; row++) {
            for (int col = 0; col < chromaWidth; col++) {
                int uIndex = row * uRowStride + col * uPixelStride;
                int vIndex = row * vRowStride + col * vPixelStride;
                output[outputOffset++] = vBuffer.get(vIndex);
                output[outputOffset++] = uBuffer.get(uIndex);
            }
        }
    }
}
