package com.denaliai.fw.jul.fmt;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogRecord;

public class DateTimeFormatter extends BaseFormatter {
	private final SimpleDateFormat DATE_FORMAT;
	private final Date m_date = new Date();

	public DateTimeFormatter(String simpleDFPattern) {
		DATE_FORMAT = new SimpleDateFormat(simpleDFPattern);
	}

	public DateTimeFormatter() {
		DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	}

	@Override
	public void write(StringBuilder out, LogRecord record) {
		m_date.setTime(record.getMillis());
		out.append(DATE_FORMAT.format(m_date));
	}
}
