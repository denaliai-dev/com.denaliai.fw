package com.denaliai.fw.jul.fmt;

import java.util.logging.LogRecord;

public abstract class BaseFormatter {
	public abstract void write(StringBuilder out, LogRecord record);
}
