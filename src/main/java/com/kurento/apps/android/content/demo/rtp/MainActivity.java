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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.kurento.apps.android.content.demo.rtp.RtpSession.SessionEstablishedHandler;
import com.kurento.apps.android.content.demo.rtp.RtpSession.SessionExceptionHandler;
import com.kurento.apps.android.content.demo.rtp.hider.SystemUiHider;
import com.kurento.apps.android.content.demo.rtp.hider.SystemUiHiderBase;
import com.kurento.commons.config.Parameters;
import com.kurento.commons.config.Value;
import com.kurento.kas.media.codecs.AudioCodecType;
import com.kurento.kas.media.codecs.VideoCodecType;
import com.kurento.mediaspec.Direction;
import com.kurento.mediaspec.MediaType;
import com.kurento.mscontrol.commons.MsControlException;
import com.kurento.mscontrol.commons.join.Joinable;
import com.kurento.mscontrol.commons.join.JoinableStream.StreamType;
import com.kurento.mscontrol.commons.networkconnection.NetworkConnection;
import com.kurento.mscontrol.kas.MediaSessionAndroid;
import com.kurento.mscontrol.kas.MsControlFactoryAndroid;
import com.kurento.mscontrol.kas.mediacomponent.MediaComponentAndroid;
import com.kurento.mscontrol.kas.networkconnection.NetIF;

public class MainActivity extends Activity {

	private static final Logger log = LoggerFactory
			.getLogger(MainActivity.class.getSimpleName());

	private static final String STUN_HOST = "";
	private static final int STUN_PORT = 0;
	private static final int MAX_BANDWIDTH = 600000;
	private MediaSessionAndroid mediaSession;

	private static final int SHOW_PREFERENCES = 100;

	private MediaComponentAndroid cameraComponent;
	private MediaComponentAndroid videoViewerComponent;

	private RtpSession session;
	private SessionEstablishedHandler sessionEstablishedHandler = new SessionEstablishedHandlerImpl();
	private SessionExceptionHandler sessionExceptionHandler = new SessionExceptionHandlerImpl();

	private WakeLock mWakeLock = null;

	private static final boolean AUTO_HIDE = true;
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
	private static final boolean TOGGLE_ON_CLICK = true;
	private static final int HIDER_FLAGS = SystemUiHiderBase.FLAG_HIDE_NAVIGATION;
	private SystemUiHiderBase mSystemUiHider;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		setContentView(R.layout.main);

		((ImageView) findViewById(R.id.theater_layout))
				.setBackgroundDrawable(new BitmapDrawable(getResources(),
						BitmapFactory.decodeStream(getResources()
								.openRawResource(R.drawable.bg_screen))));

		((ImageView) findViewById(R.id.campus))
				.setBackgroundDrawable(new BitmapDrawable(getResources(),
						BitmapFactory.decodeStream(getResources()
								.openRawResource(R.drawable.campus))));

		try {
			createMediaSession();
		} catch (MsControlException e) {
			log.error("Cannot initialize service", e);
		}

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "screen");

		createTheMagic();
	}

	@Override
	protected void onResume() {
		mWakeLock.acquire();
		super.onResume();
	}

	@Override
	protected void onPause() {
		mWakeLock.release();
		terminateSession();
		super.onPause();
	}

	public void startSession(View v) {
		try {
			session = new RtpSession(this, mediaSession);
			session.setSessionEstablishedHandler(sessionEstablishedHandler);
			session.setSessionExceptionHandler(sessionExceptionHandler);
			session.start();
			findViewById(R.id.main_button_start_session).setEnabled(false);
			findViewById(R.id.main_button_start_session).setVisibility(
					View.GONE);
			findViewById(R.id.main_button_terminate_session).setEnabled(true);
			findViewById(R.id.main_button_terminate_session).setVisibility(
					View.VISIBLE);
			findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);

			Animation myFadeInAnimation = AnimationUtils.loadAnimation(
					getApplicationContext(), R.anim.make_it_big);
			myFadeInAnimation.setFillAfter(true);
			((FrameLayout) findViewById(R.id.main_background_layout))
					.setBackgroundColor(Color.WHITE);
			((ImageView) findViewById(R.id.theater_layout))
					.startAnimation(myFadeInAnimation);

		} catch (MsControlException e) {
			log.error("Cannot start", e);
		}
	}

	public void terminateSession(View v) {
		terminateSession();
	}

	private void terminateSession() {
		Animation myFadeInAnimation = AnimationUtils.loadAnimation(
				getApplicationContext(), R.anim.make_it_small);
		myFadeInAnimation.setFillAfter(true);
		((FrameLayout) findViewById(R.id.main_background_layout))
				.setBackgroundColor(Color.BLACK);
		((ImageView) findViewById(R.id.theater_layout))
				.startAnimation(myFadeInAnimation);

		if (cameraComponent != null) {
			cameraComponent.stop();
			cameraComponent.release();
			cameraComponent = null;
		}

		if (videoViewerComponent != null) {
			videoViewerComponent.stop();
			videoViewerComponent.release();
			videoViewerComponent = null;
		}

		if (session != null) {
			session.terminate();
		}

		findViewById(R.id.main_button_terminate_session).setEnabled(false);
		findViewById(R.id.main_button_terminate_session).setVisibility(
				View.GONE);

		findViewById(R.id.main_button_start_session).setEnabled(true);
		findViewById(R.id.main_button_start_session)
				.setVisibility(View.VISIBLE);
		findViewById(R.id.progressBar1).setVisibility(View.GONE);
	}

	private void createMediaSession() throws MsControlException {
		Parameters mediaParams = MsControlFactoryAndroid.createParameters();

		InetAddress ip = NetworkIP.getLocalAddress();
		Value<InetAddress> ipValue = new Value<InetAddress>(ip);
		mediaParams.put(MediaSessionAndroid.LOCAL_ADDRESS, ipValue);

		ConnectivityManager connManager = (ConnectivityManager) this
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = connManager.getActiveNetworkInfo();
		NetIF netIF = null;
		if (ni != null) {
			ni = connManager.getActiveNetworkInfo();
			String conType = ni.getTypeName();
			if ("WIFI".equalsIgnoreCase(conType)) {
				netIF = NetIF.WIFI;
			} else if ("MOBILE".equalsIgnoreCase(conType)) {
				netIF = NetIF.MOBILE;
			}
			Value<NetIF> netIFValue = new Value<NetIF>(netIF);
			mediaParams.put(MediaSessionAndroid.NET_IF, netIFValue);
		} else {
			mediaParams.put(MediaSessionAndroid.NET_IF, null);
		}

		Value<String> stunHostValue = new Value<String>(STUN_HOST);
		mediaParams.put(MediaSessionAndroid.STUN_HOST, stunHostValue);

		Value<Integer> stunPortValue = new Value<Integer>(STUN_PORT);
		mediaParams.put(MediaSessionAndroid.STUN_PORT, stunPortValue);

		Value<Integer> maxBandwidthValue = new Value<Integer>(MAX_BANDWIDTH);
		mediaParams.put(MediaSessionAndroid.MAX_BANDWIDTH, maxBandwidthValue);

		ArrayList<AudioCodecType> audioCodecs = new ArrayList<AudioCodecType>();
		Value<List<AudioCodecType>> audioCodecsValue = new Value<List<AudioCodecType>>(
				audioCodecs);
		mediaParams.put(MediaSessionAndroid.AUDIO_CODECS, audioCodecsValue);

		ArrayList<VideoCodecType> videoCodecs = new ArrayList<VideoCodecType>();
		videoCodecs.add(VideoCodecType.MPEG4);
		Value<List<VideoCodecType>> videoCodecsValue = new Value<List<VideoCodecType>>(
				videoCodecs);
		mediaParams.put(MediaSessionAndroid.VIDEO_CODECS, videoCodecsValue);

		Map<MediaType, Direction> mediaDirection = new HashMap<MediaType, Direction>();
		mediaDirection.put(MediaType.AUDIO, Direction.INACTIVE);
		mediaDirection.put(MediaType.VIDEO, Direction.SENDRECV);
		Value<Map<MediaType, Direction>> mediaDirectionValue = new Value<Map<MediaType, Direction>>(
				mediaDirection);
		mediaParams.put(MediaSessionAndroid.STREAMS_MODES, mediaDirectionValue);

		mediaSession = MsControlFactoryAndroid.createMediaSession(mediaParams);
	}

	private void createMediaComponents() {
		if (cameraComponent == null) {
			try {
				Parameters params = new Parameters();
				params.put(
						MediaComponentAndroid.PREVIEW_SURFACE_CONTAINER,
						new Value<ViewGroup>(
								(ViewGroup) findViewById(R.id.video_capture_surface_container)));
				params.put(MediaComponentAndroid.DISPLAY_ORIENTATION,
						new Value<Integer>(0));

				cameraComponent = mediaSession.createMediaComponent(
						MediaComponentAndroid.VIDEO_PLAYER, params);
			} catch (MsControlException e) {
				log.warn("Error creating camera component", e);
			}
		}

		if (videoViewerComponent == null) {
			try {
				Parameters params = new Parameters();
				params.put(
						MediaComponentAndroid.VIEW_SURFACE_CONTAINER,
						new Value<ViewGroup>(
								(ViewGroup) findViewById(R.id.video_receive_surface_container)));
				videoViewerComponent = mediaSession.createMediaComponent(
						MediaComponentAndroid.VIDEO_RECORDER, params);
			} catch (MsControlException e) {
				log.warn("Error creating video viewer component", e);
			}
		}
	}

	private void initMedia(NetworkConnection nc) {
		createMediaComponents();

		try {
			log.debug("Video joinable stream: "
					+ nc.getJoinableStream(StreamType.video));
			log.debug("Audio joinable stream: "
					+ nc.getJoinableStream(StreamType.audio));

			if (cameraComponent != null) {
				cameraComponent.join(Joinable.Direction.SEND,
						nc.getJoinableStream(StreamType.video));
				cameraComponent.start();
				log.debug("videoPlayerComponent STARTED");
			}

			if (videoViewerComponent != null) {
				videoViewerComponent.join(Joinable.Direction.RECV,
						nc.getJoinableStream(StreamType.video));
				videoViewerComponent.start();
			}
		} catch (MsControlException e) {
			log.error("Error initiating media", e);
		}

	}

	private class SessionEstablishedHandlerImpl implements
			SessionEstablishedHandler {
		@Override
		public void onEstablishedSession(final RtpSession session) {
			// FIXME: use intents
			runOnUiThread(new Runnable() {
				public void run() {
					findViewById(R.id.progressBar1).setVisibility(View.GONE);
					((FrameLayout) findViewById(R.id.main_background_layout))
							.setBackgroundColor(Color.BLACK);
					initMedia(session.getNetworkConnection());
				}
			});
		}
	}

	private class SessionExceptionHandlerImpl implements
			SessionExceptionHandler {
		@Override
		public void onSessionException(RtpSession session, final Exception e) {
			log.error("Session exception", e);

			runOnUiThread(new Runnable() {
				public void run() {
					AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
							MainActivity.this);
					alertDialogBuilder.setTitle("Error in RTP Session");
					alertDialogBuilder.setMessage(e.getMessage());
					AlertDialog alertDialog = alertDialogBuilder.create();
					alertDialog.show();

					terminateSession();
				}
			});
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);

		return super.onCreateOptionsMenu(menu);
	}

	public void showMenu(View v) {
		Intent localPreferences = new Intent(this, Preferences.class);
		startActivityForResult(localPreferences, SHOW_PREFERENCES);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case (R.id.menu_preferences):
			Intent localPreferences = new Intent(this, Preferences.class);
			startActivityForResult(localPreferences, SHOW_PREFERENCES);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}

	/* Hider source code needs */
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}

	private final Handler mHideHandler = new Handler();
	private final Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	private void createTheMagic() {
		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.main_activity_layout);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = new SystemUiHider(this, contentView, HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider
				.setOnVisibilityChangeListener(new SystemUiHiderBase.OnVisibilityChangeListener() {
					@Override
					public void onVisibilityChange(boolean visible) {
						if (visible) {
							controlsView.setVisibility(View.VISIBLE);
						} else {
							controlsView.setVisibility(View.INVISIBLE);
						}

						if (visible && AUTO_HIDE) {
							// Schedule a hide().
							delayedHide(AUTO_HIDE_DELAY_MILLIS);
						}
					}
				});

		// Set up the user interaction to manually show or hide the system UI
		contentView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});
	}
}
