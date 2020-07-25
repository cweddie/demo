package com.eddie.zookeeper.service;

import com.eddie.zookeeper.distributeLock.DistributeLock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;

public class OrderCode implements Runnable {

	private static CountDownLatch cdl = new CountDownLatch(1000);

	private Lock lock = new DistributeLock();

	private static OrderCodeGenerate orderCodeGenerate=new OrderCodeGenerate();

	public void createOrderCode() {
		try {
			lock.lock();
			System.out.println(orderCodeGenerate.generate());
		} catch (Exception e) {
			e.fillInStackTrace();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void run() {
		createOrderCode();
	}

	public static void main(String[] args) {
		for (int i = 0; i < 1000; i++) {
			new Thread(new OrderCode()).start();
		}
	}
}
