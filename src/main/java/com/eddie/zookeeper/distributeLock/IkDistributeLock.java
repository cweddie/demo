package com.eddie.zookeeper.distributeLock;

import lombok.extern.slf4j.Slf4j;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

@Slf4j
public class IkDistributeLock implements Lock {

	private CountDownLatch cdl = new CountDownLatch(1);

	private final static String ZK_IP_PORT = "47.101.154.63:2181";

	private final static String LOCK = "/Lock";

	private String currentPath;

	private String beforePath;

	private ZkClient client = new ZkClient(ZK_IP_PORT, 1000, 1000, new SerializableSerializer());

	public IkDistributeLock() {
		if (!this.client.exists(LOCK))
			this.client.createPersistent(LOCK);
	}

	@Override
	public void lock() {
		if (tryLock()) {
			waitForLock();
			lock();
		} else
			log.info(Thread.currentThread().getName() + "获得锁");

	}

	@Override
	public void lockInterruptibly() throws InterruptedException {

	}

	@Override
	public boolean tryLock() {
		if (StringUtils.isEmpty(currentPath))
			currentPath = this.client.createEphemeralSequential(LOCK + '/', "lock");
		List<String> childrenList = this.client.getChildren(LOCK);
		Collections.sort(childrenList);
		if (currentPath.equals(childrenList.get(0)))
			return true;
		else {
			int index = Collections.binarySearch(childrenList, currentPath.substring(6));
			beforePath = childrenList.get(index - 1);
		}
		return false;
	}

	@Override
	public boolean tryLock(long l, TimeUnit timeUnit) throws InterruptedException {
		return false;
	}

	@Override
	public void unlock() {
		client.delete(currentPath);
	}

	@Override
	public Condition newCondition() {
		return null;
	}

	private void waitForLock() {
		IZkDataListener listener = new IZkDataListener() {
			@Override
			public void handleDataChange(String s, Object o) throws Exception {
				if (cdl == null) {
					cdl.countDown();
				}
			}

			@Override
			public void handleDataDeleted(String s) throws Exception {
			}
		};

		this.client.subscribeDataChanges(beforePath, listener);
		if (this.client.exists(beforePath)) {
			cdl = new CountDownLatch(1);
			try {
				cdl.await();
			} catch (Exception e) {
				e.fillInStackTrace();
			}
		}
		this.client.unsubscribeDataChanges(beforePath, listener);
	}
}
