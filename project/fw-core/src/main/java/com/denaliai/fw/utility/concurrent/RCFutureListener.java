package com.denaliai.fw.utility.concurrent;

public interface RCFutureListener<T> {
	void operationComplete(RCFuture<T> f, ParamBag params);
}
