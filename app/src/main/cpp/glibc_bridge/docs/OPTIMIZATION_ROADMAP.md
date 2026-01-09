# BTA64 Optimization Roadmap

## 借鉴自 Cranelift 和 Box64 的架构改进

### 1. SSE 寄存器缓存系统

类似 Box64 的实现，将 x64 XMM 寄存器映射到 ARM64 NEON 寄存器：

```c
/* SSE 寄存器缓存 - 类似 Box64 的 ssecache */
typedef struct {
    int8_t  neon_reg;       /* 映射到的 ARM64 NEON 寄存器 (-1 = 未缓存) */
    uint8_t dirty;          /* 是否需要写回内存 */
    uint8_t valid;          /* 缓存是否有效 */
} sse_cache_entry_t;

typedef struct {
    sse_cache_entry_t xmm[16];      /* XMM0-XMM15 缓存状态 */
    uint32_t          used_neon;    /* NEON 寄存器使用位图 */
    uint32_t          dirty_mask;   /* 脏寄存器位图 */
} sse_cache_t;

/* 获取 XMM 寄存器，可能从内存加载到 NEON */
int sse_get_reg(dynarec_context_t* dyn, int xmm_idx, int for_write);

/* 确保所有脏 XMM 寄存器写回内存 (函数调用前) */
void sse_flush_cache(dynarec_context_t* dyn);

/* 在函数调用后，XMM0 可能包含返回值 */
void sse_mark_return(dynarec_context_t* dyn);
```

### 2. Wrapper 自动生成系统

类似 Box64 的 wrapper 生成，基于函数签名：

```c
/* 函数签名编码 (Box64 风格) */
typedef enum {
    /* 返回类型 */
    RET_VOID = 'v',
    RET_INT  = 'i',
    RET_LONG = 'l',
    RET_FLOAT = 'f',
    RET_DOUBLE = 'd',
    RET_PTR  = 'p',
    
    /* 参数类型 */
    ARG_INT  = 'i',
    ARG_LONG = 'l',
    ARG_FLOAT = 'f',
    ARG_DOUBLE = 'd',
    ARG_PTR  = 'p',
} type_char_t;

/* 签名字符串: "dFpp" = double func(pointer, pointer) */
/* 自动生成 wrapper 的函数 */
void* generate_wrapper(const char* signature, void* native_func);
```

### 3. JIT/AOT 双模式 (借鉴 Cranelift)

```c
/* JIT 模式 - 运行时翻译 */
typedef struct {
    code_cache_t*   cache;          /* 已翻译代码缓存 */
    uint32_t        cache_size;
    uint32_t        threshold;      /* 热点检测阈值 */
} jit_context_t;

/* AOT 模式 - 预编译缓存 */
typedef struct {
    int             fd;             /* AOT 缓存文件 */
    void*           mapped;         /* mmap 映射区域 */
    aot_header_t*   header;
    aot_entry_t*    entries;
} aot_context_t;

/* 混合模式: 优先使用 AOT，回退到 JIT */
dynablock_t* get_translated_block(uint64_t x64_addr) {
    /* 1. 检查 AOT 缓存 */
    dynablock_t* block = aot_lookup(x64_addr);
    if (block) return block;
    
    /* 2. 检查 JIT 缓存 */
    block = jit_lookup(x64_addr);
    if (block) return block;
    
    /* 3. JIT 编译新块 */
    return jit_compile(x64_addr);
}
```

### 4. SSE 指令完整实现计划

#### 阶段 1: 数据移动 (已部分完成)
- [x] MOVSD, MOVSS (内存 ↔ XMM)
- [x] MOVQ (XMM ↔ GPR)
- [x] MOVD (XMM ↔ GPR)
- [ ] MOVAPS, MOVUPS (128-bit 对齐/非对齐)
- [ ] MOVLPS, MOVHPS (64-bit 低/高部分)

#### 阶段 2: 算术运算
- [ ] ADDSD, ADDSS (标量加法)
- [ ] SUBSD, SUBSS (标量减法)
- [ ] MULSD, MULSS (标量乘法)
- [ ] DIVSD, DIVSS (标量除法)
- [ ] ADDPD, ADDPS (打包加法)
- [ ] SQRTSD, SQRTSS (平方根)

#### 阶段 3: 比较和逻辑
- [x] COMISD, UCOMISD (有序/无序比较)
- [ ] COMISS, UCOMISS (单精度比较)
- [ ] CMPPD, CMPPS (打包比较)
- [ ] ANDPS, ORPS, XORPS (位逻辑)

#### 阶段 4: 类型转换
- [ ] CVTSI2SD, CVTSI2SS (整数→浮点)
- [ ] CVTSD2SI, CVTSS2SI (浮点→整数)
- [ ] CVTSD2SS, CVTSS2SD (精度转换)

### 5. 性能优化策略

#### Peephole 优化 (借鉴 Cranelift)
```c
/* 优化连续的加载-存储操作 */
void peephole_optimize(arm64_instruction_t* code, int count) {
    for (int i = 0; i < count - 1; i++) {
        /* 消除冗余的 load-store */
        if (is_store(code[i]) && is_load(code[i+1]) &&
            same_addr(code[i], code[i+1])) {
            /* 用寄存器移动替代 */
            replace_with_mov(&code[i], &code[i+1]);
        }
        
        /* 合并连续的 MOV 操作 */
        if (is_mov(code[i]) && is_mov(code[i+1]) &&
            code[i].dst == code[i+1].src) {
            /* 直接移动到最终目标 */
            code[i].dst = code[i+1].dst;
            code[i+1] = NOP;
        }
    }
}
```

#### 块级优化
```c
/* 热点代码内联 */
void hot_block_inline(dynablock_t* block) {
    if (block->exec_count > HOT_THRESHOLD) {
        /* 内联频繁调用的小函数 */
        inline_callees(block);
        
        /* 循环展开 */
        if (has_small_loop(block)) {
            unroll_loop(block, 4);
        }
    }
}
```

## 实现优先级

1. **高优先级**: SSE 算术指令 (ADDSD, SUBSD, MULSD, DIVSD)
2. **高优先级**: Wrapper 签名系统完善
3. **中优先级**: SSE 寄存器缓存系统
4. **中优先级**: AOT 缓存支持
5. **低优先级**: Peephole 优化器
6. **低优先级**: 热点代码内联

## 参考资源

- Box64 源码: https://github.com/ptitSeb/box64
- Cranelift: https://github.com/bytecodealliance/wasmtime/tree/main/cranelift
- ARM64 NEON 参考: https://developer.arm.com/architectures/instruction-sets/simd-isas/neon

