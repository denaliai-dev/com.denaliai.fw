package com.denaliai.fw.utility.http;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinimalHTTPResponse {
	final private Logger LOG;
	final private StringBuilder m_responseBuilder = new StringBuilder(8096);
	final private StringBuilder m_rawBuilder = new StringBuilder(8096);
	final private Map<String,String> m_headers = new HashMap<>();

	public String rawResponse;
	public String body;
	public Throwable error;
	public int code = -1;

	public MinimalHTTPResponse(Logger logger) {
		if(logger == null) {
			LOG = LoggerFactory.getLogger(MinimalHTTPResponse.class.getName());
		} else {
			LOG = logger;
		}
	}
	public MinimalHTTPResponse(Logger logger, Exception error) {
		this(logger);
		this.error = error;
	}

	public void appendToBuilder(char[] buffer, int numRead) {
		m_rawBuilder.append(buffer,0, numRead);
		m_responseBuilder.append(buffer,0, numRead);
	}

	public void close() {
		rawResponse = m_rawBuilder.toString();
		body = m_responseBuilder.toString();
		m_responseBuilder.setLength(0);
	}

	public String getHeader(String name) {
		return m_headers.get(name);
	}

	public void processFirstLine(String firstLine) {
		//HTTP-Version SP Status-Code SP Reason-Phrase CRLF
		final String[] parts = firstLine.split(" ");
		code = Integer.parseInt(parts[1]);
	}

	public void processHeader(String headerLine) {
		if(headerLine.length() == 0) {
			return;
		}
		final int colonPos = headerLine.indexOf(": ");
		if(colonPos == -1) {
			return;
		}
		String name = headerLine.substring(0, colonPos);
		String value = headerLine.substring(colonPos+2);
		m_headers.put(name.toLowerCase(),value);
		if(LOG.isDebugEnabled()) {
			LOG.debug("parsed header '{}': '{}'", name, value);
		}
	}

	public void processChunks(InputStream is, StringBuilder traceBuilder) throws Exception {
		final StringBuilder sb = new StringBuilder();
		while(true) {
			final char c = (char)is.read();
			m_rawBuilder.append(c);
			if(LOG.isTraceEnabled()) {
				traceBuilder.append(c);
			}
			if (c == '\r') {
				continue;
			}
			if(c == '\n') {
				if(sb.length() == 0) {
					continue;
				}
				final int size = Integer.parseInt(sb.toString(),16);
				sb.setLength(0);
				if(size == 0) {
					close();
					return;
				}

				final byte[] buf = new byte[size];
				final int numRead = is.read(buf);
				if (numRead != size) {
					if(LOG.isTraceEnabled()) {
						LOG.error("failed at:\n{}", traceBuilder.toString());
					}
					throw new Exception("Error in processing, size != remaining bytes");
				}
				m_responseBuilder.append(new String(buf));
				continue;
			}
			sb.append(c);
		}
	}

	public void addToRaw(int b) {
		m_rawBuilder.append((char)b);
	}
}
