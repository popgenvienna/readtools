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

import htsjdk.samtools.SAMException;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.util.FastqQualityFormat;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.vetmeduni.io.FastqPairedRecord;
import org.vetmeduni.io.readers.paired.FastqReaderPairedImpl;
import org.vetmeduni.io.readers.paired.FastqReaderPairedInterface;
import org.vetmeduni.io.readers.paired.FastqReaderPairedSanger;
import org.vetmeduni.io.readers.single.FastqReaderSingleInterface;
import org.vetmeduni.io.readers.single.FastqReaderSingleSanger;
import org.vetmeduni.io.readers.single.FastqReaderWrapper;
import org.vetmeduni.io.writers.PairFastqWriters;
import org.vetmeduni.io.writers.ReadToolsFastqWriterFactory;
import org.vetmeduni.methods.trimming.MottAlgorithm;
import org.vetmeduni.methods.trimming.TrimmingStats;
import org.vetmeduni.tools.AbstractTool;
import org.vetmeduni.tools.defaults.CommonOptions;
import org.vetmeduni.utils.fastq.FastqLogger;

import java.io.File;
import java.io.IOException;

/**
 * Class that implements the trimming algorithm from Kofler et al. 2011
 *
 * @author Daniel Gómez-Sánchez
 */
public class TrimFastq extends AbstractTool {

	/**
	 * The default quality score
	 */
	private static int DEFAULT_QUALTITY_SCORE = 20;

	/**
	 * The default minimum length
	 */
	private static int DEFAULT_MINIMUM_LENGTH = 40;

	@Override
	public int run(String[] args) {
		try {
			// PARSING THE COMMAND LINE
			CommandLine cmd = programParser(args);
			// The input file
			File input1 = new File(cmd.getOptionValue("input1"));
			// input file 2
			File input2 = (cmd.hasOption("input2")) ? new File(cmd.getOptionValue("input2")) : null;
			// The output prefix
			String output_prefix = cmd.getOptionValue("output");
			// qualityThreshold
			int qualThreshold;
			try {
				qualThreshold = (cmd.hasOption("quality-threshold")) ?
					Integer.parseInt(cmd.getOptionValue("quality-threshold")) :
					DEFAULT_QUALTITY_SCORE;
				if (qualThreshold < 0) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException e) {
				throw new ParseException("Quality threshold should be a positive integer");
			}
			// minimum length
			int minLength;
			try {
				minLength = (cmd.hasOption("min-length")) ?
					Integer.parseInt(cmd.getOptionValue("min-length")) :
					DEFAULT_MINIMUM_LENGTH;
				if (minLength < 1) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException e) {
				throw new ParseException("Minimum length should be a positive integer");
			}
			boolean discardRemainingNs = cmd.hasOption("discard-internal-N");
			boolean trimQuality = !cmd.hasOption("no-trim-quality");
			boolean no5ptrim = cmd.hasOption("no-5p-trim");
			boolean verbose = !cmd.hasOption("quiet");
			// start the new factory
			ReadToolsFastqWriterFactory factory = new ReadToolsFastqWriterFactory();
			// set the gzipped option in the factory
			factory.setGzipOutput(!cmd.hasOption(CommonOptions.disableZippedOutput.getOpt()));
			// multi-thread?
			int multi = CommonOptions.numberOfThreads(cmd);
			if (multi != 1) {
				factory.setUseAsyncIo(true);
				logger.warn("Multi-threads is only in the output. Not real parallelization implemented yet.");
			}
			// FINISH PARSING: log the command line (not longer in the param file)
			logCmdLine(args);
			// create the MottAlgorithm
			MottAlgorithm trimming = new MottAlgorithm(trimQuality, qualThreshold, minLength, discardRemainingNs,
				no5ptrim);
			// if input 2 process Pair end
			if (input2 != null) {
				logger.info("Found an existing file for the second read; Switching to paired-read mode");
				// open the reader
				FastqReaderPairedInterface reader;
				if (CommonOptions.isMaintained(logger, cmd)) {
					reader = new FastqReaderPairedImpl(input1, input2);
					logger.info("Output will be in ",
						(reader.getFastqQuality().equals(FastqQualityFormat.Standard)) ? "'sanger'" : "'illumina'");
				} else {
					reader = new FastqReaderPairedSanger(input1, input2);
				}
				// open the writer
				PairFastqWriters writer = factory.newPairWriter(output_prefix);
				processPE(trimming, reader, writer, verbose);
				// if not, single end mode
			} else {
				logger.info("Did not find an existing file for the second read; Switching to single-read mode");
				// open the reader
				FastqReaderSingleInterface reader;
				if (cmd.hasOption("nstd")) {
					reader = new FastqReaderWrapper(input1);
					logger.warn(
						"Output will not be standardize. Does not provide the option -nstd to avoid this behaviour");
					logger.info("Output will be in ",
						(reader.getFastqQuality().equals(FastqQualityFormat.Standard)) ? "'sanger'" : "'illumina'");
				} else {
					logger.info("Output will be in Sanger format independently of the input format");
					reader = new FastqReaderSingleSanger(input1);
				}
				// open the writer for single end
				FastqWriter writer = factory.newWriter(output_prefix);
				processSE(trimming, reader, writer, verbose);
			}
		} catch (ParseException e) {
			// This exceptions comes from the command line parsing
			printUsage(e.getMessage());
			return 1;
		} catch (IOException | SAMException e) {
			// this are expected errors: IO if the user provides bad inputs, SAM if there are problems in the files
			logger.error(e.getMessage());
			logger.debug(e);
			return 1;
		} catch (Exception e) {
			// unexpected exceptions return a different error code
			logger.debug(e);
			logger.error(e.getMessage());
			return 2;
		}
		return 0;
	}

	/**
	 * Process the files in pair-end mode
	 *
	 * @param trimming the algorithm with the provided settings
	 * @param reader   the reader for the pairs
	 * @param writer   the writer for the pairs
	 * @param verbose  should we be verbose?
	 *
	 * @throws IOException if there are problems with the files
	 */
	private void processPE(MottAlgorithm trimming, FastqReaderPairedInterface reader, PairFastqWriters writer,
		boolean verbose) throws IOException {
		// creating progress
		FastqLogger progress = new FastqLogger(logger, 1000000, "Processed", "read-pairs");
		TrimmingStats stats1 = null;
		TrimmingStats stats2 = null;
		int paired = 0;
		int single = 0;
		if (verbose) {
			stats1 = new TrimmingStats();
			stats2 = new TrimmingStats();
		}
		while (reader.hasNext()) {
			FastqPairedRecord record = reader.next();
			FastqRecord newRecord1 = trimming.trimFastqRecord(record.getRecord1(), reader.getFastqQuality(), stats1);
			FastqRecord newRecord2 = trimming.trimFastqRecord(record.getRecord2(), reader.getFastqQuality(), stats2);
			if (newRecord1 != null && newRecord2 != null) {
				writer.write(new FastqPairedRecord(newRecord1, newRecord2));
				paired++;
			} else if (newRecord1 != null) {
				writer.write(newRecord1);
				single++;
			} else if (newRecord2 != null) {
				writer.write(newRecord2);
				single++;
			}
			progress.add();
		}
		logger.info(progress.numberOfVariantsProcessed());
		if (verbose) {
			System.out.print("Read-pairs processed: ");
			System.out.println(progress.getCount());
			System.out.print("Read-pairs trimmed in pairs: ");
			System.out.println(paired);
			System.out.print("Read-pairs trimmed as singles: ");
			System.out.println(single);
			System.out.println("\n");
			System.out.println("FIRST READ STATISTICS");
			stats1.report(System.out);
			System.out.println("\n");
			System.out.println("SECOND READ STATISTICS");
			stats2.report(System.out);
		}
		reader.close();
		writer.close();
	}

	/**
	 * Process the files in single-end mode
	 *
	 * @param trimming the algorithm with the provided settings
	 * @param reader   the reader for the single end file
	 * @param writer   the writer for the single end file
	 * @param verbose  should we be verbose?
	 *
	 * @throws IOException if there are problems with the files
	 */
	private void processSE(MottAlgorithm trimming, FastqReaderSingleInterface reader, FastqWriter writer,
		boolean verbose) throws IOException {
		FastqLogger progress = null;
		TrimmingStats stats = null;
		progress = new FastqLogger(logger);
		if (verbose) {
			stats = new TrimmingStats();
		}
		while (reader.hasNext()) {
			FastqRecord record = reader.next();
			FastqRecord newRecord = trimming.trimFastqRecord(record, reader.getFastqQuality(), stats);
			if (newRecord != null) {
				writer.write(newRecord);
			}
			progress.add();
			writer.write(newRecord);
		}
		logger.info(progress.numberOfVariantsProcessed());
		if (verbose) {
			System.out.print("Read processed: ");
			System.out.println(progress.getCount());
			System.out.println("\n");
			System.out.println("READ STATISTICS");
			stats.report(System.out);
		}
		reader.close();
		writer.close();
	}

	@Override
	protected Options programOptions() {
		// Creating each options
		Option input1 = Option.builder("i1").longOpt("input1")
							  .desc("The FASTQ input file, or the input file of the first read").hasArg()
							  .numberOfArgs(1).argName("input_1.fq").required(true).build();
		Option input2 = Option.builder("i2").longOpt("input2").desc(
			"The FASTQ input file of the second read. In case this file is provided the software will switch to paired read mode instead of single read mode")
							  .hasArg().numberOfArgs(1).argName("input_2.fq").optionalArg(true).build();
		Option output = Option.builder("o").longOpt("output")
							  .desc("The output file prefix. Will be in fastq. Mandatory parameter").hasArg()
							  .numberOfArgs(1).argName("output_prefix").required(true).build();
		Option quality_threshold = Option.builder("q").longOpt("quality-threshold").desc(
			"Minimum average quality. A modified Mott algorithm is used for trimming, and the threshold is used for calculating a score: quality_at_base - threshold. [Default="
				+ DEFAULT_QUALTITY_SCORE + "]").hasArg().numberOfArgs(1).argName("INT").optionalArg(true).build();
		Option discard_internal_N = Option.builder("N").longOpt("discard-internal-N")
										  .desc("If set reads having internal Ns will be discarded").hasArg(false)
										  .optionalArg(true).build();
		Option min_length = Option.builder("m").longOpt("minimum-length").desc(
			"The minimum length of the read after trimming. [Default=" + DEFAULT_MINIMUM_LENGTH + "]").hasArg()
								  .numberOfArgs(1).argName("INT").optionalArg(true).build();
		Option no_trim_qual = Option.builder("nq").longOpt("no-trim-quality").desc("Switch off quality trimming")
									.hasArg(false).optionalArg(false).build();
		Option no_5p_trim = Option.builder("n5p").longOpt("no-5p-trim").desc(
			"Disable 5'-trimming (quality and 'N'); May be useful for the identification of duplicates when using trimming of reads. Duplicates are usually identified by the 5' mapping position which should thus not be modified by trimming")
								  .hasArg(false).optionalArg(true).build();
		Option quiet = Option.builder("s").longOpt("quiet").desc("Suppress output to console").optionalArg(false)
							 .build();
		Options options = new Options();
		options.addOption(quiet);
		options.addOption(no_5p_trim);
		options.addOption(no_trim_qual);
		options.addOption(min_length);
		options.addOption(discard_internal_N);
		options.addOption(quality_threshold);
		options.addOption(output);
		options.addOption(input2);
		options.addOption(input1);
		// adding common options
		options.addOption(CommonOptions.maintainFormat); // maintain the format
		options.addOption(CommonOptions.disableZippedOutput); // disable the zipped output
		options.addOption(CommonOptions.parallel); // parallelization allowed
		return options;
	}
}
