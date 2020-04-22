package com.denaliai.fw.logging.impl;

import com.denaliai.fw.config.Config;
import com.denaliai.fw.logging.ILoggingImplementation;

public class JavaUtilLogAdapter implements ILoggingImplementation {

	@Override
	public void bootstrap(String[] args) {
		System.setProperty("java.util.logging.config.class", LoggingConfigClass.class.getCanonicalName());
		System.setProperty("java.util.logging.manager", DenaliLogManager.class.getName());
		initLogging();
	}

	@Override
	public void shutdown() {
		LoggingConfigClass.shutdown();
	}

	private static void initLogging() {
		// Initialize Java Util Logging
		java.util.logging.LogManager.getLogManager();

		Config.setConfigLogger(new JavaUtilConfigLogger());
	}

}
