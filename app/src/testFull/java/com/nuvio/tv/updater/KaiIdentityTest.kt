package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.dto.GitHubAssetDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NuvioArcKai identity: its own package, its own release stream and one armeabi-v7a APK. */
class KaiIdentityTest {
    private fun asset(name: String) = GitHubAssetDto(name = name, browserDownloadUrl = "https://example.invalid/$name")

    @Test
    fun `package and updater point at NuvioArcKai, never at Arc`() {
        assertEquals("com.eibyx.nuvioarckai", BuildConfig.APPLICATION_ID)
        assertEquals("Eibyx", BuildConfig.GITHUB_OWNER)
        assertEquals("NuvioArcKai", BuildConfig.GITHUB_REPO)
        assertEquals("NuvioArcKai", BuildConfig.GITHUB_REPO_NEW_NAME)
    }

    @Test
    fun `the armeabi-v7a release asset is chosen on a 32-bit TV`() {
        val chosen = AbiSelector.chooseBestApkAsset(
            listOf(asset("SHA256SUMS.txt"), asset("native-inventory.json"), asset("app-full-armeabi-v7a-release.apk")),
            supportedAbis = listOf("armeabi-v7a", "armeabi")
        )
        assertEquals("app-full-armeabi-v7a-release.apk", chosen?.name)
    }

    @Test
    fun `nothing else in a Kai release is offered as an update`() {
        assertNull(
            AbiSelector.chooseBestApkAsset(
                listOf(asset("SHA256SUMS.txt"), asset("native-inventory.json")),
                supportedAbis = listOf("armeabi-v7a")
            )
        )
    }

    @Test
    fun `the Kai version name parses and orders after the Arc base`() {
        val kai = VersionUtils.parse(BuildConfig.VERSION_NAME)
        assertNotNull(kai)
        assertNotNull(VersionUtils.parse("1.1.0-nt.stable-kai.1"))
        assertTrue(VersionUtils.parse("1.1.0-nt.stable-kai.2")!! > VersionUtils.parse("1.1.0-nt.stable-kai.1")!!)
        assertTrue(VersionUtils.parse("1.1.0-nt.stable-kai.1")!! > VersionUtils.parse("1.1.0-nt.stable")!!)
    }
}
