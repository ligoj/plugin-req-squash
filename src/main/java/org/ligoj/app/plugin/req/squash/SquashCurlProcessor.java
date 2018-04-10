/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.req.squash;

import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.HttpResponseCallback;
import org.ligoj.app.resource.plugin.OnlyRedirectHttpResponseCallback;

/**
 * SonarQube processor.
 */
public class SquashCurlProcessor extends CurlProcessor {

	/**
	 * Login callback.
	 */
	public static final HttpResponseCallback LOGIN_CALLBACK = new OnlyRedirectHttpResponseCallback();

}
