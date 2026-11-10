package com.nuvio.tv.core.cloudstorage

import com.nuvio.tv.data.mediaserver.ServerItemRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Arc's ServerItemRef splits at the first ':'; cloud connection IDs must survive encode()/parse(). */
class KaiCloudServerRefTest {
    @Test fun `cloud item refs round-trip`() {
        val ref = ServerItemRef("${CloudStreams.CONNECTION_PREFIX}6f1c2a90-1b2c-4d5e-8f90-aabbccddeeff", "drive:file/0B:x?y")
        assertEquals(ref, ServerItemRef.parse(ref.encode()))
        assertFalse(CloudStreams.CONNECTION_PREFIX.contains(':'))
    }

    @Test fun `Google Drive is named in full`() {
        val drive = CloudAccount(id = "a", kind = CloudProviderKind.GOOGLE_DRIVE, name = "x@example.com")
        assertEquals("Google Drive", cloudSourceLabel(drive, listOf(drive)))
    }
}
