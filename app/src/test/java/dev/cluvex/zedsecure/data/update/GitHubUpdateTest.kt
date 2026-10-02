package dev.cluvex.zedsecure.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateTest {
    private val release = """
        {
          "tag_name": "v3.1.3",
          "html_url": "https://github.com/CluvexStudio/ZedSecure/releases/tag/v3.1.3",
          "body": "<div align=\"center\">badge</div>\n\n**Download for your system:**\n\n<table></table>\n\n### What's new\n\nFixes from member reports.\n\n- **Installs again:** the [GitHub](https://github.com) APK explains the `Play` signature\n- Windows shows text\n\n<sub>Checksums: [SHA256SUMS.txt](https://x)</sub>\n",
          "assets": [
            { "name": "ZedSecure-3.1.3-arm64-v8a.apk", "browser_download_url": "https://dl/arm64.apk" },
            { "name": "ZedSecure-3.1.3-armeabi-v7a.apk", "browser_download_url": "https://dl/v7.apk" },
            { "name": "ZedSecure-3.1.3-universal.apk", "browser_download_url": "https://dl/universal.apk" },
            { "name": "ZedSecure-3.1.3-x86_64.msi", "browser_download_url": "https://dl/setup.msi" }
          ]
        }
    """.trimIndent()

    @Test
    fun `a phone gets the APK for its own processor`() {
        val arm64 = UpdateChecker.parseGitHubRelease(release, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!
        assertEquals("3.1.3", arm64.versionName)
        assertEquals("https://dl/arm64.apk", arm64.downloadUrl)

        val v7 = UpdateChecker.parseGitHubRelease(release, listOf("armeabi-v7a", "armeabi"))!!
        assertEquals("https://dl/v7.apk", v7.downloadUrl)
    }

    @Test
    fun `an unknown processor falls back to the universal APK, the desktop to the release page`() {
        assertEquals("https://dl/universal.apk", UpdateChecker.parseGitHubRelease(release, listOf("riscv64"))!!.downloadUrl)
        assertEquals(
            "https://github.com/CluvexStudio/ZedSecure/releases/tag/v3.1.3",
            UpdateChecker.parseGitHubRelease(release, emptyList())!!.downloadUrl,
        )
    }

    @Test
    fun `the dialog shows only the changes, as plain text`() {
        val notes = UpdateChecker.parseGitHubRelease(release, emptyList())!!.releaseNotes
        assertEquals(
            "Fixes from member reports.\n\n• Installs again: the GitHub APK explains the Play signature\n• Windows shows text",
            notes,
        )
    }

    @Test
    fun `old desktop tags still give a comparable version, and junk gives nothing`() {
        val old = """{"tag_name":"desktop-v3.1.2","html_url":"https://x","body":"","assets":[]}"""
        assertEquals("3.1.2", UpdateChecker.parseGitHubRelease(old, emptyList())!!.versionName)
        assertTrue(UpdateChecker.compareVersions("3.1.3", "3.1.2") > 0)
        assertNull(UpdateChecker.parseGitHubRelease("""{"message":"Not Found"}""", emptyList()))
    }
}
