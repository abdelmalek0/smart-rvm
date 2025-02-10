package com.smartprints_ksa.bottle.rvm;


import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadManager {

    private static ThreadPollProxy mThreadPollProxy;

    public static ThreadPollProxy getThreadPollProxy() {
        synchronized (ThreadPollProxy.class) {
            if (mThreadPollProxy == null) {
                mThreadPollProxy = new ThreadPollProxy(3, 6, 1000);
            }
        }
        return mThreadPollProxy;
    }

    public static class ThreadPollProxy {
        private ThreadPoolExecutor poolExecutor;
        private int corePoolSize;
        private int maximumPoolSize;
        private long keepAliveTime;

        public ThreadPollProxy(int corePoolSize, int maximumPoolSize, long keepAliveTime) {
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.keepAliveTime = keepAliveTime;
        }

        public void execute(Runnable r) {
            if (poolExecutor == null || poolExecutor.isShutdown()) {
                poolExecutor = new ThreadPoolExecutor(
                        corePoolSize,
                        maximumPoolSize,
                        keepAliveTime,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<Runnable>(),
                        Executors.defaultThreadFactory());
            }
            poolExecutor.execute(r);
        }
    }
}

