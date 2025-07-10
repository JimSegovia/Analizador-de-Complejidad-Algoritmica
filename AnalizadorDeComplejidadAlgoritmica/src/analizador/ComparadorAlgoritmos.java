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
        algoritmos.add(0, new Algoritmo(nombre, complejidad, tnAproximado)); // Agregar al inicio
        actualizarTabla();
    }

    public List<Algoritmo> getAlgoritmosSeleccionados() {
        List<Algoritmo> seleccionados = new ArrayList<>();
        int[] filasSeleccionadas = tablaComparacion.getSelectedRows();
        for (int fila : filasSeleccionadas) {
            if (fila >= 0 && fila < algoritmos.size()) {
                seleccionados.add(algoritmos.get(fila));
            }
        }
        return seleccionados;
    }

    public void actualizarTabla() {
        DefaultTableModel model = (DefaultTableModel) tablaComparacion.getModel();
        model.setRowCount(0); // Limpiar tabla

        for (Algoritmo alg : algoritmos) {
            model.addRow(new Object[]{alg.nombre, alg.complejidad, alg.tnAproximado});
        }
    }

   public static class Algoritmo {
        public String nombre;
        private String complejidad;
        public String tnAproximado;

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