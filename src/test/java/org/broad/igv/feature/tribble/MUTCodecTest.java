/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.feature.tribble;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.feature.Mutation;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.TestUtils;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author jrobinso
 * Date: 4/8/13
 * Time: 2:51 PM
 */
public class MUTCodecTest extends AbstractHeadlessTest {


    @Test
    public void testSampleMeta() throws Exception {

        String path = TestUtils.DATA_DIR + "maf/TCGA_GBM_Level3_Somatic_Mutations_08.28.2008.maf";
        assertTrue(MUTCodec.isMutationAnnotationFile(new ResourceLocator(path)));

        MUTCodec codec = new MUTCodec(path, genome);
        int expectedSampleCount = 146;
        String[] samples = codec.getSamples();
        assertEquals(expectedSampleCount, samples.length);
        assertTrue(samples[0].startsWith("TCGA"));
    }

    @Test
    public void testBroadMaf() throws Exception {
        String path = TestUtils.DATA_DIR + "maf/test.maf";
        assertTrue(MUTCodec.isMutationAnnotationFile(new ResourceLocator(path)));

        MUTCodec codec = new MUTCodec(path, genome);

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(path));
            String nextLine;
            ArrayList<Mutation> mutations = new ArrayList<>();
            while ((nextLine = bufferedReader.readLine()) != null) {

                Mutation mut = codec.decode(nextLine);
                if (mut != null) {
                    mutations.add(mut);
                }
            }
            assertEquals(29, mutations.size());
        } finally {
            bufferedReader.close();
        }
    }

    @Test
    public void testTcgaMaf() throws Exception {
        String path = TestUtils.DATA_DIR + "maf/tcga_test.maf";
        assertTrue(MUTCodec.isMutationAnnotationFile(new ResourceLocator(path)));

        MUTCodec codec = new MUTCodec(path, genome);

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(path));
            String nextLine;
            ArrayList<Mutation> mutations = new ArrayList<>();
            while ((nextLine = bufferedReader.readLine()) != null) {

                Mutation mut = codec.decode(nextLine);
                if (mut != null) {
                    mutations.add(mut);
                }
            }
            assertEquals(17, mutations.size());
        } finally {
            bufferedReader.close();
        }
    }

}
