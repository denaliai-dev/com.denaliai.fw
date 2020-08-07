package com.denaliai.fw.metrics;

abstract class MetricBase {
	private final String m_name;

	MetricBase(String name) {
		m_name = name;
	}

	public final String getName() {
		return m_name;
	}

	abstract void addDataToSnapshot(long[] snapshotData, long[] instanceData);
	abstract void resetDataForNextSnapshot(long[] snapshotData);
	abstract void report(long[] snapshotData, long snapshotDurationMS, MetricsEngine.ISnapshotConsumer consumer);
	void ensureInitialized(long[] data) {
	}
}
