using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Microsoft.Xna.Framework.Input;

namespace TMLConsolePatch
{
    /// <summary>
    /// 游戏内控制台UI
    /// </summary>
    public static class ConsoleUI
    {
        private static bool _isVisible = true;
        private static string _currentInput = "";
        private static int _scrollOffset = 0;
        private static Keys _lastPressedKey = Keys.None;
        private static int _keyRepeatTimer = 0;

        private static readonly List<string> _commandHistory = new();
        private static int _historyIndex = -1;

        // UI配置
        private const int MaxVisibleLines = 25;
        private const int LineHeight = 16;
        private const int Padding = 10;
        private const float BackgroundAlpha = 0.85f;

        // 反射缓存
        private static SpriteBatch? _spriteBatch;
        private static SpriteFont? _font;
        private static PropertyInfo? _spriteBatchProperty;
        private static FieldInfo? _fontMouseTextField;
        private static MethodInfo? _drawStringMethod;
        private static Texture2D? _whitePixel;

        public static void Update(object mainInstance, GameTime gameTime)
        {
            var keyboardState = Keyboard.GetState();

            // 控制台始终显示，不需要切换
            // 按 ` 或 F1 切换控制台显示
            //if (IsKeyJustPressed(keyboardState, Keys.OemTilde) || IsKeyJustPressed(keyboardState, Keys.F1))
            //{
            //    _isVisible = !_isVisible;
            //    return;
            //}

            //if (!_isVisible)
            //    return;

            // 处理输入
            HandleInput(keyboardState);
        }

        private static void HandleInput(KeyboardState keyboardState)
        {
            var pressedKeys = keyboardState.GetPressedKeys();

            foreach (var key in pressedKeys)
            {
                // 只处理新按下的键
                if (_lastPressedKey != key || _keyRepeatTimer > 15)
                {
                    ProcessKey(key, keyboardState);
                    _lastPressedKey = key;
                    _keyRepeatTimer = 0;
                }
            }

            if (pressedKeys.Length == 0)
            {
                _lastPressedKey = Keys.None;
                _keyRepeatTimer = 0;
            }
            else
            {
                _keyRepeatTimer++;
            }
        }

        private static void ProcessKey(Keys key, KeyboardState keyboardState)
        {
            bool shift = keyboardState.IsKeyDown(Keys.LeftShift) || keyboardState.IsKeyDown(Keys.RightShift);
            bool ctrl = keyboardState.IsKeyDown(Keys.LeftControl) || keyboardState.IsKeyDown(Keys.RightControl);

            // Enter - 提交输入
            if (key == Keys.Enter)
            {
                if (!string.IsNullOrWhiteSpace(_currentInput))
                {
                    ProcessCommand(_currentInput);
                    _commandHistory.Add(_currentInput);
                    _historyIndex = _commandHistory.Count;
                    _currentInput = "";
                }
                return;
            }

            // Backspace - 删除字符
            if (key == Keys.Back)
            {
                if (_currentInput.Length > 0)
                    _currentInput = _currentInput.Substring(0, _currentInput.Length - 1);
                return;
            }

            // 上下箭头 - 历史记录
            if (key == Keys.Up)
            {
                if (_historyIndex > 0)
                {
                    _historyIndex--;
                    _currentInput = _commandHistory[_historyIndex];
                }
                return;
            }

            if (key == Keys.Down)
            {
                if (_historyIndex < _commandHistory.Count - 1)
                {
                    _historyIndex++;
                    _currentInput = _commandHistory[_historyIndex];
                }
                else
                {
                    _historyIndex = _commandHistory.Count;
                    _currentInput = "";
                }
                return;
            }

            // PageUp/PageDown - 滚动
            if (key == Keys.PageUp)
            {
                _scrollOffset = Math.Min(_scrollOffset + 5, MaxVisibleLines);
                return;
            }

            if (key == Keys.PageDown)
            {
                _scrollOffset = Math.Max(_scrollOffset - 5, 0);
                return;
            }

            // 字母和数字输入
            char? inputChar = GetCharFromKey(key, shift);
            if (inputChar.HasValue)
            {
                _currentInput += inputChar.Value;
            }
        }

        private static void ProcessCommand(string command)
        {
            ConsoleManager.AddOutput($"> {command}");

            // 内置命令
            if (command.Equals("clear", StringComparison.OrdinalIgnoreCase))
            {
                ConsoleManager.ClearOutput();
                return;
            }

            if (command.Equals("help", StringComparison.OrdinalIgnoreCase))
            {
                ConsoleManager.AddOutput("可用命令:");
                ConsoleManager.AddOutput("  clear - 清空控制台");
                ConsoleManager.AddOutput("  help - 显示帮助");
                ConsoleManager.AddOutput("  server start <世界索引> - 启动服务器");
                ConsoleManager.AddOutput("  server stop - 停止服务器");
                return;
            }

            // 服务器命令
            if (command.StartsWith("server ", StringComparison.OrdinalIgnoreCase))
            {
                ServerCommands.ProcessServerCommand(command);
                return;
            }

            // 提交到输入队列 (用于其他需要Console.ReadLine的地方)
            ConsoleManager.SubmitInput(command);
        }

        public static void Draw(object mainInstance, GameTime gameTime)
        {
            if (!_isVisible)
                return;

            InitializeGraphics(mainInstance);

            if (_spriteBatch == null || _font == null)
                return;

            try
            {
                _spriteBatch.Begin();

                var screenWidth = GetScreenWidth(mainInstance);
                var screenHeight = GetScreenHeight(mainInstance);

                // 绘制背景
                DrawBackground(_spriteBatch, screenWidth, screenHeight);

                // 绘制输出
                DrawOutput(_spriteBatch, screenWidth, screenHeight);

                // 绘制输入框
                DrawInputBox(_spriteBatch, screenWidth, screenHeight);

                _spriteBatch.End();
            }
            catch (Exception ex)
            {
                // 静默处理错误
            }
        }

        private static void DrawBackground(SpriteBatch spriteBatch, int screenWidth, int screenHeight)
        {
            int consoleHeight = (MaxVisibleLines + 2) * LineHeight + Padding * 3;
            var bgColor = new Color(0, 0, 0, (byte)(255 * BackgroundAlpha));

            if (_whitePixel == null)
            {
                _whitePixel = new Texture2D(spriteBatch.GraphicsDevice, 1, 1);
                _whitePixel.SetData(new[] { Color.White });
            }

            spriteBatch.Draw(_whitePixel, new Rectangle(0, 0, screenWidth, consoleHeight), bgColor);
        }

        private static void DrawOutput(SpriteBatch spriteBatch, int screenWidth, int screenHeight)
        {
            var lines = ConsoleManager.GetRecentOutputLines(MaxVisibleLines + _scrollOffset);
            int startY = Padding;

            // 跳过滚动偏移的行
            int skipLines = Math.Min(_scrollOffset, lines.Count);
            int displayLines = Math.Min(MaxVisibleLines, lines.Count - skipLines);

            for (int i = 0; i < displayLines; i++)
            {
                int lineIndex = lines.Count - displayLines - skipLines + i;
                if (lineIndex >= 0 && lineIndex < lines.Count)
                {
                    string line = lines[lineIndex];
                    if (line.Length > 100)
                        line = line.Substring(0, 100) + "...";

                    spriteBatch.DrawString(_font!, line, new Vector2(Padding, startY + i * LineHeight), Color.White);
                }
            }
        }

        private static void DrawInputBox(SpriteBatch spriteBatch, int screenWidth, int screenHeight)
        {
            int consoleHeight = (MaxVisibleLines + 2) * LineHeight + Padding * 3;
            int inputY = consoleHeight - LineHeight - Padding;

            // 输入提示符
            string prompt = "> " + _currentInput;
            if (DateTime.Now.Millisecond < 500)
                prompt += "_";

            spriteBatch.DrawString(_font!, prompt, new Vector2(Padding, inputY), Color.Lime);
        }

        private static void InitializeGraphics(object mainInstance)
        {
            if (_spriteBatch != null && _font != null)
                return;

            try
            {
                // 获取 SpriteBatch
                if (_spriteBatchProperty == null)
                {
                    _spriteBatchProperty = mainInstance.GetType().GetProperty("spriteBatch",
                        BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance);
                }

                if (_spriteBatchProperty != null)
                {
                    _spriteBatch = _spriteBatchProperty.GetValue(mainInstance) as SpriteBatch;
                }

                // 获取字体
                if (_fontMouseTextField == null)
                {
                    _fontMouseTextField = mainInstance.GetType().GetField("fontMouseText",
                        BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static);
                }

                if (_fontMouseTextField != null)
                {
                    _font = _fontMouseTextField.GetValue(null) as SpriteFont;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ConsoleUI] Error initializing graphics: {ex.Message}");
            }
        }

        private static int GetScreenWidth(object mainInstance)
        {
            try
            {
                var screenWidthField = mainInstance.GetType().GetField("screenWidth",
                    BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static);
                return screenWidthField != null ? (int)screenWidthField.GetValue(null) : 1920;
            }
            catch
            {
                return 1920;
            }
        }

        private static int GetScreenHeight(object mainInstance)
        {
            try
            {
                var screenHeightField = mainInstance.GetType().GetField("screenHeight",
                    BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static);
                return screenHeightField != null ? (int)screenHeightField.GetValue(null) : 1080;
            }
            catch
            {
                return 1080;
            }
        }

        private static bool IsKeyJustPressed(KeyboardState current, Keys key)
        {
            return current.IsKeyDown(key) && _lastPressedKey != key;
        }

        private static char? GetCharFromKey(Keys key, bool shift)
        {
            // 字母
            if (key >= Keys.A && key <= Keys.Z)
            {
                char c = (char)('a' + (key - Keys.A));
                return shift ? char.ToUpper(c) : c;
            }

            // 数字键
            if (key >= Keys.D0 && key <= Keys.D9)
            {
                if (shift)
                {
                    return ")!@#$%^&*("[key - Keys.D0];
                }
                return (char)('0' + (key - Keys.D0));
            }

            // 小键盘数字
            if (key >= Keys.NumPad0 && key <= Keys.NumPad9)
            {
                return (char)('0' + (key - Keys.NumPad0));
            }

            // 特殊字符
            switch (key)
            {
                case Keys.Space: return ' ';
                case Keys.OemPeriod: return shift ? '>' : '.';
                case Keys.OemComma: return shift ? '<' : ',';
                case Keys.OemMinus: return shift ? '_' : '-';
                case Keys.OemPlus: return shift ? '+' : '=';
                case Keys.OemQuestion: return shift ? '?' : '/';
                case Keys.OemSemicolon: return shift ? ':' : ';';
                case Keys.OemQuotes: return shift ? '"' : '\'';
                case Keys.OemOpenBrackets: return shift ? '{' : '[';
                case Keys.OemCloseBrackets: return shift ? '}' : ']';
                case Keys.OemPipe: return shift ? '|' : '\\';
                default: return null;
            }
        }
    }
}
