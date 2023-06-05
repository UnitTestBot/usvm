package org.usvm.examples.mock;

import static org.usvm.api.mock.UMockKt.assume;
import org.usvm.examples.mock.others.Network;

public class UseNetwork {
    public static int readBytes(byte[] packet, Network network) {
        int res = 0;
        int c;
        while ((c = network.nextByte()) != -1) {
            packet[res++] = (byte)c;
        }
        return res;
    }

    public void mockVoidMethod(Network network) {
        assume(network != null);

        network.voidMethod();
    }
}