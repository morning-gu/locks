package org.gun.locks;

import org.junit.Test;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class NonReentrantReadWriteLockTest {

    private final NonReentrantReadWriteLock lock = new NonReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    @Test
    public void testReadLockAcquireRelease() {
        assertTrue(readLock.tryLock());
        readLock.unlock();
    }

    @Test
    public void testMultipleReadLocks() {
        assertTrue(readLock.tryLock());
        assertTrue(readLock.tryLock());
        readLock.unlock();
        readLock.unlock();
    }

    @Test
    public void testWriteLockAcquireRelease() {
        assertTrue(writeLock.tryLock());
        writeLock.unlock();
    }

    @Test
    public void testWriteLockExclusivity() {
        writeLock.lock();
        assertFalse(readLock.tryLock());
        writeLock.unlock();
    }

    @Test
    public void testReadLockBlocksWrite() {
        readLock.lock();
        assertFalse(writeLock.tryLock());
        readLock.unlock();
    }

    @Test
    public void testWriteLockReentrancyNotAllowed() {
        writeLock.lock();
        assertFalse(writeLock.tryLock());
        writeLock.unlock();
    }

    @Test
    public void testLockInterruptibly() throws InterruptedException {
        Thread testThread = new Thread(() -> {
            try {
                writeLock.lockInterruptibly();
                try {
                    assertTrue(writeLock.tryLock()); // This should fail since it's non-reentrant
                } finally {
                    writeLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        testThread.start();
        testThread.interrupt();
        testThread.join(100);
        assertFalse(testThread.isAlive());
    }

    @Test
    public void testReadLockInterruptibly() throws Exception {
        // Test interruption
        Thread testThread = new Thread(() -> {
            try {
                readLock.lockInterruptibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        readLock.lock(); // Main thread holds the read lock
        testThread.start();
        testThread.interrupt();
        testThread.join();
        readLock.unlock();
        assertFalse(testThread.isAlive());
    }

    @Test
    public void testReadLockTryLockWithTimeout() throws Exception {
        // Test failure when write lock is held
        Lock writeLock = lock.writeLock();
        writeLock.lock();

        AtomicBoolean acquired = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread testThread = new Thread(() -> {
            try {
                acquired.set(readLock.tryLock(50, TimeUnit.MILLISECONDS));
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        testThread.start();
        latch.await();
        assertFalse("Read lock should not be acquired when write locked", acquired.get());
        writeLock.unlock();
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockWithoutLock() {
        writeLock.lock();
        writeLock.unlock();
        writeLock.unlock();
    }

    @Test
    public void testTryLockWithTimeout() throws InterruptedException {
        assertTrue(writeLock.tryLock(100, TimeUnit.MILLISECONDS));
        writeLock.unlock();
    }

    @Test
    public void testConditionSupportForWriteLock() {
        writeLock.lock();
        assertNotNull(writeLock.newCondition());
        writeLock.unlock();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConditionNotSupportedForReadLock() {
        readLock.newCondition();
    }

    @Test
    public void testConditionAwaitSignal() throws InterruptedException {
        Condition condition = writeLock.newCondition();

        writeLock.lock();
        try {
            Thread signalThread = new Thread(() -> {
                writeLock.lock();
                try {
                    condition.signal();
                } finally {
                    writeLock.unlock();
                }
            });

            signalThread.start();
            condition.await();
            signalThread.join();
        } finally {
            writeLock.unlock();
        }
    }

    @Test
    public void testConditionAwaitTimeout() throws InterruptedException {
        Condition condition = writeLock.newCondition();

        writeLock.lock();
        try {
            long start = System.currentTimeMillis();
            boolean result = condition.await(100, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - start;

            assertFalse(result);
            assertTrue(elapsed >= 100);
        } finally {
            writeLock.unlock();
        }
    }

    @Test
    public void testConditionSignalAll() throws InterruptedException {
        Condition condition = writeLock.newCondition();
        final boolean[] signaled = {false, false};

        Thread t1 = new Thread(() -> {
            writeLock.lock();
            try {
                condition.await();
                signaled[0] = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                writeLock.unlock();
            }
        });

        Thread t2 = new Thread(() -> {
            writeLock.lock();
            try {
                condition.await();
                signaled[1] = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                writeLock.unlock();
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(100); // Ensure threads are waiting
        writeLock.lock();
        condition.signalAll();
        writeLock.unlock();
        t1.join();
        t2.join();

        assertTrue(signaled[0]);
        assertTrue(signaled[1]);
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testConditionWithoutLockThrowsException() throws InterruptedException {
        NonReentrantReadWriteLock lock = new NonReentrantReadWriteLock();
        Condition condition = lock.writeLock().newCondition();
        condition.await();
    }

    @Test
    public void testConcurrentReadLocks() throws InterruptedException {
        int threads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    readLock.lock();
                    try {
                        Thread.sleep(50);
                    } finally {
                        readLock.unlock();
                        finishLatch.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testWriteLockExclusion() throws InterruptedException {
        writeLock.lock();

        Thread t = new Thread(() -> {
            assertFalse(readLock.tryLock());
            assertFalse(writeLock.tryLock());
        });

        t.start();
        t.join();

        writeLock.unlock();
    }

    @Test
    public void testLockOrdering() throws InterruptedException {
        writeLock.lock();
        CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            assertFalse(readLock.tryLock());
            latch.countDown();
        });

        reader.start();
        latch.await();
        writeLock.unlock();

        // Now reader should be able to acquire
        assertTrue(readLock.tryLock());
        readLock.unlock();
    }

    @Test
    public void testTryAcquireWithExistingReadLock() {
        readLock.lock();
        try {
            assertFalse(writeLock.tryLock()); // Should fail due to existing read lock
        } finally {
            readLock.unlock();
        }
    }

    @Test
    public void testTryAcquireWithExistingWriteLock() {
        writeLock.lock();
        try {
            assertFalse(writeLock.tryLock()); // Should fail due to non-reentrancy
        } finally {
            writeLock.unlock();
        }
    }

    @Test
    public void testTryAcquireSharedWithActiveWriter() {
        writeLock.lock();
        try {
            assertFalse(readLock.tryLock());
        } finally {
            writeLock.unlock();
        }
    }


    @Test
    public void testTryReleaseSharedWithMultipleReaders() {
        readLock.lock();
        readLock.lock();
        readLock.unlock(); // Shouldn't release fully yet
        readLock.unlock(); // Now fully released

        // Verify by trying to get write lock (should succeed if read locks are fully released)
        assertTrue(writeLock.tryLock());
        writeLock.unlock();
    }

    @Test
    public void testIsHeldExclusively() {
        writeLock.lock();
        try {
            assertFalse(writeLock.tryLock()); // Verify non-reentrancy
        } finally {
            writeLock.unlock();
        }
    }

    @Test
    public void testTryReleaseSharedWithNoReaders() {
        // Verify through write lock behavior
        writeLock.lock();
        try {
            // No readers should be able to acquire while write lock held
            assertFalse(readLock.tryLock());
        } finally {
            writeLock.unlock();
        }
    }
}