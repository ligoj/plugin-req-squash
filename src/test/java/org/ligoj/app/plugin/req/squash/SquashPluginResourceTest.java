package org.ligoj.app.plugin.req.squash;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.transaction.Transactional;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.resource.subscription.SubscriptionResource;
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
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv", new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");

		// Coverage only
		resource.getKey();
	}

	/**
	 * Return the subscription identifier of gStack. Assumes there is only one
	 * subscription for a service.
	 */
	protected Integer getSubscription(final String project) {
		return getSubscription(project, SquashPluginResource.KEY);
	}

	@Test
	public void delete() throws Exception {
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		// No custom data -> nothing to check;
	}

	@Test
	public void getVersion() throws Exception {
		prepareMockAdmin();
		Assertions.assertEquals("1.12.1.RELEASE", resource.getVersion(subscription));
	}

	@Test
	public void getLastVersion() throws Exception {
		Assertions.assertTrue(resource.getLastVersion().length() > 4);
	}

	@Test
	public void link() throws Exception {
		prepareMockProject();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SquashTM
		resource.link(this.subscription);
	}

	@Test
	public void linkNotFound() throws IOException {
		prepareMockProject();
		httpServer.start();

		parameterValueRepository.findAllBySubscription(subscription).stream()
				.filter(v -> v.getParameter().getId().equals(SquashPluginResource.KEY + ":project")).findFirst().get().setData("0");
		em.flush();
		em.clear();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SquashTM
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), "service:req:squash:project", "squash-project");
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		prepareMockProject();
		final SubscriptionStatusWithData nodeStatusWithData = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(nodeStatusWithData.getStatus().isUp());
	}

	private void prepareMockProject() throws IOException {
		// Main entry
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(post(urlEqualTo("/login"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Project json
		httpServer.stubFor(get(urlEqualTo("/generic-projects?sEcho=4&iDisplayStart=0&iDisplayLength=100000"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/squash/generic-projects.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockProjectSearch() throws IOException {
		// Login
		httpServer.stubFor(post(urlEqualTo("/login"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Project json
		httpServer.stubFor(get(urlEqualTo("/generic-projects?sEcho=4&iDisplayStart=0&iDisplayLength=100000&sSearch=client1"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(
						IOUtils.toString(new ClassPathResource("mock-server/squash/generic-projects-client1.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockAdmin() throws IOException {
		// Main entry
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(post(urlEqualTo("/login"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Administration page for version
		httpServer.stubFor(get(urlEqualTo("/administration")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils
				.toString(new ClassPathResource("mock-server/squash/administration.html").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	public void checkStatus() throws Exception {
		prepareMockAdmin();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void create() {
		Assertions.assertThrows(NotImplementedException.class, () -> {
			resource.create(0);
		});
	}

	@Test
	public void checkStatusAuthenticationFailed() {
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(post(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN).withBody("")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), SquashPluginResource.KEY + ":user", "squash-login");
	}

	@Test
	public void checkStatusNotAdmin() {
		// Main entry
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("")));

		// Login
		httpServer.stubFor(post(urlEqualTo("/login"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_MOVED_TEMPORARILY).withBody("").withHeader("location", "some")));

		// Administration page for version
		httpServer.stubFor(get(urlEqualTo("/administration")).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN).withBody("")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), SquashPluginResource.KEY + ":user", "squash-admin");
	}

	@Test
	public void checkStatusInvalidIndex() {
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), "pkey", "unknown-id");
	}

	@Test
	public void findAllByName() throws IOException {
		prepareMockProjectSearch();
		httpServer.start();

		final List<SquashProject> projects = resource.findAllByName("service:req:squash:dig", "client1");
		Assertions.assertEquals(3, projects.size());
		Assertions.assertEquals(79, projects.get(0).getId().intValue());
		Assertions.assertEquals("Client1 - P1", projects.get(0).getName());
	}

	@Test
	public void findAllByNameNoListing() throws IOException {
		prepareMockAdmin();
		httpServer.start();

		final List<SquashProject> projects = resource.findAllByName("service:req:squash:dig", "client1");
		Assertions.assertEquals(0, projects.size());
	}

	@Test
	public void redirect() throws IOException, URISyntaxException {
		prepareMockAdmin();
		httpServer.start();
		final Response response = resource.redirect(subscription);
		Assertions.assertEquals(302, response.getStatus());
		Assertions.assertEquals("http://localhost:8120/requirement-workspace/", response.getHeaderString("location"));
		Assertions.assertEquals("%23RequirementLibrary-1", response.getCookies().get("jstree_open").getValue());
		Assertions.assertEquals("%23RequirementLibrary-1", response.getCookies().get("jstree_select").getValue());
	}

}
