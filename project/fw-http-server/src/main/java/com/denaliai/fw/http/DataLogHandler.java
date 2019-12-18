package com.denaliai.fw.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.Logger;

@ChannelHandler.Sharable
class DataLogHandler extends ChannelInboundHandlerAdapter {
	private final Logger LOG;

	DataLogHandler(Logger logger) {
		LOG = logger;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof ByteBuf) {
			StringBuilder sb = new StringBuilder();
			ByteBufUtil.appendPrettyHexDump(sb, (ByteBuf)msg);
			LOG.trace("HTTP data buffer\n{}", sb);
		}
		ctx.fireChannelRead(msg);
	}
}