package vista;

import java.awt.BasicStroke;
import java.awt.Color;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import javax.swing.*;
import java.util.Map;


import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;


public class VistaGrafico extends JFrame {
public VistaGrafico(Map<String, String> funcionesAGraficar) {
    setTitle("Gráfico de Complejidad Temporal");
    setSize(800, 600);
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setLocationRelativeTo(null);

    JFreeChart chart = crearGrafico(funcionesAGraficar);
    ChartPanel chartPanel = new ChartPanel(chart);
    add(chartPanel);
}
private JFreeChart crearGrafico(Map<String, String> funcionesAGraficar) {
    XYSeriesCollection dataset = new XYSeriesCollection();

    for (Map.Entry<String, String> entry : funcionesAGraficar.entrySet()) {
        String nombreFuncion = entry.getKey();
        String expresion = entry.getValue();

        XYSeries series = new XYSeries(nombreFuncion);
        for (int n = 1; n <= 100; n++) {
            double valor = evaluarFuncion(expresion, n);
            series.add(n, valor);
        }
        dataset.addSeries(series);
    }

    JFreeChart chart = ChartFactory.createXYLineChart(
            "Complejidad Temporal T(n)",
            "Tamaño de entrada (n)",
            "Tiempo estimado (T(n))",
            dataset,
            PlotOrientation.VERTICAL,
            true, // Mostrar leyenda
            true, // Herramientas
            false // URLs
    );

    XYPlot plot = (XYPlot) chart.getPlot();
    XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();

    // Personalizar estilos por cada serie
    int i = 0;
    Color[] colores = {Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.PINK, Color.CYAN};
    BasicStroke[] strokes = {
        new BasicStroke(2.0f),
        new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f}, 0.0f),
        new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{2.0f, 6.0f}, 0.0f)
    };

    for (i = 0; i < dataset.getSeriesCount(); i++) {
        if (i < colores.length) {
            renderer.setSeriesPaint(i, colores[i]);
        } else {
            renderer.setSeriesPaint(i, Color.GRAY);
        }

        if (i < strokes.length) {
            renderer.setSeriesStroke(i, strokes[i % strokes.length]);
        } else {
            renderer.setSeriesStroke(i, new BasicStroke(2.0f));
        }
    }

    return chart;
}


private double evaluarFuncion(String funcion, int n) {
    String exprLimpia = funcion
            .replace("N", String.valueOf(n))
            .replace("n", String.valueOf(n))
            .replace("²", "*")
            .replace("³", "**")
            .replaceAll("\\s+", "")
            .replace("ln(", "Math.log("); // Corregir ln(n)

    try {
        Expression expression = new ExpressionBuilder(exprLimpia).build();
        return expression.evaluate();
    } catch (Exception e) {
        System.err.println("Error evaluando T(n): " + e.getMessage());
        return 0;
    }
}
}