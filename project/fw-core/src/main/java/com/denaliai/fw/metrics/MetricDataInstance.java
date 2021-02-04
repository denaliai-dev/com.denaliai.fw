package com.denaliai.fw.metrics;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

final class MetricDataInstance {
	private final WeakReference<Thread> m_owner;
	private final int m_snapshotNumber;
	private final AtomicInteger m_lock = new AtomicInteger();
	private long[] m_data;
	private volatile boolean m_isOld;

	MetricDataInstance(int numDataElements, int snapshotNumber) {
		m_owner = new WeakReference<>(Thread.currentThread());
		m_data = new long[numDataElements];
		m_snapshotNumber = snapshotNumber;
	}

	public boolean ownerStillExists() {
		return m_owner.get() != null;
	}

	void processData(long[] snapshotData, MetricBase m) {
		m.ensureInitialized(snapshotData);
		m.ensureInitialized(m_data);
		m.addDataToSnapshot(snapshotData, m_data);
	}

	final boolean lock() {
		if (m_lock.compareAndSet(0, 1) == false) {
			return false;
		}
//		if (m_isOld || m_snapshotNumber != MetricsEngine.currentSnapshotNumber()) {
		if (m_isOld) {
			m_lock.set(0);
			return false;
		}
		return true;
	}

	final void unlock() {
		m_lock.set(0);
	}

	final boolean markOld() {
		if (m_lock.compareAndSet(0, 1) == false) {
			return false;
		}
		m_isOld = true;
		m_lock.set(0);
		return true;
	}

	private void resizeData() {
		long[] newData = new long[MetricsEngine.maxDataIndex()];
		System.arraycopy(m_data, 0, newData, 0, m_data.length);
		m_data = newData;
	}

	public boolean add(int index, int value) {
		if (!lock()) {
			return false;
		}
		if (index >= m_data.length) {
			// The caller was loaded AFTER this data instance was created, we can only ignore the data for now
			resizeData();
		}
		m_data[index] += value;
		unlock();
		return true;
	}

	public boolean increment(int index) {
		if (!lock()) {
			return false;
		}
		if (index >= m_data.length) {
			// The caller was loaded AFTER this data instance was created, we can only ignore the data for now
			resizeData();
		}
		m_data[index]++;
		unlock();
		return true;
	}

	public boolean decrement(int index) {
		if (!lock()) {
			return false;
		}
		if (index >= m_data.length) {
			// The caller was loaded AFTER this data instance was created, we can only ignore the data for now
			resizeData();
		}
		m_data[index]--;
		unlock();
		return true;
	}

	public boolean record(DurationRateMetric m, MetricsEngine.TimerInstance timer) {
		if (!lock()) {
			return false;
		}
		if (m.indexOfMax >= m_data.length) {
			// The caller was loaded AFTER this data instance was created, we can only ignore the data for now
			resizeData();
		}
		if (m_data[m.initIndex] == 0) {
			m.resetDataForNextSnapshot(m_data);
		}
		long dur = timer.duration();
		if (dur < m_data[m.indexOfMin]) {
			m_data[m.indexOfMin] = dur;
		}
		if (dur > m_data[m.indexOfMax]) {
			m_data[m.indexOfMax] = dur;
		}
		m_data[m.indexOfCount]++;
		m_data[m.indexOfTotal] += dur;
		unlock();
		return true;
	}

	public boolean set(MinMaxAvgValueMetric m, int value) {
		if (!lock()) {
			return false;
		}
		if (m.indexOfMax >= m_data.length) {
			// The caller was loaded AFTER this data instance was created, we can only ignore the data for now
			resizeData();
		}
		if (m_data[m.initIndex] == 0) {
			m.resetDataForNextSnapshot(m_data);
		}
		if (value < m_data[m.indexOfMin]) {
			m_data[m.indexOfMin] = value;
		}
		if (value > m_data[m.indexOfMax]) {
			m_data[m.indexOfMax] = value;
		}
		m_data[m.indexOfCount]++;
		m_data[m.indexOfTotal] += value;
		unlock();
		return true;
	}
}
