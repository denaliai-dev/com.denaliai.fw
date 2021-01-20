package com.denaliai.fw.http;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class HttpServerUtils {

	public static String jvmCiphers() throws NoSuchAlgorithmException {
		SSLContext sslContext = SSLContext.getDefault();
		return jvmCiphers(sslContext);
	}

	public static String jvmCiphers(SSLContext sslContext) {
		SSLEngine sslEngine = sslContext.createSSLEngine();
		SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();

		String[] defaultCiphers = ssf.getDefaultCipherSuites();
		String[] availableCiphers = sslEngine.getSupportedCipherSuites();

		TreeMap<String,Boolean> ciphers = new TreeMap<>();

		for(int i=0; i<availableCiphers.length; ++i )
			ciphers.put(availableCiphers[i], Boolean.FALSE);

		for(int i=0; i<defaultCiphers.length; ++i )
			ciphers.put(defaultCiphers[i], Boolean.TRUE);

		StringBuilder sb = new StringBuilder();
		sb.append("Default\tCipher\n");
		for(Iterator<Map.Entry<String,Boolean>> i = ciphers.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry<String,Boolean> cipher=(Map.Entry<String,Boolean>)i.next();

			if(Boolean.TRUE.equals(cipher.getValue()))
				sb.append('*');
			else
				sb.append(' ');

			sb.append('\t');
			sb.append(cipher.getKey());
			sb.append('\n');
		}
		return sb.toString();
	}

	static SSLContext createServerSSLContext(String sslProtocol, String serverKeyStoreFileName, String serverKeyStorePassword, final String storeFileProtocol, final String keyAlgorithm) {
		return createServerSSLContext(sslProtocol, serverKeyStoreFileName, serverKeyStorePassword.toCharArray(), storeFileProtocol, keyAlgorithm, new TrustManager[0]);
	}

	/*
	 * Create an SSLContext for the server using the server's JKS. This instructs the server to
	 * present its certificate when clients connect over HTTPS.
	 */
	static SSLContext createServerSSLContext(final String sslProtocol, final String serverKeyStoreFileName, final char[] password, final String storeFileProtocol, final String keyAlgorithm, TrustManager[] serverTrustManagers) {
		final SSLContext sslContext;
		try {
			final KeyStore serverKeyStore = getStore(serverKeyStoreFileName, password, storeFileProtocol);
			final KeyManager[] serverKeyManagers = getKeyManagers(serverKeyStore, keyAlgorithm, password);

			sslContext = SSLContext.getInstance(sslProtocol);
			//sslContext.init(serverKeyManagers, serverTrustManagers, new SecureRandom());
			sslContext.init(serverKeyManagers, serverTrustManagers, null);

		} catch(Exception ex) {
			throw new IllegalArgumentException("Could not configure SSL context", ex);
		}
		return sslContext;
	}

	/**
	 * KeyStores provide credentials, TrustStores verify credentials.
	 *
	 * Server KeyStores stores the server's private keys, and certificates for corresponding public keys.
	 * Used here for HTTPS connections over localhost.
	 *
	 * Client TrustStores store servers' certificates.
	 */
	private static KeyStore getStore(final String storeFileName, final char[] password, final String storeFileProtocol) throws Exception {
		final KeyStore store = KeyStore.getInstance(storeFileProtocol);
		try(InputStream in = new FileInputStream(storeFileName)) {
			store.load(in, password);
		}
		return store;
	}

	/**
	 * KeyManagers decide which authentication credentials (e.g. certs) should be sent to the remote host
	 * for authentication during the SSL handshake.
	 *
	 * Server KeyManagers use their private keys during the key exchange algorithm and send certificates
	 * corresponding to their public keys to the clients.  The certificate comes from the KeyStore.
	 *
	 */
	private static KeyManager[] getKeyManagers(KeyStore store, String keyAlgorithm, final char[] password) throws Exception {
		final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyAlgorithm);
		keyManagerFactory.init(store, password);
		return keyManagerFactory.getKeyManagers();
	}
}
