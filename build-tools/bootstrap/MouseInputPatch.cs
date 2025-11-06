using System;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using HarmonyLib;

namespace AssemblyMain
{
    /// <summary>
    /// 修复 MonoGame 在 Android 上的触屏/鼠标输入偏移问题
    /// 
    /// 问题根源：MonoGame 使用 SDL_GetGlobalMouseState（屏幕绝对坐标）
    /// 然后减去 ClientBounds 偏移，但在 Android 横屏模式下这个偏移不准确。
    /// 
    /// 解决方案：在 Android 上改用 SDL_GetMouseState（窗口相对坐标），
    /// 直接获取准确的窗口内坐标，无需手动计算偏移。
    /// </summary>
    public static class MouseInputPatch
    {
        private static bool _isAndroid = false;
        private static bool _patchApplied = false;
        
        // MonoGame 类型（通过反射获取，避免编译时依赖）
        private static Type _mouseType;
        private static Type _gameWindowType;
        private static Type _mouseStateType;
        private static FieldInfo _scrollXField;
        private static FieldInfo _scrollYField;
        private static FieldInfo _mouseStateField;
        
        // 调试计数器
        private static int _debugFrameCount = 0;
        
        // 缩放比例（借鉴 FNA 逻辑）
        private static float _scaleX = 1.0f;
        private static float _scaleY = 1.0f;
        private static bool _scaleInitialized = false;
        private static int _initRetryCount = 0;

        [DllImport("libSDL2.so", CallingConvention = CallingConvention.Cdecl)]
        private static extern uint SDL_GetMouseState(out int x, out int y);

        /// <summary>
        /// 应用补丁
        /// </summary>
        public static void Apply()
        {
            try
            {
                if (_patchApplied)
                {
                    Console.WriteLine("[MouseInputPatch] Patch already applied, skipping");
                    return;
                }

                // 检测是否在 Android 上运行
                _isAndroid = IsAndroid();
                if (!_isAndroid)
                {
                    Console.WriteLine("[MouseInputPatch] Not on Android, skipping patch");
                    return;
                }

                Console.WriteLine("[MouseInputPatch] Applying Mouse.PlatformGetState patch for Android...");

                // 通过反射查找 MonoGame 类型
                var monoGameAssembly = AppDomain.CurrentDomain.GetAssemblies()
                    .FirstOrDefault(a => a.GetName().Name == "MonoGame.Framework");

                if (monoGameAssembly == null)
                {
                    Console.WriteLine("[MouseInputPatch] ⚠️ MonoGame.Framework not found");
                    return;
                }

                _mouseType = monoGameAssembly.GetType("Microsoft.Xna.Framework.Input.Mouse");
                _gameWindowType = monoGameAssembly.GetType("Microsoft.Xna.Framework.GameWindow");
                _mouseStateType = monoGameAssembly.GetType("Microsoft.Xna.Framework.Input.MouseState");

                if (_mouseType == null || _gameWindowType == null || _mouseStateType == null)
                {
                    Console.WriteLine("[MouseInputPatch] ⚠️ Could not find required MonoGame types");
                    return;
                }

                // 获取 Mouse.ScrollX 和 Mouse.ScrollY 字段
                _scrollXField = _mouseType.GetField("ScrollX", BindingFlags.NonPublic | BindingFlags.Static);
                _scrollYField = _mouseType.GetField("ScrollY", BindingFlags.NonPublic | BindingFlags.Static);
                
                // 获取 GameWindow.MouseState 字段
                _mouseStateField = _gameWindowType.GetField("MouseState", BindingFlags.NonPublic | BindingFlags.Instance);
                
                if (_mouseStateField == null)
                {
                    Console.WriteLine("[MouseInputPatch] ⚠️ Could not find GameWindow.MouseState field");
                    return;
                }

                var harmony = new Harmony("com.ralaunch.mouseinputpatch");

                // 补丁 Mouse.PlatformGetState 方法
                var platformGetStateMethod = _mouseType.GetMethod("PlatformGetState",
                    BindingFlags.NonPublic | BindingFlags.Static);

                if (platformGetStateMethod == null)
                {
                    Console.WriteLine("[MouseInputPatch] ⚠️ Could not find Mouse.PlatformGetState method");
                    return;
                }

                // 使用 Prefix 而不是 Postfix，完全替换原始逻辑
                var prefixMethod = typeof(MouseInputPatch).GetMethod(nameof(PlatformGetState_Prefix),
                    BindingFlags.Public | BindingFlags.Static);

                harmony.Patch(platformGetStateMethod, prefix: new HarmonyMethod(prefixMethod));

                _patchApplied = true;
                Console.WriteLine("[MouseInputPatch] ✓ Mouse input patch applied successfully");
                Console.WriteLine("[MouseInputPatch]   → Will use SDL_GetMouseState (window-relative coordinates)");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[MouseInputPatch] Error applying patch: {ex.Message}");
                Console.WriteLine($"[MouseInputPatch] Stack trace: {ex.StackTrace}");
            }
        }

        /// <summary>
        /// Prefix: 在 Android 上使用窗口相对坐标，避免偏移计算错误
        /// 返回 false 表示跳过原始方法
        /// </summary>
        public static bool PlatformGetState_Prefix(object window, ref object __result)
        {
            try
            {
                if (!_isAndroid || window == null)
                    return true; // 非 Android，执行原始方法

                // 🔧 关键修复：使用 SDL_GetMouseState 而不是 SDL_GetGlobalMouseState
                // SDL_GetMouseState 返回窗口相对坐标，无需手动减去窗口偏移
                int x, y;
                uint buttonState = SDL_GetMouseState(out x, out y);

                // 获取 window.Handle (属性) 和 window.MouseState (字段)
                var handleProp = _gameWindowType.GetProperty("Handle");
                
                if (handleProp == null || _mouseStateField == null)
                {
                    Console.WriteLine($"[MouseInputPatch] Could not find GameWindow members: Handle={handleProp != null}, MouseState={_mouseStateField != null}");
                    return true;
                }

                IntPtr windowHandle = (IntPtr)handleProp.GetValue(window);
                object mouseStateObj = _mouseStateField.GetValue(window);

                // 获取窗口标志以检查是否有输入焦点
                int windowFlags = 0;
                try
                {
                    // 查找 Sdl.Window 类型
                    var sdlAssembly = _mouseType.Assembly;
                    var sdlWindowType = sdlAssembly.GetType("Sdl+Window");
                    if (sdlWindowType != null)
                    {
                        var getFlagsMethod = sdlWindowType.GetMethod("GetWindowFlags",
                            BindingFlags.Public | BindingFlags.Static);
                        if (getFlagsMethod != null)
                        {
                            windowFlags = (int)getFlagsMethod.Invoke(null, new object[] { windowHandle });
                        }
                    }
                }
                catch { }

                // 更新鼠标状态
                bool hasInputFocus = (windowFlags & 0x400) != 0; // SDL_WINDOW_INPUT_FOCUS = 0x400
                
                // ButtonState 枚举：0 = Released, 1 = Pressed
                int pressed = 1;
                int released = 0;
                
                if (hasInputFocus)
                {
                    // 通过反射设置按钮状态
                    _mouseStateType.GetProperty("LeftButton").SetValue(mouseStateObj, ((buttonState & 0x1) != 0) ? pressed : released);
                    _mouseStateType.GetProperty("MiddleButton").SetValue(mouseStateObj, ((buttonState & 0x2) != 0) ? pressed : released);
                    _mouseStateType.GetProperty("RightButton").SetValue(mouseStateObj, ((buttonState & 0x4) != 0) ? pressed : released);
                    _mouseStateType.GetProperty("XButton1").SetValue(mouseStateObj, ((buttonState & 0x8) != 0) ? pressed : released);
                    _mouseStateType.GetProperty("XButton2").SetValue(mouseStateObj, ((buttonState & 0x10) != 0) ? pressed : released);
                    
                    // 滚轮值保持不变（由 SDL 事件更新）
                    if (_scrollXField != null && _scrollYField != null)
                    {
                        int scrollX = (int)_scrollXField.GetValue(null);
                        int scrollY = (int)_scrollYField.GetValue(null);
                        _mouseStateType.GetProperty("HorizontalScrollWheelValue").SetValue(mouseStateObj, scrollX);
                        _mouseStateType.GetProperty("ScrollWheelValue").SetValue(mouseStateObj, scrollY);
                    }
                }

                // 🎯 关键：借鉴 FNA 的坐标缩放逻辑，在 MonoGame 中实现
                // FNA 公式: gameX = (int)((double)x * backBufferWidth / windowWidth)
                int gameX = x;
                int gameY = y;
                
                // 尝试获取缩放参数（重试最多5次，每次间隔100帧）
                if (!_scaleInitialized && _debugFrameCount > 100 && _debugFrameCount % 100 == 0 && _initRetryCount < 5)
                {
                    _initRetryCount++;
                    try
                    {
                        Console.WriteLine($"[MouseInputPatch] 🔍 Attempting to initialize coordinate scaling (attempt {_initRetryCount}/5)...");
                        Console.WriteLine($"[MouseInputPatch]   window param: {(window == null ? "NULL" : "NOT NULL")}");
                        
                        // 如果window是null，尝试从Mouse.PrimaryWindow获取
                        object targetWindow = window;
                        if (targetWindow == null)
                        {
                            Console.WriteLine($"[MouseInputPatch]   window is null, trying Mouse.PrimaryWindow...");
                            var primaryWindowField = _mouseType?.GetField("PrimaryWindow", BindingFlags.NonPublic | BindingFlags.Static);
                            Console.WriteLine($"[MouseInputPatch]   PrimaryWindow field: {(primaryWindowField == null ? "NULL" : "FOUND")}");
                            
                            if (primaryWindowField != null)
                            {
                                targetWindow = primaryWindowField.GetValue(null);
                                Console.WriteLine($"[MouseInputPatch]   PrimaryWindow value: {(targetWindow == null ? "NULL" : "NOT NULL")}");
                            }
                        }
                        
                        if (targetWindow == null)
                        {
                            Console.WriteLine($"[MouseInputPatch] ⚠️ Both window and PrimaryWindow are null, will retry...");
                            throw new Exception("Window is null"); // Force to catch block
                        }
                        
                        Console.WriteLine($"[MouseInputPatch]   ✓ Got window object, proceeding...");
                        
                        // 从 MonoGame GameWindow 获取 ClientBounds（窗口大小）
                        var clientBoundsProp = _gameWindowType.GetProperty("ClientBounds");
                        if (clientBoundsProp != null)
                        {
                            object clientBounds = clientBoundsProp.GetValue(targetWindow);
                            var rectangleType = clientBounds.GetType();
                            int windowWidth = (int)rectangleType.GetProperty("Width").GetValue(clientBounds);
                            int windowHeight = (int)rectangleType.GetProperty("Height").GetValue(clientBounds);
                            
                            Console.WriteLine($"[MouseInputPatch]   ClientBounds: {windowWidth}x{windowHeight}");
                            
                            // 从 GraphicsDevice.PresentationParameters 获取 BackBuffer 大小
                            var graphicsDeviceProp = _gameWindowType.GetProperty("GraphicsDevice", BindingFlags.Public | BindingFlags.Instance);
                            if (graphicsDeviceProp != null)
                            {
                                object graphicsDevice = graphicsDeviceProp.GetValue(targetWindow);
                                if (graphicsDevice != null)
                                {
                                    var presentationParams = graphicsDevice.GetType().GetProperty("PresentationParameters")?.GetValue(graphicsDevice);
                                    if (presentationParams != null)
                                    {
                                        int backBufferWidth = (int)presentationParams.GetType().GetProperty("BackBufferWidth").GetValue(presentationParams);
                                        int backBufferHeight = (int)presentationParams.GetType().GetProperty("BackBufferHeight").GetValue(presentationParams);
                                        
                                        Console.WriteLine($"[MouseInputPatch]   BackBuffer: {backBufferWidth}x{backBufferHeight}");
                                        
                                        // 保存缩放比例到静态变量
                                        if (windowWidth > 0 && windowHeight > 0 && backBufferWidth > 0 && backBufferHeight > 0)
                                        {
                                            _scaleX = (float)backBufferWidth / windowWidth;
                                            _scaleY = (float)backBufferHeight / windowHeight;
                                            _scaleInitialized = true; // 只有成功才标记为已初始化
                                            
                                            Console.WriteLine($"[MouseInputPatch] 🎮 Coordinate Scaling Initialized Successfully:");
                                            Console.WriteLine($"[MouseInputPatch]   Scale: {_scaleX:F3}x, {_scaleY:F3}y");
                                        }
                                        else
                                        {
                                            Console.WriteLine($"[MouseInputPatch] ⚠️ Invalid dimensions, will retry...");
                                        }
                                    }
                                    else
                                    {
                                        Console.WriteLine($"[MouseInputPatch] ⚠️ PresentationParameters is null, will retry...");
                                    }
                                }
                                else
                                {
                                    Console.WriteLine($"[MouseInputPatch] ⚠️ GraphicsDevice is null, will retry...");
                                }
                            }
                            else
                            {
                                Console.WriteLine($"[MouseInputPatch] ⚠️ GraphicsDevice property not found");
                                _scaleInitialized = true; // 不再重试（游戏不支持）
                            }
                        }
                        else
                        {
                            Console.WriteLine($"[MouseInputPatch] ⚠️ ClientBounds property not found");
                            _scaleInitialized = true; // 不再重试（游戏不支持）
                        }
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[MouseInputPatch] ⚠️ Error initializing scaling: {ex.GetType().Name}: {ex.Message}");
                        Console.WriteLine($"[MouseInputPatch]    Stack: {ex.StackTrace}");
                        // 不标记为已初始化，允许重试
                    }
                    
                    // 如果达到最大重试次数，使用1:1缩放
                    if (!_scaleInitialized && _initRetryCount >= 5)
                    {
                        Console.WriteLine($"[MouseInputPatch] ⚠️ Failed to initialize after 5 attempts, using 1:1 scale");
                        _scaleInitialized = true;
                    }
                }
                
                // 应用 FNA 的缩放公式
                gameX = (int)((double)x * _scaleX);
                gameY = (int)((double)y * _scaleY);
                
                // 🔍 帧计数
                _debugFrameCount++;
                
                _mouseStateType.GetProperty("X").SetValue(mouseStateObj, gameX);
                _mouseStateType.GetProperty("Y").SetValue(mouseStateObj, gameY);

                // 🔍 调试日志（每60帧输出一次，避免刷屏）
                if (_debugFrameCount % 60 == 0 && (buttonState & 0x1) != 0) // 左键按下时每秒输出一次
                {
                    Console.WriteLine($"[MouseInputPatch] 🎯 SDL({x}, {y}) -> Game({gameX}, {gameY})");
                }

                // 将更新后的 MouseState 写回到 GameWindow（因为 MouseState 是值类型 struct）
                _mouseStateField.SetValue(window, mouseStateObj);

                __result = mouseStateObj;
                return false; // 跳过原始方法
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[MouseInputPatch] Error in prefix: {ex.Message}");
                return true; // 发生错误时，回退到原始方法
            }
        }

        /// <summary>
        /// 判断当前是否在 Android 平台上运行
        /// </summary>
        private static bool IsAndroid()
        {
            // 检查 Android 特有的环境变量
            if (!string.IsNullOrEmpty(Environment.GetEnvironmentVariable("ANDROID_ROOT")) ||
                !string.IsNullOrEmpty(Environment.GetEnvironmentVariable("ANDROID_DATA")))
            {
                return true;
            }

            return false;
        }
    }
}

