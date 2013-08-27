package com.kurento.apps.android.content.demo.rtp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.InputType;

public class Preferences extends PreferenceActivity {

	private static final Logger log = LoggerFactory.getLogger(Preferences.class
			.getSimpleName());

	// Preferences keys
	public static final String SERVER_ADDRESS_KEY = "SERVER_ADDRESS_KEY";
	public static final String SERVER_PORT_KEY = "SERVER_PORT_KEY";
	public static final String DEMOS_LIST_KEY = "DEMOS_LIST_KEY";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setPreferenceScreen(createPreferenceHierarchy());
		this.getPreferenceManager()
				.getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(
						onSharedPreferenceChangeListener);
	}

	@Override
	protected void onDestroy() {
		this.getPreferenceManager()
				.getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(
						onSharedPreferenceChangeListener);

		super.onDestroy();
	}

	private PreferenceScreen createPreferenceHierarchy() {
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(
				this);

		// Server
		EditTextPreference serverAddessText = new EditTextPreference(this);
		serverAddessText.setKey(SERVER_ADDRESS_KEY);
		serverAddessText
				.setTitle(getString(R.string.preference_server_address));
		serverAddessText
				.setDefaultValue(getString(R.string.preference_server_address_default));
		root.addPreference(serverAddessText);

		EditTextPreference serverPortText = new EditTextPreference(this);
		serverPortText.setKey(SERVER_PORT_KEY);
		serverPortText.setTitle(getString(R.string.preference_server_port));
		serverPortText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
		serverPortText
				.setDefaultValue(getString(R.string.preference_server_port_default));
		root.addPreference(serverPortText);

		// Demo selector
		ListPreference demosList = new ListPreference(this);
		demosList.setKey(DEMOS_LIST_KEY);
		demosList.setTitle(getText(R.string.preference_demo_selector));
		demosList
				.setSummary(getText(R.string.preference_demo_selector_summary));
		demosList.setEntries(getResources().getStringArray(
				R.array.preference_demos_list));
		demosList.setEntryValues(getResources().getStringArray(
				R.array.urls_list));
		demosList
				.setDefaultValue(getString(R.string.preference_url_demo_default));
		root.addPreference(demosList);

		return root;
	}

	public static String getServerAddress(Context context) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);

		return pref.getString(SERVER_ADDRESS_KEY,
				context.getString(R.string.preference_server_address_default));
	}

	public static int getServerPort(Context context) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);

		String port = pref.getString(SERVER_PORT_KEY,
				context.getString(R.string.preference_server_port_default));

		if ("".equals(port)) {
			port = context.getString(R.string.preference_server_port_default);
		}

		return Integer.parseInt(port);
	}

	public static String getDemoUrl(Context context) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);

		return pref.getString(DEMOS_LIST_KEY,
				context.getString(R.string.preference_url_demo_default));
	}

	private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			log.info("Preference " + key + " has changed.");
		}
	};
}
