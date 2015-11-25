package ca.uhn.fhir.tinder.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoFailureException;

import ca.uhn.fhir.tinder.model.BaseRootType;
import ca.uhn.fhir.tinder.model.Resource;

public class ResourceGeneratorUsingSpreadsheet extends BaseStructureSpreadsheetParser {
	private String myFilenameSuffix = "";
	private List<String> myInputStreamNames;
	private ArrayList<InputStream> myInputStreams;
	private String myTemplate = null;
	private String myVersion;

	public ResourceGeneratorUsingSpreadsheet(String theVersion, String theBaseDir) {
		super(theVersion, theBaseDir);
		myVersion = theVersion;
	}

	public List<String> getInputStreamNames() {
		return myInputStreamNames;
	}

	public void setBaseResourceNames(List<String> theBaseResourceNames) throws MojoFailureException {
		myInputStreamNames = theBaseResourceNames;
		myInputStreams = new ArrayList<InputStream>();

		for (String next : theBaseResourceNames) {
			String resName = "/res/" + myVersion + "/" + next + "-spreadsheet.xml";
			InputStream nextRes = getClass().getResourceAsStream(resName);
			myInputStreams.add(nextRes);
			if (nextRes == null) {
				throw new MojoFailureException("Unknown base resource name: " + resName);
			}
		}
	}

	public void setFilenameSuffix(String theFilenameSuffix) {
		myFilenameSuffix = theFilenameSuffix;
	}

	public void setTemplate(String theTemplate) {
		myTemplate = theTemplate;
	}

	@Override
	protected BaseRootType createRootType() {
		return new Resource();
	}

	@Override
	protected String getFilenameSuffix() {
		return myFilenameSuffix;
	}

	@Override
	protected Collection<InputStream> getInputStreams() {
		return myInputStreams;
	}

	@Override
	protected String getTemplate() {
		if (myTemplate != null) {
			return myTemplate;
		} else if ("dstu".equals(myVersion)) {
			return "/vm/resource_dstu.vm";
		} else {
			return "/vm/resource.vm";
		}
	}

	@Override
	protected boolean isSpreadsheet(String theFileName) {
		return theFileName.endsWith("spreadsheet.xml");
	}

}