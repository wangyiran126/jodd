// Copyright (c) 2003-present, Jodd Team (http://jodd.org)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package jodd.madvoc.component;

import jodd.introspector.PropertyDescriptor;
import jodd.madvoc.ScopeData;
import jodd.madvoc.ScopeType;
import jodd.madvoc.MadvocException;
import jodd.madvoc.meta.In;
import jodd.madvoc.meta.InOut;
import jodd.madvoc.meta.Out;
import jodd.introspector.ClassDescriptor;
import jodd.introspector.ClassIntrospector;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.ArrayList;

/**
 * Resolver for {@link jodd.madvoc.ScopeData scope data} for certain types.
 * It does not cache anything as Scope data is cached in {@link jodd.madvoc.ActionConfig}.
 * Resolving only happens during the initialization, and it might be repeated for
 * certain types (as there is no cache), but that is acceptable to reduce memory
 * usage (no cache) and several lookups (for each interceptor) during every request.
 */
public class ScopeDataResolver {

	/**
	 * Resolve scope data in given type for all scope types.
	 * Returns <code>null</code> if no scope data exist.
	 * 汇总该类的注解数据
	 */
	public ScopeData[] resolveScopeData(Class type) {
		final ScopeType[] allScopeTypes = ScopeType.values();

		ScopeData[] scopeData = new ScopeData[allScopeTypes.length];

		int count = 0;
//------------------实例化scopedata数组,每个数组的索引是该scopeType的value
		for (ScopeType scopeType : allScopeTypes) {
			ScopeData sd = inspectClassScopeData(type, scopeType);
			if (sd != null) {
				count++;
			}
			scopeData[scopeType.value()] = sd;
		}

		if (count == 0) {
			return null;
		}

		return scopeData;
	}

	/**
	 * Resolves scope data in given annotations for all scope types.
	 * Returns <code>null</code> if no scope data exist.
	 */
	public ScopeData[] resolveScopeData(String name, Class type, Annotation[] annotations) {
		final ScopeType[] allScopeTypes = ScopeType.values();

		ScopeData[] scopeData = new ScopeData[allScopeTypes.length];

		int count = 0;

		for (ScopeType scopeType : allScopeTypes) {
			ScopeData sd = inspectMethodParameterScopeData(name, type, annotations, scopeType);
			if (sd != null) {
				count++;
			}
			scopeData[scopeType.value()] = sd;
		}

		if (count == 0) {
			return null;
		}

		return scopeData;
	}

	/**
	 * Scans annotation and returns type of Madvoc annotations.
	 */
	public Class<? extends Annotation> detectAnnotationType(Annotation[] annotations) {
		for (Annotation annotation : annotations) {
			if (annotation instanceof In) {
				return annotation.annotationType();
			}
			else if (annotation instanceof Out) {
				return annotation.annotationType();
			}
			else if (annotation instanceof InOut) {
				return annotation.annotationType();
			}
		}
		return null;
	}


	// ---------------------------------------------------------------- inspect method

	/**
	 * Inspects all method parameters for scope type.
	 */
	protected ScopeData inspectMethodParameterScopeData(String name, Class type, Annotation[] annotations, ScopeType scopeType) {
		ScopeData sd = new ScopeData();
		int count = 0;

		for (Annotation annotation : annotations) {

			if (annotation instanceof In) {
				ScopeData.In scopeDataIn = resolveDataOfInOutAnnotationInScopeType((In) annotation, scopeType, name, type);
				if (scopeDataIn != null) {
					count++;
					sd.in = new ScopeData.In[] {scopeDataIn};
				}
			}
			else if (annotation instanceof Out) {
				ScopeData.Out scopeDataOut = resolveDataOfOutAnnotationInScopeType((Out) annotation, scopeType, name, type);
				if (scopeDataOut != null) {
					count++;
					sd.out = new ScopeData.Out[] {scopeDataOut};
				}
			}
			else if (annotation instanceof InOut) {
				ScopeData.In scopeDataIn = resolveDataOfInOutAnnotationInScopeType((InOut) annotation, scopeType, name, type);
				if (scopeDataIn != null) {
					count++;
					sd.in = new ScopeData.In[] {scopeDataIn};
				}

				ScopeData.Out scopeDataOut = resolveDataOfOutAnnotationInScopeType((InOut) annotation, scopeType, name, type);
				if (scopeDataOut != null) {
					count++;
					sd.out = new ScopeData.Out[] {scopeDataOut};
				}
			}
		}

		if (count == 0) {
			return null;
		}

		return sd;
	}

	// ---------------------------------------------------------------- inspect class

	/**
	 * Saves value and property name.
	 */
	protected void resolveAnnotationValueAndProperty(ScopeData.In ii, String value, String propertyName) {
		value = value.trim();
		if (value.length() > 0) {
			ii.name = value;
			ii.target = propertyName;
		}
		else {
			ii.name = propertyName;
			ii.target = null;
		}
	}

	/**
	 * Saves value and property name.
	 */
	protected void resolveAnnotationValueAndProperty(ScopeData.Out oi, String value, String propertyName) {
		value = value.trim();
		if (value.length() > 0) {
			oi.name = value;
			oi.target = propertyName;
		}
		else {
			oi.name = propertyName;
			oi.target = null;
		}
	}

	/**
	 * Inspects single IN annotation for a property.
	 */
	protected ScopeData.In resolveDataOfInOutAnnotationInScopeType(In in, ScopeType scopeType, String propertyName, Class propertyType) {
		if (in == null) {
			return null;
		}
		ScopeType scopeOfAnnotation = in.scope();
		if (scopeOfAnnotation != scopeType) {//如果与对应的scopetype不一致,则返回空值
			return null;
		}
		ScopeData.In ii = new ScopeData.In();
		resolveAnnotationValueAndProperty(ii, in.value(), propertyName);
		ii.type = propertyType;
		return ii;
	}

	/**
	 * Inspects single INOUT annotation as IN.
	 * @see #resolveDataOfInOutAnnotationInScopeType(jodd.madvoc.meta.In, jodd.madvoc.ScopeType, String, Class)
	 */
	protected ScopeData.In resolveDataOfInOutAnnotationInScopeType(InOut inOut, ScopeType scopeType, String propertyName, Class propertyType) {
		if (inOut == null) {
			return null;
		}
		ScopeType scope = inOut.scope();
		if (scope != scopeType) {
			return null;
		}
		ScopeData.In ii = new ScopeData.In();
		resolveAnnotationValueAndProperty(ii, inOut.value(), propertyName);
		ii.type = propertyType;
		return ii;
	}

	/**
	 * Inspects single OUT annotation for a property.
	 */
	protected ScopeData.Out resolveDataOfOutAnnotationInScopeType(Out out, ScopeType scopeType, String propertyName, Class propertyType) {
		if (out == null) {
			return null;
		}
		ScopeType scope = out.scope();
		if (scope != scopeType) {
			return null;
		}
		ScopeData.Out oi = new ScopeData.Out();
		resolveAnnotationValueAndProperty(oi, out.value(), propertyName);
		oi.type = propertyType;
		return oi;
	}

	/**
	 * Inspects single INOUT annotation as OUT.
	 * @see #resolveDataOfOutAnnotationInScopeType(jodd.madvoc.meta.Out, jodd.madvoc.ScopeType, String, Class)
	 */
	protected ScopeData.Out resolveDataOfOutAnnotationInScopeType(InOut inOut, ScopeType scopeType, String propertyName, Class propertyType) {
		if (inOut == null) {
			return null;
		}
		ScopeType scope = inOut.scope();
		if (scope != scopeType) {
			return null;
		}
		ScopeData.Out oi = new ScopeData.Out();
		resolveAnnotationValueAndProperty(oi, inOut.value(), propertyName);
		oi.type = propertyType;
		return oi;
	}

	/**
	 * Inspect action for all In/Out annotations.
	 * Returns <code>null</code> if there are no In and Out data.
	 */
	protected ScopeData inspectClassScopeData(Class actionClass, ScopeType scopeType) {
		ClassDescriptor cd = ClassIntrospector.lookup(actionClass);
//----------------------------获取method或者field属性
		PropertyDescriptor[] methodFieldProperties = cd.getAllPropertyDescriptors();

		List<ScopeData.In> inScopeDatas = new ArrayList<>(methodFieldProperties.length);
		List<ScopeData.Out> outScopeDatas = new ArrayList<>(methodFieldProperties.length);

		for (PropertyDescriptor property : methodFieldProperties) {
			// collect annotations

			In in = getInAnnotationFieldWriteMethodOrReadMethod(property);

			InOut inout = getInOutAnnotationFieldWriteMethodOrReadMethod(property);

			Out out = getOutAnnotationFieldWriteMethodOrReadMethod(property);

			// checks
//--------------------------------查看注解不冲突
			if (inout != null) {
				if (in != null || out != null) {
					throw new MadvocException("@InOut can not be used with @In or @Out: " + property.getClassDescriptor().getClass() + '#' + property.getName());
				}
			}


			// inspect all
//------------------------根据@in注解,生成与scopeType域值相同的该注解的scopedata
			ScopeData.In ii = resolveDataOfInOutAnnotationInScopeType(in, scopeType, property.getName(), property.getType());
			if (ii != null) {
				inScopeDatas.add(ii);
			}
			ii = resolveDataOfInOutAnnotationInScopeType(inout, scopeType, property.getName(), property.getType());
			if (ii != null) {
				inScopeDatas.add(ii);
			}

			ScopeData.Out oi = resolveDataOfOutAnnotationInScopeType(out, scopeType, property.getName(), property.getType());
			if (oi != null) {
				outScopeDatas.add(oi);
			}
			oi = resolveDataOfOutAnnotationInScopeType(inout, scopeType, property.getName(), property.getType());
			if (oi != null) {
				outScopeDatas.add(oi);
			}
		}

		if ((inScopeDatas.isEmpty()) && (outScopeDatas.isEmpty())) {
			return null;
		}
//----------------------封装所有In和out的scopedata
		ScopeData scopeData = new ScopeData();
		if (!inScopeDatas.isEmpty()) {
			scopeData.in = inScopeDatas.toArray(new ScopeData.In[inScopeDatas.size()]);
		}
		if (!outScopeDatas.isEmpty()) {
			scopeData.out = outScopeDatas.toArray(new ScopeData.Out[outScopeDatas.size()]);
		}
		return scopeData;
	}

	private Out getOutAnnotationFieldWriteMethodOrReadMethod(PropertyDescriptor pd) {
		Out out = null;
		if (pd.getFieldDescriptor() != null) {
			out = pd.getFieldDescriptor().getField().getAnnotation(Out.class);
		}
		if (out == null && pd.getWriteMethodDescriptor() != null) {
			out = pd.getWriteMethodDescriptor().getMethod().getAnnotation(Out.class);
		}
		if (out == null && pd.getReadMethodDescriptor() != null) {
			out = pd.getReadMethodDescriptor().getMethod().getAnnotation(Out.class);
		}
		return out;
	}

	private InOut getInOutAnnotationFieldWriteMethodOrReadMethod(PropertyDescriptor pd) {
		InOut inout = null;
		if (pd.getFieldDescriptor() != null) {
			inout = pd.getFieldDescriptor().getField().getAnnotation(InOut.class);
		}
		if (inout == null && pd.getWriteMethodDescriptor() != null) {
			inout = pd.getWriteMethodDescriptor().getMethod().getAnnotation(InOut.class);
		}
		if (inout == null && pd.getReadMethodDescriptor() != null) {
			inout = pd.getReadMethodDescriptor().getMethod().getAnnotation(InOut.class);
		}
		return inout;
	}

	private In getInAnnotationFieldWriteMethodOrReadMethod(PropertyDescriptor pd) {
		In in = null;
		if (pd.getFieldDescriptor() != null) {
			in = pd.getFieldDescriptor().getField().getAnnotation(In.class);
		}
		if (in == null && pd.getWriteMethodDescriptor() != null) {
			in = pd.getWriteMethodDescriptor().getMethod().getAnnotation(In.class);
		}
		if (in == null && pd.getReadMethodDescriptor() != null) {
			in = pd.getReadMethodDescriptor().getMethod().getAnnotation(In.class);
		}
		return in;

	}

}