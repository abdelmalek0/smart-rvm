//
// Created by MST on 6/15/2023.
//

#ifndef RKNN_YOLOV5_ANDROID_FP16_CLASSIFIER_H
#define RKNN_YOLOV5_ANDROID_FP16_CLASSIFIER_H

#include <android/log.h>

int create_model(int im_height, int im_width, int im_channel, char *model_path);
bool run_model(char *inDataRaw, float *y);

#endif //RKNN_YOLOV5_ANDROID_FP16_CLASSIFIER_H

