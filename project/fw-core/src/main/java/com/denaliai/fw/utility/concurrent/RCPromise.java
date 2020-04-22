package com.denaliai.fw.utility.concurrent;

import com.denaliai.fw.Application;
import io.netty.channel.EventLoopGroup;
import io.netty.util.*;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class RCPromise<T> extends AbstractReferenceCounted implements RCFuture<T> {
	private static final int MAX_NOTIFY_DEPTH = Integer.parseInt(System.getProperty("com.bk.db.concurrent.RCPromise.max-notify-depth", "8"));
	private static final Logger LOG = LoggerFactory.getLogger(RCPromise.class);
	private static final ResourceLeakDetector<RCPromise> PROMISE_LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(RCPromise.class, 1);
	private static final Recycler<RCPromise> PROMISE_RECYCLER = new Recycler<RCPromise>() {
		@Override
		protected RCPromise newObject(Handle<RCPromise> handle) {
			return new RCPromise(handle);
		}
	};
	private static final Recycler<CauseWrapper> CAUSE_RECYCLER = new Recycler<CauseWrapper>() {
		@Override
		protected CauseWrapper newObject(Handle<CauseWrapper> handle) {
			return new CauseWrapper(handle);
		}
	};
	private static final AtomicReferenceFieldUpdater<RCPromise, Object> RESULT_UPDATER = AtomicReferenceFieldUpdater.newUpdater(RCPromise.class, Object.class, "m_result");
	private static final AtomicIntegerFieldUpdater<RCPromise> NOTIFYING_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RCPromise.class, "m_notifyingListeners");
	private static final Object SUCCESS_NULL_RESULT = new Object();

	private final Recycler.Handle<RCPromise> m_recylerHandle;
	private final Queue<ListenerWrapper> m_listeners = PlatformDependent.newMpscQueue();

	private ResourceLeakTracker<RCPromise> m_leakTracker;

	private EventLoopGroup m_eventGroup;
	private volatile Object m_result;
	private volatile int m_notifyingListeners;
	private int m_numWaiting;

	private RCPromise(Recycler.Handle<RCPromise> recylerHandle) {
		m_recylerHandle = recylerHandle;
	}

	@SuppressWarnings("unchecked")
	public static <T> RCPromise<T> create(EventLoopGroup eventGroup) {
		RCPromise<T> promise = PROMISE_RECYCLER.get();
		promise.init(eventGroup);
		return promise;
	}

	@SuppressWarnings("unchecked")
	public static <T> RCPromise<T> create() {
		RCPromise<T> promise = PROMISE_RECYCLER.get();
		promise.init(Application.getTaskPool());
		return promise;
	}

	private void init(EventLoopGroup eventGroup) {
		m_leakTracker = PROMISE_LEAK_DETECT.track(this);
		m_eventGroup = eventGroup;
	}

	public RCPromise<T> setSuccess(T result) {
		if (setSuccess0(result)) {
			return this;
		}
		throw new IllegalStateException("complete already: " + this);
	}

	public boolean trySuccess(T result) {
		return setSuccess0(result);
	}

	public RCPromise<T> setFailure(Throwable cause) {
		final CauseWrapper wrapper = CAUSE_RECYCLER.get();
		wrapper.set(cause);
		if (setValue0(wrapper)) {
			return this;
		}
		wrapper.recycle();
		throw new IllegalStateException("complete already: " + this);
	}

	@Override
	@SuppressWarnings("unchecked")
	public RCPromise<T> addListener(RCFutureListener<T> listener, ParamBag params) {
		// If we have already completed
		if (m_result != null) {
			notifyListener0(listener, params);
			return this;
		}
		ListenerWrapper w = LISTENER_RECYCLER.get();
		w.init(this, listener, params);
		m_listeners.add(w);
		// It is possible to have another thread complete and then notify BEFORE we added ourselves
		// to the listeners queue
		if (m_result != null) {
			notifyListeners();
		}
		return this;
	}

	@Override
	public RCPromise<T> addListener(RCFutureListener<T> listener) {
		addListener(listener, null);
		return this;
	}

	/**
	 * Caller must have a ref count on this object when calling
	 * this method.
	 *
	 */
	@Override
	public boolean await(long timeoutInMS) {
		long timeoutNanos = timeoutInMS * 1000;
		if (timeoutNanos <= 0) {
			return isDone();
		}
		if (isDone()) {
			return true;
		}
		long startTime = System.nanoTime();
		long waitTime = timeoutNanos;
		while (true) {
			synchronized (this) {
				if (isDone()) {
					return true;
				}
				m_numWaiting++;
				try {
					wait(waitTime / 1000000, (int) (waitTime % 1000000));
				} catch (InterruptedException e) {
					// ignore this
				} finally {
					m_numWaiting--;
				}
			}
			if (isDone()) {
				return true;
			}
			waitTime = timeoutNanos - (System.nanoTime() - startTime);
			if (waitTime <= 0) {
				return isDone();
			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public T get() {
		Object result = m_result;
		if (result == null) {
			await(Long.MAX_VALUE);
			result = m_result;
		}
		if (result == SUCCESS_NULL_RESULT) {
			return null;
		}
		Throwable cause = cause0(result);
		if (cause == null) {
			return (T) result;
		}
		throw new RuntimeException(cause);
	}

	@Override
	public Throwable cause() {
		return cause0(m_result);
	}

	private static Throwable cause0(Object result) {
		if (!(result instanceof CauseWrapper)) {
			return null;
		}
		return ((CauseWrapper)result).cause;
	}

	@Override
	public boolean isSuccess() {
		return m_result != null && !(m_result instanceof CauseWrapper);
	}

	@Override
	public boolean isDone() {
		return m_result != null;
	}

	private boolean setSuccess0(T result) {
		return setValue0(result == null ? SUCCESS_NULL_RESULT : result);
	}

	private boolean setValue0(Object result) {
		if (RESULT_UPDATER.compareAndSet(this, null, result)) {
			checkNotifyWaiters();
			notifyListeners();
			return true;
		}
		return false;
	}

	private synchronized void checkNotifyWaiters() {
		if (m_numWaiting > 0) {
			notifyAll();
		}
	}

	@SuppressWarnings("unchecked")
	private void notifyListeners() {
		int startTick = m_notifyingListeners;

		// Attempt to bump the tick
		// This will either keep the other thread running and pulling from the queue
		// or we will take over if nobody is processing the queue
		if (NOTIFYING_UPDATER.compareAndSet(this, startTick, startTick+1)) {
			// If the startTick was 0, then I own the queue
			// If the startTick was != 0 someone else owns the queue already
			if (startTick != 0) {
				return;
			}
			// It was 0 and we set it to 1... we own the queue

		// We did not succeed in bumping the tick which means someone else either bumped it
		// or it was reset to 0.  Attempt to bump it, testing if it was 0
		} else if (!NOTIFYING_UPDATER.compareAndSet(this, 0, startTick+1)) {
			// Tick was not 0 (someone else owns the queue)
			return;

		} else {
			// It was 0 and we set it... we own the queue
		}

		RCInteger current = m_notifyDepth.get();
		if (current == null) {
			current = RCInteger.create(0);
			m_notifyDepth.set(current);
		} else {
			current.increment();
		}
		final boolean callDirect = (current.value() < MAX_NOTIFY_DEPTH);
		while(true) {
			ListenerWrapper w = m_listeners.poll();
			if (w == null) {
				if (NOTIFYING_UPDATER.compareAndSet(this, startTick, 0)) {
					// I was able to release ownership of the queue (nothing more has been added to queue)
					break;
				}
				startTick = m_notifyingListeners;
				continue;
			}
			if (callDirect) {
				notifyListener0(w.listener, w.params);
				w.recycle();
			} else {
				// Pending listener executions need to hold the ref to the future to keep it alive
				retain();
				m_eventGroup.execute(w);
			}
		}
		if (current.decrement() == 0) {
			current.release();
			m_notifyDepth.set(null);
		}
	}

	private static final FastThreadLocal<RCInteger> m_notifyDepth = new FastThreadLocal<>();
	private void notifyListener0(RCFutureListener<T> l, ParamBag params) {
		try {
			l.operationComplete(this, params);
		} catch (Throwable t) {
			if (LOG.isWarnEnabled()) {
				LOG.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
			}
		}
	}

	@Override
	public RCPromise<T> touch(Object hint) {
		if (m_leakTracker != null) {
			m_leakTracker.record(hint);
		}
		return this;
	}

	@Override
	protected void deallocate() {
		if (m_leakTracker != null) {
			m_leakTracker.close(this);
			m_leakTracker = null;
		}
		m_eventGroup = null;
		if (m_result instanceof CauseWrapper) {
			((CauseWrapper)m_result).recycle();
		}
		m_result = null;

		setRefCnt(1);
		m_recylerHandle.recycle(this);
	}

	@Override
	public RCPromise<T> retain() {
		super.retain();
		return this;
	}

	private static class CauseWrapper {
		private final Recycler.Handle<CauseWrapper> m_handle;
		private Throwable cause;

		CauseWrapper(Recycler.Handle<CauseWrapper> handle) {
			m_handle = handle;
		}

		void set(Throwable cause) {
			this.cause = cause;
		}

		public void recycle() {
			m_handle.recycle(this);
		}
	}

	private static final Recycler<ListenerWrapper> LISTENER_RECYCLER = new Recycler<ListenerWrapper>() {
		@Override
		protected ListenerWrapper newObject(Handle<ListenerWrapper> handle) {
			return new ListenerWrapper(handle);
		}
	};
	private static class ListenerWrapper<T> implements Runnable {
		private final Recycler.Handle<ListenerWrapper> m_handle;
		RCPromise<T> promise;
		RCFutureListener<T> listener;
		ParamBag params;

		ListenerWrapper(Recycler.Handle<ListenerWrapper> handle) {
			m_handle = handle;
		}

		void init(RCPromise<T> promise, RCFutureListener<T> listener, ParamBag params) {
			this.promise = promise;
			this.listener = listener;
			this.params = params;
		}

		void recycle() {
			this.promise = null;
			this.listener = null;
			this.params = null;
			m_handle.recycle(this);
		}

		@Override
		public void run() {
			promise.notifyListener0(listener, params);
			promise.release();
			recycle();
		}
	}
}
