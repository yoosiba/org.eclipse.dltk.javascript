/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.javascript.internal.core.codeassist.selection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.codeassist.ScriptSelectionEngine;
import org.eclipse.dltk.compiler.env.IModuleSource;
import org.eclipse.dltk.core.DLTKLanguageManager;
import org.eclipse.dltk.core.IDLTKLanguageToolkit;
import org.eclipse.dltk.core.IField;
import org.eclipse.dltk.core.IMethod;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.search.FieldReferenceMatch;
import org.eclipse.dltk.core.search.IDLTKSearchConstants;
import org.eclipse.dltk.core.search.IDLTKSearchScope;
import org.eclipse.dltk.core.search.SearchEngine;
import org.eclipse.dltk.core.search.SearchMatch;
import org.eclipse.dltk.core.search.SearchParticipant;
import org.eclipse.dltk.core.search.SearchPattern;
import org.eclipse.dltk.core.search.SearchRequestor;
import org.eclipse.dltk.internal.javascript.reference.resolvers.ReferenceResolverContext;
import org.eclipse.dltk.internal.javascript.typeinference.FakeField;
import org.eclipse.dltk.internal.javascript.typeinference.HostCollection;
import org.eclipse.dltk.internal.javascript.typeinference.IReference;
import org.eclipse.dltk.internal.javascript.typeinference.VaribleDeclarationReference;
import org.eclipse.dltk.javascript.core.JavaScriptNature;
import org.eclipse.dltk.javascript.internal.core.codeassist.AssitUtils;

public class JavaScriptSelectionEngine extends ScriptSelectionEngine {

	public JavaScriptSelectionEngine(/* Map settings */) {
		// super(settings);
	}

	IModuleSource cu;

	public IModelElement[] select(IModuleSource cu, int offset, int i) {
		String content = cu.getSourceContents();
		String fileName = cu.getFileName();
		this.cu = cu;
		int index = content.indexOf('\n', offset);
		if (index == -1) {
			index = content.length();
		}
		ReferenceResolverContext rc = AssitUtils.buildContext(
				(org.eclipse.dltk.core.ISourceModule) cu, index, content,
				fileName);
		AssitUtils.PositionCalculator calc = new AssitUtils.PositionCalculator(
				content, offset, true);
		// if (i==offset)i=1;
		final List result = new ArrayList();
		String selection = calc.getCompletion();
		if (calc.isMember()) {
			processMember(rc, calc, result, selection);
		} else {
			processGlobals(rc, result, selection);
		}
		String completionPart = calc.getCompletionPart();
		for (int j = result.size(); --j >= 0;) {
			IModelElement element = (IModelElement) result.get(j);
			if (element.getElementName().equals(completionPart)) {
				if (element instanceof IField) {
					if (element instanceof FakeField) {
						((FakeField) element).setSnippet(selection);
					}
				}
			} else {
				result.remove(j);
			}
		}
		IModelElement[] resultA = new IModelElement[result.size()];
		result.toArray(resultA);
		return resultA;
	}

	private void processGlobals(ReferenceResolverContext rc, final List result,
			String selection) {
		if (!(selection.length() == 0)) {
			// local defenitition
			// global member;
			Set resolveGlobals = rc.resolveGlobals(selection);
			Iterator it = resolveGlobals.iterator();
			while (it.hasNext()) {
				Object next = it.next();
				if (next instanceof IReference) {
					IReference r = (IReference) next;
					if (r.getName().equals(selection))
						r.addModelElements(result);
				} else if (next instanceof IModelElement
						&& ((IModelElement) next).getElementName().equals(
								selection)) {
					result.add(next);
				}
			}
			if (result.size() == 0) {
				HostCollection hostCollection = rc.getHostCollection();
				IReference rm = hostCollection.getReference(selection);
				if (rm != null) {
					rm.addModelElements(result);
				}
				if (result.size() == 0) {
					doCompletionOnFunction(selection, result);
					doCompletionOnGlobalVariable(selection, result);
				}
			}
		}
	}

	private void processMember(ReferenceResolverContext buildContext,
			AssitUtils.PositionCalculator calc, final List result,
			String selection) {
		Set resolveGlobals = buildContext.resolveGlobals(selection);
		Iterator it = resolveGlobals.iterator();
		while (it.hasNext()) {
			IReference r = (IReference) it.next();
			r.addModelElements(result);
		}
		if (result.size() == 0) {
			IReference rm = buildContext.getHostCollection().queryElement(
					selection, true);
			if (rm != null)
				rm.addModelElements(result);
			if (result.size() == 0) {

				SearchRequestor requestor = new SearchRequestor() {

					public void acceptSearchMatch(SearchMatch match)
							throws CoreException {
						FieldReferenceMatch mr = (FieldReferenceMatch) match;
						ASTNode nm = mr.getNode();
						if (nm instanceof VaribleDeclarationReference) {
							VaribleDeclarationReference vm = (VaribleDeclarationReference) nm;
							IReference reference = vm.getReference();
							if (reference != null) {
								reference.addModelElements(result);
							}
						}

					}

				};
				IDLTKLanguageToolkit toolkit = DLTKLanguageManager
						.getLanguageToolkit(JavaScriptNature.NATURE_ID);
				IDLTKSearchScope scope = SearchEngine
						.createWorkspaceScope(toolkit);
				try {

					search(selection, IDLTKSearchConstants.FIELD,
							IDLTKSearchConstants.DECLARATIONS, scope, requestor);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void doCompletionOnFunction(final String startPart,
			final List modelElements) {
		SearchRequestor requestor = new SearchRequestor() {
			public void acceptSearchMatch(SearchMatch match)
					throws CoreException {
				Object element = match.getElement();
				if (element instanceof IMethod) {
					IMethod mn = (IMethod) element;
					if (mn.getElementName().equals(startPart)) {
						if (!modelElements.isEmpty())
							return;
						modelElements.add(element);
					}
				}
			}
		};
		IDLTKLanguageToolkit toolkit = null;
		toolkit = DLTKLanguageManager
				.getLanguageToolkit(JavaScriptNature.NATURE_ID);
		IDLTKSearchScope scope = SearchEngine.createWorkspaceScope(toolkit);
		try {
			search(startPart, IDLTKSearchConstants.METHOD,
					IDLTKSearchConstants.DECLARATIONS, scope, requestor);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void doCompletionOnGlobalVariable(String startPart,
			final List methods) {
		SearchRequestor requestor = new SearchRequestor() {
			public void acceptSearchMatch(SearchMatch match)
					throws CoreException {
				Object element = match.getElement();
				if (element instanceof IField) {
					methods.add(element);
				}
				if (match instanceof FieldReferenceMatch) {
					FieldReferenceMatch mr = (FieldReferenceMatch) match;

					// String string = mr.getNode().toString();
					// if (string.startsWith("!!!"))
					// return;
					// int i = string.indexOf('.');
					// if (i != -1)
					// string = string.substring(0, i);
					// if (!completedNames.contains(string))
					// props.add(string);

				}
			}
		};
		IDLTKLanguageToolkit toolkit = null;
		toolkit = DLTKLanguageManager
				.getLanguageToolkit(JavaScriptNature.NATURE_ID);
		IDLTKSearchScope scope = SearchEngine.createWorkspaceScope(toolkit);
		try {
			search(startPart + "*", IDLTKSearchConstants.FIELD,
					IDLTKSearchConstants.DECLARATIONS, scope, requestor);
			search(startPart + "*", IDLTKSearchConstants.FIELD,
					IDLTKSearchConstants.REFERENCES, scope, requestor);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void search(String patternString, int searchFor, int limitTo,
			IDLTKSearchScope scope, SearchRequestor resultCollector)
			throws CoreException {
		search(patternString, searchFor, limitTo, EXACT_RULE, scope,
				resultCollector);
	}

	protected void search(String patternString, int searchFor, int limitTo,
			int matchRule, IDLTKSearchScope scope, SearchRequestor requestor)
			throws CoreException {
		if (patternString.indexOf('*') != -1
				|| patternString.indexOf('?') != -1) {
			matchRule |= SearchPattern.R_PATTERN_MATCH;
		}
		SearchPattern pattern = SearchPattern.createPattern(patternString,
				searchFor, limitTo, matchRule, scope.getLanguageToolkit());
		if (pattern == null) {
			pattern = SearchPattern.createPattern(patternString, searchFor,
					limitTo, matchRule, scope.getLanguageToolkit());
		}
		new SearchEngine().search(pattern,
				new SearchParticipant[] { SearchEngine
						.getDefaultSearchParticipant() }, scope, requestor,
				null);
	}

}
