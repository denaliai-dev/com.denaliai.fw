package com.denaliai.fw.utility;

import com.denaliai.fw.TestBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ByteBufUtils_Test extends TestBase {
	@Test
	public void testToString() {
		char[] scratch = new char[0];
		ByteBuf bb = ByteBufAllocator.DEFAULT.buffer();

		scratch = ByteBufUtils.writeString(bb, 0, scratch);
		Assertions.assertEquals("0", ByteBufUtils.toString(bb));
		bb.clear();

		scratch = ByteBufUtils.writeString(bb, 5, scratch);
		Assertions.assertEquals("5", ByteBufUtils.toString(bb));
		bb.clear();

		scratch = ByteBufUtils.writeString(bb, -5, scratch);
		Assertions.assertEquals("-5", ByteBufUtils.toString(bb));
		bb.clear();

		scratch = ByteBufUtils.writeString(bb, 9, scratch);
		Assertions.assertEquals("9", ByteBufUtils.toString(bb));
		bb.clear();

		Assertions.assertEquals(0, scratch.length);

		scratch = ByteBufUtils.writeString(bb, 10, scratch);
		Assertions.assertEquals("10", ByteBufUtils.toString(bb));
		bb.clear();

		scratch = ByteBufUtils.writeString(bb, -10, scratch);
		Assertions.assertEquals("-10", ByteBufUtils.toString(bb));
		bb.clear();

		scratch = ByteBufUtils.writeString(bb, Long.MAX_VALUE, scratch);
		Assertions.assertEquals("9223372036854775807", ByteBufUtils.toString(bb));
		bb.clear();

		scratch = ByteBufUtils.writeString(bb, Long.MIN_VALUE, scratch);
		Assertions.assertEquals("-9223372036854775808", ByteBufUtils.toString(bb));
		bb.clear();
	}
}
