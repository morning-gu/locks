package org.gun.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

public class NonReentrantReadWriteLock implements ReadWriteLock {

    private final ReadLock readLock;
    private final WriteLock writeLock;
    private final Sync sync;

    public NonReentrantReadWriteLock() {
        this.sync = new Sync();
        this.readLock = new ReadLock(sync);
        this.writeLock = new WriteLock(sync);
    }

    @Override
    public Lock readLock() {
        return readLock;
    }

    @Override
    public Lock writeLock() {
        return writeLock;
    }

    private static final class Sync extends AbstractQueuedSynchronizer {
        static final int SHARED_SHIFT = 16;
        static final int SHARED_UNIT = (1 << SHARED_SHIFT);
        static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        static int sharedCount(int c) {
            return c >>> SHARED_SHIFT;
        }

        static int exclusiveCount(int c) {
            return c & EXCLUSIVE_MASK;
        }

        @Override
        protected boolean tryAcquire(int acquires) {
            Thread current = Thread.currentThread();
            int c = getState();
            int w = exclusiveCount(c);

            if (c != 0) {
                // If a read lock or write lock is already held
                if (w == 0 || null != getExclusiveOwnerThread()) {
                    return false;
                }
            }

            if (w + acquires > EXCLUSIVE_MASK) {
                throw new Error("Maximum lock count exceeded");
            }

            setState(c + acquires);
            setExclusiveOwnerThread(current);
            return true;
        }

        @Override
        protected boolean tryRelease(int releases) {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }

            int c = getState() - releases;
            boolean free = exclusiveCount(c) == 0;
            if (free) {
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            // If there is a write lock (regardless of whether it is held by the current thread), acquiring a read lock will fail.
            if (exclusiveCount(getState()) != 0) {
                return -1;
            }

            int c = getState();
            int r = sharedCount(c);
            if (r == MAX_COUNT) {
                throw new Error("Maximum lock count exceeded");
            }

            if (compareAndSetState(c, c + SHARED_UNIT)) {
                return 1;
            }
            return -1;
        }

        @Override
        protected boolean tryReleaseShared(int releases) {
            for (; ; ) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc)) {
                    return nextc == 0;
                }
            }
        }

        @Override
        protected boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        Condition newCondition() {
            return new ConditionObject();
        }
    }

    private static final class ReadLock implements Lock {
        private final Sync sync;

        ReadLock(Sync sync) {
            this.sync = sync;
        }

        @Override
        public void lock() {
            sync.acquireShared(1);
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        @Override
        public boolean tryLock() {
            return sync.tryAcquireShared(1) >= 0;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(time));
        }

        @Override
        public void unlock() {
            sync.releaseShared(1);
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class WriteLock implements Lock {
        private final Sync sync;

        WriteLock(Sync sync) {
            this.sync = sync;
        }

        @Override
        public void lock() {
            sync.acquire(1);
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        @Override
        public boolean tryLock() {
            return sync.tryAcquire(1);
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(time));
        }

        @Override
        public void unlock() {
            sync.release(1);
        }

        @Override
        public Condition newCondition() {
            return sync.newCondition();
        }
    }
}
