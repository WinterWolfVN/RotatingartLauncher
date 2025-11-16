#!/usr/bin/env python3
"""
检测 .NET 程序集是否有入口点（Main 方法）

使用方法:
    python check_assembly_entry.py <assembly_path>

返回值:
    0 - 有入口点
    1 - 没有入口点
    2 - 错误
"""

import sys
import os
import struct
import json

def read_pe_header(file_path):
    """读取 PE 文件头"""
    try:
        with open(file_path, 'rb') as f:
            # 读取 DOS 头
            dos_header = f.read(64)
            if dos_header[:2] != b'MZ':
                return None

            # 获取 PE 头偏移
            pe_offset = struct.unpack('<I', dos_header[60:64])[0]
            f.seek(pe_offset)

            # 读取 PE 签名
            pe_sig = f.read(4)
            if pe_sig != b'PE\x00\x00':
                return None

            return True
    except Exception as e:
        print(f"读取 PE 头失败: {e}", file=sys.stderr)
        return None

def has_cli_header(file_path):
    """检查是否是 .NET 程序集（有 CLI 头）"""
    try:
        with open(file_path, 'rb') as f:
            # 读取 DOS 头
            dos_header = f.read(64)
            if dos_header[:2] != b'MZ':
                return False

            # 获取 PE 头偏移
            pe_offset = struct.unpack('<I', dos_header[60:64])[0]
            f.seek(pe_offset)

            # 读取 PE 签名
            pe_sig = f.read(4)
            if pe_sig != b'PE\x00\x00':
                return False

            # 读取 COFF 头
            f.read(20)  # 跳过 COFF 头

            # 读取 Optional Header 的 Magic
            magic = struct.unpack('<H', f.read(2))[0]

            # PE32 或 PE32+
            if magic == 0x10b:  # PE32
                f.seek(pe_offset + 24 + 208)
            elif magic == 0x20b:  # PE32+
                f.seek(pe_offset + 24 + 224)
            else:
                return False

            # 读取 CLI Header RVA
            cli_header_rva = struct.unpack('<I', f.read(4))[0]

            return cli_header_rva != 0
    except Exception as e:
        print(f"检查 CLI 头失败: {e}", file=sys.stderr)
        return False

def check_entry_point_simple(file_path):
    """简单检查：通过 PE 头的 AddressOfEntryPoint 判断"""
    try:
        with open(file_path, 'rb') as f:
            # 读取 DOS 头
            dos_header = f.read(64)
            if dos_header[:2] != b'MZ':
                return False

            pe_offset = struct.unpack('<I', dos_header[60:64])[0]
            f.seek(pe_offset)

            # PE 签名
            pe_sig = f.read(4)
            if pe_sig != b'PE\x00\x00':
                return False

            # 跳过 COFF 头
            f.read(20)

            # Optional Header
            magic = struct.unpack('<H', f.read(2))[0]
            f.read(2)  # Skip MajorLinkerVersion, MinorLinkerVersion
            f.read(12)  # Skip SizeOfCode, SizeOfInitializedData, SizeOfUninitializedData

            # AddressOfEntryPoint
            entry_point = struct.unpack('<I', f.read(4))[0]

            # 如果 entry_point != 0，说明有入口点
            # 但对于 .NET，还需要检查 CLI 头中的 EntryPointToken
            return entry_point != 0
    except Exception as e:
        print(f"检查入口点失败: {e}", file=sys.stderr)
        return False

def extract_cli_metadata(file_path):
    """提取 CLI 元数据（检查是否有 EntryPointToken）"""
    try:
        with open(file_path, 'rb') as f:
            # 读取 DOS 头
            dos_header = f.read(64)
            if dos_header[:2] != b'MZ':
                return None

            pe_offset = struct.unpack('<I', dos_header[60:64])[0]
            f.seek(pe_offset)

            # PE 签名
            pe_sig = f.read(4)
            if pe_sig != b'PE\x00\x00':
                return None

            # 跳过 COFF 头
            f.read(20)

            # Optional Header Magic
            magic = struct.unpack('<H', f.read(2))[0]

            # 定位到 CLI Header Data Directory
            if magic == 0x10b:  # PE32
                f.seek(pe_offset + 24 + 208)
            elif magic == 0x20b:  # PE32+
                f.seek(pe_offset + 24 + 224)
            else:
                return None

            # CLI Header RVA 和 Size
            cli_header_rva = struct.unpack('<I', f.read(4))[0]
            cli_header_size = struct.unpack('<I', f.read(4))[0]

            if cli_header_rva == 0:
                return None

            # 将 RVA 转换为文件偏移（简化版，假设在第一个 section）
            # 实际应该遍历 section headers
            f.seek(pe_offset + 24 + (272 if magic == 0x20b else 240))

            # 读取第一个 section header
            section_name = f.read(8)
            virtual_size = struct.unpack('<I', f.read(4))[0]
            virtual_address = struct.unpack('<I', f.read(4))[0]
            raw_size = struct.unpack('<I', f.read(4))[0]
            raw_offset = struct.unpack('<I', f.read(4))[0]

            # 计算 CLI Header 的文件偏移
            cli_file_offset = raw_offset + (cli_header_rva - virtual_address)
            f.seek(cli_file_offset)

            # 读取 CLI Header
            cb = struct.unpack('<I', f.read(4))[0]
            major_runtime_version = struct.unpack('<H', f.read(2))[0]
            minor_runtime_version = struct.unpack('<H', f.read(2))[0]
            metadata_rva = struct.unpack('<I', f.read(4))[0]
            metadata_size = struct.unpack('<I', f.read(4))[0]
            flags = struct.unpack('<I', f.read(4))[0]
            entry_point_token = struct.unpack('<I', f.read(4))[0]

            return {
                'has_entry_point': entry_point_token != 0,
                'entry_point_token': hex(entry_point_token),
                'flags': hex(flags),
                'is_exe': (flags & 0x00000001) != 0  # COMIMAGE_FLAGS_ILONLY
            }
    except Exception as e:
        print(f"提取 CLI 元数据失败: {e}", file=sys.stderr)
        return None

def check_assembly_entry_point(assembly_path):
    """检查程序集是否有入口点"""
    if not os.path.exists(assembly_path):
        print(f"文件不存在: {assembly_path}", file=sys.stderr)
        return None

    # 检查是否是 PE 文件
    if not read_pe_header(assembly_path):
        print(f"不是有效的 PE 文件: {assembly_path}", file=sys.stderr)
        return None

    # 检查是否是 .NET 程序集
    if not has_cli_header(assembly_path):
        print(f"不是 .NET 程序集: {assembly_path}", file=sys.stderr)
        return None

    # 提取 CLI 元数据
    metadata = extract_cli_metadata(assembly_path)

    if metadata:
        return metadata

    # 如果提取元数据失败，使用简单检查
    has_entry = check_entry_point_simple(assembly_path)
    return {'has_entry_point': has_entry, 'fallback': True}

def main():
    if len(sys.argv) != 2:
        print("使用方法: python check_assembly_entry.py <assembly_path>", file=sys.stderr)
        sys.exit(2)

    assembly_path = sys.argv[1]
    result = check_assembly_entry_point(assembly_path)

    if result is None:
        print(json.dumps({'error': '无法检测程序集', 'has_entry_point': False}))
        sys.exit(2)

    # 输出 JSON 结果
    print(json.dumps(result))

    # 返回退出码
    if result.get('has_entry_point', False):
        sys.exit(0)  # 有入口点
    else:
        sys.exit(1)  # 没有入口点

if __name__ == '__main__':
    main()
