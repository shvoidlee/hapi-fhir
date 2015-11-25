package ca.uhn.fhir.rest.client.interceptor;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;

import org.apache.commons.lang3.Validate;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;

import ca.uhn.fhir.rest.client.IClientInterceptor;
import ca.uhn.fhir.rest.server.Constants;

/**
 * HTTP interceptor to be used for adding HTTP Authorization using "bearer tokens" to requests. Bearer tokens are used for protocols such as OAUTH2 (see the <a
 * href="http://tools.ietf.org/html/rfc6750">RFC 6750</a> specification on bearer token usage for more information).
 * <p>
 * This interceptor adds a header resembling the following:<br/>
 * &nbsp;&nbsp;&nbsp;<code>Authorization: Bearer dsfu9sd90fwp34.erw0-reu</code><br/>
 * where the token portion (at the end of the header) is supplied by the invoking code.
 * </p>
 * <p>
 * See the <a href="http://hl7api.sourceforge.net/hapi-fhir/doc_rest_client.html#HTTP_Basic_Authorization">HAPI Documentation</a> for information on how to use this class.
 * </p>
 */
public class BearerTokenAuthInterceptor implements IClientInterceptor {

	private String myToken;

	public BearerTokenAuthInterceptor(String theToken) {
		Validate.notNull("theToken must not be null");
		myToken = theToken;
	}

	@Override
	public void interceptRequest(HttpRequestBase theRequest) {
		theRequest.addHeader(Constants.HEADER_AUTHORIZATION, (Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER + myToken));
	}

	@Override
	public void interceptResponse(HttpResponse theResponse) throws IOException {
		// nothing
	}

}
