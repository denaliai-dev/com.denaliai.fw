package com.denaliai.fw.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import org.apache.logging.log4j.Logger;

@ChannelHandler.Sharable
class DataLogHandler extends ChannelDuplexHandler {
	private final Logger LOG;

	DataLogHandler(Logger logger) {
		LOG = logger;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof ByteBuf) {
			StringBuilder sb = new StringBuilder();
			ByteBufUtil.appendPrettyHexDump(sb, (ByteBuf)msg);
			LOG.trace("HTTP inbound data buffer\n{}", sb);
		}
		super.channelRead(ctx, msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof ByteBuf) {
			StringBuilder sb = new StringBuilder();
			ByteBufUtil.appendPrettyHexDump(sb, (ByteBuf)msg);
			LOG.trace("HTTP outbound data buffer\n{}", sb);
		}
		super.write(ctx, msg, promise);
	}

}