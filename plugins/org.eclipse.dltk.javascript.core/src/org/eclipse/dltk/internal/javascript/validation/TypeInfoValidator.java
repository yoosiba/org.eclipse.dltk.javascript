/*******************************************************************************
 * Copyright (c) 2010 xored software, Inc.  
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html  
 *
 * Contributors:
 *     xored software, Inc. - initial API and Implementation (Alex Panchenko)
 *******************************************************************************/
package org.eclipse.dltk.internal.javascript.validation;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.compiler.problem.DefaultProblem;
import org.eclipse.dltk.compiler.problem.IProblemReporter;
import org.eclipse.dltk.compiler.problem.ProblemSeverities;
import org.eclipse.dltk.core.builder.IBuildContext;
import org.eclipse.dltk.core.builder.IBuildParticipant;
import org.eclipse.dltk.core.builder.ISourceLineTracker;
import org.eclipse.dltk.internal.javascript.ti.GetMode;
import org.eclipse.dltk.internal.javascript.ti.IReferenceAttributes;
import org.eclipse.dltk.internal.javascript.ti.ITypeInferenceContext;
import org.eclipse.dltk.internal.javascript.ti.IValueReference;
import org.eclipse.dltk.internal.javascript.ti.TypeInferencer2;
import org.eclipse.dltk.internal.javascript.ti.TypeInferencerVisitor;
import org.eclipse.dltk.javascript.ast.CallExpression;
import org.eclipse.dltk.javascript.ast.Expression;
import org.eclipse.dltk.javascript.ast.Identifier;
import org.eclipse.dltk.javascript.ast.PropertyExpression;
import org.eclipse.dltk.javascript.ast.Script;
import org.eclipse.dltk.javascript.core.JavaScriptProblems;
import org.eclipse.dltk.javascript.typeinfo.model.Element;
import org.eclipse.dltk.javascript.typeinfo.model.Method;
import org.eclipse.dltk.javascript.typeinfo.model.Parameter;
import org.eclipse.dltk.javascript.typeinfo.model.ParameterKind;
import org.eclipse.dltk.javascript.typeinfo.model.Property;
import org.eclipse.dltk.javascript.typeinfo.model.Type;
import org.eclipse.dltk.javascript.typeinfo.model.TypeKind;
import org.eclipse.emf.common.util.EList;
import org.eclipse.osgi.util.NLS;

public class TypeInfoValidator implements IBuildParticipant, JavaScriptProblems {

	public void build(IBuildContext context) throws CoreException {
		final Script script = JavaScriptValidations.parse(context);
		TypeInferencer2 inferencer = new TypeInferencer2();
		inferencer.setModelElement(context.getSourceModule());
		inferencer.setVisitor(new ValidationVisitor(inferencer, context
				.getProblemReporter(), context.getLineTracker()));
		inferencer.doInferencing(script);
	}

	private static enum VisitorMode {
		NORMAL, CALL
	}

	private static class ValidationVisitor extends TypeInferencerVisitor {

		private final IProblemReporter reporter;
		private final ISourceLineTracker lineTracker;

		public ValidationVisitor(ITypeInferenceContext context,
				IProblemReporter reporter, ISourceLineTracker lineTracker) {
			super(context);
			this.reporter = reporter;
			this.lineTracker = lineTracker;
		}

		private final Map<ASTNode, VisitorMode> modes = new IdentityHashMap<ASTNode, VisitorMode>();

		private final Stack<ASTNode> visitStack = new Stack<ASTNode>();

		@Override
		public IValueReference visit(ASTNode node) {
			visitStack.push(node);
			try {
				return super.visit(node);
			} finally {
				visitStack.pop();
			}
		}

		private VisitorMode currentMode() {
			final VisitorMode mode = modes.get(visitStack.peek());
			return mode != null ? mode : VisitorMode.NORMAL;
		}

		@Override
		public IValueReference visitCallExpression(CallExpression node) {
			final ASTNode expression = node.getExpression();
			final ASTNode methodNode;
			if (expression instanceof PropertyExpression) {
				methodNode = ((PropertyExpression) expression).getProperty();
			} else {
				methodNode = expression;
			}
			modes.put(expression, VisitorMode.CALL);
			final IValueReference reference = visit(expression);
			modes.remove(expression);
			final List<ASTNode> callArgs = node.getArguments();
			IValueReference[] arguments = new IValueReference[callArgs.size()];
			for (int i = 0, size = callArgs.size(); i < size; ++i) {
				arguments[i] = visit(callArgs.get(i));
			}
			if (reference != null) {
				final Method method = extractElement(reference, Method.class);
				if (method != null) {
					// TODO how overloaded methods should be handled?
					if (!validateParameterCount(method, callArgs)) {
						reportProblem(JavaScriptProblems.WRONG_PARAMETER_COUNT,
								NLS.bind(ValidationMessages.WrongParamCount,
										method.getDeclaringType().getName(),
										reference.getName()), methodNode
										.sourceStart(), methodNode.sourceEnd());
					}
					if (method.isDeprecated()) {
						reportProblem(JavaScriptProblems.DEPRECATED_METHOD, NLS
								.bind(ValidationMessages.DeprecatedMethod,
										reference.getName(), method
												.getDeclaringType().getName()),
								methodNode.sourceStart(), methodNode
										.sourceEnd());
					}
				} else {
					final Type type = JavaScriptValidations.typeOf(reference
							.getParent());
					if (type != null && type.getKind() == TypeKind.JAVA) {
						reportProblem(JavaScriptProblems.UNDEFINED_METHOD, NLS
								.bind(ValidationMessages.UndefinedMethod,
										reference.getName(), type.getName()),
								methodNode.sourceStart(), methodNode
										.sourceEnd());
					}
				}
				return reference.getChild(IValueReference.FUNCTION_OP);
			} else {
				return null;
			}
		}

		/**
		 * @param reference
		 * @param elementType
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private <E extends Element> E extractElement(IValueReference reference,
				Class<E> elementType) {
			Object value = reference.getAttribute(IReferenceAttributes.ELEMENT);
			if (elementType.isInstance(value)) {
				return (E) value;
			}
			return null;
		}

		/**
		 * Validates the parameter count, returns <code>true</code> if correct.
		 * 
		 * @param method
		 * @param callArgs
		 * 
		 * @return
		 */
		private boolean validateParameterCount(Method method,
				List<ASTNode> callArgs) {
			final EList<Parameter> params = method.getParameters();
			if (params.size() == callArgs.size()) {
				return true;
			}
			if (params.size() < callArgs.size()
					&& !params.isEmpty()
					&& params.get(params.size() - 1).getKind() == ParameterKind.VARARGS) {
				return true;
			}
			if (params.size() > callArgs.size()
					&& params.get(callArgs.size()).getKind() == ParameterKind.OPTIONAL) {
				return true;
			}
			return false;
		}

		@Override
		public IValueReference visitPropertyExpression(PropertyExpression node) {
			final IValueReference object = visit(node.getObject());
			final Expression propName = node.getProperty();
			final String name = extractName(propName);
			if (object != null && name != null) {
				final IValueReference result = object.getChild(name,
						GetMode.CREATE_LAZY);
				if (currentMode() != VisitorMode.CALL) {
					validateProperty(result, propName);
				}
				return result;
			} else {
				return null;
			}
		}

		@Override
		public IValueReference visitIdentifier(Identifier node) {
			final IValueReference result = peekContext().getChild(
					node.getName(), GetMode.CREATE_LAZY);
			final Property property = extractElement(result, Property.class);
			if (property != null && property.isDeprecated()) {
				reportDeprecatedProperty(property, node);
			}
			return result;
		}

		private void validateProperty(IValueReference result,
				Expression propName) {
			final Property property = extractElement(result, Property.class);
			if (property != null) {
				if (property.isDeprecated()) {
					reportDeprecatedProperty(property, propName);
				}
			} else if (extractElement(result, Method.class) == null) {
				final Type type = JavaScriptValidations.typeOf(result
						.getParent());
				if (type != null && type.getKind() == TypeKind.JAVA) {
					reportProblem(JavaScriptProblems.UNDEFINED_PROPERTY, NLS
							.bind(ValidationMessages.UndefinedProperty, result
									.getName(), type.getName()), propName
							.sourceStart(), propName.sourceEnd());
				}
			}
		}

		private void reportDeprecatedProperty(Property property, ASTNode node) {
			final String msg;
			if (property.getDeclaringType() != null) {
				msg = NLS.bind(ValidationMessages.DeprecatedProperty, property
						.getName(), property.getDeclaringType().getName());
			} else {
				msg = NLS.bind(ValidationMessages.DeprecatedPropertyNoType,
						property.getName());
			}
			reportProblem(JavaScriptProblems.DEPRECATED_PROPERTY, msg, node
					.sourceStart(), node.sourceEnd());
		}

		@Override
		protected Type resolveType(org.eclipse.dltk.javascript.ast.Type type) {
			final Type result = super.resolveType(type);
			if (result != null) {
				if (result.getKind() == TypeKind.UNKNOWN) {
					reportProblem(JavaScriptProblems.UNKNOWN_TYPE, NLS.bind(
							ValidationMessages.UnknownType, type.getName()),
							type.sourceStart(), type.sourceEnd());
				} else if (result.isDeprecated()) {
					reportProblem(JavaScriptProblems.DEPRECATED_TYPE, NLS.bind(
							ValidationMessages.DeprecatedType, type.getName()),
							type.sourceStart(), type.sourceEnd());
				}
			}
			return result;
		}

		private void reportProblem(int id, String message, int start, int end) {
			reporter.reportProblem(new DefaultProblem(message, id, null,
					ProblemSeverities.Warning, start, end, lineTracker
							.getLineNumberOfOffset(start)));
		}
	}

}
