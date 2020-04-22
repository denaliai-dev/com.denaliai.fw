package com.denaliai.fw.socket;

import com.denaliai.fw.Application;
import com.denaliai.fw.config.Config;
import com.denaliai.fw.metrics.*;
import com.denaliai.fw.utility.Pipe;
import com.denaliai.fw.utility.concurrent.PerpetualWork;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SocketClient {
	private static final AttributeKey<Connection> CONNECTION = AttributeKey.newInstance("ClientCC");

	private final CounterAndRateMetric m_newConnections;
	private final TotalCounterMetric m_activeConnections;
	private final CounterMetric m_numExceptions;
	private final ValueMetric m_readDataSize;
	private final ValueMetric m_writeDataSize;

	private final Logger LOG;
	private final Bootstrap m_clientBootstrap = new Bootstrap();

	private final IConnectHandler m_connectHandler;
	private final IDisconnectHandler m_disconnectHandler;
	private final IFailureHandler m_failureHandler;
	private final IDataHandler m_requestHandler;
	private final String m_host;
	private final int m_port;
	private final int m_readTimeoutInMS;

	private final AtomicInteger m_clientRefCount = new AtomicInteger();
	private Promise<Void> m_startDonePromise;
	private Promise<Void> m_stopDonePromise;
	private Channel m_clientConnection;
	private enum ClientState {Offline, Registering, Connecting, Connected}
	private volatile ClientState m_clientState = ClientState.Offline;

	private SocketClient(SocketClientBuilder builder) {
		LOG = LoggerFactory.getLogger(SocketServer.class.getCanonicalName() + "." + builder.m_loggerNameSuffix);
		m_connectHandler = (builder.m_connectHandler != null) ? builder.m_connectHandler : new NullHandler(LOG);
		m_disconnectHandler = (builder.m_disconnectHandler != null) ? builder.m_disconnectHandler : new NullHandler(LOG);
		m_failureHandler = (builder.m_failureHandler != null) ? builder.m_failureHandler : new NullHandler(LOG);
		m_requestHandler = (builder.m_dataHandler != null) ? builder.m_dataHandler : new NullHandler(LOG);
		m_host = builder.m_host;
		m_port = builder.m_port;
		m_readTimeoutInMS = builder.m_readTimeoutInMS;

		// The user needs to be careful about creating multiple instances of this
		m_newConnections = MetricsEngine.newCounterAndRateMetric(builder.m_loggerNameSuffix + ".new-connections");
		m_numExceptions = MetricsEngine.newCounterMetric(builder.m_loggerNameSuffix + ".exception-count");
		m_activeConnections = MetricsEngine.newTotalCounterMetric(builder.m_loggerNameSuffix + ".connection-active");
		m_readDataSize = MetricsEngine.newValueMetric(builder.m_loggerNameSuffix + ".inbound-bytes");
		m_writeDataSize = MetricsEngine.newValueMetric(builder.m_loggerNameSuffix + ".outbound-bytes");

		init();
	}

	private void init() {
		m_clientBootstrap.group(Application.getIOPool())
			.channel(NioSocketChannel.class)
			.handler(new SocketHandler())
			.option(ChannelOption.ALLOCATOR, Application.allocator())
			.option(ChannelOption.SO_KEEPALIVE, true)
			.remoteAddress(m_host, m_port);
	}

	public synchronized Future<Void> start() {
		if (m_stopDonePromise != null && !m_stopDonePromise.isDone()) {
			return Application.newFailedFuture(new IllegalStateException("Attempt to start the server before stop() is complete"));
		}
		// start/stop maintains a ref count to the client
		clientRetain();

		m_stopDonePromise = null;
		m_startDonePromise = Application.getTaskPool().next().newPromise();

		if (LOG.isDebugEnabled()) {
			LOG.debug("Connecting to {}:{}", m_host, m_port);
		}
		// Start the registration process. Does not have to be complete before we call start()
		m_clientState = ClientState.Registering;
		m_clientBootstrap.connect().addListener((f) -> {
			// On success, our SocketHandler class will be called... so we only need to do something on failure.
			if (!f.isSuccess()) {
				// In this condition, the handler will only have channelRegistered/channelUnregistered called (if at all)
				LOG.warn("register() or connect() of socket {}:{} failed, SocketClient cannot start", m_host, m_port, f.cause());
				m_clientState = ClientState.Offline;
				m_startDonePromise.tryFailure(f.cause());
			}
		});
		return m_startDonePromise;
	}

	public synchronized Future<Void> stop() {
		if (m_stopDonePromise != null) {
			return m_stopDonePromise;
		}
		m_stopDonePromise = Application.getTaskPool().next().newPromise();

		// In normal circumstances, m_startDonePromise is already done so
		// it will just call this listener
		m_startDonePromise.addListener((startFuture) -> {
			try {
				Channel serverConnection = m_clientConnection;
				if (serverConnection != null) {
					// Shut down connection
					serverConnection.close();
				}
			} finally {
				// Release start/stop ref count to the client
				clientRelease();
			}
		});

		return m_stopDonePromise;
	}

	private void clientRetain() {
		int refCnt = m_clientRefCount.incrementAndGet();
		if (LOG.isTraceEnabled()) {
			LOG.trace("clientRetain() - {}", refCnt);
		}
	}

	private void clientRelease() {
		int refCnt = m_clientRefCount.decrementAndGet();
		boolean lastConnection = (refCnt == 0);
		if (LOG.isTraceEnabled()) {
			LOG.trace("clientRelease() - {}", refCnt);
		}
		if (lastConnection && m_stopDonePromise != null) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("m_stopDonePromise.trySuccess");
			}
			m_stopDonePromise.trySuccess(null);
		}
	}

	@ChannelHandler.Sharable
	private class SocketHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
			if (LOG.isTraceEnabled()) {
				LOG.trace("SocketHandler.channelRegistered()");
			}
			// Add ref count to the client
			clientRetain();

			// Prepare the pipeline
			final ChannelPipeline pipeline = ctx.pipeline();
			if (LOG.isTraceEnabled()) {
				pipeline.addLast("data-logger", new DataLogHandler(LOG));
			}
			pipeline.addLast("read-timeout", new ReadTimeoutHandler(m_readTimeoutInMS, TimeUnit.MILLISECONDS));
			pipeline.addLast("data-handler", new SocketDataHandler());

			// Create a connection object
			ctx.channel().attr(CONNECTION).getAndSet(newConnection());

			m_clientState = ClientState.Connecting;

			super.channelRegistered(ctx);
		}

		@Override
		public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
			if (LOG.isTraceEnabled()) {
				LOG.trace("SocketHandler.channelUnregistered()");
			}
			// Release ref count from channelRegistered
			clientRelease();

			super.channelUnregistered(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			if (LOG.isTraceEnabled()) {
				LOG.trace("SocketHandler.channelActive()");
			}
			m_newConnections.increment();
			m_activeConnections.increment();
			LOG.info("Connected");

			final Connection conn = ctx.channel().attr(CONNECTION).get();
			conn.onConnect(ctx);

			m_clientState = ClientState.Connected;
			m_clientConnection = ctx.channel();

//			StatusSystem.setInboundStatus(m_statusHostAndPort, StatusType.Up, "Listening");
			m_startDonePromise.setSuccess(null);

			super.channelActive(ctx);
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
			if (LOG.isDebugEnabled()) {
				LOG.debug("SocketHandler.userEventTriggered({})", evt);
			}
			super.userEventTriggered(ctx, evt);
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			final Connection conn = ctx.channel().attr(CONNECTION).getAndSet(null);
			if (LOG.isTraceEnabled()) {
				LOG.trace("SocketHandler.channelInactive()");
			}
			conn.onDisconnect();
			m_activeConnections.decrement();
			m_clientState = ClientState.Offline;
			m_clientConnection = null;

			super.channelInactive(ctx);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			m_numExceptions.increment();

			final Connection conn = ctx.channel().attr(CONNECTION).get();
			if (conn == null) {
				if (LOG.isDebugEnabled()) {
					LOG.error("SocketHandler.exceptionCaught() - CONNECTION attribute was missing", cause);
				}
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("SocketHandler.exceptionCaught()", cause);
				}
				conn.onFailure(cause);
			}
			ctx.close();
			super.exceptionCaught(ctx, cause);
		}
	}

	private class SocketDataHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			final Connection conn = ctx.channel().attr(CONNECTION).get();
			if (LOG.isTraceEnabled()) {
				LOG.trace("SocketDataHandler.channelRead()");
			}
			if (msg instanceof ByteBuf) {
				ByteBuf buf = (ByteBuf)msg;
				m_readDataSize.add(buf.readableBytes());
				conn.onData(buf);

			} else {
				ReferenceCountUtil.safeRelease(msg);
				LOG.error("SocketDataHandler.channelRead() got an unexpected message of type {}", msg.getClass().getCanonicalName());
				ctx.close();
			}

		}
	}

	private static final ResourceLeakDetector<Connection> CONNECTION_LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(Connection.class);
	private static final Recycler<Connection> CONNECTION_RECYCLER = new Recycler<Connection>() {
		@Override
		protected Connection newObject(Handle<Connection> handle) {
			return new Connection(handle);
		}
	};

	private static final Object m_onData = new Object();
	private static final Object m_onDisconnect = new Object();
	private static final Object m_userClose = new Object();

	private Connection newConnection() {
		Connection c = CONNECTION_RECYCLER.get();
		c.init(this);
		return c;
	}

	private static final class Connection extends PerpetualWork implements ISocketConnection {
		private final Recycler.Handle<Connection> m_handle;

		private final Queue<Object> m_msgQueue = PlatformDependent.newMpscQueue();
		private final WritePipeReadNotifier m_notifier = new WritePipeReadNotifier();

		private ResourceLeakTracker<Connection> m_leakTracker;
		private Logger LOG;
		private SocketClient m_client;

		private Pipe.IConsumer m_writePipeConsumer;
		private Pipe.IProducer m_writePipeProducer;
		private Pipe.IConsumer m_readPipeConsumer;
		private Pipe.IProducer m_readPipeProducer;

		private ChannelHandlerContext m_context;
		private boolean m_disconnectRequested;

		Connection(Recycler.Handle<Connection> handle) {
			m_handle = handle;
		}

		private void init(SocketClient client) {
			m_leakTracker = CONNECTION_LEAK_DETECT.track(this);
			LOG = client.LOG;
			m_client = client;

			Pipe writePipe = Pipe.create(m_notifier);
			m_writePipeConsumer = writePipe.consumer();
			m_writePipeProducer = writePipe.producer();
			Pipe readPipe = Pipe.create();
			m_readPipeConsumer = readPipe.consumer();
			m_readPipeProducer = readPipe.producer();
		}

		void onConnect(ChannelHandlerContext context) {
			m_msgQueue.add(context);
			requestMoreWork();
		}
		void onData(ByteBuf data) {
			m_readPipeProducer.submit(data);
			m_msgQueue.add(m_onData);
			requestMoreWork();
		}
		void onDisconnect() {
			m_msgQueue.add(m_onDisconnect);
			requestMoreWork();
		}
		void onFailure(Throwable cause) {
			m_msgQueue.add(cause);
			requestMoreWork();
		}

		@Override
		public void close() {
			m_msgQueue.add(m_userClose);
			requestMoreWork();
		}

		private void recycle() {
			m_msgQueue.clear();
			m_context = null;
			m_disconnectRequested = false;

			m_writePipeProducer = null;
			m_readPipeConsumer = null;

			m_client = null;
			LOG = null;
			if (m_leakTracker != null) {
				m_leakTracker.close(this);
				m_leakTracker = null;
			}
			m_handle.recycle(this);
		}

		@Override
		protected void _doWork() {
			while(true) {
				Object msg = m_msgQueue.poll();
				if (msg == null) {
					break;
				}
				Object handler = null;
				try {
					if (msg == m_onData) {
						handler = m_client.m_requestHandler;
						m_client.m_requestHandler.onData(this, m_readPipeConsumer, m_writePipeProducer);

					} else if (msg instanceof ChannelHandlerContext) {
						m_context = (ChannelHandlerContext) msg;
						handler = m_client.m_connectHandler;
						m_client.m_connectHandler.onConnect(this, m_writePipeProducer);

					} else if (msg == m_onDisconnect) {
						handler = m_client.m_disconnectHandler;

						// Close and release our Pipe interfaces. There could be buffers in the write pipe,
						// but they will be cleanly released when the user closes their producer side
						m_writePipeConsumer.close();
						m_writePipeConsumer = null;
						m_readPipeProducer.close();
						m_readPipeProducer = null;

						// Notify user of disconnect, it is expected that the user will close their pipe interfaces (if they haven't already done so)
						try {
							m_client.m_disconnectHandler.onDisconnect(this, m_readPipeConsumer, m_writePipeProducer);
						} finally {
							// Recycle this class
							recycle();
						}

					} else if (msg == m_userClose) {
						m_disconnectRequested = true;

					} else if (msg instanceof Throwable) {
						handler = m_client.m_failureHandler;
						m_client.m_failureHandler.onFailure(this, (Throwable)msg);

					} else {
						LOG.error("Unhandled message type: {}", msg.getClass().getName());
					}
				} catch(Throwable t) {
					LOG.warn("Uncaught exception from " + handler.getClass().getName(), t);
				}
			}
			if (m_context != null) {
				if (m_writePipeConsumer != null) {
					while (true) {
						ByteBuf buf = m_writePipeConsumer.poll();
						if (buf == null) {
							break;
						}
						m_client.m_writeDataSize.add(buf.readableBytes());
						m_context.write(buf);
					}
					m_context.flush();
				}
				if (m_disconnectRequested) {
					m_context.close();
					m_context = null;
				}
			}
		}
		private final class WritePipeReadNotifier implements Runnable {
			@Override
			public void run() {
				requestMoreWork();
			}
		}
	}

	public static SocketClientBuilder builder(String host, int port) {
		return new SocketClientBuilder(host, port);
	}

	public static final class SocketClientBuilder {
		private final String m_host;
		private final int m_port;
		private IConnectHandler m_connectHandler;
		private IDisconnectHandler m_disconnectHandler;
		private IFailureHandler m_failureHandler;
		private IDataHandler m_dataHandler;
		private int m_readTimeoutInMS = Config.getFWInt("socket.SocketClient.readTimeoutMS", 30000);
		private String m_loggerNameSuffix;

		private SocketClientBuilder(String host, int port) {
			m_host = host;
			m_port = port;
		}

		public SocketClientBuilder loggerNameSuffix(String name) {
			m_loggerNameSuffix = name;
			return this;
		}

		public SocketClientBuilder onConnect(IConnectHandler connectHandler) {
			m_connectHandler = connectHandler;
			return this;
		}
		public SocketClientBuilder onDisconnect(IDisconnectHandler disconnectHandler) {
			m_disconnectHandler = disconnectHandler;
			return this;
		}
		public SocketClientBuilder onFailure(IFailureHandler failureHandler) {
			m_failureHandler = failureHandler;
			return this;
		}
		public SocketClientBuilder onData(IDataHandler dataHandler) {
			m_dataHandler = dataHandler;
			return this;
		}
		public SocketClient build() {
			if (m_loggerNameSuffix == null) {
				m_loggerNameSuffix = m_host + ":" + m_port;
			}
			return new SocketClient(this);
		}
	}


	public interface ISocketConnection {
		void close();
	}
	public interface IConnectHandler {
		void onConnect(ISocketConnection connection, Pipe.IProducer outPipe);
	}
	public interface IDataHandler {
		void onData(ISocketConnection connection, Pipe.IConsumer inConsumer, Pipe.IProducer outProducer);
	}
	public interface IDisconnectHandler {
		/**
		 * It is normal here that the user will call inConsumer.close() and outProducer.close()
		 */
		void onDisconnect(ISocketConnection connection, Pipe.IConsumer inConsumer, Pipe.IProducer outProducer);
	}
	public interface IFailureHandler {
		void onFailure(ISocketConnection connection, Throwable cause);
	}

	private static final class NullHandler implements IDataHandler, IFailureHandler, IConnectHandler, IDisconnectHandler {
		private final Logger LOG;

		NullHandler(Logger parentLogger) {
			LOG = LoggerFactory.getLogger(parentLogger.getName() + ".NullHandler");
		}

		@Override
		public void onConnect(ISocketConnection connection, Pipe.IProducer outPipe) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("onConnect()");
			}
		}

		@Override
		public void onData(ISocketConnection connection, Pipe.IConsumer inConsumer, Pipe.IProducer outProducer) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("onData()");
			}
			// Throw away the inbound data
			while(true) {
				ByteBuf buf = inConsumer.poll();
				if (buf == null) {
					break;
				}
				buf.release();
			}
			connection.close();
		}

		@Override
		public void onFailure(ISocketConnection connection, Throwable cause) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("onFailure()", cause);
			}
		}

		@Override
		public void onDisconnect(ISocketConnection connection, Pipe.IConsumer inConsumer, Pipe.IProducer outProducer) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("onDisconnect()");
			}
			// We are releasing our interest/use of these pipes
			inConsumer.close();
			outProducer.close();
		}
	}}
