package com.reinanbr.keyhack.devtools

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TextTransformsTest {
    private fun t(tool: DevTool, s: String) = TextTransforms.apply(tool, s, Instant.ofEpochSecond(1_700_000_000))

    @Test
    fun convertsCase() {
        assertEquals("myHttpServer", t(DevTool.CAMEL, "my HTTP server"))
        assertEquals("MyHttpServer", t(DevTool.PASCAL, "my_http_server"))
        assertEquals("my_http_server", t(DevTool.SNAKE, "myHTTPServer"))
        assertEquals("my-http-server", t(DevTool.KEBAB, "MyHttpServer"))
        assertEquals("MAX_RETRY_COUNT", t(DevTool.CONSTANT, "maxRetryCount"))
        assertEquals("Hello World", t(DevTool.TITLE, "hello WORLD"))
    }

    @Test
    fun encodesAndDecodes() {
        assertEquals("aGVsbG8gw6k=", t(DevTool.BASE64_ENCODE, "hello é"))
        assertEquals("hello é", t(DevTool.BASE64_DECODE, "aGVsbG8gw6k="))
        assertFailsWith<IllegalArgumentException> { t(DevTool.BASE64_DECODE, "%%%") }
        assertEquals("a+b%26c%3D%C3%A9", t(DevTool.URL_ENCODE, "a b&c=é"))
        assertEquals("a b&c=é", t(DevTool.URL_DECODE, "a+b%26c%3D%C3%A9"))
        assertEquals("&lt;a href=&quot;x&quot;&gt;", t(DevTool.HTML_ESCAPE, "<a href=\"x\">"))
        assertEquals("<é>&", t(DevTool.HTML_UNESCAPE, "&lt;&#233;&gt;&amp;"))
    }

    @Test
    fun formatsJson() {
        assertEquals("{\"a\":[1,2],\"b\":null}", t(DevTool.JSON_MINIFY, "{ \"a\": [1, 2],\n \"b\": null }"))
        assertEquals("{\n  \"a\": [\n    1\n  ]\n}", t(DevTool.JSON_PRETTY, "{\"a\":[1]}"))
        assertEquals("say \\\"hi\\\"\\n", t(DevTool.JSON_ESCAPE, "say \"hi\"\n"))
        assertEquals("say \"hi\"\n", t(DevTool.JSON_UNESCAPE, "say \\\"hi\\\"\\n"))
        assertFailsWith<IllegalArgumentException> { t(DevTool.JSON_PRETTY, "{bad") }
    }

    @Test
    fun hashes() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", t(DevTool.MD5, "hello"))
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", t(DevTool.SHA1, "hello"))
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", t(DevTool.SHA256, "hello"))
    }

    @Test
    fun generatesIdsAndTimes() {
        assertTrue(
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(t(DevTool.UUID4, "")),
        )
        val v7 = TextTransforms.uuid7(0x0189_ABCD_EF01L)
        assertTrue(v7.startsWith("0189abcd-ef01-7"), v7)
        assertTrue(v7[19] in "89ab")
        assertEquals("1700000000", t(DevTool.UNIX_TIME, ""))
        assertEquals("2023-11-14T22:13:20Z", t(DevTool.ISO_TIME, ""))
        assertEquals("2023-11-14T22:13:20Z", t(DevTool.TIME_CONVERT, "1700000000"))
        assertEquals("1700000000", t(DevTool.TIME_CONVERT, "2023-11-14T22:13:20Z"))
    }

    @Test
    fun counts() {
        assertEquals("11 chars · 2 words · 2 lines", t(DevTool.COUNT, "hello\nworld"))
    }
}
