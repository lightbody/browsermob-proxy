package net.lightbody.bmp.proxy.http;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import java.net.URI;
import org.apache.http.annotation.NotThreadSafe;

// Allows for HTTP DELETE requests to contain a body, which the HttpDelete
// class does not support. Please see:
//   http://stackoverflow.com/a/3820549/581722
@NotThreadSafe
public class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {

    public final static String METHOD_NAME = "DELETE";

    public HttpDeleteWithBody() {
        super();
    }

    public HttpDeleteWithBody(final URI uri) {
        super();
        setURI(uri);
    }

    /**
     * @throws IllegalArgumentException if the uri is invalid.
     */
    public HttpDeleteWithBody(final String uri) {
        super();
        setURI(URI.create(uri));
    }

    @Override
    public String getMethod() {
        return METHOD_NAME;
    }

}
