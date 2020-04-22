package com.denaliai.fw.logging.impl;

import java.util.logging.LogManager;

public class DenaliLogManager extends LogManager {
	private static DenaliLogManager m_instance;

	public DenaliLogManager() {
		m_instance = this;
	}

	@Override
	public void reset() throws SecurityException {
		// Prevents shutdown hook from closing on us
	}

	private void reset0() {
		super.reset();
	}

	public static void shutdown() {
		m_instance.reset0();
	}
}
