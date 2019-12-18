package com.denaliai.fw.metrics;

abstract class SingleIndexMetricBase extends MetricBase {
	protected final int index;

	SingleIndexMetricBase(String name, int index) {
		super(name);
		this.index = index;
	}

	@Override
	void addDataToSnapshot(long[] snapshotData, long[] instanceData) {
		if (index >= instanceData.length) {
			return;
		}
		snapshotData[index] += instanceData[index];
	}

	@Override
	void resetDataForNextSnapshot(long[] snapshotData) {
		snapshotData[index] = 0;
	}

}
