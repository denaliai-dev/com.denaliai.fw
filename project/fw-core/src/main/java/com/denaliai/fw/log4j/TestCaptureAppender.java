package com.denaliai.fw.log4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Plugin(name = "Capture", category = "Core", elementType = "appender", printObject = true)
public final class TestCaptureAppender extends AbstractAppender {
	private static LinkedList<String> m_captured = new LinkedList<>();

	public static void clear() {
		synchronized(m_captured) {
			m_captured.clear();
		}
	}

	public static List<String> getCaptured() {
		synchronized(m_captured) {
			return new ArrayList<String>(m_captured);
		}
	}

	private TestCaptureAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
		super(name, filter, layout, ignoreExceptions);
	}

	@PluginFactory
	public static TestCaptureAppender createAppender(@PluginAttribute("name") String name,
	                                                 @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
	                                                 @PluginElement("Layout") Layout<? extends Serializable> layout,
	                                                 @PluginElement("Filters") Filter filter) {

		if (name == null) {
			LOGGER.error("No name provided for TestCaptureAppender");
			return null;
		}
		if (layout == null) {
			layout = PatternLayout.createDefaultLayout();
		}
		return new TestCaptureAppender(name, filter, layout, ignoreExceptions);
	}

	@Override
	public void append(LogEvent event) {
		String str = new String(getLayout().toByteArray(event));
		synchronized(m_captured) {
			m_captured.add(str);
		}
	}
}