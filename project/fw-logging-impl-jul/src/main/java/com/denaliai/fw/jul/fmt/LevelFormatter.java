package com.denaliai.fw.jul.fmt;

import java.util.Arrays;
import java.util.logging.LogRecord;

public class LevelFormatter extends BaseFormatter {
	private final int m_fixedWidth;
	private final char[] m_fixedBuffer;

	public LevelFormatter(int fixedWidth) {
		m_fixedWidth = fixedWidth;
		m_fixedBuffer = new char[m_fixedWidth];
		Arrays.fill(m_fixedBuffer, ' ');
	}

	@Override
	public void write(StringBuilder out, LogRecord record) {
		int startLen = out.length();
		out.append(record.getLevel().getLocalizedName());
		int size = out.length() - startLen;
		if (size < m_fixedWidth) {
			out.append(m_fixedBuffer, 0, m_fixedWidth - size);
		}
	}
}
