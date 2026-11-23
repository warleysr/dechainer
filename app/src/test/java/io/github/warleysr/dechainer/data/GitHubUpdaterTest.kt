package io.github.warleysr.dechainer.data

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test

class GitHubUpdaterTest {

    @Test
    fun `isNewerVersion compares semantic versions accurately`() {
        GitHubUpdater.isNewerVersion("1.14.0", "1.13.1") shouldBe true
        GitHubUpdater.isNewerVersion("2.0.0", "1.99.99") shouldBe true
        GitHubUpdater.isNewerVersion("1.13.2", "1.13.1") shouldBe true
        GitHubUpdater.isNewerVersion("1.14", "1.13.9") shouldBe true
        GitHubUpdater.isNewerVersion("1.14.0", "1.14.0-rc1") shouldBe true

        GitHubUpdater.isNewerVersion("1.13.1", "1.13.1") shouldBe false
        GitHubUpdater.isNewerVersion("1.13.0", "1.13.1") shouldBe false
        GitHubUpdater.isNewerVersion("1.14.0-rc1", "1.14.0") shouldBe false
    }

    @Test
    fun `parseLatestReleaseJson returns UpdateAvailable preferring release apk`() {
        val json = """
            {
              "tag_name": "v1.15.0",
              "body": "- Fixed bugs\n- Added updater",
              "html_url": "https://github.com/warleysr/dechainer/releases/tag/v1.15.0",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://github.com/warleysr/dechainer/releases/download/v1.15.0/checksums.txt"
                },
                {
                  "name": "Dechainer-v1.15.0-debug.apk",
                  "size": 12000000,
                  "browser_download_url": "https://github.com/warleysr/dechainer/releases/download/v1.15.0/Dechainer-v1.15.0-debug.apk"
                },
                {
                  "name": "Dechainer-v1.15.0.apk",
                  "size": 10485760,
                  "browser_download_url": "https://github.com/warleysr/dechainer/releases/download/v1.15.0/Dechainer-v1.15.0.apk"
                }
              ]
            }
        """.trimIndent()

        val result = GitHubUpdater.parseLatestReleaseJson(json, "1.14.0")
        val available = result.shouldBeInstanceOf<GitHubUpdater.CheckResult.UpdateAvailable>()
        available.release.version shouldBe "1.15.0"
        available.release.apkName shouldBe "Dechainer-v1.15.0.apk"
        available.release.sizeBytes shouldBe 10485760L
        available.release.releaseNotes shouldBe "- Fixed bugs\n- Added updater"
    }

    @Test
    fun `parseLatestReleaseJson returns UpToDate when version is same or older`() {
        val json = """
            {
              "tag_name": "v1.14.0",
              "assets": [
                {
                  "name": "Dechainer-v1.14.0.apk",
                  "size": 1000,
                  "browser_download_url": "https://github.com/warleysr/dechainer/releases/download/v1.14.0/Dechainer-v1.14.0.apk"
                }
              ]
            }
        """.trimIndent()

        GitHubUpdater.parseLatestReleaseJson(json, "1.14.0")
            .shouldBeInstanceOf<GitHubUpdater.CheckResult.UpToDate>()
        GitHubUpdater.parseLatestReleaseJson(json, "1.15.0")
            .shouldBeInstanceOf<GitHubUpdater.CheckResult.UpToDate>()
    }

    @Test
    fun `parseLatestReleaseJson returns NoApkAsset when no https apk exists`() {
        val json = """
            {
              "tag_name": "v2.0.0",
              "assets": [
                {
                  "name": "source.tar.gz",
                  "size": 5000,
                  "browser_download_url": "https://github.com/warleysr/dechainer/releases/download/v2.0.0/source.tar.gz"
                }
              ]
            }
        """.trimIndent()

        GitHubUpdater.parseLatestReleaseJson(json, "1.14.0") shouldBe GitHubUpdater.CheckResult.NoApkAsset
    }
}
