package net.lightbody.bmp.proxy.dns;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class AdvancedHostResolverTest {
    private static final Logger log = LoggerFactory.getLogger(AdvancedHostResolverTest.class);

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {DnsJavaResolver.class}, {NativeResolver.class}, {NativeCacheManipulatingResolver.class}, {ChainedHostResolver.class}
        });
    }

    public AdvancedHostResolver resolver;

    public AdvancedHostResolverTest(Class<AdvancedHostResolver> resolverClass) throws IllegalAccessException, InstantiationException {
        // this is a hacky way to allow us to test the ChainedHostResolver, even though it doesn't have a no-arg constructor
        if (resolverClass.equals(ChainedHostResolver.class)) {
            this.resolver = new ChainedHostResolver(ImmutableList.of(new DnsJavaResolver(), new NativeResolver(), new NativeCacheManipulatingResolver()));
        } else {
            this.resolver = resolverClass.newInstance();
        }
    }

    private boolean ipv6Enabled = false;

    @Before
    public void testForIPv6() throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName("::1");
        if (addresses != null) {
            for (InetAddress addr : addresses) {
                if (addr.getClass() == Inet6Address.class) {
                    ipv6Enabled = true;

                    return;
                }
            }
        }
    }

    @Test
    public void testResolveAddress() {
        Collection<InetAddress> yahooAddresses = resolver.resolve("www.yahoo.com");

        assertNotNull("Collection of resolved addresses should never be null", yahooAddresses);

        assertNotEquals("Expected to find at least one address for www.yahoo.com", 0, yahooAddresses.size());
    }

    @Test
    public void testCannotResolveAddress() {
        Collection<InetAddress> noAddresses = resolver.resolve("www.notarealaddress.grenyarnia");

        assertNotNull("Collection of resolved addresses should never be null", noAddresses);

        assertEquals("Expected to find no address for www.notarealaddress.grenyarnia", 0, noAddresses.size());
    }

    @Test
    public void testResolveIPv4AndIPv6Addresses() {
        assumeTrue("Skipping test because IPv6 is not enabled", ipv6Enabled);

        boolean foundIPv4 = false;
        boolean foundIPv6 = false;
        Collection<InetAddress> addresses = resolver.resolve("www.google.com");
        for (InetAddress address : addresses) {
            if (address.getClass() == Inet4Address.class) {
                foundIPv4 = true;
            } else if (address.getClass() == Inet6Address.class) {
                foundIPv6 = true;
            }
        }

        assertTrue("Expected to find at least one IPv4 address for www.google.com", foundIPv4);

        // disabling this assert to prevent test failures on systems without ipv6 access, or when the DNS server does not return IPv6 addresses
        //assertTrue("Expected to find at least one IPv6 address for www.google.com", foundIPv6);
        // update: since the dnsjava resolver now returns ipv6 addresses *only if there are no ipv4 addresses*, it will generally not return
        // any ipv6 addresses for most hostnames

    }

    @Test
    public void testResolveLocalhost() {
        // DnsJavaResolver cannot resolve localhost, since it does not look up entries in the hosts file
        if (resolver.getClass() == DnsJavaResolver.class) {
            return;
        }

        Collection<InetAddress> addresses = resolver.resolve("localhost");

        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertNotEquals("Expected to find at least one address for localhost", 0, addresses.size());
    }

    @Test
    public void testResolveIPv4Address() {
        Collection<InetAddress> addresses = resolver.resolve("127.0.0.1");

        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertNotEquals("Expected to find at least one address for 127.0.0.1", 0, addresses.size());
    }

    @Test
    public void testResolveIPv6Address() {
        assumeTrue("Skipping test because IPv6 is not enabled", ipv6Enabled);

        Collection<InetAddress> addresses = resolver.resolve("::1");

        assertNotNull("Collection of resolved addresses should never be null", addresses);
        assertNotEquals("Expected to find at least one address for ::1", 0, addresses.size());
    }

    @Test
    public void testResolveRemappedHost() {
        Collection<InetAddress> originalAddresses = resolver.resolve("www.google.com");

        assertNotNull("Collection of resolved addresses should never be null", originalAddresses);
        assertNotEquals("Expected to find at least one address for www.google.com", 0, originalAddresses.size());

        resolver.remapHost("www.google.com", "www.bing.com");

        Collection<InetAddress> remappedAddresses = resolver.resolve("www.google.com");
        assertNotNull("Collection of resolved addresses should never be null", remappedAddresses);
        assertNotEquals("Expected to find at least one address for www.google.com remapped to www.bing.com", 0, remappedAddresses.size());

        InetAddress firstRemappedAddr = remappedAddresses.iterator().next();

        //TODO: verify this is correct -- should remapping return the remapped hostname, or the original hostname but with an IP address corresponding to the remapped hostname?
        assertEquals("Expected hostname for returned address to reflect the remapped address.", "www.bing.com", firstRemappedAddr.getHostName());
    }

    @Test
    public void testReplaceRemappedHostWithNewRemapping() {
        // remap the hostname twice. the second remapping should supercede the first.
        resolver.remapHost("www.google.com", "www.yahoo.com");
        resolver.remapHost("www.google.com", "www.bing.com");

        Collection<InetAddress> remappedAddresses = resolver.resolve("www.google.com");
        assertNotNull("Collection of resolved addresses should never be null", remappedAddresses);
        assertNotEquals("Expected to find at least one address for www.google.com remapped to www.bing.com", 0, remappedAddresses.size());

        InetAddress firstRemappedAddr = remappedAddresses.iterator().next();

        //TODO: verify this is correct -- should remapping return the remapped hostname, or the original hostname but with an IP address corresponding to the remapped hostname?
        assertEquals("Expected hostname for returned address to reflect the remapped address.", "www.bing.com", firstRemappedAddr.getHostName());
    }

    @Test
    public void testRetrieveOriginalHostByRemappedHost() {
        resolver.remapHost("www.google.com", "www.bing.com");

        Collection<String> originalHostnames = resolver.getOriginalHostnames("www.bing.com");
        assertEquals("Expected to find one original hostname after remapping", 1, originalHostnames.size());

        String original = originalHostnames.iterator().next();
        assertEquals("Expected to find original hostname of www.google.com after remapping to www.bing.com", "www.google.com", original);
    }

    @Test
    public void testRemoveHostRemapping() {
        resolver.remapHost("www.google.com", "www.notarealaddress");

        Collection<InetAddress> remappedAddresses = resolver.resolve("www.google.com");
        assertEquals("Expected to find no address for remapped www.google.com", 0, remappedAddresses.size());

        resolver.removeHostRemapping("www.google.com");

        Collection<InetAddress> regularAddress = resolver.resolve("www.google.com");
        assertNotNull("Collection of resolved addresses should never be null", remappedAddresses);
        assertNotEquals("Expected to find at least one address for www.google.com after removing remapping", 0, regularAddress.size());
    }

    @Test
    public void testClearHostRemappings() {
        resolver.remapHost("www.google.com", "www.notarealaddress");

        Collection<InetAddress> remappedAddresses = resolver.resolve("www.google.com");
        assertEquals("Expected to find no address for remapped www.google.com", 0, remappedAddresses.size());

        resolver.clearHostRemappings();

        Collection<InetAddress> regularAddress = resolver.resolve("www.google.com");
        assertNotNull("Collection of resolved addresses should never be null", remappedAddresses);
        assertNotEquals("Expected to find at least one address for www.google.com after removing remapping", 0, regularAddress.size());
    }
}
