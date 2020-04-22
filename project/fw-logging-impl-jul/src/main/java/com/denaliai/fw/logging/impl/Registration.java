package com.denaliai.fw.logging.impl;

import com.denaliai.fw.logging.LoggingImplRegistration;

public class Registration {
	static {
		LoggingImplRegistration.register(new JavaUtilLogAdapter());
	}
}
