package net.lightbody.bmp.util

import org.junit.Test

import java.nio.charset.Charset

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

class BrowserMobHttpUtilTest {
    @Test
    void testGetResourceFromUri() {
        Map<String, String> uriToResource = [
                'http://www.example.com/the/resource': '/the/resource',
                'https://example/resource': '/resource',
                'http://127.0.0.1/ip/address/resource': '/ip/address/resource',
                'https://hostname:8080/host/and/port/resource': '/host/and/port/resource',
                'http://hostname/': '/',
                'https://127.0.0.1/': '/',
                'http://127.0.0.1:1950/ip/port/resource': '/ip/port/resource',
                'https://[abcd:1234::17]/ipv6/literal/resource': '/ipv6/literal/resource',
                'http://[abcd:1234::17]:50/ipv6/with/port/literal/resource': '/ipv6/with/port/literal/resource',
                'https://hostname/query/param/resource?param=value': '/query/param/resource?param=value',
        ]

        uriToResource.each {uri, expectedResource ->
            String parsedResource = BrowserMobHttpUtil.getPathWithParamsFromUri(uri)
            assertEquals("Parsed resource from URL did not match expected resource for URL: " + uri, expectedResource, parsedResource)
        }
    }

    @Test
    void testGetHostAndPortFromUri() {
        Map<String, String> uriToHostAndPort = [
                'http://www.example.com/some/resource': 'www.example.com',
                'https://www.example.com:8080/some/resource': 'www.example.com:8080',
                'http://127.0.0.1/some/resource': '127.0.0.1',
                'https://127.0.0.1:8080/some/resource?param=value': '127.0.0.1:8080',
                'http://localhost/some/resource': 'localhost',
                'https://localhost:1820/': 'localhost:1820',
                'http://[abcd:1234::17]/ipv6/literal/resource': '[abcd:1234::17]',
                'https://[abcd:1234::17]:50/ipv6/with/port/literal/resource': '[abcd:1234::17]:50',
        ]

        uriToHostAndPort.each {uri, expectedHostAndPort ->
            String parsedHostAndPort = BrowserMobHttpUtil.getHostAndPortFromUri(uri)
            assertEquals("Parsed host and port from URL did not match expected host and port for URL: " + uri, expectedHostAndPort, parsedHostAndPort)
        }
    }

    @Test
    void testReadCharsetInContentTypeHeader() {
        Map<String, Charset> contentTypeHeaderAndCharset = [
                'text/html; charset=UTF-8' : Charset.forName('UTF-8'),
                'text/html; charset=US-ASCII' : Charset.forName('US-ASCII'),
                'text/html' : null,
                'application/json;charset=utf-8' : Charset.forName('UTF-8'),
                'text/*; charset=US-ASCII' : Charset.forName('US-ASCII'),
                'unknown-type/something-incredible' : null,
                'unknown-type/something-incredible;charset=UTF-8' : Charset.forName('UTF-8'),
                '1234 & extremely malformed!' : null,
                '1234 & extremely malformed!;charset=UTF-8' : null, // malformed content-types result in unparseable charsets
                '' : null,
        ]

        contentTypeHeaderAndCharset.each {contentTypeHeader, expectedCharset ->
            Charset derivedCharset = BrowserMobHttpUtil.readCharsetInContentTypeHeader(contentTypeHeader)
            assertEquals("Charset derived from parsed content type header did not match expected charset for content type header: " + contentTypeHeader, expectedCharset, derivedCharset)
        }

        Charset derivedCharset = BrowserMobHttpUtil.readCharsetInContentTypeHeader(null)
        assertNull("Expected null Content-Type header to return a null charset", derivedCharset)

        boolean threwException = false
        try {
            BrowserMobHttpUtil.readCharsetInContentTypeHeader('text/html; charset=FUTURE_CHARSET')
        } catch (UnsupportedCharsetException) {
            threwException = true
        }

        assertTrue('Expected an UnsupportedCharsetException to occur when parsing the content type header text/html; charset=FUTURE_CHARSET', threwException)
    }

    @Test
    void testHasTextualContent() {
        Map<String, Boolean> contentTypeHeaderAndTextFlag = [
                'text/html' : true,
                'text/*' : true,
                'application/x-javascript' : true,
                'application/javascript' : true,
                'application/xml' : true,
                'application/xhtml+xml' : true,
                'application/xhtml+xml; charset=UTF-8' : true,
                'application/octet-stream' : false,
                '': false,
        ]

        contentTypeHeaderAndTextFlag.each {contentTypeHeader, expectedIsText ->
            boolean isTextualContent = BrowserMobHttpUtil.hasTextualContent(contentTypeHeader)
            assertEquals("hasTextualContent did not return expected value for content type header: " + contentTypeHeader, expectedIsText, isTextualContent)
        }

        boolean isTextualContent = BrowserMobHttpUtil.hasTextualContent(null)
        assertFalse("Expected hasTextualContent to return false for null content type", isTextualContent)
    }

    @Test
    void testGetPathWithParamsFromURI_KeepsPathEscaped() {
        String expectedPath = "/hello%20world";
        String uri = "http://example.com" + expectedPath;
        assertEquals("getPathWithParamsFromURI did not keep the path encoded", expectedPath, BrowserMobHttpUtil.getPathWithParamsFromUri(uri));
    }

    @Test
    void testGetPathWithParamsFromURI_KeepsQueryParamsEscaped() {
        String expectedPathWithParams = "/test?hello%20world";
        String uri = "http://example.com" + expectedPathWithParams;
        assertEquals("getPathWithParamsFromURI did not keep the query params encoded", expectedPathWithParams, BrowserMobHttpUtil.getPathWithParamsFromUri(uri));
    }

    @Test
    void testRemoveMatchingPort() {
        def portRemoved = BrowserMobHttpUtil.removeMatchingPort("www.example.com:443", 443)
        assertEquals("www.example.com", portRemoved)

        def hostnameWithNonMatchingPort = BrowserMobHttpUtil.removeMatchingPort("www.example.com:443", 1234)
        assertEquals("www.example.com:443", hostnameWithNonMatchingPort)

        def hostnameNoPort = BrowserMobHttpUtil.removeMatchingPort("www.example.com", 443)
        assertEquals("www.example.com", hostnameNoPort)

        def ipv4WithoutPort = BrowserMobHttpUtil.removeMatchingPort("127.0.0.1:443", 443)
        assertEquals("127.0.0.1", ipv4WithoutPort)

        def ipv4WithNonMatchingPort = BrowserMobHttpUtil.removeMatchingPort("127.0.0.1:443", 1234)
        assertEquals("127.0.0.1:443", ipv4WithNonMatchingPort);

        def ipv4NoPort = BrowserMobHttpUtil.removeMatchingPort("127.0.0.1", 443)
        assertEquals("127.0.0.1", ipv4NoPort);

        def ipv6WithoutPort = BrowserMobHttpUtil.removeMatchingPort("[::1]:443", 443)
        assertEquals("[::1]", ipv6WithoutPort)

        def ipv6WithNonMatchingPort = BrowserMobHttpUtil.removeMatchingPort("[::1]:443", 1234)
        assertEquals("[::1]:443", ipv6WithNonMatchingPort);

        def ipv6NoPort = BrowserMobHttpUtil.removeMatchingPort("[::1]", 443)
        assertEquals("[::1]", ipv6NoPort);
    }
}
