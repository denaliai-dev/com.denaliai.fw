package com.denaliai.fw.utility.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinimalHTTPResponse {
	final private Logger LOG;
	final private StringBuilder m_responseBuilder = new StringBuilder(8096);
	final private Map<String,String> m_headers = new HashMap<>();

	public String body;

	public MinimalHTTPResponse(Logger logger) {
		if(logger == null) {
			LOG = LoggerFactory.getLogger(MinimalHTTPResponse.class.getName());
		} else {
			LOG = logger;
		}
	}

	public void appendToBuilder(char[] buffer, int numRead) {
		m_responseBuilder.append(buffer,0, numRead);
	}

	public void close() {
		body = m_responseBuilder.toString();
		m_responseBuilder.setLength(0);
	}

	public String getHeader(String name) {
		return m_headers.get(name);
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

	public void processChunks(InputStream is, StringBuilder traceBuilder) throws IOException {
		final StringBuilder sb = new StringBuilder();
		while(true) {
			final char c = (char)is.read();
			if(LOG.isTraceEnabled()) {
				traceBuilder.append(c);
			}
			if(c == '\r') {
				final int size = Integer.parseInt(sb.toString(),16);
				if(size == 0) {
					close();
					return;
				}

				for(int i = 0; i < size; i++) {
					final char c2 = (char) is.read();
					m_responseBuilder.append(c2);
				}

				is.read(); // \r
				is.read(); // \n
				continue;
			}
			sb.append(c);
		}
	}
}
