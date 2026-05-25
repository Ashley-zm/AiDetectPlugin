package com.example.aidetect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

public class AiDetectPlugin extends UniModule {

    private static final String TAG = "AiDetectPlugin";
    private static final long FIXED_TIMESTAMP = 1710000000000L;

    @UniJSMethod(uiThread = true)
    public void test(JSONObject options, UniJSCallback callback) {
        Log.e(TAG, "test called, options=" + String.valueOf(options));
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("type", "plugin_test");
        result.put("message", "AiDetectPlugin 调用成功");
        result.put("timestamp", FIXED_TIMESTAMP);

        if (callback != null) {
            callback.invoke(result);
        }
    }

    @UniJSMethod(uiThread = true)
    public JSONObject startDetect(JSONObject options, UniJSCallback callback) {
        Log.e(TAG, "startDetect entry, options=" + String.valueOf(options));
        Log.i(TAG, "startDetect called, options=" + String.valueOf(options));
        try {
            DetectConfig.save(options);
            DetectConfig.snapshot().validateForStart();
            DetectCallbackManager.setCallback(callback);

            Context context = getContext();

            if (context == null) {
                JSONObject result = createResult(false, DetectErrorCode.CAMERA_BIND_FAILED, "无法获取 Android Context");
                invokeCallback(callback, result, false);
                return result;
            }

            String openError = openDetectActivity(context);
            if (openError != null) {
                JSONObject result = createResult(false, DetectErrorCode.CAMERA_BIND_FAILED, openError);
                invokeCallback(callback, result, false);
                return result;
            }

            JSONObject result = createResult(true, "activity_opened", "DetectActivity 已打开");
            invokeCallback(callback, result, true);
            return result;
        } catch (Throwable throwable) {
            Log.e(TAG, "startDetect failed", throwable);

            JSONObject result = createResult(false, errorCodeOf(throwable, DetectErrorCode.MODEL_LOAD_FAILED), messageOf(throwable));
            invokeCallback(callback, result, false);
            return result;
        }
    }

    @UniJSMethod(uiThread = true)
    public JSONObject startDetectSync(JSONObject options) {
        Log.e(TAG, "startDetectSync entry, options=" + String.valueOf(options));
        Log.i(TAG, "startDetectSync called, options=" + String.valueOf(options));
        try {
            DetectConfig.save(options);
            DetectConfig.snapshot().validateForStart();
            DetectCallbackManager.clearCallback();

            Context context = getContext();

            if (context == null) {
                return createResult(false, DetectErrorCode.CAMERA_BIND_FAILED, "无法获取 Android Context");
            }

            String openError = openDetectActivity(context);
            if (openError != null) {
                return createResult(false, DetectErrorCode.CAMERA_BIND_FAILED, openError);
            }

            JSONObject result = createResult(true, "activity_opened", "DetectActivity 已打开");
            return result;
        } catch (Throwable throwable) {
            Log.e(TAG, "startDetectSync failed", throwable);
            return createResult(false, errorCodeOf(throwable, DetectErrorCode.MODEL_LOAD_FAILED), messageOf(throwable));
        }
    }

    @UniJSMethod(uiThread = true)
    public JSONObject stopDetect(JSONObject options, UniJSCallback callback) {
        Log.i(TAG, "stopDetect called, options=" + String.valueOf(options));
        boolean stopped = DetectActivity.stopCurrentDetect();
        JSONObject result = createResult(
                true,
                "detect_stopped",
                stopped ? "检测已停止" : "当前没有正在运行的检测页面"
        );
        invokeCallback(callback, result, false);
        return result;
    }

    @UniJSMethod(uiThread = true)
    public JSONObject takeSnapshot(JSONObject options, UniJSCallback callback) {
        Log.i(TAG, "takeSnapshot called, options=" + String.valueOf(options));
        if (DetectActivity.getActiveActivity() == null) {
            JSONObject result = JsonUtils.snapshotError(
                    DetectErrorCode.SNAPSHOT_ACTIVITY_NOT_RUNNING,
                    "检测页面未运行，无法拍照",
                    null,
                    false
            );
            invokeCallback(callback, result, false);
            return result;
        }

        boolean accepted = DetectActivity.takeSnapshotCurrent(options, callback);
        if (!accepted) {
            return JsonUtils.snapshotError(
                    DetectErrorCode.SNAPSHOT_BUSY,
                    "拍照未开始或正在进行",
                    null,
                    false
            );
        }
        return createResult(true, "snapshot_started", "拍照任务已开始");
    }

    private Context getContext() {
        Context context = getContextFromSdkInstanceField("mWXSDKInstance");
        if (context != null) {
            return context;
        }

        context = getContextFromSdkInstanceField("mUniSDKInstance");
        if (context != null) {
            return context;
        }

        return getContextFromDCloudApplication();
    }

    private Context getContextFromSdkInstanceField(String fieldName) {
        Object sdkInstance = getFieldValue(fieldName);
        if (sdkInstance == null) {
            return null;
        }

        try {
            Method getContextMethod = sdkInstance.getClass().getMethod("getContext");
            Object context = getContextMethod.invoke(sdkInstance);
            if (context instanceof Context) {
                return (Context) context;
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to get context from " + fieldName, throwable);
        }
        return null;
    }

    private Object getFieldValue(String fieldName) {
        Class<?> clazz = getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(this);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            } catch (Throwable throwable) {
                Log.w(TAG, "Unable to read field " + fieldName, throwable);
                return null;
            }
        }
        return null;
    }

    private Context getContextFromDCloudApplication() {
        try {
            Class<?> appImplClass = Class.forName("io.dcloud.application.DCLoudApplicationImpl");
            Method selfMethod = appImplClass.getMethod("self");
            Object appImpl = selfMethod.invoke(null);
            if (appImpl == null) {
                return null;
            }

            Method getContextMethod = appImpl.getClass().getMethod("getContext");
            Object context = getContextMethod.invoke(appImpl);
            if (context instanceof Context) {
                return (Context) context;
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to get context from DCLoudApplicationImpl", throwable);
        }
        return null;
    }

    private String openDetectActivity(Context context) {
        try {
            Log.e(TAG, "openDetectActivity, context=" + context.getClass().getName());
            Intent intent = new Intent(context, DetectActivity.class);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            Log.e(TAG, "openDetectActivity startActivity called");
            return null;
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to open DetectActivity", throwable);
            return throwable.toString();
        }
    }

    private JSONObject createResult(boolean success, String type, String message) {
        JSONObject result = new JSONObject();
        result.put("success", success);
        if (success) {
            result.put("type", type);
        } else {
            result.put("type", "error");
            result.put("code", type);
        }
        result.put("message", message);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private void invokeCallback(UniJSCallback callback, JSONObject result, boolean keepAlive) {
        DetectConfig.invokeCallback(callback, result, keepAlive);
    }

    private String errorCodeOf(Throwable throwable, String fallbackCode) {
        if (throwable instanceof DetectException) {
            return ((DetectException) throwable).getCode();
        }
        return fallbackCode;
    }

    private String messageOf(Throwable throwable) {
        if (throwable instanceof DetectException && throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return throwable == null ? "Unknown error" : throwable.toString();
    }
}
