package com.denaliai.fw.utility.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

import javax.net.SocketFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinimalHTTPRequest {

	private MinimalHTTPRequest() {
	}

	// TODO need to add optional headers to GET
	// TODO need to add POST

	public static String get(String urlString) {
		final Logger LOG = LogManager.getLogger(MinimalHTTPRequest.class.getName());
		final URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			LOG.error("", e);
			throw new IllegalArgumentException("Could not parse URL '" + urlString + "'", e);
		}
		return get(url);
	}

	public static String get(URL url) {
		if (url.getProtocol() == "https") {
			return MinimalHTTPSRequest.get(url);
		} else {
			final int port = (url.getPort() == -1) ? 80 : url.getPort();
			return get(url.getHost(), port, url.getFile());
		}
	}

	public static String get(String host, String file) {
		return get(host, 80, file);
	}

	public static String get(String host, int port, String file) {
		final Logger LOG = LogManager.getLogger(MinimalHTTPRequest.class.getName() + "." + host);
		final SocketFactory factory = SocketFactory.getDefault();
		return get(factory, host, port, file, 15000, LOG);
	}
	static String get(final SocketFactory factory, String host, int port, String file, final Logger LOG) {
		return get(factory, host, port, file, 15000, LOG);
	}
	static String get(final SocketFactory factory, String host, int port, String file, final int readTimeoutInMS, final Logger LOG) {
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
			final Socket socket = factory.createSocket(addr, port);
			socket.setSoTimeout(readTimeoutInMS);
			try {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Sending verb string and headers");
				}
				// Send request to the web server
				final OutputStream out = socket.getOutputStream();
				try {
					StringBuilder request = new StringBuilder();
					request.append("GET ").append(file).append(" HTTP/1.1\r\n");
					request.append("Host: ").append(host);
					if (port != 80) {
						request.append(':').append(port);
					}
					request.append("\r\n");
					request.append("Connection: close\r\n");
					request.append("\r\n");
					final byte[] requestBytes = request.toString().getBytes(Charset.forName("ASCII"));
					out.write(requestBytes);
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
