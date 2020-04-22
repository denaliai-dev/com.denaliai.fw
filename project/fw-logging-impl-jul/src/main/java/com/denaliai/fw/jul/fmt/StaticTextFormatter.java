package com.denaliai.fw.jul.fmt;

import java.util.logging.LogRecord;

public class StaticTextFormatter extends BaseFormatter {
	private final String m_text;

	public StaticTextFormatter(String text) {
		m_text = text;
	}

	@Override
	public void write(StringBuilder out, LogRecord record) {
		out.append(m_text);
	}
}
