package ca.uhn.fhir.rest.param;

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

import java.util.ArrayList;
import java.util.List;

import ca.uhn.fhir.model.api.IQueryParameterOr;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.method.QualifiedParamList;

abstract class BaseOrListParam<T extends IQueryParameterType> implements IQueryParameterOr<T> {

	private List<T> myList=new ArrayList<T>();

//	public void addToken(T theParam) {
//		Validate.notNull(theParam,"Param can not be null");
//		myList.add(theParam);
//	}
	
	@Override
	public void setValuesAsQueryTokens(QualifiedParamList theParameters) {
		myList.clear();
		for (String next : theParameters) {
			T nextParam = newInstance();
			nextParam.setValueAsQueryToken(theParameters.getQualifier(), next);
			myList.add(nextParam);
		}
	}

	abstract T newInstance();

	public void add(T theParameter) {
		if (theParameter != null) {
			myList.add(theParameter);
		}
	}
	
	@Override
	public List<T> getValuesAsQueryTokens() {
		return myList;
	}

}
