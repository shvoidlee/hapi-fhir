package ca.uhn.fhir.util;

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

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.ISupportsUndeclaredExtensions;

/**
 * @see FhirTerser#visit(ca.uhn.fhir.model.api.IResource, IModelVisitor)
 */
public interface IModelVisitor {

	/**
	 * 
	 * @param theElement
	 * @param theChildDefinition May be null if this is a root element
	 * @param theDefinition
	 */
	void acceptElement(IElement theElement, BaseRuntimeChildDefinition theChildDefinition, BaseRuntimeElementDefinition<?> theDefinition);

	/**
	 * 
	 * @param theContainingElement
	 * @param theChildDefinition May be null if this is a root element
	 * @param theDefinition
	 * @param theNextExt
	 */
	void acceptUndeclaredExtension(ISupportsUndeclaredExtensions theContainingElement, BaseRuntimeChildDefinition theChildDefinition, BaseRuntimeElementDefinition<?> theDefinition, ExtensionDt theNextExt);

	
	
}
