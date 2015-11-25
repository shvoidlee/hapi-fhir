package ca.uhn.fhir.context;

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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import ca.uhn.fhir.model.api.ICodeEnum;
import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;

public abstract class BaseRuntimeChildDatatypeDefinition extends BaseRuntimeDeclaredChildDefinition {

	private Class<? extends ICodeEnum> myCodeType;
	private Class<? extends IDatatype> myDatatype;

	private BaseRuntimeElementDefinition<?> myElementDefinition;

	public BaseRuntimeChildDatatypeDefinition(Field theField, String theElementName, Child theChildAnnotation, Description theDescriptionAnnotation, Class<? extends IDatatype> theDatatype) {
		super(theField, theChildAnnotation, theDescriptionAnnotation, theElementName);
		assert theDatatype != IDatatype.class; // should use RuntimeChildAny
		myDatatype = theDatatype;
	}

	@Override
	public String getChildNameByDatatype(Class<? extends IElement> theDatatype) {
		if (myDatatype.equals(theDatatype)) {
			return getElementName();
		}
		return null;
	}

	@Override
	public BaseRuntimeElementDefinition<?> getChildElementDefinitionByDatatype(Class<? extends IElement> theDatatype) {
		Class<? extends IElement> datatype = theDatatype;
		if (myDatatype.equals(datatype)) {
			return myElementDefinition;
		}
		return null;
	}

	@Override
	public BaseRuntimeElementDefinition<?> getChildByName(String theName) {
		if (getElementName().equals(theName)) {
			return myElementDefinition;
		}
		return null;
	}

	public Class<? extends ICodeEnum> getCodeType() {
		return myCodeType;
	}

	public Class<? extends IDatatype> getDatatype() {
		return myDatatype;
	}

	@Override
	public Set<String> getValidChildNames() {
		return Collections.singleton(getElementName());
	}

	@Override
	void sealAndInitialize(Map<Class<? extends IElement>, BaseRuntimeElementDefinition<?>> theClassToElementDefinitions) {
		myElementDefinition = theClassToElementDefinitions.get(getDatatype());
		assert myElementDefinition != null : "Unknown type: " + getDatatype();
	}

	public void setCodeType(Class<? extends ICodeEnum> theType) {
		if (myElementDefinition != null) {
			throw new IllegalStateException("Can not set code type at runtime");
		}
		myCodeType = theType;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getElementName() + "]";
	}

	
}
