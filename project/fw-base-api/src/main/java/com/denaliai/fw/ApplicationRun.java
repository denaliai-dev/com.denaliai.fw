package com.denaliai.fw;

import com.denaliai.fw.config.Config;
import com.denaliai.fw.logging.ILoggingImplementation;
import com.denaliai.fw.logging.LoggingImplRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ApplicationRun {
	// This is here to force the loading of the Config system before running any methods.  During the config load
	// it can read properties from config files and add them to the System properties.
	@SuppressWarnings("unused")
	private static final Boolean FORCE_CONFIG = Config.getFWBoolean("not-found", null);

	private static volatile boolean m_terminateRun;
	private static Runnable m_fatalExitHandler;
	private static IBootstrapLogger m_bootstrapLogger = new DefaultBootstrapLogger();

	public static void registerBoostrapLogger(IBootstrapLogger bootstrapLogger) {
		m_bootstrapLogger = bootstrapLogger;
	}

	static ILoggingImplementation registeredLoggingImpl() {
		return LoggingImplRegistration.registeredLoggingImpl();
	}

	static void registerFatalExitHandler(Runnable handler) {
		m_fatalExitHandler = handler;
	}

	static void loadLoggingImplementation() {
		Class<?> clazz;
		try {
			clazz = Class.forName("com.denaliai.fw.logging.impl.Registration");

		} catch(ClassNotFoundException ex) {
			System.out.println("ERROR: Could not find a logging implementation Registration class");
			return;

		} catch(Exception ex) {
			System.out.println("ERROR: Exception looking for a logging implementation Registration class");
			ex.printStackTrace();
			return;
		}
		Object regObj;
		try {
			regObj = clazz.newInstance();
		} catch(Exception ex) {
			System.out.println("ERROR: Exception creating instance of Registration class: " + clazz.getCanonicalName());
			ex.printStackTrace();
			return;
		}
		if (LoggingImplRegistration.registeredLoggingImpl() == null) {
			System.out.println("ERROR: Logging Registration class must call LoggingImplRegistration.register in a static block: " + clazz.getCanonicalName());
			return;
		}
	}

	static boolean indicateTermination() {
		synchronized(ApplicationRun.class) {
			if (m_terminateRun) {
				return false;
			}
			m_terminateRun = true;
		}
		return true;
	}

	public static boolean isTerminating() {
		return m_terminateRun;
	}

	public static void fatalExit() {
		if (m_fatalExitHandler != null) {
			m_fatalExitHandler.run();
			return;
		}
		if (LoggingImplRegistration.registeredLoggingImpl() != null) {
			LoggingImplRegistration.registeredLoggingImpl().shutdown();
		}
		Runtime.getRuntime().halt(1);
	}

	public static void bootstrapLog(String msg) {
		m_bootstrapLogger.log(msg);
	}

	public interface IBootstrapLogger {
		void log(String msg);
	}

	private static final class DefaultBootstrapLogger implements IBootstrapLogger {
		private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		@Override
		public synchronized void log(String msg) {
			System.out.println("[     ] " + DATE_FORMAT.format(new Date()) + " " + msg);
		}

	}
}
