# BTA64 表驱动翻译系统

## 已迁移指令

### 表驱动处理 (inst_table.c)
| 类别 | 指令 |
|------|------|
| 算术 | ADD, SUB, ADC, SBB, INC, DEC, NEG, CMP |
| 乘除 | MUL, IMUL, DIV, IDIV |
| 逻辑 | AND, OR, XOR, NOT, TEST |
| 移位 | SHL, SAL, SHR, SAR, ROL, ROR, RCL, RCR |
| 位操作 | BSF, BSR, POPCNT, LZCNT, TZCNT, BSWAP, BT, BTS, BTR, BTC |
| 交换 | XCHG, CMPXCHG, XADD |
| 扩展 | CBW, CWDE, CDQE, CWD, CDQ, CQO |
| 系统 | NOP, PAUSE, MFENCE, LFENCE, SFENCE, RDTSC |

### 手动处理 (dynarec_translate.c)
| 类别 | 指令 | 原因 |
|------|------|------|
| 数据移动 | MOV, MOVSX, MOVZX, LEA | 复杂内存寻址 |
| 栈操作 | PUSH, POP, ENTER, LEAVE | 栈指针操作 |
| 控制流 | JMP, CALL, RET, Jcc | 分支处理 |
| 条件 | SETcc, CMOVcc | 条件码映射 |
| 字符串 | MOVS, STOS, LODS, SCAS, CMPS | REP前缀处理 |
| SSE | 全部 | NEON映射复杂 |

## 架构概述

```
┌─────────────────────────────────────────────────────────────┐
│                    emit_instruction()                        │
├─────────────────────────────────────────────────────────────┤
│  1. 快速路径: inst_table_lookup() 查表                       │
│     └─> inst_translate() 表驱动生成代码                      │
│                                                              │
│  2. 手动路径: switch(iclass) 复杂指令处理                    │
│     └─> MOV, LEA, CALL, JCC, SSE 等需要特殊逻辑的指令        │
└─────────────────────────────────────────────────────────────┘
```

## 文件结构

```
src/x64/
├── inst_table.h          # 指令表数据结构定义
├── inst_table.c          # 表驱动翻译实现
├── dynarec_translate.c   # 主翻译引擎（使用表驱动）
└── arm64_emitter.h       # ARM64 指令生成宏
```

## 添加新指令

### 方法1: 通过配置文件（推荐）

编辑 `tools/inst_config.json`:

```json
{
  "arith": {
    "NEW_INST": {"arm64": "ARM64_OP", "flags": true}
  }
}
```

然后运行: `python tools/codegen.py`

### 方法2: 直接修改指令表

编辑 `src/x64/inst_table.c` 的 `g_inst_map`:

```c
static const inst_map_entry_t g_inst_map[] = {
    // 添加新指令
    { ND_INS_NEW_INST, OP_TYPE, INST_FLAG_XXX },
    ...
};
```

### 方法3: 手动处理（复杂指令）

在 `dynarec_translate.c` 的 switch-case 中添加:

```c
case BTA64_ICLASS_NEW:
    // 复杂处理逻辑
    break;
```

## 操作类型 (inst_table.h)

```c
typedef enum {
    OP_NONE,    // 无操作
    OP_ADD,     // 加法
    OP_SUB,     // 减法
    OP_AND,     // 与
    OP_OR,      // 或
    OP_XOR,     // 异或
    OP_SHL,     // 左移
    OP_SHR,     // 右移
    OP_MUL,     // 乘法
    OP_DIV,     // 除法
    // ... 更多
} inst_op_t;
```

## 指令标志

```c
#define INST_FLAG_SETS_FLAGS    0x0001  // 设置 RFLAGS
#define INST_FLAG_USES_CF       0x0002  // 使用 Carry Flag
#define INST_FLAG_NO_STORE      0x0004  // 不存储结果
#define INST_FLAG_UNARY         0x0008  // 一元操作
#define INST_FLAG_IMPLICIT_RAX  0x0010  // 隐式使用 RAX
```

## 优势

1. **可维护性**: 添加新指令只需修改配置/表
2. **性能**: 表查找 O(1)，避免大量 switch-case
3. **可扩展性**: 容易添加新的操作类型
4. **分离关注点**: 简单指令用表，复杂指令手动

## 示例：ADD 指令翻译流程

```
x64: ADD RAX, RBX

1. inst_table_lookup(ND_INS_ADD) 
   -> 返回 { ND_INS_ADD, OP_ADD, INST_FLAG_SETS_FLAGS }

2. inst_translate() 识别 OP_ADD:
   - 加载 RAX 到 nat_dst
   - 加载 RBX 到 nat_src  
   - 生成: ADDSx_REG(nat_dst, nat_dst, nat_src)
   - 存储结果到 CPU state
   
3. 生成 ARM64:
   ADD X20, X20, X23   ; xRAX = xRAX + xRBX
   STR X20, [X19, #0]  ; 保存到 cpu->regs[RAX]
```

