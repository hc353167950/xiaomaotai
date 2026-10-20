package com.example.xiaomaotai.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.xiaomaotai.ui.theme.BrandBlue
import com.example.xiaomaotai.ui.theme.BrandBlueLight
import com.example.xiaomaotai.ui.theme.BrandMint
import com.example.xiaomaotai.ui.theme.LocalDarkTheme
import com.example.xiaomaotai.ui.theme.Shape
import com.example.xiaomaotai.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * 备份与同步页（原「我的」页面）。
 *
 * 三个同步动作语义（详见 [WebDavSync]）：
 * - 备份到云端：本地全量覆盖云端
 * - 从云端恢复：云端全量覆盖本地（覆盖前自动本地快照）
 * - 智能合并：两边并集，按「名称 + 日期」去重，谁都不丢
 *
 * 顶部数据条已压缩：原来的大数字横幅占近 100dp，把下面的输入框挤到屏外；
 * 现在改成 52dp 的单行条，一屏就能看到服务器地址和同步按钮。
 */
@Composable
fun WebDavScreen(
    eventStore: EventStore,
    credentials: WebDavCredentials,
    sync: WebDavSync,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
        SettingsTopBar(title = "备份与同步", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.screenH)
                .verticalScrollCompat()
                .padding(bottom = Space.huge)
        ) {
            Spacer(modifier = Modifier.height(Space.xs))

            // ---------- 紧凑数据条 ----------
            CompactStatsBar(
                localCount = localCount,
                configured = credentials.isConfigured()
            )

            Spacer(modifier = Modifier.height(Space.md))

            // ---------- 连接配置 ----------
            SettingsCard {
                Column(modifier = Modifier.padding(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(Space.iconSm + 1.dp)
                        )
                        Spacer(modifier = Modifier.width(Space.sm))
                        Text(
                            text = "WebDAV 连接",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Space.md))

                    SyncTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = "服务器地址",
                        placeholder = "https://dav.example.com/dav/",
                        keyboardType = KeyboardType.Uri
                    )

                    Spacer(modifier = Modifier.height(Space.sm))

                    SyncTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "用户名",
                        placeholder = "登录 WebDAV 的账号",
                        keyboardType = KeyboardType.Text
                    )

                    Spacer(modifier = Modifier.height(Space.sm))

                    SyncTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "密码 / 应用专用密码",
                        placeholder = "坚果云 / infini-cloud 请填应用密码",
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
                        text = "凭据使用系统级加密存储，不会明文写入。会备份到 xiaomaotai/events.json。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Space.md))

            // ---------- 同步动作 ----------
            SettingsCard {
                Column(modifier = Modifier.padding(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(Space.iconSm + 1.dp)
                        )
                        Spacer(modifier = Modifier.width(Space.sm))
                        Text(
                            text = "同步操作",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(Space.xs))

                    Text(
                        text = "备份：本地覆盖云端　恢复：云端覆盖本地　合并：两边取并集",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(Space.md))

                    // 备份 / 恢复 并排
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        SyncActionButton(
                            modifier = Modifier.weight(1f),
                            text = "备份",
                            icon = Icons.Default.CloudUpload,
                            enabled = !busy,
                            onClick = { runAction("正在备份…") { sync.backupToCloud() } }
                        )
                        SyncActionButton(
                            modifier = Modifier.weight(1f),
                            text = "恢复",
                            icon = Icons.Default.CloudDownload,
                            enabled = !busy,
                            danger = true,
                            onClick = { runAction("正在恢复…") { sync.restoreFromCloud() } }
                        )
                    }

                    Spacer(modifier = Modifier.height(Space.sm))

                    SyncActionButton(
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
                        text = "测试连接",
                        icon = Icons.Default.WifiTethering,
                        enabled = !busy,
                        subtle = true,
                        onClick = { runAction("正在测试…") { sync.testConnection() } }
                    )
                }
            }
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

/**
 * 紧凑数据条：单行显示「本地 N 条纪念日 · 配置状态」。
 *
 * 相比原来 34sp 大数字 + 上下 20dp 内边距的横幅，高度从约 100dp 降到 52dp，
 * 数值仍是视觉重点（20sp 粗体），但不再抢走垂直空间。
 */
@Composable
private fun CompactStatsBar(localCount: Int, configured: Boolean) {
    val isDark = LocalDarkTheme.current
    val start = if (isDark) Color(0xFF3A5FA8) else BrandBlueLight
    val end = if (isDark) Color(0xFF1B3F8F) else BrandBlue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.medium)
            .background(Brush.linearGradient(listOf(start, end)))
    ) {
        // 右侧柔光，保留一点质感但不喧宾夺主
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(90.dp)
                .offset(x = 30.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本地",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.width(Space.xs))
            Text(
                text = localCount.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.width(Space.xxs))
            Text(
                text = "条纪念日",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 配置状态徽标
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Text(
                    text = if (configured) "已配置" else "未配置",
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// ---------- 复用小部件 ----------

@Composable
private fun SyncTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        placeholder = {
            Text(
                text = placeholder,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        singleLine = true,
        shape = Shape.small,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailing
    )
}

@Composable
private fun SyncActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    subtle: Boolean = false,
    onClick: () -> Unit
) {
    val container = when {
        danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        subtle -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    }
    val content = when {
        danger -> MaterialTheme.colorScheme.error
        subtle -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = Space.MinTouchTarget),
        enabled = enabled,
        shape = Shape.small,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Space.iconSm)
            )
            Spacer(modifier = Modifier.width(Space.xs))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

/** 滚动修饰符：避免与其他页面的 rememberScrollState 命名冲突 */
@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.verticalScroll(rememberScrollState())
