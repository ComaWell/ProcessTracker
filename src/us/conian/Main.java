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
		int sampleInterval = CLIUtils.determineSampleInterval(in, (args == null || args.length < 1 ? null : args[0]));
		int numSamples = CLIUtils.determineNumSamples(in, (args == null || args.length < 2 ? null : args[1]));
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
		
		if (samples != null && !samples.isEmpty() && CLIUtils.determineShouldSave(in)) {
			File directory = CLIUtils.createSampleDirectory(in, DATA_FOLDER, start);
			for (Map.Entry<String, List<Sample>> entry : samples.entrySet()) {
				File output = CLIUtils.createSampleFile(directory, entry.getKey());
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

}
