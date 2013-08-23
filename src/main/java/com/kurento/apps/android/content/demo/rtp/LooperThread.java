package com.kurento.apps.android.content.demo.rtp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Handler;
import android.os.Looper;

public class LooperThread extends Thread {

	private static final Logger log = LoggerFactory
			.getLogger(LooperThread.class.getSimpleName());

	private Handler mHandler;
	private Object initControl = new Object();
	private boolean initiated = false;
	private boolean quit = false;

	@Override
	public void run() {
		Looper.prepare();
		synchronized (this) {
			mHandler = new Handler();
			if (quit) {
				quit();
			}
		}
		synchronized (initControl) {
			initiated = true;
			initControl.notifyAll();
		}
		Looper.loop();
	}

	public boolean post(Runnable r) {
		try {
			synchronized (initControl) {
				if (!initiated) {
					initControl.wait();
				}
			}
			return mHandler.post(r);
		} catch (InterruptedException e) {
			log.error("Cannot run", e);
			return false;
		}
	}

	public synchronized void quit() {
		quit = true;
		if (mHandler != null) {
			mHandler.getLooper().quit();
		}
	}
}
