cmake_minimum_required(VERSION 3.18.1)

project(yolov8ncnn)

add_library(
        yolov8ncnn
        SHARED
        yolo_ncnn_jni.cpp
)

find_library(
        log-lib
        log
)

find_library(
        android-lib
        android
)

target_link_libraries(
        yolov8ncnn
        ${log-lib}
        ${android-lib}
)

# Future real NCNN integration:
# 1. Put NCNN headers under:
#    src/main/cpp/ncnn/include
# 2. Put per-ABI static or shared libraries under:
#    src/main/cpp/ncnn/libs/${ANDROID_ABI}
# 3. Add include/library wiring here, for example:
#    target_include_directories(yolov8ncnn PRIVATE src/main/cpp/ncnn/include)
#    add_library(ncnn STATIC IMPORTED)
#    set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION
#        ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/libs/${ANDROID_ABI}/libncnn.a)
#    target_link_libraries(yolov8ncnn ncnn ...)
