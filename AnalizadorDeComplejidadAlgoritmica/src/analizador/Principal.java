package analizador;
import vista.VistaGeneral;

public class Principal {
   public static void main(String[] args) {
        
        java.awt.EventQueue.invokeLater(() -> {
            VistaGeneral ventana = new VistaGeneral();
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });        
        
        // Prueba por consola
        
       /* Analizador_Léxico lexer = new Analizador_Léxico();
        Scanner sc = new Scanner(System.in);
        System.out.print("Leer cadena: ");
        String input = sc.nextLine();

        lexer.analizar(input); // Produce tokens
        Parser parser = new Parser();
        parser.setTokens(lexer.getTokens());
        parser.setEntradaOriginal(lexer.getEntradaOriginal());

        boolean resultado = parser.sintactico();
        System.out.println("Resultado: " + (resultado ? "Cadena valida" : "Cadena invalida"));*/
    }
}