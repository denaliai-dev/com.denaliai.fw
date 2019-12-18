package com.denaliai.fw.utility.http;

import java.net.URL;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinimalHTTPSRequest {

	private MinimalHTTPSRequest() {
	}

	public static String get(String urlString) {
		return MinimalHTTPRequest.get(urlString);
	}

	public static String get(URL url) {
		if (url.getProtocol() == "http") {
			return MinimalHTTPRequest.get(url);
		} else {
			final int port = (url.getPort() == -1) ? 443 : url.getPort();
			return get(url.getHost(), port, url.getFile());
		}
	}

	public static String get(String host, String file) {
		return get(host, 443, file);
	}

	public static String get(String host, int port, String file) {
		final Logger LOG = LogManager.getLogger(MinimalHTTPSRequest.class.getName() + "." + host);
		final SocketFactory factory = SSLSocketFactory.getDefault();
		return MinimalHTTPRequest.get(factory, host, port, file, LOG);
	}
}
