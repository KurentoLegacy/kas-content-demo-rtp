/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
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

	static final LooperThread webRtcLT = new LooperThread();

	static {
		webRtcLT.start();
	}

	private static boolean initiated = false;

	public static synchronized void initWebRtc(final Context context) {
		if (initiated) {
			return;
		}

		webRtcLT.post(new Runnable() {
			@Override
			public void run() {
				PeerConnectionFactory.initializeAndroidGlobals(context
						.getApplicationContext());
			}
		});

		initiated = true;
	}

	private static final String DEFAULT_STUN_ADDRESS = "stun.l.google.com";
	private static final int DEFAULT_STUN_PORT = 19302;
	private static final String DEFAULT_STUN_PASSWORD = "";

	private PeerConnection peerConnection;
	private MediaStream localStream;
	private MediaStream remoteStream;

	private PeerConnectionObserver peerConnectionObserver = new PeerConnectionObserver();

	public WebRtcSession(Context ctx) {
		super(ctx);

		webRtcLT.post(new Runnable() {
			@Override
			public void run() {
				startSync();
			}
		});
	}

	public void setLocalDisplay(ViewGroup viewGroup) {
		setDisplay(viewGroup, localStream);
	}

	public void setRemoteDisplay(ViewGroup viewGroup) {
		setDisplay(viewGroup, remoteStream);
	}

	private void startSync() {
		PeerConnectionFactory pcf = PeerConnectionFactorySingleton
				.getInstance();

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

		peerConnection = pcf.createPeerConnection(iceServers, constraints,
				peerConnectionObserver);

		localStream = pcf.createLocalMediaStream("MediaStream0");

		AudioSource as = PeerConnectionFactorySingleton.createAudioSource();
		AudioTrack audioTrack = pcf.createAudioTrack("AudioTrack0", as);
		localStream.addTrack(audioTrack);

		VideoSource vs = PeerConnectionFactorySingleton.getVideoSource();
		VideoTrack videoTrack = pcf.createVideoTrack("VideoTrack0", vs);
		localStream.addTrack(videoTrack);

		peerConnection.addStream(localStream, new MediaConstraints());
	}

	private void releaseMediaSync() {
		if (peerConnection != null) {
			peerConnection.close();
			peerConnection.dispose();
			peerConnection = null;
			localStream = null;
		}

		PeerConnectionFactorySingleton.disposeVideoSource();
	}

	@Override
	public void releaseMedia() {
		webRtcLT.post(new Runnable() {
			@Override
			public void run() {
				releaseMediaSync();
			}
		});
	}

	private void pcSetLocalDescriptionSync(SessionDescription sdp,
			final Callback<String> callback) {
		if (peerConnection == null) {
			log.error("PeerConnection is null. Cannot set local description");
			return;
		}

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
				log.debug("setLocalDescription onFailure: " + error);
				callback.onError(new Exception(error));
			}

			@Override
			public void onSetSuccess() {
				log.debug("setLocalDescription onSuccess");
			}
		}, sdp);
	}

	private void createSdpOfferSync(final Callback<String> callback) {
		if (peerConnection == null) {
			log.error("PeerConnection is null. Cannot create offer");
			return;
		}

		MediaConstraints constraints = new MediaConstraints();

		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveAudio", "true"));
		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveVideo", "true"));

		peerConnectionObserver.setSdpCallback(callback);
		peerConnection.createOffer(new SdpObserver() {
			@Override
			public void onCreateFailure(String error) {
				log.error("createOffer onCreateFailure: " + error);
				callback.onError(new Exception(error));
			}

			@Override
			public void onCreateSuccess(final SessionDescription sdp) {
				log.debug("createOffer onSuccess");
				webRtcLT.post(new Runnable() {
					@Override
					public void run() {
						pcSetLocalDescriptionSync(sdp, callback);
					}
				});
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
	public void generateSdpOffer(final Callback<String> callback) {
		webRtcLT.post(new Runnable() {
			@Override
			public void run() {
				createSdpOfferSync(callback);
			}
		});
	}

	private void pcCreateAnswerSync(final Callback<String> callback) {
		if (peerConnection == null) {
			log.error("PeerConnection is null. Cannot create answer");
			return;
		}

		MediaConstraints constraints = new MediaConstraints();
		peerConnection.createAnswer(new SdpObserver() {
			@Override
			public void onCreateFailure(String error) {
				log.debug("createAnswer onFailure: " + error);
				callback.onError(new Exception(error));
			}

			@Override
			public void onCreateSuccess(final SessionDescription sdp) {
				log.debug("createAnswer onSuccess");
				webRtcLT.post(new Runnable() {
					@Override
					public void run() {
						pcSetLocalDescriptionSync(sdp, callback);
					}
				});
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

	private void createSdpAnswerSync(String sdpOffer,
			final Callback<String> callback) {
		if (peerConnection == null) {
			log.error("PeerConnection is null. Cannot create answer");
			return;
		}

		final SessionDescription sdp = new SessionDescription(
				SessionDescription.Type.OFFER, sdpOffer);

		// FIXME
		peerConnectionObserver.setSdpCallback(callback);
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
				webRtcLT.post(new Runnable() {
					@Override
					public void run() {
						pcCreateAnswerSync(callback);
					}
				});
			}
		}, sdp);
	}

	public void createSdpAnswer(final String sdpOffer,
			final Callback<String> callback) {
		webRtcLT.post(new Runnable() {
			@Override
			public void run() {
				createSdpAnswerSync(sdpOffer, callback);
			}
		});
	}

	private void processSdpAnswerSync(String sdpAnswer,
			final Callback<Void> callback) {
		if (peerConnection == null) {
			log.error("PeerConnection is null. Cannot process answer");
			return;
		}

		final SessionDescription sdp = new SessionDescription(
				SessionDescription.Type.ANSWER, sdpAnswer);

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

	@Override
	public void processSdpAnswer(final Callback<Void> callback,
			final String sdpAnswer) {
		webRtcLT.post(new Runnable() {
			@Override
			public void run() {
				processSdpAnswerSync(sdpAnswer, callback);
			}
		});
	}

	private class PeerConnectionObserver implements PeerConnection.Observer {

		private Callback<String> sdpCalback;

		public synchronized Callback<String> getSdpCallback() {
			return sdpCalback;
		}

		public synchronized void setSdpCallback(Callback<String> sdpCalback) {
			this.sdpCalback = sdpCalback;
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
				webRtcLT.post(new Runnable() {
					@Override
					public void run() {
						Callback<String> c = getSdpCallback();
						if (c == null) {
							log.error("There is not callback");
							return;
						}

						if (peerConnection == null) {
							String error = "PeerConnection is dispossed";
							log.error(error);
							c.onError(new Exception(error));
							return;
						}

						String localDescription = peerConnection
								.getLocalDescription().description;
						if (localDescription != null) {
							c.onSuccess(localDescription);
						} else {
							String error = "Local SDP is null";
							log.error(error);
							c.onError(new Exception(error));
						}
					}
				});
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
			remoteStream = stream;
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

	private static VideoStreamView getVideoStreamViewFromActivity(
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
