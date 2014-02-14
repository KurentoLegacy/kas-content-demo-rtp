/*
 * kurento VideoStreamView.java
 * Copyright 2013, Kurento
 * Copyright 2013, Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.kurento.apps.android.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.VideoRenderer.I420Frame;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

/**
 * A GLSurfaceView{,.Renderer} that efficiently renders YUV frames from local &
 * remote VideoTracks using the GPU for CSC. Clients will want to call the
 * constructor, setSize() and updateFrame() as appropriate, but none of the
 * other public methods of this class are of interest to clients (only to system
 * classes).
 */
public class VideoStreamView extends GLSurfaceView implements
		GLSurfaceView.Renderer {

	private static final Logger log = LoggerFactory
			.getLogger(VideoStreamView.class.getSimpleName());

	private int viewWidth = -1;
	private int viewHeight = -1;

	private class Stream {
		private int[] texture = { -1, -1, -1 };
		private int width = -1;
		private int height = -1;
		private FloatBuffer vertices = directNativeFloatBuffer(new float[] { 0,
				0, 0, 0, 0, 0, 0, 0 });
	};

	private int posLocation = -1;
	private long lastFPSLogTime = System.nanoTime();
	private long numFramesSinceLastLog = 0;
	private final FramePool framePool = new FramePool();

	private final ArrayList<Stream> streams = new ArrayList<Stream>();

	public VideoStreamView(Context c) {
		super(c);

		setEGLConfigChooser(false); // Don't need a depth buffer.

		setEGLContextClientVersion(2);
		setRenderer(this);
		setRenderMode(RENDERMODE_WHEN_DIRTY);
		setBackgroundColor(android.R.color.transparent);
		setZOrderMediaOverlay(true);
	}

	public synchronized int registerStream() {
		int id = streams.size();
		streams.add(new Stream());
		return id;
	}

	public synchronized void setStreamDimensions(int streamId, int width,
			int height, int xPos, int yPos) {
		Stream stream = streams.get(streamId);

		int sXMin = xPos;
		int sXMax = sXMin + width;
		int sYMin = yPos;
		int sYMax = sYMin + height;

		int location[] = new int[2];
		getLocationInWindow(location);
		int xMin = location[0];
		int xMax = xMin + getWidth();
		int yMin = location[1];
		int yMax = yMin + getHeight();

		if (xMin > sXMin || xMax < sXMax || yMin > sYMin || yMax < sYMax) {
			log.error("Widget out of surface gl boundaries");
			return;
		}

		float x1 = ((float) (sXMin - xMin) / xMax) * 2 - 1;
		float x2 = ((float) (sXMax - xMin) / xMax) * 2 - 1;
		float y1 = ((float) (sYMin - yMin) / yMax) * -2 + 1;
		float y2 = ((float) (sYMax - yMin) / yMax) * -2 + 1;

		log.debug("Stream: " + streamId + " vertices");
		log.debug("x1: " + x1);
		log.debug("x2: " + x2);
		log.debug("y1: " + y1);
		log.debug("y2: " + y2);

		stream.vertices = directNativeFloatBuffer(new float[] { x1, y1, x1, y2,
				x2, y1, x2, y2 });

	}

	/** Queue |frame| to be uploaded. */
	public void queueFrame(final int streamId, I420Frame frame) {
		// Paying for the copy of the YUV data here allows CSC and painting time
		// to get spent on the render thread instead of the UI thread.
		abortUnless(FramePool.validateDimensions(frame), "Frame too large!");

		I420Frame frameTaken = framePool.takeFrame(frame);
		if (frameTaken == null) {
			log.warn("Frame will not be shown");
			return;
		}

		final I420Frame frameCopy = frameTaken.copyFrom(frame);
		queueEvent(new Runnable() {
			@Override
			public void run() {
				updateFrame(streamId, frameCopy);
			}
		});
	}

	// Upload the planes from |frame| to the textures owned by this View.
	private void updateFrame(int streamId, I420Frame frame) {
		int[] textures;

		synchronized (this) {
			textures = streams.get(streamId).texture;
		}

		texImage2D(frame, textures);
		framePool.returnFrame(frame);
		requestRender();
	}

	private synchronized void resize() {
		log.debug("resize");
		if (viewWidth == -1 || viewHeight == -1)
			return;

		GLES20.glViewport(0, 0, viewWidth, viewHeight);

		for (Stream stream : streams) {
			if (stream.width != -1 && stream.height != -1) {
				GLES20.glGenTextures(3, stream.texture, 0);
				for (int i = 0; i < 3; ++i) {
					int w = i == 0 ? stream.width : stream.width / 2;
					int h = i == 0 ? stream.height : stream.height / 2;
					GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
							stream.texture[i]);
					GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0,
							GLES20.GL_LUMINANCE, w, h, 0, GLES20.GL_LUMINANCE,
							GLES20.GL_UNSIGNED_BYTE, null);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
				}
			}
		}

		checkNoGLES2Error();
	}

	/** Inform this View of the dimensions of frames coming from |stream|. */
	public synchronized void setSize(int streamId, int width, int height) {
		log.debug("setSize endpoint: " + streamId);

		// Generate 3 texture ids for Y/U/V and place them into |textures|,
		// allocating enough storage for |width|x|height| pixels.

		Stream stream = streams.get(streamId);
		stream.width = width;
		stream.height = height;

		resize();
	}

	@Override
	public synchronized void onSurfaceChanged(GL10 unused, int width, int height) {
		log.debug("onSurfaceChanged: " + width + "x" + height);
		viewWidth = width;
		viewHeight = height;
		resize();
	}

	@Override
	public void onDrawFrame(GL10 unused) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

		synchronized (this) {
			for (Stream stream : streams) {
				drawRectangle(stream.texture, stream.vertices);
			}
		}

		++numFramesSinceLastLog;
		long now = System.nanoTime();
		if (lastFPSLogTime == -1 || now - lastFPSLogTime > 1e9) {
			double fps = numFramesSinceLastLog / ((now - lastFPSLogTime) / 1e9);
			log.debug("Rendered FPS: " + fps);
			lastFPSLogTime = now;
			numFramesSinceLastLog = 1;
		}
		checkNoGLES2Error();
	}

	@Override
	public void onSurfaceCreated(GL10 unused, EGLConfig config) {
		log.debug("On surface created");
		int program = GLES20.glCreateProgram();
		addShaderTo(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_STRING, program);
		addShaderTo(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_STRING, program);

		GLES20.glLinkProgram(program);
		int[] result = new int[] { GLES20.GL_FALSE };
		result[0] = GLES20.GL_FALSE;
		GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
		abortUnless(result[0] == GLES20.GL_TRUE,
				GLES20.glGetProgramInfoLog(program));
		GLES20.glUseProgram(program);

		GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "y_tex"), 0);
		GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_tex"), 1);
		GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "v_tex"), 2);

		// Actually set in drawRectangle(), but queried only once here.
		posLocation = GLES20.glGetAttribLocation(program, "in_pos");

		int tcLocation = GLES20.glGetAttribLocation(program, "in_tc");
		GLES20.glEnableVertexAttribArray(tcLocation);
		GLES20.glVertexAttribPointer(tcLocation, 2, GLES20.GL_FLOAT, false, 0,
				textureCoords);

		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		checkNoGLES2Error();
	}

	// Wrap a float[] in a direct FloatBuffer using native byte order.
	private static FloatBuffer directNativeFloatBuffer(float[] array) {
		FloatBuffer buffer = ByteBuffer.allocateDirect(array.length * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		buffer.put(array);
		buffer.flip();
		return buffer;
	}

	// Upload the YUV planes from |frame| to |textures|.
	private void texImage2D(I420Frame frame, int[] textures) {
		for (int i = 0; i < 3; ++i) {
			ByteBuffer plane = frame.yuvPlanes[i];
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
			int w = i == 0 ? frame.width : frame.width / 2;
			int h = i == 0 ? frame.height : frame.height / 2;
			abortUnless(w == frame.yuvStrides[i], frame.yuvStrides[i] + "!="
					+ w);
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
					w, h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE,
					plane);
		}
		checkNoGLES2Error();
	}

	// Draw |textures| using |vertices| (X,Y coordinates).
	private void drawRectangle(int[] textures, FloatBuffer vertices) {
		for (int i = 0; i < 3; ++i) {
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
		}

		GLES20.glVertexAttribPointer(posLocation, 2, GLES20.GL_FLOAT, false, 0,
				vertices);
		GLES20.glEnableVertexAttribArray(posLocation);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		checkNoGLES2Error();
	}

	// Compile & attach a |type| shader specified by |source| to |program|.
	private static void addShaderTo(int type, String source, int program) {
		int[] result = new int[] { GLES20.GL_FALSE };
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, source);
		GLES20.glCompileShader(shader);
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
		abortUnless(result[0] == GLES20.GL_TRUE,
				GLES20.glGetShaderInfoLog(shader) + ", source: " + source);
		GLES20.glAttachShader(program, shader);
		GLES20.glDeleteShader(shader);
		checkNoGLES2Error();
	}

	// Poor-man's assert(): die with |msg| unless |condition| is true.
	private static void abortUnless(boolean condition, String msg) {
		if (!condition) {
			throw new RuntimeException(msg);
		}
	}

	// Assert that no OpenGL ES 2.0 error has been raised.
	private static void checkNoGLES2Error() {
		int error = GLES20.glGetError();
		abortUnless(error == GLES20.GL_NO_ERROR, "GLES20 error: " + error);
	}

	// Texture Coordinates mapping the entire texture.
	private static final FloatBuffer textureCoords = directNativeFloatBuffer(new float[] {
			0, 0, 0, 1, 1, 0, 1, 1 });

	// Pass-through vertex shader.
	private static final String VERTEX_SHADER_STRING = "varying vec2 interp_tc;\n"
			+ "\n"
			+ "attribute vec4 in_pos;\n"
			+ "attribute vec2 in_tc;\n"
			+ "\n"
			+ "void main() {\n"
			+ "  gl_Position = in_pos;\n"
			+ "  interp_tc = in_tc;\n" + "}\n";

	// YUV to RGB pixel shader. Loads a pixel from each plane and pass through
	// the
	// matrix.
	private static final String FRAGMENT_SHADER_STRING = "precision mediump float;\n"
			+ "varying vec2 interp_tc;\n"
			+ "\n"
			+ "uniform sampler2D y_tex;\n"
			+ "uniform sampler2D u_tex;\n"
			+ "uniform sampler2D v_tex;\n"
			+ "\n"
			+ "void main() {\n"
			+ "  float y = texture2D(y_tex, interp_tc).r;\n"
			+ "  float u = texture2D(u_tex, interp_tc).r - .5;\n"
			+ "  float v = texture2D(v_tex, interp_tc).r - .5;\n"
			+
			// CSC according to http://www.fourcc.org/fccyvrgb.php
			"  gl_FragColor = vec4(y + 1.403 * v, "
			+ "                      y - 0.344 * u - 0.714 * v, "
			+ "                      y + 1.77 * u, 1);\n" + "}\n";

}
