package com.denaliai.fw.http;

import com.denaliai.fw.Application;
import com.denaliai.fw.config.Config;
import com.denaliai.fw.metrics.*;
import com.denaliai.fw.utility.concurrent.PerpetualWork;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpServer {
	private static final int SERVER_BACKLOG = Config.getFWInt("http.HttpServer.defaultServerSocketBacklog", 128);
	private static final int MAX_CONTENT_SIZE = Config.getFWInt("http.HttpServer.maxContentSize", 512*1024);
	private static final String ACCESS_CONTROL_ALLOW_ORIGIN = Config.getFWString("http.HttpServer.accessControlAllowOrigin", null);
	private static final String ACCESS_CONTROL_ALLOW_METHODS = Config.getFWString("http.HttpServer.accessControlAllowMethods", null);
	private static final String ACCESS_CONTROL_ALLOW_HEADERS = Config.getFWString("http.HttpServer.accessControlAllowHeaders", null);
	private static final AttributeKey<Connection> CONNECTION = AttributeKey.newInstance("ConnectionClass");

	private final CounterAndRateMetric m_newConnections;
	private final CounterAndRateMetric m_disconnections;
	private final CounterAndRateMetric m_newRequests;
	private final CounterMetric m_numExceptions;
	private final CounterMetric m_earlyDisconnects;
	private final TotalCounterMetric m_listenerActive;
	private final TotalCounterMetric m_activeConnections;
	private final DurationRateMetric m_requestRate;
	private final ValueMetric m_requestDataSize;
	private final ValueMetric m_responseDataSize;

	private final Logger LOG;
	private final ServerBootstrap m_serverBootstrap = new ServerBootstrap();
	private final ConnectionInboundMsgHandler m_connectionMsgHandler = new ConnectionInboundMsgHandler();
	private final ByteCounterMsgHandler m_childSocketByteCounter = new ByteCounterMsgHandler();

	private final IConnectHandler m_connectHandler;
	private final IDisconnectHandler m_disconnectHandler;
	private final IFailureHandler m_failureHandler;
	private final IRequestHandler m_requestHandler;
	private final Promise<Void> m_registerDone = Application.getTaskPool().next().newPromise();
	private final int m_httpPort;
	private final int m_readTimeoutInMS;

	private final AtomicInteger m_httpServerRefCount = new AtomicInteger();
	private Promise<Void> m_startDonePromise;
	private volatile Promise<Void> m_stopDonePromise;
	private boolean m_isStarted = false;
	private Channel m_serverConnection;
	private enum ServerState {Offline, Registering, Binding, BoundListening}
	private volatile ServerState m_serverState;

	private HttpServer(HttpServerBuilder builder) {
		LOG = LoggerFactory.getLogger(HttpServer.class.getCanonicalName() + "." + builder.m_loggerNameSuffix);
		if (LOG.isTraceEnabled()) {
			LOG.trace("Setting up handlers");
		}
		m_connectHandler = (builder.m_connectHandler != null) ? builder.m_connectHandler : new NullHandler(LOG);
		m_disconnectHandler = (builder.m_disconnectHandler != null) ? builder.m_disconnectHandler : new NullHandler(LOG);
		m_failureHandler = (builder.m_failureHandler != null) ? builder.m_failureHandler : new NullHandler(LOG);
		m_requestHandler = (builder.m_requestHandler != null) ? builder.m_requestHandler : new NullHandler(LOG);
		m_readTimeoutInMS = builder.m_readTimeoutInMS;
		m_httpPort = builder.m_httpPort;

		if (LOG.isTraceEnabled()) {
			LOG.trace("Creating metrics");
		}
		m_listenerActive = MetricsEngine.newTotalCounterMetric(builder.m_loggerNameSuffix + ".listening");
		m_newConnections = MetricsEngine.newCounterAndRateMetric(builder.m_loggerNameSuffix + ".new-connections");
		m_disconnections = MetricsEngine.newCounterAndRateMetric(builder.m_loggerNameSuffix + ".disconnections");
		m_newRequests = MetricsEngine.newCounterAndRateMetric(builder.m_loggerNameSuffix + ".new-requests");
		m_numExceptions = MetricsEngine.newCounterMetric(builder.m_loggerNameSuffix + ".exception-count");
		m_activeConnections = MetricsEngine.newTotalCounterMetric(builder.m_loggerNameSuffix + ".active-connections");
		m_requestRate = MetricsEngine.newRateMetric(builder.m_loggerNameSuffix + ".success-request-rate");
		m_requestDataSize = MetricsEngine.newValueMetric(builder.m_loggerNameSuffix + ".request-bytes");
		m_responseDataSize = MetricsEngine.newValueMetric(builder.m_loggerNameSuffix + ".response-bytes");
		m_earlyDisconnects = MetricsEngine.newCounterMetric(builder.m_loggerNameSuffix + ".early-disconnect");

		init();
	}

	private void init() {
		if (LOG.isTraceEnabled()) {
			LOG.trace("init() - start");
		}
		m_serverBootstrap.group(Application.getIOPool(), Application.getIOPool())
			.channel(NioServerSocketChannel.class)
			.handler(new ServerSocketHandler())
			.option(ChannelOption.ALLOCATOR, Application.allocator())
			.option(ChannelOption.SO_BACKLOG, SERVER_BACKLOG)
			.childHandler(new MyChannelInitializer())
			.childOption(ChannelOption.ALLOCATOR, Application.allocator())
			.childOption(ChannelOption.SO_KEEPALIVE, true);

		if (LOG.isTraceEnabled()) {
			LOG.trace("Registering server");
		}
		m_serverState = ServerState.Registering;
		m_serverBootstrap.register().addListener((regFuture) -> {
			if (regFuture.isSuccess()) {
				if (LOG.isTraceEnabled()) {
					LOG.trace("Server register successful");
				}
				m_registerDone.setSuccess(null);
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Server register failed");
				}
				m_registerDone.setFailure(regFuture.cause());
			}
		});
		if (LOG.isTraceEnabled()) {
			LOG.trace("init() - end");
		}
	}

	public synchronized Future<Void> start() {
		if (m_isStarted) {
			throw new IllegalStateException("Server already started");
		}
		m_isStarted = true;

		// start/stop maintain a ref count to the server
		serverRetain();

		m_stopDonePromise = null;
		m_startDonePromise = Application.getTaskPool().next().newPromise();
		m_registerDone.addListener((regDone) -> {
			if (!regDone.isSuccess()) {
				LOG.warn("register() of listening socket failed, HttpServer cannot start", regDone.cause());
				m_serverState = ServerState.Offline;
				m_startDonePromise.setFailure(regDone.cause());
				serverRelease();
				return;
			}
			m_serverState = ServerState.Binding;
			final ChannelFuture f;
			try {
				f = m_serverBootstrap.bind(m_httpPort);
			} catch(Throwable t) {
				LOG.warn("Call to bind({}) failed, HttpServer cannot start", m_httpPort, t);
				m_serverState = ServerState.Offline;
				m_startDonePromise.setFailure(t);
				serverRelease();
				return;
			}
			f.addListener(new BindListener());
		});
		return m_startDonePromise;
	}

	public synchronized Future<Void> stop() {
		if (m_isStarted == false) {
			return Application.newSucceededFuture();
		}
		if (m_stopDonePromise != null) {
			return m_stopDonePromise;
		}
		m_stopDonePromise = Application.getTaskPool().next().newPromise();
		m_stopDonePromise.addListener(f -> {
			// Set this so start() can be called again
			m_isStarted = false;
		});

		m_startDonePromise.addListener((startFuture) -> {
			if (!startFuture.isSuccess()) {
				// Server didn't start right
				m_stopDonePromise.setFailure(startFuture.cause());
				return;
			}
			try {
				Channel serverConnection = m_serverConnection;
				if (serverConnection != null) {
					// Shut down listening
					serverConnection.close();
					m_serverConnection = null;
				}
			} finally {
				// start/stop maintain a ref count to the server
				serverRelease();
			}
		});

		return m_stopDonePromise;
	}

	private void serverRetain() {
		m_httpServerRefCount.incrementAndGet();
	}

	private void serverRelease() {
		boolean lastConnection = (m_httpServerRefCount.decrementAndGet() == 0);
		if (lastConnection && m_stopDonePromise != null) {
			m_stopDonePromise.trySuccess(null);
		}
	}

	private class BindListener implements ChannelFutureListener {


		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (future.isSuccess()) {
				if (LOG.isDebugEnabled()) {
					LOG.info("[{}] Channel bound", future.channel());
				}
			} else {
				// TODO add a retry timer when a bind failure because port is in use.
				// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
				// it might just be a lazy cleanup of the socket from a previous run
				m_serverState = ServerState.Offline;
				m_serverConnection = null;

				//future.channel().close(); No need to close, it never opened?
//					NeuronApplication.logError(LOG, "Bind to port {} failed, taking neuron offline", m_port, cause);
//					StatusSystem.setInboundStatus(m_statusHostAndPort, StatusType.Down, statusText);
				if (LOG.isDebugEnabled()) {
					LOG.info("Exception during bind", future.cause());
				} else {
					LOG.info("Exception during bind: {}", future.cause().getMessage());
				}
				m_startDonePromise.tryFailure(future.cause());
				serverRelease();
			}
		}

	}


	private class MyChannelInitializer extends ChannelInitializer<SocketChannel> {
//		This is never called
//		@Override
//		public void channelActive(ChannelHandlerContext ctx) throws Exception {

//		This is never called
//		@Override
//		public void channelInactive(ChannelHandlerContext ctx) throws Exception {

		@Override
		public void initChannel(SocketChannel childChannel) {
			final String remoteHostAddress = childChannel.remoteAddress().toString();

			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] MyChannelInitializer.initChannel() - start", remoteHostAddress);
			} else if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] connected", remoteHostAddress);
			}

			ServerState serverState = m_serverState;
			// We might get here before the server state has transitioned from Binding to Online... if we got here
			// then we are bound so its ok that housekeeping hasn't caught up yet
			if (serverState != ServerState.Binding && serverState != ServerState.BoundListening) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("[{}] Channel rejected due to server state {}", remoteHostAddress, serverState);
				}
				childChannel.close();
				return;
			}
			final ChannelPipeline pipeline = childChannel.pipeline();
			pipeline.addLast(m_childSocketByteCounter);
//			if (m_sslContext != null) {
//				pipeline.addLast("sslEngine", m_sslContext.newHandler(childChannel.alloc()));
//			}
			if (LOG.isTraceEnabled()) {
				pipeline.addLast("data-logger", new DataLogHandler(LOG));
			}
			pipeline.addLast("read-timeout", new ReadTimeoutHandler(m_readTimeoutInMS, TimeUnit.MILLISECONDS));
			pipeline.addLast("codec", new HttpServerCodec(/*4096, 8192, 8192*/)); // TODO: make these configurable
			if (!LOG.isTraceEnabled()) {
				// Need to prevent compressing so we can log the uncompressed buffers
				pipeline.addLast("compressor", new HttpContentCompressor());
			}
			pipeline.addLast("keepAlive", new HttpServerKeepAliveHandler());
			pipeline.addLast("aggregator", new HttpObjectAggregator(MAX_CONTENT_SIZE, true));
			pipeline.addLast(m_connectionMsgHandler);

			final Connection c = new Connection(remoteHostAddress, childChannel);
			final Connection prevConnection = childChannel.attr(CONNECTION).getAndSet(c);
			if (prevConnection != null) {
				LOG.error("There was a previous connection!! This should never happen");
			}
			c.callOnConnect();

			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] MyChannelInitializer.initChannel() - end", remoteHostAddress);
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] MyChannelInitializer.exceptionCaught()", ctx.channel().remoteAddress().toString());
			}
			super.exceptionCaught(ctx, cause);
		}
	}


	@ChannelHandler.Sharable
	private class ByteCounterMsgHandler extends ChannelDuplexHandler {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (msg instanceof ByteBuf) {
				m_requestDataSize.add(((ByteBuf)msg).readableBytes());
			}
			super.channelRead(ctx, msg);
		}

		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			if (msg instanceof ByteBuf) {
				m_responseDataSize.add(((ByteBuf)msg).readableBytes());
			}
			super.write(ctx, msg, promise);
		}
	}

	@ChannelHandler.Sharable
	private class ConnectionInboundMsgHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			final Connection conn = ctx.channel().attr(CONNECTION).get();
			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] channelRead()", conn.remoteHostAddress());
			}

			if (msg instanceof FullHttpRequest) {
				// This can be called multiple times if multiple requests are sent on the same connection!!
				FullHttpRequest req = (FullHttpRequest)msg;
				m_newRequests.increment();

				if (!req.decoderResult().isSuccess()) {
					if (LOG.isDebugEnabled()) {
						LOG.error("[{}] HTTP decoder failed", ctx.channel().remoteAddress().toString(), req.decoderResult().cause());
						conn.callOnFailure(req.decoderResult().cause());
						req.release();
						ctx.close();
						return;
					}
				}

				// TODO THIS IS NEEDED FOR SENDING MULTIPLE REQUESTS ON THE SAME CONNECTION <---------------------------------------------------------------------------------------------------------------------
				// We have the full message, no need for timeout anymore
				//ctx.pipeline().remove("read-timeout");

				if (LOG.isDebugEnabled()) {
					final StringBuilder sb = new StringBuilder();
					final HttpHeaders headers = req.headers();
					if (!headers.isEmpty()) {
						sb.append("\r\n");
						for (Map.Entry<String, String> h: headers) {
							CharSequence key = h.getKey();
							CharSequence value = h.getValue();
							sb.append('\t').append(key).append(" = ").append(value).append("\r\n");
						}
					}
					LOG.info("{} {} {}{}", req.method(), req.protocolVersion(), req.uri(), sb);
				}
				conn.callOnRequest(req);
			} else {
				ReferenceCountUtil.safeRelease(msg);
				if (conn == null) {
					if (LOG.isDebugEnabled()) {
						LOG.error("[{}] ConnectionInboundInitHandler got an unexpected message of type {}", ctx.channel().remoteAddress().toString(), msg.getClass().getCanonicalName());
					}
				} else {
					if (LOG.isDebugEnabled()) {
						LOG.error("[{}] ConnectionInboundInitHandler got an unexpected message of type {}", conn.remoteHostAddress(), msg.getClass().getCanonicalName());
					}
					conn.callOnFailure(new RuntimeException("Unexpected message type"));
				}
				ctx.close();
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			m_numExceptions.increment();

			final Connection conn = ctx.channel().attr(CONNECTION).get();
			if (conn == null) {
				if (LOG.isDebugEnabled()) {
					LOG.error("[{}] ConnectionInboundMsgHandler.exceptionCaught() - CONNECTION attribute was missing", ctx.channel().remoteAddress().toString(), cause);
				}
				ctx.close();
				return;
			}
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] ConnectionInboundMsgHandler.exceptionCaught()", conn.remoteHostAddress(), cause);
			}
			conn.callOnFailure(cause);
			ctx.close();
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] ConnectionInboundMsgHandler.channelActive()", ctx.channel().remoteAddress().toString());
			}
			final Connection conn = ctx.channel().attr(CONNECTION).get();
			conn.setContext(ctx);

			super.channelActive(ctx);
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] ConnectionInboundMsgHandler.channelInactive()", ctx.channel().remoteAddress().toString());
			}
			final Connection conn = ctx.channel().attr(CONNECTION).getAndSet(null);
			if (conn == null) {
				if (LOG.isDebugEnabled()) {
					LOG.error("[{}] MyChannelInitializer.channelInactive() - CONNECTION attribute was missing", ctx.channel().remoteAddress().toString());
				}
			} else {
				conn.callOnDisconnect();
				if (LOG.isDebugEnabled()) {
					LOG.debug("[{}] disconnected", conn.remoteHostAddress());
				}
			}
			m_disconnections.increment();
			m_activeConnections.decrement();

			// This releases the child socket's "use" of the server
			serverRelease();

			super.channelInactive(ctx);
		}

	}

	@ChannelHandler.Sharable
	private class ServerSocketHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] ServerSocketHandler.channelRead(): {} {}", ctx.channel(), msg.getClass().getName(), msg.toString());
			}
			if (msg instanceof NioSocketChannel) {
				// This increments our child socket's "use" of the server
				serverRetain();

				m_newConnections.increment();
				m_activeConnections.increment();
			} else {
				throw new RuntimeException("This shouldn't happen");
			}
			super.channelRead(ctx, msg);
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] ServerSocketHandler.userEventTriggered(): {}", ctx.channel(), evt.toString());
			}
			super.userEventTriggered(ctx, evt);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] ServerSocketHandler.channelActive(), serverState {}", ctx.channel(), m_serverState);
			}
//					if (m_deinitializing) {
//						return;
//					}
			// The server socket (listening socket) maintains a ref to the HttpServer
			serverRetain();
			m_listenerActive.increment();

			m_serverState = ServerState.BoundListening;
			m_serverConnection = ctx.channel();

			LOG.info("Listening for HTTP requests on port {}", m_httpPort);
//			StatusSystem.setInboundStatus(m_statusHostAndPort, StatusType.Up, "Listening");
			m_startDonePromise.setSuccess(null);

			super.channelActive(ctx);
		}

		/*
			We assume that we will never get a channelInactive unless channelActive was called first
		 */
		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] ServerSocketHandler.channelInactive(), serverState {}", ctx.channel(), m_serverState);
			}

			// Channel has closed
			m_serverState = ServerState.Offline;

			// We only add a reference to the HttpServer once we go active, but we never did
			serverRelease();
			m_listenerActive.decrement();

//			NeuronApplication.logInfo(LOG, "Listener closed");
//			StatusSystem.setInboundStatus(m_statusHostAndPort, StatusType.Down, "Not listening, neuron deinitialized");

			super.channelInactive(ctx);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] ServerSocketHandler.exceptionCaught(), serverState {}", ctx.channel(), m_serverState, cause);
			}
			m_numExceptions.increment();
			if (m_serverState == ServerState.Binding) {
				LOG.info("[{}] Exception binding socket, calling close", ctx.channel(), cause);
				ctx.close();
				// TODO restart bind?
			}
		}
	}

	private static final Object m_onConnect = new Object();
	private static final Object m_onDisconnect = new Object();

	// TODO consider making this recylced
	// TODO need to increment/decrement a latch-like thing to keep the server running until this guy is all done doing his thing
	private final class Connection extends PerpetualWork implements ISocketConnection, IHttpRequest, IHttpResponse {
		private final Queue<Object> m_msgQueue = PlatformDependent.newMpscQueue();
		private final String m_connectionToString;
		private Channel m_channel;

		private ChannelHandlerContext m_context;
		private MetricsEngine.IMetricTimer m_requestTimer;
		private HttpVersion m_httpRequestProtocolVersion;
		private HttpMethod m_httpRequestMethod;
		private FullHttpRequest m_httpRequest;
		private Map<String,String> m_responseHeaders;

		Connection(String connectionToString, Channel channel) {
			m_connectionToString = connectionToString;
			m_channel = channel;
		}

		void callOnConnect() {
			m_msgQueue.add(m_onConnect);
			requestMoreWork();
		}
		void setContext(ChannelHandlerContext context) {
			m_msgQueue.add(context);
			requestMoreWork();
		}
		void callOnRequest(FullHttpRequest httpRequest) {
			m_msgQueue.add(httpRequest);
			requestMoreWork();
		}
		void callOnDisconnect() {
			m_msgQueue.add(m_onDisconnect);
			requestMoreWork();
		}
		void callOnFailure(Throwable cause) {
			m_msgQueue.add(cause);
			requestMoreWork();
		}

		@Override
		public String remoteHostAddress() {
			return m_connectionToString;
		}

		/*
			IHttpRequest
		*/
		@Override
		public String requestMethod() {
			return m_httpRequestMethod.name();
		}

		@Override
		public String requestURI() {
			return m_httpRequest.uri();
		}

		@Override
		public String headerValue(String name) {
			return m_httpRequest.headers().get(name);
		}

		@Override
		public ByteBuf data() {
			return m_httpRequest.content();
		}

		/*
			IHttpResponse
		*/

		@Override
		public void addHeader(String key, String value) {
			if (m_responseHeaders == null) {
				m_responseHeaders = new HashMap<>();
			}
			m_responseHeaders.put(key, value);
		}

		@Override
		public void respondOk(ByteBuf data) {
			respond(HttpResponseStatus.OK, data);
		}

		@Override
		public void respond(HttpResponseStatus httpResponseStatus, ByteBuf data) {
			DefaultFullHttpResponse response = new DefaultFullHttpResponse(m_httpRequestProtocolVersion, httpResponseStatus, data);
			HttpHeaders headers = response.headers();
			headers.add(HttpHeaderNames.CONTENT_LENGTH, data.readableBytes());

			String secFetchMode = m_httpRequest.headers().get("sec-fetch-mode");
			if ("cors".equals(secFetchMode)) {
				if (ACCESS_CONTROL_ALLOW_ORIGIN != null) {
					headers.add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, ACCESS_CONTROL_ALLOW_ORIGIN);
				}
				if (ACCESS_CONTROL_ALLOW_METHODS != null) {
					headers.add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, ACCESS_CONTROL_ALLOW_METHODS);
				}
				if (ACCESS_CONTROL_ALLOW_HEADERS != null) {
					headers.add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, ACCESS_CONTROL_ALLOW_HEADERS);
				}
			}
			if (m_responseHeaders != null) {
				for(Map.Entry<String,String> e : m_responseHeaders.entrySet()) {
					headers.add(e.getKey(), e.getValue());
				}
				m_responseHeaders = null;
			}
			m_msgQueue.add(response);
			requestMoreWork();
		}

		@Override
		public String toString() {
			return m_connectionToString;
		}

		private void startRequest(FullHttpRequest request) {
			m_httpRequest = request;
			m_httpRequestProtocolVersion = m_httpRequest.protocolVersion();
			m_httpRequestMethod = m_httpRequest.method();
			m_requestTimer = MetricsEngine.startTimer();
		}

		private void endRequest() {
			ReferenceCountUtil.safeRelease(m_httpRequest);
			m_httpRequest = null;
			if (m_requestTimer != null) {
				m_requestTimer.close();
				m_requestTimer = null;
			}
		}

		private void clearConnection() {
			m_channel = null;
			m_context = null;
		}

		@Override
		protected void _doWork() {
			while (true) {
				Object msg = m_msgQueue.poll();
				if (msg == null) {
					break;
				}
				if (msg == m_onConnect) {
					try {
						m_connectHandler.onConnect(this);
					} catch (Throwable t) {
						LOG.warn("Uncaught exception from " + m_connectHandler.getClass().getName(), t);
					}

				} else if (msg == m_onDisconnect) {
					try {
						m_disconnectHandler.onDisconnect(this);
					} catch (Throwable t) {
						LOG.warn("Uncaught exception from " + m_disconnectHandler.getClass().getName(), t);
					}
					clearConnection();

				} else if (msg instanceof ChannelHandlerContext) {
					m_context = (ChannelHandlerContext) msg;

				} else if (msg instanceof FullHttpRequest) {
					startRequest((FullHttpRequest)msg);
					// The user may hold onto the objects passed in and choose to reply later.  If the user DOES NOT
					// call one of the respond() methods in IHttpResponse we will leak the request
					try {
						m_requestHandler.onRequest(this, this);
					} catch (Throwable t) {
						LOG.warn("Uncaught exception from " + m_requestHandler.getClass().getName(), t);
					}

				} else if (msg instanceof DefaultFullHttpResponse) {
					DefaultFullHttpResponse response = (DefaultFullHttpResponse) msg;
					if (m_context == null) {
						// The client disconnected, simply throw away the response
						response.release();
						m_earlyDisconnects.increment();

					} else {
						if (LOG.isDebugEnabled()) {
							LOG.debug("[{}] writeAndFlush({})", m_connectionToString, response.status().code());
						}
						// Since this happens only once in a Connection and connections are created PER request,
						// its ok to use the lambda here since it would be a wash to create a class to wrap it
						final MetricsEngine.IMetricTimer requestTimer = m_requestTimer;
						m_requestTimer = null;

						final boolean shuttingDown = (m_stopDonePromise != null);
						// If we are shutting down, need to make sure we close the connection (after this request is sent)
						if (shuttingDown) {
							response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
						}
						m_context.writeAndFlush(response).addListener((f) -> {
							if (requestTimer == null) {
								return;
							}
							if (f.isSuccess()) {
								m_requestRate.record(requestTimer);
							}
							requestTimer.close();
						});
						// We are not going to trust the client to close the connection, so we will after we flush
						if (shuttingDown) {
							m_context.close();
						}
					}
					endRequest();

				} else if (msg instanceof Throwable) {
					// The user may hold onto the objects passed in and choose to reply later
					// TODO need to handle breaking the association when we close this associated connection <---------------------------------------------------------------
					try {
						m_failureHandler.onFailure((Throwable) msg, this);
					} catch (Throwable t) {
						LOG.warn("Uncaught exception from " + m_failureHandler.getClass().getName(), t);
					}

				}
			}
		}
	}

	public static HttpServerBuilder builder() {
		return new HttpServerBuilder();
	}

	public static final class HttpServerBuilder {
		private IConnectHandler m_connectHandler;
		private IDisconnectHandler m_disconnectHandler;
		private IFailureHandler m_failureHandler;
		private IRequestHandler m_requestHandler;
		private int m_httpPort = Config.getFWInt("http.HttpServer.defaultHttpPort", 80);
		private int m_readTimeoutInMS = Config.getFWInt("http.HttpServer.readTimeoutMS", 5000);
		private String m_loggerNameSuffix;


		public HttpServerBuilder loggerNameSuffix(String name) {
			m_loggerNameSuffix = name;
			return this;
		}

		public HttpServerBuilder onConnect(IConnectHandler connectHandler) {
			m_connectHandler = connectHandler;
			return this;
		}
		public HttpServerBuilder onDisconnect(IDisconnectHandler disconnectHandler) {
			m_disconnectHandler = disconnectHandler;
			return this;
		}
		public HttpServerBuilder onFailure(IFailureHandler failureHandler) {
			m_failureHandler = failureHandler;
			return this;
		}
		public HttpServerBuilder onRequest(IRequestHandler requestHandler) {
			m_requestHandler = requestHandler;
			return this;
		}
		public HttpServerBuilder listenPort(int port) {
			m_httpPort = port;
			return this;
		}
		public HttpServer build() {
			if (m_loggerNameSuffix == null) {
				m_loggerNameSuffix = "Http" + m_httpPort;
			}
			return new HttpServer(this);
		}
	}

	public interface IHttpHeader {
		String name();
		String value();
	}
	public interface IHttpRequest {
		String remoteHostAddress();
		String requestMethod();
		String requestURI();
		String headerValue(String name);
		ByteBuf data();
	}

	public interface ISocketConnection {
		String remoteHostAddress();
	}
	public interface IHttpResponse {
		void addHeader(String key, String value);
		void respondOk(ByteBuf data);
		void respond(HttpResponseStatus httpResponseStatus, ByteBuf data);
	}
	public interface IConnectHandler {
		void onConnect(ISocketConnection connection);
	}
	public interface IDisconnectHandler {
		void onDisconnect(ISocketConnection connection);
	}
	public interface IFailureHandler {
		void onFailure(Throwable cause, ISocketConnection connection);
	}
	public interface IRequestHandler {
		void onRequest(IHttpRequest request, IHttpResponse response);
	}

	private static final class NullHandler implements IRequestHandler, IFailureHandler, IConnectHandler, IDisconnectHandler {
		private final Logger LOG;

		NullHandler(Logger parentLogger) {
			LOG = LoggerFactory.getLogger(parentLogger.getName() + ".NullHandler");
		}

		@Override
		public void onConnect(ISocketConnection connection) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] onConnect()", connection.remoteHostAddress());
			}
		}

		@Override
		public void onDisconnect(ISocketConnection connection) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] onDisconnect()", connection.remoteHostAddress());
			}
		}

		@Override
		public void onFailure(Throwable cause, ISocketConnection connection) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] onFailure()", connection.remoteHostAddress(), cause);
			}
		}

		@Override
		public void onRequest(IHttpRequest request, IHttpResponse response) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] onRequest()", request.remoteHostAddress());
			}
			response.respondOk(Application.allocateIOBuffer());
		}
	}
}
