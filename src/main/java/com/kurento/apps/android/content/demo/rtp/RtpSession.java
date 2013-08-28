package com.kurento.apps.android.content.demo.rtp;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sdp.SdpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;

import com.kurento.apps.android.content.demo.rtp.jsonrpc.AsyncJsonRpcClient;
import com.kurento.apps.android.content.demo.rtp.jsonrpc.AsyncJsonRpcClient.JsonRpcRequestHandler;
import com.kurento.commons.media.format.conversor.SdpConversor;
import com.kurento.kmf.content.jsonrpc.Constraints;
import com.kurento.kmf.content.jsonrpc.JsonRpcConstants;
import com.kurento.kmf.content.jsonrpc.JsonRpcRequest;
import com.kurento.kmf.content.jsonrpc.JsonRpcResponse;
import com.kurento.mediaspec.SessionSpec;
import com.kurento.mscontrol.commons.EventType;
import com.kurento.mscontrol.commons.MediaErr;
import com.kurento.mscontrol.commons.MediaEventListener;
import com.kurento.mscontrol.commons.MsControlException;
import com.kurento.mscontrol.commons.networkconnection.NetworkConnection;
import com.kurento.mscontrol.commons.networkconnection.SdpPortManagerEvent;
import com.kurento.mscontrol.kas.MediaSessionAndroid;

public class RtpSession {

	private static final Logger log = LoggerFactory.getLogger(RtpSession.class
			.getSimpleName());

	public interface SessionExceptionHandler {
		public void onSessionException(RtpSession session, Exception e);
	}

	public interface SessionEstablishedHandler {
		public void onEstablishedSession(RtpSession session);
	}

	private final LooperThread looperThread = new LooperThread();
	private final Context context;

	private static final AtomicInteger sequenceNumber = new AtomicInteger(0);

	private String uuid;
	private String sessionId = null;
	private NetworkConnection nc;

	private String serverAddres;
	private int serverPort;
	private String demoUrl;

	private boolean request2Terminate = false;

	private SessionExceptionHandler sessionExceptionHandler;
	private SessionEstablishedHandler sessionEstablishedHandler;

	RtpSession(Context context, MediaSessionAndroid mediaSession)
			throws MsControlException {
		this.context = context;
		this.uuid = UUID.randomUUID().toString();
		this.nc = mediaSession.createNetworkConnection();

		serverAddres = Preferences.getServerAddress(context);
		serverPort = Preferences.getServerPort(context);
		demoUrl = Preferences.getDemoUrl(context);

		createDefaultHandlers();
		looperThread.start();
	}

	public String getUuid() {
		return uuid;
	}

	public synchronized String getSessionId() {
		return sessionId;
	}

	public synchronized void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public NetworkConnection getNetworkConnection() {
		return nc;
	}

	public void setSessionEstablishedHandler(
			SessionEstablishedHandler sessionEstablishedHandler) {
		this.sessionEstablishedHandler = sessionEstablishedHandler;
	}

	public void setSessionExceptionHandler(
			SessionExceptionHandler sessionExceptionHandler) {
		this.sessionExceptionHandler = sessionExceptionHandler;
	}

	private void startSync() throws MsControlException {
		nc.getSdpPortManager().addListener(generateOfferListener);
		nc.getSdpPortManager().generateSdpOffer();
	}

	public void start() {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				try {
					startSync();
				} catch (MsControlException e) {
					log.error("Cannot start", e);
					sessionExceptionHandler.onSessionException(RtpSession.this,
							e);
				}
			}
		});
	}

	private void terminateSync() {
		nc.release();

		synchronized (this) {
			request2Terminate = true;
			if (sessionId == null) {
				log.info("The session with " + uuid + " is not stablished yet");
				return;
			}

			try {
				JsonRpcRequest req = JsonRpcRequest.newRequest(
						JsonRpcConstants.METHOD_TERMINATE, "", sessionId,
						sequenceNumber.getAndIncrement());
				URL url = new URL(
						context.getString(R.string.preference_server_standard_protocol_default),
						serverAddres, serverPort, demoUrl);
				AsyncJsonRpcClient.sendRequest(url, req,
						new JsonRpcRequestHandler() {

							@Override
							public void onSuccess(JsonRpcResponse resp) {
								log.debug("Terminate on success");
							}

							@Override
							public void onError(Exception e) {
								log.error("Exception sending request", e);
							}
						});

			} catch (IOException e) {
				log.error("error: " + e.getMessage(), e);
			}

			looperThread.quit();
		}
	}

	public void terminate() {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				terminateSync();
			}
		});
	}

	private MediaEventListener<SdpPortManagerEvent> generateOfferListener = new MediaEventListener<SdpPortManagerEvent>() {
		@Override
		public void onEvent(SdpPortManagerEvent event) {
			event.getSource().removeListener(this);

			MediaErr error = event.getError();
			if (!MediaErr.NO_ERROR.equals(error)) {
				terminate();
				sessionExceptionHandler.onSessionException(RtpSession.this,
						new MsControlException("Cannot generate offer. "
								+ error));
				return;
			}

			EventType eventType = event.getEventType();
			if (SdpPortManagerEvent.OFFER_GENERATED.equals(eventType)) {
				log.debug("SdpPortManager successfully generated a SDP to be send to remote peer");
				try {
					String sdpOffer = SdpConversor.sessionSpec2Sdp(event
							.getMediaServerSdp());
					log.debug("generated SDP: " + sdpOffer);
					sendRpcStart(sdpOffer);
				} catch (SdpException e) {
					log.error("error: " + e.getMessage(), e);
					terminate();
					sessionExceptionHandler.onSessionException(RtpSession.this,
							e);
				} catch (IOException e) {
					log.error("error: " + e.getMessage(), e);
					terminate();
					sessionExceptionHandler.onSessionException(RtpSession.this,
							e);
				}
			}

		}
	};

	private void sendRpcStart(String sdpOffer) throws IOException {
		// TODO: configure from media session
		JsonRpcRequest req = JsonRpcRequest.newRequest(
				JsonRpcConstants.METHOD_START, sdpOffer, "",
				sequenceNumber.getAndIncrement(), Constraints.INACTIVE,
				Constraints.SENDRECV);
		URL url = new URL(
				context.getString(R.string.preference_server_standard_protocol_default),
				serverAddres, serverPort, demoUrl);
		AsyncJsonRpcClient.sendRequest(url, req, new JsonRpcRequestHandler() {
			@Override
			public void onSuccess(JsonRpcResponse resp) {
				int errorCode = resp.getErrorCode();
				if (JsonRpcConstants.ERROR_NO_ERROR != errorCode) {
					String msg = "Error in JSON-RPC response: "
							+ resp.gerErrorMessage() + "(" + errorCode + ")";
					terminate();
					sessionExceptionHandler.onSessionException(RtpSession.this,
							new MsControlException(msg));
					return;
				}

				String sdpAnswer = resp.getSdp();
				log.debug("SDP answer: " + sdpAnswer);
				setSessionId(resp.getSessionId());
				log.debug("Session ID: " + getSessionId());

				try {
					nc.getSdpPortManager().addListener(processAnswerListener);
					nc.getSdpPortManager().processSdpAnswer(
							SdpConversor.sdp2SessionSpec(sdpAnswer));
				} catch (SdpException e) {
					log.error("error: " + e.getMessage(), e);
					terminate();
					sessionExceptionHandler.onSessionException(RtpSession.this,
							e);
				} catch (MsControlException e) {
					log.error("error: " + e.getMessage(), e);
					terminate();
					sessionExceptionHandler.onSessionException(RtpSession.this,
							e);
				}
			}

			@Override
			public void onError(Exception e) {
				log.error("Exception sending request", e);
				terminate();
				sessionExceptionHandler.onSessionException(RtpSession.this, e);
			}
		});
	}

	private MediaEventListener<SdpPortManagerEvent> processAnswerListener = new MediaEventListener<SdpPortManagerEvent>() {
		@Override
		public void onEvent(SdpPortManagerEvent event) {
			event.getSource().removeListener(this);

			MediaErr error = event.getError();
			if (!MediaErr.NO_ERROR.equals(error)) {
				terminate();
				sessionExceptionHandler.onSessionException(RtpSession.this,
						new MsControlException("Cannot generate offer. "
								+ error));
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
						sessionEstablishedHandler
								.onEstablishedSession(RtpSession.this);
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
					terminate();
					sessionExceptionHandler.onSessionException(RtpSession.this,
							e);
				} catch (MsControlException e) {
					log.error("Error confirming nc");
					terminate();
					sessionExceptionHandler.onSessionException(RtpSession.this,
							e);
				}
			} else {
				log.warn("Event received: " + eventType);
				terminate();
				sessionExceptionHandler.onSessionException(RtpSession.this,
						new MsControlException("Cannot process answer"));
			}
		}
	};

	private void createDefaultHandlers() {
		sessionEstablishedHandler = new SessionEstablishedHandler() {
			@Override
			public void onEstablishedSession(RtpSession session) {
				log.info("Default onEstablishedSession");
			}
		};

		sessionExceptionHandler = new SessionExceptionHandler() {
			@Override
			public void onSessionException(RtpSession session, Exception e) {
				log.info("Default onSessionException");
			}
		};
	}

}
