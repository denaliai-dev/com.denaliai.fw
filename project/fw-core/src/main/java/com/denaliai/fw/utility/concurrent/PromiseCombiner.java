package com.denaliai.fw.utility.concurrent;

import io.netty.util.concurrent.Future;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class PromiseCombiner {
	private static final AtomicIntegerFieldUpdater<PromiseCombiner> DONE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(PromiseCombiner.class, "m_numDone");
	private static final AtomicIntegerFieldUpdater<PromiseCombiner> EXPECTED_UPDATER = AtomicIntegerFieldUpdater.newUpdater(PromiseCombiner.class, "m_expectedCount");
	private final ThisIter m_iter = new ThisIter();
	private IPromiseCombinerCallback m_callback;
	private ParamBag m_callbackBag;
	private volatile int m_expectedCount;
	private volatile boolean m_doneAdding;
	private volatile int m_numDone;
	private FutureSlot m_current;
	private FutureSlot m_front;

	public PromiseCombiner() {
	}

	/**
	 * This passes the current ref into this object which owns it and will release() it at the end
	 */
	@SuppressWarnings("unchecked")
	public void add(Future f) {
		if (m_front == null) {
			m_current = m_front = new FutureSlot(null);
		} else if (m_current.next == null){
			m_current.next = new FutureSlot(null);
			m_current = m_current.next;
		} else {
			m_current = m_current.next;
		}
		final FutureSlot myFutureSlot = m_current;
		m_current.future = f;
		EXPECTED_UPDATER.incrementAndGet(this);
		f.addListener((future) -> {
			if (future.isSuccess()) {
				myFutureSlot.result = f.get();
			} else {
				myFutureSlot.result = f.cause();
			}
			operationComplete();
		});
	}

	public void finished(IPromiseCombinerCallback callback) {
		m_callback = callback;
		// This order guarantees the LAST caller to operationComplete() will be the one to make the callback
		EXPECTED_UPDATER.incrementAndGet(this);
		m_doneAdding = true;
		operationComplete();
	}

	private void operationComplete() {
		int numDone = DONE_UPDATER.incrementAndGet(PromiseCombiner.this);
		if (m_doneAdding && numDone==m_expectedCount) {
			m_iter.reset(m_front);
			m_callback.allDone(m_iter, m_callbackBag);
			m_callbackBag = null;
		}
	}

	private static class FutureSlot {
		private FutureSlot next;
		Future<?> future;
		Object result;

		FutureSlot(FutureSlot next) {
			this.next = next;
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

	public interface IPromiseCombinerCallback {
		/**
		 * Never keep a reference to Iterator results, only use it in this method
		 */
		void allDone(Iterator<Object> results, ParamBag bag);
	}
}
