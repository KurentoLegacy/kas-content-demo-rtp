package com.kurento.apps.android.content.demo.rtp;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaConstraints.KeyValuePair;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnection.SignalingState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRenderer.I420Frame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.kurento.apps.android.media.VideoStreamView;

public class WebRtcSession extends MediaSession {

	private static final Logger log = LoggerFactory
			.getLogger(WebRtcSession.class.getSimpleName());

	private static final String DEFAULT_STUN_ADDRESS = "stun.l.google.com";
	private static final int DEFAULT_STUN_PORT = 19302;
	private static final String DEFAULT_STUN_PASSWORD = "";

	/*
	 * This looper is used to manage the peerconnection always in the same
	 * thread to avoid internal problems.
	 */
	private final LooperThread pcLooperThread = new LooperThread();

	private PeerConnectionFactory peerConnectionFactory;
	private PeerConnection peerConnection;
	private VideoCapturer capturer;
	private VideoSource videoSource;
	private MediaStream localStream;
	private MediaStream remoteStream;

	WebRtcSession(Context context) {
		super(context);
	}

	private synchronized MediaStream getLocalStream() {
		return localStream;
	}

	private synchronized MediaStream getRemoteStream() {
		return remoteStream;
	}

	public synchronized void setRemoteStream(MediaStream remoteStream) {
		if (peerConnection != null) {
			this.remoteStream = remoteStream;
		}
	}

	private synchronized String getLocalDescriptionPCSync() {
		if (peerConnection == null) {
			return null;
		}

		return peerConnection.getLocalDescription().description;
	}

	public void setLocalDisplay(ViewGroup viewGroup) {
		setDisplay(viewGroup, getLocalStream());
	}

	public void setRemoteDisplay(ViewGroup viewGroup) {
		setDisplay(viewGroup, getRemoteStream());
	}

	private synchronized void generateSdpOfferPCSync(
			final Callback<String> callback) {
		MediaConstraints constraints = new MediaConstraints();

		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveAudio", "true"));
		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveVideo", "true"));

		peerConnection.createOffer(new SdpObserver() {

			@Override
			public void onCreateFailure(String error) {
				log.error("createOffer onCreateFailure: " + error);
				callback.onError(new Exception(error));
			}

			@Override
			public void onCreateSuccess(SessionDescription sdp) {
				log.debug("createOffer onSuccess");
				peerConnection.setLocalDescription(new SdpObserver() {
					@Override
					public void onCreateFailure(String error) {
						// Nothing to do
					}

					@Override
					public void onCreateSuccess(SessionDescription sdp) {
						// Nothing to do
					}

					@Override
					public void onSetFailure(String error) {
						log.error("setLocalDescription onFailure: " + error);
						callback.onError(new Exception(error));
					}

					@Override
					public void onSetSuccess() {
						log.debug("setLocalDescription onSuccess");
					}
				}, sdp);
			}

			@Override
			public void onSetFailure(String error) {
				// Nothing to do
			}

			@Override
			public void onSetSuccess() {
				// Nothing to do
			}
		}, constraints);
	}

	@Override
	protected void generateSdpOffer(final Callback<String> callback) {
		pcLooperThread.start();
		pcLooperThread.post(new Runnable() {
			@Override
			public void run() {
				createPeerConnectionPCSync(callback);
				generateSdpOfferPCSync(callback);
			}
		});
	}

	@Override
	protected void processSdpAnswer(final Callback<Void> callback,
			String sdpAnswer) {
		final SessionDescription sdp = new SessionDescription(
				SessionDescription.Type.ANSWER, sdpAnswer);

		pcLooperThread.post(new Runnable() {
			@Override
			public void run() {
				peerConnection.setRemoteDescription(new SdpObserver() {
					@Override
					public void onCreateFailure(String error) {
						// Nothing to do
					}

					@Override
					public void onCreateSuccess(SessionDescription sdp) {
						// Nothing to do
					}

					@Override
					public void onSetFailure(String error) {
						log.error("setRemoteDescription onFailure: " + error);
						callback.onError(new Exception(error));
					}

					@Override
					public void onSetSuccess() {
						log.debug("setRemoteDescription onSuccess");
						callback.onSuccess(null);
					}
				}, sdp);
			}
		});
	}

	@Override
	protected void releaseMedia() {
		pcLooperThread.post(new Runnable() {
			@Override
			public void run() {
				if (peerConnection != null) {
					peerConnection.close();
					peerConnection.dispose();
					peerConnection = null;
					localStream = null;
					remoteStream = null;
				}

				if (capturer != null) {
					capturer.dispose();
					capturer = null;
				}

				if (videoSource != null) {
					videoSource.stop();
					videoSource.dispose();
					videoSource = null;
				}

				if (peerConnectionFactory != null) {
					peerConnectionFactory.dispose();
					peerConnectionFactory = null;
				}
				// pcLooperThread.quit(); // FIXME: fails when detach thread
			}
		});
	}

	private synchronized void createPeerConnectionPCSync(
			Callback<String> callback) {
		peerConnectionFactory = new PeerConnectionFactory();

		StringBuilder stunAddress = new StringBuilder();
		stunAddress.append("stun:").append(DEFAULT_STUN_ADDRESS).append(":")
				.append(DEFAULT_STUN_PORT);

		log.debug("stun server: " + stunAddress.toString());
		PeerConnection.IceServer stunServer = new PeerConnection.IceServer(
				stunAddress.toString(), "", DEFAULT_STUN_PASSWORD);

		List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();
		iceServers.add(stunServer);

		MediaConstraints constraints = new MediaConstraints();
		constraints.optional.add(new KeyValuePair("DtlsSrtpKeyAgreement",
				"true"));

		peerConnection = peerConnectionFactory.createPeerConnection(iceServers,
				constraints, new PeerConnectionObserver(callback));

		localStream = peerConnectionFactory
				.createLocalMediaStream("MediaStream0");

		AudioSource audioSource = peerConnectionFactory
				.createAudioSource(new MediaConstraints());
		AudioTrack audioTrack = peerConnectionFactory.createAudioTrack(
				"AudioTrack0", audioSource);
		localStream.addTrack(audioTrack);

		log.debug("Create back camera");
		capturer = VideoCapturer
				.create("Camera 0, Facing back, Orientation 90");

		if (capturer != null) {
			videoSource = peerConnectionFactory.createVideoSource(capturer,
					new MediaConstraints());
			VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(
					"VideoTrack0", videoSource);
			localStream.addTrack(videoTrack);
		}
		peerConnection.addStream(localStream, new MediaConstraints());
	}

	private class PeerConnectionObserver implements PeerConnection.Observer {

		private Callback<String> callback;

		PeerConnectionObserver(Callback<String> callback) {
			this.callback = callback;
		}

		@Override
		public void onSignalingChange(SignalingState newState) {
			log.debug("peerConnection onSignalingChange: " + newState);
		}

		@Override
		public void onRenegotiationNeeded() {
			log.debug("peerConnection onRenegotiationNeeded");
		}

		@Override
		public void onRemoveStream(MediaStream arg0) {
			log.debug("peerConnection onRemoveStream");
		}

		@Override
		public void onIceGatheringChange(IceGatheringState newState) {
			log.debug("peerConnection onIceGatheringChange: " + newState);
			if (IceGatheringState.COMPLETE.equals(newState)) {
				String localDescription = getLocalDescriptionPCSync();
				if (localDescription != null) {
					callback.onSuccess(localDescription);
				} else {
					String error = "Local SDP is null";
					log.error(error);
					callback.onError(new Exception(error));
				}
			}
		}

		@Override
		public void onIceConnectionChange(IceConnectionState newState) {
			log.debug("peerConnection onIceConnectionChange: " + newState);
		}

		@Override
		public void onIceCandidate(IceCandidate candidate) {
			log.debug("peerConnection onIceCandidate: " + candidate.sdp);
		}

		@Override
		public void onError() {
			log.debug("peerConnection onError");
		}

		@Override
		public void onDataChannel(DataChannel arg0) {
			log.debug("peerConnection onDataChannel");
		}

		@Override
		public void onAddStream(MediaStream stream) {
			log.debug("peerConnection onAddStream");
			setRemoteStream(stream);
		}
	}

	/* Video stream management */
	// TODO: improve names and create an external class to export these
	// utilities

	private static void setDisplay(ViewGroup viewGroup, MediaStream stream) {
		if (stream == null || !(viewGroup.getContext() instanceof Activity))
			return;
		Activity activity = (Activity) viewGroup.getContext();
		VideoStreamView sv = getVideoStreamViewFromActivity(activity);

		Preview preview = new Preview(viewGroup.getContext(), sv);

		if (stream != null && stream.videoTracks.size() > 0) {
			stream.videoTracks.get(0).addRenderer(new VideoRenderer(preview));
		}

		viewGroup.addView(preview, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private static final int STREAM_ID = 10000;

	private static synchronized VideoStreamView getVideoStreamViewFromActivity(
			Activity activity) {
		VideoStreamView sv = null;
		try {
			sv = (VideoStreamView) activity.findViewById(STREAM_ID);
		} catch (ClassCastException e) {
			// Ignore
		}

		if (sv == null) {
			log.info("Creating videostream view");
			sv = new VideoStreamView(activity);
			sv.setId(STREAM_ID);

			FrameLayout content = (FrameLayout) activity
					.findViewById(android.R.id.content);

			content.addView(sv, 0, new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			log.info("Videostream view is already created");
		}

		return sv;
	}

	private static class Preview extends ViewGroup implements
			VideoRenderer.Callbacks {

		private final int streamId;
		private final VideoStreamView sv;

		Preview(Context c, VideoStreamView sv) {
			super(c);

			this.sv = sv;
			streamId = sv.registerStream();
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			if (!changed)
				return;

			int position[] = new int[2];
			getLocationInWindow(position);

			sv.setStreamDimensions(streamId, getWidth(), getHeight(),
					position[0], position[1]);
		}

		@Override
		public void renderFrame(I420Frame frame) {
			sv.queueFrame(streamId, frame);
		}

		@Override
		public void setSize(final int width, final int height) {
			sv.queueEvent(new Runnable() {
				@Override
				public void run() {
					sv.setSize(streamId, width, height);
				}
			});
		}
	}

}
