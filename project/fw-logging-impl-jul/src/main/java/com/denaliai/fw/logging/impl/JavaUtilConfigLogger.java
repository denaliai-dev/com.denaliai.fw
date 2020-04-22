package com.denaliai.fw.logging.impl;

import com.denaliai.fw.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaUtilConfigLogger implements Config.IConfigLogger {
	private static final Logger LOG = LoggerFactory.getLogger(Config.class);

	@Override
	public void info(String format, Object... args)
	{
		LOG.info(String.format(format, args));
	}

	@Override
	public void warn(String format, Object... args)
	{
		LOG.warn(String.format(format, args));
	}

	@Override
	public void fatal(String format, Object... args)
	{
		LOG.error(String.format(format, args));
	}
}
