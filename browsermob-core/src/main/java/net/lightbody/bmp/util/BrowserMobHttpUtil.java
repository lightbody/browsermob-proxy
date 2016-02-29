package net.lightbody.bmp.util;

import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.exception.DecompressionException;
import net.lightbody.bmp.exception.UnsupportedCharsetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Utility class with static methods for processing HTTP requests and responses.
 */
public class BrowserMobHttpUtil {
    private static final Logger log = LoggerFactory.getLogger(BrowserMobHttpUtil.class);

    /**
     * Default MIME content type if no Content-Type header is present. According to the HTTP 1.1 spec, section 7.2.1:
     * <pre>
     *     Any HTTP/1.1 message containing an entity-body SHOULD include a Content-Type header field defining the media
     *     type of that body. If and only if the media type is not given by a Content-Type field, the recipient MAY
     *     attempt to guess the media type via inspection of its content and/or the name extension(s) of the URI used to
     *     identify the resource. If the media type remains unknown, the recipient SHOULD treat it as
     *     type "application/octet-stream".
     * </pre>
     */
    public static final String UNKNOWN_CONTENT_TYPE = "application/octet-stream";

    /**
     * The default charset when the Content-Type header does not specify a charset. From the HTTP 1.1 spec section 3.7.1:
     * <pre>
     *     When no explicit charset parameter is provided by the sender, media subtypes of the "text" type are defined to have a default
     *     charset value of "ISO-8859-1" when received via HTTP. Data in character sets other than "ISO-8859-1" or its subsets MUST be
     *     labeled with an appropriate charset value.
     * </pre>
     */
    public static final Charset DEFAULT_HTTP_CHARSET = StandardCharsets.ISO_8859_1;

    /**
     * Buffer size when decompressing content.
     */
    public static final int DECOMPRESS_BUFFER_SIZE = 16192;

    /**
     * Returns the size of the headers, including the 2 CRLFs at the end of the header block.
     *
     * @param headers headers to size
     * @return length of the headers, in bytes
     */
    public static long getHeaderSize(HttpHeaders headers) {
        long headersSize = 0;
        for (Map.Entry<String, String> header : headers.entries()) {
            // +2 for ': ', +2 for new line
            headersSize += header.getKey().length() + header.getValue().length() + 4;
        }
        return headersSize;
    }

    /**
     * Decompresses the gzipped byte stream.
     *
     * @param fullMessage gzipped byte stream to decomress
     * @return decompressed bytes
     * @throws DecompressionException thrown if the fullMessage cannot be read or decompressed for any reason
     */
    public static byte[] decompressContents(byte[] fullMessage) throws DecompressionException {
        InflaterInputStream gzipReader = null;
        ByteArrayOutputStream uncompressed;
        try {
            gzipReader = new GZIPInputStream(new ByteArrayInputStream(fullMessage));

            uncompressed = new ByteArrayOutputStream(fullMessage.length);

            byte[] decompressBuffer = new byte[DECOMPRESS_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = gzipReader.read(decompressBuffer)) > -1) {
                uncompressed.write(decompressBuffer, 0, bytesRead);
            }

            fullMessage = uncompressed.toByteArray();
        } catch (IOException e) {
            throw new DecompressionException("Unable to decompress response", e);
        } finally {
            try {
                if (gzipReader != null) {
                    gzipReader.close();
                }
            } catch (IOException e) {
                log.warn("Unable to close gzip stream", e);
            }
        }
        return fullMessage;
    }

    /**
     * Returns true if the content type string indicates textual content. Currently these are any Content-Types that start with one of the
     * following:
     * <pre>
     *     text/
     *     application/x-javascript
     *     application/javascript
     *     application/json
     *     application/xml
     *     application/xhtml+xml
     * </pre>
     *
     * @param contentType contentType string to parse
     * @return true if the content type is textual
     */
    public static boolean hasTextualContent(String contentType) {
        return contentType != null &&
                (contentType.startsWith("text/") ||
                contentType.startsWith("application/x-javascript") ||
                contentType.startsWith("application/javascript")  ||
                contentType.startsWith("application/json")  ||
                contentType.startsWith("application/xml")  ||
                contentType.startsWith("application/xhtml+xml")
                );
    }

    /**
     * Extracts all readable bytes from the ByteBuf as a byte array.
     *
     * @param content ByteBuf to read
     * @return byte array containing the readable bytes from the ByteBuf
     */
    public static byte[] extractReadableBytes(ByteBuf content) {
        byte[] binaryContent = new byte[content.readableBytes()];

        content.markReaderIndex();
        content.readBytes(binaryContent);
        content.resetReaderIndex();

        return binaryContent;
    }

    /**
     * Converts the byte array into a String based on the specified charset. The charset cannot be null.
     *
     * @param content bytes to convert to a String
     * @param charset the character set of the content
     * @return String containing the converted content
     * @throws IllegalArgumentException if charset is null
     */
    public static String getContentAsString(byte[] content, Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("Charset cannot be null");
        }

        return new String(content, charset);
    }

    /**
     * Reads the charset directly from the Content-Type header string. If the Content-Type header does not contain a charset,
     * is malformed or unparsable, or if the header is null or empty, this method returns null.
     *
     * @param contentTypeHeader the Content-Type header string; can be null or empty
     * @return the character set indicated in the contentTypeHeader, or null if the charset is not present or is not parsable
     * @throws UnsupportedCharsetException if there is a charset specified in the content-type header, but it is not supported on this platform
     */
    public static Charset readCharsetInContentTypeHeader(String contentTypeHeader) throws UnsupportedCharsetException {
        if (contentTypeHeader == null || contentTypeHeader.isEmpty()) {
            return null;
        }

        MediaType mediaType;
        try {
             mediaType = MediaType.parse(contentTypeHeader);
        } catch (IllegalArgumentException e) {
            log.info("Unable to parse Content-Type header: {}. Content-Type header will be ignored.", contentTypeHeader, e);
            return null;
        }

        try {
            return mediaType.charset().orNull();
        } catch (java.nio.charset.UnsupportedCharsetException e) {
            throw new UnsupportedCharsetException(e);
        }
    }

    /**
     * Identify the host of an HTTP request. This method uses the URI of the request if possible, otherwise it attempts to find the host
     * in the request headers.
     *
     * @param httpRequest HTTP request to parse the host from
     * @return the host the request is connecting to, or null if no host can be found
     */
    public static String getHostFromRequest(HttpRequest httpRequest) {
        // try to use the URI from the request first, if the URI starts with http:// or https://. checking for http/https avoids confusing
        // java's URI class when the request is for a malformed URL like '//some-resource'.
        String host = null;
        if (startsWithHttpOrHttps(httpRequest.getUri())) {
            try {
                URI uri = new URI(httpRequest.getUri());
                host = uri.getHost();
            } catch (URISyntaxException e) {
            }
        }

        // if there was no host in the URI, attempt to grab the host from the Host header
        if (host == null || host.isEmpty()) {
            host = parseHostHeader(httpRequest, false);
        }

        return host;
    }

    /**
     * Gets the host and port from the specified request. Returns the host and port from the request URI if available,
     * otherwise retrieves the host and port from the Host header.
     *
     * @param httpRequest HTTP request
     * @return host and port of the request
     */
    public static String getHostAndPortFromRequest(HttpRequest httpRequest) {
        if (startsWithHttpOrHttps(httpRequest.getUri())) {
            try {
                return getHostAndPortFromUri(httpRequest.getUri());
            } catch (URISyntaxException e) {
                // the URI could not be parsed, so return the host and port in the Host header
            }
        }

        return parseHostHeader(httpRequest, true);
    }

    /**
     * Retrieves the raw (unescaped) path + query string from the specified request. The returned path will not include
     * the scheme, host, or port.
     *
     * @param httpRequest HTTP request
     * @return the unescaped path + query string from the HTTP request
     */
    public static String getRawPathAndParamsFromRequest(HttpRequest httpRequest) {
        // if this request's URI contains a full URI (including scheme, host, etc.), strip away the non-path components
        if (startsWithHttpOrHttps(httpRequest.getUri())) {
            try {
                return getRawPathAndParamsFromUri(httpRequest.getUri());
            } catch (MalformedURLException e) {
                // could not parse the URI, so fall through and return the URI as-is
            }
        }

        return httpRequest.getUri();
    }

    /**
     * Returns true if the string starts with http:// or https://.
     *
     * @param uri string to evaluate
     * @return true if the string starts with http:// or https://
     */
    public static boolean startsWithHttpOrHttps(String uri) {
        if (uri == null) {
            return false;
        }

        // the scheme is case insensitive, according to RFC 7230, section 2.7.3:
        /*
            The scheme and host
            are case-insensitive and normally provided in lowercase; all other
            components are compared in a case-sensitive manner.
        */
        String lowercaseUri = uri.toLowerCase(Locale.US);

        return lowercaseUri.startsWith("http://") || lowercaseUri.startsWith("https://");
    }

    /**
     * Retrieves the raw (unescaped) path and query parameters from the URI, stripping out the scheme, host, and port.
     * The path will begin with a leading '/'. For example, 'http://example.com/some/resource?param%20name=param%20value'
     * would return '/some/resource?param%20name=param%20value'.
     *
     * @param uriString the URI to parse, containing a scheme, host, port, path, and query parameters
     * @return the unescaped path and query parameters from the URI
     * @throws MalformedURLException if the specified URI is invalid or cannot be parsed
     */
    public static String getRawPathAndParamsFromUri(String uriString) throws MalformedURLException {
        URL uri = new URL(uriString);
        String path = uri.getPath();
        String query = uri.getQuery();

        if (query != null) {
            return path + '?' + query;
        } else {
            return path;
        }
    }

    /**
     * Retrieves the host and port from the specified URI.
     *
     * @param uriString URI to retrieve the host and port from
     * @return the host and port from the URI as a String
     * @throws URISyntaxException if the specified URI is invalid or cannot be parsed
     */
    public static String getHostAndPortFromUri(String uriString) throws URISyntaxException {
        URI uri = new URI(uriString);
        if (uri.getPort() == -1) {
            return uri.getHost();
        } else {
            return HostAndPort.fromParts(uri.getHost(), uri.getPort()).toString();
        }
    }

    /**
     * Returns true if the specified response is an HTTP redirect response, i.e. a 300, 301, 302, 303, or 307.
     *
     * @param httpResponse HTTP response
     * @return true if the response is a redirect, otherwise false
     */
    public static boolean isRedirect(HttpResponse httpResponse) {
        switch (httpResponse.getStatus().code()) {
            case 300:
            case 301:
            case 302:
            case 303:
            case 307:
                return true;

            default:
                return false;
        }
    }

    /**
     * Removes a port from a host+port if the string contains the specified port. If the host+port does not contain
     * a port, or contains another port, the string is returned unaltered. For example, if hostWithPort is the
     * string {@code www.website.com:443}, this method will return {@code www.website.com}.
     *
     * <b>Note:</b> The hostWithPort string is not a URI and should not contain a scheme or resource. This method does
     * not attempt to validate the specified host; it <i>might</i> throw IllegalArgumentException if there was a problem
     * parsing the hostname, but makes no guarantees. In general, it should be validated externally, if necessary.
     *
     * @param hostWithPort string containing a hostname and optional port
     * @param portNumber port to remove from the string
     * @return string with the specified port removed, or the original string if it did not contain the portNumber
     */
    public static String removeMatchingPort(String hostWithPort, int portNumber) {
        HostAndPort parsedHostAndPort = HostAndPort.fromString(hostWithPort);
        if (parsedHostAndPort.hasPort() && parsedHostAndPort.getPort() == portNumber) {
            // HostAndPort.getHostText() strips brackets from ipv6 addresses, so reparse using fromHost
            return HostAndPort.fromHost(parsedHostAndPort.getHostText()).toString();
        } else {
            return hostWithPort;
        }
    }

    /**
     * Retrieves the host and, optionally, the port from the specified request's Host header.
     *
     * @param httpRequest HTTP request
     * @param includePort when true, include the port
     * @return the host and, optionally, the port specified in the request's Host header
     */
    private static String parseHostHeader(HttpRequest httpRequest, boolean includePort) {
        // this header parsing logic is adapted from ClientToProxyConnection#identifyHostAndPort.
        List<String> hosts = httpRequest.headers().getAll(HttpHeaders.Names.HOST);
        if (!hosts.isEmpty()) {
            String hostAndPort = hosts.get(0);

            if (includePort) {
                return hostAndPort;
            } else {
                HostAndPort parsedHostAndPort = HostAndPort.fromString(hostAndPort);
                return parsedHostAndPort.getHostText();
            }
        } else {
            return null;
        }
    }
}
