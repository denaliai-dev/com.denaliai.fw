package com.denaliai.fw.http;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;

@ChannelHandler.Sharable
class DoNothingHandler extends ChannelDuplexHandler {
	DoNothingHandler() {
	}

}