# AGENT_PLAN.md — 通宵接管任务（2026-08-06 opencode）

## 项目现状
- Android 记账 App「Y.E cost」（com.expense.tracker），Kotlin + Jetpack Compose + Room + ML Kit OCR + LLM 自然语言记账
- 服务器：腾讯云 CloudBase（envId ilove-d5g0gzrpp375112b9，上海），云函数 ye-cost-api（兑换码核销 + AI 代理 hy3），实测 /health 200 在线
- GitHub：https://github.com/sca331613-commits/ai-expense-bot（dev 分支已推送至 10b19ee）

## 技术栈
Compose BOM 2024.02 / Kotlin 1.9.22 / Room 2.6.1 / OkHttp / ML Kit / versionCode 27 (3.6) / minSdk 26

## 用户真实问题 → 修复状态
- [x] 解析速度超级慢 → 记录上下文 200→30、历史消息 10×4000→6×1200、打字机 3 倍速
- [x] 批量记账需要确认（准则只有删除要确认）→ 仅删除确认
- [x] 服务器部署位置疑问 → 确认为腾讯云在线；GitHub 从未推送（已推）
- [x] 时间停到 8.1 → 无时间词时新增强制当前时间、修改不改日期；会员页显示服务器时间
- [x] 全部 164 单元测试通过；release 构建 + debug 证书签名通过
- [ ] 云函数/下载站线上部署（阻塞：tcb 登录失效，需大统领扫码）
- [ ] 实体手机复测（阻塞：无设备）

## 验收条件（已完成项）
- 测试：gradlew testDebugUnitTest → 164/164
- 构建：gradlew assembleRelease -PYE_COST_SUBSCRIPTION_API_BASE_URL=https://ilove-d5g0gzrpp375112b9-1413557923.ap-shanghai.app.tcloudbase.com/ye-cost-api → SUCCESS
- 签名：apksigner v2/v3 + zipalign 通过，证书 e5ac68a1... 与上版一致

## Git
- dev 分支：09d7374（v3.6 final 快照+修复）、f4431ff（merge 远程）、10b19ee（清理）已推送
- 网站 worktree：59b2259（指向 8.6 APK + /health serverTime，本地仓库未推送）
