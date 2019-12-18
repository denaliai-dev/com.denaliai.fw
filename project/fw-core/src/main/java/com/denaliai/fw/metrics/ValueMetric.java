package com.denaliai.fw.metrics;

public class ValueMetric extends SingleIndexMetricBase {
	private final String m_consumerKey_Count;

	ValueMetric(String name, int index) {
		super(name, index);
		m_consumerKey_Count = name + ".value";
	}

	public void add(int value) {
		MetricsEngine.add(index, value);
	}

	@Override
	void report(long[] snapshotData, long snapshotDurationInNS, MetricsEngine.ISnapshotConsumer consumer) {
		consumer.apply(m_consumerKey_Count, snapshotData[index]);
	}
}
