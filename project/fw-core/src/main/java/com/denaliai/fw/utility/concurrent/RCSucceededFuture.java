package com.denaliai.fw.utility.concurrent;

import io.netty.util.*;

public class RCSucceededFuture<T> extends AbstractReferenceCounted implements RCFuture<T> {
	private static final ResourceLeakDetector<RCSucceededFuture> PROMISE_LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(RCSucceededFuture.class, 1);
	private static final Recycler<RCSucceededFuture> PROMISE_RECYCLER = new Recycler<RCSucceededFuture>() {
		@Override
		protected RCSucceededFuture newObject(Handle<RCSucceededFuture> handle) {
			return new RCSucceededFuture(handle);
		}
	};

	private final Recycler.Handle<RCSucceededFuture> m_recylerHandle;

	private ResourceLeakTracker<RCSucceededFuture> m_leakTracker;
	private T m_result;

	private RCSucceededFuture(Recycler.Handle<RCSucceededFuture> recylerHandle) {
		m_recylerHandle = recylerHandle;
	}

	@SuppressWarnings("unchecked")
	public static <T> RCSucceededFuture<T> create(T result) {
		RCSucceededFuture<T> promise = (RCSucceededFuture<T>)PROMISE_RECYCLER.get();
		promise.init(result);
		return promise;
	}

	private void init(T result) {
		m_leakTracker = PROMISE_LEAK_DETECT.track(this);
		m_result = result;
	}

	@Override
	public RCSucceededFuture<T> addListener(RCFutureListener<T> listener, ParamBag params) {
		// Just call it directly
		listener.operationComplete(this, params);
		return this;
	}

	@Override
	public RCSucceededFuture<T> addListener(RCFutureListener<T> listener) {
		// Just call it directly
		listener.operationComplete(this, null);
		return this;
	}

	/**
	 * Caller must have a ref count on this object when calling
	 * this method.
	 *
	 */
	@Override
	public boolean await(long timeoutInMS) {
		return true;
	}

	@Override
	public T get() {
		return m_result;
	}

	@Override
	public Throwable cause() {
		return null;
	}

	@Override
	public boolean isSuccess() {
		return true;
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		if (m_leakTracker != null) {
			m_leakTracker.record(hint);
		}
		return this;
	}

	@Override
	public RCSucceededFuture<T> retain() {
		super.retain();
		return this;
	}

	@Override
	protected void deallocate() {
		if (m_leakTracker != null) {
			m_leakTracker.close(this);
		}
		m_result = null;

		setRefCnt(1);
		m_recylerHandle.recycle(this);
	}

}
