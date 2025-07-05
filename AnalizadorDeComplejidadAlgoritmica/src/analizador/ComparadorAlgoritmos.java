package analizador;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public class ComparadorAlgoritmos {
    private JTable tablaComparacion;
    private List<Algoritmo> algoritmos = new ArrayList<>();

    public ComparadorAlgoritmos(JTable tablaComparacion) {
        this.tablaComparacion = tablaComparacion;
    }

    public void agregarAlgoritmo(String nombre, String complejidad, String tnAproximado) {
        algoritmos.add(new Algoritmo(nombre, complejidad, tnAproximado));
        actualizarTabla();
    }

    private void actualizarTabla() {
        DefaultTableModel model = (DefaultTableModel) tablaComparacion.getModel();
        model.setRowCount(0); // Limpiar tabla
        for (Algoritmo alg : algoritmos) {
            model.addRow(new Object[]{alg.getNombre(), alg.getComplejidad(), alg.getTnAproximado()});
        }
    }

    static class Algoritmo {
        private String nombre;
        private String complejidad;
        private String tnAproximado;

        public Algoritmo(String nombre, String complejidad, String tnAproximado) {
            this.nombre = nombre;
            this.complejidad = complejidad;
            this.tnAproximado = tnAproximado;
        }

        public String getNombre() { return nombre; }
        public String getComplejidad() { return complejidad; }
        public String getTnAproximado() { return tnAproximado; }
    }
}