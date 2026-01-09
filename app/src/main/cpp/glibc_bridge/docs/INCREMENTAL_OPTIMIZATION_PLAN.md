# BTA64 增量优化计划

## 现有架构分析

**现有代码已经有很多优化基础设施，只需激活和完善！**

### 已存在但未使用的功能

1. **SSE Cache 结构** (`dynarec_core.h:429`)
   ```c
   sse_cache_entry_t   ssecache[16];   // 已定义！
   ```

2. **NEON Cache 结构** (`dynarec_core.h:410`)
   ```c
   neon_cache_entry_t  neon[32];       // 已定义！
   ```

3. **寄存器映射定义** (`dynarec_core.h:367`)
   ```c
   #define vXMM0       0   /* XMM0-7 in v0-v7 */
   #define vXMM8       16  /* XMM8-15 in v16-v23 */
   ```

## 增量优化路径（保留现有代码）

### 阶段 1：激活 SSE 寄存器缓存（1-2天）

**目标**: XMM 寄存器直接映射到 NEON，避免每次都从内存加载

**修改文件**: `dynarec_translate.c`

```c
/* 添加到 dynarec_context_t 或新建 sse_cache_t */
typedef struct {
    int8_t  xmm_to_neon[16];    /* XMM[i] -> NEON reg (-1=未分配) */
    int8_t  neon_to_xmm[32];    /* NEON[i] -> XMM reg (-1=空闲) */
    uint16_t dirty;              /* 脏位图 */
} sse_cache_t;

/* 在 block 开始时初始化 */
static void sse_cache_init(sse_cache_t* cache) {
    memset(cache->xmm_to_neon, -1, 16);
    memset(cache->neon_to_xmm, -1, 32);
    cache->dirty = 0;
}

/* 获取 XMM 寄存器对应的 NEON 寄存器 */
static int sse_get_neon(sse_cache_t* cache, int xmm) {
    if (cache->xmm_to_neon[xmm] >= 0) {
        return cache->xmm_to_neon[xmm];  /* 已在 NEON 中 */
    }
    
    /* 分配新的 NEON 寄存器 (v0-v7 给 XMM0-7, v16-v23 给 XMM8-15) */
    int neon = (xmm < 8) ? xmm : (16 + xmm - 8);
    cache->xmm_to_neon[xmm] = neon;
    cache->neon_to_xmm[neon] = xmm;
    
    /* 从内存加载到 NEON */
    /* LDRq_U12(neon, xEmu, offsetof(x64_cpu_state_t, xmm) + xmm*16); */
    return neon;
}

/* block 结束时刷新脏寄存器 */
static void sse_cache_flush(sse_cache_t* cache, uint32_t** code) {
    for (int i = 0; i < 16; i++) {
        if (cache->dirty & (1 << i)) {
            int neon = cache->xmm_to_neon[i];
            if (neon >= 0) {
                /* STRq_U12(neon, xEmu, offsetof(x64_cpu_state_t, xmm) + i*16); */
            }
        }
    }
}
```

**改动点**:
- 在 `BTA64_ICLASS_SSE_MOV`, `BTA64_ICLASS_SSE_ARITH` 等 case 中使用缓存

### 阶段 2：添加更多 SSE 指令（3-5天）

**直接从 Box64 移植**, 文件: `box64/src/dynarec/arm64/dynarec_arm64_f20f.c`

需要实现的高频指令:
```c
/* 算术 */
case 0x58: /* ADDSD */
case 0x59: /* MULSD */
case 0x5C: /* SUBSD */
case 0x5E: /* DIVSD */

/* 转换 */
case 0x2A: /* CVTSI2SD */
case 0x2C: /* CVTTSD2SI */
case 0x2D: /* CVTSD2SI */

/* 比较 */
case 0x2E: /* UCOMISD - 已实现 */
case 0x2F: /* COMISD - 已实现 */
```

**示例实现** (ADDSD):
```c
case BTA64_ICLASS_SSE_ARITH:
    if (decoded->opcode == 0x58) {  /* ADDSD */
        int dst_neon = sse_get_neon(&cache, op0->u.reg.index);
        int src_neon = sse_get_neon(&cache, op1->u.reg.index);
        
        /* FADD Dd, Dn, Dm */
        FADDD(dst_neon, dst_neon, src_neon);
        
        cache.dirty |= (1 << op0->u.reg.index);
    }
    break;
```

### 阶段 3：添加 Profile 收集（2-3天）

**目标**: 为将来的优化 JIT 收集热点信息

```c
/* 添加到 dynablock_t */
typedef struct dynablock {
    uint64_t x64_addr;
    void* arm64_code;
    uint32_t size;
    
    /* 新增 Profile 信息 */
    uint32_t exec_count;        /* 执行次数 */
    uint32_t branch_taken;      /* 分支跳转次数 */
    uint32_t branch_not_taken;  /* 分支不跳次数 */
    uint8_t  tier;              /* 当前编译层级 */
} dynablock_t;

/* 在 block 入口添加计数器 */
static void emit_profile_counter(dynablock_t* block, uint32_t** code) {
    /* 加载 exec_count 地址 */
    MOV64x(xSCRATCH0, (uint64_t)&block->exec_count);
    /* 原子加 1 */
    LDADDw(xSCRATCH1, xZR, xSCRATCH0);  /* 需要添加这个宏 */
}
```

### 阶段 4：实现代码缓存（1-2天）

**目标**: 缓存已翻译的代码块，避免重复翻译

```c
/* 简单的哈希表缓存 */
#define CODE_CACHE_SIZE 4096

typedef struct {
    uint64_t x64_addr;
    dynablock_t* block;
} code_cache_entry_t;

static code_cache_entry_t g_code_cache[CODE_CACHE_SIZE];

static dynablock_t* code_cache_lookup(uint64_t x64_addr) {
    uint32_t hash = (x64_addr >> 2) & (CODE_CACHE_SIZE - 1);
    if (g_code_cache[hash].x64_addr == x64_addr) {
        return g_code_cache[hash].block;
    }
    return NULL;
}

static void code_cache_insert(uint64_t x64_addr, dynablock_t* block) {
    uint32_t hash = (x64_addr >> 2) & (CODE_CACHE_SIZE - 1);
    g_code_cache[hash].x64_addr = x64_addr;
    g_code_cache[hash].block = block;
}
```

### 阶段 5：添加 AOT 缓存（3-5天）

**目标**: 保存编译结果到文件，下次启动直接加载

```c
/* AOT 缓存文件格式 */
typedef struct {
    char magic[8];              /* "BTA64AOT" */
    uint32_t version;
    uint64_t elf_hash;          /* ELF 文件哈希 */
    uint32_t entry_count;
} aot_cache_header_t;

typedef struct {
    uint64_t x64_addr;
    uint32_t code_offset;
    uint32_t code_size;
    uint32_t reloc_count;
} aot_cache_entry_t;

/* 保存到: /data/data/com.app/cache/bta64_aot_<elf_hash>.bin */
```

## 优化优先级排序

| 优先级 | 优化 | 预期提升 | 工作量 | 依赖 |
|--------|------|---------|--------|------|
| **1** | SSE 寄存器缓存 | +50% (SSE代码) | 1-2天 | 无 |
| **2** | 更多 SSE 指令 | +30% (数学运算) | 3-5天 | 无 |
| **3** | 代码缓存 | +20% (重复block) | 1-2天 | 无 |
| **4** | Profile 收集 | 为将来准备 | 2-3天 | 无 |
| **5** | AOT 缓存 | +启动时间 | 3-5天 | 代码缓存 |
| **6** | 优化 JIT (Tier 2) | +2-3x | 2-4周 | Profile |
| **7** | FEX IR 集成 | 长期收益 | 1-2月 | 无 |

## 建议实施顺序

### 本周可完成（最大收益）

1. **激活 SSE 缓存** - 当前 SSE 操作每次都从内存加载，改用直接 NEON 寄存器
2. **实现 ADDSD/SUBSD/MULSD/DIVSD** - 这4条指令覆盖大部分浮点运算

### 下周可完成

3. **代码块缓存** - 避免重复翻译同一 block
4. **更多 SSE 转换指令** - CVTSI2SD, CVTSD2SI 等

### 长期（1-2月）

5. **Profile 收集 + 热点优化**
6. **AOT 缓存**
7. **考虑 FEX IR 集成**

## 代码修改示例

### 在现有 dynarec_translate.c 中激活缓存

```diff
 static void emit_sse_mov(dynarec_context_t* ctx, x64_inst_info_t* decoded) {
     bta64_operand_t* dst = &decoded->operands[0];
     bta64_operand_t* src = &decoded->operands[1];
     
+    /* 使用 SSE 缓存而不是直接内存访问 */
+    sse_cache_t* cache = &ctx->sse_cache;
+    
     if (dst->type == BTA64_OP_REG && dst->u.reg.type == BTA64_REG_XMM) {
         if (src->type == BTA64_OP_REG && src->u.reg.type == BTA64_REG_XMM) {
-            /* 旧: 通过内存 */
-            int src_off = offsetof(x64_cpu_state_t, xmm_u64) + src->u.reg.index * 16;
-            int dst_off = offsetof(x64_cpu_state_t, xmm_u64) + dst->u.reg.index * 16;
-            LDRq_U12(vSCRATCH0, xEmu, src_off);
-            STRq_U12(vSCRATCH0, xEmu, dst_off);
+            /* 新: 直接 NEON 寄存器 */
+            int dst_neon = sse_get_neon(cache, dst->u.reg.index);
+            int src_neon = sse_get_neon(cache, src->u.reg.index);
+            MOV_Vd(dst_neon, src_neon);  /* 单条指令! */
+            cache->dirty |= (1 << dst->u.reg.index);
         }
     }
 }
```

## 结论

**不需要删除任何现有代码**，只需：
1. 激活已定义的数据结构（`sse_cache_entry_t` 等）
2. 在 SSE 指令处理中使用缓存
3. 添加更多 SSE 指令实现
4. 逐步添加 Profile 和 AOT 功能

现有架构设计良好，已经为这些优化预留了接口！

