package com.denaliai.fw.metrics;

public class TotalCounterMetric extends SingleIndexMetricBase {
	private final String m_consumerKey_Count;

	TotalCounterMetric(String name, int index) {
		super(name, index);
		m_consumerKey_Count = name + ".count";
	}

	public void increment() {
		MetricsEngine.increment(index);
	}
	public void decrement() {
		MetricsEngine.decrement(index);
	}
	public void add(int value) {
		MetricsEngine.add(index, value);
	}

	@Override
	void resetDataForNextSnapshot(long[] snapshotData) {
		// We need to leave the data as a running total
	}

	@Override
	void report(long[] snapshotData, long snapshotDurationInNS, MetricsEngine.ISnapshotConsumer consumer) {
		consumer.apply(m_consumerKey_Count, snapshotData[index]);
	}
}
