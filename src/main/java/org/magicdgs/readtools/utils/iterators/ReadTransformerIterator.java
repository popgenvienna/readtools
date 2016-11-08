/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Daniel Gómez-Sánchez
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

package org.magicdgs.readtools.utils.iterators;

import org.broadinstitute.hellbender.transformers.ReadTransformer;
import org.broadinstitute.hellbender.utils.Utils;
import org.broadinstitute.hellbender.utils.read.GATKRead;

import java.util.Iterator;

/**
 * An iterator that transform reads from an existing iterator of reads.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
// TODO: this should be included at some point into GATK4
public class ReadTransformerIterator implements Iterator<GATKRead>, Iterable<GATKRead> {

    private final Iterator<GATKRead> nestedIterator;
    private final ReadTransformer readTransformer;

    /**
     * Create a ReadTransformerIterator given a pre-existing iterator of reads and a read
     * transformer. Transformed reads will pass this transformer.
     *
     * @param nestedIterator  underlying iterator from which to pull reads (may not be null)
     * @param readTransformer transformer function to apply to the reads (may not be null)
     */
    public ReadTransformerIterator(final Iterator<GATKRead> nestedIterator,
            final ReadTransformer readTransformer) {
        Utils.nonNull(nestedIterator, "null iterator");
        Utils.nonNull(readTransformer, "null transformer");
        this.nestedIterator = nestedIterator;
        this.readTransformer = readTransformer;
    }

    @Override
    public Iterator<GATKRead> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return nestedIterator.hasNext();
    }

    @Override
    public GATKRead next() {
        return readTransformer.apply(nestedIterator.next());
    }
}