import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

public class TrecEval2 extends ApplicationFrame {

	XYSeriesCollection dataset = new XYSeriesCollection();
	JFreeChart chart = null;
	private String index = null;

	public TrecEval2(String title, String model) throws Throwable {
		super(title);
		populateDataset(model);
		chart = createChart(dataset);
		ChartPanel chartPanel = new ChartPanel(chart);
		chartPanel.setPreferredSize(new Dimension(500, 270));
		setContentPane(chartPanel);
	}

	private static JFreeChart createChart(XYSeriesCollection dataset) {
		JFreeChart chart = ChartFactory.createXYLineChart("Precision-Recall",
				"Threshold", "Value", dataset, PlotOrientation.VERTICAL, true, // include
																				// legend
				true, // tooltips
				false // urls
				);

		chart.setBackgroundPaint(Color.white);

		XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(Color.lightGray);
		plot.setRangeGridlinePaint(Color.white);

		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		renderer.setSeriesPaint(0, Color.RED);
		renderer.setSeriesPaint(1, Color.GREEN);
		renderer.setSeriesStroke(0, new BasicStroke(1.0f));
		renderer.setSeriesStroke(1, new BasicStroke(1.0f));
		plot.setRenderer(renderer);

		return chart;
	}

	public void populateDataset(String model) throws Throwable {

		XYSeries precisionDataset = new XYSeries("Precision");
		XYSeries recallDataset = new XYSeries("Recall");

		for (int i = 0; i < 20; ++i ) {
			float threshold = 0.0f + i * 0.05f;
			Process proc = Runtime
					.getRuntime()
					.exec(new String[] {
							"/bin/sh",
							"-c",
							"cd /media/110GBPart/TextRetrieval/trec-demo/bin/; "
									+ "java -classpath /media/110GBPart/TextRetrieval/trec-demo/bin:/media/110GBPart/TextRetrieval/trec-demo/lib/lucene-core-4.1.0.jar:/media/110GBPart/TextRetrieval/trec-demo/lib/lucene-analyzers-common-4.1.0.jar:/media/110GBPart/TextRetrieval/trec-demo/lib/lucene-queryparser-4.1.0.jar"
									+ " SearchOhsumedFiles"
									+ " -index /media/WIN8/MSLR-DATASET/OHSUMED/ohsu-trec-copy/anm-relevant/index"
									+ " -queries /media/WIN8/MSLR-DATASET/OHSUMED/ohsu-trec-copy/trec9-train/query.ohsu.1-63"
									+ " -runid " + model + " -threshold "
									+ threshold + " >  op.txt" });
			proc.waitFor();
			proc = Runtime
					.getRuntime()
					.exec(new String[] {
							"/bin/sh",
							"-c",
							"cd /media/110GBPart/TextRetrieval/trec-demo-orig/trec-demo/eval/trec_eval.8.1; "
									+ " ./trec_eval -al2 qrels.ohsu.batch.87.mod2 /media/110GBPart/TextRetrieval/trec-demo/bin/op.txt > results.txt; " });
			proc.waitFor();

			BufferedReader br = new BufferedReader(
					new FileReader(
							"/media/110GBPart/TextRetrieval/trec-demo-orig/trec-demo/eval/trec_eval.8.1/results.txt"));
			String line = null;
			float recall = 0.0f, precision = 0, map = 0;
			while ((line = br.readLine()) != null) {
				if (line.contains("map")) {
					map = Float.parseFloat(line.split("\\s+")[2]);
				} else if (line.contains("exact_prec")) {
					precision = Float.parseFloat(line.split("\\s+")[2]);
				} else if (line.contains("exact_recall")) {
					recall = Float.parseFloat(line.split("\\s+")[2]);
					break;
				}
			}
			System.out.println("" + map + " " + precision + " " + recall);
			br.close();
			precisionDataset.add(threshold, precision);
			recallDataset.add(threshold, recall);
		}
		dataset.addSeries(recallDataset);
		dataset.addSeries(precisionDataset);
	}

	private void saveChart(String path) {
		File imageFile = new File(path);
		int width = 640;
		int height = 480;

		try {
			ChartUtilities.saveChartAsPNG(imageFile, chart, width, height);
		} catch (IOException ex) {
			System.err.println(ex);
		}
	}

	public static void main(String[] args) throws Throwable {
		String index = null;
		String savePath = null;

		// TODO remove hard-coded stuff
		for (int i = 0; i < args.length; i++) {
			if ("-index".equals(args[i])) {
				index = args[i + 1];
				i++;
			} else if ("-save".equals(args[i])) {
				savePath = args[i + 1];
				i++;
			}
		}

		String model = "dfr";
		TrecEval2 trecEval = new TrecEval2("Evaluation for model: " + model,
				model);
		trecEval.pack();
		if(savePath != null)
			trecEval.saveChart(savePath);
		RefineryUtilities.centerFrameOnScreen(trecEval);
		trecEval.setVisible(true);
	}
}
