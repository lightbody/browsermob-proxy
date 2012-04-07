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

Once that is done, a new proxy will be available on the port returned. All you have to do is point a browser to that proxy on that port and you should be able to browser the internet. The following additional APIs will then be available:

 - PUT /proxy/[port]/har - creates a new HAR attached to the proxy and returns the HAR content if there was a previous HAR. Supports the following parameters:
  - initialPageRef - the string name of the first page ref that should be used in the HAR. Defaults to "Page 1".
 - PUT /proxy/[port]/har/pageRef - starts a new page on the existing HAR. Supports the following parameters:
  - pageRef - the string name of the first page ref that should be used in the HAR. Defaults to "Page N" where N is the next page number.
 - PUT /proxy/[port]/har/pageRef - creates a new HAR attached to the proxy and returns the HAR content if there was a previous HAR
 - DELETE /proxy/[port] - shuts down the proxy and closes the port
 - GET /proxy/[port]/har - returns the JSON/HAR content representing all the HTTP traffic passed through the proxy
 - PUT /proxy/[port]/whitelist - Sets a list of URL patterns to whitelist. Takes the following parameters:
  - regex - a comma separated list of regular expressions
  - status - the HTTP status code to return for URLs that do not match the whitelist
 - PUT /proxy/[port]/blacklist - Set a URL to blacklist. Takes the following parameters:
  - regex - the blacklist regular expression
  - status - the HTTP status code to return for URLs that are blacklisted
 - PUT /proxy/[port]/limit - Limit the bandwidth through the proxy. Takes the following parameters:
  - downstreamKbps - Sets the downstream kbps
  - upstreamKbps - Sets the upstream kbps
  - latency - Add the given latency to each HTTP request

For example, once you've started the proxy you can create a new HAR to start recording data like so:

    [~]$ curl -X PUT -d 'initialPageRef=Foo' http://localhost:8080/proxy/9091/har

Now when traffic goes through port 9091 it will be attached to a page reference named "Foo". Consult the HAR specification for more info on what a "pageRef" is. You can also start a new pageRef like so:

    [~]$ curl -X PUT -d 'pageRef=Bar' http://localhost:8080/proxy/9091/har/pageRef

That will ensure no more HTTP requests get attached to the old pageRef (Foo) and start getting attached to the new pageRef (Bar). You can also get the HAR content at any time like so:

    [~]$ curl http://localhost:8080/proxy/9091/har

*TODO*: Other REST APIs supporting all the BrowserMob Proxy features will be added soon.

Embedded Mode
-------------

If you're using Java and Selenium, the easiest way to get started is to embed the project directly in your test. First, you'll need to make sure that all the dependencies are imported in to the project. You can find them in the *lib* directory. Or, if you're using Maven, you can add this to your pom:

    <dependency>
        <groupId>org.browsermob</groupId>
        <artifactId>browsermob-proxy</artifactId>
        <version>2.0-SNAPSHOT</version>
    </dependency>

*TODO*: We haven't yet released the artifacts to Maven's central repository, but we are working on it. The above will work as soon as it's ready.

Once done, you can start a proxy using `org.browsermob.proxy.ProxyServer`:

    ProxyServer server = new ProxyServer(9090);
    server.start();

This class supports every feature that the proxy supports. In fact, the REST API is a subset of the methods exposed here, so new features will show up here before they show up in the REST API. Consult the Javadocs for the full API.

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

While not yet available via the REST interface, you can manipulate the requests like so:

    server.addRequestInterceptor(new HttpRequestInterceptor() {
        @Override
        public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
            request.removeHeaders("User-Agent");
            request.addHeader("User-Agent", "Bananabot/1.0");
        }
    });

The interceptor is the type `org.apache.http.HttpRequestInterceptor`, which is part of the [Apache HTTP Client](http://hc.apache.org/httpcomponents-client-ga/) project. You can consult the API docs for the full set of options available to you in the interceptor.

We will soon be adding support for this advanced capability in the REST interface as well, using JavaScript snippets that can be posted as the interceptor code.

SSL Support
-----------

While the proxy supports SSL, it requires that a Certificate Authority be installed in to the browser. This allows the browser to trust all the SSL traffic coming from the proxy, which will be proxied using a classic man-in-the-middle technique. IT IS CRITICAL THAT YOU NOT INSTALL THIS CERTIFICATE AUTHORITY ON A BROWSER THAT IS USED FOR ANYTHING OTHER THAN TESTING.

If you're doing testing with Selenium, you'll want to make sure that the browser profile that gets set up by Selenium not only has the proxy configured, but also has the CA installed. Unfortuantely, there is no API for doing this in Selenium, so you'll have to solve it uniquely for each browser type. We hope to make this easier in upcoming releases.

NodeJS Support
--------------

NodeJS bindings for browswermob-proxy are available [here](https://github.com/zzo/browsermob-node).  Built-in support for [Selenium](http://seleniumhq.com) or use [CapserJS-on-PhantomJS](http://casperjs.org) or anything else to drive traffic for HAR generation.
