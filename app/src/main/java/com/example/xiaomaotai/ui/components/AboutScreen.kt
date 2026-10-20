package com.example.xiaomaotai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaomaotai.AppInfo
import com.example.xiaomaotai.ui.theme.BrandBlue
import com.example.xiaomaotai.ui.theme.BrandBlueLight
import com.example.xiaomaotai.ui.theme.LocalDarkTheme
import com.example.xiaomaotai.ui.theme.Shape
import com.example.xiaomaotai.ui.theme.Space

/**
 * 关于页。
 *
 * 设计思路：不用「一堆信息行」的公文式排版，而是
 * 1. 一张品牌渐变卡承载 logo + 名称 + 版本胶囊 + 一句话理念；
 * 2. 三个价值主张做成小胶囊（本地优先 / 无账号 / 数据在你手里）；
 * 3. 「这个版本」用短列表说清改了什么；
 * 4. 「技术信息」把实现细节摊开——这不是炫耀，是告诉用户数据去了哪里。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { AppInfo.version(context) }

    SettingsSubPage(title = "关于", onBack = onBack) {
        BrandHeroCard(versionName = version.name)

        ValueChips()

        SettingsGroupLabel("这个版本")
        SettingsCard {
            Column(modifier = Modifier.padding(Space.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${version.name}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(Space.sm))
                    Text(
                        text = "build ${version.code}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Space.md))
                AppInfo.RELEASE_NOTES.forEach { note ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(BrandBlue)
                        )
                        Spacer(modifier = Modifier.width(Space.sm))
                        Text(
                            text = note,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SettingsGroupLabel("技术信息")
        SettingsCard {
            TechRow("农历算法", "lunar-java")
            SettingsDivider()
            TechRow("同步协议", "WebDAV")
            SettingsDivider()
            TechRow("数据存储", "仅本机")
            SettingsDivider()
            TechRow("账号体系", "无")
        }

        Spacer(modifier = Modifier.height(Space.lg))

        Text(
            text = "用 ♥ 记录每一个明天",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 品牌主视觉卡：渐变底 + 右上柔光 + 圆形 logo 位。
 *
 * logo 用应用名首字的圆形徽标代替——既不动 mipmap 资源，
 * 也能在没有图片的情况下保持视觉重心。
 */
@Composable
private fun BrandHeroCard(versionName: String) {
    val isDark = LocalDarkTheme.current
    val start = if (isDark) Color(0xFF3A5FA8) else BrandBlueLight
    val end = if (isDark) Color(0xFF1B3F8F) else BrandBlue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.large)
            .background(Brush.linearGradient(listOf(start, end)))
    ) {
        // 右上柔光
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(160.dp)
                .offset(x = 50.dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.24f), Color.Transparent)
                    )
                )
        )
        // 顶部高光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.38f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl, vertical = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 圆形字标
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "茅",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
            }

            Spacer(modifier = Modifier.height(Space.md))

            Text(
                text = AppInfo.NAME,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.4).sp
            )

            Spacer(modifier = Modifier.height(Space.sm))

            // 版本胶囊
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "v$versionName",
                    modifier = Modifier.padding(horizontal = Space.md, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(modifier = Modifier.height(Space.md))

            Text(
                text = AppInfo.SLOGAN,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 三个价值主张：本地优先 / 无账号 / 数据在你手里 */
@Composable
private fun ValueChips() {
    val items = listOf("本地优先", "无账号", "数据在你手里")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        items.forEach { label ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = Shape.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(vertical = Space.sm),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 技术信息单行：左标签 右值 */
@Composable
private fun TechRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
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
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
