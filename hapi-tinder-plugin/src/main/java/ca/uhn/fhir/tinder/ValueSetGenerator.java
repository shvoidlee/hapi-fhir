package ca.uhn.fhir.tinder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.BundleEntry;
import ca.uhn.fhir.model.dstu.resource.ValueSet;
import ca.uhn.fhir.model.dstu.resource.ValueSet.ComposeInclude;
import ca.uhn.fhir.model.dstu.resource.ValueSet.Define;
import ca.uhn.fhir.model.dstu.resource.ValueSet.DefineConcept;
import ca.uhn.fhir.model.primitive.CodeDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.tinder.TinderStructuresMojo.ValueSetFileDefinition;
import ca.uhn.fhir.tinder.model.ValueSetTm;
import ca.uhn.fhir.tinder.parser.ResourceGeneratorUsingSpreadsheet;

public class ValueSetGenerator {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ValueSetGenerator.class);
	private List<ValueSetFileDefinition> myResourceValueSetFiles;
	private Set<ValueSetTm> myMarkedValueSets = new HashSet<ValueSetTm>();

	private Map<String, ValueSetTm> myValueSets = new HashMap<String, ValueSetTm>();
	private int myValueSetCount;
	private int myConceptCount;
	private String myVersion;

	public ValueSetGenerator(String theVersion) {
		myVersion =theVersion;
	}

	public String getClassForValueSetIdAndMarkAsNeeded(String theId) {
		ValueSetTm vs = myValueSets.get(theId);
		if (vs == null) {
			return null;
		} else {
			myMarkedValueSets.add(vs);
			return vs.getClassName();
		}
	}

	public Map<String, ValueSetTm> getValueSets() {
		return myValueSets;
	}

	public void parse() throws FileNotFoundException, IOException {
		IParser newXmlParser = new FhirContext(ValueSet.class).newXmlParser();

		ourLog.info("Parsing built-in ValueSets");
		String vs = IOUtils.toString(ValueSetGenerator.class.getResourceAsStream("/vs/" + myVersion + "/all-valuesets-bundle.xml"));
		Bundle bundle = newXmlParser.parseBundle(vs);
		for (BundleEntry next : bundle.getEntries()) {
			ValueSet nextVs = (ValueSet) next.getResource();
			parseValueSet(nextVs);
		}

		if (myResourceValueSetFiles != null) {
			for (ValueSetFileDefinition next : myResourceValueSetFiles) {
				File file = new File(next.getValueSetFile());
				ourLog.info("Parsing ValueSet file: {}" + file.getName());
				vs = IOUtils.toString(new FileReader(file));
				ValueSet nextVs = (ValueSet) newXmlParser.parseResource(vs);
				ValueSetTm tm = parseValueSet(nextVs);

				myMarkedValueSets.add(tm);
			}
		}

		// File[] files = new
		// File(myResourceValueSetFiles).listFiles((FilenameFilter) new
		// WildcardFileFilter("*.xml"));
		// for (File file : files) {
		// ourLog.info("Parsing ValueSet file: {}" + file.getName());
		// vs = IOUtils.toString(new FileReader(file));
		// ValueSet nextVs = (ValueSet) newXmlParser.parseResource(vs);
		// parseValueSet(nextVs);
		// }

	}

	private ValueSetTm parseValueSet(ValueSet nextVs) {
		myConceptCount += nextVs.getDefine().getConcept().size();
		ourLog.info("Parsing ValueSetTm #{} - {} - {} concepts total", myValueSetCount++, nextVs.getName().getValue(), myConceptCount);
		// output.addConcept(next.getCode().getValue(),
		// next.getDisplay().getValue(), next.getDefinition());

		ValueSetTm vs = new ValueSetTm();

		vs.setName(nextVs.getName().getValue());
		vs.setDescription(nextVs.getDescription().getValue());
		vs.setId(nextVs.getIdentifier().getValue());
		vs.setClassName(toClassName(nextVs.getName().getValue()));

		{
			Define define = nextVs.getDefine();
			String system = define.getSystem().getValueAsString();
			for (DefineConcept nextConcept : define.getConcept()) {
				String nextCodeValue = nextConcept.getCode().getValue();
				String nextCodeDisplay = StringUtils.defaultString(nextConcept.getDisplay().getValue());
				String nextCodeDefinition = StringUtils.defaultString(nextConcept.getDefinition().getValue());
				vs.addConcept(system, nextCodeValue, nextCodeDisplay, nextCodeDefinition);
			}
		}

		for (ComposeInclude nextInclude : nextVs.getCompose().getInclude()) {
			String system = nextInclude.getSystem().getValueAsString();
			for (CodeDt nextConcept : nextInclude.getCode()) {
				String nextCodeValue = nextConcept.getValue();
				vs.addConcept(system, nextCodeValue, null, null);
			}
		}

		if (myValueSets.containsKey(vs.getName())) {
			ourLog.warn("Duplicate Name: " + vs.getName());
		} else {
			myValueSets.put(vs.getName(), vs);
		}

		// This is hackish, but deals with "Administrative Gender Codes" vs "AdministrativeGender"
		if (vs.getName().endsWith(" Codes")) {
			myValueSets.put(vs.getName().substring(0, vs.getName().length() - 6).replace(" ", ""), vs);
		}
		myValueSets.put(vs.getName().replace(" ", ""), vs);
		
		return vs;
	}

	public void setResourceValueSetFiles(List<ValueSetFileDefinition> theResourceValueSetFiles) {
		myResourceValueSetFiles = theResourceValueSetFiles;
	}

	public void write(Collection<ValueSetTm> theValueSets, File theOutputDirectory, String thePackageBase) throws IOException {
		for (ValueSetTm nextValueSetTm : theValueSets) {
			write(nextValueSetTm, theOutputDirectory, thePackageBase);
		}
	}

	private String toClassName(String theValue) {
		StringBuilder b = new StringBuilder();
		for (String next : theValue.split("\\s+")) {
			next = next.trim();
			if (StringUtils.isBlank(next)) {
				continue;
			}
			if (next.startsWith("(") && next.endsWith(")")) {
				continue;
			}
			next = next.replace("/", "");
			next = next.replace("-", "");
			next = next.replace(',', '_');
			next = next.replace('.', '_');
			if (next.contains(" ")) {
				next = WordUtils.capitalizeFully(next);
			}
			b.append(next);
		}

		b.append("Enum");
		return b.toString();
	}

	// private void setValueSetName(String theString) {
	// myValueSetName = theString;
	// }

	private void write(ValueSetTm theValueSetTm, File theOutputDirectory, String thePackageBase) throws IOException {
		if (!theOutputDirectory.exists()) {
			theOutputDirectory.mkdirs();
		}
		if (!theOutputDirectory.isDirectory()) {
			throw new IOException(theOutputDirectory + " is not a directory");
		}

		File f = new File(theOutputDirectory, theValueSetTm.getClassName() + ".java");
		FileWriter w = new FileWriter(f, false);

		ourLog.info("Writing file: {}", f.getAbsolutePath());

		VelocityContext ctx = new VelocityContext();
		ctx.put("valueSet", theValueSetTm);
		ctx.put("packageBase", thePackageBase);

		VelocityEngine v = new VelocityEngine();
		v.setProperty("resource.loader", "cp");
		v.setProperty("cp.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		v.setProperty("runtime.references.strict", Boolean.TRUE);

		InputStream templateIs = ResourceGeneratorUsingSpreadsheet.class.getResourceAsStream("/vm/valueset.vm");
		InputStreamReader templateReader = new InputStreamReader(templateIs);
		v.evaluate(ctx, w, "", templateReader);

		w.close();
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {

		// p.setOutputDirectory("../hapi-fhir-base/src/main/java/ca/uhn/fhir/model/dstu/valueset/");
		// p.setOutputDirectory("target/generated/valuesets/ca/uhn/fhir/model/dstu/valueset");
		// p.parse();

	}

	public void writeMarkedValueSets(File theOutputDirectory, String thePackageBase) throws MojoFailureException {
		try {
			write(myMarkedValueSets, theOutputDirectory, thePackageBase);
		} catch (IOException e) {
			throw new MojoFailureException("Failed to write valueset", e);
		}
	}

}
