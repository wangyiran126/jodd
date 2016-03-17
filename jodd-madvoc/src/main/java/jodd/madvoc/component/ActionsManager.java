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

import jodd.introspector.ClassIntrospector;
import jodd.introspector.MethodDescriptor;
import jodd.madvoc.ActionInfo;
import jodd.madvoc.ActionPathInfo;
import jodd.madvoc.ActionDef;
import jodd.madvoc.MadvocException;
import jodd.madvoc.macro.PathMatcher;
import jodd.petite.meta.PetiteInject;
import jodd.util.ClassLoaderUtil;
import jodd.util.StringUtil;
import jodd.util.collection.SortedArrayList;
import jodd.log.Logger;
import jodd.log.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages all Madvoc action and aliases registrations.
 */
public class ActionsManager {

	private static final Logger log = LoggerFactory.getLogger(ActionsManager.class);

	@PetiteInject
	protected ActionMethodParser actionMethodParser;

	@PetiteInject
	protected ActionPathMacroManager actionPathMacroManager;

	@PetiteInject
	protected MadvocConfig madvocConfig;
//-------------action方法个数
	protected int actionsCount;
	protected boolean asyncMode;
	protected final HashMap<String, ActionPathInfo> pathInfosNotWithMatcher;		// map of all action paths w/o macros
	//deep排序,deep最小的放在第一位,从而达到优化
	protected final SortedArrayList<ActionPathInfo> pathInfosWithMatcher;		// list of all action paths with macros
	protected final HashMap<String, ActionInfo> actionInfos;		// another map of all action configs
	protected Map<String, String> pathAliases;					// path aliases

	public ActionsManager() {
		this.pathInfosNotWithMatcher = new HashMap<>();
		this.pathInfosWithMatcher = new SortedArrayList<>(new ActionConfigSetComparator());
		this.pathAliases = new HashMap<>();
		this.actionInfos = new HashMap<>();
		this.asyncMode = false;
	}

	/**
	 * Comparator that considers first chunks number then action path.
	 */
	public static class ActionConfigSetComparator implements Comparator<ActionPathInfo>, Serializable {
		private static final long serialVersionUID = 1;

		public int compare(ActionPathInfo set1, ActionPathInfo set2) {
			int deep1 = set1.deep;
			int deep2 = set2.deep;

			if (deep1 == deep2) {
				return set1.actionPath.compareTo(set2.actionPath);
			}
			return deep1 - deep2;
		}
	}

	/**
	 * Returns all registered action configurations.
	 * Returned list is a join of action paths
	 * with and without the macro.
	 */
	public List<ActionInfo> getAllActionConfigurations() {
		List<ActionInfo> all = new ArrayList<>(actionsCount);

		for (ActionPathInfo set : pathInfosNotWithMatcher.values()) {
			all.addAll(set.getActionConfigs());
		}
		for (ActionPathInfo set : pathInfosWithMatcher) {
			all.addAll(set.getActionConfigs());
		}
		return all;
	}

	/**
	 * Returns total number of registered actions.
	 */
	public int getActionsCount() {
		return actionsCount;
	}

	/**
	 * Returns <code>true</code> if at least one action has
	 * async mode turned on.
	 */
	public boolean isAsyncModeOn() {
		return asyncMode;
	}

	// ---------------------------------------------------------------- register variations

	/**
	 * Resolves action method for given action class ane method name.
	 */
	public Method resolveActionMethod(Class<?> actionClass, String methodName) {
		MethodDescriptor methodDescriptor = ClassIntrospector.lookup(actionClass).getMethodDescriptor(methodName, false);
		if (methodDescriptor == null) {
			throw new MadvocException("Public method not found: " + actionClass.getSimpleName() + "#" + methodName);
		}
		return methodDescriptor.getMethod();
	}

	/**
	 * Registers action with provided action signature.
	 */
	public ActionInfo register(String actionSignature) {
		return register(actionSignature, null);
	}

	/**
	 * Registers action with provided action signature.
	 */
	public ActionInfo register(String actionSignature, ActionDef actionDef) {
		int ndx = actionSignature.indexOf('#');
		if (ndx == -1) {
			throw new MadvocException("Madvoc action signature syntax error: " + actionSignature);
		}
		String actionClassName = actionSignature.substring(0, ndx);
		String actionMethodName = actionSignature.substring(ndx + 1);
		Class actionClass;
		try {
			actionClass = ClassLoaderUtil.loadClass(actionClassName);
		} catch (ClassNotFoundException cnfex) {
			throw new MadvocException("Madvoc action class not found: " + actionClassName, cnfex);
		}
		return register(actionClass, actionMethodName, actionDef);
	}

	public ActionInfo register(Class actionClass, Method actionMethod) {
		return registerAction(actionClass, actionMethod, null);
	}

	public ActionInfo register(Class actionClass, Method actionMethod, ActionDef actionDef) {
		return registerAction(actionClass, actionMethod, actionDef);
	}

	/**
	 * Registers action with provided action class and method name.
	 */
	public ActionInfo register(Class actionClass, String actionMethodName) {
		Method actionMethod = resolveActionMethod(actionClass, actionMethodName);
		return registerAction(actionClass, actionMethod, null);
	}

	public ActionInfo register(Class actionClass, String actionMethodName, ActionDef actionDef) {
		Method actionMethod = resolveActionMethod(actionClass, actionMethodName);
		return registerAction(actionClass, actionMethod, actionDef);
	}

	// ---------------------------------------------------------------- registration

	/**
	 * Registration main point. Does two things:
	 * <ul>
	 *     <li>{@link jodd.madvoc.component.ActionMethodParser#parse(Class, java.lang.reflect.Method, jodd.madvoc.ActionDef) parse action}
	 *     and creates {@link ActionInfo}</li>
	 *     <li>{@link #registerAction(ActionInfo) registers} created {@link ActionInfo}</li>
	 * </ul>
	 * Returns created {@link ActionInfo}.
	 * @see #registerAction(ActionInfo)
	 */
	protected ActionInfo registerAction(Class actionClass, Method actionMethod, ActionDef actionDef) {
		//-------------------------解析actionClass类,创建actionConfig
		ActionInfo actionInfo = actionMethodParser.parse(actionClass, actionMethod, actionDef);
		if (actionInfo == null) {
			return null;
		}
		return registerAction(actionInfo);
	}

	/**
	 * Registers manually created {@link ActionInfo action configurations}.
	 * Optionally, if action path with the same name already exist,
	 * exception will be thrown.
	 */
	public ActionInfo registerAction(ActionInfo actionInfo) {
		String actionPath = actionInfo.actionPath;

		if (log.isDebugEnabled()) {
			log.debug("Registering Madvoc action: " + actionInfo.actionPath + " to: " +
					actionInfo.getClassMethod());
		}

		ActionPathInfo actionPathInfo = createActionPathInfo(actionInfo.actionPath);

		if (actionPathInfo.pathMatcher != null) {
			// new action patch contain macros
			int ndx = -1;
			for (int i = 0; i < pathInfosWithMatcher.size(); i++) {
				if (pathInfosWithMatcher.get(i).actionPath.equals(actionPath)) {
					ndx = i;
					break;
				}
			}
			if (ndx < 0) {
				pathInfosWithMatcher.add(actionPathInfo);
			} else {
				actionPathInfo = pathInfosWithMatcher.get(ndx);
			}
		} else {
			// action path is without macros
			if (!pathInfosNotWithMatcher.containsKey(actionInfo.actionPath)) {
				pathInfosNotWithMatcher.put(actionInfo.actionPath, actionPathInfo);
			} else {
				actionPathInfo = pathInfosNotWithMatcher.get(actionInfo.actionPath);
			}

		}
		boolean isDuplicate = actionPathInfo.add(actionInfo);

		if (madvocConfig.isDetectDuplicatePathsEnabled()) {
			if (isDuplicate) {
				throw new MadvocException("Duplicate action path for " + actionInfo);
			}
		}

		// finally

		this.actionInfos.put(actionInfo.getClassMethod(), actionInfo);

		if (!isDuplicate) {
			actionsCount++;
		}

		// async check
		if (actionInfo.isAsync()) {
			asyncMode = true;
		}

		return actionInfo;
	}

	/**
	 * Creates new action config set from the action path.
	 */
	protected ActionPathInfo createActionPathInfo(String actionPath) {
		PathMatcher pathMatcher = actionPathMacroManager.buildActionPathMatcher(actionPath);

		return new ActionPathInfo(actionPath, pathMatcher);
	}

	// ---------------------------------------------------------------- look-up

	/**
	 * Returns action configurations for provided action path.
	 * First it lookups for exact <code>actionPath</code>.
	 * If action path is not registered, it is split into chunks
	 * and match against macros.
	 * Returns <code>null</code> if action path is not registered.
	 * <code>method</code> must be in uppercase.
	 */
	public ActionInfo lookup(String actionPath, String method) {

		// 1st try: the map
//----------------------通过普通的没有通配符的路径查询actinpathinfo,
		ActionPathInfo actionPathInfo = pathInfosNotWithMatcher.get(actionPath);
		if (actionPathInfo != null) {
			ActionInfo actionInfo = actionPathInfo.lookup(method);
			if (actionInfo != null) {
				return actionInfo;
			}
		}

		// 2nd try: the list

		int actionPathDeep = StringUtil.count(actionPath, '/');

		int len = pathInfosWithMatcher.size();

		int lastMatched = -1;
		int maxMatchedChars = -1;
//-------------------根据matcher查找
		for (int i = 0; i < len; i++) {
			actionPathInfo = pathInfosWithMatcher.get(i);

			int deep = actionPathInfo.deep;
			//------------"/"个数不对先排除
			if (deep < actionPathDeep) {
				continue;
			}
			if (deep > actionPathDeep) {
				break;
			}

			// same deep level, try the fully match
//-------------------------与deep相同的开始比较path
			int matchedChars = actionPathInfo.pathMatcher.match(actionPath);

			if (matchedChars == -1) {
				continue;
			}
//-----------------------找到匹配最多的
			if (matchedChars > maxMatchedChars) {
				maxMatchedChars = matchedChars;
				lastMatched = i;
			}
		}

		if (lastMatched < 0) {
			return null;
		}

		ActionPathInfo set = pathInfosWithMatcher.get(lastMatched);

		return set.lookup(method);
	}

	/**
	 * Lookups action config for given action class and method string (aka 'action string').
	 * The action string has the following format: <code>className#methodName</code>.
	 * @see ActionInfo#getClassMethod()
	 */
	public ActionInfo lookup(String actionString) {
		return actionInfos.get(actionString);
	}

	// ---------------------------------------------------------------- aliases

	/**
	 * Registers new path alias.
	 */
	public void registerPathAlias(String alias, String path) {
		pathAliases.put(alias, path);
	}

	/**
	 * Returns path alias.
	 */
	public String lookupPathAlias(String alias) {
		return pathAliases.get(alias);
	}

}