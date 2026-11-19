package com.nuvio.tv.reshaped.sync

/**
 * NuvioArcKai: Reshaped syncs its Live TV playlists between TVs through its own settings sync.
 * Kai keeps Live TV on this TV only (no background sync), so changes are not sent anywhere.
 */
object ReshapedSync {
    fun onLocalChange() = Unit
}
