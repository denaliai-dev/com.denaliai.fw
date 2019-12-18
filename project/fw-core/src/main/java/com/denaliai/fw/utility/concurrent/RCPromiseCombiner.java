package com.denaliai.fw.utility.concurrent;

import io.netty.util.*;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class RCPromiseCombiner extends AbstractReferenceCounted {
	private static final ResourceLeakDetector<RCPromiseCombiner> LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(RCPromiseCombiner.class, 1);
	private static final Recycler<RCPromiseCombiner> RECYCLER = new Recycler<RCPromiseCombiner>() {
		@Override
		protected RCPromiseCombiner newObject(Handle<RCPromiseCombiner> handle) {
			return new RCPromiseCombiner(handle);
		}
	};

	private static final AtomicIntegerFieldUpdater<RCPromiseCombiner> DONE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RCPromiseCombiner.class, "m_numDone");
	private static final AtomicIntegerFieldUpdater<RCPromiseCombiner> EXPECTED_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RCPromiseCombiner.class, "m_expectedCount");
	private final RCFutureListener<?> m_listener = new RCFutureListener<Object>() {
		@Override
		public void operationComplete(RCFuture<Object> f, ParamBag bag) {
			FutureSlot slot = bag.po();
			bag.release();
			if (f.isSuccess()) {
				slot.result = f.get();
			} else {
				slot.result = f.cause();
			}
			RCPromiseCombiner.this.operationComplete();
			RCPromiseCombiner.this.release();
		}
	};
	private final Recycler.Handle<RCPromiseCombiner> m_handle;
	private final ThisIter m_iter = new ThisIter();
	private ResourceLeakTracker<RCPromiseCombiner> m_leakTracker;
	private IRCPromiseCombinerCallback m_callback;
	private ParamBag m_callbackBag;
	private volatile int m_expectedCount;
	private volatile boolean m_doneAdding;
	private volatile int m_numDone;
	private FutureSlot m_current;
	private FutureSlot m_front;

	private RCPromiseCombiner(Recycler.Handle<RCPromiseCombiner> handle) {
		m_handle = handle;
	}

	public static RCPromiseCombiner create() {
		RCPromiseCombiner c = RECYCLER.get();
		c.init();
		return c;
	}

	private void init() {
		m_leakTracker = LEAK_DETECT.track(this);
	}

	/**
	 * This passes the current ref into this object which owns it and will release() it at the end
	 */
	public void add(RCFuture f) {
		if (m_front == null) {
			m_current = m_front = new FutureSlot(null);
		} else if (m_current.next == null){
			m_current.next = new FutureSlot(null);
			m_current = m_current.next;
		} else {
			m_current = m_current.next;
		}
		m_current.future = f;
		EXPECTED_UPDATER.incrementAndGet(this);
		// Each listener gets a reference
		retain();
		f.addListener(m_listener, ParamBag.create().po(m_current));
	}

	@SuppressWarnings("unchecked")
	public void finished(IRCPromiseCombinerCallback callback, ParamBag bag) {
		m_callbackBag = bag;
		m_callback = callback;
		// This order guarantees the LAST caller to the listener will be the one to make the callback
		EXPECTED_UPDATER.incrementAndGet(this);
		m_doneAdding = true;
		operationComplete();
	}

	private void operationComplete() {
		int numDone = DONE_UPDATER.incrementAndGet(RCPromiseCombiner.this);
		if (m_doneAdding && numDone==m_expectedCount) {
			m_iter.reset(m_front);
			m_callback.allDone(m_iter, m_callbackBag);
			m_callbackBag = null;
		}
	}

	@Override
	protected void deallocate() {
		m_expectedCount = 0;
		m_numDone = 0;
		m_doneAdding = false;
		m_current = m_front;
		m_callback = null;
		m_callbackBag = null;
		FutureSlot fs = m_front;
		while(fs != null) {
			if (fs.future == null) {
				break;
			}
			fs.clear();
			fs = fs.next;
		}
		m_leakTracker.close(this);
		setRefCnt(1);
		m_handle.recycle(this);
	}

	@Override
	public RCPromiseCombiner touch(Object hint) {
		m_leakTracker.record(hint);
		return this;
	}

	private static class FutureSlot {
		private FutureSlot next;
		RCFuture<?> future;
		Object result;

		FutureSlot(FutureSlot next) {
			this.next = next;
		}

		void clear() {
			future.release();
			future = null;
			result = null;
		}
	}
	private class ThisIter implements Iterator<Object> {
		private FutureSlot m_iterItem;

		void reset(FutureSlot start) {
			m_iterItem = start;
		}

		@Override
		public boolean hasNext() {
			// If we are not null and the last used future slot next does not point at the current iter item
			return m_iterItem != null && m_current.next != m_iterItem;
		}

		@Override
		public Object next() {
			Object n = m_iterItem.result;
			m_iterItem = m_iterItem.next;
			return n;
		}
	}

	public interface IRCPromiseCombinerCallback {
		/**
		 * Never keep a reference to Iterator results, only use it in this method
		 */
		void allDone(Iterator<Object> results, ParamBag bag);
	}
}
