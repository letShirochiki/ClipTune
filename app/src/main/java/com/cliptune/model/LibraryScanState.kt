package com.cliptune.model

/**
 * 一次目录扫描的结果。
 *
 * 列表为空时必须能区分「还没选目录」「目录本身是空的」「目录里有文件但没有可识别的媒体」
 * 「授权失效/读不到」这几种情况。否则界面上只能显示一句“请先授权”，
 * 用户会误以为选择没有生效，也就是常见的“选了但读不到”。
 */
sealed interface LibraryScanState {

    /** 还没有选择目录 */
    data object NoDirectory : LibraryScanState

    /**
     * 扫描成功。
     *
     * @param directoryName 目录友好名，例如 Movies、DCIM/Camera
     * @param itemCount 命中并写入媒体库的文件数
     * @param fileCount 目录里遍历到的文件总数
     */
    data class Ready(
        val directoryName: String?,
        val itemCount: Int,
        val fileCount: Int
    ) : LibraryScanState

    /**
     * 扫描失败。
     *
     * @param permissionLost 是否因为授权失效或没有权限，这种情况必须提示用户重新选择目录
     */
    data class Failed(
        val directoryName: String?,
        val message: String,
        val permissionLost: Boolean
    ) : LibraryScanState
}
