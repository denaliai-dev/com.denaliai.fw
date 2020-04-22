package com.denaliai.fw.logging;

import com.denaliai.fw.ApplicationRun;
import com.denaliai.fw.config.Config;

public class LoggingImplRegistration {
	private static final boolean DEBUG_LOGGING = Config.getFWBoolean("debugLoggingInit", Boolean.FALSE);
	private static ILoggingImplementation m_impl;

	public static void register(ILoggingImplementation loggingImplementation) {
		if (DEBUG_LOGGING) {
			ApplicationRun.bootstrapLog("Using logging implementation " + loggingImplementation.getClass().getCanonicalName());
		}
		m_impl = loggingImplementation;
	}

	public static ILoggingImplementation registeredLoggingImpl() {
		return m_impl;
	}

	public static final boolean debugLoggingInit() {
		return DEBUG_LOGGING;
	}
}
