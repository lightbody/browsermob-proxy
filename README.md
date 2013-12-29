[![Build Status](https://travis-ci.org/v-dev/browsermob-proxy.png?branch=request-interceptor-test)](https://travis-ci.org/v-dev/browsermob-proxy)

BrowserMob Proxy
================

BrowserMob Proxy is a simple utility that makes it easy to capture performance data from browsers, typically written using automation toolkits such as Selenium and Watir.

Features
--------

The proxy is programmatically controlled via a REST interface or by being embedded directly inside Java-based programs and unit tests. It captures performance data the [HAR format](http://groups.google.com/group/http-archive-specification). It addition it also can actually control HTTP traffic, such as:

 - blacklisting and whitelisting certain URL patterns
 - simulating various bandwidth and latency
 - remapping DNS lookups
 - flushing DNS caching
 - controlling DNS and request timeouts
 - automatic BASIC authorization

REST API
--------

To get started, first start the proxy by running `browsermob-proxy` or `browsermob-proxy.bat` in the bin directory:

    $ sh browsermob-proxy -port 9090
    INFO 05/31 03:12:48 o.b.p.Main           - Starting up...
    2011-05-30 20:12:49.517:INFO::jetty-7.3.0.v20110203
    2011-05-30 20:12:49.689:INFO::started o.e.j.s.ServletContextHandler{/,null}
    2011-05-30 20:12:49.820:INFO::Started SelectChannelConnector@0.0.0.0:9090

Once started, there won't be an actual proxy running until you create a new proxy. You can do this by POSTing to /proxy:

    [~]$ curl -X POST http://localhost:9090/proxy
    {"port":9091}

or optionally specify your own port:

    [~]$ curl -X POST -d 'port=9099' http://localhost:9090/proxy
    {"port":9099}

or if running BrowserMob Proxy in a multi-homed environment, specify a desired bind address (default is `0.0.0.0`):

    [~]$ curl -X POST -d 'bindAddress=192.168.1.222' http://localhost:9090/proxy
    {"port":9096}

Once that is done, a new proxy will be available on the port returned. All you have to do is point a browser to that proxy on that port and you should be able to browse the internet. The following additional APIs will then be available:

 - GET /proxy - get a list of ports attached to `ProxyServer` instances managed by `ProxyManager`
 - PUT /proxy/[port]/har - creates a new HAR attached to the proxy and returns the HAR content if there was a previous HAR. Supports the following parameters:
  - initialPageRef - the string name of the first page ref that should be used in the HAR. Defaults to "Page 1".
  - captureHeaders - Boolean, capture headers
  - captureContent - Boolean, capture content bodies
  - captureBinaryContent - Boolean, capture binary content
 - PUT /proxy/[port]/har/pageRef - starts a new page on the existing HAR. Supports the following parameters:
  - pageRef - the string name of the first page ref that should be used in the HAR. Defaults to "Page N" where N is the next page number.
 - DELETE /proxy/[port] - shuts down the proxy and closes the port
 - GET /proxy/[port]/har - returns the JSON/HAR content representing all the HTTP traffic passed through the proxy
 - PUT /proxy/[port]/whitelist - Sets a list of URL patterns to whitelist. Takes the following parameters:
  - regex - a comma separated list of regular expressions
  - status - the HTTP status code to return for URLs that do not match the whitelist
 - DELETE /proxy/[port]/whitelist - Clears all URL patterns from the whitelist 
 - PUT /proxy/[port]/blacklist - Set a URL to blacklist. Takes the following parameters:
  - regex - the blacklist regular expression
  - status - the HTTP status code to return for URLs that are blacklisted
 - DELETE /proxy/[port]/blacklist - Clears all URL patterns from the blacklist
 - PUT /proxy/[port]/limit - Limit the bandwidth through the proxy. Takes the following parameters:
  - downstreamKbps - Sets the downstream kbps
  - upstreamKbps - Sets the upstream kbps
  - latency - Add the given latency to each HTTP request
  - enable - (true/false) a boolean that enable bandwidth limiter. By default the limit is disabled, although setting any of the properties above will implicitly enable throttling
  - payloadPercentage - a number ]0, 100] specifying what percentage of data sent is payload. e.g. use this to take into account overhead due to tcp/ip.
  - maxBitsPerSecond - The max bits per seconds you want this instance of StreamManager to respect.
 - POST /proxy/[port]/headers - Set and override HTTP Request headers. For example setting a custom User-Agent.
  - Payload data should be json encoded set of headers (not url-encoded)
 - POST /proxy/[port]/hosts - Overrides normal DNS lookups and remaps the given hosts with the associated IP address
  - Payload data should be json encoded set of name/value pairs (ex: {"example.com": "1.2.3.4"})
 - POST /proxy/[port]/auth/basic/[domain] - Sets automatic basic authentication for the specified domain
  - Payload data should be json encoded username and password name/value pairs (ex: {"username": "myUsername", "password": "myPassword"}
 - PUT /proxy/[port]/wait - wait till all request are being made
  - quietPeriodInMs - Sets quiet period in milliseconds
  - timeoutInMs - Sets timeout in milliseconds 
 - PUT /proxy/[port]/timeout - Handles different proxy timeouts. Takes the following parameters:
  - requestTimeout - request timeout in milliseconds. A timeout value of -1 is interpreted as infinite timeout. It equals -1 by default.
  - readTimeout - read timeout in milliseconds. Which is the timeout for waiting for data or, put differently, a maximum period inactivity between two consecutive data packets). A timeout value of zero is interpreted as an infinite timeout. It equals 60000 by default
  - connectionTimeout - Determines the timeout in milliseconds until a connection is established. A timeout value of zero is interpreted as an infinite timeout. It eqauls 60000 by default
  - dnsCacheTimeout - Sets the maximum length of time that records will be stored in this Cache. A nonpositive value disables this feature (that is, sets no limit). It equals 0 y default
 - PUT /proxy/[port]/rewrite - Redirecting URL's
  - matchRegex - a matching URL regular expression
  - replace - replacement URL
 - DELETE /proxy/[port]/rewrite - Removes all URL redirection rules currently in effect
 - PUT /proxy/[port]/retry - Setting the retry count
  - retrycount - the number of times a method will be retried
 - DELETE /proxy/[port]/dns/cache - Empties the Cache.

For example, once you've started the proxy you can create a new HAR to start recording data like so:

    [~]$ curl -X PUT -d 'initialPageRef=Foo' http://localhost:8080/proxy/9091/har

Now when traffic goes through port 9091 it will be attached to a page reference named "Foo". Consult the HAR specification for more info on what a "pageRef" is. You can also start a new pageRef like so:

    [~]$ curl -X PUT -d 'pageRef=Bar' http://localhost:8080/proxy/9091/har/pageRef

That will ensure no more HTTP requests get attached to the old pageRef (Foo) and start getting attached to the new pageRef (Bar). You can also get the HAR content at any time like so:

    [~]$ curl http://localhost:8080/proxy/9091/har

Sometimes you will want to route requests through an upstream proxy server. In this case specify your proxy server by adding the httpProxy parameter to your create proxy request:

    [~]$ curl -X POST http://localhost:9090/proxy?httpProxy=yourproxyserver.com:8080
    {"port":9091}

Alternatively, you can specify the upstream proxy config for all proxies created using the standard JVM [system properties for HTTP proxies](http://docs.oracle.com/javase/6/docs/technotes/guides/net/proxies.html).
Note that you can still override the default upstream proxy via the POST payload, but if you omit the payload the JVM
system properties will be used to specify the upstream proxy.

*TODO*: Other REST APIs supporting all the BrowserMob Proxy features will be added soon.

Embedded Mode
-------------

If you're using Java and Selenium, the easiest way to get started is to embed the project directly in your test. First, you'll need to make sure that all the dependencies are imported in to the project. You can find them in the *lib* directory. Or, if you're using Maven, you can add this to your pom:

    <dependency>
        <groupId>net.lightbody.bmp</groupId>
        <artifactId>browsermob-proxy</artifactId>
        <version>LATEST_VERSION (ex: 2.0-beta-9)</version>
        <scope>test</scope>
    </dependency>

Once done, you can start a proxy using `net.lightbody.bmp.proxy.ProxyServer`:

    ProxyServer server = new ProxyServer(9090);
    server.start();

This class supports every feature that the proxy supports. In fact, the REST API is a subset of the methods exposed here, so new features will show up here before they show up in the REST API. Consult the Javadocs for the full API.

If your project already defines a Selenium dependency then you may want to exclude the version that browsermob-proxy pulls in, like so:

    <dependency>
        <groupId>net.lightbody.bmp</groupId>
        <artifactId>browsermob-proxy</artifactId>
        <version>LATEST_VERSION (ex: 2.0-beta-9)</version>
        <scope>test</scope>
        <exclusions>
            <exclusion>
                <groupId>org.seleniumhq.selenium</groupId>
                <artifactId>selenium-api</artifactId>
            </exclusion>
        </exclusions>
    </dependency>


Using With Selenium
-------------------

You can use the REST API with Selenium however you want. But if you're writing your tests in Java and using Selenium 2, this is the easiest way to use it:

    // start the proxy
    ProxyServer server = new ProxyServer(4444);
    server.start();

    // get the Selenium proxy object
    Proxy proxy = server.seleniumProxy();

    // configure it as a desired capability
    DesiredCapabilities capabilities = new DesiredCapabilities();
    capabilities.setCapability(CapabilityType.PROXY, proxy);

    // start the browser up
    WebDriver driver = new FirefoxDriver(capabilities);

    // create a new HAR with the label "yahoo.com"
    server.newHar("yahoo.com");

    // open yahoo.com
    driver.get("http://yahoo.com");

    // get the HAR data
    Har har = server.getHar();


HTTP Request Manipulation
-------------------

You can manipulate the requests like so:

    server.addRequestInterceptor(new RequestInterceptor() {
        @Override
        public void process(BrowserMobHttpRequest request, Har har) {
            request.getMethod().removeHeaders("User-Agent");
            request.getMethod().addHeader("User-Agent", "Bananabot/1.0");
        }
    });

You can also POST a JavaScript payload to `/:port/interceptor/request` and `/:port/interceptor/response` using the REST interface. The functions will have a `request`/`response` variable, respectively, and a `har` variable (which may be null if a HAR isn't set up yet). The JavaScript code will be run by [Rhino](https://github.com/mozilla/rhino) and have access to the same Java API in the example above:

    [~]$ curl -X POST -H 'Content-Type: text/plain' -d 'request.getMethod().removeHeaders("User-Agent");' http://localhost:9090/proxy/9091/interceptor/request

Consult the Java API docs for more info.

SSL Support
-----------

While the proxy supports SSL, it requires that a Certificate Authority be installed in to the browser. This allows the browser to trust all the SSL traffic coming from the proxy, which will be proxied using a classic man-in-the-middle technique. IT IS CRITICAL THAT YOU NOT INSTALL THIS CERTIFICATE AUTHORITY ON A BROWSER THAT IS USED FOR ANYTHING OTHER THAN TESTING.

If you're doing testing with Selenium, you'll want to make sure that the browser profile that gets set up by Selenium not only has the proxy configured, but also has the CA installed (Firefox set up by Selenium has installed CA by default). Unfortuantely, there is no API for doing this in Selenium, so you'll have to solve it uniquely for each browser type. We hope to make this easier in upcoming releases.

NodeJS Support
--------------

NodeJS bindings for browswermob-proxy are available [here](https://github.com/zzo/browsermob-node).  Built-in support for [Selenium](http://seleniumhq.org) or use [CapserJS-on-PhantomJS](http://casperjs.org) or anything else to drive traffic for HAR generation.
