package ca.uhn.fhir.jpa.dao;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.junit.Before;

import ca.uhn.fhir.jpa.rp.dstu2.PatientResourceProvider;
import ca.uhn.fhir.rest.method.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;

public abstract class BaseJpaDstu2SystemTest extends BaseJpaDstu2Test {
	protected RequestDetails myRequestDetails;
	private RestfulServer myServer;

	@SuppressWarnings("unchecked")
	@Before
	public void before() throws ServletException {
		myRequestDetails = mock(RequestDetails.class);

		if (myServer == null) {
			myServer = new RestfulServer(myFhirCtx);

			PatientResourceProvider patientRp = new PatientResourceProvider();
			patientRp.setDao(myPatientDao);
			myServer.setResourceProviders(patientRp);
			myServer.init(mock(ServletConfig.class));
		}

		when(myRequestDetails.getServer()).thenReturn(myServer);
		HttpServletRequest servletRequest = mock(HttpServletRequest.class);
		when(myRequestDetails.getServletRequest()).thenReturn(servletRequest);
		when(myRequestDetails.getFhirServerBase()).thenReturn("http://example.com/base");
		when(servletRequest.getHeaderNames()).thenReturn(mock(Enumeration.class));
		when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("/Patient"));
	}

}
