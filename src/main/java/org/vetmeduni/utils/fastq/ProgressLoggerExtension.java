/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Daniel Gómez-Sánchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 */

package org.vetmeduni.utils.fastq;

import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.ProgressLogger;
import org.vetmeduni.utils.Formats;

import static org.vetmeduni.utils.Formats.timeFmt;

/**
 * Extension of {@link htsjdk.samtools.util.ProgressLogger}
 *
 * @author Daniel Gómez-Sánchez
 */
public class ProgressLoggerExtension extends ProgressLogger {

	public ProgressLoggerExtension(Log log, int n, String verb, String noun) {
		super(log, n, verb, noun);
	}

	public ProgressLoggerExtension(Log log, int n, String verb) {
		super(log, n, verb);
	}

	public ProgressLoggerExtension(Log log, int n) {
		super(log, n);
	}

	public ProgressLoggerExtension(Log log) {
		super(log);
	}

	/**
	 * Formats a number of seconds into hours:minutes:seconds.
	 */
	private String formatElapseTime(final long seconds) {
		final long s = seconds % 60;
		final long allMinutes = seconds / 60;
		final long m = allMinutes % 60;
		final long h = allMinutes / 60;
		return timeFmt.format(h) + ":" + timeFmt.format(m) + ":" + timeFmt.format(s);
	}

	public synchronized String numberOfVariantsProcessed() {
		final long seconds = getElapsedSeconds();
		final String elapsed = formatElapseTime(seconds);
		return String
			.format("%s %s %s. Elapsed time: %s", this.verb, Formats.commaFmt.format(getCount()), this.noun, elapsed);
	}
}