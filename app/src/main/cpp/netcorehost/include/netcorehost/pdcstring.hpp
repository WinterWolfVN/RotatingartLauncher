#ifndef NETCOREHOST_PDCSTRING_HPP
#define NETCOREHOST_PDCSTRING_HPP

#include <string>
#include <memory>
#include "bindings.hpp"

namespace netcorehost {

/**
 * 平台相关的字符串类型 (Platform Dependent C String)
 * 在 Windows 上使用 wchar_t (UTF-16)
 * 在其他平台上使用 char (UTF-8)
 */
class PdCString {
public:
    PdCString();
    ~PdCString();

    // 从标准字符串创建
    static PdCString from_str(const std::string& str);
    
    // 从标准宽字符串创建
    static PdCString from_wstr(const std::wstring& str);

    // 从平台字符串创建
#ifdef _WINDOWS
    explicit PdCString(const std::wstring& str);
    explicit PdCString(const wchar_t* str);
#else
    explicit PdCString(const std::string& str);
    explicit PdCString(const char* str);
#endif

    // 复制和移动
    PdCString(const PdCString& other);
    PdCString(PdCString&& other) noexcept;
    PdCString& operator=(const PdCString& other);
    PdCString& operator=(PdCString&& other) noexcept;

    // 获取原始指针
    const bindings::char_t* c_str() const;
    const bindings::char_t* data() const { return c_str(); }

    // 转换为标准字符串
    std::string to_string() const;
    std::wstring to_wstring() const;

    // 判空
    bool empty() const;
    size_t length() const;

private:
    std::unique_ptr<bindings::char_t[]> data_;
    size_t length_;
};

/**
 * 平台相关的字符串视图 (类似于 Rust 的 PdCStr)
 */
class PdCStr {
public:
    explicit PdCStr(const bindings::char_t* str);
    
    const bindings::char_t* c_str() const { return str_; }
    const bindings::char_t* data() const { return str_; }
    
    std::string to_string() const;
    std::wstring to_wstring() const;
    
    size_t length() const;

private:
    const bindings::char_t* str_;
};

// 辅助宏，用于创建平台相关的字符串字面量
#ifdef _WINDOWS
    #define PDCSTR(str) L##str
#else
    #define PDCSTR(str) str
#endif

} // namespace netcorehost

#endif // NETCOREHOST_PDCSTRING_HPP

