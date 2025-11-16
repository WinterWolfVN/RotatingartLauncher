/**
 * @file netcorehost_manager_jni.cpp
 * @brief NetCoreManager JNI 实现
 */

#include <jni.h>
#include <string>
#include <vector>
#include "netcorehost_manager.h"
#include "app_logger.h"

#define LOG_TAG "NetCoreManager_JNI"

// 辅助函数：将 jstring 转换为 C 字符串
static std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";

    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);

    return result;
}

// 辅助函数：将 jstring 数组转换为 C 字符串数组
static std::vector<std::string> jarray_to_vector(JNIEnv* env, jobjectArray jarray) {
    std::vector<std::string> result;

    if (!jarray) return result;

    jsize length = env->GetArrayLength(jarray);
    for (jsize i = 0; i < length; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(jarray, i);
        if (jstr) {
            result.push_back(jstring_to_string(env, jstr));
            env->DeleteLocalRef(jstr);
        }
    }

    return result;
}

// 辅助函数：将 std::vector<std::string> 转换为 const char* 数组
static std::vector<const char*> vector_to_carray(const std::vector<std::string>& vec) {
    std::vector<const char*> result;
    for (const auto& str : vec) {
        result.push_back(str.c_str());
    }
    return result;
}

extern "C" {

/**
 * @brief 初始化 .NET 运行时
 */
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeInit(
    JNIEnv* env, jclass clazz,
    jstring dotnetRoot, jint frameworkMajor) {

    std::string dotnet_root = jstring_to_string(env, dotnetRoot);

    LOGI(LOG_TAG, "JNI: nativeInit(dotnetRoot=%s, frameworkMajor=%d)",
         dotnet_root.c_str(), frameworkMajor);

    return netcore_init(dotnet_root.c_str(), frameworkMajor);
}

/**
 * @brief 运行程序集
 */
JNIEXPORT jint JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeRunApp(
    JNIEnv* env, jclass clazz,
    jstring appDir, jstring assemblyName, jint argc, jobjectArray argv) {

    std::string app_dir = jstring_to_string(env, appDir);
    std::string assembly_name = jstring_to_string(env, assemblyName);

    LOGI(LOG_TAG, "JNI: nativeRunApp(appDir=%s, assembly=%s, argc=%d)",
         app_dir.c_str(), assembly_name.c_str(), argc);

    // 转换参数数组
    std::vector<std::string> args_vec;
    std::vector<const char*> args_carray;

    if (argc > 0 && argv) {
        args_vec = jarray_to_vector(env, argv);
        args_carray = vector_to_carray(args_vec);
    }

    return netcore_run_app(
        app_dir.c_str(),
        assembly_name.c_str(),
        argc,
        argc > 0 ? args_carray.data() : nullptr
    );
}

/**
 * @brief 加载程序集
 */
JNIEXPORT jlong JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeLoadAssembly(
    JNIEnv* env, jclass clazz,
    jstring appDir, jstring assemblyName) {

    std::string app_dir = jstring_to_string(env, appDir);
    std::string assembly_name = jstring_to_string(env, assemblyName);

    LOGI(LOG_TAG, "JNI: nativeLoadAssembly(appDir=%s, assembly=%s)",
         app_dir.c_str(), assembly_name.c_str());

    void* context_handle = nullptr;
    int result = netcore_load_assembly(
        app_dir.c_str(),
        assembly_name.c_str(),
        &context_handle
    );

    if (result != 0) {
        LOGE(LOG_TAG, "Failed to load assembly: %d", result);
        return 0;
    }

    return reinterpret_cast<jlong>(context_handle);
}

/**
 * @brief 调用方法
 */
JNIEXPORT jlong JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeCallMethod(
    JNIEnv* env, jclass clazz,
    jlong contextHandle, jstring typeName, jstring methodName, jstring delegateType) {

    void* context = reinterpret_cast<void*>(contextHandle);
    std::string type_name = jstring_to_string(env, typeName);
    std::string method_name = jstring_to_string(env, methodName);
    std::string delegate_type = delegateType ? jstring_to_string(env, delegateType) : "";

    LOGI(LOG_TAG, "JNI: nativeCallMethod(type=%s, method=%s)",
         type_name.c_str(), method_name.c_str());

    void* result_ptr = nullptr;
    int result = netcore_call_method(
        context,
        type_name.c_str(),
        method_name.c_str(),
        delegate_type.empty() ? nullptr : delegate_type.c_str(),
        &result_ptr
    );

    if (result != 0) {
        LOGE(LOG_TAG, "Failed to call method: %d", result);
        return 0;
    }

    return reinterpret_cast<jlong>(result_ptr);
}

/**
 * @brief 获取属性
 */
JNIEXPORT jlong JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeGetProperty(
    JNIEnv* env, jclass clazz,
    jlong contextHandle, jstring typeName, jstring propertyName, jstring delegateType) {

    void* context = reinterpret_cast<void*>(contextHandle);
    std::string type_name = jstring_to_string(env, typeName);
    std::string property_name = jstring_to_string(env, propertyName);
    std::string delegate_type = jstring_to_string(env, delegateType);

    LOGI(LOG_TAG, "JNI: nativeGetProperty(type=%s, property=%s)",
         type_name.c_str(), property_name.c_str());

    void* result_ptr = nullptr;
    int result = netcore_get_property(
        context,
        type_name.c_str(),
        property_name.c_str(),
        delegate_type.c_str(),
        &result_ptr
    );

    if (result != 0) {
        LOGE(LOG_TAG, "Failed to get property: %d", result);
        return 0;
    }

    return reinterpret_cast<jlong>(result_ptr);
}

/**
 * @brief 关闭上下文
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeCloseContext(
    JNIEnv* env, jclass clazz, jlong contextHandle) {

    void* context = reinterpret_cast<void*>(contextHandle);

    LOGI(LOG_TAG, "JNI: nativeCloseContext(0x%lx)", contextHandle);

    netcore_close_context(context);
}

/**
 * @brief 获取最后错误
 */
JNIEXPORT jstring JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeGetLastError(
    JNIEnv* env, jclass clazz) {

    const char* error = netcore_get_last_error();

    if (error == nullptr) {
        return nullptr;
    }

    return env->NewStringUTF(error);
}

/**
 * @brief 清理资源
 */
JNIEXPORT void JNICALL
Java_com_app_ralaunch_netcore_NetCoreManager_nativeCleanup(
    JNIEnv* env, jclass clazz) {

    LOGI(LOG_TAG, "JNI: nativeCleanup()");

    netcore_cleanup();
}

} // extern "C"
