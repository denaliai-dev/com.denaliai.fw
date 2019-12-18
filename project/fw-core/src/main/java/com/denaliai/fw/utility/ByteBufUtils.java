package com.denaliai.fw.utility;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public final class ByteBufUtils {
	public static final int MAX_LONG_STRING = Math.max(Long.toString(Long.MAX_VALUE).length(), Long.toString(Long.MIN_VALUE).length());

	private static final char[] ensureSpace(char[] scratch, int length, int maxSize) {
		if (length < scratch.length) {
			return scratch;
		}
		char[] newScratch = new char[maxSize];
		System.arraycopy(scratch, 0, newScratch, 0, scratch.length);
		return newScratch;
	}

	public static char[] writeString(ByteBuf dest, long value, char[] scratch) {
		if (value < 10 && value >= 0) {
			dest.writeByte('0' + (int)value);
			return scratch;
		}
		if (value < 0 && value > -10) {
			dest.writeByte('-');
			dest.writeByte('0' + (int)-value);
			return scratch;
		}
		long runValue = value;
		int index = 0;
		while(true) {
			int tens = (int)Math.abs(runValue % 10l);
			scratch = ensureSpace(scratch, index+1, MAX_LONG_STRING);
			scratch[index++] = (char)('0' + tens);
			runValue /= 10;
			if (runValue == 0) {
				break;
			}
		}
		if (value < 0) {
			dest.writeByte('-');
		}
		for(int i=index-1; i>=0; i--) {
			dest.writeByte(scratch[i]);
		}
		return scratch;
	}


	public static String toString(ByteBuf buf) {
		return new String(ByteBufUtil.getBytes(buf));
	}
}
