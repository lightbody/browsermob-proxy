# MITM with LittleProxy
The MITM module is a LittleProxy-compatible module that enables man-in-the-middle interception of HTTPS requests. MITM allows you to:
- [Generate both RSA and EC private keys](#improving-performance-with-elliptic-curve-ec-cryptography) (EC provides a significant performance boost, ~50x faster than RSA)
- [Use a custom Certificate Authority](#using-a-custom-certificate-authority) (e.g. a corporate CA) to sign impersonated certificates, or generate (and optionally save) a new CA on-the-fly
- [Specify a custom trust store](#trusted-root-certificates-and-custom-trust-stores) on proxy-to-server connections, allowing the proxy to trust personal or corporate CAs
- [Use OpenSSL](#openssl-support), improving performance over Java's built-in TLS implementation

Though MITM is developed and distributed with BrowserMob Proxy, it has no dependency on BMP and can be used in a LittleProxy-only environment. The only additional dependency is the Bouncy Castle encryption library.

## Quick start
### LittleProxy (without BrowserMob Proxy)

To use MITM with standalone LittleProxy, add a dependency on the `mitm` module in your pom:

```xml
    <!-- existing LittleProxy dependency -->
    <dependency>
        <groupId>org.littleshoot</groupId>
        <artifactId>littleproxy</artifactId>
        <version>1.1.3</version>
    </dependency>
    
    <!-- new dependency on the MITM module -->
    <dependency>
        <groupId>net.lightbody.bmp</groupId>
        <artifactId>mitm</artifactId>
        <version>2.1.4</version>
    </dependency>
```

When creating your LittleProxy server, set the MitmManager to an instance of `net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager`:

```java
    HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap()
            .withManInTheMiddle(ImpersonatingMitmManager.builder().build());
```

The default implementation of `ImpersonatingMitmManager` will generate a new CA Root Certificate when the first request is made to the proxy. See below for instructions on saving the generated root certificate, or using your own root certificate and private key.

### BrowserMob Proxy
The MITM module is enabled by default with BrowserMob Proxy. No additional steps are required to enable MITM with BrowserMob Proxy. 

By default, BrowserMob Proxy will use the `ca-keystore-rsa.p12` file to load its CA Root Certificate and Private Key. The corresponding certificate file is `ca-certificate-rsa.cer`, which can be installed as a trusted Certificate Authority in browsers or other HTTP clients to avoid HTTPS warnings when using BrowserMob Proxy.

## Examples
Several examples are available to help you get started:

Example File | Configuration
-------------|--------------
[LittleProxyDefaultConfigExample.java](src/test/java/net/lightbody/bmp/mitm/example/CustomCAKeyStoreExample.java) | Default configuration with LittleProxy
[SaveGeneratedCAExample.java](src/test/java/net/lightbody/bmp/mitm/example/SaveGeneratedCAExample.java) | Save a dynamically-generated CA root certificate for installation in a browser
[CustomCAKeyStoreExample.java](src/test/java/net/lightbody/bmp/mitm/example/CustomCAKeyStoreExample.java) and [CustomCAPemFileExample.java](src/test/java/net/lightbody/bmp/mitm/example/CustomCAPemFileExample.java) | Use an existing CA certificate and private key
[EllipticCurveCAandServerExample.java](src/test/java/net/lightbody/bmp/mitm/example/EllipticCurveCAandServerExample.java) | Use EC cryptography when generating the CA private key and when impersonating server certificates

## Generating and Saving Root Certificates
By default, when using the MITM module with LittleProxy, the CA Root Certificate and Private Key are generated dynamically. The dynamically generated Root Certificate and Private Key can be saved for installation in a browser or later reuse by using the methods on the `RootCertificateGenerator` class. For example:

```java
    // create a CA Root Certificate using default settings
    RootCertificateGenerator rootCertificateGenerator = RootCertificateGenerator.builder().build();
    
    // save the newly-generated Root Certificate and Private Key -- the .cer file can be imported 
    // directly into a browser
    rootCertificateGenerator.saveRootCertificateAsPemFile(new File("/tmp/certificate.cer"));
    rootCertificateGenerator.savePrivateKeyAsPemFile(new File("/tmp/private-key.pem"), "password");
    
    // or save the certificate and private key as a PKCS12 keystore, for later use
    rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new File("/tmp/keystore.p12"), 
            "privateKeyAlias", "password");
    
    // tell the ImpersonatingMitmManager  use the RootCertificateGenerator we just configured
    ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
            .rootCertificateSource(rootCertificateGenerator)
            .build();
    
    // tell LittleProxy to use the ImpersonatingMitmManager when MITMing
    HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap()
            .withManInTheMiddle(mitmManager);
```

## Using a Custom Certificate Authority
Whether you are using the MITM module with LittleProxy or BrowserMob Proxy, you can provide your own root certificate and private key to use when signing impersonated server certificates. To use a root certificate and private key from a key store (PKCS12 or JKS), use the `KeyStoreFileCertificateSource` class:

```java
    CertificateAndKeySource existingCertificateSource = 
            new KeyStoreFileCertificateSource("PKCS12", new File("/path/to/keystore.p12", "privateKeyAlias", "password");

    // configure the MitmManager to use the custom KeyStore source
    ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
            .rootCertificateSource(existingCertificateSource)
            .build();

    // when using LittleProxy, use the .withManInTheMiddle method on the bootstrap:
    HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap()
            .withManInTheMiddle(mitmManager);
            
    // when using BrowserMob Proxy, use .setMitmManager() on the BrowserMobProxy object:
    BrowserMobProxy proxyServer = new BrowserMobProxyServer();
    proxyServer.setMitmManager(mitmManager);
```

You can also load the root certificate and private key from separate PEM-encoded files using the `PemFileCertificateSource` class, or create an implementation of `CertificateAndKeySource` that loads the certificate and private key from another source.

## Trusted Root Certificates and Custom Trust Stores
The MITM module trusts the Certificate Authorities in the JVM's default trust store, as well as a default list of trusted CAs derived from NSS/Firefox's list of trusted CAs (courtesy of the cURL team: https://curl.haxx.se/ca/cacert.pem).

To add your own CA to the list of root CAs trusted by the MITM module, use the `add()` methods in the `net.lightbody.bmp.mitm.TrustSource` class. Alternatively, it is possible to disable upstream server validation, but this is only recommended when testing. Examples:
```java
    // your root CA certificate(s) may be in a Java KeyStore, a PEM-encoded File or String, or an X509Certificate
    File pemEncodedCAFile = ...;
    TrustSource trustSource = TrustSource.defaultTrustSource().add(pemEncodedCAFile);

    // when using MITM+LittleProxy, use the trustAllServers() method, or set the TrustSource on the ImpersonatingMitmManager:
    ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
        .trustSource(trustSource) // use an explicit trust source, or:
        .trustAllServers(true) // do not validate servers' certificates
        .build();

    // when using BrowserMob Proxy, use the .setTrustSource() method:
    BrowserMobProxy proxyServer = new BrowserMobProxyServer();
    proxyServer.setTrustSource(trustSource);
    // or disable server certificate validation:
    proxyServer.setTrustAllServers(true);
```

## Improving Performance with Elliptic Curve (EC) Cryptography
By default, the certificates generated by the MITM module use RSA private keys for both impersonated server certificates and for generated CA root certificates. However, all modern browsers support Elliptic Curve Cryptography, which uses smaller key sizes. As a result, impersonated EC server certificates can be generated significantly faster (approximately 50x faster is common, typically <10ms per impersonated certificate).

The MITM module's RootCertificateGenerator can be configured to generate an EC root certificate for use with EC server certificates. If you are using your own CA root certificate
and private key, make sure to generate an EC private key if you intend to use impersonated EC server certificates. (Though it is possible to generate "hybrid"
server certificates with an EC key signed by an RSA CA, they are uncommon, and not all clients support them. In particular, Java clients and servers [before 8u92 do not support hybrid certificates.](https://bugs.openjdk.java.net/browse/JDK-8136442))

To generate EC certificates for impersonated servers, set the `serverKeyGenerator` to `ECKeyGenerator` in ImpersonatingMitmManager. To generate an EC root certificate and private key, set the `keyGenerator` to `ECKeyGenerator` in RootCertificateGenerator:

```java
    // create a RootCertificateGenerator that generates EC Certificate Authorities; you may also load your
    // own EC certificate and private key using any other CertificateAndKeySource implementation 
    // (KeyStoreFileCertificateSource, PemFileCertificateSource, etc.).
    CertificateAndKeySource rootCertificateGenerator = RootCertificateGenerator.builder()
            .keyGenerator(new ECKeyGenerator())
            .build();

    // tell the ImpersonatingMitmManager to generate EC keys and to use the EC RootCertificateGenerator
    ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
            .rootCertificateSource(rootCertificateGenerator)
            .serverKeyGenerator(new ECKeyGenerator())
            .build();
            
    // when using LittleProxy:
    HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap()
            .withManInTheMiddle(mitmManager);
            
    // when using BrowserMob Proxy:
    BrowserMobProxy proxy = new BrowserMobProxyServer();
    proxy.setMitmManager(mitmManager);
```

## OpenSSL support
The MITM module takes advantage of Netty's support for OpenSSL, allowing you to use OpenSSL instead of Java's built-in TLS implementation, which may provide
significant performance benefits. The MITM module itself requires no additional configuration to use OpenSSL: all you need is an OpenSSL installation and a dependency on the `netty-tcnative` library for your platform.
See Netty's OpenSSL instructions for details: http://netty.io/wiki/requirements-for-4.x.html#tls-with-openssl

## Acknowledgements
The MITM module would not have been possible without the efforts of Frank Ganske, the Zed Attack Proxy, and Brad Hill. Thank you for all your excellent work!
