#!/usr/bin/env python3
"""
批量重命名脚本：将 bta64 重命名为 glibc_bridge
"""
import os
import re

# 需要处理的文件扩展名
EXTENSIONS = ('.c', '.h', '.cpp', '.hpp', '.cmake', '.txt', '.md', '.kts', '.gradle')

# 替换规则
REPLACEMENTS = [
    # 头文件包含
    (r'#include\s+"bta64/', r'#include "glibc_bridge/'),
    (r'#include\s+<bta64/', r'#include <glibc_bridge/'),
    (r'#include\s+"bta64_', r'#include "glibc_bridge_'),
    (r'#include\s+<bta64_', r'#include <glibc_bridge_'),
    
    # 类型和结构体
    (r'\bbta64_t\b', 'glibc_bridge_t'),
    (r'\bbta64_elf_t\b', 'glibc_bridge_elf_t'),
    (r'\bbta64_config_t\b', 'glibc_bridge_config_t'),
    (r'\bbta64_error_t\b', 'glibc_bridge_error_t'),
    (r'\bbta64_log_level_t\b', 'glibc_bridge_log_level_t'),
    (r'\bbta64_result_t\b', 'glibc_bridge_result_t'),
    (r'\bbta64_elf_info_t\b', 'glibc_bridge_elf_info_t'),
    (r'\bstruct\s+bta64_', r'struct glibc_bridge_'),
    (r'\bstruct\s+bta64_s\b', 'struct glibc_bridge_s'),
    (r'\bstruct\s+bta64_elf_s\b', 'struct glibc_bridge_elf_s'),
    
    # 宏定义
    (r'\bBTA64_', 'GLIBC_BRIDGE_'),
    
    # 函数名
    (r'\bbta64_([a-z])', r'glibc_bridge_\1'),
    
    # 变量名（保留 g_bta64 等全局变量，但可以替换其他）
    (r'\bg_bta64_', 'g_glibc_bridge_'),
    
    # 注释和字符串
    (r'BTA64\b', 'glibc-bridge'),
    (r'bta64\b', 'glibc-bridge'),
]

def process_file(filepath):
    """处理单个文件"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original = content
        for pattern, replacement in REPLACEMENTS:
            content = re.sub(pattern, replacement, content)
        
        if content != original:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
    except Exception as e:
        print(f"Error processing {filepath}: {e}")
    return False

def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(base_dir, 'src')
    
    if not os.path.exists(src_dir):
        print(f"Source directory not found: {src_dir}")
        return
    
    count = 0
    for root, dirs, files in os.walk(src_dir):
        for file in files:
            if file.endswith(EXTENSIONS):
                filepath = os.path.join(root, file)
                if process_file(filepath):
                    count += 1
                    print(f"Processed: {filepath}")
    
    print(f"\nTotal files processed: {count}")

if __name__ == '__main__':
    main()

