package com.kurento.apps.android.content.demo.rtp;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.kurento.apps.android.content.demo.rtp.RtpSession.SessionEstablishedHandler;
import com.kurento.apps.android.content.demo.rtp.RtpSession.SessionExceptionHandler;
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
	private MediaSessionAndroid mediaSession;

	private MediaComponentAndroid cameraComponent;
	private MediaComponentAndroid videoViewerComponent;

	private RtpSession session;
	private SessionEstablishedHandler sessionEstablishedHandler = new SessionEstablishedHandlerImpl();
	private SessionExceptionHandler sessionExceptionHandler = new SessionExceptionHandlerImpl();

	private WakeLock mWakeLock = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		setContentView(R.layout.main);

		try {
			createMediaSession();
		} catch (MsControlException e) {
			log.error("Cannot initialize service", e);
		}

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "screen");
	}

	@Override
	protected void onResume() {
		mWakeLock.acquire();
		super.onResume();
	}

	@Override
	protected void onPause() {
		mWakeLock.release();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		terminateSession();
		super.onDestroy();
	}

	public void startSession(View v) {
		try {
			session = new RtpSession(this, mediaSession);
			session.setSessionEstablishedHandler(sessionEstablishedHandler);
			session.setSessionExceptionHandler(sessionExceptionHandler);
			session.start();
			findViewById(R.id.main_button_start_session).setEnabled(false);
			findViewById(R.id.main_button_terminate_session).setEnabled(true);
		} catch (MsControlException e) {
			log.error("Cannot start", e);
		}
	}

	public void terminateSession(View v) {
		terminateSession();
	}

	private void terminateSession() {
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
		findViewById(R.id.main_button_start_session).setEnabled(true);
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
		mediaDirection.put(MediaType.VIDEO, Direction.SENDONLY);
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
					initMedia(session.getNetworkConnection());
				}
			});
		}

	}

	private class SessionExceptionHandlerImpl implements
			SessionExceptionHandler {

		@Override
		public void onSessionException(RtpSession session, Exception e) {
			log.error("Session exception", e);
		}

	}

}
