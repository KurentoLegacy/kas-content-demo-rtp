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
package com.kurento.apps.android.content.demo.rtp.hider;

import android.app.Activity;
import android.view.View;

public abstract class SystemUiHiderBase {

	public static final int FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES = 0x1;
	public static final int FLAG_FULLSCREEN = 0x2;
	public static final int FLAG_HIDE_NAVIGATION = FLAG_FULLSCREEN | 0x4;
	protected Activity mActivity;
	protected View mAnchorView;
	protected int mFlags;
	protected OnVisibilityChangeListener mOnVisibilityChangeListener = sDummyListener;

	protected SystemUiHiderBase(Activity activity, View anchorView, int flags) {
		mActivity = activity;
		mAnchorView = anchorView;
		mFlags = flags;
	}

	public abstract void setup();

	public abstract boolean isVisible();

	public abstract void hide();

	public abstract void show();

	public void toggle() {
		if (isVisible()) {
			hide();
		} else {
			show();
		}
	}

	public void setOnVisibilityChangeListener(
			OnVisibilityChangeListener listener) {
		if (listener == null) {
			listener = sDummyListener;
		}

		mOnVisibilityChangeListener = listener;
	}

	private static OnVisibilityChangeListener sDummyListener = new OnVisibilityChangeListener() {
		@Override
		public void onVisibilityChange(boolean visible) {
		}
	};

	public interface OnVisibilityChangeListener {
		public void onVisibilityChange(boolean visible);
	}

}
