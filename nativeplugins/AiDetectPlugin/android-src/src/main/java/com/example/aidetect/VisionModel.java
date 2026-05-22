package com.example.aidetect;

import android.content.Context;
import android.graphics.Bitmap;

public interface VisionModel {

    void init(Context context, DetectConfig config) throws Exception;

    VisionResult infer(Bitmap bitmap) throws Exception;

    void release();
}
