package com.example.aidetect;

import android.content.Context;

import java.io.InputStream;

public final class AssetModelPathUtils {

    private AssetModelPathUtils() {
    }

    public static String resolveParamPath(Context context, ModelConfig config) throws DetectException {
        String explicit = trim(config.modelPath);
        if (explicit.endsWith(".param")) {
            assertAssetExists(context, explicit, DetectErrorCode.NCNN_MODEL_LOAD_FAILED);
            return explicit;
        }
        return findByExtension(context, explicit, ".param");
    }

    public static String resolveBinPath(Context context, ModelConfig config, String paramPath) throws DetectException {
        String explicit = trim(config.binPath);
        if (explicit.length() > 0) {
            assertAssetExists(context, explicit, DetectErrorCode.NCNN_MODEL_LOAD_FAILED);
            return explicit;
        }

        String modelPath = trim(config.modelPath);
        if (modelPath.endsWith(".bin")) {
            assertAssetExists(context, modelPath, DetectErrorCode.NCNN_MODEL_LOAD_FAILED);
            return modelPath;
        }

        if (paramPath.endsWith(".param")) {
            String inferred = paramPath.substring(0, paramPath.length() - 6) + ".bin";
            if (assetExists(context, inferred)) {
                return inferred;
            }
        }
        return findByExtension(context, modelPath, ".bin");
    }

    public static void assertAssetExists(Context context, String assetPath, String code) throws DetectException {
        if (!assetExists(context, assetPath)) {
            throw new DetectException(code, "模型文件不存在：" + assetPath);
        }
    }

    public static boolean assetExists(Context context, String assetPath) {
        String path = trim(assetPath);
        if (path.length() == 0) {
            return false;
        }

        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(path);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String findByExtension(Context context, String assetDir, String extension) throws DetectException {
        String dir = trim(assetDir);
        if (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        if (dir.length() == 0) {
            throw new DetectException(DetectErrorCode.NCNN_MODEL_LOAD_FAILED, "模型目录为空");
        }

        try {
            String[] names = context.getAssets().list(dir);
            if (names == null) {
                names = new String[0];
            }
            for (String name : names) {
                if (name != null && name.toLowerCase().endsWith(extension)) {
                    return dir + "/" + name;
                }
            }
        } catch (Throwable throwable) {
            throw new DetectException(
                    DetectErrorCode.NCNN_MODEL_LOAD_FAILED,
                    "模型目录读取失败：" + dir,
                    throwable
            );
        }

        throw new DetectException(
                DetectErrorCode.NCNN_MODEL_LOAD_FAILED,
                "模型目录缺少 " + extension + " 文件：" + dir
        );
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
