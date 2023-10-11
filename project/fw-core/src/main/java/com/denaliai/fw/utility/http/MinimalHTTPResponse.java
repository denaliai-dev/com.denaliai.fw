package com.denaliai.fw.utility.http;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinimalHTTPResponse {
	final private Logger LOG;
	final private StringBuilder m_responseBuilder = new StringBuilder(8096);
	private String m_headersString;
	private Map<String,String> m_headers;

	public String body;

	public MinimalHTTPResponse(Logger logger) {
		if(logger == null) {
			LOG = LoggerFactory.getLogger(MinimalHTTPResponse.class.getName());
		} else {
			LOG = logger;
		}
	}

	public void appendToBuilder(char[] buffer, int numRead) {
		m_responseBuilder.append(buffer,0,numRead);
	}

	public void close() {
		// find headers
		for(int i=0; i<m_responseBuilder.length()-1; i++) {
			if (m_responseBuilder.charAt(i) == '\r'
							&& m_responseBuilder.charAt(i+1) == '\n'
							&& i+3 < m_responseBuilder.length()
							&& m_responseBuilder.charAt(i+2) == '\r'
							&& m_responseBuilder.charAt(i+3) == '\n'
			) {
				m_headersString = m_responseBuilder.substring(0, i + 4);
				body = m_responseBuilder.substring(i + 4);
				break;
			}
		}
		m_responseBuilder.setLength(0);
	}

	public String getHeader(String name) {
		if(m_headers == null) {
			parseHeadersString();
		}
		return m_headers.get(name);
	}

	private void parseHeadersString() {
		m_headers = new HashMap<>();
		if(m_headersString == null || m_headersString.length() == 0) {
			return;
		}
		final StringBuilder sb = new StringBuilder(256);
		for(int i = 0; i < m_headersString.length(); i++) {
			final char c = m_headersString.charAt(i);
			if(c != '\r' && c != '\n') {
				sb.append(c);
			} else if(c == '\n' && sb.length() > 0) {
				final String header = sb.toString();
				sb.setLength(0);
				int colonPos = header.indexOf(": ");
					if(colonPos != -1) {
						String name = header.substring(0, colonPos);
						String value = header.substring(colonPos+2);
						m_headers.put(name,value);
						if(LOG.isDebugEnabled()) {
							LOG.debug("parsed header '{}': '{}'", name, value);
						}
					}
			}
		}
	}
}
