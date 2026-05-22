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
            DetectConfig.setCallback(callback);

            Context context = getContext();

            if (context == null) {
                JSONObject result = createResult(false, "activity_open_failed", "无法获取 Android Context");
                invokeCallback(callback, result, false);
                return result;
            }

            JSONObject result = createResult(true, "activity_opened", "DetectActivity 已打开");
            invokeCallback(callback, result, true);

            openDetectActivity(context);
            return result;
        } catch (Throwable throwable) {
            Log.e(TAG, "startDetect failed", throwable);

            JSONObject result = createResult(false, "start_detect_exception", throwable.toString());
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
            DetectConfig.clearCallback();

            Context context = getContext();

            if (context == null) {
                return createResult(false, "activity_open_failed", "无法获取 Android Context");
            }

            JSONObject result = createResult(true, "activity_opened", "DetectActivity 已打开");
            openDetectActivity(context);
            return result;
        } catch (Throwable throwable) {
            Log.e(TAG, "startDetectSync failed", throwable);
            return createResult(false, "start_detect_exception", throwable.toString());
        }
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

    private void openDetectActivity(Context context) {
        try {
            Log.e(TAG, "openDetectActivity, context=" + context.getClass().getName());
            Intent intent = new Intent(context, DetectActivity.class);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            Log.e(TAG, "openDetectActivity startActivity called");
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to open DetectActivity", throwable);
        }
    }

    private JSONObject createResult(boolean success, String type, String message) {
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("type", type);
        result.put("message", message);
        return result;
    }

    private void invokeCallback(UniJSCallback callback, JSONObject result, boolean keepAlive) {
        DetectConfig.invokeCallback(callback, result, keepAlive);
    }
}
