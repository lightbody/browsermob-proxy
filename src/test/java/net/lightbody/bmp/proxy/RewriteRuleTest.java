package net.lightbody.bmp.proxy;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import java.io.IOException;

import net.lightbody.bmp.proxy.util.IOUtils;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;

public class RewriteRuleTest extends DummyServerTest {
	
	@Test
	public void testThatRewriteRulesCanBeCleared() throws IllegalStateException, ClientProtocolException, IOException {
		proxy.rewriteUrl("(.*)a\\.txt", "$1b.txt");
		// assume that rewrite rules are working
		String body = IOUtils.readFully(client.execute(new HttpGet("http://127.0.0.1:8080/a.txt")).getEntity().getContent());
		assumeThat(body, equalTo("this is b.txt"));
		// check that clearing them works
		proxy.clearRewriteRules();
		body = IOUtils.readFully(client.execute(new HttpGet("http://127.0.0.1:8080/a.txt")).getEntity().getContent());
		assertThat(body, equalTo("this is a.txt"));
	}

}
