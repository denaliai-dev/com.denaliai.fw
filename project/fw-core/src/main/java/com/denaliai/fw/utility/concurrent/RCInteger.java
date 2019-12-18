package com.denaliai.fw.utility.concurrent;

import io.netty.util.*;

public class RCInteger extends AbstractReferenceCounted {
	private static final ResourceLeakDetector<RCInteger> LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(RCInteger.class, 1);
	private static final Recycler<RCInteger> RECYCLER = new Recycler<RCInteger>() {
		@Override
		protected RCInteger newObject(Recycler.Handle<RCInteger> handle) {
			return new RCInteger(handle);
		}
	};

	private final Recycler.Handle<RCInteger> m_handle;
	private ResourceLeakTracker<RCInteger> m_leakTracker;
	private int m_value;

	private RCInteger(Recycler.Handle<RCInteger> handle) {
		m_handle = handle;
	}

	private RCInteger init(int value) {
		m_leakTracker = LEAK_DETECT.track(this);
		m_value = value;
		return this;
	}

	public static RCInteger create(int value) {
		return RECYCLER.get().init(value);
	}

	public int value() {
		return m_value;
	}

	public int increment() {
		m_value++;
		return m_value;
	}
	public int decrement() {
		m_value--;
		return m_value;
	}

	public void value(int value) {
		m_value = value;
	}

	@Override
	public RCInteger retain() {
		super.retain();
		return this;
	}

	@Override
	public RCInteger touch(Object hint) {
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

		setRefCnt(1);
		m_handle.recycle(this);
	}
}
