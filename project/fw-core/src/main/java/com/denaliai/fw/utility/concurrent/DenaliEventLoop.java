package com.denaliai.fw.utility.concurrent;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.PlatformDependent;

import java.util.Queue;
import java.util.concurrent.Executor;

public class DenaliEventLoop extends SingleThreadEventLoop {

	public DenaliEventLoop() {
		super(null, new DefaultThreadFactory(DenaliEventLoop.class), true);
	}

	public DenaliEventLoop(EventLoopGroup parent, Executor executor) {
		super(parent, executor, true);
	}

	@Override
	protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
		return new TempBlockingQueue<Runnable>(maxPendingTasks);
	}

	@Override
	protected void run() {
		for (;;) {
			Runnable task = takeTask();
			if (task != null) {
				task.run();
				updateLastExecutionTime();
			}

			if (confirmShutdown()) {
				break;
			}
		}
	}
}
