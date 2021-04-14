package com.denaliai.fw.utility.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

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
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPSRequest.class.getName() + "." + host);
		final SocketFactory factory = SSLSocketFactory.getDefault();
		return MinimalHTTPRequest.get(factory, host, port, file, LOG);
	}


	public static String post(String urlString, String contentType, String postedData) {
		return MinimalHTTPRequest.post(urlString, contentType, postedData);
	}
	public static String post(URL url, String contentType, String postedData) {
		if (url.getProtocol() == "http") {
			return MinimalHTTPRequest.post(url, contentType, postedData);
		} else {
			final int port = (url.getPort() == -1) ? 443 : url.getPort();
			return post(url.getHost(), port, url.getFile(), contentType, postedData);
		}
	}
	public static String post(String host, String file, String contentType, String postedData) {
		return post(host, 443, file, contentType, postedData);
	}
	public static String post(String host, int port, String file, String contentType, String postedData) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPSRequest.class.getName() + "." + host);
		final SocketFactory factory = SSLSocketFactory.getDefault();
		return MinimalHTTPRequest.post(factory, host, port, file, LOG, contentType, postedData);
	}
}
