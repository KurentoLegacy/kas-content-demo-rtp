package com.kurento.apps.android.content.demo.rtp.jsonrpc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import com.kurento.apps.android.content.demo.rtp.Preferences;
import com.kurento.apps.android.content.demo.rtp.R;
import com.kurento.kmf.content.jsonrpc.GsonUtils;
import com.kurento.kmf.content.jsonrpc.JsonRpcRequest;
import com.kurento.kmf.content.jsonrpc.JsonRpcResponse;

public class AsyncJsonRpcClient {

	private static final Logger log = LoggerFactory
			.getLogger(AsyncJsonRpcClient.class.getSimpleName());

	public interface JsonRpcRequestHandler {
		void onSuccess(JsonRpcResponse resp);

		void onError(Exception e);
	}

	public static void sendRequest(final Context context,
			final JsonRpcRequest req, final JsonRpcRequestHandler handler)
			throws IOException {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				String reqJson = GsonUtils.toString(req);

				try {
					URL urlObj = new URL(
							context.getString(R.string.preference_server_standard_protocol_default),
							Preferences.getServerAddress(context), Preferences
									.getServerPort(context), Preferences
									.getDemoUrl(context));
					String url = Uri.parse(urlObj.toString()).buildUpon()
							.build().toString();
					HttpHost host = new HttpHost(urlObj.getHost(),
							urlObj.getPort(), urlObj.getProtocol());

					HttpClient httpClient = new DefaultHttpClient();
					HttpPost post = new HttpPost(url);

					log.debug("url: " + url);
					log.debug("JSON to send: " + reqJson);

					post.setEntity(new StringEntity(reqJson, "UTF8"));
					post.setHeader("Content-type", "application/json");

					HttpResponse response = httpClient.execute(host, post);
					StatusLine status = response.getStatusLine();
					int code = status.getStatusCode();

					log.debug("status code: " + code + " message: "
							+ status.getReasonPhrase());

					if (code != HttpStatus.SC_OK) {
						handler.onError(new HttpException(
								"Error in HTTP request: " + code));
						return null;
					}

					String respJson = entityToString(response.getEntity());
					log.debug("respJson: " + respJson);
					JsonRpcResponse resp = GsonUtils.getGson().fromJson(
							respJson, JsonRpcResponse.class);
					if (resp == null) {
						handler.onError(new ParseException(
								"No valid JSON-RPC response"));
					} else {
						handler.onSuccess(resp);
					}

				} catch (IOException e) {
					handler.onError(e);
				}

				return null;
			}
		}.execute();
	}

	private static String entityToString(HttpEntity entity) throws IOException {
		InputStream is = entity.getContent();
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(is));
		StringBuilder str = new StringBuilder();

		String line = null;
		try {
			while ((line = bufferedReader.readLine()) != null) {
				str.append(line + "\n");
			}
		} finally {
			try {
				is.close();
			} catch (IOException ignore) {
			}
		}
		return str.toString();
	}

}
