package com.denaliai.fw.utility.concurrent;

import io.netty.channel.EventLoop;
import io.netty.channel.MultithreadEventLoopGroup;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class DenaliEventLoopGroup extends MultithreadEventLoopGroup {

	public DenaliEventLoopGroup() {
		super(0, (ThreadFactory) null);
	}
	/**
	 * Create a new instance
	 *
	 * @param nThreads the number of threads to use
	 */
	public DenaliEventLoopGroup(int nThreads) {
		super(nThreads, (ThreadFactory) null);
	}

	@Override
	protected EventLoop newChild(Executor executor, Object... args) throws Exception {
		return new DenaliEventLoop(this, executor);
	}
}
