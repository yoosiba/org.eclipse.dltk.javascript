/*******************************************************************************
 * Copyright (c) 2009 xored software, Inc.  
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html  
 *
 * Contributors:
 *     xored software, Inc. - initial API and Implementation (Vladimir Belov)
 *******************************************************************************/

package org.eclipse.dltk.javascript.ast;

import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.ast.ASTVisitor;
import org.eclipse.dltk.core.ISourceRange;
import org.eclipse.dltk.core.SourceRange;

public abstract class Comment extends ASTNode {

	private String text;

	public Comment() {
	}

	@Override
	public void traverse(ASTVisitor visitor) throws Exception {
		if (visitor.visit(this)) {
			visitor.endvisit(this);
		}
	}

	public String getText() {
		return this.text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public abstract boolean isMultiLine();

	public abstract boolean isDocumentation();

	@Override
	public String toString() {
		return getClass().getSimpleName() + '[' + getText() + ']';
	}

	public ISourceRange getRange() {
		return new SourceRange(sourceStart(), sourceEnd() - sourceStart());
	}
}
