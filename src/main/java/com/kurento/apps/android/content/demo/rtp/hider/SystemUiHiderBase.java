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
