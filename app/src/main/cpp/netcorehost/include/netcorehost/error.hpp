#ifndef NETCOREHOST_ERROR_HPP
#define NETCOREHOST_ERROR_HPP

#include <string>
#include <stdexcept>
#include <optional>
#include "bindings.hpp"

namespace netcorehost {

/**
 * 托管操作成功代码
 */
enum class HostingSuccess {
    Success,
    HostAlreadyInitialized,
    DifferentRuntimeProperties,
    Unknown
};

/**
 * 托管操作错误代码
 */
enum class HostingError {
    InvalidArgFailure,
    CoreHostLibLoadFailure,
    CoreHostLibMissingFailure,
    CoreHostEntryPointFailure,
    CoreHostCurHostFindFailure,
    CoreClrResolveFailure,
    CoreClrBindFailure,
    CoreClrInitFailure,
    CoreClrExeFailure,
    ResolverInitFailure,
    ResolverResolveFailure,
    LibHostCurExeFindFailure,
    LibHostInitFailure,
    LibHostExecModeFailure,
    LibHostSdkFindFailure,
    LibHostInvalidArgs,
    InvalidConfigFile,
    AppArgNotRunnable,
    AppHostExeNotBoundFailure,
    FrameworkMissingFailure,
    HostApiFailed,
    HostApiBufferTooSmall,
    LibHostUnknownCommand,
    LibHostAppRootFindFailure,
    SdkResolverResolveFailure,
    FrameworkCompatFailure,
    FrameworkCompatRetry,
    AppHostExeNotBundle,
    BundleExtractionFailure,
    BundleExtractionIOError,
    LibHostDuplicateProperty,
    HostApiUnsupportedVersion,
    HostInvalidState,
    HostPropertyNotFound,
    CoreHostIncompatibleConfig,
    HostApiUnsupportedScenario,
    HostFeatureDisabled,
    Unknown
};

/**
 * 托管操作结果
 */
class HostingResult {
public:
    // 从状态码创建
    static HostingResult from_status_code(uint32_t code);
    static HostingResult from_status_code(int32_t code);
    
    // 创建成功结果
    static HostingResult from_success(HostingSuccess success);
    
    // 创建错误结果
    static HostingResult from_error(HostingError error);
    
    // 判断是否成功
    bool is_success() const { return is_success_; }
    bool is_error() const { return !is_success_; }
    
    // 获取值
    uint32_t value() const;
    
    // 获取成功代码（如果是成功）
    std::optional<HostingSuccess> get_success() const;
    
    // 获取错误代码（如果是错误）
    std::optional<HostingError> get_error() const;
    
    // 将结果转换为异常（如果是错误）
    void throw_if_error() const;
    
    // 获取错误消息
    std::string get_error_message() const;

private:
    HostingResult(bool is_success, HostingSuccess success, HostingError error, uint32_t raw_code)
        : is_success_(is_success), success_(success), error_(error), raw_code_(raw_code) {}
    
    bool is_success_;
    HostingSuccess success_;
    HostingError error_;
    uint32_t raw_code_;
};

/**
 * 托管异常类
 */
class HostingException : public std::runtime_error {
public:
    explicit HostingException(HostingError error);
    explicit HostingException(HostingError error, const std::string& message);
    
    HostingError error() const { return error_; }
    uint32_t error_code() const;

private:
    HostingError error_;
};

/**
 * 应用或托管结果（可能是应用退出码或托管错误）
 */
class AppOrHostingResult {
public:
    explicit AppOrHostingResult(int32_t code) : code_(code) {}
    
    int32_t value() const { return code_; }
    HostingResult as_hosting_result() const { return HostingResult::from_status_code(code_); }
    
private:
    int32_t code_;
};

// 辅助函数：将错误码转换为字符串
std::string hosting_error_to_string(HostingError error);
std::string hosting_success_to_string(HostingSuccess success);

} // namespace netcorehost

#endif // NETCOREHOST_ERROR_HPP

