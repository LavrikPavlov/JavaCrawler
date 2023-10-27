package ru.crawler.classes;

import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

import java.util.List;

public class Graphs {

    private final XYChart chartWordsList = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Word List")
            .xAxisTitle("Indexed")
            .yAxisTitle("Words")
            .build();

    private final XYChart chartUrlList = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("URL List")
            .xAxisTitle("Indexed")
            .yAxisTitle("URLS")
            .build();

    private final XYChart chartRefList = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Ref List")
            .xAxisTitle("Indexed")
            .yAxisTitle("Refs")
            .build();

    public void drawRef(List<Integer> x1, List<Integer> y1){
        chartRefList.addSeries("Refs", x1, y1);
        new SwingWrapper<>(chartRefList).displayChart();
    }

    public void drawUrl(List<Integer> x1, List<Integer> y1){
        chartUrlList.addSeries("Url", x1, y1);
        new SwingWrapper<>(chartUrlList).displayChart();
    }

    public void drawWords(List<Integer> x1, List<Integer> y1){
        chartWordsList.addSeries("Words", x1, y1);
        new SwingWrapper<>(chartWordsList).displayChart();
    }
}
