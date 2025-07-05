package vista;

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
private JFreeChart crearGrafico(Map<String, String> funciones) {
    XYSeriesCollection dataset = new XYSeriesCollection();

    for (Map.Entry<String, String> entry : funciones.entrySet()) {
        String nombreFuncion = entry.getKey();
        String expresion = entry.getValue();

        XYSeries series = new XYSeries(nombreFuncion);
        for (int n = 1; n <= 100; n++) {
            double valor = evaluarFuncion(expresion, n);
            series.add(n, valor);
        }
        dataset.addSeries(series);
    }

    return ChartFactory.createXYLineChart(
            "Complejidad Temporal T(n)",
            "Tamaño de Entrada (n)",
            "Tiempo Estimado (T(n))",
            dataset,
            PlotOrientation.VERTICAL,
            true, // Mostrar leyenda
            true, // Herramientas
            false // URLs
    );
}

    private double evaluarFuncion(String funcion, int n) {
        String exprLimpia = funcion
                .replace("N", String.valueOf(n))
                .replace("n", String.valueOf(n))
                .replace("²", "*")
                .replace("³", "**")
                .replaceAll("\\s+", "");

        try {
            Expression expression = new ExpressionBuilder(exprLimpia).build();
            return expression.evaluate();
        } catch (Exception e) {
            System.err.println("Error evaluando T(n): " + e.getMessage());
            return 0;
        }
    }
}