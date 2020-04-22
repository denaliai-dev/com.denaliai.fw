package com.denaliai.fw.logging.impl;

import com.denaliai.fw.ApplicationRun;
import com.denaliai.fw.config.Config;
import com.denaliai.fw.jul.DenaliAIFormatter;
import com.denaliai.fw.jul.DenaliConsoleHandler;
import com.denaliai.fw.jul.fmt.*;
import com.denaliai.fw.logging.LoggingImplRegistration;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.*;

public class LoggingConfigClass {
	private static DenaliConsoleHandler m_consoleHandler;
	private static FileHandler m_fileHandler;
	// Prevent these from getting garbage collected by JUL
	private static final List<Logger> m_keepLoggersAlive = new LinkedList<>();

	static void shutdown() {
		if (LoggingImplRegistration.debugLoggingInit()) {
			LoggerFactory.getLogger(JavaUtilLogAdapter.class).info("LoggingConfigClass.shutdown()");
		}
		DenaliLogManager.shutdown();
	}

	public LoggingConfigClass() {
		try {
			init();
		} catch(Exception ex) {
			ApplicationRun.bootstrapLog("Exception during Java Util Logging configuration");
			ex.printStackTrace();
		}
	}
	private void init() {
		LogManager mgr = LogManager.getLogManager();
		DenaliAIFormatter fmt = new DenaliAIFormatter(
			// [INFO ] 2020-04-21 12:30:53.794 [nioEventLoopGroup-2-2] Http10000 - GET HTTP/1.1 /nothing_to_get.html
			new StaticTextFormatter("["),
			new LevelFormatter(5),
			new StaticTextFormatter("] "),
			new DateTimeFormatter(),
			new StaticTextFormatter(" ["),
			new CurrentThreadFormatter(),
			new StaticTextFormatter("] "),
			new SimpleLoggerNameFormatter(),
			new StaticTextFormatter(" - "),
			new MessageFormatter(),
			new StaticTextFormatter(System.lineSeparator())
		);

		if (Config.getFWBoolean("jul.disable-console", Boolean.FALSE) == false) {
			m_consoleHandler = new DenaliConsoleHandler();
			m_consoleHandler.setLevel(Level.ALL);
			m_consoleHandler.setFormatter(fmt);
		}

		try {
			String logDir = Config.getFWString("log-dir", "logs");
			String logFilePrefix = Config.getFWString("log-file-prefix", "app");
			String logFilePattern = logDir + "/" + logFilePrefix + "-%g" + ".log";
			int maxFileSize = Config.getFWInt("log-max-file-size-bytes", 5*1024*1024);
			int maxFileCount = Config.getFWInt("log-max-file-count", 10);
			m_fileHandler = new FileHandler(logFilePattern, maxFileSize, maxFileCount, true);
			m_fileHandler.setLevel(Level.ALL);
			m_fileHandler.setFormatter(fmt);
		} catch(Exception ex) {
			LoggerFactory.getLogger(JavaUtilLogAdapter.class).error("Could not create FileHandler", ex);
		}

		Logger root = Logger.getLogger("");
		root.setLevel(Level.parse(Config.getFWString("logger-level-root", Level.INFO.getName())));
		if (m_consoleHandler != null) {
			root.addHandler(m_consoleHandler);
		}
		if (m_fileHandler != null) {
			root.addHandler(m_fileHandler);
		}

		Level fwLevel = Level.parse(Config.getFWString("logger-level-fw", Level.INFO.getName()));
		Logger l = Logger.getLogger("io.netty");
		l.setLevel(fwLevel);
		m_keepLoggersAlive.add(l);
		l = Logger.getLogger("org.asynchttpclient");
		l.setLevel(fwLevel);
		m_keepLoggersAlive.add(l);
		l = Logger.getLogger("com.denaliai.fw");
		l.setLevel(fwLevel);
		m_keepLoggersAlive.add(l);

		initFromConfig();
	}

	private void initFromConfig() {
		for(String configKey : Config.keys()) {
			if (configKey.startsWith("logger.")) {
				final String loggerName = configKey.substring(7);
				final String levelString = Config.getString(configKey, null);
				final org.slf4j.event.Level level;
				try {
					level = org.slf4j.event.Level.valueOf(levelString);
				} catch(Exception ex) {
					LoggerFactory.getLogger(JavaUtilLogAdapter.class).error("Could not parse logger level '{}' for '{}'", levelString, configKey, ex);
					continue;
				}
				final Level julLevel;
				switch(level) {
					case ERROR:
						julLevel = Level.SEVERE;
						break;
					case WARN:
						julLevel = Level.WARNING;
						break;
					case DEBUG:
						julLevel = Level.FINE;
						break;
					case TRACE:
						julLevel = Level.FINEST;
						break;
					case INFO:
					default:
						julLevel = Level.INFO;
						break;
				}
				if (level == null) {
					LoggerFactory.getLogger(JavaUtilLogAdapter.class).error("Could not parse logger level '{}' for '{}'", levelString, configKey);

				} else if (loggerName.equals("root")) {
					if (LoggingImplRegistration.debugLoggingInit()) {
						LoggerFactory.getLogger(JavaUtilLogAdapter.class).info("Setting root logger to {}", julLevel.getLocalizedName());
					}
					Logger.getLogger("").setLevel(julLevel);

				} else {
					if (LoggingImplRegistration.debugLoggingInit()) {
						LoggerFactory.getLogger(JavaUtilLogAdapter.class).info("Setting logger {} to {}", loggerName, julLevel.getLocalizedName());
					}
					Logger logger = Logger.getLogger(loggerName);
					logger.setLevel(julLevel);
					m_keepLoggersAlive.add(logger);
				}
			}
		}
		LoggerFactory.getLogger(JavaUtilLogAdapter.class).info("Logging initialized");
	}

}
