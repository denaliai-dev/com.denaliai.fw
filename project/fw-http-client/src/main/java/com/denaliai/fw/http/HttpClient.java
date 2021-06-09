package com.denaliai.fw.http;

import com.denaliai.fw.Application;
import com.denaliai.fw.config.Config;
import com.denaliai.fw.metrics.*;
import com.denaliai.fw.utility.concurrent.PerpetualWork;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.asynchttpclient.*;
import org.asynchttpclient.netty.LazyResponseBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpClient {
	private static final Logger LOG = LoggerFactory.getLogger(HttpClient.class);

	private static final Integer Config_MaxConnections = Config.getFWInt("http.HttpClient.maxConnections", null);
	private static final Integer Config_MaxConnectionsPerHost = Config.getFWInt("http.HttpClient.maxConnectionsPerHost", null);
	private static final Boolean Config_KeepAlive = Config.getFWBoolean("http.HttpClient.keepAlive", null);

	private static final CounterMetric m_newRequests = MetricsEngine.newCounterMetric("HttpClient.new-requests");
	private static final DurationRateMetric m_successRequests = MetricsEngine.newRateMetric("HttpClient.successful-requests");
	private static final CounterMetric m_requestFailures = MetricsEngine.newCounterMetric("HttpClient.failed-requests");
	private static final ValueMetric m_requestDataSize = MetricsEngine.newValueMetric("HttpClient.request-content-bytes");
	private static final ValueMetric m_responseDataSize = MetricsEngine.newValueMetric("HttpClient.response-content-bytes");
	private static final TotalCounterMetric m_outstandingRequests = MetricsEngine.newTotalCounterMetric("HttpClient.outstanding-requests");

	private final AtomicInteger m_requestNum = new AtomicInteger();
	private final Worker m_worker = new Worker();
	private final Promise<Void> m_startPromise = Application.newPromise();
	private final Promise<Void> m_stopPromise = Application.newPromise();
	private volatile boolean m_requestStart;
	private volatile boolean m_requestStop;
	private Queue<HttpClientRequest> m_requestQueue = PlatformDependent.newMpscQueue();
	private AtomicInteger m_requestsDone = new AtomicInteger();
	private Integer m_maxConnectionsPerHost = Config_MaxConnectionsPerHost;
	private Boolean m_keepAlive = Config_KeepAlive;
	private Boolean m_useInsecureTrustManager;

	public HttpClient() {
	}

	public void useInsecureTrustManager(boolean value) {
		m_useInsecureTrustManager = value;
	}

	public void setMaxConnectionsPerHost(int num) {
		m_maxConnectionsPerHost = num;
	}

	public void setKeepAlive(boolean value) {
		m_keepAlive = value;
	}

	public Future<Void> start() {
		m_requestStart = true;
		m_worker.requestMoreWork();
		return m_startPromise;
	}

	public Future<Void> stop() {
		m_requestStop = true;
		m_worker.requestMoreWork();
		return m_stopPromise;
	}

	/**
	 * Once the HttpClientRequest is passed into this method it will cause unpredictable
	 * behavior to make any changes to it.
	 *
	 */
	public void submit(HttpClientRequest req) {
		m_requestQueue.add(req);
		m_worker.requestMoreWork();
	}

	private final class Worker extends PerpetualWork {
		private final Logger LOG = LoggerFactory.getLogger(Worker.class);
		private AsyncHttpClient m_client;
		private boolean m_started;
		private boolean m_stopped;
		private boolean m_stopWaitLogged;
		private int m_runningRequests;

		@Override
		protected void _doWork() {
			int numDone = m_requestsDone.getAndSet(0);
			m_runningRequests -= numDone;
			if (!m_stopped && m_requestStop) {
				if (m_runningRequests == 0) {
					stop();
				} else if (!m_stopWaitLogged) {
					m_stopWaitLogged = true;
					if (LOG.isDebugEnabled()) {
						LOG.debug("Waiting on {} outstanding requests", m_runningRequests);
					}
				}
			}
			if (!m_started && m_requestStart) {
				start();
			}
			try {
				processQueue();
			} catch (Exception ex) {
				LOG.error("Unhandled exception", ex);
			}
		}

		private void processQueue() {
			while(true) {
				HttpClientRequest req = m_requestQueue.poll();
				if (req == null) {
					return;
				}
				if (m_requestStop) {
					if (req.getFailedHandler() != null) {
						try {
							req.getFailedHandler().requestFailed(new HttpClientShutdownException());
						} catch(Exception ex) {
							LOG.error("Unexpected exception in user callback", ex);
						}
					}
					req.recycle();
					continue;
				}
				m_outstandingRequests.increment();
				m_newRequests.increment();
				m_runningRequests++;

				final BoundRequestBuilder clientReq;
				try {
					clientReq = m_client.prepareGet(req.getURL());

					clientReq.setMethod(req.getMethod());
					if (req.getQueryParams() != null) {
						for(HttpClientRequest.NameValue nv :  req.getQueryParams()) {
							clientReq.addQueryParam(nv.name, nv.value);
						}
					}
					if (req.getFormParams() != null) {
						for(HttpClientRequest.NameValue nv :  req.getFormParams()) {
							clientReq.addFormParam(nv.name, nv.value);
						}
					} else if (req.getBodyData() != null) {
						clientReq.setBody(req.getBodyData());
						m_requestDataSize.add(req.getBodyData().length());
					} else if (req.getBodyBuf() != null) {
						// Need to ensure we keep the req alive until the end of the HTTP request
						clientReq.setBody(req.getBodyBuf().nioBuffer());
						m_requestDataSize.add(req.getBodyBuf().readableBytes());
					}
					if (m_keepAlive == Boolean.TRUE) {
						clientReq.addHeader(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
					}
					if (req.getHeaders() != null) {
						for(HttpClientRequest.NameValue nv :  req.getHeaders()) {
							clientReq.addHeader(nv.name, nv.value);
						}
					}
					if (req.followRedirect()  != null) {
						clientReq.setFollowRedirect(req.followRedirect());
					}
					if (req.readTimeout() != null) {
						clientReq.setReadTimeout(req.readTimeout());
					}
					if (req.requestTimeout() != null) {
						clientReq.setRequestTimeout(req.requestTimeout());
					}
					//req.setRealm(realm)
					//req.setSignatureCalculator(signatureCalculator);

				} catch(Exception ex) {
					LOG.error("Unexpected exception", ex);
					if (req.getFailedHandler() != null) {
						try {
							req.getFailedHandler().requestFailed(ex);
						} catch(Exception ex2) {
							LOG.error("Unexpected exception in user callback", ex2);
						}
					}
					m_requestFailures.increment();
					m_outstandingRequests.decrement();
					m_runningRequests--;
					req.recycle();
					continue;
				}
				// I am splitting up the try/catch blocks in case we want to do something differently
				// in the second block given the exception was thrown by an async method
				try {
					final RequestHandler handler = new RequestHandler(req);

					if (LOG.isDebugEnabled()) {
						LOG.debug("[{}] Request start", handler.m_requestId);
					}

					// I am trusting that if req.execute throws an exception it is not in a bad state.  If it catches an
					// exception I am expecting it to call the future it returns.  If it allows an exception to be thrown
					// I trust that it means that it will not put anything into the future
					handler.setResponseFuture(clientReq.execute(handler));

				} catch(Exception ex) {
					LOG.error("Unexpected exception", ex);
					if (req.getFailedHandler() != null) {
						try {
							req.getFailedHandler().requestFailed(ex);
						} catch(Exception ex2) {
							LOG.error("Unexpected exception in user callback", ex2);
						}
					}
					m_requestFailures.increment();
					m_outstandingRequests.decrement();
					m_runningRequests--;
					req.recycle();
				}
			}
		}

		private void start() {
			m_started = true;
			try {
				_start();
				m_startPromise.setSuccess(null);
			} catch(Exception ex) {
				LOG.debug("start() exception", ex);
				m_startPromise.setFailure(ex);
			}
		}

		private void stop() {
			m_started = true; // If someone calls stop() we should never even try to start
			m_stopped = true;
			try {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Closing client");
				}
				m_client.close();
				m_stopPromise.setSuccess(null);
			} catch(Exception ex) {
				LOG.debug("stop() exception", ex);
				m_stopPromise.setFailure(ex);
			}
		}

		private void _start() {
			DefaultAsyncHttpClientConfig.Builder dsl = Dsl.config()
				.setEventLoopGroup(Application.getIOPool())
				.setAllocator(Application.allocator())
				.setResponseBodyPartFactory(AsyncHttpClientConfig.ResponseBodyPartFactory.LAZY);
			// .setTcpNoDelay(tcpNoDelay)
			// .setUserAgent(userAgent)
			// .setConnectionTtl(connectionTtl)
			// .setConnectTimeout(connectTimeout)
			//	.setKeepAliveStrategy(keepAliveStrategy)
			// .setFollowRedirect(followRedirect)
			// .setMaxRedirects(maxRedirects)
			// .setPooledConnectionIdleTimeout(pooledConnectionIdleTimeout)
			// .setReadTimeout(readTimeout)
			// .setSoLinger(soLinger)
			// .addIOExceptionFilter(ioExceptionFilter)  allow retry on I/O exceptions
			// .addResponseFilter(responseFilter) allow retry on certain HTTP statuses (or no HTTP status)
			// .setEnabledProtocols(enabledProtocols)
			// .setEnabledCipherSuites(enabledCipherSuites)
			// .setDisableHttpsEndpointIdentificationAlgorithm(disableHttpsEndpointIdentificationAlgorithm);
			// .setFilterInsecureCipherSuites(filterInsecureCipherSuites)
			// .setHandshakeTimeout(handshakeTimeout)
			// .setSslSessionTimeout(sslSessionTimeout)
			// .setStrict302Handling(strict302Handling)
			// .setUseInsecureTrustManager(useInsecureTrustManager)
			// .setUseNativeTransport(useNativeTransport)
			if (Config_MaxConnections != null) {
				dsl.setMaxConnections(Config_MaxConnections);
			}
			if (m_maxConnectionsPerHost != null) {
				dsl.setMaxConnectionsPerHost(m_maxConnectionsPerHost);
			}
			if (m_keepAlive != null) {
				dsl.setKeepAlive(m_keepAlive);
			}
			if (m_useInsecureTrustManager != null) {
				dsl.setUseInsecureTrustManager(m_useInsecureTrustManager);
			}

			if (LOG.isDebugEnabled()) {
				LOG.debug("Creating client");
			}
			m_client = Dsl.asyncHttpClient(dsl);
		}
	}


	private class RequestHandler extends AsyncCompletionHandlerBase implements Runnable, HttpClientResponse {
		private final int m_requestId = m_requestNum.incrementAndGet();
		private final HttpClientRequest m_request;
		private final MetricsEngine.IMetricTimer m_requestTimer;
		private ByteBuf m_responseBuf;
		private CompositeByteBuf m_responseCBuf;
		private ListenableFuture<Response> m_responseFuture;
		private Response m_response;

		RequestHandler(HttpClientRequest request) {
			m_request = request;
			m_requestTimer = MetricsEngine.startTimer();
		}

		void setResponseFuture(ListenableFuture<Response> responseFuture) {
			m_responseFuture = responseFuture;
			m_responseFuture.addListener(this, Application.getTaskPool());
		}

		/**
		 * Called when the request is completed from the response future
		 */
		@Override
		public void run() {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] Request complete", m_requestId);
			}
			try {
				try {
					m_response = m_responseFuture.get();
				} catch(Exception ex) {
					if (LOG.isDebugEnabled()) {
						LOG.info("HTTP processing exception", ex);
					}
					m_response = null;
				}
				// m_response could be null if there is no Http status (any kind of protocol failure)
				if (m_response == null) {
					if (m_request.getFailedHandler() != null) {
						try {
							m_request.getFailedHandler().requestFailed(new HttpRequestFailureException("A protocol failure or I/O exception occurred during the processing of the request, turn on debug logging for " + HttpClient.class.getCanonicalName() + " to get the details"));
						} catch(Exception ex2) {
							LOG.error("Unexpected exception in user callback", ex2);
						}
					} else {
						LOG.info("[{}] A protocol failure or I/O exception occurred during the processing of the request, turn on debug logging for {} to get the details", m_requestId, HttpClient.class.getCanonicalName());
					}
					m_requestFailures.increment();

				} else {
					if (m_responseBuf != null) {
						m_responseDataSize.add(m_responseBuf.readableBytes());
					} else if (m_responseCBuf != null) {
						m_responseDataSize.add(m_responseCBuf.readableBytes());
					}
					m_successRequests.record(m_requestTimer);
					if (m_request.getSuccessHandler() != null) {
						try {
							m_request.getSuccessHandler().requestComplete(this);
						} catch(Exception ex2) {
							LOG.error("Unexpected exception in user callback", ex2);
						}
					}
				}
			} catch (Exception ex) {
				LOG.error("Unexpected exception", ex);
			}
			m_outstandingRequests.decrement();
			m_requestsDone.incrementAndGet();
			m_worker.requestMoreWork();
			if (m_responseBuf != null) {
				m_responseBuf.release();
				m_responseBuf = null;
			} else if (m_responseCBuf != null) {
				m_responseCBuf.release();
				m_responseCBuf = null;
			}
			m_requestTimer.close();
			m_request.recycle();
		}

		@Override
		public State onBodyPartReceived(HttpResponseBodyPart content) {
			if (m_responseBuf != null) {
				m_responseCBuf = Application.allocateCompositeBuffer();
				m_responseCBuf.addComponent(true, m_responseBuf);
				m_responseBuf = null;
			}
			if (m_responseCBuf != null) {
				m_responseCBuf.addComponent(true, ((LazyResponseBodyPart) content).getBuf().retain());
			} else {
				m_responseBuf = ((LazyResponseBodyPart) content).getBuf().retain();
			}
			return State.CONTINUE;
		}

		@Override
		public void onThrowable(Throwable t) {
			// TODO Need to do what with this, is it returned in the m_responseFuture?
			// Yes it is.
		}

		@Override
		public int getHttpStatusCode() {
			return m_response.getStatusCode();
		}

		@Override
		public String getHttpStatusText() {
			return m_response.getStatusText();
		}

		@Override
		public ByteBuf getResponseData() {
			if (m_responseBuf != null) {
				return m_responseBuf;
			}
			return m_responseCBuf;
		}
	}

}
