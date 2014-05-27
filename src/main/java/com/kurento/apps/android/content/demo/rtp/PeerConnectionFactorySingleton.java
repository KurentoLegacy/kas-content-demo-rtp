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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.AudioSource;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaConstraints.KeyValuePair;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;

public class PeerConnectionFactorySingleton extends PeerConnectionFactory {

	private static final Logger log = LoggerFactory
			.getLogger(PeerConnectionFactorySingleton.class.getSimpleName());

	private static PeerConnectionFactorySingleton instance = null;

	private PeerConnectionFactorySingleton() {
	}

	public synchronized static PeerConnectionFactorySingleton getInstance() {
		if (instance == null) {
			instance = new PeerConnectionFactorySingleton();
		}

		return instance;
	}

	/* Video */
	private static VideoCapturer videoCapturer;
	private static VideoSource videoSource;

	public synchronized static VideoSource getVideoSource() {
		if (videoSource != null) {
			return videoSource;
		}

		PeerConnectionFactory peerConnectionFactory = PeerConnectionFactorySingleton
				.getInstance();

		videoCapturer = VideoCapturer
				.create("Camera 0, Facing back, Orientation 90");

		MediaConstraints vc = new MediaConstraints();
		videoSource = peerConnectionFactory
				.createVideoSource(videoCapturer, vc);

		return videoSource;
	}

	synchronized static void disposeVideoSource() {
		if (videoCapturer != null) {
			videoCapturer.dispose();
			videoCapturer = null;
		}

		if (videoSource != null) {
			videoSource.stop();
			videoSource.dispose();
			videoSource = null;
		}
	}

	static AudioSource createAudioSource() {
		MediaConstraints ac = new MediaConstraints();
		ac.optional.add(new KeyValuePair("googEchoCancellation", "false"));
		ac.optional.add(new KeyValuePair("googNoiseSuppression", "false"));

		return PeerConnectionFactorySingleton.getInstance().createAudioSource(
				ac);
	}

}
