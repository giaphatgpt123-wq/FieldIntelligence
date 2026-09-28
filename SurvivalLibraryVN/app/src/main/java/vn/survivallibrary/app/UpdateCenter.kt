package vn.survivallibrary.app

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UpdateForest = Color(0xFF1D5A3D)
private val UpdateSage = Color(0xFFE9F1E4)
private val UpdatePaper = Color(0xFFFFFEFA)
private val UpdateMuted = Color(0xFF667168)
private val UpdateWarn = Color(0xFFFFE9B6)

@Composable
fun SurvivalLibraryRoot() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var showUpdateCenter by remember { mutableStateOf(false) }

    var libraryRunning by remember { mutableStateOf(false) }
    var libraryResult by remember { mutableStateOf<UpdateRunResult?>(null) }
    var installed by remember { mutableStateOf<List<InstalledPackageState>>(emptyList()) }

    var appChecking by remember { mutableStateOf(false) }
    var appDownloading by remember { mutableStateOf(false) }
    var appCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
    var preparedAppUpdate by remember { mutableStateOf<PreparedAppUpdate?>(null) }
    var appActionMessage by remember { mutableStateOf<String?>(null) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun readInstalledPackages(): List<InstalledPackageState> = runCatching {
        OfflineLibraryDb(appContext).use { it.installedPackageStates() }
    }.getOrDefault(emptyList())

    fun runLibraryUpdate() {
        if (libraryRunning) return
        libraryRunning = true
        libraryResult = null
        Thread {
            val next = LibraryUpdateEngine.checkAndUpdate(appContext)
            val nextInstalled = readInstalledPackages()
            mainHandler.post {
                libraryResult = next
                installed = nextInstalled
                libraryRunning = false
            }
        }.start()
    }

    fun checkAppUpdate() {
        if (appChecking || appDownloading) return
        appChecking = true
        appActionMessage = null
        preparedAppUpdate = null
        Thread {
            val result = AppUpdateEngine.checkForUpdate(appContext)
            mainHandler.post {
                appCheckResult = result
                appChecking = false
            }
        }.start()
    }

    fun downloadAppUpdate(release: AppReleaseInfo) {
        if (appChecking || appDownloading) return
        appDownloading = true
        appActionMessage = null
        Thread {
            try {
                val prepared = AppUpdateEngine.downloadAndVerify(appContext, release)
                mainHandler.post {
                    preparedAppUpdate = prepared
                    appActionMessage = "APK v${release.versionName} đã tải và xác minh. Sẵn sàng cài đặt."
                    appDownloading = false
                }
            } catch (error: Exception) {
                mainHandler.post {
                    appActionMessage = "Không thể chuẩn bị bản cập nhật: ${error.message ?: "lỗi không xác định"}"
                    appDownloading = false
                }
            }
        }.start()
    }

    fun installPreparedUpdate(prepared: PreparedAppUpdate) {
        try {
            when (AppUpdateEngine.launchInstaller(context, prepared)) {
                InstallerLaunchResult.LAUNCHED -> {
                    appActionMessage = "Đã mở trình cài đặt Android. Xác nhận Cập nhật để hoàn tất."
                }
                InstallerLaunchResult.PERMISSION_REQUIRED -> {
                    appActionMessage = "Android đang yêu cầu quyền cài ứng dụng từ nguồn này. Cho phép rồi quay lại và bấm Cài bản mới lần nữa."
                }
            }
        } catch (error: Exception) {
            appActionMessage = "Không mở được trình cài đặt: ${error.message ?: "lỗi không xác định"}"
        }
    }

    Box(Modifier.fillMaxSize()) {
        PremiumSurvivalApp()
        ExtendedFloatingActionButton(
            onClick = {
                installed = readInstalledPackages()
                showUpdateCenter = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 86.dp),
            containerColor = UpdateForest,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
            text = { Text("Cập nhật", fontWeight = FontWeight.Bold) }
        )
    }

    if (showUpdateCenter) {
        AlertDialog(
            onDismissRequest = {
                if (!libraryRunning && !appChecking && !appDownloading) showUpdateCenter = false
            },
            title = { Text("Trung tâm cập nhật", fontWeight = FontWeight.Black) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppUpdateCard(
                        currentVersion = AppUpdateEngine.currentVersionName(appContext),
                        checking = appChecking,
                        downloading = appDownloading,
                        checkResult = appCheckResult,
                        prepared = preparedAppUpdate,
                        message = appActionMessage,
                        onCheck = { checkAppUpdate() },
                        onDownload = { downloadAppUpdate(it) },
                        onInstall = { installPreparedUpdate(it) }
                    )

                    Surface(
                        color = UpdateSage,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Cập nhật thư viện", fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(
                                "Dữ liệu được cập nhật độc lập với APK. Gói mới chỉ được cài sau khi kiểm HTTPS, SHA-256, manifest và Bộ quy tắc thư viện.",
                                color = UpdateMuted,
                                fontSize = 11.sp
                            )
                            LibraryDataPackages.merge(installed).forEach { item ->
                                PackageStatusCard(item)
                            }

                            if (libraryRunning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(10.dp))
                                    Text("Đang kiểm tra dữ liệu...", fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = { runLibraryUpdate() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text("Kiểm tra & cập nhật thư viện")
                                }
                            }

                            val updateResult = libraryResult
                            if (updateResult != null) {
                                val ok = updateResult.checked && updateResult.errors.isEmpty()
                                ResultNotice(
                                    ok = ok,
                                    text = updateResult.message,
                                    details = updateResult.errors
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showUpdateCenter = false },
                    enabled = !libraryRunning && !appChecking && !appDownloading
                ) {
                    Text("Đóng")
                }
            }
        )
    }
}

@Composable
private fun AppUpdateCard(
    currentVersion: String,
    checking: Boolean,
    downloading: Boolean,
    checkResult: AppUpdateCheckResult?,
    prepared: PreparedAppUpdate?,
    message: String?,
    onCheck: () -> Unit,
    onDownload: (AppReleaseInfo) -> Unit,
    onInstall: (PreparedAppUpdate) -> Unit
) {
    Surface(
        color = UpdatePaper,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = UpdateForest)
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("Cập nhật ứng dụng", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("Phiên bản đang cài: v$currentVersion", color = UpdateMuted, fontSize = 11.sp)
                }
            }

            Text(
                "Kiểm tra bản APK mới trên GitHub Release. APK chỉ được mở cài sau khi xác minh SHA-256, package, versionCode và chữ ký.",
                color = UpdateMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            when {
                checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Đang kiểm tra phiên bản mới...", fontSize = 12.sp)
                    }
                }
                downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Đang tải và xác minh APK...", fontSize = 12.sp)
                    }
                }
                prepared != null -> {
                    Button(
                        onClick = { onInstall(prepared) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Cài bản mới v${prepared.release.versionName}")
                    }
                }
                checkResult is AppUpdateCheckResult.UpdateAvailable -> {
                    val release = checkResult.release
                    Surface(color = UpdateSage, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Có phiên bản mới v${release.versionName}", fontWeight = FontWeight.Black)
                            if (release.notes.isNotBlank()) {
                                Text(
                                    release.notes.take(240),
                                    color = UpdateMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { onDownload(release) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Tải bản v${release.versionName}")
                    }
                }
                checkResult is AppUpdateCheckResult.UpToDate -> {
                    ResultNotice(true, "Ứng dụng đang là phiên bản mới nhất.")
                    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("Kiểm tra lại")
                    }
                }
                checkResult is AppUpdateCheckResult.Failed -> {
                    ResultNotice(false, "Không kiểm tra được phiên bản mới.", listOf(checkResult.message))
                    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Text("Thử lại")
                    }
                }
                else -> {
                    Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Kiểm tra phiên bản mới")
                    }
                }
            }

            if (!message.isNullOrBlank()) {
                Text(message, color = UpdateMuted, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun ResultNotice(ok: Boolean, text: String, details: List<String> = emptyList()) {
    Surface(
        color = if (ok) UpdateSage else UpdateWarn,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = if (ok) UpdateForest else Color(0xFF8A5A00)
                )
                Spacer(Modifier.size(7.dp))
                Text(text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            details.take(4).forEach { detail ->
                Text("• $detail", color = UpdateMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PackageStatusCard(item: LibraryPackageUiState) {
    Surface(
        color = UpdatePaper,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.descriptor.displayName, Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 13.sp)
                val statusText = when (item.status) {
                    PackageInstallStatus.NOT_INSTALLED -> "Chưa cài"
                    PackageInstallStatus.INSTALLED -> "Đã cài v${item.installed?.version ?: 0}"
                    PackageInstallStatus.UPDATE_AVAILABLE -> "Có cập nhật"
                    PackageInstallStatus.BLOCKED -> "Bị chặn"
                }
                Text(statusText, color = UpdateForest, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Text(item.descriptor.description, color = UpdateMuted, fontSize = 10.sp, lineHeight = 13.sp)
            if (item.installed != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.recordCount} hồ sơ • ${item.verifiedCount} đã kiểm chứng",
                    color = UpdateMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
