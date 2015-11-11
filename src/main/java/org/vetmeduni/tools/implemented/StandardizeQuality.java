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
package org.vetmeduni.tools.implemented;

import htsjdk.samtools.*;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.vetmeduni.io.readers.single.FastqReaderSingleSanger;
import org.vetmeduni.io.readers.single.FastqReaderSingleInterface;
import org.vetmeduni.tools.AbstractTool;
import org.vetmeduni.utils.IOUtils;
import org.vetmeduni.utils.fastq.FastqLogger;
import org.vetmeduni.utils.fastq.ProgressLoggerExtension;
import org.vetmeduni.utils.fastq.QualityUtils;
import org.vetmeduni.utils.record.SAMRecordUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static org.vetmeduni.utils.concurrent.Defaults.BUFFER_SIZE_FACTOR;

/**
 * Class for converting from Illumina to Sanger encoding both FASTQ and BAM files
 *
 * TODO: implement correctly the multi-thread; now it's taking twice the normal implementation
 *
 * TODO: document
 *
 * @author Daniel Gómez-Sánchez
 */
public class StandardizeQuality extends AbstractTool {

	/**
	 * The default number of threads
	 */
	private static int DEFAULT_THREADS = 1;

	@Override
	public int run(String[] args) {
		try {
			CommandLine cmd = programParser(args);
			File input = new File(cmd.getOptionValue("i"));
			File output = new File(cmd.getOptionValue("o"));
			boolean index = cmd.hasOption("ind");
			int threads = (cmd.hasOption("nt")) ? Integer.parseInt(cmd.getOptionValue("nt")) : DEFAULT_THREADS;
			logCmdLine(args);
			// first check the quality
			switch (QualityUtils.getFastqQualityFormat(input)) {
				case Standard:
					logger.error("File is already in Sanger formatting. No conversion will be performed");
					return 1;
				default:
					break;
			}
			if (IOUtils.isBamOrSam(input)) {
				runBam(input, output, index, threads);
			} else {
				if (index) {
					logger.warn("Index could not be performed for FASTQ file");
				}
				runFastq(input, output, threads);
			}
		} catch (IOException e) {
			logger.info(e.getMessage());
			logger.debug(e);
			return 1;
		} catch (Exception e) {
			logger.debug(e);
			return 2;
		}
		return 0;
	}

	private void runBam(File input, File output, boolean index, int nThreads) throws Exception {
		// Open readers and writers
		SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(input);
		SAMFileWriter writer = new SAMFileWriterFactory().setCreateIndex(index)
														 .makeSAMOrBAMWriter(reader.getFileHeader(),
															 reader.getFileHeader().getSortOrder()
																 == SAMFileHeader.SortOrder.coordinate, output);
		// run
		if (nThreads == 1) {
			runBam(reader, writer);
		} else {
			runBamMulti(reader, writer, nThreads);
		}
		// close readers and writers
		reader.close();
		writer.close();
	}

	/**
	 * TODO: document
	 *
	 * @param input
	 * @param output
	 * @param nThreads
	 *
	 * @throws Exception
	 */
	private void runFastq(File input, File output, int nThreads) throws Exception {
		// open reader and factory
		FastqReaderSingleInterface reader = new FastqReaderSingleSanger(input);
		FastqWriterFactory factory = new FastqWriterFactory();

		// run
		if (nThreads != 1) {
			logger.warn("Multi-thread output does not mean multi-thread processing");
			factory.setUseAsyncIo(true);
		}
		FastqWriter writer = factory.newWriter(output);
		runFastq(reader, writer);
		// close the readers and writers
		reader.close();
		writer.close();
	}

	/**
	 * TODO: document
	 *
	 * @param reader
	 * @param writer
	 *
	 * @throws Exception
	 */
	private void runFastq(FastqReaderSingleInterface reader, FastqWriter writer) throws Exception {
		// start iterations
		FastqLogger progress = new FastqLogger(logger);
		for (FastqRecord record : reader) {
			writer.write(record);
			progress.add();
		}
		logger.info(progress.numberOfVariantsProcessed());
	}

	/**
	 * TODO: document
	 *
	 * @param reader
	 * @param writer
	 *
	 * @throws IOException
	 */
	private void runBam(SamReader reader, SAMFileWriter writer) throws Exception {
		// start iterations
		ProgressLoggerExtension progress = new ProgressLoggerExtension(logger);
		for (SAMRecord record : reader) {
			BAMtoSanger job = new BAMtoSanger(record, progress);
			writer.addAlignment(job.call());
		}
		logger.info(progress.numberOfVariantsProcessed());
	}

	/**
	 * TODO: document
	 *
	 * @param reader
	 * @param writer
	 * @param nThreads
	 */
	private void runBamMulti(SamReader reader, SAMFileWriter writer, int nThreads) {
		// open the executor
		final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(nThreads);
		// the buffer size for the executor is set to twice the number of threads
		final int bufferSize = nThreads * BUFFER_SIZE_FACTOR;
		Collection<Callable<SAMRecord>> jobs = new ArrayList<>();
		// start iterations
		ProgressLoggerExtension progress = new ProgressLoggerExtension(logger);
		for (SAMRecord record : reader) {
			jobs.add(new BAMtoSanger(record, progress));
			if (jobs.size() >= bufferSize) {
				// run all and empty the list
				try {
					List<Future<SAMRecord>> result = executor.invokeAll(jobs);
					for (Future<SAMRecord> future : result) {
						SAMRecord newRecord = future.get();
						writer.addAlignment(newRecord);
					}
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e.getMessage());
				}
				jobs.clear();
			}
		}
		logger.debug("Jobs: " + jobs.size(), ". Terminated: ", executor.isTerminated());
		// run the remaining jobs if they are not added
		if (jobs.size() != 0) {
			if (jobs.size() >= bufferSize) {
				// run all and empty the list
				try {
					List<Future<SAMRecord>> result = executor.invokeAll(jobs);
					for (Future<SAMRecord> future : result) {
						SAMRecord newRecord = future.get();
						writer.addAlignment(newRecord);
					}
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e.getMessage());
				}
			}
		}
		logger.info(progress.numberOfVariantsProcessed());
	}

	/**
	 * TODO: document
	 */
	private static class BAMtoSanger implements Callable<SAMRecord> {

		private final SAMRecord record;

		private final ProgressLoggerExtension progress;

		public BAMtoSanger(SAMRecord record, ProgressLoggerExtension progress) {
			this.record = record;
			this.progress = progress;
		}

		@Override
		public SAMRecord call() throws Exception {
			SAMRecord toReturn = SAMRecordUtils.copyToSanger(record);
			progress.record(toReturn);
			return toReturn;
		}
	}

	@Override
	protected Options programOptions() {
		Option input = Option.builder("i").longOpt("input").desc("Input BAM/FASTQ to standardize the quality").hasArg()
							 .numberOfArgs(1).argName("INPUT").required().build();
		Option output = Option.builder("o").longOpt("output").desc(
			"Output for the coverted file. The extension determine the format SAM/BAM or FASTQ/GZIP").hasArg()
							  .numberOfArgs(1).argName("OUTPUT").required().build();
		Option index = Option.builder("ind").longOpt("index").desc("If the output is a BAM file, index it")
							 .hasArg(false).required(false).build();
//		Option parallel = Option.builder("nt").longOpt("number-of-thread")
//								.desc("Specified the number of threads to use. [Default=" + DEFAULT_THREADS + "]")
//								.hasArg().numberOfArgs(1).argName("INT").optionalArg(true).build();
		Options options = new Options();
		options.addOption(input);
		options.addOption(output);
		options.addOption(index);
//		options.addOption(parallel);
		return options;
	}
}