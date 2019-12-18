package com.denaliai.fw;

import com.denaliai.fw.config.Config;
import com.denaliai.fw.config.Log4jConfigLogger;
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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

import java.util.concurrent.TimeUnit;

public class Application {
	private static final long SHUTDOWN_QUIET_PERIOD = Config.getFWInt("core.NeuronApplication.shutdownQuietPeriod", 0);
	private static final long SHUTDOWN_MAX_WAIT = Config.getFWInt("core.NeuronApplication.shutdownMaxWait", 15000);
	private static final CounterMetric m_applicationStart = MetricsEngine.newCounterMetric("application-start");
	private static final CounterMetric m_applicationStop = MetricsEngine.newCounterMetric("application-stop");

	private static final PooledByteBufAllocator m_ioBufferPool = PooledByteBufAllocator.DEFAULT;
	private static final NioEventLoopGroup m_ioPool;
	private static final EventLoopGroup m_taskPool;
	private static boolean m_haltOnFatalExit = true;
	private static IShutdownHandler m_userShutdownHandler;
	private static volatile boolean m_terminateRun;

	static {
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
			Configurator.shutdown(LoggerContext.getContext(), 60, TimeUnit.SECONDS); // TODO pull this from config
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

	static void initLogging() {
		if (m_terminateRun) {
			LogManager.getLogger(Application.class).error("Attempt to re-initialize a terminated application");
			fatalExit();
		}
		Config.setConfigLogger(new Log4jConfigLogger());
		for(String configKey : Config.keys()) {
			if (configKey.startsWith("logger.")) {
				final String loggerName = configKey.substring(7);
				final String levelString = Config.getString(configKey, null);
				final Level level;
				try {
					level = Level.getLevel(levelString);
				} catch(Exception ex) {
					LogManager.getLogger(Application.class).error("Could not parse logger level '{}' for '{}'", levelString, configKey, ex);
					continue;
				}
				if (level == null) {
					LogManager.getLogger(Application.class).error("Could not parse logger level '{}' for '{}'", levelString, configKey);

				} else if (loggerName.equals("root")) {
					Configurator.setRootLevel(level);

				} else {
					Configurator.setLevel(loggerName, level);
				}
			}
		}
	}


	public static void run() {
		final Logger LOG = LogManager.getLogger(Application.class);
		final String ver = Application.class.getPackage().getImplementationVersion();
		if (ver != null) {
			LOG.info("Starting ({})", ver);
		} else {
			LOG.info("Starting");
		}
		m_applicationStart.increment();
		Runtime.getRuntime().addShutdownHook(new ShutdownHook());
	}

	public static boolean isTerminating() {
		return m_terminateRun;
	}

	public static void terminate() {
		new Thread(() -> terminate0()).start();
	}

	private static void terminate0() {
		synchronized(Application.class) {
			if (m_terminateRun) {
				return;
			}
			m_terminateRun = true;
		}

		m_applicationStop.increment();
		// User needs to snapshot in the shutdown handler if they want this counter
		final Logger LOG = LogManager.getLogger(Application.class);
		if(m_userShutdownHandler != null) {
			LOG.info("Waiting on shutdown handler");
			Promise<Void> shutdownDone = getTaskPool().next().newPromise();
			m_userShutdownHandler.shutdown(shutdownDone);
			try {
				shutdownDone.await();
			} catch (InterruptedException e) {
			}
		}
		finalTerminate();
	}

	private static void finalTerminate() {
		final Logger LOG = LogManager.getLogger(Application.class);

		// Shut down thread pools
		LOG.debug("Waiting for io pool shutdown");
		m_ioPool.shutdownGracefully(SHUTDOWN_QUIET_PERIOD, SHUTDOWN_MAX_WAIT, TimeUnit.MILLISECONDS).awaitUninterruptibly();
		LOG.debug("Waiting for task pool shutdown");
		m_taskPool.shutdownGracefully(SHUTDOWN_QUIET_PERIOD, SHUTDOWN_MAX_WAIT, TimeUnit.MILLISECONDS).awaitUninterruptibly();
		LOG.info("End of shutdown hook");
		Configurator.shutdown(LoggerContext.getContext(), 60, TimeUnit.SECONDS); // TODO pull this from config
	}

	private static class ShutdownHook extends Thread {

		@Override
		public void run() {
			terminate0();
		}

	}

	public interface IShutdownHandler {
		void shutdown(Promise<Void> shutdownDone);
	}
}
