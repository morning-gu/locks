# locks
提供一些JDK没有的锁实现

## NonReentrantReadWriteLock
参考JDK的`ReentrantReadWriteLock`，基于`ReadWriteLock`接口实现一个不可重入的读写锁。