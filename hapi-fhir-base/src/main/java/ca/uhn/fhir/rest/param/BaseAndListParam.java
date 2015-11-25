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

import ca.uhn.fhir.model.api.IQueryParameterAnd;
import ca.uhn.fhir.model.api.IQueryParameterOr;
import ca.uhn.fhir.model.dstu.valueset.SearchParamTypeEnum;
import ca.uhn.fhir.rest.method.QualifiedParamList;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

public abstract class BaseAndListParam<T extends IQueryParameterOr<?>> implements IQueryParameterAnd<T> {

	private List<T> myValues=new ArrayList<T>(); 
	
	public void addValue(T theValue) {
		myValues.add(theValue);
	}
	
	@Override
	public void setValuesAsQueryTokens(List<QualifiedParamList> theParameters) throws InvalidRequestException {
		myValues.clear();
		for (QualifiedParamList nextParam : theParameters) {
			T nextList = newInstance();
			nextList.setValuesAsQueryTokens(nextParam);
			myValues.add(nextList);
		}
	}

	public abstract SearchParamTypeEnum getSearchParamType();
	
	abstract T newInstance();

	@Override
	public List<T> getValuesAsQueryTokens() {
		return myValues;
	}


}
