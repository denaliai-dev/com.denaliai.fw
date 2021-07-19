package com.denaliai.fw.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.Recycler;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import java.util.ArrayList;
import java.util.List;

public class HttpClientRequest {
	private static final ResourceLeakDetector<HttpClientRequest> LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(HttpClientRequest.class);
	private static final Recycler<HttpClientRequest> RECYCLER = new Recycler<HttpClientRequest>() {
		@Override
		protected HttpClientRequest newObject(Handle<HttpClientRequest> handle) {
			return new HttpClientRequest(handle);
		}
	};

	private final Recycler.Handle<HttpClientRequest> m_handle;
	private ResourceLeakTracker<HttpClientRequest> m_tracker;
	private String m_method;
	private String m_url;

	private List<NameValue> m_queryParams;
	private List<NameValue> m_headers;

	private String m_stringData;
	private ByteBuf m_bbData;
	private List<NameValue> m_formParams;

	private Boolean m_followRedirect;
	private Integer m_requestTimeout;
	private Integer m_readTimeout;

	private IRequestSuccessHandler m_successHandler;
	private IRequestFailedHandler m_failedHandler;

	private HttpClientRequest(Recycler.Handle<HttpClientRequest> handle) {
		m_handle = handle;
	}

	public static HttpClientRequest create(String url) {
		return create("GET", url);
	}
	public static HttpClientRequest create(String method, String url) {
		HttpClientRequest req = RECYCLER.get();
		req.init(method, url);
		return req;
	}

	private void init(String method, String url) {
		m_tracker = LEAK_DETECT.track(this);
		m_method = method;
		m_url = url;
	}

	void recycle() {
		m_url = null;

		if (m_queryParams != null) {
			m_queryParams.clear();
		}
 		if (m_headers != null) {
		   m_headers.clear();
	   }

		m_stringData = null;
 		if (m_bbData != null) {
		   m_bbData.release();
		   m_bbData = null;
	   }
		if (m_formParams != null) {
			m_formParams.clear();
		}

		m_followRedirect = null;
		m_requestTimeout = null;
		m_readTimeout = null;

		m_successHandler = null;
		m_failedHandler = null;

		if (m_tracker != null) {
			m_tracker.close(this);
		}
		m_handle.recycle(this);
	}

	/**
	 * This handler is both a success and a failed handler wrapped into one.
	 * Just a convenience method but is mutually exclusive with onSuccess and onFailed
	 */
	public void onCompletionHandler(IRequestCompletionHandler completionHandler) {
		m_successHandler = completionHandler;
		m_failedHandler = completionHandler;
	}

	/**
	 * This handler is called when there is a successful HTTP conversation.
	 * It is mutually exclusive with onCompletionHandler
	 */
	public void onSuccess(IRequestSuccessHandler handler) {
		m_successHandler = handler;
	}
	public IRequestSuccessHandler getSuccessHandler() {
		return m_successHandler;
	}

	/**
	 * This handler is called when there is a failed HTTP conversation.
	 * It is mutually exclusive with onCompletionHandler
	 */
	public void onFailed(IRequestFailedHandler handler) {
		m_failedHandler = handler;
	}
	public IRequestFailedHandler getFailedHandler() {
		return m_failedHandler;
	}

	public String getMethod() {
		return m_method;
	}
	public String getURL() {
		return m_url;
	}
	public ByteBuf getBodyBuf() {
		return m_bbData;
	}
	public String getBodyData() {
		return m_stringData;
	}
	public List<NameValue> getQueryParams() {
		return m_queryParams;
	}
	public List<NameValue> getHeaders() {
		return m_headers;
	}
	public List<NameValue> getFormParams() {
		return m_formParams;
	}
	public Boolean followRedirect() {
		return m_followRedirect;
	}
	public Integer requestTimeout() {
		return m_requestTimeout;
	}
	public void requestTimeout(Integer timeout) {
		m_requestTimeout = timeout;
	}
	public Integer readTimeout() {
		return m_readTimeout;
	}
	public void readTimeout(int timeout) {
		m_readTimeout = timeout;
	}

	public void addQueryParam(String name, String value) {
		if (m_queryParams == null) {
			m_queryParams = new ArrayList<>();
		}
		m_queryParams.add(new NameValue(name, value));
	}

	public void addHeader(String name, String value) {
		if (m_headers == null) {
			m_headers = new ArrayList<>();
		}
		m_headers.add(new NameValue(name, value));
	}

	public void addOrReplaceHeader(String name, String value) {
		if (m_headers == null) {
			m_headers = new ArrayList<>();
			m_headers.add(new NameValue(name, value));
			return;
		}
		for(int i=0; i<m_headers.size(); i++) {
			NameValue nv = m_headers.get(i);
			if (nv.name.equals(name)) {
				m_headers.set(i, new NameValue(name, value));
				return;
			}
		}
		m_headers.add(new NameValue(name, value));
	}

	public void addFormParam(String name, String value) {
		if (m_formParams == null) {
			m_formParams = new ArrayList<>();
			addOrReplaceHeader(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());
		}
		m_formParams.add(new NameValue(name, value));
	}

	/**
	 * This passes the ref count into this class.  It will be released() once the request completes.
	 * After it is passed in, it will cause unpredictable results if it is modified.
	 */
	public void addBodyData(String contentType, ByteBuf data) {
		m_formParams = null;
		m_stringData = null;
		m_bbData = data;
		addOrReplaceHeader(HttpHeaderNames.CONTENT_TYPE.toString(), contentType);
	}

	public void addBodyData(String contentType, String data) {
		addBodyData(data);
		addOrReplaceHeader(HttpHeaderNames.CONTENT_TYPE.toString(), contentType);
	}

	public void addBodyData(String data) {
		m_formParams = null;
		m_stringData = data;
		if (m_bbData != null) {
			m_bbData.release();
			m_bbData = null;
		}
	}

	static class NameValue {
		final String name;
		final String value;

		NameValue(String name, String value) {
			this.name = name;
			this.value = value;
		}
	}

	public interface IRequestSuccessHandler {
		/**
		 * This does not indicate that the server responded a 200, it just means that there was a successful HTTP
		 * conversation.
		 */
		void requestComplete(HttpClientResponse response);
	}
	public interface IRequestFailedHandler {
		void requestFailed(Throwable cause);
	}
	public interface IRequestCompletionHandler extends IRequestSuccessHandler, IRequestFailedHandler{
	}
}
