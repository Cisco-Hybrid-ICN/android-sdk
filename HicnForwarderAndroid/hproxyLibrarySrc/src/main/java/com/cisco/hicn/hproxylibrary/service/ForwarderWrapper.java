package com.cisco.hicn.hproxylibrary.service;

import java.io.Serializable;

public abstract class ForwarderWrapper implements Serializable {
    public abstract boolean isRunningForwarder();
}
