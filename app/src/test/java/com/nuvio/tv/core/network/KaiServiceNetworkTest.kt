package com.nuvio.tv.core.network

import com.nuvio.tv.data.local.KaiServiceNetworkStore
import com.nuvio.tv.data.local.KaiServicePass
import com.nuvio.tv.data.local.KaiServiceTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiServiceNetworkTest {
    @Test fun `every TorBox CDN node is the same service`() {
        assertEquals("tb-cdn.st", KaiServiceNetwork.serviceKeyOf("store-031.weur.tb-cdn.st"))
        assertEquals("tb-cdn.st", KaiServiceNetwork.serviceKeyOf("store-007.eeur.tb-cdn.st:443"))
        assertEquals("TorBox", KaiServiceNetwork.displayName("tb-cdn.st"))
        assertEquals(KaiServiceNetwork.groupKey("torbox.app"), KaiServiceNetwork.groupKey("tb-cdn.st"))
    }

    @Test fun `other services and plain domains`() {
        assertEquals("Real-Debrid", KaiServiceNetwork.displayName(KaiServiceNetwork.serviceKeyOf("47.download.real-debrid.com")!!))
        assertEquals("example.co.uk", KaiServiceNetwork.serviceKeyOf("cdn.example.co.uk"))
        assertEquals("example.org", KaiServiceNetwork.displayName(KaiServiceNetwork.serviceKeyOf("a.b.example.org")!!))
    }

    @Test fun `addresses without a name have no service`() {
        assertNull(KaiServiceNetwork.serviceKeyOf("192.168.1.20:8096"))
        assertNull(KaiServiceNetwork.serviceKeyOf("localhost"))
        assertNull(KaiServiceNetwork.serviceKeyOf(""))
        assertNull(KaiServiceNetwork.serviceKeyOf(null))
    }

    private fun test(key: String, at: Long) = KaiServiceTest(key, "store-1.$key", at, "Wi-Fi", 40.0,
        listOf(KaiServicePass(2, 8, 70.0), KaiServicePass(3, 16, 95.5)), 25.0)

    @Test fun `a new test replaces the same service and goes first`() {
        val old = listOf(test("tb-cdn.st", 1), test("real-debrid.com", 2))
        val merged = KaiServiceNetworkStore.merged(old, test("torbox.app", 3), KaiServiceNetwork::groupKey)
        assertEquals(listOf("torbox.app", "real-debrid.com"), merged.map { it.serviceKey })
    }

    @Test fun `only the newest services are kept`() {
        var list = emptyList<KaiServiceTest>()
        repeat(KaiServiceNetworkStore.MAX_SERVICES + 3) { list = KaiServiceNetworkStore.merged(list, test("s$it.com", it.toLong()), KaiServiceNetwork::groupKey) }
        assertEquals(KaiServiceNetworkStore.MAX_SERVICES, list.size)
        assertEquals("s${KaiServiceNetworkStore.MAX_SERVICES + 2}.com", list.first().serviceKey)
    }

    @Test fun `stored tests round trip and hold no link`() {
        val saved = listOf(test("tb-cdn.st", 5))
        val raw = KaiServiceNetworkStore.encode(saved)
        assertEquals(saved, KaiServiceNetworkStore.decode(raw))
        assertFalse(raw.contains("http"))
        assertTrue(KaiServiceNetworkStore.decode("not json").isEmpty())
        assertTrue(KaiServiceNetworkStore.decode(null).isEmpty())
    }
}
