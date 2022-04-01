package us.conian;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class Main {
	
	private static final File DATA_FOLDER;
	private static final File DATA_RAW;
	
	static {
		try {
			DATA_FOLDER = new File(new File(Main.class.getProtectionDomain().getCodeSource().getLocation()
					.toURI().getPath()).getParent(), "data");
			if (!DATA_FOLDER.exists())
				DATA_FOLDER.mkdirs();
			DATA_RAW = new File(DATA_FOLDER, "raw");
			if (!DATA_RAW.exists())
				DATA_RAW.mkdirs();
		} catch(Exception e) {
			throw new InternalError("Failed to find or create cluster directory: " + e.getMessage());
		}
	}
	
	private static final DateTimeFormatter SAMPLE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	
	private static final String STOP = "stop";
	
	private static final long POLLING_INTERVAL = 2000L;//2 seconds

	public static void main(String[] args) throws IOException {
		LocalDateTime start = LocalDateTime.now();
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		int sampleInterval = determineSampleInterval(in, (args == null || args.length < 1 ? null : args[0]));
		int numSamples = determineNumSamples(in, (args == null || args.length < 2 ? null : args[1]));
		System.out.println("Enter 'stop' at any point to exit early");
		File outputRaw = File.createTempFile(SAMPLE_DATE_FORMAT.format(start), ".txt", DATA_RAW);
		Process counterProcess = CounterUtils.buildCounterProcess(sampleInterval, numSamples, outputRaw);
		boolean wasInterrupted = false;
		while (counterProcess.isAlive()) {
			try {
				Thread.sleep(POLLING_INTERVAL);
			} catch (InterruptedException unused) {
			}
			if (in.ready() && in.readLine().equalsIgnoreCase(STOP)) {
				System.out.println("Stopping early");
				counterProcess.destroyForcibly();
				wasInterrupted = true;
				break;
			}
			System.out.println("Working...");
		}
		
		Map<String, List<Sample>> samples = null;
		try (BufferedReader reader = new BufferedReader(new FileReader(outputRaw))) {
			List<String> rawData = reader.lines().toList();
			samples = CounterUtils.parseRaw(rawData, wasInterrupted);
			System.out.println("Done! Number of programs sampled: " + samples.size());
		} catch (IOException e) {
			System.out.println("Failed to parse samples file: " + e.getLocalizedMessage());
		}
		
		if (samples != null && !samples.isEmpty() && determineShouldSave(in)) {
			File directory = createSampleDirectory(in, start);
			for (Map.Entry<String, List<Sample>> entry : samples.entrySet()) {
				File output = createSampleFile(directory, entry.getKey());
				try (FileWriter writer = new FileWriter(output)) {
					for (Sample sample : entry.getValue()) {
						writer.write(SampleUtils.toCSVString(sample));
					}
				}
			}
			System.out.println("Saved all samples to " + directory.getAbsolutePath());
		}
		else {
			outputRaw.delete();
		}
		in.close();
		
		System.out.println("\nExiting...");
	}
	
	private static int determineSampleInterval(BufferedReader in, String arg) throws IOException {
		int sampleInterval = -1;
		if (arg != null)
			try {
				sampleInterval = Integer.parseInt(arg);
				if (sampleInterval < 1)
					System.err.println("Invalid sample interval argument (cannot be less than 1): \"" + arg + "\"");
			} catch (NumberFormatException unused) {
				System.err.println("Invalid sample interval argument (must be an integer): \"" + arg + "\"");
			}
		while (sampleInterval < 1) {
			System.out.print("Please specify the sample interval (the minimum time in seconds between each sample) > ");
			while (!in.ready()) { }
			String input = in.readLine();
			try {
				sampleInterval = Integer.parseInt(input);
				if (sampleInterval < 1)
					System.err.println("The sample interval must be a positive integer");
			} catch (NumberFormatException e1) {
				System.err.println("Uh-oh, it looks like you didn't enter an integer: \'"
						+ input + "\'");
			}
		}
		System.out.println("Using a sample interval of " + sampleInterval);
		return sampleInterval;
	}
	
	private static int determineNumSamples(BufferedReader in, String arg) throws IOException {
		int numSamples = 0;
		if (arg != null)
			try {
				numSamples = Integer.parseInt(arg);
				if (numSamples == 0)
					System.err.println("Invalid sample number argument (cannot be 0): \"" + arg + "\"");
			} catch (NumberFormatException unused) {
				System.err.println("Invalid sample number argument (must be an integer): \"" + arg + "\"");
			}
		while (numSamples == 0) {
			System.out.print("Please specify the number of samples to run (or a negative number to run samples continuously) > ");
			while (!in.ready()) { }
			String input = in.readLine();
			try {
				numSamples = Integer.parseInt(input);
				if (numSamples == 0)
					System.err.println("The number of samples cannot be 0");
			} catch (NumberFormatException e1) {
				System.err.println("Uh-oh, it looks like you didn't enter an integer: \'"
						+ input + "\'");
			}
		}
		System.out.println((numSamples < 1 ? "Gathering samples continuously" : "Gathering " + numSamples + " samples"));
		return numSamples;
	}
	
	private static boolean determineShouldSave(BufferedReader in) throws IOException {
		while (true) {
			System.out.print("Do you want to save the collected samples? (Y/n) > ");
			while (!in.ready()) { }
			String input = in.readLine();
			if (input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes"))
				return true;
			else if (input.equalsIgnoreCase("n") || input.equalsIgnoreCase("no"))
				return false;
			else System.err.println("Invalid response. Please answer with \"y\", \"yes\", \"n\", or \"no\"");
		}
	}

	private static File createSampleDirectory(BufferedReader in, LocalDateTime time) throws IOException {
		File sampleFolder = new File(DATA_FOLDER, SAMPLE_DATE_FORMAT.format(time));
		while (sampleFolder.exists() && sampleFolder.listFiles().length != 0) {
			System.err.println("The directory \"" + sampleFolder.getAbsolutePath() + "\" already exists and is not empty");
			System.out.print("Please enter a new directory name instead > ");
			while (!in.ready()) { }
			sampleFolder = new File(DATA_FOLDER, in.readLine());
		}
		sampleFolder.mkdirs();
		return sampleFolder;
	}
	
	private static File createSampleFile(File directory, String counterName) throws IOException {
		File sampleFile = new File(directory, counterName + CSVUtils.FILE_EXTENSION);
		if (sampleFile.exists()) {
			System.err.println("The file \"" + sampleFile.getAbsolutePath() + "\" already exists in this sample set, skipping");
			return null;
		}
		sampleFile.createNewFile();
		return sampleFile;
	}

}
