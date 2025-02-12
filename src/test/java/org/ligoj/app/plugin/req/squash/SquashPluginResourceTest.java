/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.req.squash;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link SquashPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class SquashPluginResourceTest extends AbstractServerTest {
	@Autowired
	private SquashPluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ParameterValueRepository parameterValueRepository;

	protected int subscription;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class<?>[]{Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class},
				StandardCharsets.UTF_8);
		this.subscription = getSubscription("Jupiter");

		// Coverage only
		Assertions.assertEquals("service:req:squash", resource.getKey());
	}

	/**
	 * Return the subscription identifier of Jupiter. Assumes there is only one subscription for a service.
	 */
	private Integer getSubscription(final String project) {
		return getSubscription(project, SquashPluginResource.KEY);
	}

	@Test
	void delete() throws Exception {
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		// No custom data -> nothing to check;
	}

	@Test
	void getVersion() throws Exception {
		prepareMockAdmin();
		Assertions.assertEquals("1.12.1.RELEASE", resource.getVersion(subscription));
	}

	@Test
	void getLastVersion() throws Exception {
		Assertions.assertTrue(resource.getLastVersion().length() > 4);
	}

	@Test
	void link() throws Exception {
		prepareMockProject();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SquashTM
		resource.link(this.subscription);
	}

	@Test
	void linkNotFound() throws IOException {
		prepareMockProject();
		httpServer.start();

		parameterValueRepository.findAllBySubscription(subscription).stream()
				.filter(v -> v.getParameter().getId().equals(SquashPluginResource.KEY + ":project")).findFirst().get()
				.setData("0");
		em.flush();
		em.clear();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SquashTM
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.link(this.subscription)), "service:req:squash:project", "squash-project");
	}

	@Test
	void checkSubscriptionStatus() throws Exception {
		prepareMockProject();
		final SubscriptionStatusWithData nodeStatusWithData = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(nodeStatusWithData.getStatus().isUp());
	}

	@Test
	void checkSubscriptionStatusInvalidIndex() throws IOException {
		final Map<String, String> parameters = new HashMap<>(subscriptionResource.getParametersNoCheck(subscription));
		parameters.put("service:req:squash:project", "999");
		prepareMockProject();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkSubscriptionStatus(parameters)), "service:req:squash:project", "squash-project");
	}

	private void prepareMockProject() throws IOException {
		// Main entry
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(post(urlEqualTo("/login")).willReturn(
				aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Project json
		httpServer.stubFor(get(urlEqualTo("/generic-projects?sEcho=4&iDisplayStart=0&iDisplayLength=100000"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/squash/generic-projects.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockProjectSearch() throws IOException {
		// Login
		httpServer.stubFor(post(urlEqualTo("/login")).willReturn(
				aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Project json
		httpServer.stubFor(
				get(urlEqualTo("/generic-projects?sEcho=4&iDisplayStart=0&iDisplayLength=100000&sSearch=client1"))
						.willReturn(
								aResponse().withStatus(HttpStatus.SC_OK)
										.withBody(IOUtils.toString(new ClassPathResource(
														"mock-server/squash/generic-projects-client1.json").getInputStream(),
												StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockAdmin() throws IOException {
		// Main entry
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(post(urlEqualTo("/login")).willReturn(
				aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Administration page for version
		httpServer.stubFor(get(urlEqualTo("/administration")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/squash/administration.html").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	void checkStatus() throws Exception {
		prepareMockAdmin();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void create() {
		Assertions.assertThrows(NotImplementedException.class, () -> resource.create(0));
	}

	@Test
	void checkStatusAuthenticationFailed() {
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(
				post(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN).withBody("")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription))), SquashPluginResource.KEY + ":user", "squash-login");
	}

	@Test
	void checkStatusNotAdmin() {
		// Main entry
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(post(urlEqualTo("/login")).willReturn(
				aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Administration page for version
		httpServer.stubFor(get(urlEqualTo("/administration"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN).withBody("")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription))), SquashPluginResource.KEY + ":user", "squash-admin");
	}

	@Test
	void checkStatusInvalidUrl() {
		httpServer.stubFor(
				get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription))), "service:req:squash:url", "squash-connection");
	}

	@Test
	void findAllByName() throws IOException {
		prepareMockProjectSearch();
		httpServer.start();

		final List<SquashProject> projects = resource.findAllByName("service:req:squash:dig", "client1");
		Assertions.assertEquals(3, projects.size());
		Assertions.assertEquals(79, projects.getFirst().getId().intValue());
		Assertions.assertEquals("Client1 - P1", projects.getFirst().getName());
	}

	@Test
	void findAllByNameNoListing() throws IOException {
		prepareMockAdmin();
		httpServer.start();

		final var projects = resource.findAllByName("service:req:squash:dig", "client1");
		Assertions.assertEquals(0, projects.size());
	}

	@Test
	void redirect() throws IOException, URISyntaxException {
		prepareMockAdmin();
		httpServer.start();
		final Response response = resource.redirect(subscription);
		Assertions.assertEquals(302, response.getStatus());
		Assertions.assertEquals("http://localhost:8120/requirement-workspace/", response.getHeaderString("location"));
		Assertions.assertEquals("%23RequirementLibrary-1", response.getCookies().get("jstree_open").getValue());
		Assertions.assertEquals("%23RequirementLibrary-1", response.getCookies().get("jstree_select").getValue());
	}

}
