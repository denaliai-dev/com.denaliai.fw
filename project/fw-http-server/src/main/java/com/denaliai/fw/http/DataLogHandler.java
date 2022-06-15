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
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		LOG.trace("[{}] DataLogHandler.channelReadComplete()", ctx.channel().remoteAddress().toString());
		super.channelReadComplete(ctx);
	}

	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		LOG.trace("[{}] DataLogHandler.close()", ctx.channel().remoteAddress().toString());
		super.close(ctx, promise);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		LOG.trace("[{}] DataLogHandler.exceptionCaught()", ctx.channel().remoteAddress().toString());
		super.exceptionCaught(ctx, cause);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof ByteBuf) {
			LOG.trace("[{}] HTTP inbound data buffer\n{}", ctx.channel().remoteAddress().toString(), printBuffer((ByteBuf) msg));
		} else {
			LOG.trace("[{}] HTTP inbound message: {}", ctx.channel().remoteAddress().toString(), msg.getClass().getCanonicalName());
		}
		super.channelRead(ctx, msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof ByteBuf) {
			LOG.trace("[{}] HTTP outbound data buffer\n{}", ctx.channel().remoteAddress().toString(), printBuffer((ByteBuf) msg));
		} else {
			LOG.trace("[{}] HTTP outbound message: {}", ctx.channel().remoteAddress().toString(), msg.getClass().getCanonicalName());
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