# BTA64 终极性能架构设计

## 最高性能动态二进制翻译架构

基于 Rosetta 2、QEMU、FEX、Box64 等项目的最佳实践，结合学术研究的最新成果。

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        BTA64 Ultimate Performance Pipeline                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  x86/x64 ELF Binary                                                         │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Phase 0: AOT Pre-Translation                      │   │
│  │  ┌──────────────┐                                                    │   │
│  │  │  AOT Cache   │ ← 安装时/首次运行预编译热点代码                     │   │
│  │  │  检查        │                                                    │   │
│  │  └──────────────┘                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│       │ miss                    │ hit                                       │
│       ▼                         ▼                                           │
│  ┌───────────────────┐    ┌────────────────────┐                           │
│  │                   │    │  直接执行 AOT 代码  │                           │
│  │  JIT Pipeline     │    │  (最高性能)         │                           │
│  │                   │    └────────────────────┘                           │
│  └───────────────────┘                                                     │
│       │                                                                     │
│       ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Phase 1: Fast Interpretation                    │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │  Tier 0: 模板解释器 (Template Interpreter)                    │   │   │
│  │  │  - 每条 x64 指令对应预生成的 ARM64 代码片段                    │   │   │
│  │  │  - 无优化，但启动快                                           │   │   │
│  │  │  - 收集 Profile 信息 (执行计数、分支方向)                      │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│       │ 热点检测 (exec_count > threshold1)                                 │
│       ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Phase 2: Baseline JIT                           │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │  Tier 1: 基线 JIT 编译器                                       │   │   │
│  │  │  - x64 → FEX-style IR → ARM64                                  │   │   │
│  │  │  - 快速编译，基本优化                                          │   │   │
│  │  │  - SSE 寄存器映射到 NEON                                       │   │   │
│  │  │  - 继续收集 Profile 信息                                       │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│       │ 超热点检测 (exec_count > threshold2)                               │
│       ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Phase 3: Optimizing JIT                         │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │  Tier 2: 优化 JIT 编译器                                       │   │   │
│  │  │  - 基于 Profile 的优化 (PGO)                                   │   │   │
│  │  │  - 内联热点函数                                                │   │   │
│  │  │  - 循环展开、向量化                                            │   │   │
│  │  │  - 逃逸分析、标量替换                                          │   │   │
│  │  │  - 冗余标志消除                                                │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│       │ (可选) 极热代码                                                    │
│       ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Phase 4: Super-Optimizing JIT                   │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │  Tier 3: 超级优化编译器                                        │   │   │
│  │  │  - 整个函数/循环的全局优化                                     │   │   │
│  │  │  - 投机优化 + 去优化路径                                       │   │   │
│  │  │  - 可选: 使用 LLVM 后端                                        │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 核心优化技术

### 1. 多层 JIT 编译 (Tiered Compilation)

```c
/* 类似 Java HotSpot / V8 的分层编译 */

typedef enum {
    TIER_INTERPRETER = 0,   // 解释执行，收集 profile
    TIER_BASELINE    = 1,   // 快速 JIT，基本优化
    TIER_OPTIMIZED   = 2,   // 完整优化 JIT
    TIER_SUPER       = 3,   // 超级优化 (可选 LLVM)
} compile_tier_t;

typedef struct {
    uint64_t x64_addr;
    uint32_t exec_count;
    uint32_t branch_taken;
    uint32_t branch_not_taken;
    compile_tier_t current_tier;
    void* code[4];  // 每层的编译结果
} code_block_profile_t;

/* 升级阈值 */
#define TIER1_THRESHOLD   100     // 执行 100 次后升级到 Tier 1
#define TIER2_THRESHOLD   10000   // 执行 10000 次后升级到 Tier 2
#define TIER3_THRESHOLD   100000  // 执行 100000 次后升级到 Tier 3

/* 热点检测和升级 */
void check_and_upgrade(code_block_profile_t* profile) {
    if (profile->exec_count > TIER3_THRESHOLD && profile->current_tier < TIER_SUPER) {
        schedule_recompile(profile, TIER_SUPER);
    } else if (profile->exec_count > TIER2_THRESHOLD && profile->current_tier < TIER_OPTIMIZED) {
        schedule_recompile(profile, TIER_OPTIMIZED);
    } else if (profile->exec_count > TIER1_THRESHOLD && profile->current_tier < TIER_BASELINE) {
        schedule_recompile(profile, TIER_BASELINE);
    }
}
```

### 2. SSE/AVX 寄存器完美映射

```c
/* 类似 Rosetta 2 的 XMM → NEON 映射 */

/*
 * x64 XMM0-XMM15 (128-bit) 直接映射到 ARM64 V0-V15 (128-bit)
 * 避免内存加载/存储，直接使用 NEON 指令
 */

typedef struct {
    /* 寄存器映射状态 */
    int8_t  xmm_to_neon[16];    // XMM[i] -> NEON[xmm_to_neon[i]]
    int8_t  neon_to_xmm[32];    // NEON[i] -> XMM[neon_to_xmm[i]] (-1 = 空闲)
    uint32_t dirty_mask;        // 脏寄存器位图
    uint32_t used_mask;         // 使用中的 NEON 寄存器
} sse_regalloc_state_t;

/* 编译时寄存器分配 */
static inline int alloc_xmm_to_neon(sse_regalloc_state_t* state, int xmm) {
    if (state->xmm_to_neon[xmm] >= 0) {
        return state->xmm_to_neon[xmm];  // 已分配
    }
    
    // 找空闲 NEON 寄存器
    for (int i = 0; i < 16; i++) {
        if (state->neon_to_xmm[i] < 0) {
            state->xmm_to_neon[xmm] = i;
            state->neon_to_xmm[i] = xmm;
            state->used_mask |= (1 << i);
            return i;
        }
    }
    
    // 需要溢出 - 选择 LRU
    return spill_and_alloc(state, xmm);
}
```

### 3. 高级 IR 优化 Passes

```c
/* 参考 LLVM/FEX 的优化 passes */

typedef struct ir_pass {
    const char* name;
    int (*run)(ir_function_t* func, pass_context_t* ctx);
    int priority;  // 执行顺序
} ir_pass_t;

/* 优化 passes 列表 */
static ir_pass_t g_optimization_passes[] = {
    // Tier 1 优化 (快速)
    {"constant_folding",     pass_const_fold,          10},
    {"dead_code_elim",       pass_dce,                 20},
    {"copy_propagation",     pass_copy_prop,           30},
    
    // Tier 2 优化 (完整)
    {"common_subexpr_elim",  pass_cse,                 40},
    {"redundant_flag_elim",  pass_flag_elim,           50},
    {"strength_reduction",   pass_strength_reduce,     60},
    {"loop_invariant_motion",pass_licm,                70},
    
    // Tier 3 优化 (激进)
    {"function_inlining",    pass_inline,              80},
    {"loop_unrolling",       pass_unroll,              90},
    {"vectorization",        pass_vectorize,          100},
    {"speculative_opt",      pass_speculate,          110},
    
    {NULL, NULL, 0}
};

/* 冗余标志消除 - 关键优化 */
void pass_flag_elim(ir_function_t* func) {
    /*
     * x86 很多指令设置 FLAGS，但通常只有少数被使用
     * 消除不使用的 FLAGS 计算
     * 
     * 例如：
     *   ADD RAX, RBX    ; 设置 ZNCOV
     *   MOV RCX, RAX    ; 不使用 FLAGS
     *   CMP RCX, 0      ; 重新设置 FLAGS
     *   JE label        ; 只使用 Z
     *   
     * 可以消除 ADD 的 FLAGS 计算
     */
    for (ir_inst_t* inst = func->entry; inst; inst = inst->next) {
        if (inst->sets_flags && !is_flags_used_before_overwrite(inst)) {
            inst->sets_flags = false;  // 不生成 FLAGS 计算代码
        }
    }
}
```

### 4. 代码布局优化

```c
/* 类似 BOLT/Codestitcher 的代码布局优化 */

typedef struct {
    uint64_t from_addr;
    uint64_t to_addr;
    uint32_t taken_count;
    uint32_t not_taken_count;
} branch_profile_t;

/* 基于 Profile 的基本块重排 */
void optimize_code_layout(compiled_function_t* func, profile_data_t* profile) {
    /*
     * 将热点代码路径放在一起，减少 I-cache miss
     * 
     * 原始布局:     优化后布局:
     * BB1           BB1  (hot)
     * BB2 (cold)    BB3  (hot)
     * BB3 (hot)     BB4  (hot)
     * BB4 (hot)     BB2  (cold) <- 移到末尾
     */
    
    // 计算基本块热度
    for (int i = 0; i < func->num_blocks; i++) {
        func->blocks[i].heat = calculate_block_heat(&func->blocks[i], profile);
    }
    
    // 按热度排序，热代码在前
    qsort(func->blocks, func->num_blocks, sizeof(basic_block_t), compare_by_heat);
    
    // 重新生成代码
    regenerate_code(func);
}
```

### 5. AOT 缓存系统

```c
/* 类似 Rosetta 2 的 AOT 缓存 */

typedef struct {
    char magic[8];              // "BTA64AOT"
    uint32_t version;
    uint32_t flags;
    uint64_t x64_hash;          // 原始 x64 代码哈希
    uint32_t code_size;
    uint32_t reloc_count;
    // 后跟: ARM64 代码 + 重定位信息
} aot_cache_entry_t;

typedef struct {
    char magic[8];              // "BTA64HDR"
    uint32_t version;
    uint32_t entry_count;
    uint64_t total_size;
    // 后跟: entry 数组
} aot_cache_header_t;

/* AOT 缓存位置: /data/data/app/cache/bta64_aot/ */

/* 首次安装时预编译 */
void aot_precompile_elf(const char* elf_path) {
    elf_t* elf = load_elf(elf_path);
    
    // 识别所有函数入口
    for (int i = 0; i < elf->num_functions; i++) {
        function_t* func = &elf->functions[i];
        
        // 用 Tier 2 级别编译
        compiled_code_t* code = compile_function(func, TIER_OPTIMIZED);
        
        // 保存到 AOT 缓存
        aot_cache_save(func->addr, code);
    }
}

/* 运行时 AOT 查找 */
void* aot_lookup(uint64_t x64_addr) {
    uint64_t hash = hash_x64_block(x64_addr);
    aot_cache_entry_t* entry = aot_cache_find(hash);
    
    if (entry && verify_entry(entry, x64_addr)) {
        return aot_cache_load_code(entry);
    }
    return NULL;
}
```

### 6. 投机优化和去优化

```c
/* 类似 V8/HotSpot 的投机优化 */

typedef struct {
    uint64_t guard_addr;        // 检查点地址
    void* deopt_state;          // 去优化状态
    void* slow_path;            // 慢速路径代码
} deoptimization_point_t;

/* 投机内联 */
void speculative_inline(ir_function_t* caller, ir_call_t* call, profile_t* profile) {
    // 检查调用目标是否稳定
    if (profile->call_target_monomorphic) {
        // 90%+ 调用同一个目标，可以投机内联
        ir_function_t* callee = profile->most_common_target;
        
        // 插入类型检查 guard
        ir_inst_t* guard = ir_emit_guard(caller,
            IR_GUARD_CALL_TARGET,
            call->target,
            callee->addr,
            create_deopt_point(call));
        
        // 内联 callee 的代码
        inline_function(caller, call, callee);
    }
}

/* 运行时去优化 */
void deoptimize(deoptimization_point_t* point, x64_cpu_state_t* cpu) {
    // 恢复到解释器状态
    restore_interpreter_state(cpu, point->deopt_state);
    
    // 降级到 Tier 1
    downgrade_to_tier1(point->guard_addr);
    
    // 继续用解释器执行
    interpreter_continue(cpu);
}
```

### 7. 并行编译

```c
/* 后台编译线程池 */

typedef struct {
    pthread_t threads[4];       // 编译线程
    compile_queue_t queue;      // 编译任务队列
    atomic_int active_count;
} compile_thread_pool_t;

/* 异步编译请求 */
void async_compile(uint64_t x64_addr, compile_tier_t tier) {
    compile_task_t task = {
        .x64_addr = x64_addr,
        .tier = tier,
        .priority = calculate_priority(x64_addr)
    };
    
    // 当前继续用旧代码执行
    // 编译完成后原子替换
    queue_push(&g_compile_pool.queue, &task);
}

/* 编译线程 */
void* compile_thread(void* arg) {
    while (1) {
        compile_task_t task;
        queue_pop(&g_compile_pool.queue, &task);
        
        // 编译
        compiled_code_t* code = compile(task.x64_addr, task.tier);
        
        // 原子替换
        atomic_store(&g_code_cache[task.x64_addr], code);
    }
}
```

## 性能对比预估

| 技术 | 预期性能提升 | 实现复杂度 |
|------|-------------|-----------|
| 模板解释器 (Tier 0) | 基准线 | 低 |
| 基线 JIT (Tier 1) | 5-10x | 中 |
| 优化 JIT (Tier 2) | 20-50x | 高 |
| 超级优化 (Tier 3) | 50-100x | 很高 |
| AOT 缓存 | +20% (启动时间) | 中 |
| SSE→NEON 映射 | +50% (向量代码) | 中 |
| 冗余标志消除 | +10-30% | 中 |
| 代码布局优化 | +5-15% | 中 |

## 推荐实现路径

### 阶段 1 (1-2 周): 基础框架
1. 实现模板解释器框架
2. 实现 Profile 数据收集
3. 实现基本的代码缓存

### 阶段 2 (2-4 周): Tier 1 JIT
1. 移植 FEX 的 IR 定义
2. 实现 x64→IR 转换
3. 实现 IR→ARM64 代码生成
4. 实现 SSE 寄存器映射

### 阶段 3 (4-8 周): Tier 2 优化 JIT
1. 实现优化 passes (DCE, CSE, Flag Elim)
2. 实现 Profile-guided 优化
3. 实现循环优化

### 阶段 4 (4-8 周): AOT 和高级优化
1. 实现 AOT 缓存系统
2. 实现代码布局优化
3. 实现投机优化框架

### 阶段 5 (可选): Tier 3 LLVM 后端
1. FEX IR → LLVM IR 转换
2. 利用 LLVM 的完整优化管线
3. 实现去优化路径

## 参考项目

| 项目 | 关键技术 | 参考价值 |
|------|---------|---------|
| Rosetta 2 | AOT+JIT, 寄存器映射 | 架构设计 |
| FEX | SSA IR, 向量优化 | IR 和代码生成 |
| QEMU TCG | 模板解释器, 多后端 | 解释器设计 |
| Box64 | Wrapper 系统, 向量指令 | 实用技巧 |
| V8/HotSpot | 分层编译, 投机优化 | JIT 优化 |
| LLVM | 优化 passes | 优化算法 |

