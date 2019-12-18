package com.denaliai.fw.http;

import io.netty.buffer.ByteBuf;

public interface HttpClientResponse {
	int getHttpStatusCode();
	String getHttpStatusText();

	/**
	 * The response object owns a reference count on the buffer.  If you want to keep the buffer after
	 * releasing the response, you need to call retain before releasing this object.
	 *
	 * @return the response buffer instance or null if there is none
	 */
	ByteBuf getResponseData();
}
