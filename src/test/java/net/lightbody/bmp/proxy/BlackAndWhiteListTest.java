package net.lightbody.bmp.proxy;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

/**
 * Tests to exercise blacklist and whitelist functionality
 * 
 * @author Andy Clark (andy.clark@realvnc.com)
 * 
 */
public class BlackAndWhiteListTest extends DummyServerTest {

	/**
	 * Checks that a proxy whitelist can be cleared successfully.
	 */
	@Test
	public void testWhitelistCanBeCleared() throws ClientProtocolException, IOException {
		proxy.whitelistRequests(new String[] { ".*\\.txt" }, 500);
		// assume that proxy is working before
		assumeThat(httpStatusWhenGetting("http://127.0.0.1:8080/a.txt"), is(200));
		assumeThat(httpStatusWhenGetting("http://127.0.0.1:8080/c.png"), is(500));
		// clear the whitelist
		proxy.clearWhitelist();
		// check that no whitelist is in effect
		assertThat(httpStatusWhenGetting("http://127.0.0.1:8080/a.txt"), is(200));
		assertThat(httpStatusWhenGetting("http://127.0.0.1:8080/c.png"), is(200));
	}
	
	/**
	 * Checks that a proxy blacklist can be cleared successfully.
	 */
	@Test
	public void testBlacklistCanBeCleared() throws ClientProtocolException, IOException {
		proxy.blacklistRequests(".*\\.txt", 404);
		// assume that proxy is working before
		assumeThat(httpStatusWhenGetting("http://127.0.0.1:8080/a.txt"), is(404));
		assumeThat(httpStatusWhenGetting("http://127.0.0.1:8080/c.png"), is(200));
		// clear the blacklist
		proxy.clearBlacklist();
		// check that no blacklist is in effect
		assertThat(httpStatusWhenGetting("http://127.0.0.1:8080/a.txt"), is(200));
		assertThat(httpStatusWhenGetting("http://127.0.0.1:8080/c.png"), is(200));
	}


	/**
	 * Makes a HTTP Get request to the supplied URI, and returns the HTTP status
	 * code.
	 * <p>
	 * Consumes any HTTP response body to prevent subsequent calls from hanging.
	 */
	protected int httpStatusWhenGetting(String uri)
			throws ClientProtocolException, IOException {
		HttpResponse response = null;
		try {
			response = client.execute(new HttpGet(uri));
			return response.getStatusLine().getStatusCode();
		} finally {
			EntityUtils.consumeQuietly(response.getEntity());
		}
	}

}
