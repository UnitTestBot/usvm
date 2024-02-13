package org.usvm.samples.approximations;

import org.jacodb.approximation.annotation.Approximate;

import java.util.Map;

@SuppressWarnings("ALL")
@Approximate(jdk.internal.misc.VM.class)
public class TestVMApproximation {

    // the init level when the VM is fully initialized
    private static final int JAVA_LANG_SYSTEM_INITED = 1;
    private static final int MODULE_SYSTEM_INITED = 2;
    private static final int SYSTEM_LOADER_INITIALIZING = 3;
    private static final int SYSTEM_BOOTED = 4;
    private static final int SYSTEM_SHUTDOWN = 5;


    // 0, 1, 2, ...
    private static volatile int initLevel = 0;
    private static final Object lock = new Object();

    private static long directMemory = 64 * 1024 * 1024;

    // buffers, allocated by ByteBuffer.allocateDirect, to be page aligned.
    private static boolean pageAlignDirectMemory;

    private static Map<String, String> savedProps;

    /* Current count of objects pending for finalization */
    private static volatile int finalRefCount;

    /* Peak count of objects pending for finalization */
    private static volatile int peakFinalRefCount;

    /* The threadStatus field is set by the VM at state transition
     * in the hotspot implementation. Its value is set according to
     * the JVM TI specification GetThreadState function.
     */
    private static final int JVMTI_THREAD_STATE_ALIVE = 0x0001;
    private static final int JVMTI_THREAD_STATE_TERMINATED = 0x0002;
    private static final int JVMTI_THREAD_STATE_RUNNABLE = 0x0004;
    private static final int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
    private static final int JVMTI_THREAD_STATE_WAITING_INDEFINITELY = 0x0010;
    private static final int JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT = 0x0020;
}
