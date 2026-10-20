package com.example.xiaomaotai.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.EventStore
import com.example.xiaomaotai.WebDavCredentials
import com.example.xiaomaotai.WebDavSync
import com.example.xiaomaotai.ui.theme.LocalUiStyle
import com.example.xiaomaotai.ui.theme.UiStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import com.example.xiaomaotai.ui.theme.LocalDarkTheme
import com.example.xiaomaotai.ui.theme.BrandBlue
import com.example.xiaomaotai.ui.theme.BrandBlueLight
import com.example.xiaomaotai.ui.theme.BrandMint
import com.example.xiaomaotai.ui.theme.Space
import com.example.xiaomaotai.ui.theme.Shape
import kotlinx.coroutines.launch

/**
 * "我的"页面：本地数据概览 + WebDAV 手动同步中心。
 *
 * 三个同步动作语义（详见 [WebDavSync]）：
 * - 备份到云端：本地全量覆盖云端
 * - 从云端恢复：云端全量覆盖本地（覆盖前自动本地快照）
 * - 智能合并：两边并集，按「名称 + 日期」去重，谁都不丢
 */
@Composable
fun ProfileScreen(
    eventStore: EventStore,
    credentials: WebDavCredentials,
    sync: WebDavSync,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isGlass = LocalUiStyle.current == UiStyle.GlassCountdown
    val scope = rememberCoroutineScope()

    // 本地事件数量（随 EventStore 状态变化刷新）
    val events by eventStore.eventsFlow.collectAsState()
    val localCount = events.size

    // 表单状态：进入页面时从加密存储读一次
    var serverUrl by remember { mutableStateOf(credentials.serverUrl) }
    var username by remember { mutableStateOf(credentials.username) }
    var password by remember { mutableStateOf(credentials.password) }
    var showPassword by remember { mutableStateOf(false) }

    // 操作状态
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var mergePlan by remember { mutableStateOf<WebDavSync.MergePlan?>(null) }

    fun persistCredentials() {
        credentials.serverUrl = serverUrl
        credentials.username = username
        credentials.password = password
    }

    /** 统一执行一个耗时同步动作，带 loading 与结果提示 */
    fun runAction(label: String, action: suspend () -> Result<String>) {
        persistCredentials()
        busy = true
        busyLabel = label
        scope.launch {
            val result = action()
            busy = false
            busyLabel = ""
            result.onSuccess {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部标题栏
        if (isGlass) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Space.screenH, vertical = Space.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "我的",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        onClick = onNavigateToSettings,
                        shape = Shape.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 0.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(Space.iconSm)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Space.screenH, vertical = Space.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenH)
        ) {
            Spacer(modifier = Modifier.height(Space.sm))

            // ---------- 本地数据概览（渐变横幅） ----------
            StatsBanner(localCount = localCount)

            Spacer(modifier = Modifier.height(Space.md))

            // ---------- WebDAV 配置 ----------
            ProfileCard {
                SectionTitle("WebDAV 同步设置", Icons.Default.Cloud)

                Spacer(modifier = Modifier.height(Space.md))

                ProfileTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = "服务器地址",
                    placeholder = "https://dav.jianguoyun.com/dav/",
                    keyboardType = KeyboardType.Uri
                )

                Spacer(modifier = Modifier.height(Space.sm))

                ProfileTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "用户名",
                    placeholder = "登录 WebDAV 的账号",
                    keyboardType = KeyboardType.Text
                )

                Spacer(modifier = Modifier.height(Space.sm))

                ProfileTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码 / 应用专用密码",
                    placeholder = "坚果云请填应用密码",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff
                                              else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Space.iconSm)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Space.sm))

                Text(
                    text = "凭据使用系统级加密存储保存，不会以明文写入。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(Space.md))

            // ---------- 三个同步动作 ----------
            ProfileCard {
                SectionTitle("数据同步", Icons.Default.Sync)

                Spacer(modifier = Modifier.height(Space.xs))

                Text(
                    text = "备份到云端：用本地数据覆盖云端\n" +
                           "从云端恢复：用云端数据覆盖本地\n" +
                           "智能合并：两边取并集，重复的只保留一份",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Space.md))

                SyncActionButton(
                    text = "备份到云端",
                    icon = Icons.Default.CloudUpload,
                    enabled = !busy,
                    onClick = {
                        runAction("正在备份…") { sync.backupToCloud() }
                    }
                )

                Spacer(modifier = Modifier.height(Space.sm))

                SyncActionButton(
                    text = "从云端恢复",
                    icon = Icons.Default.CloudDownload,
                    enabled = !busy,
                    danger = true,
                    onClick = {
                        runAction("正在恢复…") { sync.restoreFromCloud() }
                    }
                )

                Spacer(modifier = Modifier.height(Space.sm))

                SyncActionButton(
                    text = "智能合并",
                    icon = Icons.Default.Merge,
                    enabled = !busy,
                    onClick = {
                        // 先预演，弹确认框，用户确认后才真正写入
                        persistCredentials()
                        busy = true
                        busyLabel = "正在比对…"
                        scope.launch {
                            val result = sync.previewMerge()
                            busy = false
                            busyLabel = ""
                            result.onSuccess { mergePlan = it }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "比对失败",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Space.sm))

                SyncActionButton(
                    text = "测试连接",
                    icon = Icons.Default.WifiTethering,
                    enabled = !busy,
                    subtle = true,
                    onClick = {
                        runAction("正在测试…") { sync.testConnection() }
                    }
                )
            }

            Spacer(modifier = Modifier.height(Space.xxl))
        }
    }

    // ---------- 合并预演确认框 ----------
    mergePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { mergePlan = null },
            shape = Shape.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = BrandBlue.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Merge,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier
                                .padding(Space.sm)
                                .size(Space.iconMd)
                        )
                    }
                    Spacer(modifier = Modifier.width(Space.md))
                    Text(
                        text = "确认合并",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "本地 ${plan.localCount} 条 · 云端 ${plan.remoteCount} 条",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Space.md))
                    MergeStatRow("从云端新增", "${plan.addedFromRemote} 条", BrandBlue)
                    Spacer(modifier = Modifier.height(Space.xs))
                    MergeStatRow("本地独有（将上传）", "${plan.keptLocalOnly} 条", BrandMint)
                    Spacer(modifier = Modifier.height(Space.xs))
                    MergeStatRow("完全重复（只留一份）", "${plan.unchanged} 条",
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(Space.md))
                    Text(
                        text = "合并后共 ${plan.mergedCount} 条，同时写入本地与云端。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mergePlan = null
                    runAction("正在合并…") { sync.mergeWithCloud() }
                }) {
                    Text("开始合并", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { mergePlan = null }) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
            }
        )
    }

    // ---------- 全屏 loading ----------
    if (busy) {
        GlobalLoadingDialog(message = busyLabel)
    }
}

// ---------- 复用小部件 ----------

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = Shape.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl, vertical = Space.xl),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Space.iconMd)
        )
        Spacer(modifier = Modifier.width(Space.sm))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = {
            Text(
                placeholder,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = Shape.small,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    )
}

@Composable
private fun SyncActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    danger: Boolean = false,
    subtle: Boolean = false,
    onClick: () -> Unit
) {
    val container = when {
        subtle -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val content = when {
        subtle -> MaterialTheme.colorScheme.onSurfaceVariant
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(Space.MinTouchTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.5f)
        ),
        shape = Shape.small,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Space.iconSm))
        Spacer(modifier = Modifier.width(Space.sm))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 本地数据概览横幅。
 *
 * 用品牌渐变 + 柔光做视觉锚点，比普通白卡更有存在感。
 * 数字用大字号突出，单位小字弱化。
 */
@Composable
private fun StatsBanner(localCount: Int) {
    val isDark = LocalDarkTheme.current
    val shape = Shape.medium
    val start = if (isDark) Color(0xFF3A5FA8) else BrandBlueLight
    val end = if (isDark) Color(0xFF1B3F8F) else BrandBlue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(start, end)))
    ) {
        // 右上柔光
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(140.dp)
                .offset(x = 40.dp, y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.26f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl, vertical = Space.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本地纪念日",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.82f),
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = localCount.toString(),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.width(Space.xs))
                    Text(
                        text = "条",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            Text(
                text = "数据仅保存在本机\n可手动备份到 WebDAV",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.End
            )
        }
    }
}

/** 合并统计单行：左侧说明 + 右侧数值（带颜色） */
@Composable
private fun MergeStatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
