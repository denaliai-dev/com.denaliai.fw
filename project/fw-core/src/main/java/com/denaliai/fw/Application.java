package com.denaliai.fw;

import com.denaliai.fw.config.Config;
import com.denaliai.fw.logging.LoggingImplRegistration;
import com.denaliai.fw.metrics.CounterMetric;
import com.denaliai.fw.metrics.MetricsEngine;
import com.denaliai.fw.utility.concurrent.DenaliEventLoopGroup;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Application {
	private static final boolean HALT_ON_SHUTDOWN = Config.getFWBoolean("Application.haltOnShutdown", Boolean.FALSE);
	private static final int HALT_ON_SHUTDOWN_EXIT_CODE = Config.getFWInt("Application.haltOnShutdown", 0);
	private static final long SHUTDOWN_QUIET_PERIOD = Config.getFWInt("Application.shutdownQuietPeriod", 0);
	private static final long SHUTDOWN_MAX_WAIT = Config.getFWInt("Application.shutdownMaxWait", 15000);

	private static final CounterMetric m_applicationStart = MetricsEngine.newCounterMetric("application-start");
	private static final CounterMetric m_applicationStop = MetricsEngine.newCounterMetric("application-stop");

	private static final PooledByteBufAllocator m_ioBufferPool = PooledByteBufAllocator.DEFAULT;
	private static final NioEventLoopGroup m_ioPool;
	private static final EventLoopGroup m_taskPool;
	private static boolean m_haltOnFatalExit = true;
	private static IShutdownHandler m_userShutdownHandler;

	static {
		ApplicationRun.registerFatalExitHandler(() -> Application.fatalExit());
		m_ioPool = new NioEventLoopGroup(Config.getFWInt("core.Application.ioPoolCount", 2));
		int taskPoolSize = Config.getFWInt("core.Application.taskPoolCount", -1);
		if (taskPoolSize <= 0) {
			m_taskPool = new DenaliEventLoopGroup();
		} else {
			m_taskPool = new DenaliEventLoopGroup(taskPoolSize);
		}
	}

	public static void fatalExit() {
		if (m_haltOnFatalExit) {
			if (LoggingImplRegistration.registeredLoggingImpl() != null) {
				LoggingImplRegistration.registeredLoggingImpl().shutdown();
			}
			Runtime.getRuntime().halt(1);
		}
	}

	public static void setShutdownHandler(IShutdownHandler shutdownHandler) {
		m_userShutdownHandler = shutdownHandler;
	}

	public static void shutdown() {
		terminate();
	}

	static void disableFatalExitHalt() {
		m_haltOnFatalExit = false;
	}

	public static ByteBufAllocator allocator() {
		return m_ioBufferPool;
	}

	public static ByteBuf allocateIOBuffer() {
		return m_ioBufferPool.buffer();
	}

	public static ByteBuf allocateEmptyBuffer() {
		return m_ioBufferPool.heapBuffer(0,0);
	}

	public static ByteBufAllocator ioBufferAllocator() {
		return m_ioBufferPool;
	}

	public static CompositeByteBuf allocateCompositeBuffer() {
		return m_ioBufferPool.compositeBuffer();
	}

	public static CompositeByteBuf allocateCompositeBuffer(int maxNumComponents) {
		return m_ioBufferPool.compositeBuffer(maxNumComponents);
	}

	public static NioEventLoopGroup getIOPool() {
		return m_ioPool;
	}

	public static EventLoopGroup getTaskPool() {
		return m_taskPool;
	}

	public static <T> Promise<T> newPromise() {
		return m_taskPool.next().<T>newPromise();
	}

	public static <T> Future<T> newSucceededFuture(T result) {
		return m_taskPool.next().newSucceededFuture(result);
	}
	public static Future<Void> newSucceededFuture() {
		return m_taskPool.next().newSucceededFuture(null);
	}

	public static <T> Future<T> newFailedFuture(Throwable cause) {
		return m_taskPool.next().newFailedFuture(cause);
	}

	public static void run() {
		final Logger LOG = LoggerFactory.getLogger(Application.class);
		final String ver = Application.class.getPackage().getImplementationVersion();
		if (ver != null) {
			String callerVer = "";
			final StackTraceElement[] stack = (new Throwable()).getStackTrace();
			if (stack != null) {
				for (int i = 0; i < stack.length; i++) {
					StackTraceElement el = stack[i];
					if (!el.getClassName().startsWith("com.denaliai.fw")) {
						try {
							Class<?> clazz = Application.class.getClassLoader().loadClass(el.getClassName());
							String cv = clazz.getPackage().getImplementationVersion();
							if (cv != null) {
								callerVer = cv + " ";
							}
						} catch(Throwable t) {
						}
						break;
					}
				}
			}
			LOG.info("Starting {}({})", callerVer, ver);
		} else {
			LOG.info("Starting");
		}
		m_applicationStart.increment();
		Runtime.getRuntime().addShutdownHook(new ShutdownHook());
	}

	public static boolean isTerminating() {
		return ApplicationRun.isTerminating();
	}

	public static void terminate() {
		Thread t = new Thread(() -> {
			try {
				terminate0();
			} catch (Throwable ex) {
				ex.printStackTrace(System.err);
			}
		}, "terminate()");
		t.setDaemon(false);
		t.start();
	}

	public static void terminateAndWait() {
		try {
			terminate0();
		} catch (Throwable ex) {
			ex.printStackTrace(System.err);
		}
	}

	public static void haltImmediately() {
		try {
			final Logger LOG = LoggerFactory.getLogger(Application.class);
			LOG.info("Immediately halting the application");
		} catch(Throwable t) {
		}
		// Immediately terminate the process
		Runtime.getRuntime().halt(1);
	}

	private static void terminate0() {
		if (!ApplicationRun.indicateTermination()) {
			return;
		}

		m_applicationStop.increment();
		// User needs to snapshot in the shutdown handler if they want this counter
		final Logger LOG = LoggerFactory.getLogger(Application.class);
		if(m_userShutdownHandler != null) {
			LOG.info("Waiting on shutdown handler");
			Promise<Void> shutdownDone = getTaskPool().next().newPromise();
			try {
				m_userShutdownHandler.shutdown(shutdownDone);
				try {
					shutdownDone.await();
				} catch (InterruptedException e) {
				}
			} catch (Exception ex) {
				LOG.error("Exception in user shutdown handler", ex);
			}
			LOG.debug("Shutdown handler done");
		}
		finalTerminate();
	}

	private static void finalTerminate() {
		final Logger LOG = LoggerFactory.getLogger(Application.class);

		// Shut down thread pools
		LOG.debug("Waiting for io pool shutdown");
		m_ioPool.shutdownGracefully(SHUTDOWN_QUIET_PERIOD, SHUTDOWN_MAX_WAIT, TimeUnit.MILLISECONDS).awaitUninterruptibly();
		LOG.debug("Waiting for task pool shutdown");
		m_taskPool.shutdownGracefully(SHUTDOWN_QUIET_PERIOD, SHUTDOWN_MAX_WAIT, TimeUnit.MILLISECONDS).awaitUninterruptibly();
		LOG.info("End of shutdown hook");
		if (LoggingImplRegistration.registeredLoggingImpl() != null) {
			LoggingImplRegistration.registeredLoggingImpl().shutdown();
		}
		if (HALT_ON_SHUTDOWN) {
			Runtime.getRuntime().halt(HALT_ON_SHUTDOWN_EXIT_CODE);
		}
	}

	private static class ShutdownHook extends Thread {
		ShutdownHook() {
			setName("ShutdownHook");
			setDaemon(false);
		}
		@Override
		public void run() {
			terminate0();
		}

	}

	public interface IShutdownHandler {
		void shutdown(Promise<Void> shutdownDone);
	}

}
