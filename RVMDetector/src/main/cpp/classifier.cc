/**
  * @ClassName classifier
  * @Description inference code for battery classifier
  * @Author abdelmalek0
  * @Date 2023/6/15
  * @Version 1.0
  */

#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include <vector>
#include <ctime>

#include <cstdint>

#include "rknn_api.h"

#include "classifier.h"
#include "rga/rga.h"
#include "rga/im2d.h"
#include "rga/im2d_version.h"
#include "post_process.h"

//#define DEBUG_DUMP
//#define EVAL_TIME
#define ZERO_COPY 1
#define DO_NOT_FLIP -1

static int g_inf_count = 0;

static int g_post_count = 0;

static rknn_context ctx = 0;

static bool created = false;

static int img_width = 0;    // the width of the actual input image
static int img_height = 0;   // the height of the actual input image

static int m_in_width = 0;   // the width of the RKNN model input
static int m_in_height = 0;  // the height of the RKNN model input
static int m_in_channel = 0; // the channel of the RKNN model input

static float scale_w = 0.0;
static float scale_h = 0.0;

static uint32_t n_input = 1;
static uint32_t n_output = 1;

static rknn_tensor_attr input_attrs[1];
static rknn_tensor_attr output_attrs[1];

static rknn_tensor_mem *input_mems[1];
static rknn_tensor_mem *output_mems[1];

static rga_buffer_t g_rga_src;
static rga_buffer_t g_rga_dst;

static std::vector<float> out_scales;
static std::vector<int32_t> out_zps;

int create_model(int im_height, int im_width, int im_channel, char *model_path)
{
    img_height = im_height;
    img_width = im_width;

//    LOGI("try classifier_init!")

    // 0. RGA version check
//    LOGI("RGA API Version: %s", RGA_API_VERSION)
    // Please refer to the link to confirm the RGA driver version, make sure it is higher than 1.2.4
    // https://github.com/airockchip/librga/blob/main/docs/Rockchip_FAQ_RGA_CN.md#rga-driver

    // 1. Load model
    FILE *fp = fopen(model_path, "rb");
    if(fp == NULL) {
        LOGE("fopen %s fail!\n", model_path);
        return -1;
    }
    fseek(fp, 0, SEEK_END);
    uint32_t model_len = ftell(fp);
    void *model = malloc(model_len);
    fseek(fp, 0, SEEK_SET);
    if(model_len != fread(model, 1, model_len, fp)) {
        LOGE("fread %s fail!\n", model_path);
        free(model);
        fclose(fp);
        return -1;
    }

    fclose(fp);

    // 2. Init RKNN model
    int ret = rknn_init(&ctx, model, model_len, 0, nullptr);
    free(model);

    if(ret < 0) {
        LOGE("classifier_init fail! ret=%d\n", ret);
        return -1;
    }

    // 3. Query input/output attr.
    rknn_input_output_num io_num;
    rknn_query_cmd cmd = RKNN_QUERY_IN_OUT_NUM;
    // 3.1 Query input/output num.
    ret = rknn_query(ctx, cmd, &io_num, sizeof(io_num));
    LOGE("size of io: %d" , sizeof(io_num))


    if (ret != RKNN_SUCC) {
        LOGE("classifier_query io_num fail!ret=%d\n", ret);
        return -1;
    }
    n_input = io_num.n_input;
    n_output = io_num.n_output;

    LOGE("n_input: %d" , n_input)
    LOGE("n_output: %d" , n_output)

    // 3.2 Query input attributes
    memset(input_attrs, 0, n_input * sizeof(rknn_tensor_attr));

    LOGE("sizeof(rknn_tensor_attr): %d" , sizeof(rknn_tensor_attr))

    for (int i = 0; i < n_input; ++i) {
        input_attrs[i].index = i;
        cmd = RKNN_QUERY_INPUT_ATTR;
        ret = rknn_query(ctx, cmd, &(input_attrs[i]), sizeof(rknn_tensor_attr));
        if (ret < 0) {
            LOGE("classifier_query input_attrs[%d] fail!ret=%d\n", i, ret);
            return -1;
        }
    }
    // 3.2.0 Update global model input shape.
    if (RKNN_TENSOR_NHWC == input_attrs[0].fmt) {
        m_in_height = input_attrs[0].dims[1];
        m_in_width = input_attrs[0].dims[2];
        m_in_channel = input_attrs[0].dims[3];
    } else if (RKNN_TENSOR_NCHW == input_attrs[0].fmt) {
        m_in_height = input_attrs[0].dims[2];
        m_in_width = input_attrs[0].dims[3];
        m_in_channel = input_attrs[0].dims[1];
    } else {
        LOGE("Unsupported model input layout: %d!\n", input_attrs[0].fmt);
        return -1;
    }

    // 3.3 Query output attributes
    memset(output_attrs, 0, n_output * sizeof(rknn_tensor_attr));

    LOGE("sizeof(rknn_tensor_attr): %d" , sizeof(rknn_tensor_attr))

    for (int i = 0; i < n_output; ++i) {
        output_attrs[i].index = i;
        cmd = RKNN_QUERY_OUTPUT_ATTR;
        ret = rknn_query(ctx, cmd, &(output_attrs[i]), sizeof(rknn_tensor_attr));

        if (ret < 0) {
            LOGE("classifier_query output_attrs[%d] fail!ret=%d\n", i, ret);
            return -1;
        }
        // set out_scales/out_zps for post_process
        out_scales.push_back(output_attrs[i].scale);
        out_zps.push_back(output_attrs[i].zp);
    }

#if ZERO_COPY
    // 4. Set input/output buffer
    // 4.1 Set inputs memory
    // 4.1.1 Create input tensor memory, input data type is INT8, yolo has only 1 input.
    input_mems[0] = rknn_create_mem(ctx, input_attrs[0].size_with_stride * sizeof(char ));
    memset(input_mems[0]->virt_addr, 0, input_attrs[0].size_with_stride * sizeof(char ));
    LOGE("input_attrs[0].size_with_stride: %d" , input_attrs[0].size_with_stride)
    // 4.1.2 Update input attrs
    input_attrs[0].index = 0;
    input_attrs[0].type = RKNN_TENSOR_UINT8;
    input_attrs[0].size = m_in_height * m_in_width * m_in_channel * sizeof(char );
    input_attrs[0].fmt = RKNN_TENSOR_NHWC;
    // TODO -- The efficiency of pass through will be higher, we need adjust the layout of input to
    //         meet the use condition of pass through.
    input_attrs[0].pass_through = 0;
    // 4.1.3 Set input buffer
    rknn_set_io_mem(ctx, input_mems[0], &(input_attrs[0]));
    // 4.1.4 bind virtual address to rga virtual address
    g_rga_dst = wrapbuffer_virtualaddr((void *)input_mems[0]->virt_addr, m_in_width, m_in_height,
                                       RK_FORMAT_RGB_888);

    // 4.2 Set outputs memory
    for (int i = 0; i < n_output; ++i) {
        // 4.2.1 Create output tensor memory, output data type is int8, post_process need int8 data.
        output_mems[i] = rknn_create_mem(ctx, output_attrs[i].n_elems * sizeof(float));
        memset(output_mems[i]->virt_addr, 0, output_attrs[i].n_elems * sizeof(float));
        output_attrs[i].size = output_attrs[i].n_elems * sizeof(float );
        output_attrs[i].type = RKNN_TENSOR_FLOAT32;
        // 4.1.3 Set output buffer
        rknn_set_io_mem(ctx, output_mems[i], &(output_attrs[i]));
    }
#else
    void *in_data = malloc(m_in_width * m_in_height * m_in_channel);
    memset(in_data, 0, m_in_width * m_in_height * m_in_channel);
    g_rga_dst = wrapbuffer_virtualaddr(in_data, m_in_width, m_in_height, RK_FORMAT_RGB_888);
#endif

    created = true;

//    LOGI("classifier_init success!");

    return 0;
}


bool run_model(char *inDataRaw, float *y)
{
    int ret;
    bool status = false;
    if(!created) {
//        LOGE("run_yolo: init yolo hasn't successful!");
        return false;
    }

#ifdef EVAL_TIME
    struct timeval start_time, stop_time;

    gettimeofday(&start_time, NULL);
#endif
    g_rga_src = wrapbuffer_virtualaddr((void *)inDataRaw, img_width, img_height,
                                       RK_FORMAT_RGBA_8888);

    // convert color format and resize. RGA8888 -> RGB888
    ret = imresize(g_rga_src, g_rga_dst);
    if (IM_STATUS_SUCCESS != ret) {
//        LOGE("run_yolo: resize image with rga failed: %s\n", imStrError((IM_STATUS)ret));
        return false;
    }
#ifdef EVAL_TIME
    gettimeofday(&stop_time, NULL);
    LOGI("imresize use %f ms\n", (__get_us(stop_time) - __get_us(start_time)) / 1000);
#endif

#ifdef DEBUG_DUMP
    // save resized image
    if (g_inf_count == 5) {
        char out_img_name[1024];
        memset(out_img_name, 0, sizeof(out_img_name));
        sprintf(out_img_name, "/data/user/0/com.rockchip.gpadc.yolodemo/cache/resized_img_%d.rgb", g_inf_count);
        FILE *fp = fopen(out_img_name, "w");
//        LOGI("n_elems: %d", input_attrs[0].n_elems);
//        fwrite(input_mems[0]->virt_addr, 1, input_attrs[0].n_elems * sizeof(unsigned char), fp);
//        fflush(fp);
        for (int i = 0; i < input_attrs[0].n_elems; ++i) {
            fprintf(fp, "%d\n", *((uint8_t *)(g_rga_dst.vir_addr) + i));
        }
        fclose(fp);
    }

#endif

#if ZERO_COPY
#else
    rknn_input inputs[1];
    inputs[0].index = 0;
    inputs[0].type = RKNN_TENSOR_UINT8;
    inputs[0].size = m_in_width * m_in_height * m_in_channel;
    inputs[0].fmt = RKNN_TENSOR_NHWC;
    inputs[0].pass_through = 0;
    inputs[0].buf = g_rga_dst.vir_addr;
#ifdef EVAL_TIME
    gettimeofday(&start_time, NULL);
#endif
    rknn_inputs_set(ctx, 1, inputs);
#ifdef EVAL_TIME
    gettimeofday(&stop_time, NULL);
    LOGI("rknn_inputs_set use %f ms\n", (__get_us(stop_time) - __get_us(start_time)) / 1000);
#endif
#endif

#ifdef EVAL_TIME
    gettimeofday(&start_time, NULL);
#endif
    ret = rknn_run(ctx, nullptr);
    if(ret < 0) {
//        LOGE("classifier_run fail! ret=%d\n", ret);
        return false;
    }
#ifdef EVAL_TIME
    gettimeofday(&stop_time, NULL);
    LOGI("inference use %f ms\n", (__get_us(stop_time) - __get_us(start_time)) / 1000);

    // outputs format are all NCHW.
    gettimeofday(&start_time, NULL);
#endif

#if ZERO_COPY
    memcpy(y, (float*) (output_mems[0]->virt_addr), output_attrs[0].n_elems * sizeof(float));
#else
    rknn_output outputs[3];
    memset(outputs, 0, sizeof(outputs));
    for (int i = 0; i < 3; ++i) {
        outputs[i].want_float = 0;
    }
    rknn_outputs_get(ctx, 3, outputs, NULL);
    memcpy(y0, outputs[0].buf, output_attrs[0].n_elems * sizeof(char));
    memcpy(y1, outputs[1].buf, output_attrs[1].n_elems * sizeof(char));
    memcpy(y2, outputs[2].buf, output_attrs[2].n_elems * sizeof(char));
    rknn_outputs_release(ctx, 3, outputs);
#endif

#ifdef EVAL_TIME
    gettimeofday(&stop_time, NULL);
    LOGI("copy output use %f ms\n", (__get_us(stop_time) - __get_us(start_time)) / 1000);
#endif

#ifdef DEBUG_DUMP
    if (g_inf_count == 5) {
        for (int i = 0; i < n_output; ++i) {
            char out_path[1024];
            memset(out_path, 0, sizeof(out_path));
            sprintf(out_path, "/data/user/0/com.rockchip.gpadc.yolodemo/cache/out_%d.tensor", i);
            FILE *fp = fopen(out_path, "w");
            for (int j = 0; j < output_attrs[i].n_elems; ++j) {
#if ZERO_COPY
                fprintf(fp, "%d\n", *((int8_t *)(output_mems[i]->virt_addr) + i));
#else
                fprintf(fp, "%d\n", *((int8_t *)(outputs[i].buf) + i));
#endif
            }
            fclose(fp);
        }
    }
    if (g_inf_count < 10) {
        g_inf_count++;
    }
#endif

    status = true;

//    LOGI("run_classifier: end\n");

    return status;
}