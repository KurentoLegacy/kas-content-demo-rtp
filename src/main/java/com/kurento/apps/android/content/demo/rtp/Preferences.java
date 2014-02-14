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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.widget.EditText;

public class Preferences extends PreferenceActivity {

	private static final Logger log = LoggerFactory.getLogger(Preferences.class
			.getSimpleName());

	public enum RtcType {
		RTP, WEBRTC;
	}

	// Preferences keys
	public static final String SERVER_ADDRESS_KEY = "SERVER_ADDRESS_KEY";
	public static final String SERVER_PORT_KEY = "SERVER_PORT_KEY";
	public static final String RTC_TYPE_KEY = "RTC_TYPE_KEY";
	public static final String DEMOS_LIST_KEY = "DEMOS_LIST_KEY";
	public static final String CUSTOM_DEMOS_SET_KEY = "CUSTOM_DEMOS_SET_KEY";

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

		CharSequence[] fixedMediaTypes = getResources().getStringArray(
				R.array.preference_rtc_type_list);
		ListPreference rtcTypeList = new ListPreference(this);
		rtcTypeList.setKey(RTC_TYPE_KEY);
		rtcTypeList.setTitle(getString(R.string.preference_rtc_type));
		rtcTypeList.setEntries(fixedMediaTypes);
		rtcTypeList.setEntryValues(fixedMediaTypes);
		rtcTypeList.setDefaultValue(fixedMediaTypes[0].toString());
		root.addPreference(rtcTypeList);

		root.addPreference(buildDemoSelector());

		return root;
	}

	private ListPreference buildDemoSelector() {
		final ListPreference demosList = new ListPreference(this);
		demosList.setKey(DEMOS_LIST_KEY);
		demosList.setTitle(getText(R.string.preference_demo_selector));
		demosList.setSummary(getDemoUrl(this));

		CharSequence[] demos = getDemos();
		demosList.setEntries(demos);
		demosList.setEntryValues(demos);
		demosList
				.setDefaultValue(getString(R.string.preference_url_demo_default));

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.preference_custom_demo_url_title));

		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		builder.setView(input);

		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String t = input.getText().toString();
						if (!t.isEmpty()) {
							demosList.setSummary(t);
							CharSequence[] newEntries = Preferences.this
									.updateCustomDemos(t);
							demosList.setEntries(newEntries);
							demosList.setEntryValues(newEntries);
							demosList.setValue(t);
						}
					}
				});

		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				});

		final AlertDialog dialog = builder.create();

		demosList
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						if (getString(R.string.preference_custom_demo_item)
								.equals(newValue)) {
							dialog.show();
							return false;
						}

						preference.setSummary(newValue.toString());
						return true;
					}
				});

		return demosList;
	}

	private CharSequence[] getDemos() {
		CharSequence[] fixedDemos = getResources().getStringArray(
				R.array.urls_list);
		Set<String> customDemos = PreferenceManager
				.getDefaultSharedPreferences(this).getStringSet(
						CUSTOM_DEMOS_SET_KEY, null);

		int entriesSize = fixedDemos.length + 1;
		if (customDemos != null) {
			entriesSize += customDemos.size();
		}
		CharSequence[] entries = new CharSequence[entriesSize];

		int i = 0;
		for (; i < fixedDemos.length; i++) {
			entries[i] = fixedDemos[i];
		}

		if (customDemos != null) {
			List<String> customDemosList = new ArrayList<String>(customDemos);
			Collections.sort(customDemosList);
			for (String demo : customDemosList) {
				entries[i++] = demo;
			}
		}

		entries[i++] = getString(R.string.preference_custom_demo_item);

		return entries;
	}

	private void addCustomDemo(String entry) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(this);

		Set<String> customDemos = pref.getStringSet(CUSTOM_DEMOS_SET_KEY, null);
		if (customDemos == null) {
			customDemos = new HashSet<String>();
		}
		customDemos.add(entry);

		pref.edit().putStringSet(CUSTOM_DEMOS_SET_KEY, customDemos).commit();
	}

	CharSequence[] updateCustomDemos(String entry) {
		addCustomDemo(entry);
		return getDemos();
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

	public static RtcType getMediaType(Context context) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);

		String value = pref.getString(RTC_TYPE_KEY,
				context.getString(R.string.preference_rtc_type_default));
		if (context.getString(R.string.preference_rtc_type_webrtc)
				.equals(value)) {
			return RtcType.WEBRTC;
		}

		return RtcType.RTP;
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
