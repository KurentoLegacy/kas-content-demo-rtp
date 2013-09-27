/*
 * (C) Copyright 2013 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
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
