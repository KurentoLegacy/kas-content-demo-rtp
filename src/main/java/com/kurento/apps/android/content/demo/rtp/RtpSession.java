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

import javax.sdp.SdpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;

import com.kurento.commons.media.format.conversor.SdpConversor;
import com.kurento.mediaspec.SessionSpec;
import com.kurento.mscontrol.commons.EventType;
import com.kurento.mscontrol.commons.MediaErr;
import com.kurento.mscontrol.commons.MediaEventListener;
import com.kurento.mscontrol.commons.MsControlException;
import com.kurento.mscontrol.commons.networkconnection.NetworkConnection;
import com.kurento.mscontrol.commons.networkconnection.SdpPortManagerEvent;
import com.kurento.mscontrol.kas.MediaSessionAndroid;

public class RtpSession extends MediaSession {

	private static final Logger log = LoggerFactory.getLogger(RtpSession.class
			.getSimpleName());

	private NetworkConnection nc;

	RtpSession(Context context, MediaSessionAndroid mediaSession)
			throws MsControlException {
		super(context);

		this.nc = mediaSession.createNetworkConnection();
	}

	public NetworkConnection getNetworkConnection() {
		return nc;
	}

	@Override
	protected void generateSdpOffer(Callback<String> callback) {
		try {
			nc.getSdpPortManager().addListener(
					new GenerateOfferListener(callback));
			nc.getSdpPortManager().generateSdpOffer();
		} catch (MsControlException e) {
			log.error("Cannot start", e);
			callback.onError(e);
		}
	}

	@Override
	protected void processSdpAnswer(Callback<Void> callback, String sdpAnswer) {
		try {
			nc.getSdpPortManager().addListener(
					new ProcessAnswerListener(callback));
			nc.getSdpPortManager().processSdpAnswer(
					SdpConversor.sdp2SessionSpec(sdpAnswer));
		} catch (SdpException e) {
			log.error("error: " + e.getMessage(), e);
			callback.onError(e);
		} catch (MsControlException e) {
			log.error("error: " + e.getMessage(), e);
			callback.onError(e);
		}
	}

	@Override
	protected void releaseMedia() {
		nc.release();
	}

	private class GenerateOfferListener implements
			MediaEventListener<SdpPortManagerEvent> {

		private Callback<String> callback;

		GenerateOfferListener(Callback<String> callback) {
			this.callback = callback;
		}

		@Override
		public void onEvent(SdpPortManagerEvent event) {
			event.getSource().removeListener(this);

			MediaErr error = event.getError();
			if (!MediaErr.NO_ERROR.equals(error)) {
				callback.onError(new MsControlException(
						"Cannot generate offer. " + error));
				return;
			}

			EventType eventType = event.getEventType();
			if (SdpPortManagerEvent.OFFER_GENERATED.equals(eventType)) {
				log.debug("SdpPortManager successfully generated a SDP to be send to remote peer");
				try {
					String sdpOffer = SdpConversor.sessionSpec2Sdp(event
							.getMediaServerSdp());
					log.debug("generated SDP: " + sdpOffer);
					callback.onSuccess(sdpOffer);
				} catch (SdpException e) {
					log.error("error: " + e.getMessage(), e);
					callback.onError(e);
				}
			}

		}
	}

	private class ProcessAnswerListener implements
			MediaEventListener<SdpPortManagerEvent> {

		private Callback<Void> callback;

		ProcessAnswerListener(Callback<Void> callback) {
			this.callback = callback;
		}

		@Override
		public void onEvent(SdpPortManagerEvent event) {
			event.getSource().removeListener(this);

			MediaErr error = event.getError();
			if (!MediaErr.NO_ERROR.equals(error)) {
				callback.onError(new MsControlException(
						"Cannot generate offer. " + error));
				return;
			}

			EventType eventType = event.getEventType();
			if (SdpPortManagerEvent.ANSWER_PROCESSED.equals(eventType)) {
				try {
					synchronized (this) {
						if (request2Terminate) {
							terminate();
							return;
						}
						nc.confirm();
						callback.onSuccess(null);
					}

					SessionSpec localSdp = nc.getSdpPortManager()
							.getMediaServerSessionDescription();
					log.debug("local SDP: "
							+ SdpConversor.sessionSpec2Sdp(localSdp));
					SessionSpec remoteSdp = nc.getSdpPortManager()
							.getUserAgentSessionDescription();
					log.debug("remote SDP: "
							+ SdpConversor.sessionSpec2Sdp(remoteSdp));
				} catch (SdpException e) {
					log.error("error: " + e.getMessage(), e);
					callback.onError(e);
				} catch (MsControlException e) {
					log.error("Error confirming nc");
					callback.onError(e);
				}
			} else {
				log.warn("Event received: " + eventType);
				callback.onError(new MsControlException("Cannot process answer"));
			}
		}
	}

}
