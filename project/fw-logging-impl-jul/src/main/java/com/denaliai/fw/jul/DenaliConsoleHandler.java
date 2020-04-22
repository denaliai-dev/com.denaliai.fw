package com.denaliai.fw.jul;

import java.util.logging.*;

public class DenaliConsoleHandler extends StreamHandler {

	public DenaliConsoleHandler() {
		setOutputStream(System.out);
	}

	@Override
	public void publish(LogRecord record) {
		if (!isLoggable(record)) {
			return;
		}
		super.publish(record);
		flush();
	}

	@Override
	public void close() {
		flush();
	}
}
