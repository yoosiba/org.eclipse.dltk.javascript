package org.eclipse.dltk.internal.javascript.parser.structure;

import java.util.Collections;
import java.util.List;

import org.eclipse.dltk.compiler.IElementRequestor.FieldInfo;
import org.eclipse.dltk.compiler.ISourceElementRequestor;
import org.eclipse.dltk.javascript.ast.VariableDeclaration;
import org.eclipse.dltk.javascript.typeinfo.model.JSType;

@Structure3
public class VariableNode extends ParentNode implements IDeclaration {

	private final JSType type;
	private final VariableDeclaration declaration;
	private IStructureNode value;

	public VariableNode(IParentNode parent, VariableDeclaration declaration,
			JSType type) {
		super(parent);
		this.declaration = declaration;
		this.type = type;
	}

	public String getName() {
		return declaration.getVariableName();
	}

	public JSType getType() {
		return type;
	}

	public IStructureNode getValue() {
		return value;
	}

	public void setValue(IStructureNode value) {
		this.value = value;
	}

	@Override
	public List<IStructureNode> getChildren() {
		return value != null ? Collections.singletonList(value) : Collections
				.<IStructureNode> emptyList();
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(getName());
		sb.append(":variable");
		if (value != null) {
			sb.append("=");
			sb.append(value);
		}
		return sb.toString();
	}

	public void reportStructure(ISourceElementRequestor requestor,
			boolean allowDeclarations) {
		if (allowDeclarations) {
			final FieldInfo info = new FieldInfo();
			info.declarationStart = declaration.start();
			info.name = getName();
			info.nameSourceStart = declaration.getIdentifier().start();
			info.nameSourceEnd = declaration.getIdentifier().end();
			info.type = typeToModel(type);
			requestor.enterField(info);
			reportChildrenStructure(requestor, allowDeclarations);
			requestor.exitField(declaration.end() - 1);
		} else {
			reportChildrenStructure(requestor, allowDeclarations);
		}
	}

}
