package com.denaliai.fw.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

@Plugin(name = "ConsoleDisableFilter", category = "Core", elementType = "filter", printObject = true)
public final class ConsoleDisableFilter extends AbstractFilter {
	private static final boolean DISABLED = System.getProperty("com.denaliai.fw.log4j.disable-console", "false").equals("true");

	private ConsoleDisableFilter() {
		super(Result.NEUTRAL, Result.NEUTRAL);
	}

	@Override
	public Result filter(final LogEvent event) {
		if (DISABLED) {
			return Result.DENY;
		}
		return Result.NEUTRAL;
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
		return _filter(logger, level);
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
		return _filter(logger, level);
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
		return _filter(logger, level);
	}

	private Result _filter(Logger logger, Level level) {
		if (DISABLED) {
			return Result.DENY;
		}
		return Result.NEUTRAL;
	}

	@PluginFactory
	public static ConsoleDisableFilter createFilter() {
		return new ConsoleDisableFilter();
	}
}