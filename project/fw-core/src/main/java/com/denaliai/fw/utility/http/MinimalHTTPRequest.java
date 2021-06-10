package com.denaliai.fw.utility.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.net.SocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinimalHTTPRequest {

	private MinimalHTTPRequest() {
	}

	public static String get(String urlString) {
		return get(urlString, (Map<String, String>)null);
	}
	public static String get(String urlString, final Map<String, String> headers) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName());
		final URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			LOG.error("", e);
			throw new IllegalArgumentException("Could not parse URL '" + urlString + "'", e);
		}
		return get(url, headers);
	}
	public static String get(URL url) {
		return get(url, null);
	}
	public static String get(URL url, final Map<String, String> headers) {
		if (url.getProtocol().equals("https")) {
			return MinimalHTTPSRequest.get(url, headers);
		} else {
			final int port = (url.getPort() == -1) ? 80 : url.getPort();
			return get(url.getHost(), port, url.getFile(), headers);
		}
	}
	public static String get(String host, String file) {
		return get(host, 80, file);
	}
	public static String get(String host, String file, final Map<String, String> headers) {
		return get(host, 80, file, headers);
	}
	public static String get(String host, int port, String file) {
		return get(host, port, file, null);
	}
	public static String get(String host, int port, String file, final Map<String, String> headers) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName() + "." + host);
		final SocketFactory factory = SocketFactory.getDefault();
		return execute(factory, "GET", host, port, file, 15000, LOG, null, headers, null);
	}
	static String get(final SocketFactory factory, String host, int port, String file, final Logger LOG) {
		return execute(factory, "GET", host, port, file, 15000, LOG, null, null, null);
	}
	static String get(final SocketFactory factory, String host, int port, String file, final Map<String, String> headers, final Logger LOG) {
		return execute(factory, "GET", host, port, file, 15000, LOG, null, headers, null);
	}

	public static String post(String urlString, String contentType, String postedData) {
		return post(urlString, contentType, (Map<String, String>)null, postedData);
	}
	public static String post(String urlString, String contentType, final Map<String, String> headers, String postedData) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName());
		final URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			LOG.error("", e);
			throw new IllegalArgumentException("Could not parse URL '" + urlString + "'", e);
		}
		return post(url, contentType, postedData);
	}
	public static String post(URL url, String contentType, String postedData) {
		if (url.getProtocol().equals("https")) {
			return MinimalHTTPSRequest.post(url, contentType, postedData);
		} else {
			final int port = (url.getPort() == -1) ? 80 : url.getPort();
			return post(url.getHost(), port, url.getFile(), contentType, postedData);
		}
	}
	public static String post(String host, String file, String contentType, String postedData) {
		return post(host, file, contentType, postedData);
	}
	public static String post(String host, String file, String contentType, final Map<String, String> headers, String postedData) {
		return post(host, 80, file, contentType, headers, postedData);
	}
	public static String post(String host, int port, String file, String contentType, String postedData) {
		return post(host, port, file, contentType, null, postedData);
	}
	public static String post(String host, int port, String file, String contentType, final Map<String, String> headers, String postedData) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName() + "." + host);
		final SocketFactory factory = SocketFactory.getDefault();
		return execute(factory, "POST", host, port, file, 15000, LOG, contentType, headers, postedData);
	}
	static String post(final SocketFactory factory, String host, int port, String file, final Logger LOG, String contentType, String postedData) {
		return execute(factory, "POST", host, port, file, 15000, LOG, contentType, null, postedData);
	}
	static String post(final SocketFactory factory, String host, int port, String file, final Logger LOG, String contentType, final Map<String, String> headers, String postedData) {
		return execute(factory, "POST", host, port, file, 15000, LOG, contentType, headers, postedData);
	}


	static String execute(final SocketFactory factory, String method, String host, int port, String file, final int readTimeoutInMS, final Logger LOG, final String contentType, final Map<String, String> headers, final String postedData) {
		final StringBuilder responseBuilder = new StringBuilder(8096);
		final InetAddress addr;
		try {
			addr = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			LOG.error("", e);
			throw new IllegalArgumentException("Could not resolve host '" + host + "'", e);
		}
		try {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Connecting to {} on port {}", host, port);
			}
			final Socket socket = factory.createSocket();
			socket.setSoTimeout(readTimeoutInMS);

			// Send request to the web server
			socket.connect(new InetSocketAddress(addr, port), readTimeoutInMS);
			try {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Sending verb string and headers");
				}
				final OutputStream out = socket.getOutputStream();
				try {
					final byte[] postedDataBytes = (postedData == null) ? null : postedData.getBytes(StandardCharsets.UTF_8);

					StringBuilder request = new StringBuilder();
					request.append(method).append(" ").append(file).append(" HTTP/1.1\r\n");
					request.append("Host: ").append(host);
					if (port != 80) {
						request.append(':').append(port);
					}
					request.append("\r\n");
					if (headers != null) {
						for(Map.Entry<String, String> e : headers.entrySet()) {
							request.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
						}
					}
					if (postedData != null) {
						request.append("Content-Type: ").append(contentType).append("\r\n");
						request.append("Content-Length: ").append(postedDataBytes.length).append("\r\n");
					}
					request.append("Connection: close\r\n");
					request.append("\r\n");
					final byte[] requestBytes = request.toString().getBytes(Charset.forName("ASCII"));
					out.write(requestBytes);
					if (postedDataBytes != null) {
						out.write(postedDataBytes);
					}
					out.flush();

					if (LOG.isDebugEnabled()) {
						LOG.debug("Reading response");
					}
					// Read response
					final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), Charset.forName("UTF-8")));
					try {
						final char[] buffer = new char[4096];
						while(true) {
							final int numRead = in.read(buffer);
							if (numRead == -1) {
								break;
							}
							responseBuilder.append(buffer, 0, numRead);
						}
						// Now find and skip headers
						for(int i=0; i<responseBuilder.length()-1; i++) {
							if (responseBuilder.charAt(i) == '\r'
									&& responseBuilder.charAt(i+1) == '\n'
									&& i+3 < responseBuilder.length()
									&& responseBuilder.charAt(i+2) == '\r'
									&& responseBuilder.charAt(i+3) == '\n'
									) {
								responseBuilder.delete(0, i+4);
							}
						}
					} finally {
						in.close();
					}
				} finally {
					out.close();
				}
			} finally {
				socket.close();
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug("Done");
			}
		} catch(Throwable t) {
			LOG.error("", t);
		}

		return responseBuilder.toString();
	}



}
