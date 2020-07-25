package com.eddie.zookeeper.service;

import java.text.SimpleDateFormat;
import java.util.Date;

public class OrderCodeGenerate {


	private int i = 0;

	public String generate() {
		Date date=new Date();
		SimpleDateFormat simpleDateFormat=new SimpleDateFormat("MMddHHmmss");
		return simpleDateFormat.format(date) + (++i);
	}
}
