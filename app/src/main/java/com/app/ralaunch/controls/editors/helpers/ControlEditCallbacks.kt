package com.app.ralaunch.controls.editors.helpers

import com.app.ralaunch.controls.data.ControlData

/**
 * 控件编辑回调接口
 */
interface OnControlUpdatedListener {
    fun onControlUpdated(data: ControlData?)
}

interface OnControlDeletedListener {
    fun onControlDeleted(data: ControlData?)
}

interface OnControlCopiedListener {
    fun onControlCopied(data: ControlData?)
}
