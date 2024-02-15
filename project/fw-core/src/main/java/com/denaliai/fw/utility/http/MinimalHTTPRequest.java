package com.denaliai.fw.utility.http;

import java.io.BufferedReader;
import java.io.InputStream;
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

	public static MinimalHTTPResponse get(String urlString) {
		return get(urlString, (Map<String, String>)null);
	}
	public static MinimalHTTPResponse get(String urlString, final Map<String, String> headers) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName());
		final URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			if(LOG.isDebugEnabled()) {
				LOG.error("", e);
			}
			return new MinimalHTTPResponse(LOG, new IllegalArgumentException("Could not parse URL '" + urlString + "'", e));
		}
		return get(url, headers);
	}
	public static MinimalHTTPResponse get(URL url) {
		return get(url, null);
	}
	public static MinimalHTTPResponse get(URL url, final Map<String, String> headers) {
		if (url.getProtocol().equals("https")) {
			return MinimalHTTPSRequest.get(url, headers);
		} else {
			final int port = (url.getPort() == -1) ? 80 : url.getPort();
			return get(url.getHost(), port, url.getFile(), headers);
		}
	}
	public static MinimalHTTPResponse get(String host, String file) {
		return get(host, 80, file);
	}
	public static MinimalHTTPResponse get(String host, String file, final Map<String, String> headers) {
		return get(host, 80, file, headers);
	}
	public static MinimalHTTPResponse get(String host, int port, String file) {
		return get(host, port, file, null);
	}
	public static MinimalHTTPResponse get(String host, int port, String file, final Map<String, String> headers) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName());
		final SocketFactory factory = SocketFactory.getDefault();
		return execute(factory, "GET", host, port, file, 15000, LOG, null, headers, null);
	}
	static MinimalHTTPResponse get(final SocketFactory factory, String host, int port, String file, final Logger LOG) {
		return execute(factory, "GET", host, port, file, 15000, LOG, null, null, null);
	}
	static MinimalHTTPResponse get(final SocketFactory factory, String host, int port, String file, final Map<String, String> headers, final Logger LOG) {
		return execute(factory, "GET", host, port, file, 15000, LOG, null, headers, null);
	}

	public static MinimalHTTPResponse post(String urlString, String contentType, String postedData) {
		return post(urlString, contentType, (Map<String, String>)null, postedData);
	}
	public static MinimalHTTPResponse post(String urlString, String contentType, final Map<String, String> headers, String postedData) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName());
		final URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			if(LOG.isDebugEnabled()) {
				LOG.error("", e);
			}
			return new MinimalHTTPResponse(LOG, new IllegalArgumentException("Could not parse URL '" + urlString + "'", e));
		}
		return post(url, contentType, postedData);
	}
	public static MinimalHTTPResponse post(URL url, String contentType, String postedData) {
		if (url.getProtocol().equals("https")) {
			return MinimalHTTPSRequest.post(url, contentType, postedData);
		} else {
			final int port = (url.getPort() == -1) ? 80 : url.getPort();
			return post(url.getHost(), port, url.getFile(), contentType, postedData);
		}
	}
	public static MinimalHTTPResponse post(String host, String file, String contentType, String postedData) {
		return post(host, file, contentType, postedData);
	}
	public static MinimalHTTPResponse post(String host, String file, String contentType, final Map<String, String> headers, String postedData) {
		return post(host, 80, file, contentType, headers, postedData);
	}
	public static MinimalHTTPResponse post(String host, int port, String file, String contentType, String postedData) {
		return post(host, port, file, contentType, null, postedData);
	}
	public static MinimalHTTPResponse post(String host, int port, String file, String contentType, final Map<String, String> headers, String postedData) {
		final Logger LOG = LoggerFactory.getLogger(MinimalHTTPRequest.class.getName());
		final SocketFactory factory = SocketFactory.getDefault();
		return execute(factory, "POST", host, port, file, 15000, LOG, contentType, headers, postedData);
	}
	static MinimalHTTPResponse post(final SocketFactory factory, String host, int port, String file, final Logger LOG, String contentType, String postedData) {
		return execute(factory, "POST", host, port, file, 15000, LOG, contentType, null, postedData);
	}
	static MinimalHTTPResponse post(final SocketFactory factory, String host, int port, String file, final Logger LOG, String contentType, final Map<String, String> headers, String postedData) {
		return execute(factory, "POST", host, port, file, 15000, LOG, contentType, headers, postedData);
	}


	static MinimalHTTPResponse execute(final SocketFactory factory, String method, String host, int port, String file, final int readTimeoutInMS, final Logger LOG, final String contentType, final Map<String, String> headers, final String postedData) {
		final InetAddress addr;
		MinimalHTTPResponse response = new MinimalHTTPResponse(LOG);
		try {
			addr = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			if(LOG.isDebugEnabled()) {
				LOG.error("", e);
			}
			response.error = new IllegalArgumentException("Could not resolve host '" + host + "'", e);
			return response;
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
					final InputStream is = socket.getInputStream();
					final StringBuilder sb = new StringBuilder();
					int breakCount = 0;
					boolean first = true;
					try {
						while(true) {
							int b = is.read();
							if(b == -1) {
								response.error = new Exception("Stream ended before headers");
								return response;
							}
							response.addToRaw(b);
							if(b == '\r') {
								continue;
							}
							if(b == '\n') {
								if(sb.length() == 0) {
									break;
								}
								if(first) {
									first = false;
									response.processFirstLine(sb.toString());
								} else {
									response.processHeader(sb.toString());
								}
								sb.setLength(0);
							} else {
								sb.append((char)b);
							}
						}

						if("chunked".equals(response.getHeader("transfer-encoding"))) {
							final StringBuilder traceBuilder = new StringBuilder();
							try{
								response.processChunks(is, traceBuilder);
							} catch (Throwable t) {
								if(LOG.isDebugEnabled()) {
									LOG.error("Failed parsing chunk", t);
									LOG.error("at:\n'{}'",traceBuilder);
								}
								response.error = t;
							}
						} else {
							final BufferedReader in = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
							try {
								final char[] buffer = new char[4096];
								while(true) {
									final int numRead = in.read(buffer);
									if (numRead == -1) {
										break;
									}
									response.appendToBuilder(buffer, numRead);
								}

								response.close();
							} finally {
								in.close();
							}
						}
					} finally {
						is.close();
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
			if(LOG.isDebugEnabled()) {
				LOG.error("host={} addr={}", host, addr, t);
			}
			response.error = t;
		}

		return response;
	}
}
