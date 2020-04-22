package com.denaliai.fw.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import org.slf4j.Logger;

@ChannelHandler.Sharable
class DataLogHandler extends ChannelDuplexHandler {
	private final Logger LOG;

	DataLogHandler(Logger logger) {
		LOG = logger;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof ByteBuf) {
			LOG.trace("HTTP inbound data buffer\n{}", printBuffer((ByteBuf)msg));
		}
		super.channelRead(ctx, msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof ByteBuf) {
			LOG.trace("HTTP outbound data buffer\n{}", printBuffer((ByteBuf)msg));
		}
		super.write(ctx, msg, promise);
	}

	private static StringBuilder printBuffer(ByteBuf buf) {
		StringBuilder sb = new StringBuilder();
		if (buf.readableBytes() <= 1024) {
			ByteBufUtil.appendPrettyHexDump(sb, buf);
		} else {
			ByteBufUtil.appendPrettyHexDump(sb, buf, 0, 512);
			sb.append("\n...truncated...\n");
			int endStart = buf.readableBytes()-1-512;
			ByteBufUtil.appendPrettyHexDump(sb, buf, endStart, 512);
		}
		return sb;
	}
}