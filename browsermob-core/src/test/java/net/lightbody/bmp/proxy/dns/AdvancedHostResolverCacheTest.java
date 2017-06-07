package net.lightbody.bmp.proxy.dns;

import com.google.common.collect.ImmutableList;
import net.lightbody.bmp.proxy.test.util.NewProxyServerTestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

@RunWith(Parameterized.class)
public class AdvancedHostResolverCacheTest {
    private static final Logger log = LoggerFactory.getLogger(AdvancedHostResolverCacheTest.class);

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                // skip DNS cache operations for NativeResolver
                {DnsJavaResolver.class}, {NativeCacheManipulatingResolver.class}, {ChainedHostResolver.class}
        });
    }

    public final AdvancedHostResolver resolver;

    public AdvancedHostResolverCacheTest(Class<AdvancedHostResolver> resolverClass) throws IllegalAccessException, InstantiationException {
        // this is a hacky way to allow us to test the ChainedHostResolver, even though it doesn't have a no-arg constructor
        if (resolverClass.equals(ChainedHostResolver.class)) {
            // don't use the NativecacheManipulatingResolver on Windows, since it is unsupported
            this.resolver = new ChainedHostResolver(
                    NewProxyServerTestUtil.isWindows() ? ImmutableList.of(new DnsJavaResolver())
                                                : ImmutableList.of(new NativeCacheManipulatingResolver(), new DnsJavaResolver()));
        } else {
            this.resolver = resolverClass.newInstance();
        }
    }

    @Before
    public void skipForTravisCi() {
        // skip these tests on the CI server since the DNS lookup is extremely fast, even when cached
        assumeFalse("true".equals(System.getenv("TRAVIS")));
    }

    @Before
    public void skipForNativeDnsCacheOnWindows() {
        // the NativecacheManipulatingResolver does not work on Windows because Java seems to use to the OS-level cache
        assumeFalse("NativeCacheManipulatingResolver does not support cache manipulation on Windows",
                NewProxyServerTestUtil.isWindows() && this.resolver instanceof NativeCacheManipulatingResolver);
    }

    @Test
    public void testCanClearDNSCache() {
        // populate the cache
        resolver.resolve("www.msn.com");

        resolver.clearDNSCache();

        long start = System.nanoTime();
        resolver.resolve("www.msn.com");
        long finish = System.nanoTime();

        assertNotEquals("Expected non-zero DNS lookup time for www.msn.com after clearing DNS cache", 0, finish - start);
    }

    @Test
    public void testCachedPositiveLookup() {
        long start = System.nanoTime();
        // must use an address that we haven't already resolved in another test
        resolver.resolve("news.bing.com");
        long finish = System.nanoTime();

        long uncachedLookupNs = finish - start;

        assertNotEquals("Expected non-zero DNS lookup time for news.bing.com on first lookup", 0, uncachedLookupNs);

        start = System.nanoTime();
        resolver.resolve("news.bing.com");
        finish = System.nanoTime();

        long cachedLookupNs = finish - start;

        assertTrue("Expected extremely fast DNS lookup time for news.bing.com on second (cached) lookup. Uncached: " + uncachedLookupNs + "ns; cached: " + cachedLookupNs + "ns.", cachedLookupNs <= uncachedLookupNs / 2);
    }

    @Test
    public void testCachedNegativeLookup() {
        long start = System.nanoTime();
        resolver.resolve("fake.notarealaddress");
        long finish = System.nanoTime();

        long uncachedLookupNs = finish - start;

        assertNotEquals("Expected non-zero DNS lookup time for fake.notarealaddress on first lookup", 0, uncachedLookupNs);

        start = System.nanoTime();
        resolver.resolve("fake.notarealaddress");
        finish = System.nanoTime();

        long cachedLookupNs = finish - start;

        assertTrue("Expected extremely fast DNS lookup time for fake.notarealaddress on second (cached) lookup. Uncached: " + uncachedLookupNs + "ns; cached: " + cachedLookupNs + "ns.", cachedLookupNs <= uncachedLookupNs / 2);
    }

    @Test
    public void testSetPositiveCacheTtl() throws InterruptedException {
        resolver.clearDNSCache();
        resolver.setPositiveDNSCacheTimeout(2, TimeUnit.SECONDS);

        // populate the cache
        Collection<InetAddress> addresses = resolver.resolve("www.msn.com");

        // make sure there are addresses, since this is a *positive* TTL test
        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertNotEquals("Expected to find addresses for www.msn.com", 0, addresses.size());

        // wait for the cache to clear
        Thread.sleep(2500);

        long start = System.nanoTime();
        addresses = resolver.resolve("www.msn.com");
        long finish = System.nanoTime();

        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertNotEquals("Expected to find addresses for www.msn.com", 0, addresses.size());

        assertNotEquals("Expected non-zero DNS lookup time for www.msn.com after setting positive cache TTL", 0, finish - start);
    }

    @Test
    public void testSetNegativeCacheTtl() throws InterruptedException {
        Random random = new Random();
        String fakeAddress = random.nextInt() + ".madeup.thisisafakeaddress";

        resolver.clearDNSCache();
        resolver.setNegativeDNSCacheTimeout(2, TimeUnit.SECONDS);

        // populate the cache
        Collection<InetAddress> addresses = resolver.resolve(fakeAddress);

        // make sure there are no addresses, since this is a *negative* TTL test
        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertEquals("Expected to find no addresses for " + fakeAddress, 0, addresses.size());

        // wait for the cache to clear
        Thread.sleep(2500);

        long start = System.nanoTime();
        addresses = resolver.resolve(fakeAddress);
        long finish = System.nanoTime();

        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertEquals("Expected to find no addresses for " + fakeAddress, 0, addresses.size());

        assertNotEquals("Expected non-zero DNS lookup time for " + fakeAddress + " after setting negative cache TTL", 0, finish - start);
    }

    @Test
    public void testSetEternalNegativeCacheTtl() {
        Random random = new Random();
        String fakeAddress = random.nextInt() + ".madeup.thisisafakeaddress";

        resolver.clearDNSCache();
        resolver.setNegativeDNSCacheTimeout(-1, TimeUnit.SECONDS);

        // populate the cache
        Collection<InetAddress> addresses = resolver.resolve(fakeAddress);

        // make sure there are no addresses, since this is a *negative* TTL test
        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertEquals("Expected to find no addresses for " + fakeAddress, 0, addresses.size());

        long start = System.nanoTime();
        addresses = resolver.resolve(fakeAddress);
        long finish = System.nanoTime();

        long cachedLookupNs = finish - start;

        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertEquals("Expected to find no addresses for " + fakeAddress, 0, addresses.size());

        assertTrue("Expected extremely fast DNS lookup time for " + fakeAddress + " after setting eternal negative cache TTL. Cached lookup time: " + cachedLookupNs + "ns.", cachedLookupNs <= TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSetEternalPositiveCacheTtl() {
        resolver.clearDNSCache();
        resolver.setPositiveDNSCacheTimeout(-1, TimeUnit.SECONDS);

        log.info("Using resolver: {}", resolver.getClass().getSimpleName());

        // populate the cache
        long one = System.nanoTime();
        Collection<InetAddress> addresses = resolver.resolve("www.msn.com");
        long two = System.nanoTime();
        log.info("Time to resolve address without cache: {}ns", two - one);

        // make sure there are addresses, since this is a *positive* TTL test
        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertNotEquals("Expected to find addresses for www.msn.com", 0, addresses.size());

        long start = System.nanoTime();
        addresses = resolver.resolve("www.msn.com");
        long finish = System.nanoTime();

        long cachedLookupNs = finish - start;

        log.info("Time to resolve address with cache: {}ns", cachedLookupNs);

        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertNotEquals("Expected to find addresses for www.msn.com", 0, addresses.size());

        assertTrue("Expected extremely fast DNS lookup time for www.msn.com after setting eternal negative cache TTL. Cached lookup time: " + cachedLookupNs + "ns.", cachedLookupNs <= TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS));
    }
}
