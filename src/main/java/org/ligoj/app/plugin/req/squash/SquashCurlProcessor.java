/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.req.squash;

import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.HttpResponseCallback;
import org.ligoj.bootstrap.core.curl.OnlyRedirectHttpResponseCallback;

/**
 * SonarQube processor.
 */
public class SquashCurlProcessor extends CurlProcessor {

	/**
	 * Login callback.
	 */
	public static final HttpResponseCallback LOGIN_CALLBACK = new OnlyRedirectHttpResponseCallback();

}
