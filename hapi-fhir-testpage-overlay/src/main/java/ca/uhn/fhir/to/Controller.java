package ca.uhn.fhir.to;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.HttpEntityWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.thymeleaf.TemplateEngine;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.dstu.resource.Conformance;
import ca.uhn.fhir.model.dstu.resource.Conformance.Rest;
import ca.uhn.fhir.model.dstu.resource.Conformance.RestQuery;
import ca.uhn.fhir.model.dstu.resource.Conformance.RestResource;
import ca.uhn.fhir.model.dstu.resource.Conformance.RestResourceSearchParam;
import ca.uhn.fhir.model.dstu.valueset.SearchParamTypeEnum;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.model.primitive.DecimalDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.client.GenericClient;
import ca.uhn.fhir.rest.client.IClientInterceptor;
import ca.uhn.fhir.rest.client.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.Constants;
import ca.uhn.fhir.rest.server.EncodingEnum;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.to.model.HomeRequest;
import ca.uhn.fhir.to.model.ResourceRequest;
import ca.uhn.fhir.to.model.TransactionRequest;
import ca.uhn.fhir.util.ExtensionConstants;

@org.springframework.stereotype.Controller()
public class Controller {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(Controller.class);
	private static final String PARAM_RESOURCE = "resource";
	private static final String RESOURCE_COUNT_EXT_URL = "http://hl7api.sourceforge.net/hapi-fhir/res/extdefs.html#resourceCount";

	@Autowired
	private TesterConfig myConfig;

	@Autowired
	private FhirContext myCtx;
	private List<String> myFilterHeaders;

	@Autowired
	private TemplateEngine myTemplateEngine;
	
	private Conformance initConformance;

	@RequestMapping(value = { "/about" })
	public String actionAbout(final HomeRequest theRequest, final ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		theModel.put("notHome", true);
		theModel.put("extraBreadcrumb", "About");

		ourLog.info(logPrefix(theModel) + "Displayed about page");

		return "about";
	}

	@RequestMapping(value = { "/conformance" })
	public String actionConformance(final HomeRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);
		ResultType returnsResource = ResultType.RESOURCE;

		long start = System.currentTimeMillis();
		try {
			client.conformance();
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, "Loaded conformance", interceptor);

		ourLog.info(logPrefix(theModel) + "Displayed conformance profile");

		return "result";
	}

	@RequestMapping(value = { "/create" })
	public String actionCreate(final HttpServletRequest theReq, final HomeRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		doActionCreateOrValidate(theReq, theRequest, theBindingResult, theModel, "create");
		return "result";
	}

	@RequestMapping(value = { "/delete" })
	public String actionDelete(HttpServletRequest theReq, HomeRequest theRequest, BindingResult theBindingResult, ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		RuntimeResourceDefinition def;
		try {
			def = getResourceType(theReq);
		} catch (ServletException e) {
			theModel.put("errorMsg", e.toString());
			return "resource";
		}

		String id = StringUtils.defaultString(theReq.getParameter("resource-delete-id"));
		if (StringUtils.isBlank(id)) {
			theModel.put("errorMsg", "No ID specified");
			return "resource";
		}

		ResultType returnsResource = ResultType.BUNDLE;
		String outcomeDescription = "Delete Resource";

		long start = System.currentTimeMillis();
		try {
			client.delete(def.getImplementingClass(), new IdDt(id));
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;
		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, outcomeDescription, interceptor);

		ourLog.info(logPrefix(theModel) + "Deleted resource of type " + def.getName());

		return "result";
	}

	@RequestMapping(value = { "/get-tags" })
	public String actionGetTags(HttpServletRequest theReq, HomeRequest theRequest, BindingResult theBindingResult, ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		Class<? extends IResource> resType = null;
		ResultType returnsResource = ResultType.TAGLIST;
		String outcomeDescription = "Tag List";

		long start = System.currentTimeMillis();
		try {
			if (isNotBlank(theReq.getParameter(PARAM_RESOURCE))) {
				RuntimeResourceDefinition def;
				try {
					def = getResourceType(theReq);
				} catch (ServletException e) {
					theModel.put("errorMsg", e.toString());
					return "resource";
				}

				resType = def.getImplementingClass();
				String id = theReq.getParameter("resource-tags-id");
				if (isNotBlank(id)) {
					String vid = theReq.getParameter("resource-tags-vid");
					if (isNotBlank(vid)) {
						client.getTags().forResource(resType, id, vid).execute();
						ourLog.info(logPrefix(theModel) + "Got tags for type " + def.getName() + " ID " + id + " version" + vid);
					} else {
						client.getTags().forResource(resType, id).execute();
						ourLog.info(logPrefix(theModel) + "Got tags for type " + def.getName() + " ID " + id);
					}
				} else {
					client.getTags().forResource(resType).execute();
					ourLog.info(logPrefix(theModel) + "Got tags for type " + def.getName());
				}
			} else {
				client.getTags().execute();
				ourLog.info(logPrefix(theModel) + "Got tags for server");
			}
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, outcomeDescription, interceptor);

		return "result";
	}

	@RequestMapping(value = { "/history-server" })
	public String actionHistoryServer(final HttpServletRequest theReq, final HomeRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		doActionHistory(theReq, theRequest, theBindingResult, theModel, "history-server", "Server History");
		return "result";
	}

	@RequestMapping(value = { "/history-type" })
	public String actionHistoryType(final HttpServletRequest theReq, final HomeRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		doActionHistory(theReq, theRequest, theBindingResult, theModel, "history-type", "History");
		return "result";
	}

	@RequestMapping(value = { "/", "/home" })
	public String actionHome(final HomeRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		addCommonParams(theRequest, theModel);
		return "home";
	}

	@RequestMapping(value = { "/page" })
	public String actionPage(HttpServletRequest theReq, HomeRequest theRequest, BindingResult theBindingResult, ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		String url = defaultString(theReq.getParameter("page-url"));
		if (!url.startsWith(theModel.get("base").toString())) {
			ourLog.warn(logPrefix(theModel) + "Refusing to load page URL: {}", url);
			theModel.put("errorMsg", "Invalid page URL: " + url);
			return "result";
		}

		url = url.replace("&amp;", "&");

		ResultType returnsResource = ResultType.BUNDLE;

		long start = System.currentTimeMillis();
		try {
			ourLog.info(logPrefix(theModel) + "Loading paging URL: {}", url);
			client.loadPage().url(url).execute();
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		String outcomeDescription = "Bundle Page";

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, outcomeDescription, interceptor);

		return "result";
	}

	@RequestMapping(value = { "/read" })
	public String actionRead(HttpServletRequest theReq, HomeRequest theRequest, BindingResult theBindingResult, ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		RuntimeResourceDefinition def;
		try {
			def = getResourceType(theReq);
		} catch (ServletException e) {
			theModel.put("errorMsg", e.toString());
			return "resource";
		}
		String id = StringUtils.defaultString(theReq.getParameter("id"));
		if (StringUtils.isBlank(id)) {
			theModel.put("errorMsg", "No ID specified");
			return "resource";
		}
		ResultType returnsResource = ResultType.RESOURCE;

		String versionId = StringUtils.defaultString(theReq.getParameter("vid"));
		String outcomeDescription;
		if (StringUtils.isBlank(versionId)) {
			versionId = null;
			outcomeDescription = "Read Resource";
		} else {
			outcomeDescription = "VRead Resource";
		}

		long start = System.currentTimeMillis();
		try {
			IdDt resid = new IdDt(def.getName(), id, versionId);
			ourLog.info(logPrefix(theModel) + "Reading resource: {}", resid);
			client.read(def.getImplementingClass(), resid);
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, outcomeDescription, interceptor);

		return "result";
	}

	@RequestMapping({ "/resource" })
	public String actionResource(final ResourceRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		Conformance conformance = addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		String resourceName = theRequest.getResource();
		RuntimeResourceDefinition def = myCtx.getResourceDefinition(theRequest.getResource());

		TreeSet<String> includes = new TreeSet<String>();
		TreeSet<String> sortParams = new TreeSet<String>();
		List<RestQuery> queries = new ArrayList<Conformance.RestQuery>();
		boolean haveSearchParams = false;
		List<List<String>> queryIncludes = new ArrayList<List<String>>();
		for (Rest nextRest : conformance.getRest()) {
			for (RestResource nextRes : nextRest.getResource()) {
				if (nextRes.getType().getValue().equals(resourceName)) {
					for (StringDt next : nextRes.getSearchInclude()) {
						if (next.isEmpty() == false) {
							includes.add(next.getValue());
						}
					}
					for (RestResourceSearchParam next : nextRes.getSearchParam()) {
						if (next.getType().getValueAsEnum() != SearchParamTypeEnum.COMPOSITE) {
							sortParams.add(next.getName().getValue());
						}
					}
					if (nextRes.getSearchParam().size() > 0) {
						haveSearchParams = true;
					}
				}
			}
			for (RestQuery nextQuery : nextRest.getQuery()) {
				boolean queryMatchesResource = false;
				List<ExtensionDt> returnTypeExt = nextQuery.getUndeclaredExtensionsByUrl(ExtensionConstants.QUERY_RETURN_TYPE);
				if (returnTypeExt != null) {
					for (ExtensionDt nextExt : returnTypeExt) {
						if (resourceName.equals(nextExt.getValueAsPrimitive().getValueAsString())) {
							queries.add(nextQuery);
							queryMatchesResource = true;
							break;
						}
					}
				}

				if (queryMatchesResource) {
					ArrayList<String> nextQueryIncludes = new ArrayList<String>();
					queryIncludes.add(nextQueryIncludes);
					List<ExtensionDt> includesExt = nextQuery.getUndeclaredExtensionsByUrl(ExtensionConstants.QUERY_ALLOWED_INCLUDE);
					if (includesExt != null) {
						for (ExtensionDt nextExt : includesExt) {
							nextQueryIncludes.add(nextExt.getValueAsPrimitive().getValueAsString());
						}
					}
				}
			}
		}
		theModel.put("includes", includes);
		theModel.put("queries", queries);
		theModel.put("haveSearchParams", haveSearchParams);
		theModel.put("queryIncludes", queryIncludes);
		theModel.put("sortParams", sortParams);

		if (isNotBlank(theRequest.getUpdateId())) {
			String updateId = theRequest.getUpdateId();
			String updateVid = defaultIfEmpty(theRequest.getUpdateVid(), null);
			IResource updateResource = client.read(def.getImplementingClass(), new IdDt(resourceName, updateId, updateVid));
			String updateResourceString = theRequest.newParser(myCtx).setPrettyPrint(true).encodeResourceToString(updateResource);
			theModel.put("updateResource", updateResourceString);
			theModel.put("updateResourceId", updateId);
		}

		ourLog.info(logPrefix(theModel) + "Showing resource page: {}", resourceName);

		return "resource";
	}

	public static class CaptureInterceptor implements IClientInterceptor {

		private HttpRequestBase myLastRequest;
		private HttpResponse myLastResponse;
		private String myResponseBody;

		@Override
		public void interceptRequest(HttpRequestBase theRequest) {
			assert myLastRequest == null;
			myLastRequest = theRequest;
		}

		@Override
		public void interceptResponse(HttpResponse theResponse) throws IOException {
			assert myLastResponse == null;
			myLastResponse = theResponse;

			HttpEntity respEntity = theResponse.getEntity();
			if (respEntity != null) {
				final byte[] bytes;
				try {
					bytes = IOUtils.toByteArray(respEntity.getContent());
				} catch (IllegalStateException e) {
					throw new InternalErrorException(e);
				}

				myResponseBody = new String(bytes, "UTF-8");
				theResponse.setEntity(new MyEntityWrapper(respEntity, bytes));
			}
		}

		public HttpRequestBase getLastRequest() {
			return myLastRequest;
		}

		public HttpResponse getLastResponse() {
			return myLastResponse;
		}

		private static class MyEntityWrapper extends HttpEntityWrapper {

			private byte[] myBytes;

			public MyEntityWrapper(HttpEntity theWrappedEntity, byte[] theBytes) {
				super(theWrappedEntity);
				myBytes = theBytes;
			}

			@Override
			public InputStream getContent() throws IOException {
				return new ByteArrayInputStream(myBytes);
			}

			@Override
			public void writeTo(OutputStream theOutstream) throws IOException {
				theOutstream.write(myBytes);
			}

		}

		public String getLastResponseBody() {
			return myResponseBody;
		}

	}

	@RequestMapping(value = { "/search" })
	public String actionSearch(HttpServletRequest theReq, HomeRequest theRequest, BindingResult theBindingResult, ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		StringWriter clientCodeJsonStringWriter = new StringWriter();
		JsonGenerator clientCodeJsonWriter = Json.createGenerator(clientCodeJsonStringWriter);
		clientCodeJsonWriter.writeStartObject();
		clientCodeJsonWriter.write("action", "search");
		clientCodeJsonWriter.write("base", (String) theModel.get("base"));

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		IUntypedQuery search = client.search();
		IQuery query;
		if (isNotBlank(theReq.getParameter("resource"))) {
			try {
				query = search.forResource(getResourceType(theReq).getImplementingClass());
			} catch (ServletException e) {
				theModel.put("errorMsg", e.toString());
				return "resource";
			}
			clientCodeJsonWriter.write("resource", theReq.getParameter("resource"));
		} else {
			query = search.forAllResources();
			clientCodeJsonWriter.writeNull("resource");
		}

		if (client.getPrettyPrint() != null) {
			clientCodeJsonWriter.write("pretty", client.getPrettyPrint().toString());
		} else {
			clientCodeJsonWriter.writeNull("pretty");
		}

		if (client.getEncoding() != null) {
			clientCodeJsonWriter.write("format", client.getEncoding().getRequestContentType());
		} else {
			clientCodeJsonWriter.writeNull("format");
		}

		String outcomeDescription = "Search for Resources";

		clientCodeJsonWriter.writeStartArray("params");
		int paramIdx = -1;
		while (true) {
			paramIdx++;

			String paramIdxString = Integer.toString(paramIdx);
			boolean shouldContinue = handleSearchParam(paramIdxString, theReq, query, clientCodeJsonWriter);
			if (!shouldContinue) {
				break;
			}
		}
		clientCodeJsonWriter.writeEnd();

		clientCodeJsonWriter.writeStartArray("includes");
		String[] incValues = theReq.getParameterValues(Constants.PARAM_INCLUDE);
		if (incValues != null) {
			for (String next : incValues) {
				if (isNotBlank(next)) {
					query.include(new Include(next));
					clientCodeJsonWriter.write(next);
				}
			}
		}
		clientCodeJsonWriter.writeEnd();

		String limit = theReq.getParameter("resource-search-limit");
		if (isNotBlank(limit)) {
			if (!limit.matches("[0-9]+")) {
				theModel.put("errorMsg", "Search limit must be a numeric value.");
				return "resource";
			}
			int limitInt = Integer.parseInt(limit);
			query.limitTo(limitInt);
			clientCodeJsonWriter.write("limit", limit);
		} else {
			clientCodeJsonWriter.writeNull("limit");
		}

		long start = System.currentTimeMillis();
		ResultType returnsResource;
		try {
			ourLog.info(logPrefix(theModel) + "Executing a search");

			query.execute();
			returnsResource = ResultType.BUNDLE;
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, outcomeDescription, interceptor);

		clientCodeJsonWriter.writeEnd();
		clientCodeJsonWriter.close();
		String clientCodeJson = clientCodeJsonStringWriter.toString();
		theModel.put("clientCodeJson", clientCodeJson);

		return "result";
	}

	@RequestMapping(value = { "/transaction" })
	public String actionTransaction(final TransactionRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		String body = preProcessMessageBody(theRequest.getTransactionBody());

		Bundle bundle;
		try {
			if (body.startsWith("{")) {
				bundle = myCtx.newJsonParser().parseBundle(body);
			} else if (body.startsWith("<")) {
				bundle = myCtx.newXmlParser().parseBundle(body);
			} else {
				theModel.put("errorMsg", "Message body does not appear to be a valid FHIR resource instance document. Body should start with '<' (for XML encoding) or '{' (for JSON encoding).");
				return "home";
			}
		} catch (DataFormatException e) {
			ourLog.warn("Failed to parse bundle", e);
			theModel.put("errorMsg", "Failed to parse transaction bundle body. Error was: " + e.getMessage());
			return "home";
		}

		ResultType returnsResource = ResultType.BUNDLE;
		long start = System.currentTimeMillis();
		try {
			ourLog.info(logPrefix(theModel) + "Executing transaction with {} resources", bundle.size());
			client.transaction().withBundle(bundle).execute();
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, "Transaction", interceptor);

		return "result";
	}

	@RequestMapping(value = { "/update" })
	public String actionUpdate(final HttpServletRequest theReq, final HomeRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		doActionCreateOrValidate(theReq, theRequest, theBindingResult, theModel, "update");
		return "result";
	}

	@RequestMapping(value = { "/validate" })
	public String actionValidate(final HttpServletRequest theReq, final HomeRequest theRequest, final BindingResult theBindingResult, final ModelMap theModel) {
		doActionCreateOrValidate(theReq, theRequest, theBindingResult, theModel, "validate");
		return "result";
	}

	private Conformance addCommonParams(final HomeRequest theRequest, final ModelMap theModel) {
		if (myConfig.getDebugTemplatesMode()) {
			myTemplateEngine.getCacheManager().clearAllCaches();
		}

		final String serverId = theRequest.getServerIdWithDefault(myConfig);
		final String serverBase = theRequest.getServerBase(myConfig);
		final String serverName = theRequest.getServerName(myConfig);
		theModel.put("serverId", serverId);
		theModel.put("base", serverBase);
		theModel.put("baseName", serverName);
		theModel.put("resourceName", defaultString(theRequest.getResource()));
		theModel.put("encoding", theRequest.getEncoding());
		theModel.put("pretty", theRequest.getPretty());
		theModel.put("serverEntries", myConfig.getIdToServerName());
		/*if (initConformance != null) {
		    System.out.println("initConformance exist");
		    return initConformance;
		} else {
		    initConformance = loadAndAddConf(theRequest, theModel);
		    System.out.println("initConformance new");
		    return initConformance;
		}*/
		return loadAndAddConf(theRequest, theModel);
	}

	private Header[] applyHeaderFilters(Header[] theAllHeaders) {
		if (myFilterHeaders == null || myFilterHeaders.isEmpty()) {
			return theAllHeaders;
		}
		ArrayList<Header> retVal = new ArrayList<Header>();
		for (Header next : theAllHeaders) {
			if (!myFilterHeaders.contains(next.getName().toLowerCase())) {
				retVal.add(next);
			}
		}
		return retVal.toArray(new Header[retVal.size()]);
	}

	private void doActionCreateOrValidate(HttpServletRequest theReq, HomeRequest theRequest, BindingResult theBindingResult, ModelMap theModel, String theMethod) {
		boolean validate = "validate".equals(theMethod);

		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		Class<? extends IResource> type = null; // def.getImplementingClass();
		if ("history-type".equals(theMethod)) {
			RuntimeResourceDefinition def = myCtx.getResourceDefinition(theRequest.getResource());
			type = def.getImplementingClass();
		}

		String body = validate ? theReq.getParameter("resource-validate-body") : theReq.getParameter("resource-create-body");
		if (isBlank(body)) {
			theModel.put("errorMsg", "No message body specified");
			return;
		}

		body = preProcessMessageBody(body);

		IResource resource;
		try {
			if (body.startsWith("{")) {
				resource = myCtx.newJsonParser().parseResource(type, body);
			} else if (body.startsWith("<")) {
				resource = myCtx.newXmlParser().parseResource(type, body);
			} else {
				theModel.put("errorMsg", "Message body does not appear to be a valid FHIR resource instance document. Body should start with '<' (for XML encoding) or '{' (for JSON encoding).");
				return;
			}
		} catch (DataFormatException e) {
			ourLog.warn("Failed to parse resource", e);
			theModel.put("errorMsg", "Failed to parse message body. Error was: " + e.getMessage());
			return;
		}

		String outcomeDescription;

		long start = System.currentTimeMillis();
		ResultType returnsResource = ResultType.RESOURCE;
		outcomeDescription = "";
		boolean update = false;
		try {
			if (validate) {
				outcomeDescription = "Validate Resource";
				client.validate(resource);
			} else {
				String id = theReq.getParameter("resource-create-id");
				if ("update".equals(theMethod)) {
					outcomeDescription = "Update Resource";
					client.update(id, resource);
					update = true;
				} else {
					outcomeDescription = "Create Resource";
					ICreateTyped create = client.create().resource(body);
					if (isNotBlank(id)) {
						create.withId(id);
					}
					create.execute();
				}
			}
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, outcomeDescription, interceptor);

		try {
			if (validate) {
				ourLog.info(logPrefix(theModel) + "Validated resource of type " + getResourceType(theReq).getName());
			} else if (update) {
				ourLog.info(logPrefix(theModel) + "Updated resource of type " + getResourceType(theReq).getName());
			} else {
				ourLog.info(logPrefix(theModel) + "Created resource of type " + getResourceType(theReq).getName());
			}
		} catch (Exception e) {
			ourLog.warn("Failed to determine resource type from request", e);
		}

	}

	private void doActionHistory(HttpServletRequest theReq, HomeRequest theRequest, BindingResult theBindingResult, ModelMap theModel, String theMethod, String theMethodDescription) {
		addCommonParams(theRequest, theModel);

		CaptureInterceptor interceptor = new CaptureInterceptor();
		GenericClient client = theRequest.newClient(myCtx, myConfig, interceptor);

		String id = null;
		Class<? extends IResource> type = null; // def.getImplementingClass();
		if ("history-type".equals(theMethod)) {
			RuntimeResourceDefinition def = myCtx.getResourceDefinition(theRequest.getResource());
			type = def.getImplementingClass();
			id = StringUtils.defaultString(theReq.getParameter("resource-history-id"));
		}

		DateTimeDt since = null;
		String sinceStr = theReq.getParameter("since");
		if (isNotBlank(sinceStr)) {
			since = new DateTimeDt(sinceStr);
		}

		Integer limit = null;
		String limitStr = theReq.getParameter("limit");
		if (isNotBlank(limitStr)) {
			limit = Integer.parseInt(limitStr);
		}

		ResultType returnsResource = ResultType.BUNDLE;

		long start = System.currentTimeMillis();
		try {
			ourLog.info(logPrefix(theModel) + "Retrieving history for type {} ID {} since {}", new Object[] { type, id, since });
			client.history(type, id, since, limit);
		} catch (Exception e) {
			returnsResource = handleClientException(client, e, theModel);
		}
		long delay = System.currentTimeMillis() - start;

		processAndAddLastClientInvocation(client, returnsResource, theModel, delay, theMethodDescription, interceptor);

	}

	private String format(String theResultBody, EncodingEnum theEncodingEnum) {
		String str = StringEscapeUtils.escapeHtml4(theResultBody);
		if (str == null || theEncodingEnum == null) {
			return str;
		}

		StringBuilder b = new StringBuilder();

		if (theEncodingEnum == EncodingEnum.JSON) {

			boolean inValue = false;
			boolean inQuote = false;
			for (int i = 0; i < str.length(); i++) {
				char prevChar = (i > 0) ? str.charAt(i - 1) : ' ';
				char nextChar = str.charAt(i);
				char nextChar2 = (i + 1) < str.length() ? str.charAt(i + 1) : ' ';
				char nextChar3 = (i + 2) < str.length() ? str.charAt(i + 2) : ' ';
				char nextChar4 = (i + 3) < str.length() ? str.charAt(i + 3) : ' ';
				char nextChar5 = (i + 4) < str.length() ? str.charAt(i + 4) : ' ';
				char nextChar6 = (i + 5) < str.length() ? str.charAt(i + 5) : ' ';
				if (inQuote) {
					b.append(nextChar);
					if (prevChar != '\\' && nextChar == '&' && nextChar2 == 'q' && nextChar3 == 'u' && nextChar4 == 'o' && nextChar5 == 't' && nextChar6 == ';') {
						b.append("quot;</span>");
						i += 5;
						inQuote = false;
					} else if (nextChar == '\\' && nextChar2 == '"') {
						b.append("quot;</span>");
						i += 5;
						inQuote = false;
					}
				} else {
					if (nextChar == ':') {
						inValue = true;
						b.append(nextChar);
					} else if (nextChar == '[' || nextChar == '{') {
						b.append("<span class='hlControl'>");
						b.append(nextChar);
						b.append("</span>");
						inValue = false;
					} else if (nextChar == '}' || nextChar == '}' || nextChar == ',') {
						b.append("<span class='hlControl'>");
						b.append(nextChar);
						b.append("</span>");
						inValue = false;
					} else if (nextChar == '&' && nextChar2 == 'q' && nextChar3 == 'u' && nextChar4 == 'o' && nextChar5 == 't' && nextChar6 == ';') {
						if (inValue) {
							b.append("<span class='hlQuot'>&quot;");
						} else {
							b.append("<span class='hlTagName'>&quot;");
						}
						inQuote = true;
						i += 5;
					} else if (nextChar == ':') {
						b.append("<span class='hlControl'>");
						b.append(nextChar);
						b.append("</span>");
						inValue = true;
					} else {
						b.append(nextChar);
					}
				}
			}

		} else {
			boolean inQuote = false;
			boolean inTag = false;
			for (int i = 0; i < str.length(); i++) {
				char nextChar = str.charAt(i);
				char nextChar2 = (i + 1) < str.length() ? str.charAt(i + 1) : ' ';
				char nextChar3 = (i + 2) < str.length() ? str.charAt(i + 2) : ' ';
				char nextChar4 = (i + 3) < str.length() ? str.charAt(i + 3) : ' ';
				char nextChar5 = (i + 4) < str.length() ? str.charAt(i + 4) : ' ';
				char nextChar6 = (i + 5) < str.length() ? str.charAt(i + 5) : ' ';
				if (inQuote) {
					b.append(nextChar);
					if (nextChar == '&' && nextChar2 == 'q' && nextChar3 == 'u' && nextChar4 == 'o' && nextChar5 == 't' && nextChar6 == ';') {
						b.append("quot;</span>");
						i += 5;
						inQuote = false;
					}
				} else if (inTag) {
					if (nextChar == '&' && nextChar2 == 'g' && nextChar3 == 't' && nextChar4 == ';') {
						b.append("</span><span class='hlControl'>&gt;</span>");
						inTag = false;
						i += 3;
					} else if (nextChar == ' ') {
						b.append("</span><span class='hlAttr'>");
						b.append(nextChar);
					} else if (nextChar == '&' && nextChar2 == 'q' && nextChar3 == 'u' && nextChar4 == 'o' && nextChar5 == 't' && nextChar6 == ';') {
						b.append("<span class='hlQuot'>&quot;");
						inQuote = true;
						i += 5;
					} else {
						b.append(nextChar);
					}
				} else {
					if (nextChar == '&' && nextChar2 == 'l' && nextChar3 == 't' && nextChar4 == ';') {
						b.append("<span class='hlControl'>&lt;</span><span class='hlTagName'>");
						inTag = true;
						i += 3;
					} else {
						b.append(nextChar);
					}
				}
			}
		}

		return b.toString();
	}

	private String formatUrl(String theUrlBase, String theResultBody) {
		String str = theResultBody;
		if (str == null) {
			return str;
		}

		try {
			str = URLDecoder.decode(str, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			ourLog.error("Should not happen", e);
		}

		StringBuilder b = new StringBuilder();
		b.append("<span class='hlUrlBase'>");

		boolean inParams = false;
		for (int i = 0; i < str.length(); i++) {
			char nextChar = str.charAt(i);
			// char nextChar2 = i < str.length()-2 ? str.charAt(i+1):' ';
			// char nextChar3 = i < str.length()-2 ? str.charAt(i+2):' ';
			if (!inParams) {
				if (nextChar == '?') {
					inParams = true;
					b.append("</span><wbr /><span class='hlControl'>?</span><span class='hlTagName'>");
				} else {
					if (i == theUrlBase.length()) {
						b.append("</span><wbr /><span class='hlText'>");
					}
					b.append(nextChar);
				}
			} else {
				if (nextChar == '&') {
					b.append("</span><wbr /><span class='hlControl'>&amp;</span><span class='hlTagName'>");
				} else if (nextChar == '=') {
					b.append("</span><span class='hlControl'>=</span><span class='hlAttr'>");
					// }else if (nextChar=='%' && Character.isLetterOrDigit(nextChar2)&& Character.isLetterOrDigit(nextChar3)) {
					// URLDecoder.decode(s, enc)
				} else {
					b.append(nextChar);
				}
			}
		}

		if (inParams) {
			b.append("</span>");
		}
		return b.toString();
	}

	private RuntimeResourceDefinition getResourceType(HttpServletRequest theReq) throws ServletException {
		String resourceName = StringUtils.defaultString(theReq.getParameter(PARAM_RESOURCE));
		RuntimeResourceDefinition def = myCtx.getResourceDefinition(resourceName);
		if (def == null) {
			throw new ServletException("Invalid resourceName: " + resourceName);
		}
		return def;
	}

	private ResultType handleClientException(GenericClient theClient, Exception e, ModelMap theModel) {
		ResultType returnsResource;
		returnsResource = ResultType.NONE;
		ourLog.warn("Failed to invoke server", e);

		if (theClient.getLastResponse() == null) {
			theModel.put("errorMsg", "Error: " + e.getMessage());
		}

		return returnsResource;
	}

	private boolean handleSearchParam(String paramIdxString, HttpServletRequest theReq, IQuery theQuery, JsonGenerator theClientCodeJsonWriter) {
		String nextName = theReq.getParameter("param." + paramIdxString + ".name");
		if (isBlank(nextName)) {
			return false;
		}

		String nextQualifier = StringUtils.defaultString(theReq.getParameter("param." + paramIdxString + ".qualifier"));
		String nextType = theReq.getParameter("param." + paramIdxString + ".type");

		List<String> parts = new ArrayList<String>();
		for (int i = 0; i < 5; i++) {
			parts.add(defaultString(theReq.getParameter("param." + paramIdxString + "." + i)));
		}

		List<String> values;
		boolean addToWhere=true;
		if ("token".equals(nextType)) {
			if (isBlank(parts.get(2))) {
				return true;
			}
			values = Collections.singletonList(StringUtils.join(parts, ""));
			addToWhere=false;
			theQuery.where(new TokenClientParam(nextName + nextQualifier).exactly().systemAndCode(parts.get(0), parts.get(2)));
		} else if ("date".equals(nextType)) {
			values = new ArrayList<String>();
			if (isNotBlank(parts.get(1))) {
				values.add(StringUtils.join(parts.get(0), parts.get(1)));
			}
			if (isNotBlank(parts.get(3))) {
				values.add(StringUtils.join(parts.get(2), parts.get(3)));
			}
			if (values.isEmpty()) {
				return true;
			}
		} else {
			values = Collections.singletonList(StringUtils.join(parts, ""));
			if (isBlank(values.get(0))) {
				return true;
			}
		}

		for (String nextValue : values) {

			theClientCodeJsonWriter.writeStartObject();
			theClientCodeJsonWriter.write("type", nextType);
			theClientCodeJsonWriter.write("name", nextName);
			theClientCodeJsonWriter.write("qualifier", nextQualifier);
			theClientCodeJsonWriter.write("value", nextValue);
			theClientCodeJsonWriter.writeEnd();
			if (addToWhere) {
			theQuery.where(new StringClientParam(nextName + nextQualifier).matches().value(nextValue));
			}

		}

		if (StringUtils.isNotBlank(theReq.getParameter("param." + paramIdxString + ".0.name"))) {
			handleSearchParam(paramIdxString + ".0", theReq, theQuery , theClientCodeJsonWriter);
		}

		return true;
	}

	private Conformance loadAndAddConf(final HomeRequest theRequest, final ModelMap theModel) {
	    myCtx.getRestfulClientFactory().setConnectionRequestTimeout(120000);
	    myCtx.getRestfulClientFactory().setConnectTimeout(120000);
	    myCtx.getRestfulClientFactory().setSocketTimeout(120000);
	    IGenericClient client = myCtx.newRestfulGenericClient(theRequest.getServerBase(myConfig));

		Conformance conformance;
		try {
			conformance = (Conformance)client.conformance();
		} catch (Exception e) {
			ourLog.warn("Failed to load conformance statement", e);
			theModel.put("errorMsg", "Failed to load conformance statement, error was: " + e.toString());
			conformance = new Conformance();
		}
		myCtx.getRestfulClientFactory().setConnectionRequestTimeout(10000);
        myCtx.getRestfulClientFactory().setConnectTimeout(10000);
        myCtx.getRestfulClientFactory().setSocketTimeout(10000);
        
		theModel.put("jsonEncodedConf", myCtx.newJsonParser().encodeResourceToString(conformance));

		Map<String, Number> resourceCounts = new HashMap<String, Number>();
		long total = 0;
		for (Rest nextRest : conformance.getRest()) {
			for (RestResource nextResource : nextRest.getResource()) {
				List<ExtensionDt> exts = nextResource.getUndeclaredExtensionsByUrl(RESOURCE_COUNT_EXT_URL);
				if (exts != null && exts.size() > 0) {
					Number nextCount = ((DecimalDt) (exts.get(0).getValue())).getValueAsNumber();
					resourceCounts.put(nextResource.getType().getValue(), nextCount);
					total += nextCount.longValue();
				}
			}
		}
		theModel.put("resourceCounts", resourceCounts);

		if (total > 0) {
			for (Rest nextRest : conformance.getRest()) {
				Collections.sort(nextRest.getResource(), new Comparator<RestResource>() {
					@Override
					public int compare(RestResource theO1, RestResource theO2) {
						DecimalDt count1 = new DecimalDt();
						List<ExtensionDt> count1exts = theO1.getUndeclaredExtensionsByUrl(RESOURCE_COUNT_EXT_URL);
						if (count1exts != null && count1exts.size() > 0) {
							count1 = (DecimalDt) count1exts.get(0).getValue();
						}
						DecimalDt count2 = new DecimalDt();
						List<ExtensionDt> count2exts = theO2.getUndeclaredExtensionsByUrl(RESOURCE_COUNT_EXT_URL);
						if (count2exts != null && count2exts.size() > 0) {
							count2 = (DecimalDt) count2exts.get(0).getValue();
						}
						int retVal = count2.compareTo(count1);
						if (retVal == 0) {
							retVal = theO1.getType().getValue().compareTo(theO2.getType().getValue());
						}
						return retVal;
					}
				});
			}
		}

		theModel.put("conf", conformance);
		theModel.put("requiredParamExtension", ExtensionConstants.PARAM_IS_REQUIRED);

		return conformance;
	}

	private String logPrefix(ModelMap theModel) {
		return "[server=" + theModel.get("serverId") + "] - ";
	}

	private String parseNarrative(EncodingEnum theCtEnum, String theResultBody) {
		try {
			IResource resource = theCtEnum.newParser(myCtx).parseResource(theResultBody);
			String retVal = resource.getText().getDiv().getValueAsString();
			return StringUtils.defaultString(retVal);
		} catch (Exception e) {
			ourLog.error("Failed to parse resource", e);
			return "";
		}
	}

	private String preProcessMessageBody(String theBody) {
		if (theBody == null) {
			return "";
		}
		String retVal = theBody.trim();

		StringBuilder b = new StringBuilder();
		for (int i = 0; i < retVal.length(); i++) {
			char nextChar = retVal.charAt(i);
			int nextCharI = nextChar;
			if (nextCharI == 65533) {
				b.append(' ');
				continue;
			}
			if (nextCharI == 160) {
				b.append(' ');
				continue;
			}
			if (nextCharI == 194) {
				b.append(' ');
				continue;
			}
			b.append(nextChar);
		}
		retVal = b.toString();
		return retVal;
	}

	private void processAndAddLastClientInvocation(GenericClient theClient, ResultType theResultType, ModelMap theModelMap, long theLatency, String outcomeDescription,
			CaptureInterceptor theInterceptor) {
		try {
			HttpRequestBase lastRequest = theInterceptor.getLastRequest();
			HttpResponse lastResponse = theInterceptor.getLastResponse();
			String requestBody = null;
			String requestUrl = lastRequest != null ? lastRequest.getURI().toASCIIString() : null;
			String action = lastRequest != null ? lastRequest.getMethod() : null;
			String resultStatus = lastResponse != null ? lastResponse.getStatusLine().toString() : null;
			String resultBody = StringUtils.defaultString(theInterceptor.getLastResponseBody());

			if (lastRequest instanceof HttpEntityEnclosingRequest) {
				HttpEntity entity = ((HttpEntityEnclosingRequest) lastRequest).getEntity();
				if (entity.isRepeatable()) {
					requestBody = IOUtils.toString(entity.getContent());
				}
			}

			ContentType ct = lastResponse != null ? ContentType.get(lastResponse.getEntity()) : null;
			String mimeType = ct != null ? ct.getMimeType() : null;
			EncodingEnum ctEnum = EncodingEnum.forContentType(mimeType);
			String narrativeString = "";

			StringBuilder resultDescription = new StringBuilder();
			Bundle bundle = null;

			if (ctEnum == null) {
				resultDescription.append("Non-FHIR response");
			} else {
				switch (ctEnum) {
				case JSON:
					if (theResultType == ResultType.RESOURCE) {
						narrativeString = parseNarrative(ctEnum, resultBody);
						resultDescription.append("JSON resource");
					} else if (theResultType == ResultType.BUNDLE) {
						resultDescription.append("JSON bundle");
						bundle = myCtx.newJsonParser().parseBundle(resultBody);
					}
					break;
				case XML:
				default:
					if (theResultType == ResultType.RESOURCE) {
						narrativeString = parseNarrative(ctEnum, resultBody);
						resultDescription.append("XML resource");
					} else if (theResultType == ResultType.BUNDLE) {
						resultDescription.append("XML bundle");
						bundle = myCtx.newXmlParser().parseBundle(resultBody);
					}
					break;
				}
			}

			resultDescription.append(" (").append(resultBody.length() + " bytes)");

			Header[] requestHeaders = lastRequest != null ? applyHeaderFilters(lastRequest.getAllHeaders()) : new Header[0];
			Header[] responseHeaders = lastResponse != null ? applyHeaderFilters(lastResponse.getAllHeaders()) : new Header[0];

			theModelMap.put("outcomeDescription", outcomeDescription);
			theModelMap.put("resultDescription", resultDescription.toString());
			theModelMap.put("action", action);
			theModelMap.put("bundle", bundle);
			theModelMap.put("resultStatus", resultStatus);

			theModelMap.put("requestUrl", requestUrl);
			theModelMap.put("requestUrlText", formatUrl(theClient.getUrlBase(), requestUrl));

			String requestBodyText = format(requestBody, ctEnum);
			theModelMap.put("requestBody", requestBodyText);

			String resultBodyText = format(resultBody, ctEnum);
			theModelMap.put("resultBody", resultBodyText);

			theModelMap.put("resultBodyIsLong", resultBodyText.length() > 1000);
			theModelMap.put("requestHeaders", requestHeaders);
			theModelMap.put("responseHeaders", responseHeaders);
			theModelMap.put("narrative", narrativeString);
			theModelMap.put("latencyMs", theLatency);

		} catch (Exception e) {
			ourLog.error("Failure during processing", e);
			theModelMap.put("errorMsg", "Error during processing: " + e.getMessage());
		}

	}

	private enum ResultType {
		BUNDLE, NONE, RESOURCE, TAGLIST
	}

}
