#include <jni.h>
#include "SDL.h"

JNIEXPORT void JNICALL
Java_com_app_ralaunch_feature_controls_bridges_SDLInputBridge_nativeStartTextInput(
        JNIEnv *env, jclass clazz) {
    SDL_StartTextInput();
}

JNIEXPORT void JNICALL
Java_com_app_ralaunch_feature_controls_bridges_SDLInputBridge_nativeStopTextInput(
        JNIEnv *env, jclass clazz) {
    SDL_StopTextInput();
}