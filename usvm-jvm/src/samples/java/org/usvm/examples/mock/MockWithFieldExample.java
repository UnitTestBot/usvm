package org.usvm.examples.mock;

import org.usvm.examples.mock.others.VersionStamp;

public class MockWithFieldExample {
    public VersionStamp checkAndUpdate(VersionStamp stamp) {
        if (stamp.initial > stamp.version) {
            stamp.version = stamp.initial;
        } else {
            stamp.version = stamp.version + 1;
        }
        return stamp;
    }
}
