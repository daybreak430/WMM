package com.wmm.ledger.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class BackupFileProvider : FileProvider()

internal fun prepareBackupShare(context: Context, data: LedgerSnapshot): Uri {
    val root = File(context.cacheDir, "backup-share")
    check(root.isDirectory || root.mkdirs())
    // 接收方可能稍后才读取附件，不能在分享面板关闭时立刻删除文件。
    val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
    root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }

    val directory = Files.createTempDirectory(root.toPath(), "export-").toFile()
    try {
        val file = File(directory, "记个账-备份-${LocalDate.now()}.json")
        file.writeBytes(Backup.encode(data))
        return FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
    } catch (error: Exception) {
        directory.deleteRecursively()
        throw error
    }
}
