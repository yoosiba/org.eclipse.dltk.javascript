/*******************************************************************************
 * Copyright (c) 2011 NumberFour AG
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     NumberFour AG - initial API and Implementation (Alex Panchenko)
 *******************************************************************************/
package org.eclipse.dltk.javascript.typeinfo;

/**
 * "Runtime" type expression hierarchy, used when evaluating the code. The
 * instances are created based on EMF-backed
 * {@link org.eclipse.dltk.javascript.typeinfo.model.JSType} type expressions
 * used only for declarations, resolving all
 * {@link org.eclipse.dltk.javascript.typeinfo.model.Type} proxies along the
 * way.
 */
public interface IRType {

	/**
	 * Returns the name of this type expression.
	 */
	String getName();

	/**
	 * Checks if this type expression is compatible with the specified one.
	 */
	TypeCompatibility isAssignableFrom(IRType type);

	/**
	 * Returns the {@link ITypeSystem} this type expression belongs to, or null.
	 * 
	 * @return
	 */
	ITypeSystem activeTypeSystem();

	/**
	 * Answers if additional members could be added to this type expression.
	 */
	boolean isExtensible();

}
