package com.cliptune.model

import android.net.Uri

/**
 * 音频转换过程的状态表示
 */
sealed class ConversionState {

    /**
     * 初始状态 / 空闲状态
     */
    data object Idle : ConversionState()

    /**
     * 正在转换，progress 为 0f-1f 的进度
     */
    data class Converting(val progress: Float) : ConversionState()

    /**
     * 转换成功，outputUri 为保存后的文件 Uri
     */
    data class Success(val outputUri: Uri) : ConversionState()

    /**
     * 转换失败，message 为错误信息
     */
    data class Error(val message: String) : ConversionState()
}

