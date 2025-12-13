package com.app.ralaunch.activity;

import android.util.Log;

/**
 * SDL IME/文本输入相关的工具，集中管理反射调用。
 */
public class GameImeHelper {

    private static final String TAG = "GameImeHelper";

    public static void sendTextToGame(String text) {
        try {
            Class<?> sdlInputConnectionClass = Class.forName("org.libsdl.app.SDLInputConnection");
            java.lang.reflect.Method nativeCommitText = sdlInputConnectionClass.getDeclaredMethod(
                    "nativeCommitText", String.class, int.class);
            nativeCommitText.setAccessible(true);
            nativeCommitText.invoke(null, text, 1);
        } catch (Exception e) {
            Log.e(TAG, "发送文本失败", e);
        }
    }

    public static void sendBackspaceToGame() {
        try {
            Class<?> sdlInputConnectionClass = Class.forName("org.libsdl.app.SDLInputConnection");
            java.lang.reflect.Method onNativeKeyDown = sdlInputConnectionClass.getDeclaredMethod(
                    "nativeKeyDown", int.class);
            java.lang.reflect.Method onNativeKeyUp = sdlInputConnectionClass.getDeclaredMethod(
                    "nativeKeyUp", int.class);
            onNativeKeyDown.setAccessible(true);
            onNativeKeyUp.setAccessible(true);
            int SDL_SCANCODE_BACKSPACE = 42;
            onNativeKeyDown.invoke(null, SDL_SCANCODE_BACKSPACE);
            onNativeKeyUp.invoke(null, SDL_SCANCODE_BACKSPACE);
        } catch (Exception e) {
            Log.e(TAG, "发送Backspace失败", e);
        }
    }

    public static void enableSDLTextInputForIME() {
        try {
            Class<?> sdlActivityClass = Class.forName("org.libsdl.app.SDLActivity");
            java.lang.reflect.Method showTextInput = sdlActivityClass.getDeclaredMethod(
                    "showTextInput", int.class, int.class, int.class, int.class);
            showTextInput.setAccessible(true);
            showTextInput.invoke(null, 0, 0, 0, 0);
        } catch (Exception e) {
            Log.e(TAG, "启用SDL文本输入失败", e);
        }
    }

    public static void disableSDLTextInput() {
        try {
            Class<?> sdlActivityClass = Class.forName("org.libsdl.app.SDLActivity");
            
            // 获取 COMMAND_TEXTEDIT_HIDE 常量值 (值为 3)
            int COMMAND_TEXTEDIT_HIDE = 3;
            
            // 获取 mSingleton 字段
            java.lang.reflect.Field mSingletonField = sdlActivityClass.getDeclaredField("mSingleton");
            mSingletonField.setAccessible(true);
            Object mSingleton = mSingletonField.get(null);
            
            if (mSingleton == null) {
                Log.w(TAG, "SDLActivity.mSingleton is null, cannot hide text input");
                return;
            }
            
            // 调用 sendCommand 方法
            java.lang.reflect.Method sendCommandMethod = sdlActivityClass.getDeclaredMethod("sendCommand", int.class, Object.class);
            sendCommandMethod.setAccessible(true);
            sendCommandMethod.invoke(mSingleton, COMMAND_TEXTEDIT_HIDE, null);
        } catch (Exception e) {
            Log.e(TAG, "禁用SDL文本输入失败", e);
        }
    }
}

