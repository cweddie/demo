package com.eddie.zookeeper.distributeLock;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.naming.factory.BeanFactory;
import org.apache.zookeeper.CreateMode;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;


@Slf4j
public class DistributeLock implements Lock {

	//	@Value("zookeeper.ipAddrPort")
	private String ipAddrPort = "47.101.154.63:2181";

	//	@Value("zookeeper.lockNode")
	private String lockNode = "/Lock";

	private final int timeout = 3000;

	private static CuratorFramework client;

	private String currentPath;

	private String beforePath;

	private CountDownLatch cdl;

	public DistributeLock() {
		client = CuratorFrameworkFactory.builder().connectString(ipAddrPort)
				         .connectionTimeoutMs(timeout).retryPolicy(new ExponentialBackoffRetry(1000, 3)).build();
		client.start();

		try {
			if (client.checkExists().forPath(lockNode) == null)
				client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(lockNode, "test".getBytes());
		} catch (Exception e) {
			log.error("创建lock目录失败");
			e.fillInStackTrace();
		}
	}


	@Override
	public void lock() {
		if (!tryLock()) {
			waitForLock();
			lock();
		} else
			log.info(Thread.currentThread().getName() + "获得分布式锁！");
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {

	}

	@Override
	public boolean tryLock() {
		try {
			ThreadLocal threadLocal=new ThreadLocal();
			if  (StringUtils.isEmpty(currentPath))
				currentPath = client.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(getPath(lockNode, "lock"));
			List<String> childrenList = client.getChildren().forPath(lockNode);
			Collections.sort(childrenList);
			if (currentPath.equals(getPath(lockNode, childrenList.get(0))))
				return true;

			else {
				int index = Collections.binarySearch(childrenList, currentPath.substring(6));
				beforePath = getPath(lockNode, childrenList.get(index - 1));
			}
		} catch (Exception e) {
			e.fillInStackTrace();
		}
		return false;
	}

	@Override
	public boolean tryLock(long l, TimeUnit timeUnit) throws InterruptedException {
		return false;
	}

	@Override
	public void unlock() {
		try {
			client.delete().forPath(currentPath);
		} catch (Exception e) {
			e.fillInStackTrace();
		}
	}


	public void waitForLock() {
		TreeCache treeNode = new TreeCache(client, beforePath);
		try {
			treeNode.start();
		} catch (Exception e) {
			e.fillInStackTrace();
		}
		treeNode.getListenable().addListener(new TreeCacheListener() {
			@Override
			public void childEvent(CuratorFramework curatorFramework, TreeCacheEvent treeCacheEvent) throws Exception {
				if (treeCacheEvent.getType().equals(TreeCacheEvent.Type.NODE_REMOVED)) {
					log.info(Thread.currentThread().getName() + ": 捕获到前一节点被删除");
					if (cdl != null) {
						cdl.countDown();
					}
				}
			}
		});
		try {
			if (client.checkExists().forPath(beforePath) != null) {
				cdl = new CountDownLatch(1);
				cdl.await();
			}
		} catch (Exception e) {
			e.fillInStackTrace();
		}

	}

	@Override
	public Condition newCondition() {
		return null;
	}

	private String getPath(String lockNode, String childPath) {
		return lockNode + '/' + childPath;
	}
}
