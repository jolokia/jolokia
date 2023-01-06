package org.jolokia.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;

public class ServletUtil {

	/**
	 * Create a servlet input stream usable in tests
	 *
	 * @param pData data which should be returned on read of the stream
	 * @return the created servlet input stream
	 */
	public static ServletInputStream createServletInputStream(String pData) {
		final ByteArrayInputStream bis =
				new ByteArrayInputStream(pData.getBytes());
		return new ServletInputStream() {
			@Override
			public boolean isFinished() {
				return bis.available() == 0;
			}

			@Override
			public boolean isReady() {
				return bis.available() > 0;
			}

			@Override
			public void setReadListener(ReadListener readListener) {

			}

			@Override
			public int read() throws IOException {
				return bis.read();
			}
		};
	}

	/**
	 * Prepare a servlet config Mock
	 *
	 * @param config configuration mock to prepare
	 * @param pInitParams init params to return on config.getInitParameter()
	 */
	@SuppressWarnings("PMD.ReplaceVectorWithList")
	public static void prepareServletConfigMock(ServletConfig config,String ... pInitParams) {
		Map<String,String> configParams = new HashMap<String, String>();
		if (pInitParams != null) {
			for (int i = 0; i < pInitParams.length; i += 2) {
				configParams.put(pInitParams[i],pInitParams[i+1]);
			}
			for (Map.Entry<String,String> entry : configParams.entrySet()) {
				EasyMock.expect(config.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
			}
		}

		final Vector<String> paramNames = new Vector<String>(configParams.keySet());
		EasyMock.expect(config.getInitParameterNames()).andAnswer(new IAnswer<Enumeration<String>>() {
			public Enumeration answer() throws Throwable {
				return paramNames.elements();
			}
		}).anyTimes();
	}

	/**
	 * Prepare a servlet context Mock so that the config parameters are returned properly
	 *
	 * @param pContext mocked context
	 * @param pContextParams context parameters to return
	 */
	public static void prepareServletContextMock(ServletContext pContext, String ... pContextParams) {
		Map<String,String> configParams = new HashMap<String, String>();
		if (pContextParams != null) {
			for (int i = 0; i < pContextParams.length; i += 2) {
				configParams.put(pContextParams[i],pContextParams[i+1]);
			}
			for (Map.Entry<String,String> entry : configParams.entrySet()) {
				EasyMock.expect(pContext.getInitParameter(entry.getKey())).andReturn(entry.getValue()).anyTimes();
			}
		}
		final Vector paramNames = new Vector(configParams.keySet());
		EasyMock.expect(pContext.getInitParameterNames()).andAnswer(new IAnswer<Enumeration<String>>() {
			public Enumeration answer() throws Throwable {
				return paramNames.elements();
			}
		}).anyTimes();
	}
}
