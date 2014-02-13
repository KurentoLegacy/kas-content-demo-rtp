package com.kurento.apps.android.content.demo.rtp;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;

import com.kurento.apps.android.content.demo.rtp.jsonrpc.AsyncJsonRpcClient;
import com.kurento.apps.android.content.demo.rtp.jsonrpc.AsyncJsonRpcClient.JsonRpcRequestHandler;
import com.kurento.kmf.content.jsonrpc.Constraints;
import com.kurento.kmf.content.jsonrpc.JsonRpcRequest;
import com.kurento.kmf.content.jsonrpc.JsonRpcResponse;
import com.kurento.kmf.content.jsonrpc.JsonRpcResponseError;
import com.kurento.kmf.content.jsonrpc.param.JsonRpcConstraints;
import com.kurento.kmf.content.jsonrpc.result.JsonRpcResponseResult;
import com.kurento.mscontrol.commons.MsControlException;

public abstract class MediaSession {

	private static final Logger log = LoggerFactory
			.getLogger(MediaSession.class.getSimpleName());

	public interface SessionExceptionHandler {
		public void onSessionException(MediaSession session, Exception e);
	}

	public interface SessionEstablishedHandler {
		public void onEstablishedSession(MediaSession session);
	}

	protected interface Callback<T> {
		void onSuccess(T result);

		void onError(Exception e);
	}

	private final Context context;

	private String uuid;
	private String sessionId = null;
	protected boolean request2Terminate = false; // TODO: this must be private

	private String serverAddres;
	private int serverPort;
	private String demoUrl;

	private SessionExceptionHandler sessionExceptionHandler;
	private SessionEstablishedHandler sessionEstablishedHandler;

	private final LooperThread looperThread = new LooperThread();
	private static final AtomicInteger sequenceNumber = new AtomicInteger(0);

	MediaSession(Context context) {
		this.context = context;

		serverAddres = Preferences.getServerAddress(context);
		serverPort = Preferences.getServerPort(context);
		demoUrl = Preferences.getDemoUrl(context);
		uuid = UUID.randomUUID().toString();

		createDefaultHandlers();
	}

	public void setSessionEstablishedHandler(
			SessionEstablishedHandler sessionEstablishedHandler) {
		this.sessionEstablishedHandler = sessionEstablishedHandler;
	}

	public void setSessionExceptionHandler(
			SessionExceptionHandler sessionExceptionHandler) {
		this.sessionExceptionHandler = sessionExceptionHandler;
	}

	private synchronized String getSessionId() {
		return sessionId;
	}

	private synchronized void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	protected abstract void generateSdpOffer(Callback<String> callback);

	public void start() {
		looperThread.start();
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				generateSdpOffer(new Callback<String>() {
					@Override
					public void onSuccess(String offer) {
						try {
							sendRpcStart(offer);
						} catch (IOException e) {
							log.error("error: " + e.getMessage(), e);
							terminate();
							sessionExceptionHandler.onSessionException(
									MediaSession.this, e);
						}
					}

					@Override
					public void onError(Exception e) {
						log.error("error: " + e.getMessage(), e);
						terminate();
						sessionExceptionHandler.onSessionException(
								MediaSession.this, e);
					}
				});
			}
		});
	}

	protected abstract void releaseMedia();

	private synchronized void terminateSync() {
		request2Terminate = true;
		if (sessionId == null) {
			log.info("The session with " + uuid + " is not stablished yet");
			return;
		}

		JsonRpcRequest req = JsonRpcRequest.newTerminateRequest(0,
				"Terminate RTP session", sessionId,
				sequenceNumber.getAndIncrement());

		try {
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
	}

	public void terminate() {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				releaseMedia();
				terminateSync();
				looperThread.quit();
			}
		});
	}

	protected abstract void processSdpAnswer(Callback<Void> callback,
			String sdpAnswer);

	private void sendRpcStart(String sdpOffer) throws IOException {
		JsonRpcRequest req = JsonRpcRequest.newStartRequest(sdpOffer,
				new JsonRpcConstraints(Constraints.SENDRECV.toString(),
						Constraints.INACTIVE.toString()), sequenceNumber
						.getAndIncrement());
		URL url = new URL(
				context.getString(R.string.preference_server_standard_protocol_default),
				serverAddres, serverPort, demoUrl);

		AsyncJsonRpcClient.sendRequest(url, req, new JsonRpcRequestHandler() {
			@Override
			public void onSuccess(JsonRpcResponse resp) {
				if (resp.isError()) {
					JsonRpcResponseError error = resp.getResponseError();
					String msg = "Error in JSON-RPC response: "
							+ error.getMessage() + "(" + error.getCode() + ")";
					terminate();
					sessionExceptionHandler.onSessionException(
							MediaSession.this, new MsControlException(msg));
					return;
				}

				JsonRpcResponseResult result = resp.getResponseResult();
				setSessionId(result.getSessionId());
				log.debug("Session ID: " + getSessionId());

				String sdpAnswer = result.getSdp();
				log.debug("SDP answer: " + sdpAnswer);
				processSdpAnswer(new Callback<Void>() {
					@Override
					public void onSuccess(Void result) {
						sessionEstablishedHandler
								.onEstablishedSession(MediaSession.this);
					}

					@Override
					public void onError(Exception e) {
						log.error("error: " + e.getMessage(), e);
						terminate();
						sessionExceptionHandler.onSessionException(
								MediaSession.this, e);
					}
				}, sdpAnswer);
			}

			@Override
			public void onError(Exception e) {
				log.error("Exception sending request", e);
				terminate();
				sessionExceptionHandler
						.onSessionException(MediaSession.this, e);
			}
		});
	}

	private void createDefaultHandlers() {
		sessionEstablishedHandler = new SessionEstablishedHandler() {
			@Override
			public void onEstablishedSession(MediaSession session) {
				log.info("Default onEstablishedSession");
			}
		};

		sessionExceptionHandler = new SessionExceptionHandler() {
			@Override
			public void onSessionException(MediaSession session, Exception e) {
				log.info("Default onSessionException");
			}
		};
	}
}
