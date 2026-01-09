# BTA64 - Run Linux ARM64 (glibc) executables on Android
# 基于box64/libhybris/android2gnulinux的兼容层实现
#
# 模块化架构:
#   src/
#   ├── bta64_core.c          - 核心运行时
#   ├── bta64_loader.c        - ELF加载器
#   ├── bta64_runner.c        - 程序执行
#   ├── bta64_dynlink.c       - 动态链接入口
#   ├── bta64_stdio.c         - FILE结构转换
#   ├── bta64_tls.c           - TLS和ctype包装
#   ├── bta64_sharedlib.c     - 共享库加载
#   ├── dynlink/              - 动态链接子模块
#   │   ├── bta64_log.c
#   │   ├── bta64_symbol_table.c
#   │   ├── bta64_resolver.c
#   │   └── bta64_reloc.c
#   └── wrappers/             - 函数包装器
#       ├── wrapper_libc.c
#       ├── wrapper_stat.c
#       ├── wrapper_locale.c
#       ├── wrapper_fortify.c
#       ├── wrapper_gettext.c
#       └── wrapper_cxx.c

CC ?= gcc
AR ?= ar
CFLAGS ?= -Wall -Wextra -Wno-unused-parameter -fPIC
CFLAGS += -I./src/include -I./src

# 调试/发布模式
ifdef DEBUG
CFLAGS += -g -O0 -DDEBUG
else
CFLAGS += -O2
endif

# Android NDK编译
ifdef ANDROID_NDK
CC = $(ANDROID_NDK)/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android$(ANDROID_API)-clang
CFLAGS += -DANDROID
LDFLAGS += -llog
endif

# ============================================================================
# 源文件
# ============================================================================

# 核心模块
CORE_SRCS = \
	src/bta64_core.c \
	src/bta64_loader.c \
	src/bta64_runner.c \
	src/bta64_dynlink.c \
	src/bta64_stdio.c \
	src/bta64_tls.c \
	src/bta64_sharedlib.c

# 动态链接子模块
DYNLINK_SRCS = \
	src/dynlink/bta64_log.c \
	src/dynlink/bta64_symbol_table.c \
	src/dynlink/bta64_resolver.c \
	src/dynlink/bta64_reloc.c

# 函数包装器
WRAPPER_SRCS = \
	src/wrappers/wrapper_common.c \
	src/wrappers/wrapper_libc.c \
	src/wrappers/wrapper_stat.c \
	src/wrappers/wrapper_locale.c \
	src/wrappers/wrapper_fortify.c \
	src/wrappers/wrapper_gettext.c \
	src/wrappers/wrapper_cxx.c

# x64动态重编译 (仅ARM64目标)
X64_SRCS = \
	src/x64/bddisasm_adapter.c \
	src/x64/dynarec_translate.c \
	src/x64/x64_wrapper_bridge.c \
	src/x64/inst_table.c

# 所有源文件
ALL_SRCS = $(CORE_SRCS) $(DYNLINK_SRCS) $(WRAPPER_SRCS) $(X64_SRCS)
ALL_OBJS = $(ALL_SRCS:.c=.o)

# 兼容层 (旧接口,保留兼容)
COMPAT_SRCS = \
	src/compat/compat_core.c \
	src/compat/wrapped_libc.c
COMPAT_OBJS = $(COMPAT_SRCS:.c=.o)

# ============================================================================
# 目标
# ============================================================================

.PHONY: all clean install android test dirs

all: dirs libbta64.so libbta64.a

# 创建必要目录
dirs:
	@mkdir -p src/dynlink src/wrappers

# 共享库
libbta64.so: $(ALL_OBJS)
	$(CC) $(CFLAGS) -shared -o $@ $^ -ldl -lpthread $(LDFLAGS)

# 静态库
libbta64.a: $(ALL_OBJS)
	$(AR) rcs $@ $^

# 兼容层库 (旧接口)
libbta64compat.so: $(COMPAT_OBJS)
	$(CC) $(CFLAGS) -shared -o $@ $^ -ldl -lpthread $(LDFLAGS)

# 编译规则
%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

# ============================================================================
# 安装
# ============================================================================

PREFIX ?= /usr/local

install: libbta64.so libbta64.a
	install -d $(DESTDIR)$(PREFIX)/lib
	install -d $(DESTDIR)$(PREFIX)/include/bta64
	install -m 644 libbta64.so $(DESTDIR)$(PREFIX)/lib/
	install -m 644 libbta64.a $(DESTDIR)$(PREFIX)/lib/
	install -m 644 src/include/*.h $(DESTDIR)$(PREFIX)/include/bta64/

# ============================================================================
# 清理
# ============================================================================

clean:
	rm -f libbta64.so libbta64.a libbta64compat.so
	rm -f $(ALL_OBJS) $(COMPAT_OBJS)

# ============================================================================
# Android构建
# ============================================================================

android: 
	@echo "Building for Android..."
	@if [ -z "$(ANDROID_NDK)" ]; then \
		echo "Error: ANDROID_NDK not set"; \
		exit 1; \
	fi
	$(MAKE) ANDROID_NDK=$(ANDROID_NDK) ANDROID_API=$(ANDROID_API)

# ============================================================================
# 打印信息
# ============================================================================

info:
	@echo "BTA64 Dynamic Linker - Modular Architecture"
	@echo ""
	@echo "Core modules:"
	@for f in $(CORE_SRCS); do echo "  $$f"; done
	@echo ""
	@echo "Dynamic link modules:"
	@for f in $(DYNLINK_SRCS); do echo "  $$f"; done
	@echo ""
	@echo "Wrapper modules:"
	@for f in $(WRAPPER_SRCS); do echo "  $$f"; done
