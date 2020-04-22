package com.denaliai.fw.log4j2;

import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Plugin(name = "LevelCheck", category = "Core", elementType = "appender", printObject = true)
public final class TestLevelCheckAppender extends AbstractAppender {
	private static boolean m_gotFatal;
	private static boolean m_gotError;
	private static boolean m_gotWarn;
	private static boolean m_gotInfo;
	private static boolean m_gotDebug;
	private static boolean m_gotTrace;

	public static void reset() {
		m_gotFatal = false;
		m_gotError = false;
		m_gotWarn = false;
		m_gotInfo = false;
		m_gotDebug = false;
		m_gotTrace = false;
	}

	public static boolean gotLevelAtOrLower(Level level) {
		if (level == Level.TRACE) {
			return m_gotTrace;
		}
		if (level == Level.DEBUG) {
			return m_gotDebug || m_gotTrace;
		}
		if (level == Level.INFO) {
			return m_gotInfo || m_gotDebug || m_gotTrace;
		}
		if (level == Level.WARN) {
			return m_gotWarn || m_gotInfo || m_gotDebug || m_gotTrace;
		}
		if (level == Level.ERROR) {
			return m_gotError || m_gotWarn || m_gotInfo || m_gotDebug || m_gotTrace;
		}
		return m_gotFatal || m_gotError || m_gotWarn || m_gotInfo || m_gotDebug || m_gotTrace;
	}

	public static boolean gotLevelAtOrHigher(Level level) {
		if (level == Level.FATAL) {
			return m_gotFatal;
		}
		if (level == Level.ERROR) {
			return m_gotFatal || m_gotError;
		}
		if (level == Level.WARN) {
			return m_gotFatal || m_gotError || m_gotWarn;
		}
		if (level == Level.INFO) {
			return m_gotFatal || m_gotError || m_gotWarn || m_gotInfo;
		}
		if (level == Level.DEBUG) {
			return m_gotFatal || m_gotError || m_gotWarn || m_gotInfo || m_gotDebug;
		}
		return m_gotFatal || m_gotError || m_gotWarn || m_gotInfo || m_gotDebug || m_gotTrace;
	}

	public static boolean gotLevel(Level level) {
		if (level == Level.TRACE) {
			return m_gotTrace;
		}
		if (level == Level.DEBUG) {
			return m_gotDebug;
		}
		if (level == Level.INFO) {
			return m_gotInfo;
		}
		if (level == Level.WARN) {
			return m_gotWarn;
		}
		if (level == Level.ERROR) {
			return m_gotError;
		}
		if (level == Level.FATAL) {
			return m_gotFatal;
		}
		return m_gotFatal && m_gotError && m_gotWarn && m_gotInfo && m_gotDebug && m_gotTrace;
	}

	private TestLevelCheckAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
		super(name, filter, layout, ignoreExceptions);
	}

	@PluginFactory
	public static TestLevelCheckAppender createAppender(@PluginAttribute("name") String name,
	                                                    @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
	                                                    @PluginElement("Layout") Layout<? extends Serializable> layout,
	                                                    @PluginElement("Filters") Filter filter) {

		if (name == null) {
			LOGGER.error("No name provided for TestLevelCheckAppender");
			return null;
		}
		if (layout == null) {
			layout = PatternLayout.createDefaultLayout();
		}
		return new TestLevelCheckAppender(name, filter, layout, ignoreExceptions);
	}

	@Override
	public void append(LogEvent event) {
		if (event.getLevel() == Level.TRACE) {
			m_gotTrace = true;
		} else if (event.getLevel() == Level.DEBUG) {
			m_gotDebug = true;
		} else if (event.getLevel() == Level.INFO) {
			m_gotInfo = true;
		} else if (event.getLevel() == Level.WARN) {
			m_gotWarn = true;
		} else if (event.getLevel() == Level.ERROR) {
			m_gotError = true;
		} else if (event.getLevel() == Level.FATAL) {
			m_gotFatal = true;
		}
	}
}