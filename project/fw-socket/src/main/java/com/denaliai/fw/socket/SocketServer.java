package com.denaliai.fw.socket;

import com.denaliai.fw.Application;
import com.denaliai.fw.config.Config;
import com.denaliai.fw.metrics.*;
import com.denaliai.fw.utility.Pipe;
import com.denaliai.fw.utility.concurrent.PerpetualWork;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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

public final class SocketServer {
	private static final int SERVER_BACKLOG = Config.getFWInt("socket.SocketServer.defaultServerSocketBacklog", 128);
	private static final AttributeKey<Connection> CONNECTION = AttributeKey.newInstance("ConnectionClass");

	private final CounterAndRateMetric m_newConnections;
	private final CounterMetric m_numExceptions;
	private final TotalCounterMetric m_listenerActive;
	private final TotalCounterMetric m_activeConnections;
	private final ValueMetric m_readDataSize;
	private final ValueMetric m_writeDataSize;

	private final Logger LOG;
	private final ServerBootstrap m_serverBootstrap = new ServerBootstrap();
	private final ConnectionInboundMsgHandler m_connectionMsgHandler = new ConnectionInboundMsgHandler();

	private final IConnectHandler m_connectHandler;
	private final IDisconnectHandler m_disconnectHandler;
	private final IFailureHandler m_failureHandler;
	private final IDataHandler m_requestHandler;
	private final Promise<Void> m_registerDone = Application.getTaskPool().next().newPromise();
	private final int m_socketListenPort;
	private final int m_readTimeoutInMS;

	private final AtomicInteger m_socketServerRefCount = new AtomicInteger();
	private Promise<Void> m_startDonePromise;
	private Promise<Void> m_stopDonePromise;
	private Channel m_serverConnection;
	private enum ServerState {Offline, Registering, Binding, BoundListening}
	private volatile ServerState m_serverState;

	private SocketServer(SocketServerBuilder builder) {
		LOG = LoggerFactory.getLogger(SocketServer.class.getCanonicalName() + "." + builder.m_loggerNameSuffix);
		m_connectHandler = (builder.m_connectHandler != null) ? builder.m_connectHandler : new NullHandler(LOG);
		m_disconnectHandler = (builder.m_disconnectHandler != null) ? builder.m_disconnectHandler : new NullHandler(LOG);
		m_failureHandler = (builder.m_failureHandler != null) ? builder.m_failureHandler : new NullHandler(LOG);
		m_requestHandler = (builder.m_dataHandler != null) ? builder.m_dataHandler : new NullHandler(LOG);
		m_readTimeoutInMS = builder.m_readTimeoutInMS;
		m_socketListenPort = builder.m_socketListenPort;

		m_listenerActive = MetricsEngine.newTotalCounterMetric(builder.m_loggerNameSuffix + ".listening");
		m_newConnections = MetricsEngine.newCounterAndRateMetric(builder.m_loggerNameSuffix + ".new-connections");
		m_numExceptions = MetricsEngine.newCounterMetric(builder.m_loggerNameSuffix + ".exception-count");
		m_activeConnections = MetricsEngine.newTotalCounterMetric(builder.m_loggerNameSuffix + ".active-connections");
		m_readDataSize = MetricsEngine.newValueMetric(builder.m_loggerNameSuffix + ".inbound-bytes");
		m_writeDataSize = MetricsEngine.newValueMetric(builder.m_loggerNameSuffix + ".outbound-bytes");

		init();
	}

	private void init() {
		m_serverBootstrap.group(Application.getIOPool(), Application.getIOPool())
			.channel(NioServerSocketChannel.class)
			.handler(new ServerSocketHandler())
			.option(ChannelOption.ALLOCATOR, Application.allocator())
			.option(ChannelOption.SO_BACKLOG, SERVER_BACKLOG)
			.childHandler(new MyChannelInitializer())
			.childOption(ChannelOption.ALLOCATOR, Application.allocator())
			.childOption(ChannelOption.SO_KEEPALIVE, true);

		m_serverState = ServerState.Registering;
		m_serverBootstrap.register().addListener((regFuture) -> {
			if (regFuture.isSuccess()) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Server register successful");
				}
				m_registerDone.setSuccess(null);
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Server register failed");
				}
				m_registerDone.setFailure(regFuture.cause());
			}
		});

	}

	public synchronized Future<Void> start() {
		// start/stop maintain a ref count to the server
		serverRetain();

		m_stopDonePromise = null;
		m_startDonePromise = Application.getTaskPool().next().newPromise();
		m_registerDone.addListener((regDone) -> {
			if (!regDone.isSuccess()) {
				LOG.warn("register() of listening socket failed, SocketServer cannot start", regDone.cause());
				m_serverState = ServerState.Offline;
				m_startDonePromise.setFailure(regDone.cause());
				serverRelease();
				return;
			}
			m_serverState = ServerState.Binding;
			final ChannelFuture f;
			try {
				f = m_serverBootstrap.bind(m_socketListenPort);
			} catch(Throwable t) {
				LOG.warn("Call to bind({}) failed, SocketServer cannot start", m_socketListenPort, t);
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
		if (m_stopDonePromise != null) {
			return m_stopDonePromise;
		}
		m_stopDonePromise = Application.getTaskPool().next().newPromise();

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
		m_socketServerRefCount.incrementAndGet();
	}

	private void serverRelease() {
		boolean lastConnection = (m_socketServerRefCount.decrementAndGet() == 0);
		if (lastConnection && m_stopDonePromise != null) {
			m_stopDonePromise.setSuccess(null);
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
				m_startDonePromise.setFailure(future.cause());
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
			if (LOG.isTraceEnabled()) {
				pipeline.addLast("data-logger", new DataLogHandler(LOG));
			}
			pipeline.addLast("read-timeout", new ReadTimeoutHandler(m_readTimeoutInMS, TimeUnit.MILLISECONDS));
			pipeline.addLast(m_connectionMsgHandler);

			final Connection conn = newConnection(remoteHostAddress, childChannel);
			childChannel.attr(CONNECTION).getAndSet(conn);
			conn.onConnect();

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
	private class ConnectionInboundMsgHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			final Connection conn = ctx.channel().attr(CONNECTION).get();
			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] channelRead()", conn.remoteHostAddress());
			}
			if (msg instanceof ByteBuf) {
				ByteBuf buf = (ByteBuf)msg;
				m_readDataSize.add(buf.readableBytes());
				conn.onData(buf);

			} else {
				ReferenceCountUtil.safeRelease(msg);
				LOG.error("[{}] ConnectionInboundMsgHandler got an unexpected message of type {}", conn.remoteHostAddress(), msg.getClass().getCanonicalName());
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
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("[{}] ConnectionInboundMsgHandler.exceptionCaught()", conn.remoteHostAddress(), cause);
				}
				conn.onFailure(cause);
			}
			ctx.close();
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			final Connection conn = ctx.channel().attr(CONNECTION).get();
			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] ConnectionInboundMsgHandler.channelActive()", conn.remoteHostAddress());
			}
			conn.setContext(ctx);

			super.channelActive(ctx);
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			final Connection conn = ctx.channel().attr(CONNECTION).getAndSet(null);
			if (LOG.isTraceEnabled()) {
				LOG.trace("[{}] ConnectionInboundMsgHandler.channelInactive()", conn.remoteHostAddress());
			}
			conn.onDisconnect();

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
			// The server socket (listening socket) maintains a ref to the SocketServer
			serverRetain();
			m_listenerActive.increment();

			m_serverState = ServerState.BoundListening;
			m_serverConnection = ctx.channel();

			LOG.info("Listening for socket connections on port {}", m_socketListenPort);
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

			// We only add a reference to the SocketServer once we go active, but we never did
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
				// TODO restart bind
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

	private static final Object m_onConnect = new Object();
	private static final Object m_onData = new Object();
	private static final Object m_onDisconnect = new Object();
	private static final Object m_userClose = new Object();

	private Connection newConnection(String connectionToString, Channel channel) {
		Connection c = CONNECTION_RECYCLER.get();
		c.init(this, connectionToString, channel);
		return c;
	}

	// TODO consider making this recylced
	// TODO need to increment/decrement a latch-like thing to keep the server running until this guy is all done doing his thing
	private static final class Connection extends PerpetualWork implements ISocketConnection {
		private final Recycler.Handle<Connection> m_handle;

		private final Queue<Object> m_msgQueue = PlatformDependent.newMpscQueue();
		private final WritePipeReadNotifier m_notifier = new WritePipeReadNotifier();

		private ResourceLeakTracker<Connection> m_leakTracker;
		private Logger LOG;
		private SocketServer m_server;
		private String m_connectionToString;
		private Channel m_channel;

		private Pipe.IConsumer m_writePipeConsumer;
		private Pipe.IProducer m_writePipeProducer;
		private Pipe.IConsumer m_readPipeConsumer;
		private Pipe.IProducer m_readPipeProducer;

		private ChannelHandlerContext m_context;
		private boolean m_disconnectRequested;

		Connection(Recycler.Handle<Connection> handle) {
			m_handle = handle;
		}

		private void init(SocketServer server, String connectionToString, Channel channel) {
			m_leakTracker = CONNECTION_LEAK_DETECT.track(this);
			LOG = server.LOG;
			m_server = server;
			m_connectionToString = connectionToString;
			m_channel = channel;

			Pipe writePipe = Pipe.create(m_notifier);
			m_writePipeConsumer = writePipe.consumer();
			m_writePipeProducer = writePipe.producer();
			Pipe readPipe = Pipe.create();
			m_readPipeConsumer = readPipe.consumer();
			m_readPipeProducer = readPipe.producer();
		}

		@Override
		public String remoteHostAddress() {
			return m_connectionToString;
		}

		void onConnect() {
			m_msgQueue.add(m_onConnect);
			requestMoreWork();
		}
		void setContext(ChannelHandlerContext context) {
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

		@Override
		public String toString() {
			return m_connectionToString;
		}

		private void recycle() {
			m_msgQueue.clear();
			m_context = null;
			m_disconnectRequested = false;

			m_writePipeProducer = null;
			m_readPipeConsumer = null;

			m_channel = null;
			m_connectionToString = null;
			m_server = null;
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
						if (LOG.isTraceEnabled()) {
							LOG.trace("[{}] Connection.onData()", remoteHostAddress());
						}
						handler = m_server.m_requestHandler;
						m_server.m_requestHandler.onData(this, m_readPipeConsumer, m_writePipeProducer);

					} else if (msg == m_onConnect) {
						if (LOG.isTraceEnabled()) {
							LOG.trace("[{}] Connection.onConnect()", remoteHostAddress());
						}
						handler = m_server.m_connectHandler;
						m_server.m_connectHandler.onConnect(this, m_writePipeProducer);

					} else if (msg == m_onDisconnect) {
						if (LOG.isTraceEnabled()) {
							LOG.trace("[{}] Connection.onDisconnect()", remoteHostAddress());
						}
						handler = m_server.m_disconnectHandler;

						// Close and release our Pipe interfaces. There could be buffers in the write pipe,
						// but they will be cleanly released when the user closes their producer side
						m_writePipeConsumer.close();
						m_writePipeConsumer = null;
						m_readPipeProducer.close();
						m_readPipeProducer = null;

						// Notify user of disconnect, it is expected that the user will close their pipe interfaces (if they haven't already done so)
						try {
							m_server.m_disconnectHandler.onDisconnect(this, m_readPipeConsumer, m_writePipeProducer);
						} finally {
							// Recycle this class
							recycle();
						}

					} else if (msg instanceof ChannelHandlerContext) {
						m_context = (ChannelHandlerContext) msg;

					} else if (msg == m_userClose) {
						m_disconnectRequested = true;
						if (LOG.isTraceEnabled()) {
							LOG.trace("[{}] Connection.userClose()", remoteHostAddress());
						}

					} else if (msg instanceof Throwable) {
						handler = m_server.m_failureHandler;
						m_server.m_failureHandler.onFailure(this, (Throwable)msg);

					} else {
						LOG.error("Unhandled message type: {}", msg.getClass().getName());
					}
				} catch(Throwable t) {
					LOG.warn("Uncaught exception from " + handler.getClass().getName(), t);
				}
			}
			if (m_context != null) {
				if (m_writePipeConsumer != null) {
					boolean wroteData = false;
					while (true) {
						ByteBuf buf = m_writePipeConsumer.poll();
						if (buf == null) {
							break;
						}
						m_server.m_writeDataSize.add(buf.readableBytes());
						if (LOG.isTraceEnabled()) {
							LOG.trace("[{}] Connection.write()", remoteHostAddress());
						}
						m_context.write(buf);
						wroteData = true;
					}
					if (wroteData) {
						if (LOG.isTraceEnabled()) {
							LOG.trace("[{}] Connection.flush()", remoteHostAddress());
						}
						m_context.flush();
					}
				}
				if (m_disconnectRequested) {
					if (LOG.isTraceEnabled()) {
						LOG.trace("[{}] Connection.close()", remoteHostAddress());
					}
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

	public static SocketServerBuilder builder(int socketListenPort) {
		return new SocketServerBuilder(socketListenPort);
	}

	public static final class SocketServerBuilder {
		private final int m_socketListenPort;
		private IConnectHandler m_connectHandler;
		private IDisconnectHandler m_disconnectHandler;
		private IFailureHandler m_failureHandler;
		private IDataHandler m_dataHandler;
		private int m_readTimeoutInMS = Config.getFWInt("socket.SocketServer.readTimeoutMS", 30000);
		private String m_loggerNameSuffix;

		private SocketServerBuilder(int port) {
			m_socketListenPort = port;
		}

		public SocketServerBuilder loggerNameSuffix(String name) {
			m_loggerNameSuffix = name;
			return this;
		}

		public SocketServerBuilder onConnect(IConnectHandler connectHandler) {
			m_connectHandler = connectHandler;
			return this;
		}
		public SocketServerBuilder onDisconnect(IDisconnectHandler disconnectHandler) {
			m_disconnectHandler = disconnectHandler;
			return this;
		}
		public SocketServerBuilder onFailure(IFailureHandler failureHandler) {
			m_failureHandler = failureHandler;
			return this;
		}
		public SocketServerBuilder onData(IDataHandler dataHandler) {
			m_dataHandler = dataHandler;
			return this;
		}
		public SocketServer build() {
			if (m_loggerNameSuffix == null) {
				m_loggerNameSuffix = "Socket" + m_socketListenPort;
			}
			return new SocketServer(this);
		}
	}


	public interface ISocketConnection {
		String remoteHostAddress();
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
				LOG.debug("[{}] onConnect()", connection.remoteHostAddress());
			}
		}

		@Override
		public void onData(ISocketConnection connection, Pipe.IConsumer inConsumer, Pipe.IProducer outProducer) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] onData()", connection.remoteHostAddress());
			}
			// We are an echo server here
			while(true) {
				ByteBuf buf = inConsumer.poll();
				if (buf == null) {
					break;
				}
				outProducer.submit(buf);
			}
			connection.close();
		}

		@Override
		public void onFailure(ISocketConnection connection, Throwable cause) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] onFailure()", connection.remoteHostAddress(), cause);
			}
		}

		@Override
		public void onDisconnect(ISocketConnection connection, Pipe.IConsumer inConsumer, Pipe.IProducer outProducer) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[{}] onDisconnect()", connection.remoteHostAddress());
			}
			// We are releasing our interest/use of these pipes
			inConsumer.close();
			outProducer.close();
		}
	}
}
