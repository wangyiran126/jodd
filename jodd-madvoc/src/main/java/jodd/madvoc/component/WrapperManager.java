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

import jodd.madvoc.ActionWrapper;
import jodd.madvoc.BaseActionWrapperStack;
import jodd.madvoc.MadvocException;
import jodd.madvoc.injector.Target;
import jodd.petite.meta.PetiteInject;
import jodd.util.ReflectUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base wrapper manager implements common logic of a wrapper.
 */
public abstract class WrapperManager<T extends ActionWrapper> {

	@PetiteInject
	protected ContextInjectorComponent contextInjectorComponent;

	@PetiteInject
	protected MadvocConfig madvocConfig;

	protected WrapperManager() {
		wrappers = new HashMap<>();
	}

	// ---------------------------------------------------------------- container

	protected Map<String, T> wrappers;

	/**
	 * Returns all action wrappers.
	 */
	protected Set<T> getAll() {
		Set<T> set = new HashSet<>(wrappers.size());
		set.addAll(wrappers.values());
		return set;
	}


	/**
	 * Looks up for existing wrapper. Returns <code>null</code> if wrapper is not already registered.
	 */
	public T lookup(String name) {
		return wrappers.get(name);
	}

	/**
	 * Resolves single wrapper. Creates new wrapper instance if not already registered.
	 * Does not replaceSpecialActionWrapper the wrappers.
	 */
	public T resolve(Class<? extends T> wrapperClass) {
		String wrapperClassName = wrapperClass.getName();

		T wrapper = lookup(wrapperClassName);

		if (wrapper == null) {
			wrapper = createWrapper(wrapperClass);

			initializeWrapper(wrapper);

			wrappers.put(wrapperClassName, wrapper);
		}
		return wrapper;
	}

	/**
	 * Resolves wrappers. Unregistered wrappers will be registered. Returned array may be
	 * different size than size of provided array, due to {@link #replaceSpecialActionWrapper(Class[]) expanding}.
	 */
	public T[] resolveAll(Class<? extends T>[] wrapperClasses) {
		if (wrapperClasses == null) {
			return null;
		}
		wrapperClasses = replaceSpecialActionWrapper(wrapperClasses);
		T[] result = createArray(wrapperClasses.length);

		for (int i = 0; i < wrapperClasses.length; i++) {
			result[i] = resolve(wrapperClasses[i]);
		}
		return result;
	}

	/**
	 * Creates an array of wrapper instances.
	 */
	protected abstract T[] createArray(int len);

	// ---------------------------------------------------------------- init

	/**
	 * Initializes action wrapper.
	 */
	protected void initializeWrapper(T wrapper) {
		contextInjectorComponent.injectContext(new Target(wrapper));

		wrapper.init();
	}

	// ---------------------------------------------------------------- expander

	/**
	 * Returns default wrappers from the configuration.
	 */
	protected abstract Class<? extends T>[] getMadvocConfigDefaultInterceptors();

	/**
	 * Returns marker wrapper class, shortcut for default web app wrappers.
	 */
	protected abstract Class<? extends T> getDefaultWebAppWrapper();

	/**
	 * Replaces all {@link #getDefaultWebAppWrapper()} with {@link #getDefaultWebAppWrapper()}
	 * and {@link BaseActionWrapperStack} with stack values.
	 * 替换与getDefaultWebAppWrapper()相同的拦截器,换成defaultInterceptors里面的拦截器
	 */
	protected Class<? extends T>[] replaceSpecialActionWrapper(Class<? extends T>[] interceptorsOrFilters) {
		if (interceptorsOrFilters == null) {
			return null;
		}
		List<Class<? extends T>> interceptorsList = new ArrayList<>(interceptorsOrFilters.length);
		interceptorsList.addAll(Arrays.asList(interceptorsOrFilters));

		int currentIndex = 0;
		while (currentIndex < interceptorsList.size()) {
			Class<? extends T> wrapperClass = interceptorsList.get(currentIndex);
			if (wrapperClass == null) {
				continue;
			}
			//------------------------------替换DefaultWebAppInterceptors成defaultInterceptors
			if (wrapperClass.equals(getDefaultWebAppWrapper())) {
				interceptorsList.remove(currentIndex);
				// add default wrappers interceptorsList
				Class<? extends T>[] defaultInterceptors = getMadvocConfigDefaultInterceptors();
				if (defaultInterceptors != null) {
					int addIndex = currentIndex;
					for (Class<? extends T> defaultInterceptor : defaultInterceptors) {
						// can't add default interceptorsList stack to default interceptorsList
						if (defaultInterceptor.equals(getDefaultWebAppWrapper())) {
							throw new MadvocException("Default wrapper interceptorsList is self-contained (cyclic dependency)!");
						}
						interceptorsList.add(addIndex, defaultInterceptor);
						addIndex++;
					}
				}
				continue;
			}
			//————————————————————————————————替换BaseActionWrapperStack成
			if (ReflectUtil.isTypeOf(wrapperClass, BaseActionWrapperStack.class)) {
				BaseActionWrapperStack stack = (BaseActionWrapperStack) resolve(wrapperClass);
				interceptorsList.remove(currentIndex);
				Class<? extends T>[] stackWrappers = stack.getWrappers();
				if (stackWrappers != null) {
					interceptorsList.addAll(currentIndex, Arrays.asList(stackWrappers));
				}
				currentIndex--;
				//continue;
			}
			currentIndex++;
		}
		return interceptorsList.toArray(new Class[interceptorsList.size()]);
	}

	// ---------------------------------------------------------------- create

	/**
	 * Creates new wrapper.
	 */
	protected <R extends T> R createWrapper(Class<R> wrapperClass) {
		try {
		    return wrapperClass.newInstance();
		} catch (Exception ex) {
			throw new MadvocException("Invalid Madvoc wrapper: " + wrapperClass, ex);
		}
	}

}