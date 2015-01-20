package net.lightbody.bmp.proxy.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.conn.DnsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Address;
import org.xbill.DNS.Cache;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

public class BrowserMobHostNameResolver implements DnsResolver {
    /**
     * Allows fallback to the native Java lookup mechanism when xbill cannot resolve the hostname. Controlled by bmp.allowNativeDnsFallback system property.
     */
	public static final String ALLOW_NATIVE_DNS_FALLBACK = "bmp.allowNativeDnsFallback";
    
	private static final int MAX_RETRY_COUNT = 5;

	private static final Logger LOG = LoggerFactory.getLogger(BrowserMobHostNameResolver.class);

    private Map<String, String> remappings = new ConcurrentHashMap<String, String>();
    private Map<String, List<String>> reverseMapping = new ConcurrentHashMap<String, List<String>>();

    private Cache cache;
    private Resolver resolver;

    public BrowserMobHostNameResolver(Cache cache) {
        this.cache = cache;
        try {
            resolver = new ExtendedResolver();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public BrowserMobHostNameResolver(Cache cache, Resolver resolver) {
        this.cache = cache;
        this.resolver = resolver;
    }

    @Override
    public InetAddress[] resolve(String hostname) throws UnknownHostException {
        String remapping = remappings.get(hostname);
        if (remapping != null) {
            hostname = remapping;
        }

        try {
            return new InetAddress[]{Address.getByAddress(hostname)};
        } catch (UnknownHostException e) {
            // that's fine, this just means it's not an IP address and we gotta look it up, which is common
        }

        boolean isCached;
		try {
			isCached = this.isCached(hostname);
		} catch (TextParseException e) {
			throw new UnknownHostException(hostname);
		}

		Date start = new Date();
		
		Record[] records = findByDNS(hostname);
        
        Date end = new Date();

        List<InetAddress> addrList;
        
        if (records == null || records.length == 0) {
        	// if native java fallback is enabled, attempt to resolve the hostname natively before giving up
        	if (Boolean.getBoolean(ALLOW_NATIVE_DNS_FALLBACK)) {
        		InetAddress[] addresses = findByNativeLookup(hostname);
        		addrList = Arrays.asList(addresses);
        	} else {
        		throw new UnknownHostException(hostname);
        	}
        } else {
        	// found records using the non-native lookup mechanism
        	addrList = new ArrayList<InetAddress>(records.length);
        	 
        	for(Record record : records){
    	        // assembly the addr object
    	        ARecord a = (ARecord) record;
    	        InetAddress addr = InetAddress.getByAddress(hostname, a.getAddress().getAddress());
    			addrList.add(addr);
            }        	
        }

        if (!isCached) {
            // TODO: Associate the the host name with the connection. We do this because when using persistent
            // connections there won't be a lookup on the 2nd, 3rd, etc requests, and as such we wouldn't be able to
            // know what IP address we were requesting.
            RequestInfo.get().dns(start, end, addrList.get(0).getHostAddress());
        } else {
            // if it is a cached hit, we just record zero since we don't want
            // to skew the data with method call timings (specially under load)
            RequestInfo.get().dns(end, end, addrList.get(0).getHostAddress());
        }

        return addrList.toArray(new InetAddress[0]);
    }

	private InetAddress[] findByNativeLookup(String hostname) throws UnknownHostException {
		return InetAddress.getAllByName(hostname);
	}

	private Record[] findByDNS(String hostname) throws UnknownHostException {
		Lookup lookup;
		try {
			lookup = new Lookup(Name.fromString(hostname), Type.A);
		} catch (TextParseException e) {
			throw new UnknownHostException(hostname);
		}

        lookup.setCache(cache);
        lookup.setResolver(resolver);
        // we set the retry count to -1 because we want the first execution not be counted as a retry.
        int retryCount = -1;
        Record[] records;
        
        // we iterate while the status is TRY_AGAIN and MAX_RETRY_COUNT is not exceeded
        do{
        	records = lookup.run();
        	retryCount++;
        }while(lookup.getResult() == Lookup.TRY_AGAIN && retryCount < MAX_RETRY_COUNT );
		return records;
	}

    public void remap(String source, String target) {
        remappings.put(source, target);
        List<String> list = reverseMapping.get(target);
        if (list == null) {
            list = new ArrayList<String>();
        }
        list.add(source);
        reverseMapping.put(target, list);
    }

    public String remapping(String host) {
        return remappings.get(host);
    }

    public List<String> original(String host) {
        return reverseMapping.get(host);
    }

    public void clearCache() {
        this.cache.clearCache();
    }

    public void setCacheTimeout(int timeout) {
        cache.setMaxCache(timeout);
    }

    public boolean isCached(String hostname) throws TextParseException {
        return cache.lookupRecords(Name.fromString(hostname), Type.ANY, 3).isSuccessful();
    }
}
