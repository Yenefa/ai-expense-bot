package com.expense.tracker.privacy

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * 系统备份边界契约（v3.15.1 起）：账目/聊天/周期账单（`expense.db`）与预算（`budget_prefs`）
 * 属于财务数据，**不得进入系统云备份**；凭据与长期记忆同样排除。
 *
 * 三条规则同时成立；本测试是这条边界的**唯一权威**（在 CI 中运行）。`tools/test-api-key-backup-rules.ps1`
 * 只是调用本测试的薄封装，不要在脚本里再维护第二份期望清单。
 * - Android 8–11（`backup_rules.xml`）：云备份与设备迁移共用同一套规则（平台限制）；
 * - Android 12+ 云备份（`data_extraction_rules.xml` 的 `cloud-backup`）：同样排除；
 * - Android 12+ 设备迁移（`device-transfer`）：**保留**账目与预算——设备对设备直传，不经服务器。
 *
 * 若有人把账目或预算加回云备份，这里必须红。
 */
class BackupPolicyTest {

    private val credentialExclusions = listOf(
        "sharedpref" to "secure_api_key.xml",
        "sharedpref" to "secure_subscription_credential.xml",
        "file" to "datastore/user_prefs.preferences_pb",
        "file" to "datastore/subscription_prefs.preferences_pb",
        "file" to "datastore/user_profile_prefs.preferences_pb",
        "file" to "datastore/proactive_prefs.preferences_pb",
    )

    private val financialExclusions = listOf(
        "database" to "expense.db",
        "database" to "expense.db-wal",
        "database" to "expense.db-shm",
        "database" to "expense.db-journal",
        "file" to "datastore/budget_prefs.preferences_pb",
    )

    @Test
    fun `云备份不得包含财务数据与凭据`() {
        val legacy = excludesOf(parse(repoFile("app/src/main/res/xml/backup_rules.xml")), "full-backup-content")
        assertExcludedExactlyOnce("Android 8-11 fullBackupContent（云备份与设备迁移共用）", legacy, credentialExclusions + financialExclusions)

        val cloud = excludesOf(parse(repoFile("app/src/main/res/xml/data_extraction_rules.xml")), "data-extraction-rules", "cloud-backup")
        assertExcludedExactlyOnce("Android 12+ cloud-backup", cloud, credentialExclusions + financialExclusions)
    }

    @Test
    fun `设备迁移保留账目与预算`() {
        val transfer = excludesOf(parse(repoFile("app/src/main/res/xml/data_extraction_rules.xml")), "data-extraction-rules", "device-transfer")
        assertExcludedExactlyOnce("Android 12+ device-transfer", transfer, credentialExclusions)
        financialExclusions.forEach { entry ->
            assertThat(transfer).doesNotContain(entry)
        }
    }

    @Test
    fun `清单声明了两套备份规则资源`() {
        val application = parse(repoFile("app/src/main/AndroidManifest.xml"))
            .documentElement
            .getElementsByTagName("application")
            .item(0) as Element
        assertThat(application.getAttribute("android:allowBackup")).isEqualTo("true")
        assertThat(application.getAttribute("android:fullBackupContent")).isEqualTo("@xml/backup_rules")
        assertThat(application.getAttribute("android:dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
    }

    private fun assertExcludedExactlyOnce(scope: String, actual: List<Pair<String, String>>, expected: List<Pair<String, String>>) {
        expected.forEach { entry ->
            val count = actual.count { it == entry }
            assertWithMessage("$scope 必须恰好排除 domain=${entry.first} path=${entry.second} 一次").that(count).isEqualTo(1)
        }
    }

    private fun excludesOf(doc: Document, rootTag: String, section: String? = null): List<Pair<String, String>> {
        val root = doc.getElementsByTagName(rootTag).item(0) as Element
        val scope = if (section == null) root else root.getElementsByTagName(section).item(0) as Element
        val nodes = scope.getElementsByTagName("exclude")
        return (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("domain") to element.getAttribute("path")
        }
    }

    private fun parse(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun repoFile(relativePath: String): File {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: error("repository root not found from ${System.getProperty("user.dir")}")
        val file = File(root, relativePath)
        check(file.exists()) { "missing $relativePath" }
        return file
    }
}
