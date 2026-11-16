package com.reinanbr.keyhack.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GifProviderTest {
    @Test
    fun parsesGiphy() {
        val body =
            """{"data": [{"id": "abc", "title": "cat", "images": {
                 "fixed_width_small": {"url": "https://media1.giphy.com/s.gif", "width": "100", "height": 80},
                 "downsized": {"url": "https://media1.giphy.com/d.gif", "width": 300, "height": 240}}},
                 {"id": "broken"}],
               "pagination": {"total_count": 50, "count": 25, "offset": 0}}"""
        val page = GiphyProvider("k").parse(body)
        assertEquals(1, page.items.size)
        assertEquals("https://media1.giphy.com/d.gif", page.items[0].full.url)
        assertEquals(240, page.items[0].full.height)
        assertEquals(100, page.items[0].preview.width)
        assertEquals("25", page.next)
    }

    @Test
    fun buildsEncodedUrls() {
        val url = GiphyProvider("my key").searchUrl("gato & cão", null, 24, "pt-BR")
        assertTrue(url.startsWith("https://api.giphy.com/v1/gifs/search?api_key=my+key&q=gato+%26+c%C3%A3o"), url)
    }

    @Test
    fun noKeyMeansNoProvider() {
        assertNull(GifProvider.create("giphy", "  "))
        assertNull(GifProvider.create("nope", "k"))
        assertNull(GifProvider.create("tenor", "k"), "the Tenor API was shut down on 2026-06-30")
    }

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
