package com.denaliai.fw.jul;

import com.denaliai.fw.jul.fmt.BaseFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class DenaliAIFormatter extends Formatter {
	private final BaseFormatter[] m_formatters;

	public DenaliAIFormatter(BaseFormatter ...args) {
		m_formatters = args;
	}

	private final StringBuilder m_sb = new StringBuilder();
	private final StringBuilder m_sbSource = new StringBuilder();
	public synchronized String format(LogRecord record) {
		record.setMessage(formatMessage(record));
		m_sb.setLength(0);
		for(BaseFormatter fmt : m_formatters) {
			fmt.write(m_sb, record);
		}
		if (record.getThrown() != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			record.getThrown().printStackTrace(pw);
			pw.close();
			m_sb.append(sw.toString());
		}
		String out = m_sb.toString();
		m_sb.setLength(0);
		m_sbSource.setLength(0);
		return out;
	}
}