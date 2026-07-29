package com.expense.tracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow
import com.expense.tracker.ui.theme.softShadow

/**
 * 软件说明书 — 帮助用户快速理解 App 的核心功能与交互细节。
 *
 * 设计：每节是一张白色卡片（softShadow）+ emoji 标题 + 多段 bullet 文本，
 * 整页可滚动，与设置菜单保持视觉一致（ChatGPT 白底极简风）。
 */
@Composable
fun UserManualScreen(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .iconBtnShadow()
                        .clip(CircleShape)
                        .background(AppColors.Bg)
                        .pointerInput(onClose) { detectTapGestures(onTap = { onClose() }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary) }
                Spacer(Modifier.size(12.dp))
                Text("软件说明书", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            }

            Spacer(Modifier.size(16.dp))
            Text(
                "欢迎使用记账助手 — 一款像和 AI 聊天一样记账的 Android 原生 App。所有数据只存在你的手机本地。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )

            Spacer(Modifier.size(20.dp))

            ManualSection(
                emoji = "🚀",
                title = "快速开始",
                lines = listOf(
                    "1. 打开 App 看到的就是聊天主页面",
                    "2. 想用 AI 自然语言记账 → 点亮输入框右侧的 🧠 按钮（思考模式）",
                    "3. 不想用 AI / 没配置 API Key → 关闭 🧠，下方会出现分类标签 + 金额输入框（模板模式）",
                    "4. 第一次使用思考模式前，先去 ⚙ 设置 → 🧠 LLM 设置 配置 API",
                ),
            )

            ManualSection(
                emoji = "💬",
                title = "思考模式 — 自然语言记账",
                lines = listOf(
                    "示例：「午饭35块，下午买了杯18的咖啡」 → AI 自动拆成两笔记账",
                    "也可以闲聊：「今天天气真好」 → AI 正常对话，不会强行记账",
                    "支持相对时间：「昨天买了双鞋200」「上周三花了50打车」 → AI 基于真实当前时间算出绝对日期",
                    "回复会逐字打字出现，配合三点跳动的思考动画",
                ),
            )

            ManualSection(
                emoji = "⚡",
                title = "模板模式 — 1 秒快速记账",
                lines = listOf(
                    "关闭 🧠 后，下方出现分类标签 + 金额输入框",
                    "单击分类 → 选中；输入金额 → 提交，立即落库",
                    "双击分类 → 弹出居中气泡，可填详细描述（如「菠萝百香果」）+ 金额，气泡里还能切换分类（动画过渡）",
                ),
            )

            ManualSection(
                emoji = "👆",
                title = "消息操作 — 长按",
                lines = listOf(
                    "长按任一聊天气泡 → 弹出底部菜单",
                    "「复制」：把消息文字复制到剪贴板（user / assistant 都支持）",
                    "「编辑」：仅 user 消息支持 — 改文字 + 同步改关联的金额/分类/备注（如果这条消息记了一笔账）",
                    "编辑保存后所有数据 UPDATE 原行，不会新建记录",
                ),
            )

            ManualSection(
                emoji = "📊",
                title = "支出分析",
                lines = listOf(
                    "底部 Dock → 📊 分析",
                    "周 / 月 / 年 三个周期切换",
                    "柱形图：每天/每周的总支出",
                    "折线图：消费次数趋势（Y 轴整数刻度）",
                    "饼图：按分类占比着色（餐饮黄、饮品浅蓝、医疗红、住房绿…）",
                    "🧠 智核分析：让 AI 看你的消费数据，给 3-5 条针对性洞察",
                    "💹 投资分类自动排除（短线很快收回，不算消费），但历史明细仍保留",
                ),
            )

            ManualSection(
                emoji = "📅",
                title = "历史明细",
                lines = listOf(
                    "底部 Dock → 📅 历史",
                    "按日期分组的卡片，点开看当天每一笔",
                    "每一笔显示：分类 emoji + 备注小字 + 时间 + 金额",
                    "向左滑动任一明细行 → 揭开红色「删除」按钮；向右滑回去 → 关闭。灵敏度对称，轻轻一拨即可",
                ),
            )

            ManualSection(
                emoji = "⚙",
                title = "设置 — 你现在的位置",
                lines = listOf(
                    "🧠 LLM 设置：配置 API 地址（DeepSeek / OpenAI / 豆包）、密钥、模型名",
                    "📁 数据导入与导出：可导入本应用 CSV（自动跳过重复账目），也可导出 JSON（全部记账+聊天）或 CSV（仅记账）",
                    "📖 软件说明书：你正在看的这页",
                    "ℹ️ 关于：版本号 + 简介",
                ),
            )

            ManualSection(
                emoji = "🔒",
                title = "数据安全",
                lines = listOf(
                    "记账数据存在手机本地 SQLite（/data/data 私有目录）",
                    "API Key 存在本地 DataStore，不会上传到任何第三方",
                    "App 不收集、不联网上传任何用户行为数据",
                    "唯一会发请求的就是你配置的 LLM API（地址由你自己填）",
                    "升级 App（同签名）数据不会丢；签名变了或卸载会清空",
                ),
            )

            ManualSection(
                emoji = "💡",
                title = "小技巧",
                lines = listOf(
                    "AI 偶尔回复了不规范文字 — App 会容错当作闲聊显示，不会报错",
                    "AI 不知道「现在几点」是常见错觉 — 我们每次请求都把当前时间注入 system prompt，不会瞎填日期",
                    "如果删错了一笔，可先去「最近删除」恢复；也建议定期用「数据导入与导出」做个 JSON 或 CSV 备份",
                    "投资类的支出（买股票/基金）选 💹 投资 分类，不会污染消费洞察",
                ),
            )

            Spacer(Modifier.size(24.dp))
            Text(
                "源码 + 反馈：github.com/sca331613-commits/ai-expense-bot",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ManualSection(emoji: String, title: String, lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .softShadow(elevation = 2.dp, cornerRadius = 16.dp, spotAlpha = 0.06f)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Bg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
            )
        }
        lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("•", color = AppColors.TextSecondary, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}
