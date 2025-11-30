using System;
using System.Collections.Concurrent;
using System.Threading;
using Terraria.Net;
using Terraria.Net.Sockets;

namespace HostAndPlayPatch;

/// <summary>
/// 内存 Socket 实现 - 用于同进程内服务器和客户端之间的通信
/// 避免了真实 TCP 连接的开销
/// </summary>
public class MemorySocket : ISocket
{
    private static int _nextId = 0;
    
    // 服务器端和客户端共享的消息队列
    private static readonly ConcurrentDictionary<int, MemorySocketPair> _socketPairs = new();
    
    private readonly int _id;
    private readonly bool _isServerSide;
    private MemorySocketPair? _pair;
    private MemoryAddress? _remoteAddress;
    private bool _isConnected;
    private bool _isListening;
    private SocketConnectionAccepted? _connectionCallback;
    
    // 接收缓冲区
    private readonly ConcurrentQueue<byte[]> _receiveQueue = new();
    private byte[]? _pendingData;
    private int _pendingOffset;
    
    public MemorySocket(bool isServerSide = false)
    {
        _id = Interlocked.Increment(ref _nextId);
        _isServerSide = isServerSide;
    }
    
    private MemorySocket(MemorySocketPair pair, bool isServerSide)
    {
        _id = Interlocked.Increment(ref _nextId);
        _pair = pair;
        _isServerSide = isServerSide;
        _isConnected = true;
        _remoteAddress = new MemoryAddress(isServerSide ? "Client" : "Server");
    }
    
    public void Close()
    {
        _isConnected = false;
        _isListening = false;
        _pair?.Disconnect(_isServerSide);
    }
    
    public bool IsConnected()
    {
        return _isConnected && (_pair?.IsConnected ?? false);
    }
    
    public void Connect(RemoteAddress address)
    {
        if (address is not MemoryAddress memAddr)
            throw new ArgumentException("MemorySocket only accepts MemoryAddress");
        
        // 查找匹配的服务器 Socket
        var pair = new MemorySocketPair();
        _pair = pair;
        _remoteAddress = memAddr;
        _isConnected = true;
        
        // 通知服务器有新连接
        MemorySocketServer.NotifyConnection(this, pair);
    }
    
    public RemoteAddress GetRemoteAddress()
    {
        return _remoteAddress ?? new MemoryAddress("Unknown");
    }
    
    public bool IsDataAvailable()
    {
        return !_receiveQueue.IsEmpty || _pendingData != null;
    }
    
    public void AsyncReceive(byte[] data, int offset, int size, SocketReceiveCallback callback, object? state)
    {
        // 在后台线程处理接收
        ThreadPool.QueueUserWorkItem(_ =>
        {
            try
            {
                int bytesRead = 0;
                
                // 首先处理上次未完成的数据
                if (_pendingData != null)
                {
                    int available = _pendingData.Length - _pendingOffset;
                    int toCopy = Math.Min(available, size);
                    Buffer.BlockCopy(_pendingData, _pendingOffset, data, offset, toCopy);
                    bytesRead = toCopy;
                    _pendingOffset += toCopy;
                    
                    if (_pendingOffset >= _pendingData.Length)
                    {
                        _pendingData = null;
                        _pendingOffset = 0;
                    }
                }
                else
                {
                    // 等待新数据
                    while (_isConnected && _receiveQueue.IsEmpty)
                    {
                        Thread.Sleep(1);
                    }
                    
                    if (_receiveQueue.TryDequeue(out var packet))
                    {
                        int toCopy = Math.Min(packet.Length, size);
                        Buffer.BlockCopy(packet, 0, data, offset, toCopy);
                        bytesRead = toCopy;
                        
                        if (toCopy < packet.Length)
                        {
                            _pendingData = packet;
                            _pendingOffset = toCopy;
                        }
                    }
                }
                
                callback(state, bytesRead);
            }
            catch (Exception)
            {
                callback(state, 0);
            }
        });
    }
    
    public void AsyncSend(byte[] data, int offset, int size, SocketSendCallback callback, object? state)
    {
        // 复制数据并发送到对端
        byte[] packet = new byte[size];
        Buffer.BlockCopy(data, offset, packet, 0, size);
        
        _pair?.Send(packet, !_isServerSide);
        
        callback(state);
    }
    
    public void SendQueuedPackets()
    {
        // 内存 Socket 不需要队列发送
    }
    
    public bool StartListening(SocketConnectionAccepted callback)
    {
        _connectionCallback = callback;
        _isListening = true;
        MemorySocketServer.RegisterServer(this);
        return true;
    }
    
    public void StopListening()
    {
        _isListening = false;
        MemorySocketServer.UnregisterServer(this);
    }
    
    internal void OnDataReceived(byte[] data)
    {
        _receiveQueue.Enqueue(data);
    }
    
    internal void AcceptConnection(MemorySocket clientSocket, MemorySocketPair pair)
    {
        // 创建服务器端 Socket
        var serverSocket = new MemorySocket(pair, isServerSide: true);
        pair.SetServerSocket(serverSocket);
        
        _connectionCallback?.Invoke(serverSocket);
    }
}

/// <summary>
/// 内存 Socket 对 - 管理服务器端和客户端之间的双向通信
/// </summary>
public class MemorySocketPair
{
    private MemorySocket? _serverSocket;
    private MemorySocket? _clientSocket;
    private bool _isConnected = true;
    
    public bool IsConnected => _isConnected;
    
    public void SetServerSocket(MemorySocket socket)
    {
        _serverSocket = socket;
    }
    
    public void SetClientSocket(MemorySocket socket)
    {
        _clientSocket = socket;
    }
    
    public void Send(byte[] data, bool toServer)
    {
        if (!_isConnected) return;
        
        var target = toServer ? _serverSocket : _clientSocket;
        target?.OnDataReceived(data);
    }
    
    public void Disconnect(bool fromServer)
    {
        _isConnected = false;
    }
}

/// <summary>
/// 内存 Socket 服务器管理器
/// </summary>
public static class MemorySocketServer
{
    private static MemorySocket? _serverSocket;
    private static readonly object _lock = new();
    
    public static void RegisterServer(MemorySocket socket)
    {
        lock (_lock)
        {
            _serverSocket = socket;
        }
    }
    
    public static void UnregisterServer(MemorySocket socket)
    {
        lock (_lock)
        {
            if (_serverSocket == socket)
                _serverSocket = null;
        }
    }
    
    public static void NotifyConnection(MemorySocket clientSocket, MemorySocketPair pair)
    {
        lock (_lock)
        {
            pair.SetClientSocket(clientSocket);
            _serverSocket?.AcceptConnection(clientSocket, pair);
        }
    }
}

/// <summary>
/// 内存地址类型
/// </summary>
public class MemoryAddress : RemoteAddress
{
    private readonly string _name;
    
    public MemoryAddress(string name)
    {
        _name = name;
        Type = AddressType.Tcp; // 模拟 TCP 地址类型
    }
    
    public override string GetIdentifier() => $"Memory:{_name}";
    public override string GetFriendlyName() => $"Local ({_name})";
    public override bool IsLocalHost() => true;
}

