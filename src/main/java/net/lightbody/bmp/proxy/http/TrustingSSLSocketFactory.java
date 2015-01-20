package net.lightbody.bmp.proxy.http;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import net.lightbody.bmp.proxy.util.TrustEverythingSSLTrustManager;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.protocol.HttpContext;
import org.java_bandwidthlimiter.StreamManager;

public class TrustingSSLSocketFactory extends SSLConnectionSocketFactory {

	public enum SSLAlgorithm {
        SSLv3,
        TLSv1
    }

    private static SSLContext sslContext;
    private StreamManager streamManager;

    static {
        sslContext = SSLContexts.createDefault();
		try {
			sslContext = SSLContexts.custom().loadTrustMaterial(null, 
				new TrustStrategy() {
					@Override
				    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				        return true;
				    }
				}
			).build();
			
			sslContext.init(null, new TrustManager[]{new TrustEverythingSSLTrustManager()}, null);
		} catch (KeyManagementException e) {
			throw new RuntimeException("Unexpected key management error", e);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Unexpected key management error", e);
		} catch (KeyStoreException e) {
			throw new RuntimeException("Unexpected key management error", e);
		}
    }

    public TrustingSSLSocketFactory(StreamManager streamManager) {
    	this(new AllowAllHostnameVerifier(), streamManager);
    }
    
    public TrustingSSLSocketFactory(X509HostnameVerifier hostnameVerifier, StreamManager streamManager) {
    	 super(sslContext, hostnameVerifier);
         assert streamManager != null;
         this.streamManager = streamManager;
    }
    
    @Override
    public Socket createSocket(HttpContext context) throws IOException {
    	//creating an anonymous class deriving from socket
        //we just need to override methods for connect to get some metrics
        //and get-in-out streams to provide throttling
        Socket newSocket = new SimulatedSocket(streamManager);
        SimulatedSocketFactory.configure(newSocket);
		return newSocket;
    }
    
    @Override
    public Socket createLayeredSocket(final Socket socket, final String target, final int port, final HttpContext context) throws IOException {
    	SSLSocket sslSocket = (SSLSocket) super.createLayeredSocket(socket, target, port, context);
//    	sslSocket.setEnabledProtocols(new String[] { SSLAlgorithm.SSLv3.name(), SSLAlgorithm.TLSv1.name() } );
//    	sslSocket.setEnabledCipherSuites(new String[] { "SSL_RSA_WITH_RC4_128_MD5" });
    	return sslSocket;
    }
    
    @Override
    /**
     * This function is call just before the handshake
     * 
     * @see http://hc.apache.org/httpcomponents-client-ga/httpclient/xref/org/apache/http/conn/ssl/SSLConnectionSocketFactory.html
     */
    protected void prepareSocket (SSLSocket socket) throws IOException {
	    socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
	    	private final Date handshakeStart = new Date();
	    	
	    	@Override
	    	public void handshakeCompleted(HandshakeCompletedEvent handshakeCompletedEvent) {
	    		if(handshakeStart != null) {
    	       		RequestInfo.get().ssl(handshakeStart, new Date());
	    		}
	    	}
	    });
    }
}