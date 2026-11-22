package com.reinanbr.keyhack.net

import com.reinanbr.keyhack.media.GiphyProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpFetcherTest {
    @Test
    fun fetcherOnlyAllowsHttpsToProviderHosts() {
        val f = HttpFetcher(GiphyProvider("k").allowedHosts)
        assertTrue(f.isAllowed("https://media2.giphy.com/x.gif"))
        assertTrue(f.isAllowed("https://api.giphy.com/v1"))
        assertFalse(f.isAllowed("http://media2.giphy.com/x.gif"))
        assertFalse(f.isAllowed("https://evilgiphy.com/x.gif"))
        assertFalse(f.isAllowed("https://giphy.com.evil.net/x.gif"))
        assertFalse(f.isAllowed("https://user@giphy.com/x"))
        assertFalse(f.isAllowed("file:///etc/passwd"))
    }
}
