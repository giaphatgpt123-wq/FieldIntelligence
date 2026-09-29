package vn.duongodau.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.duongodau.app.interop.AppInterop
import vn.duongodau.app.interop.InteropResult
import vn.duongodau.app.update.AppUpdateManager
import vn.duongodau.app.update.InstallResult
import vn.duongodau.app.update.UpdateCheckResult

private val PanelNavy = Color(0xFF071A2E)
private val PanelTeal = Color(0xFF0E6F73)
private val PanelOrange = Color(0xFFF4A229)
private val PanelSlate = Color(0xFF5C6F7B)
private val PanelGood = Color(0xFF1A9B67)
private val PanelWarning = Color(0xFFE58A16)
private val PanelDanger = Color(0xFFD94B4B)

@Composable
fun SystemPanel(initialBridgeText: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var installResult by remember { mutableStateOf<InstallResult?>(null) }
    var bridgeText by remember(initialBridgeText) { mutableStateOf(initialBridgeText) }
    var bridgeStatus by remember { mutableStateOf<String?>(null) }

    fun checkNow() {
        scope.launch {
            checking = true
            updateResult = AppUpdateManager.checkForUpdate(context)
            checking = false
        }
    }

    LaunchedEffect(Unit) { checkNow() }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Cài đặt & cập nhật", fontWeight = FontWeight.Black, fontSize = 18.sp, color = PanelNavy)
            Text(
                "Một package ổn định • kiểm tra chữ ký • cài đè đúng phiên bản • có đường lui khi mạng hoặc app đích không sẵn sàng.",
                color = PanelSlate,
                fontSize = 12.sp
            )

            UpdateStatus(updateResult, checking)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { checkNow() },
                    enabled = !checking && !installing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (checking) "Đang kiểm tra…" else "Kiểm tra cập nhật")
                }

                val installable = when (val r = updateResult) {
                    is UpdateCheckResult.Available -> r.info
                    is UpdateCheckResult.DevChannel -> r.stableInfo
                    else -> null
                }
                Button(
                    onClick = {
                        val info = installable ?: return@Button
                        scope.launch {
                            installing = true
                            installResult = AppUpdateManager.downloadVerifyAndInstall(context, info)
                            installing = false
                        }
                    },
                    enabled = installable != null && !checking && !installing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelOrange, contentColor = PanelNavy)
                ) {
                    Text(if (installing) "Đang tải…" else "Tải & cài", fontWeight = FontWeight.Bold)
                }
            }

            InstallStatus(installResult) {
                AppUpdateManager.openLegacyUninstall(context)
            }

            Spacer(Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PanelTeal.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, PanelTeal.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Liên thông với ứng dụng khác", fontWeight = FontWeight.Bold, color = PanelTeal)
                    Text(
                        "Có thể nhận chia sẻ/deep link từ app khác; khi mở app đích sẽ thử native app trước rồi tự rơi về ứng dụng phù hợp hoặc web.",
                        color = PanelSlate,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = bridgeText,
                        onValueChange = { bridgeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tên đường / địa điểm để liên thông") },
                        placeholder = { Text("Ví dụ: QL20, Trần Phú, Đà Lạt") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                bridgeStatus = describe(AppInterop.openGoogleMapsSearch(context, bridgeText))
                            },
                            enabled = bridgeText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PanelTeal)
                        ) { Text("Mở Maps") }
                        OutlinedButton(
                            onClick = {
                                bridgeStatus = describe(AppInterop.shareRoad(context, bridgeText, null, null))
                            },
                            enabled = bridgeText.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Chia sẻ") }
                    }
                    bridgeStatus?.let { Text(it, color = PanelSlate, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun UpdateStatus(result: UpdateCheckResult?, checking: Boolean) {
    val (text, tone) = when {
        checking -> "Đang kiểm tra kênh phát hành…" to PanelTeal
        result == null -> "Chưa kiểm tra cập nhật." to PanelSlate
        result is UpdateCheckResult.Available -> "Có bản ${result.info.versionName} (code ${result.info.versionCode})." to PanelWarning
        result is UpdateCheckResult.UpToDate -> "Đang ở bản mới nhất: ${result.installedVersionName}." to PanelGood
        result is UpdateCheckResult.DevChannel -> {
            val version = result.stableInfo?.versionName ?: "chưa có"
            "Đang chạy kênh DEV. Bản ổn định mới nhất: $version." to PanelTeal
        }
        result is UpdateCheckResult.Error -> result.message to PanelDanger
        else -> "Trạng thái cập nhật chưa xác định." to PanelSlate
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.28f))
    ) {
        Text(text, modifier = Modifier.padding(11.dp), color = tone, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun InstallStatus(result: InstallResult?, onUninstallLegacy: () -> Unit) {
    when (result) {
        null -> Unit
        InstallResult.InstallerOpened -> Text("Đã chuyển sang trình cài Android. Hệ điều hành sẽ xác nhận bước cài đè.", color = PanelGood, fontSize = 12.sp)
        InstallResult.UnknownSourcesPermissionRequired -> Text("Android cần cấp quyền ‘Cài ứng dụng không rõ nguồn gốc’ cho Đường ở đâu. Cấp một lần rồi bấm Tải & cài lại.", color = PanelWarning, fontSize = 12.sp)
        is InstallResult.LegacySignatureConflict -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PanelWarning.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, PanelWarning.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Phát hiện bản P0 cũ dùng chữ ký khác. Android không cho cài đè. Chỉ cần gỡ bản cũ một lần để chuyển sang kênh ký ổn định; các bản sau sẽ cập nhật đè bình thường.",
                        color = PanelWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onUninstallLegacy) { Text("Gỡ bản P0 cũ") }
                }
            }
        }
        is InstallResult.Error -> Text(result.message, color = PanelDanger, fontSize = 12.sp)
    }
}

private fun describe(result: InteropResult): String = when (result) {
    is InteropResult.Opened -> "Đã chuyển sang ${result.target}."
    is InteropResult.Failed -> result.message
}
