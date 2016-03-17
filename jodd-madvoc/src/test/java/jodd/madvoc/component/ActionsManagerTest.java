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

import jodd.madvoc.ActionInfo;
import jodd.madvoc.ActionDef;
import jodd.madvoc.WebApplication;
import jodd.madvoc.macro.RegExpPathMatcher;
import jodd.madvoc.macro.WildcardPathMatcher;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ActionsManagerTest {

	public static class FooAction {
		public void one() {
		}
		public void two() {
		}
		public void three() {
		}
	}

	@Test
	public void testActionPathMacros1() {
		WebApplication webapp = new WebApplication(true);
		webapp.registerMadvocComponents();
		ActionsManager actionsManager = webapp.getComponent(ActionsManager.class);

		actionsManager.register(FooAction.class, "one", new ActionDef("/${one}"));

		ActionInfo actionInfo = actionsManager.lookup("/foo", null);
		assertNotNull(actionInfo);

		actionInfo = actionsManager.lookup("/foo/boo", null);
		assertNull(actionInfo);
		actionInfo = actionsManager.lookup("/foo/boo/zoo", null);
		assertNull(actionInfo);
	}

	@Test
	public void testActionPathMacros2() {
		WebApplication webapp = new WebApplication(true);
		webapp.registerMadvocComponents();
		ActionsManager actionsManager = webapp.getComponent(ActionsManager.class);

		actionsManager.register(FooAction.class, "one", new ActionDef("/${one}"));
		actionsManager.register(FooAction.class, "two", new ActionDef("/xxx-${two}"));

		ActionInfo actionInfo = actionsManager.lookup("/foo", null);
		assertEquals("one", actionInfo.actionClassMethod.getName());

		actionInfo = actionsManager.lookup("/foo/boo", null);
		assertNull(actionInfo);

		actionInfo = actionsManager.lookup("/xxx-foo", null);
		assertEquals("two", actionInfo.actionClassMethod.getName());	// best match!

	}

	@Test
	public void testActionPathMacros3() {
		WebApplication webapp = new WebApplication(true);
		webapp.registerMadvocComponents();
		ActionsManager actionsManager = webapp.getComponent(ActionsManager.class);

		actionsManager.register(FooAction.class, "one", new ActionDef("/yyy-${one}"));
		actionsManager.register(FooAction.class, "two", new ActionDef("/xxx-${two}"));

		assertEquals(2, actionsManager.getActionsCount());

		ActionInfo actionInfo = actionsManager.lookup("/foo", null);
		assertNull(actionInfo);

		actionInfo = actionsManager.lookup("/yyy-111", null);
		assertEquals("one", actionInfo.actionClassMethod.getName());

		actionInfo = actionsManager.lookup("/xxx-222", null);
		assertEquals("two", actionInfo.actionClassMethod.getName());

		try {
			actionsManager.register(FooAction.class, "two", new ActionDef("/xxx-${two}"));
			Assert.fail();
		} catch (Exception ex) {
			// ignore
		}
	}

	@Test
	public void testActionPathMacros4() {
		WebApplication webapp = new WebApplication(true);
		webapp.registerMadvocComponents();
		ActionsManager actionsManager = webapp.getComponent(ActionsManager.class);

		actionsManager.register(FooAction.class, "one", new ActionDef("/${one}"));
		actionsManager.register(FooAction.class, "one", new ActionDef("/dummy"));		// no macro
		actionsManager.register(FooAction.class, "two", new ActionDef("/${two}/${three}"));
		actionsManager.register(FooAction.class, "three", new ActionDef("/life/${three}"));

		ActionInfo actionInfo = actionsManager.lookup("/foo", null);
		assertEquals("one", actionInfo.actionClassMethod.getName());

 		actionInfo = actionsManager.lookup("/scott/ramonna", null);
		assertEquals("two", actionInfo.actionClassMethod.getName());

		actionInfo = actionsManager.lookup("/life/universe", null);
		assertEquals("three", actionInfo.actionClassMethod.getName());

		actionInfo = actionsManager.lookup("/scott/ramonna/envy", null);
		assertNull(actionInfo);

		actionInfo = actionsManager.lookup("/life/universe/else", null);
		assertNull(actionInfo);
	}

	@Test
	public void testActionPathMacrosRegexp() {
		WebApplication webapp = new WebApplication(true);
		webapp.registerMadvocComponents();
		ActionsManager actionsManager = webapp.getComponent(ActionsManager.class);

		MadvocConfig madvocConfig = webapp.getComponent(MadvocConfig.class);
		madvocConfig.setPathMacroClass(RegExpPathMatcher.class);

		actionsManager.register(FooAction.class, "one", new ActionDef("/${one:[ab]+}"));

		ActionInfo actionInfo = actionsManager.lookup("/a", null);
		assertNotNull(actionInfo);

		actionInfo = actionsManager.lookup("/ac", null);
		assertNull(actionInfo);
	}

	@Test
	public void testActionPathMacrosWildcard() {
		WebApplication webapp = new WebApplication(true);
		webapp.registerMadvocComponents();
		ActionsManager actionsManager = webapp.getComponent(ActionsManager.class);

		MadvocConfig madvocConfig = webapp.getComponent(MadvocConfig.class);
		madvocConfig.setPathMacroClass(WildcardPathMatcher.class);

		actionsManager.register(FooAction.class, "one", new ActionDef("/${one:a?a}"));

		ActionInfo actionInfo = actionsManager.lookup("/aaa", null);
		assertNotNull(actionInfo);

		actionInfo = actionsManager.lookup("/aab", null);
		assertNull(actionInfo);
	}
}
