cmake_minimum_required(VERSION 3.18.1)

project(yolov8ncnn)

set(NCNN_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/ncnn)

add_library(
        yolov8ncnn
        SHARED
        yolo_ncnn_jni.cpp
)

add_library(ncnn SHARED IMPORTED)
set_target_properties(
        ncnn
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libncnn.so
)

target_include_directories(
        yolov8ncnn
        PRIVATE
        ${NCNN_ROOT}/include
)

find_library(
        log-lib
        log
)

find_library(
        android-lib
        android
)

find_library(
        jnigraphics-lib
        jnigraphics
)

target_link_libraries(
        yolov8ncnn
        ncnn
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)

# Future real NCNN integration:
# Current project layout uses shared prebuilt libraries:
# - Headers: src/main/cpp/ncnn/include/ncnn/*.h
# - Shared libs: src/main/jniLibs/${ANDROID_ABI}/libncnn.so
#
# Static library alternative:
# - Put libncnn.a under src/main/cpp/ncnn/libs/${ANDROID_ABI}/libncnn.a
# - Change add_library(ncnn SHARED IMPORTED) to STATIC
# - Change IMPORTED_LOCATION to the libncnn.a path
