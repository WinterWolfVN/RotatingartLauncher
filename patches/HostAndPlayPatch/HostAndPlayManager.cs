using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Terraria;
using Terraria.IO;
using Terraria.Localization;
using Terraria.Net;
using Terraria.Net.Sockets;

namespace HostAndPlayPatch;

/// <summary>
/// Host & Play 管理器
/// 实现在单个游戏进程中同时运行服务器和客户端
/// </summary>
public static class HostAndPlayManager
{
    private static bool _isHosting = false;
    private static Thread? _serverThread;
    private static Thread? _clientThread;
    
    /// <summary>
    /// 是否正在 Host & Play 模式
    /// </summary>
    public static bool IsHostAndPlay => _isHosting;
    
    /// <summary>
    /// 启动 Host & Play 模式
    /// </summary>
    /// <param name="worldData">世界文件数据</param>
    /// <param name="playerData">玩家文件数据</param>
    /// <param name="maxPlayers">最大玩家数</param>
    /// <param name="port">服务器端口（可选，使用内存Socket时忽略）</param>
    /// <param name="useMemorySocket">是否使用内存Socket（推荐true）</param>
    public static void StartHostAndPlay(
        WorldFileData worldData,
        PlayerFileData playerData,
        int maxPlayers = 8,
        int port = 7777,
        bool useMemorySocket = true)
    {
        if (_isHosting)
        {
            Console.WriteLine("[HostAndPlay] 已经在 Host & Play 模式中");
            return;
        }
        
        _isHosting = true;
        
        Console.WriteLine("[HostAndPlay] 启动 Host & Play 模式...");
        Console.WriteLine($"[HostAndPlay] 世界: {worldData.Name}");
        Console.WriteLine($"[HostAndPlay] 玩家: {playerData.Name}");
        Console.WriteLine($"[HostAndPlay] 最大玩家: {maxPlayers}");
        Console.WriteLine($"[HostAndPlay] 使用内存Socket: {useMemorySocket}");
        
        // 步骤1：初始化服务器
        InitializeServerSide(worldData, maxPlayers, port, useMemorySocket);
        
        // 步骤2：加载世界
        LoadWorldForServer(worldData);
        
        // 步骤3：启动服务器监听
        StartServerListening(useMemorySocket);
        
        // 步骤4：作为客户端连接
        ConnectAsClient(playerData, port, useMemorySocket);
    }
    
    /// <summary>
    /// 停止 Host & Play 模式
    /// </summary>
    public static void StopHostAndPlay()
    {
        if (!_isHosting) return;
        
        Console.WriteLine("[HostAndPlay] 停止 Host & Play 模式...");
        
        // 断开客户端
        Netplay.Disconnect = true;
        
        // 等待线程结束
        _clientThread?.Join(5000);
        _serverThread?.Join(5000);
        
        _isHosting = false;
        Main.netMode = 0;
        
        Console.WriteLine("[HostAndPlay] Host & Play 模式已停止");
    }
    
    private static void InitializeServerSide(WorldFileData worldData, int maxPlayers, int port, bool useMemorySocket)
    {
        // 设置服务器参数
        Main.maxNetPlayers = maxPlayers;
        Netplay.ListenPort = port;
        Netplay.ServerPassword = "";
        
        // 初始化客户端数组
        for (int i = 0; i < 256; i++)
        {
            Netplay.Clients[i] = new RemoteClient();
            Netplay.Clients[i].Reset();
            Netplay.Clients[i].Id = i;
            Netplay.Clients[i].ReadBuffer = new byte[1024];
        }
        
        // 初始化消息缓冲区
        for (int i = 0; i < 257; i++)
        {
            NetMessage.buffer[i] = new MessageBuffer();
            NetMessage.buffer[i].whoAmI = i;
        }
        
        Console.WriteLine("[HostAndPlay] 服务器端初始化完成");
    }
    
    private static void LoadWorldForServer(WorldFileData worldData)
    {
        Console.WriteLine($"[HostAndPlay] 加载世界: {worldData.Path}");
        
        // 设置活动世界
        Main.ActiveWorldFileData = worldData;
        
        // 使用同步方式加载世界
        bool loadSuccess = false;
        try
        {
            // 调用世界加载
            WorldFile.LoadWorld(worldData.IsCloudSave);
            loadSuccess = true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[HostAndPlay] 世界加载失败: {ex.Message}");
        }
        
        if (loadSuccess)
        {
            Console.WriteLine("[HostAndPlay] 世界加载完成");
        }
    }
    
    private static void StartServerListening(bool useMemorySocket)
    {
        // 设置为服务器模式
        Main.netMode = 2;
        Main.myPlayer = 255;
        Netplay.Disconnect = false;
        
        if (useMemorySocket)
        {
            // 使用内存 Socket
            Netplay.TcpListener = new MemorySocket(isServerSide: true);
        }
        else
        {
            // 使用真实 TCP Socket
            Netplay.TcpListener = new TcpSocket();
        }
        
        // 启动服务器线程
        _serverThread = new Thread(ServerLoop)
        {
            IsBackground = true,
            Name = "HostAndPlay Server Thread"
        };
        _serverThread.Start();
        
        Console.WriteLine("[HostAndPlay] 服务器监听已启动");
    }
    
    private static void ServerLoop()
    {
        try
        {
            // 开始监听
            Netplay.TcpListener.StartListening(OnConnectionAccepted);
            
            Console.WriteLine("[HostAndPlay] 服务器正在运行...");
            
            // 服务器主循环
            while (!Netplay.Disconnect && _isHosting)
            {
                // 更新已连接的客户端
                UpdateConnectedClients();
                Thread.Sleep(1);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[HostAndPlay] 服务器错误: {ex.Message}");
        }
        finally
        {
            Netplay.TcpListener?.StopListening();
        }
    }
    
    private static void OnConnectionAccepted(ISocket client)
    {
        Console.WriteLine($"[HostAndPlay] 新连接: {client.GetRemoteAddress().GetFriendlyName()}");
        
        // 找到空闲的客户端槽位
        for (int i = 0; i < Main.maxNetPlayers; i++)
        {
            if (!Netplay.Clients[i].IsConnected())
            {
                Netplay.Clients[i].Reset();
                Netplay.Clients[i].Socket = client;
                Console.WriteLine($"[HostAndPlay] 客户端 {i} 已连接");
                return;
            }
        }
        
        Console.WriteLine("[HostAndPlay] 服务器已满");
    }
    
    private static void UpdateConnectedClients()
    {
        for (int i = 0; i < 256; i++)
        {
            if (Netplay.Clients[i].PendingTermination)
            {
                if (Netplay.Clients[i].PendingTerminationApproved)
                {
                    Netplay.Clients[i].State = 0;
                    NetMessage.SyncDisconnectedPlayer(i);
                    Netplay.Clients[i].Reset();
                }
                continue;
            }
            
            if (Netplay.Clients[i].IsConnected())
            {
                Netplay.Clients[i].Update();
            }
            else if (Netplay.Clients[i].IsActive)
            {
                // SetPendingTermination 是 internal 方法，需要使用反射或直接重置
                // 直接重置客户端
                Console.WriteLine($"[HostAndPlay] 客户端 {i} 连接丢失，正在重置...");
                Netplay.Clients[i].State = 0;
                NetMessage.SyncDisconnectedPlayer(i);
                Netplay.Clients[i].Reset();
            }
        }
    }
    
    private static void ConnectAsClient(PlayerFileData playerData, int port, bool useMemorySocket)
    {
        Console.WriteLine("[HostAndPlay] 作为客户端连接...");
        
        // 设置玩家数据
        Main.ActivePlayerFileData = playerData;
        Player.Hooks.EnterWorld(Main.myPlayer);
        
        // 启动客户端连接线程
        _clientThread = new Thread(() => ClientLoop(port, useMemorySocket))
        {
            IsBackground = true,
            Name = "HostAndPlay Client Thread"
        };
        _clientThread.Start();
    }
    
    private static void ClientLoop(int port, bool useMemorySocket)
    {
        try
        {
            // 短暂延迟，确保服务器已启动
            Thread.Sleep(100);
            
            // 重置连接状态
            Netplay.Connection = new RemoteServer();
            Netplay.Connection.ReadBuffer = new byte[ushort.MaxValue];
            
            // 创建 Socket
            if (useMemorySocket)
            {
                Netplay.Connection.Socket = new MemorySocket();
            }
            else
            {
                Netplay.Connection.Socket = new TcpSocket();
                Netplay.ServerIP = System.Net.IPAddress.Loopback;
                Netplay.ServerIPText = "127.0.0.1";
            }
            
            // 设置为客户端模式
            Main.netMode = 1;
            Main.menuMode = 14;
            Netplay.Disconnect = false;
            
            // 连接
            if (useMemorySocket)
            {
                Netplay.Connection.Socket.Connect(new MemoryAddress("Server"));
            }
            else
            {
                Netplay.Connection.Socket.Connect(new TcpAddress(System.Net.IPAddress.Loopback, port));
            }
            
            Console.WriteLine("[HostAndPlay] 客户端已连接到服务器");
            
            // 运行客户端循环
            RunClientLoop();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[HostAndPlay] 客户端错误: {ex.Message}");
        }
    }
    
    private static void RunClientLoop()
    {
        NetMessage.buffer[256].Reset();
        
        while (!Netplay.Disconnect && _isHosting)
        {
            if (Netplay.Connection.Socket.IsConnected())
            {
                Netplay.Connection.IsActive = true;
                
                // 状态机处理
                if (Netplay.Connection.State == 0)
                {
                    Main.statusText = Language.GetTextValue("Net.FoundServer");
                    Netplay.Connection.State = 1;
                    NetMessage.SendData(1); // 发送连接请求
                }
                
                // 处理接收数据
                if (!Netplay.Connection.IsReading && 
                    Netplay.Connection.Socket.IsDataAvailable())
                {
                    Netplay.Connection.IsReading = true;
                    Netplay.Connection.Socket.AsyncReceive(
                        Netplay.Connection.ReadBuffer,
                        0,
                        Netplay.Connection.ReadBuffer.Length,
                        Netplay.Connection.ClientReadCallBack);
                }
                
                // 处理服务器端消息
                if (NetMessage.buffer[256].checkBytes)
                {
                    NetMessage.CheckBytes();
                }
            }
            else if (Netplay.Connection.IsActive)
            {
                Main.statusText = Language.GetTextValue("Net.LostConnection");
                Netplay.Disconnect = true;
            }
            
            Thread.Sleep(1);
        }
    }
    
    /// <summary>
    /// 在游戏主循环中更新网络
    /// 需要在 Main.Update 中调用
    /// </summary>
    public static void UpdateInMainThread()
    {
        if (!_isHosting) return;
        
        // 处理服务器端消息
        for (int i = 0; i < 256; i++)
        {
            if (NetMessage.buffer[i].checkBytes)
            {
                NetMessage.CheckBytes(i);
            }
        }
        
        // 处理客户端消息
        if (NetMessage.buffer[256].checkBytes)
        {
            NetMessage.CheckBytes();
        }
    }
}

