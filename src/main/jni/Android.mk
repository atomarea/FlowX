LOCAL_PATH := $(call my-dir)

LOCAL_MODULE    := avutil 

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libavutil.a
else
    ifeq ($(TARGET_ARCH_ABI),armeabi)
	LOCAL_SRC_FILES := ./ffmpeg/armv5te/libavutil.a
    else
        ifeq ($(TARGET_ARCH_ABI),x86)
	    LOCAL_SRC_FILES := ./ffmpeg/i686/libavutil.a
        endif
    endif
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := avformat

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libavformat.a
else
    ifeq ($(TARGET_ARCH_ABI),armeabi)
	LOCAL_SRC_FILES := ./ffmpeg/armv5te/libavformat.a
    else
        ifeq ($(TARGET_ARCH_ABI),x86)
	    LOCAL_SRC_FILES := ./ffmpeg/i686/libavformat.a
        endif
    endif
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := avcodec 

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_SRC_FILES := ./ffmpeg/armv7-a/libavcodec.a
else
    ifeq ($(TARGET_ARCH_ABI),armeabi)
	LOCAL_SRC_FILES := ./ffmpeg/armv5te/libavcodec.a
    else
        ifeq ($(TARGET_ARCH_ABI),x86)
	    LOCAL_SRC_FILES := ./ffmpeg/i686/libavcodec.a
        endif
    endif
endif

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CPP_EXTENSION := .cc
LOCAL_ARM_MODE := arm
LOCAL_MODULE := breakpad
LOCAL_CPPFLAGS := -Wall -std=c++11 -DANDROID -finline-functions -ffast-math -Os -fno-strict-aliasing

include $(CLEAR_VARS)

LOCAL_SRC_FILES     += \
./libyuv/source/compare_common.cc \
./libyuv/source/compare_gcc.cc \
./libyuv/source/compare_neon64.cc \
./libyuv/source/compare_win.cc \
./libyuv/source/compare.cc \
./libyuv/source/convert_argb.cc \
./libyuv/source/convert_from_argb.cc \
./libyuv/source/convert_from.cc \
./libyuv/source/convert_jpeg.cc \
./libyuv/source/convert_to_argb.cc \
./libyuv/source/convert_to_i420.cc \
./libyuv/source/convert.cc \
./libyuv/source/cpu_id.cc \
./libyuv/source/mjpeg_decoder.cc \
./libyuv/source/mjpeg_validate.cc \
./libyuv/source/planar_functions.cc \
./libyuv/source/rotate_any.cc \
./libyuv/source/rotate_argb.cc \
./libyuv/source/rotate_common.cc \
./libyuv/source/rotate_gcc.cc \
./libyuv/source/rotate_mips.cc \
./libyuv/source/rotate_neon64.cc \
./libyuv/source/rotate_win.cc \
./libyuv/source/rotate.cc \
./libyuv/source/row_any.cc \
./libyuv/source/row_common.cc \
./libyuv/source/row_gcc.cc \
./libyuv/source/row_mips.cc \
./libyuv/source/row_neon64.cc \
./libyuv/source/row_win.cc \
./libyuv/source/scale_any.cc \
./libyuv/source/scale_argb.cc \
./libyuv/source/scale_common.cc \
./libyuv/source/scale_gcc.cc \
./libyuv/source/scale_mips.cc \
./libyuv/source/scale_neon64.cc \
./libyuv/source/scale_win.cc \
./libyuv/source/scale.cc \
./libyuv/source/video_common.cc

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_CFLAGS += -DLIBYUV_NEON
    LOCAL_SRC_FILES += \
        ./libyuv/source/compare_neon.cc.neon    \
        ./libyuv/source/rotate_neon.cc.neon     \
        ./libyuv/source/row_neon.cc.neon        \
        ./libyuv/source/scale_neon.cc.neon
endif

LOCAL_SRC_FILES     += \
./jni.c \
./video.c \

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)