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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    val statusDb = remember { OfflineLibraryDb(appContext) }
    DisposableEffect(statusDb) { onDispose { statusDb.close() } }

    var showUpdateCenter by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateRunResult?>(null) }
    var installed by remember { mutableStateOf(statusDb.installedPackageStates()) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun runUpdate() {
        if (running) return
        running = true
        result = null
        Thread {
            val next = LibraryUpdateEngine.checkAndUpdate(appContext)
            mainHandler.post {
                result = next
                installed = statusDb.installedPackageStates()
                running = false
            }
        }.start()
    }

    Box(Modifier.fillMaxSize()) {
        PremiumSurvivalApp()
        ExtendedFloatingActionButton(
            onClick = { showUpdateCenter = true },
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
            onDismissRequest = { if (!running) showUpdateCenter = false },
            title = { Text("Cập nhật dữ liệu", fontWeight = FontWeight.Black) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Nút này cập nhật thư viện độc lập với APK. Gói mới chỉ được cài sau khi kiểm HTTPS, SHA-256, manifest và Bộ quy tắc thư viện.",
                        color = UpdateMuted,
                        fontSize = 12.sp
                    )
                    LibraryDataPackages.merge(installed).forEach { item ->
                        PackageStatusCard(item)
                    }
                    val updateResult = result
                    if (running) {
                        Surface(
                            color = UpdateSage,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(10.dp))
                                Text("Đang kiểm tra và xác minh gói cập nhật...", fontSize = 12.sp)
                            }
                        }
                    } else if (updateResult != null) {
                        val ok = updateResult.checked && updateResult.errors.isEmpty()
                        Surface(
                            color = if (ok) UpdateSage else UpdateWarn,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (ok) UpdateForest else Color(0xFF8A5A00)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(updateResult.message, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                updateResult.errors.take(4).forEach { error ->
                                    Text("• $error", color = UpdateMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { runUpdate() }, enabled = !running) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (running) "Đang cập nhật" else "Kiểm tra & cập nhật")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateCenter = false }, enabled = !running) {
                    Text("Đóng")
                }
            }
        )
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
