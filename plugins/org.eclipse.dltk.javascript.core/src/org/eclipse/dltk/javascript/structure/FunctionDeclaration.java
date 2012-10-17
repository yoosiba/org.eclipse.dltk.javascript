package org.eclipse.dltk.javascript.structure;

import org.eclipse.dltk.core.ISourceNode;
import org.eclipse.dltk.javascript.ast.FunctionStatement;
import org.eclipse.dltk.javascript.typeinference.ReferenceLocation;
import org.eclipse.dltk.javascript.typeinfo.IModelBuilder.IMethod;

public class FunctionDeclaration extends FunctionNode implements IDeclaration {

	public FunctionDeclaration(IParentNode parent, FunctionStatement function,
			IMethod method) {
		super(parent, function, method);
	}

	@Override
	public String getName() {
		return function.getFunctionName();
	}

	@Override
	public ISourceNode getNameNode() {
		return function.getIdentifier();
	}

	public ReferenceLocation getLocation() {
		return method.getLocation();
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(getName());
		sb.append('(');
		sb.append(argumentsToString());
		sb.append(")");
		if (getType() != null) {
			sb.append(':');
			sb.append(getType().getName());
		}
		sb.append(" function");
		return sb.toString();
	}

}
