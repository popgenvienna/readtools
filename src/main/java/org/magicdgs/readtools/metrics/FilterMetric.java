/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gómez-Sánchez
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
 * SOFTWARE.
 */

package org.magicdgs.readtools.metrics;

import htsjdk.samtools.metrics.MetricBase;

/**
 * Holds summary statistics for filtering processing.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class FilterMetric extends MetricBase {

    /** Name of the filter applied to the reads. */
    public String FILTER;

    /** Total number reads reaching the filter. */
    public int TOTAL = 0;

    /** Number of reads passing the filter. */
    public int PASSED = 0;

    /** Constructor for unknown filter name. */
    public FilterMetric() {
        this("UNKNOWN");
    }

    /** Constructor for default filter name. */
    public FilterMetric(final String filter) {
        this.FILTER = filter;
    }
}