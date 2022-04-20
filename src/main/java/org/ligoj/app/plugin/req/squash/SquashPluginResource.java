/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.req.squash;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.req.ReqResource;
import org.ligoj.app.plugin.req.ReqServicePlugin;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.BitBucketTag;
import org.ligoj.app.resource.plugin.BitBucketTags;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Squash TM resource.
 */
@Path(SquashPluginResource.URL)
@Service
@Produces(MediaType.APPLICATION_JSON)
public class SquashPluginResource extends AbstractToolPluginResource implements ReqServicePlugin {

	private static final TypeReference<TableItem<SquashProject>> VALUE_TYPE_REF = new TypeReference<>() {
		// Nothing to override
	};

	/**
	 * Squash TM version tags
	 */
	private static final String VERSION_TAG_START = "<label>Version</label><span>";
	private static final String VERSION_TAG_END = "</span>";

	/**
	 * Plug-in key.
	 */
	public static final String URL = ReqResource.SERVICE_URL + "/squash";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * Public server URL used to fetch the last available version of the product.
	 */
	@Value("${service-req-squash-server:https://api.bitbucket.org}")
	private String publicServer;

	/**
	 * Squash TM user name able to connect to instance.
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * Squash TM user password able to connect to instance.
	 */
	public static final String PARAMETER_PASSWORD = KEY + ":password";

	/**
	 * Squash TM project's identifier, an integer
	 */
	public static final String PARAMETER_PROJECT = KEY + ":project";

	/**
	 * Web site URL
	 */
	public static final String PARAMETER_URL = KEY + ":url";

	@Override
	public void link(final int subscription) throws IOException {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		// Validate the project settings
		validateProject(parameters);
	}

	/**
	 * Validate the project connectivity.
	 *
	 * @param parameters the project parameters.
	 * @return project details.
	 * @throws IOException When the Squash TM content cannot be parsed.
	 */
	protected SquashProject validateProject(final Map<String, String> parameters) throws IOException {
		// Get project's configuration
		final int id = Integer.parseInt(ObjectUtils.defaultIfNull(parameters.get(PARAMETER_PROJECT), "0"));
		final SquashProject result = getProject(parameters, id);

		if (result == null) {
			// Invalid id
			throw new ValidationJsonException(PARAMETER_PROJECT, "squash-project", id);
		}

		return result;
	}

	/**
	 * Validate the basic REST connectivity to Squash.
	 *
	 * @param parameters the server parameters.
	 * @return the detected Squash version.
	 */
	protected String validateAdminAccess(final Map<String, String> parameters) {
		final String url = StringUtils.appendIfMissing(parameters.get(PARAMETER_URL), "/");

		// Check access
		CurlProcessor.validateAndClose(url + "login", PARAMETER_URL, "squash-connection");

		// Authentication request
		try (SquashCurlProcessor curl = new SquashCurlProcessor()) {
			if (!curl.process(authenticate(parameters, url))) {
				throw new ValidationJsonException(PARAMETER_USER, "squash-login");
			}

			// Check the user has enough rights to access to the administration page
			final CurlRequest admin = new CurlRequest("GET", url + "administration", null);
			admin.setSaveResponse(true);
			if (!curl.process(admin)) {
				throw new ValidationJsonException(PARAMETER_USER, "squash-admin");
			}
			return getVersion(admin.getResponse());
		}
	}

	/**
	 * Create and return an authenticate request.
	 */
	private CurlRequest authenticate(final Map<String, String> parameters, final String url) {
		return new CurlRequest(HttpMethod.POST, StringUtils.appendIfMissing(url, "/") + "login",
				"username=" + parameters.get(PARAMETER_USER) + "&password="
						+ StringUtils.trimToEmpty(parameters.get(PARAMETER_PASSWORD)),
				SquashCurlProcessor.LOGIN_CALLBACK,
				"Accept:text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
	}

	/**
	 * Return a Squash's resource. Return <code>null</code> when the resource is not
	 * found.
	 * 
	 * @param parameters The subscription parameters.
	 * @param resource   The requested resource URL
	 * @return The resource content.
	 */
	protected String getResource(final Map<String, String> parameters, final String resource) {
		return getResource(new SquashCurlProcessor(), parameters, parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a Squash's resource. Return <code>null</code> when the resource is not
	 * found.
	 * 
	 * @param processor  The CURL processor.
	 * @param parameters The subscription parameters.
	 * @param url        The base URL.
	 * @param resource   The requested resource URL
	 * @return The resource content.
	 */
	protected String getResource(final CurlProcessor processor, final Map<String, String> parameters, final String url,
			final String resource) {
		// Get the resource using the preempted authentication
		final CurlRequest request = new CurlRequest(HttpMethod.GET, StringUtils.appendIfMissing(url, "/") + resource,
				null);
		request.setSaveResponse(true);

		// Execute the requests
		processor.process(authenticate(parameters, url), request);
		processor.close();
		return request.getResponse();
	}

	/**
	 * Redirect to the home page of the linked project. Send a redirect code with
	 * the relevant cookies used by Squash TM since there is no way to force the
	 * link to a desired project.
	 * 
	 * @param subscription The subscription identifier.
	 *
	 * @return The response redirection to go to the right project.
	 * @throws URISyntaxException When the Squash TM base URL is malformed.
	 */
	@GET
	@Path("redirect/{subscription:\\d+}")
	public Response redirect(@PathParam("subscription") final int subscription) throws URISyntaxException {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
		final ResponseBuilder responseBuilder = Response.status(Status.FOUND).location(
				new URI(StringUtils.appendIfMissing(parameters.get(PARAMETER_URL), "/") + "requirement-workspace/"));
		responseBuilder.cookie(
				new NewCookie("jstree_open", "%23RequirementLibrary-" + parameters.get(PARAMETER_PROJECT), "/", null,
						null, -1, false),
				new NewCookie("jstree_select", "%23RequirementLibrary-" + parameters.get(PARAMETER_PROJECT), "/", null,
						null, -1, false));
		return responseBuilder.build();
	}

	@Override
	public String getVersion(final Map<String, String> parameters) {
		// Get the version from the raw HTML of the administration page
		return getVersion(getResource(parameters, "administration"));
	}

	private String getVersion(final String adminPage) {
		// Get the version from the raw HTML of the administration page
		final String page = ObjectUtils.defaultIfNull(adminPage, VERSION_TAG_START + VERSION_TAG_END);
		final int versionIndex = page.indexOf(VERSION_TAG_START) + VERSION_TAG_START.length();
		return page.substring(versionIndex, page.indexOf(VERSION_TAG_END, versionIndex));
	}

	/**
	 * Return all Squash TM projects without limit.
	 * 
	 * @param parameters The subscription parameters.
	 * @return The resource content.
	 * @throws IOException When the Squash TM content cannot be parsed.
	 */
	protected List<SquashProject> getProjects(final Map<String, String> parameters) throws IOException {
		return getProjectsDataTables(parameters, null);
	}

	/**
	 * Return all Squash TM projects without limit and an optional criteria.
	 * 
	 * @param parameters The subscription parameters.
	 * @param criteria   The criteria (plain text) for the lookup.
	 * @return The resource content.
	 * @throws IOException When the Squash TM content cannot be parsed.
	 */
	protected List<SquashProject> getProjectsDataTables(final Map<String, String> parameters, final String criteria)
			throws IOException {
		return new ObjectMapper().readValue(
				StringUtils.defaultIfEmpty(getResource(parameters,
						"generic-projects?sEcho=4&iDisplayStart=0&iDisplayLength=100000"
								+ (criteria == null ? "" : "&sSearch=" + criteria)),
						"{\"aaData\":[]}"),
				VALUE_TYPE_REF).getAaData();
	}

	/**
	 * Return Squash project from its identifier.
	 * 
	 * @param parameters The subscription parameters.
	 * @param id         The Squash TM project identifier.
	 * @return The resource content.
	 * @throws IOException When the Squash TM content cannot be parsed.
	 */
	protected SquashProject getProject(final Map<String, String> parameters, final int id) throws IOException {
		return getProjects(parameters).stream().filter(project -> project.getId().equals(id)).findFirst().orElse(null);
	}

	/**
	 * Search the Squash TM the projects matching to the given criteria. Name only
	 * is considered.
	 *
	 * @param node     the node to be tested with given parameters.
	 * @param criteria the search criteria.
	 * @return project names matching the criteria.
	 * @throws IOException When the Squash TM content cannot be parsed.
	 */
	@GET
	@Path("{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<SquashProject> findAllByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) throws IOException {

		// Prepare the context, an ordered set of projects
		final Map<String, String> parameters = pvResource.getNodeParameters(node);

		// Get the projects and parse them
		return getProjectsDataTables(parameters, criteria);
	}

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public String getLastVersion() throws IOException {
		try (CurlProcessor curl = new CurlProcessor()) {
			final String tagsAsJson = curl.get(publicServer
					+ "/2.0/repositories/nx/squashtest-tm/refs/tags?pagelen=1&q=name~%22squash-tm-%22&sort=-target.date",
					"Content-Type:application/json");
			return StringUtils.removeStart(
					new ObjectMapper()
							.readValue(StringUtils.defaultIfEmpty(tagsAsJson, "{\"values\":[]}"), BitBucketTags.class)
							.getValues().stream().findFirst().map(BitBucketTag::getName).orElse("squash-tm-"),
					"squash-tm-");
		}
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws IOException {
		final SubscriptionStatusWithData nodeStatusWithData = new SubscriptionStatusWithData();
		nodeStatusWithData.put("project", validateProject(parameters));
		return nodeStatusWithData;
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		// Status is UP <=> Administration access is UP
		validateAdminAccess(parameters);
		return true;
	}

}
