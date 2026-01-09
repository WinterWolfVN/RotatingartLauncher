# BTA64 架构升级方案：结合 FEX IR 和 Cranelift 优化

## 背景分析

### FEX 的优势
- **完整的 SSA IR**：3000+ 行的 IR 定义 (`IR.json`)
- **成熟的 x86→IR 转换**：`OpcodeDispatcher` 完整实现所有 x86/x64 指令
- **完整的向量支持**：`VectorOps.cpp` 有 4500+ 行，覆盖 SSE/AVX
- **优化 passes**：包括 DCE、寄存器分配、冗余标志消除等
- **C++ 实现**：可以直接集成到 BTA64

### Cranelift 的优势
- **先进的 IR 优化**：Peephole、常量折叠、强度削减
- **高效的代码生成**：寄存器分配、指令选择
- **JIT/AOT 双模式**：灵活的编译策略
- **Rust 生态**：类型安全、内存安全

## 可行方案

### 方案 1：移植 FEX 前端 + 使用 Cranelift 后端（复杂度：高）

```
┌─────────────────────────────────────────────────────────────────┐
│                         BTA64 Pipeline                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  x86/x64 Binary                                                 │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────┐                                               │
│  │   bddisasm  │  ← x86 解码器（已有）                          │
│  └─────────────┘                                               │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │ FEX OpcodeDispatcher│  ← 移植 FEX 前端                       │
│  │   (x86 → FEX IR)    │                                       │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │     FEX IR (SSA)    │  ← 中间表示                            │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │ FEX IR → Cranelift  │  ← IR 转换层（需要开发）               │
│  │      IR 转换        │                                       │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │   Cranelift IR      │                                       │
│  │   + 优化 Passes     │                                       │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │ Cranelift ARM64     │  ← Cranelift 后端                      │
│  │   代码生成          │                                       │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│   ARM64 Native Code                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**优点**：
- 获得 Cranelift 的所有优化
- 代码质量可能最高

**缺点**：
- 需要开发 FEX IR → Cranelift IR 转换层
- Cranelift 是 Rust，需要 FFI 或重写
- 复杂度非常高

### 方案 2：直接移植 FEX 完整管线（推荐，复杂度：中）

```
┌─────────────────────────────────────────────────────────────────┐
│                    移植 FEX 到 BTA64                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  x86/x64 Binary                                                 │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │ FEX Frontend        │  ← 复用 FEX 的解码器                   │
│  │ (Decode + Dispatch) │                                       │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │     FEX IR (SSA)    │  ← 复用 FEX 的 IR 定义                 │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │  FEX 优化 Passes    │  ← 复用 FEX 的优化                     │
│  │  - DCE              │                                       │
│  │  - RegAlloc         │                                       │
│  │  - FlagElim         │                                       │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────┐                                       │
│  │ FEX ARM64 JIT       │  ← 复用 FEX 的 JIT                     │
│  │   Backend           │                                       │
│  └─────────────────────┘                                       │
│       │                                                         │
│       ▼                                                         │
│   ARM64 Native Code                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**优点**：
- FEX 是成熟的生产级实现
- 直接获得所有 SSE/AVX 支持
- 保持 C++ 技术栈
- 有详细文档和测试

**缺点**：
- 需要适配 Android/bionic 环境
- FEX 代码量大（但都是有用的）

### 方案 3：参考 FEX 架构，改进 BTA64（复杂度：低-中）

保留 BTA64 当前架构，但借鉴 FEX 的关键组件：

```c
/* 1. 引入类似 FEX 的 IR 结构 */
typedef enum {
    IR_OP_INVALID = 0,
    
    // ALU ops
    IR_OP_ADD,
    IR_OP_SUB,
    IR_OP_MUL,
    IR_OP_DIV,
    
    // SSE scalar ops
    IR_OP_FADD_SS,      // addss
    IR_OP_FADD_SD,      // addsd
    IR_OP_FSUB_SS,
    IR_OP_FSUB_SD,
    IR_OP_FMUL_SS,
    IR_OP_FMUL_SD,
    IR_OP_FDIV_SS,
    IR_OP_FDIV_SD,
    
    // SSE vector ops
    IR_OP_VADD_PS,      // addps
    IR_OP_VADD_PD,      // addpd
    IR_OP_VSUB_PS,
    IR_OP_VSUB_PD,
    
    // SSE move ops
    IR_OP_VMOV_SS,
    IR_OP_VMOV_SD,
    IR_OP_VMOV_PS,
    IR_OP_VMOV_PD,
    
    // Memory ops
    IR_OP_LOAD,
    IR_OP_STORE,
    IR_OP_LOAD_CONTEXT,
    IR_OP_STORE_CONTEXT,
    
    // Control flow
    IR_OP_JUMP,
    IR_OP_COND_JUMP,
    IR_OP_CALL,
    IR_OP_RET,
    
    IR_OP_MAX
} ir_opcode_t;

/* 2. SSA 值表示 */
typedef struct ir_value {
    uint32_t id;            // SSA ID
    ir_type_t type;         // GPR, FPR, etc.
    uint8_t size;           // 1, 2, 4, 8, 16 bytes
} ir_value_t;

/* 3. IR 指令 */
typedef struct ir_inst {
    ir_opcode_t opcode;
    ir_value_t dest;
    ir_value_t src[4];      // 最多 4 个源操作数
    uint8_t num_src;
    struct ir_inst* next;
    struct ir_inst* prev;
} ir_inst_t;

/* 4. 基本块 */
typedef struct ir_block {
    uint64_t x64_addr;      // 对应的 x64 地址
    ir_inst_t* first;
    ir_inst_t* last;
    struct ir_block* next;
} ir_block_t;
```

## 实现步骤

### 第一阶段：移植 FEX 核心组件

1. **移植 FEX IR 定义** (`IR.json` → C 结构)
   ```bash
   # FEX 使用 Python 从 JSON 生成 C++ 代码
   python Scripts/json_ir_generator.py
   ```

2. **移植 OpcodeDispatcher 的 SSE 部分**
   - `Vector.cpp` - 所有向量操作
   - `AVX_128.cpp` - AVX 128-bit 支持

3. **移植 JIT 后端的向量代码生成**
   - `VectorOps.cpp` - 4500+ 行向量代码生成

### 第二阶段：集成优化 Passes

```c
/* 参考 FEX 的优化 passes */

// 1. 死代码消除 (DCE)
void ir_pass_dce(ir_block_t* block);

// 2. 冗余标志消除
void ir_pass_flag_elimination(ir_block_t* block);

// 3. 常量折叠
void ir_pass_const_fold(ir_block_t* block);

// 4. 寄存器分配
void ir_pass_regalloc(ir_block_t* block, regalloc_result_t* result);
```

### 第三阶段：AOT 缓存支持

```c
/* 参考 FEX 的 AOT 缓存 */
typedef struct {
    uint64_t x64_hash;          // x64 代码块哈希
    uint32_t arm64_offset;      // ARM64 代码偏移
    uint32_t arm64_size;        // ARM64 代码大小
} aot_entry_t;

typedef struct {
    char magic[4];              // "BTA0"
    uint32_t version;
    uint32_t entry_count;
    aot_entry_t entries[];
} aot_cache_header_t;

// 查找 AOT 缓存
void* aot_lookup(uint64_t x64_addr);

// 保存到 AOT 缓存
void aot_save(uint64_t x64_addr, void* arm64_code, size_t size);
```

## 关键文件参考

| FEX 文件 | 功能 | 移植优先级 |
|----------|------|-----------|
| `IR.json` | IR 定义 | 高 |
| `OpcodeDispatcher/Vector.cpp` | x86→IR 向量转换 | 高 |
| `JIT/VectorOps.cpp` | IR→ARM64 向量代码生成 | 高 |
| `IR/Passes/RegisterAllocationPass.cpp` | 寄存器分配 | 中 |
| `IR/Passes/RedundantFlagCalculationElimination.cpp` | 标志优化 | 中 |

## 工作量估计

| 方案 | 工作量 | 预期收益 |
|------|--------|----------|
| 方案 1 (FEX+Cranelift) | 6-12 个月 | 最高代码质量 |
| 方案 2 (移植 FEX) | 3-6 个月 | 完整功能 |
| 方案 3 (参考 FEX) | 1-3 个月 | 增量改进 |

## 建议

**推荐方案 2**：直接移植 FEX 的相关组件到 BTA64。原因：
1. FEX 是经过大量测试的生产级代码
2. C++ 可以直接集成
3. 不需要跨语言 FFI
4. 获得完整的 SSE/AVX 支持

**如果时间有限，可以从方案 3 开始**：
1. 先移植 `VectorOps.cpp` 中的关键向量指令
2. 参考 FEX 的 IR 设计改进 BTA64 的内部表示
3. 逐步添加优化 passes

