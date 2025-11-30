# Host & Play 补丁 - 单进程开服并游玩

## 概述

此补丁实现在 **单个游戏进程中同时运行服务器和客户端**，无需启动多个进程即可开服并与好友游玩。

## 工作原理

### 传统多人游戏架构

```
┌─────────────────┐         TCP          ┌─────────────────┐
│   服务器进程     │ ◄──────────────────► │   客户端进程     │
│  Main.netMode=2 │        网络          │  Main.netMode=1 │
│  Main.myPlayer=255│                    │  Main.myPlayer=0│
└─────────────────┘                      └─────────────────┘
```

### Host & Play 架构

```
┌──────────────────────────────────────────────────────────────┐
│                      单个游戏进程                              │
│  ┌────────────────────┐      MemorySocket      ┌───────────────────┐  │
│  │    服务器线程       │ ◄─────────────────────► │    客户端线程      │  │
│  │  处理游戏逻辑       │      内存队列通信        │  渲染和输入处理    │  │
│  │  管理世界状态       │                        │  玩家控制         │  │
│  └────────────────────┘                        └───────────────────┘  │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │                      主线程 (Game Loop)                        │   │
│  │  • 调用 HostAndPlayManager.UpdateInMainThread()                │   │
│  │  • 处理渲染                                                    │   │
│  │  • 处理输入                                                    │   │
│  └───────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

## 核心组件

### 1. MemorySocket (内存Socket)

实现 `ISocket` 接口，用于服务器和客户端之间的内存通信：

```csharp
// 替代真实 TCP Socket
public class MemorySocket : ISocket
{
    // 接收队列
    private ConcurrentQueue<byte[]> _receiveQueue;
    
    // 发送到对端
    public void AsyncSend(byte[] data, ...) {
        _pair.Send(data, !_isServerSide);
    }
}
```

**优势**:
- 零网络延迟
- 无需端口转发
- 无防火墙问题
- 更低的 CPU 和内存开销

### 2. HostAndPlayManager

管理服务器和客户端的生命周期：

```csharp
public static class HostAndPlayManager
{
    // 启动 Host & Play
    public static void StartHostAndPlay(
        WorldFileData worldData,
        PlayerFileData playerData,
        int maxPlayers = 8,
        bool useMemorySocket = true);
    
    // 停止
    public static void StopHostAndPlay();
    
    // 在主循环中更新
    public static void UpdateInMainThread();
}
```

### 3. 关键游戏状态

| 状态 | 服务器 | 客户端 |
|------|--------|--------|
| `Main.netMode` | 2 | 1 |
| `Main.myPlayer` | 255 | 0-254 |
| `Main.dedServ` | false | false |
| `Netplay.Connection` | - | 有效 |
| `Netplay.Clients[]` | 有效 | - |

## 使用方法

### 方法1: API 调用

```csharp
using HostAndPlayPatch;

// 使用当前选择的世界
HostAndPlayAPI.StartHostAndPlay();

// 指定世界
HostAndPlayAPI.StartHostAndPlay("/path/to/world.wld", maxPlayers: 4);

// 停止
HostAndPlayAPI.StopHostAndPlay();
```

### 方法2: 命令行参数

```bash
./tModLoader -hostandplay
```

### 方法3: 游戏内菜单

通过修改多人游戏菜单添加 "主机并游玩" 选项（需要额外的 UI 补丁）。

## 技术细节

### 网络消息流程

```
1. 客户端发送消息
   └─► NetMessage.SendData(msgType, ...)
       └─► MemorySocket.AsyncSend(data)
           └─► MemorySocketPair.Send(data, toServer=true)
               └─► 服务器MemorySocket._receiveQueue.Enqueue(data)

2. 服务器处理消息
   └─► ServerLoop() -> UpdateConnectedClients()
       └─► RemoteClient.Update()
           └─► MemorySocket.AsyncReceive()
               └─► MessageBuffer.GetData() 处理消息

3. 服务器广播
   └─► NetMessage.SendData(msgType, remoteClient=-1)
       └─► 发送给所有连接的客户端
```

### 线程模型

```
主线程 (UI Thread)
├── Game.Update()
│   └── HostAndPlayManager.UpdateInMainThread()
│       ├── 处理服务器消息 NetMessage.CheckBytes(0-255)
│       └── 处理客户端消息 NetMessage.CheckBytes(256)
└── Game.Draw()

服务器线程 (Background)
└── ServerLoop()
    ├── 监听新连接
    └── UpdateConnectedClients()

客户端线程 (Background)
└── ClientLoop()
    ├── 处理服务器响应
    └── 发送客户端请求
```

## 兼容性

- **tModLoader**: 完全支持（需要 .NET 8.0）
- **原版 Terraria**: 需要额外适配
- **Mods**: 大部分兼容，但某些依赖多进程的 Mod 可能有问题

## 限制

1. **单主机限制**: 主机玩家始终是 Player 0
2. **性能**: 单进程承担所有负载，高玩家数时可能影响帧率
3. **存档**: 需要特殊处理以避免数据竞争

## 调试

启用详细日志：

```csharp
// 设置环境变量
Environment.SetEnvironmentVariable("HOSTANDPLAY_DEBUG", "1");
```

日志输出示例：

```
[HostAndPlay] 启动 Host & Play 模式...
[HostAndPlay] 世界: MyWorld
[HostAndPlay] 玩家: MyPlayer
[HostAndPlay] 服务器端初始化完成
[HostAndPlay] 世界加载完成
[HostAndPlay] 服务器监听已启动
[HostAndPlay] 客户端已连接到服务器
[HostAndPlay] 新连接: Local (Client)
[HostAndPlay] 客户端 0 已连接
```

## 文件结构

```
HostAndPlayPatch/
├── HostAndPlayPatch.csproj  # 项目文件
├── MemorySocket.cs          # 内存Socket实现
├── HostAndPlayManager.cs    # 主管理器
├── StartupHook.cs           # 启动钩子
├── patch.json               # 补丁配置
└── README.md                # 说明文档
```

## 编译

```bash
cd patches/HostAndPlayPatch
dotnet build -c Release
```

输出: `bin/Release/net8.0/HostAndPlayPatch.dll`

## 许可证

MIT License

