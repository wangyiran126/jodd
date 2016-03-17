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

package jodd.madvoc.macro;

import jodd.util.StringPool;
import jodd.util.StringUtil;

/**
 * Common class for <code>PathMacro</code> implementations.
 * Assume that macros are defined in the following way:
 * <code>prefix${name:pattern}suffix</code>.
 * Pattern is optional, and if missing all values are matched.
 */
public abstract class BasePathMatcher implements PathMatcher {
//-------------------prefix有几个
	protected int prefixCountInPath;
	//names为每个prefix, suffix中间的内容
	protected String[] matchContents;		// macros names
	protected String[] patterns;	// macros patterns, if defined, elements may be null
	//fixed为prefix, suffix前面的内容,最后为suffix后面内容
	protected String[] fixed;		// array of fixed strings surrounding macros

	/**
	 * {@inheritDoc}
	 */
	public boolean init(final String actionPath, String[] separators) {
		String prefix = separators[0];
		String split = separators[1];
		String suffix = separators[2];
//-----------------计算"${"在actionPath路径中的个数
		prefixCountInPath = StringUtil.count(actionPath, prefix);

		if (prefixCountInPath == 0) {
			return false;
		}

		matchContents = new String[prefixCountInPath];
		patterns = new String[prefixCountInPath];
		fixed = new String[prefixCountInPath + 1];

		int afterSuffixIndex = 0;
		int i = 0;

		while (true) {
		//ndx[0]为prefix所占索引,ndx[1]为prefix之后的字符索引,ndx[2]为suffix索引,ndx[3]为为suffix之后的字符索引
			int[] ndx = StringUtil.indexOfRegion(actionPath, prefix, suffix, afterSuffixIndex);

			if (ndx == null) {
				break;
			}
		//---------------fix[0]为prefix前面的字符
			fixed[i] = actionPath.substring(afterSuffixIndex, ndx[0]);
		//--------name为actionPath路径,prefix,suffix中间的内容
			String content = actionPath.substring(ndx[1], ndx[2]);

			// name:pattern
			String pattern = null;

			int colonNdx = content.indexOf(split);
			if (colonNdx != -1) {
				pattern = content.substring(colonNdx + 1).trim();

				content = content.substring(0, colonNdx).trim();
			}

			this.patterns[i] = pattern;
			this.matchContents[i] = content;
			//-----------offset为suffix之后的索引
			// iterate
			afterSuffixIndex = ndx[3];
			i++;
		}

		if (afterSuffixIndex < actionPath.length()) {
			fixed[i] = actionPath.substring(afterSuffixIndex);
		} else {
			fixed[i] = StringPool.EMPTY;
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public String[] getMatchContents() {
		return matchContents;
	}

	/**
	 * {@inheritDoc}
	 */
	public String[] getPatterns() {
		return patterns;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getPrefixCountInPath() {
		return prefixCountInPath;
	}

	/**
	 * {@inheritDoc}
	 */
	public int match(String actionPath) {
		String[] matchContents = process(actionPath, true);

		if (matchContents == null) {
			return -1;
		}

		int macroChars = 0;
		for (String value : matchContents) {
			if (value != null) {
				macroChars += value.length();
			}
		}

		return actionPath.length() - macroChars;
	}

	/**
	 * Matches the stripped value with the pattern.
	 * Pattern at provided <code>macroIndex</code> is not <code>null</code>!
	 */
	protected abstract boolean matchValue(int macroIndex, String value);

	/**
	 * {@inheritDoc}
	 */
	public String[] extract(String actionPath) {
		return process(actionPath, false);
	}


	// ---------------------------------------------------------------- common

	/**
	 * Process action path in two modes: matching mode and extracting mode.
	 * @return string array of extracted macro values (null element is allowed) or null
	 */
	private String[] process(String actionPath, boolean match) {
		// first check the first fixed as a prefix
		if (match && !actionPath.startsWith(fixed[0])) {
			return null;
		}

		String[] values = new String[prefixCountInPath];
//---------------排除prefix之前内容的偏移量
		int beforePrefixStrLength = fixed[0].length();
		int i = 0;

		while (i < prefixCountInPath) {
			//-----------------当前匹配次数
			int currentMatchCount = i;

			// defines next fixed string to match
			String nextFixed;
			while (true) {
				currentMatchCount++;
				if (currentMatchCount > prefixCountInPath) {
					nextFixed = null;	// match to the end of line
					break;
				}
				nextFixed = fixed[currentMatchCount];
				if (nextFixed.length() != 0) {
					break;
				}
				// next fixed is an empty string, so skip the next macro.
			}

			// find next fixed string
			int matchContentLength;

			if (nextFixed != null) {
				matchContentLength = actionPath.indexOf(nextFixed, beforePrefixStrLength);
			} else {
				matchContentLength = actionPath.length();
			}

			if (matchContentLength == -1) {
				return null;
			}

			String matchContent = actionPath.substring(beforePrefixStrLength, matchContentLength);
			values[i] = matchContent;

			if (match && patterns[i] != null) {
				if (!matchValue(i, matchContent)) {
					return null;
				}
			}

			if (nextFixed == null) {
				beforePrefixStrLength = matchContentLength;
				break;
			}

			// iterate
			int nextFixedLength = nextFixed.length();
			beforePrefixStrLength = matchContentLength + nextFixedLength;

			i = currentMatchCount;
		}

		if (beforePrefixStrLength != actionPath.length()) {
			// action path is not consumed fully during this matching
			return null;
		}

		return values;
	}

}