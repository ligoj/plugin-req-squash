package org.ligoj.app.plugin.req.squash;

import org.ligoj.bootstrap.core.NamedBean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

/**
 * Squash TM project retrieved from HTML pages. Name, and also some additional information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SquashProject extends NamedBean<Integer> {

	/**
	 * Project identifier.
	 */
	@JsonProperty(value = "project-id", access = Access.WRITE_ONLY)
	private int project;

	/**
	 * Set the project
	 * 
	 * @param project
	 *            Identifier
	 */
	public void setProject(final int project) {
		setId(project);
	}
}
