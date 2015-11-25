package ca.uhn.fhir.rest.server;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu.resource.Patient;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.util.PortUtil;

/**
 * Created by dsotnikov on 2/25/2014.
 */
public class PagingTest {

	private static CloseableHttpClient ourClient;
	private static int ourPort;
	private static Server ourServer;
	private static FhirContext ourContext;
	private static RestfulServer myRestfulServer;
	private static SimpleBundleProvider ourBundleProvider;
	private IPagingProvider myPagingProvider;

	@Test
	public void testSearchExactMatch() throws Exception {
		when(myPagingProvider.getDefaultPageSize()).thenReturn(5);
		when(myPagingProvider.getMaximumPageSize()).thenReturn(9);
		when(myPagingProvider.storeResultList(any(IBundleProvider.class))).thenReturn("ABCD");
		when(myPagingProvider.retrieveResultList(eq("ABCD"))).thenReturn(ourBundleProvider);

		String link;
		String base = "http://localhost:" + ourPort;
		{
			HttpGet httpGet = new HttpGet(base + "/Patient?_format=xml&_pretty=true");
			HttpResponse status = ourClient.execute(httpGet);
			String responseContent = IOUtils.toString(status.getEntity().getContent());
			IOUtils.closeQuietly(status.getEntity().getContent());

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourContext.newXmlParser().parseBundle(responseContent);
			assertEquals(5, bundle.getEntries().size());
			assertEquals("0", bundle.getEntries().get(0).getId().getIdPart());
			assertEquals("4", bundle.getEntries().get(4).getId().getIdPart());
			assertEquals(base + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=5&" + Constants.PARAM_COUNT + "=5&_format=xml&_pretty=true", bundle.getLinkNext()
					.getValue());
			assertNull(bundle.getLinkPrevious().getValue());
			link = bundle.getLinkNext().getValue();
		}
		{
			HttpGet httpGet = new HttpGet(link.replace("=xml", "=json"));
			HttpResponse status = ourClient.execute(httpGet);
			String responseContent = IOUtils.toString(status.getEntity().getContent());
			IOUtils.closeQuietly(status.getEntity().getContent());

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourContext.newJsonParser().parseBundle(responseContent);
			assertEquals(5, bundle.getEntries().size());
			assertEquals("5", bundle.getEntries().get(0).getId().getIdPart());
			assertEquals("9", bundle.getEntries().get(4).getId().getIdPart());
			assertNull(bundle.getLinkNext().getValue());
			assertEquals(base + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=0&" + Constants.PARAM_COUNT + "=5&_format=json&_pretty=true", bundle.getLinkPrevious()
					.getValue());
		}
	}

	@Test
	public void testSearchInexactOffset() throws Exception {
		when(myPagingProvider.getDefaultPageSize()).thenReturn(5);
		when(myPagingProvider.getMaximumPageSize()).thenReturn(9);
		when(myPagingProvider.storeResultList(any(IBundleProvider.class))).thenReturn("ABCD");
		when(myPagingProvider.retrieveResultList(eq("ABCD"))).thenReturn(ourBundleProvider);

		String base = "http://localhost:" + ourPort;
		{
			HttpGet httpGet = new HttpGet(base + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=8&" + Constants.PARAM_COUNT + "=5&_format=xml&_pretty=true");
			HttpResponse status = ourClient.execute(httpGet);
			String responseContent = IOUtils.toString(status.getEntity().getContent());
			IOUtils.closeQuietly(status.getEntity().getContent());

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourContext.newXmlParser().parseBundle(responseContent);
			assertEquals(2, bundle.getEntries().size());
			assertEquals("8", bundle.getEntries().get(0).getId().getIdPart());
			assertEquals("9", bundle.getEntries().get(1).getId().getIdPart());
			assertNull(bundle.getLinkNext().getValue());
			assertEquals(base + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=3&" + Constants.PARAM_COUNT + "=5&_format=xml&_pretty=true", bundle.getLinkPrevious()
					.getValue());
		}

	}

	@Test
	public void testSearchSmallPages() throws Exception {
		when(myPagingProvider.getDefaultPageSize()).thenReturn(5);
		when(myPagingProvider.getMaximumPageSize()).thenReturn(9);
		when(myPagingProvider.storeResultList(any(IBundleProvider.class))).thenReturn("ABCD");
		when(myPagingProvider.retrieveResultList(eq("ABCD"))).thenReturn(ourBundleProvider);

		String link;
		String base = "http://localhost:" + ourPort;
		{
			HttpGet httpGet = new HttpGet(base + "/Patient?_count=2");
			HttpResponse status = ourClient.execute(httpGet);
			String responseContent = IOUtils.toString(status.getEntity().getContent());
			IOUtils.closeQuietly(status.getEntity().getContent());

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourContext.newXmlParser().parseBundle(responseContent);
			assertEquals(2, bundle.getEntries().size());
			assertEquals("0", bundle.getEntries().get(0).getId().getIdPart());
			assertEquals("1", bundle.getEntries().get(1).getId().getIdPart());
			assertEquals(base + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=2&" + Constants.PARAM_COUNT + "=2&_format=xml", bundle.getLinkNext().getValue());
			assertNull(bundle.getLinkPrevious().getValue());
			link = bundle.getLinkNext().getValue();
		}
		{
			HttpGet httpGet = new HttpGet(link);
			HttpResponse status = ourClient.execute(httpGet);
			String responseContent = IOUtils.toString(status.getEntity().getContent());
			IOUtils.closeQuietly(status.getEntity().getContent());

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourContext.newXmlParser().parseBundle(responseContent);
			assertEquals(2, bundle.getEntries().size());
			assertEquals("2", bundle.getEntries().get(0).getId().getIdPart());
			assertEquals("3", bundle.getEntries().get(1).getId().getIdPart());
			assertEquals(base + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=4&" + Constants.PARAM_COUNT + "=2&_format=xml", bundle.getLinkNext().getValue());
			assertEquals(base + '/' + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=2&" + Constants.PARAM_COUNT + "=2&_format=xml", bundle.getLinkSelf().getValue());
			assertEquals(base + '?' + Constants.PARAM_PAGINGACTION + "=ABCD&" + Constants.PARAM_PAGINGOFFSET + "=0&" + Constants.PARAM_COUNT + "=2&_format=xml", bundle.getLinkPrevious().getValue());
		}

	}

	@AfterClass
	public static void afterClass() throws Exception {
		ourServer.stop();
	}

	@Before
	public void before() {
		myPagingProvider = mock(IPagingProvider.class);
		myRestfulServer.setPagingProvider(myPagingProvider);
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ourContext = new FhirContext();

		ourPort = PortUtil.findFreePort();
		ourServer = new Server(ourPort);

		DummyPatientResourceProvider patientProvider = new DummyPatientResourceProvider();

		ServletHandler proxyHandler = new ServletHandler();
		myRestfulServer = new RestfulServer();
		myRestfulServer.setResourceProviders(patientProvider);
		ServletHolder servletHolder = new ServletHolder(myRestfulServer);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

		List<IResource> retVal = new ArrayList<IResource>();
		for (int i = 0; i < 10; i++) {
			Patient patient = new Patient();
			patient.setId("" + i);
			patient.addName().addFamily("" + i);
			retVal.add(patient);
		}
		ourBundleProvider = new SimpleBundleProvider(retVal);
	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class DummyPatientResourceProvider implements IResourceProvider {

		@Search
		public IBundleProvider findPatient() {
			return ourBundleProvider;
		}

		@Override
		public Class<? extends IResource> getResourceType() {
			return Patient.class;
		}

	}

}
