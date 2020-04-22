package com.denaliai.fw.socket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;

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
			LOG.trace("Socket data buffer\n{}", sb);
		}
		ctx.fireChannelRead(msg);
	}
}