package com.denaliai.fw.utility;

import io.netty.buffer.ByteBuf;
import io.netty.util.Recycler;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.internal.PlatformDependent;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class Pipe {
	private static final ResourceLeakDetector<Pipe> LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(Pipe.class);
	private static final Recycler<Pipe> RECYCLER = new Recycler<Pipe>() {
		@Override
		protected Pipe newObject(Handle<Pipe> handle) {
			return new Pipe(handle);
		}
	};
	private final Recycler.Handle<Pipe> m_handle;
	private final Queue<ByteBuf> m_queue = PlatformDependent.newSpscQueue();
	private final AtomicInteger m_refCount = new AtomicInteger();

	private ResourceLeakTracker<Pipe> m_tracker;
	private Runnable m_readNotify;

	private Pipe(Recycler.Handle<Pipe> handle) {
		m_handle = handle;
	}

	private void init(Runnable readNotify) {
		m_tracker = LEAK_DETECT.track(this);
		m_refCount.set(2);
		m_readNotify = readNotify;
	}

	public static Pipe create(Runnable readNotify) {
		Pipe p = RECYCLER.get();
		p.init(readNotify);
		return p;
	}

	public static Pipe create() {
		return create(null);
	}

	public IProducer producer() {
		return new Producer(this);
	}

	public IConsumer consumer() {
		return new Consumer(this);
	}

	/**
	 * This method is thread safe for the two threads that have the Producer and Consumer interfaces
	 */
	private void close() {
		if (m_refCount.decrementAndGet() == 0) {
			// Both consumer and producer have released the pipe, let's clean up
			while(true) {
				ByteBuf buf = m_queue.poll();
				if (buf == null) {
					break;
				}
				buf.release();
			}
			if (m_tracker != null) {
				m_tracker.close(this);
				m_tracker = null;
			}
			m_readNotify = null;
			m_handle.recycle(this);
		}
	}

	/**
	 * This class is NOT thread safe
	 */
	private static class Producer implements IProducer {
		private Pipe m_pipe;

		private Producer(Pipe pipe) {
			m_pipe = pipe;
		}

		@Override
		public void submit(ByteBuf buf) {
			if (m_pipe == null) {
				buf.release();
				throw new IllegalStateException("Pipe.Producer is closed");
			}
			m_pipe.m_queue.add(buf);
			if (m_pipe.m_readNotify != null) {
				m_pipe.m_readNotify.run();
			}
		}

		@Override
		public void close() {
			m_pipe.close();
			m_pipe = null;
		}
	}

	/**
	 * This class is NOT thread safe
	 */
	private static class Consumer implements IConsumer {
		private Pipe m_pipe;

		private Consumer(Pipe pipe) {
			m_pipe = pipe;
		}

		@Override
		public ByteBuf poll() {
			if (m_pipe == null) {
				throw new IllegalStateException("Pipe.Consumer is closed");
			}
			return m_pipe.m_queue.poll();
		}

		@Override
		public void close() {
			m_pipe.close();
			m_pipe = null;
		}
	}

	/**
	 * The class implementing this interface is NOT thread safe, only call it from one thread at a time
	 */
	public interface IProducer {
		void submit(ByteBuf buf);
		void close();
	}

	/**
	 * The class implementing this interface is NOT thread safe, only call it from one thread at a time
	 */
	public interface IConsumer {
		ByteBuf poll();
		void close();
	}
}
