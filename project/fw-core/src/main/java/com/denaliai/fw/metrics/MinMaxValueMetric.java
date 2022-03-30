package com.denaliai.fw.metrics;

public class MinMaxValueMetric extends MetricBase {
	private final String m_consumerKey_Min;
	private final String m_consumerKey_Max;
	final int initIndex;
	final int indexOfMin;
	final int indexOfMax;

	MinMaxValueMetric(String name, int initIndex, int indexOfMin, int indexOfMax) {
		super(name);
		this.initIndex = initIndex;
		this.indexOfMin = indexOfMin;
		this.indexOfMax = indexOfMax;
		if (initIndex >= indexOfMin || indexOfMin >= indexOfMax) {
			throw new IllegalArgumentException("indexOfMax must be the largest index passed in");
		}
		m_consumerKey_Min = name + ".min";
		m_consumerKey_Max = name + ".max";
	}

	public void set(long value) {
		MetricsEngine.set(this, value);
	}

	@Override
	void ensureInitialized(long[] data) {
		if (indexOfMax >= data.length) {
			return;
		}
		if (data[initIndex] == 0) {
			resetDataForNextSnapshot(data);
		}
	}

	@Override
	void addDataToSnapshot(long[] snapshotData, long[] instanceData) {
		if (indexOfMax >= instanceData.length) {
			return;
		}
		// 2 indicates data is written
		if (instanceData[initIndex] != 2) {
			return;
		}
		snapshotData[initIndex] = 2;
		if (instanceData[indexOfMin] < snapshotData[indexOfMin]) {
			snapshotData[indexOfMin] = instanceData[indexOfMin];
		}
		if (instanceData[indexOfMax] > snapshotData[indexOfMax]) {
			snapshotData[indexOfMax] = instanceData[indexOfMax];
		}
	}

	@Override
	void resetDataForNextSnapshot(long[] snapshotData) {
		// 1 indicates that we are initialized, but without data
		snapshotData[initIndex] = 1;
		snapshotData[indexOfMin] = Long.MAX_VALUE;
		snapshotData[indexOfMax] = Long.MIN_VALUE;
	}

	@Override
	void report(long[] snapshotData, long snapshotDurationMS, MetricsEngine.ISnapshotConsumer consumer) {
		// Init 2 indicates that there is data written
		if (snapshotData[initIndex] != 2) {
			consumer.apply(m_consumerKey_Min, 0L);
			consumer.apply(m_consumerKey_Max, 0L);
		} else {
			consumer.apply(m_consumerKey_Min, snapshotData[indexOfMin]);
			consumer.apply(m_consumerKey_Max, snapshotData[indexOfMax]);
		}
	}
}
