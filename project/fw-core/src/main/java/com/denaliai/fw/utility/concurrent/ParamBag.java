package com.denaliai.fw.utility.concurrent;

import io.netty.util.*;

public class ParamBag extends AbstractReferenceCounted {
	private static final int MAX_NUM_PARAMS = Integer.parseInt(System.getProperty("com.bk.db.concurrent.ParamBag.max-num-params", "24"));
	private static final ResourceLeakDetector<ParamBag> LEAK_DETECT = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(ParamBag.class, 1);
	private static final Recycler<ParamBag> RECYCLER = new Recycler<ParamBag>() {
		@Override
		protected ParamBag newObject(Handle<ParamBag> handle) {
			return new ParamBag(handle);
		}
	};
	private final Recycler.Handle<ParamBag> m_recylerHandle;
	private final int[] m_ints = new int[MAX_NUM_PARAMS];
	private final long[] m_longs = new long[MAX_NUM_PARAMS];
	private final Object[] m_objs = new Object[MAX_NUM_PARAMS];
	private int m_stackTop;

	private ResourceLeakTracker<ParamBag> m_leakTracker;
	private ParameterizedCallback m_callback;
//	private Object m_creationThreadContext;
//	private Object m_prevThreadContext;

	private ParamBag(Recycler.Handle<ParamBag> handle) {
		m_recylerHandle = handle;
	}

	public static ParamBag create(ParameterizedCallback callback) {
		ParamBag bag = RECYCLER.get();
		bag.init(callback);
		return bag;
	}

	public static ParamBag create() {
		ParamBag bag = RECYCLER.get();
		bag.init();
		return bag;
	}

	private void init() {
		m_leakTracker = LEAK_DETECT.track(this);
//		m_creationThreadContext = Engine.copyThreadContext();
	}

	private void init(ParameterizedCallback callback) {
		m_leakTracker = LEAK_DETECT.track(this);
		setCallback(callback);
//		m_creationThreadContext = Engine.copyThreadContext();
	}

	public ParamBag setCallback(ParameterizedCallback callback) {
		m_callback = callback;
		return this;
	}

	public ParamBag pi(int value) {
		m_ints[m_stackTop++] = value;
		return this;
	}
	public int pi() {
		return m_ints[--m_stackTop];
	}

	public ParamBag pl(long value) {
		m_longs[m_stackTop++] = value;
		return this;
	}
	public long pl() {
		return m_longs[--m_stackTop];
	}

	public ParamBag po(Object value) {
		m_objs[m_stackTop++] = value;
		return this;
	}

	@SuppressWarnings("unchecked")
	public <T> T po() {
		return (T)m_objs[--m_stackTop];
	}

//
//	public Object pushThreadContext() {
//		// Need to add-ref so we can stay alive until pop
//		retain();
//		return Engine.pushMigratedThreadContext(m_creationThreadContext);
//	}
//
//	public ParamBag popThreadContext(Object prevTC) {
//		Engine.popMigratedThreadContext(prevTC);
//		// Release retain() done in pushThreadContext()
//		release();
//		return this;
//	}

	void run() {
		m_callback.callback(this);
	}

	@Override
	protected void deallocate() {
		if (m_leakTracker != null) {
			m_leakTracker.close(this);
			m_leakTracker = null;
		}
		for(int i=0; i<m_stackTop; i++) {
			Object o = m_objs[i];
			if (o != null) {
				ReferenceCountUtil.safeRelease(o, 1);
				m_objs[i] = null;
			}
		}
		m_stackTop = 0;
		m_callback = null;
//		m_creationThreadContext = null;

		setRefCnt(1);
		m_recylerHandle.recycle(this);
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		if (m_leakTracker != null) {
			m_leakTracker.record(hint);
		}
		return this;
	}

	@Override
	public ParamBag retain() {
		return (ParamBag)super.retain();
	}
}
