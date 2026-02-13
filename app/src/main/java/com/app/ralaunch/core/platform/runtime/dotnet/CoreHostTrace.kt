package com.app.ralaunch.core.platform.runtime.dotnet

object CoreHostTrace {

    fun initCoreHostTraceRedirect() {
        nativeInitCoreHostTraceRedirect()
    }

    private external fun nativeInitCoreHostTraceRedirect();
}