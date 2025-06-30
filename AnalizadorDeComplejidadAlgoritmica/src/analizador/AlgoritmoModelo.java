package analizador;
import java.util.Scanner;
import java.util.regex.*;
import java.util.Stack;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AlgoritmoModelo {
    // Patrones constantes (Regla 1)
    private static final Pattern ASIGNACION_SIMPLE = Pattern.compile("=\\s*[^=]");
    private static final Pattern OP_CON_ASIGNACION = Pattern.compile("\\+\\+|--|\\+=|-=|\\*=|/=");
    private static final Pattern COMPARACIONES = Pattern.compile("==|!=|<|>|<=|>=|\\|\\||&&");
    private static final Pattern OPERACIONES_ARIT = Pattern.compile("[+\\-*/%]");
    private static final Pattern ACCESO_ARRAY = Pattern.compile("\\[[^\\]]+\\]");
    
    // Palabras clave para filtrar
    private static final Set<String> KEYWORDS = Set.of(
        "auto", "break", "case", "char", "const", "continue", "default", "do",
        "double", "else", "enum", "extern", "float", "for", "goto", "if",
        "int", "long", "register", "return", "short", "signed", "sizeof", "static",
        "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
        "printf", "scanf", "endl", "string", "using", "namespace", "std"
    );
    
    // Mapa para almacenar funciones definidas por el usuario
    private static Map<String, Funcion> funcionesUsuario = new HashMap<>();
    
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Ingresa el codigo C++ (escribe 'END' en una nueva linea para finalizar):");
        StringBuilder codigo = new StringBuilder();
        String linea;
        
        while (!(linea = sc.nextLine()).equals("END")) {
            codigo.append(linea).append("\n");
        }
        
        // Procesar el codigo
        procesarCodigo(codigo);
        sc.close();
    }
    
    // Metodo principal para procesar el codigo
    private static void procesarCodigo(StringBuilder codigo) {
        ResultadoComplejidad totalOE = new ResultadoComplejidad();
        String[] lineas = codigo.toString().split("\n");
        Stack<Bloque> pilaBloques = new Stack<>();
        boolean enCuerpo = false;
        boolean enFuncion = false;
        String funcionActual = null;
        Bloque bloqueActual = null;
        
        for (String ln : lineas) {
            ln = ln.trim();
            System.out.println("DEBUG: Procesando linea: '" + ln + "'");
            if (ln.isEmpty() || ln.equals("{")) continue;
            
            if (ln.startsWith("#include") || 
                ln.startsWith("#define") ||
                ln.startsWith("#pragma") ||
                ln.startsWith("using namespace") || 
                ln.startsWith("using std") ||
                ln.startsWith("import ") ||
                ln.startsWith("package ") ||
                ln.matches("^using\\s+.*") ||
                ln.matches("^namespace\\s+.*")) {
                System.out.println("DEBUG: Ignorando línea de preprocesador/import: " + ln);
                continue;
            }
            
            
            // --------------------------------
            // Deteccion de declaracion de funcion
            // --------------------------------
            if (esDeclaracionFuncion(ln) && !ln.contains("main")) {
                String nombreFuncion = extraerNombreFuncion(ln);
                funcionActual = nombreFuncion;
                enFuncion = true;
                funcionesUsuario.put(nombreFuncion, new Funcion(nombreFuncion));
                System.out.println("DEBUG: Declarando funcion: " + nombreFuncion);
                continue;
            }
            
            // --------------------------------
            // Manejo especial para main
            // --------------------------------
            if (ln.contains("main") && ln.contains("(")) {
                enFuncion = false; // main se ejecuta normalmente
                funcionActual = null;
                System.out.println("DEBUG: Entrando a funcion main");
                continue;
            }
            
            // Verificar si hay un bloque if cerrado esperando else
            if (!pilaBloques.isEmpty()) {
    Bloque tope = pilaBloques.peek();
    if (tope.tipo == TipoBloque.IF && tope.cuerpoIfCerrado && !ln.startsWith("else")) {
        // El if terminó y no hay else, así que suma condición + cuerpo del if
        pilaBloques.pop();
        ResultadoComplejidad cuerpoMax = ResultadoComplejidad.max(tope.oeCuerpo, tope.oeCuerpoElse);
        System.out.println("DEBUG: IF sin ELSE terminado. Condición: " + tope.oeCondicion + 
                          ", Cuerpo IF: " + tope.oeCuerpo + 
                          ", Cuerpo ELSE: " + tope.oeCuerpoElse + 
                          ", Max seleccionado: " + cuerpoMax);

        ResultadoComplejidad totalBloque = tope.oeCondicion.sumar(cuerpoMax);

        // CORRECCIÓN: Verificar si estamos dentro de un ciclo
        if (!pilaBloques.isEmpty() && 
            (pilaBloques.peek().tipo == TipoBloque.FOR || pilaBloques.peek().tipo == TipoBloque.WHILE)) {
            // Estamos dentro de un ciclo, sumar al cuerpo del ciclo
            Bloque cicloActivo = pilaBloques.peek();
            cicloActivo.oeCuerpo = cicloActivo.oeCuerpo.sumar(totalBloque);
            System.out.println("DEBUG: IF dentro de ciclo. Sumando " + totalBloque + " al cuerpo del ciclo. Nuevo total del cuerpo: " + cicloActivo.oeCuerpo);
        } else if (enFuncion) {
            funcionesUsuario.get(funcionActual).oeTotal = funcionesUsuario.get(funcionActual).oeTotal.sumar(totalBloque);
            System.out.println("DEBUG: Sumando " + totalBloque + " OEs a función " + funcionActual);
        } else {
            totalOE = totalOE.sumar(totalBloque);
        }
        enCuerpo = !pilaBloques.isEmpty(); // Seguimos en cuerpo si hay más bloques
    }
}
            
            // Sección corregida del manejo de cierre de bloques
            // Modificación en la sección de manejo de cierre de bloques para ciclos anidados
    if (ln.equals("}") || ln.contains("}")) {
    // Si estamos cerrando una función
    if (enFuncion && pilaBloques.isEmpty()) {
        System.out.println("DEBUG: Cerrando función: " + funcionActual);
        enFuncion = false;
        funcionActual = null;
        continue;
    }

    if (!pilaBloques.isEmpty()) {
        bloqueActual = pilaBloques.peek();

        // Para ciclos FOR y WHILE: cerrar inmediatamente cuando se encuentra }
        if (bloqueActual.tipo == TipoBloque.FOR || bloqueActual.tipo == TipoBloque.WHILE) {
            // Procesar cualquier código que esté antes de la } en la misma línea
            String antesLlave = ln.substring(0, ln.indexOf("}")).trim();
            if (!antesLlave.isEmpty()) {
                ResultadoComplejidad oeAntesLlave = calcularOEExcluyendoLlamadas(antesLlave);
                ResultadoComplejidad oeLlamadaAntesLlave = calcularLlamadaFuncion(antesLlave);
                ResultadoComplejidad totalOEAntesLlave = oeAntesLlave.sumar(oeLlamadaAntesLlave);

                bloqueActual.oeCuerpo = bloqueActual.oeCuerpo.sumar(totalOEAntesLlave);
                System.out.println("DEBUG: Procesando codigo antes de }: " + antesLlave + " (" + totalOEAntesLlave + " OEs)");
            }

            // Cerrar ciclo
            bloqueActual = pilaBloques.pop();
            ResultadoComplejidad totalCiclo = calcularComplejidadCiclo(bloqueActual);
            System.out.println("DEBUG: Cerrando ciclo " + bloqueActual.tipo + ". Complejidad total: " + totalCiclo);

            // Verificar si estamos dentro de otro ciclo
            if (!pilaBloques.isEmpty() && 
                (pilaBloques.peek().tipo == TipoBloque.FOR || pilaBloques.peek().tipo == TipoBloque.WHILE)) {
                // Estamos dentro de otro ciclo, sumar al cuerpo del ciclo padre
                Bloque cicloPadre = pilaBloques.peek();
                cicloPadre.oeCuerpo = cicloPadre.oeCuerpo.sumar(totalCiclo);
                System.out.println("DEBUG: Ciclo anidado detectado. El ciclo interior (" + totalCiclo + ") se suma como instruccion del ciclo padre");
            } else if (enFuncion) {
                funcionesUsuario.get(funcionActual).oeTotal = funcionesUsuario.get(funcionActual).oeTotal.sumar(totalCiclo);
                System.out.println("DEBUG: Sumando " + totalCiclo + " OEs a función " + funcionActual);
            } else {
                totalOE = totalOE.sumar(totalCiclo);
                System.out.println("DEBUG: Sumando al total: " + totalCiclo + ", Total actual: " + totalOE);
            }
            enCuerpo = !pilaBloques.isEmpty(); // Seguimos en cuerpo si hay más bloques

            // Procesar cualquier código después de la } en la misma línea
            String despuesLlave = ln.substring(ln.indexOf("}") + 1).trim();
            if (!despuesLlave.isEmpty()) {
                System.out.println("DEBUG: Procesando codigo después de }: " + despuesLlave);
                // AQUÍ DEBERÍAS PROCESAR EL CÓDIGO DESPUÉS DE }
                ResultadoComplejidad oeDespuesLlave = calcularOEExcluyendoLlamadas(despuesLlave);
                ResultadoComplejidad oeLlamadaDespuesLlave = calcularLlamadaFuncion(despuesLlave);
                ResultadoComplejidad totalOEDespuesLlave = oeDespuesLlave.sumar(oeLlamadaDespuesLlave);
                
                // Sumar según el contexto actual
                if (!pilaBloques.isEmpty()) {
                    Bloque bloqueActivo = pilaBloques.peek();
                    if (bloqueActivo.enElse) {
                        bloqueActivo.oeCuerpoElse = bloqueActivo.oeCuerpoElse.sumar(totalOEDespuesLlave);
                    } else {
                        bloqueActivo.oeCuerpo = bloqueActivo.oeCuerpo.sumar(totalOEDespuesLlave);
                    }
                } else if (enFuncion) {
                    funcionesUsuario.get(funcionActual).oeTotal = funcionesUsuario.get(funcionActual).oeTotal.sumar(totalOEDespuesLlave);
                } else {
                    totalOE = totalOE.sumar(totalOEDespuesLlave);
                }
            }
        }
        // Para IF: manejar lógica especial de IF-ELSE
        else if (bloqueActual.tipo == TipoBloque.IF && !bloqueActual.enElse) {
            // CORRECCIÓN: Procesar código antes de } en el IF
            String antesLlave = ln.substring(0, ln.indexOf("}")).trim();
            if (!antesLlave.isEmpty()) {
                ResultadoComplejidad oeAntesLlave = calcularOEExcluyendoLlamadas(antesLlave);
                ResultadoComplejidad oeLlamadaAntesLlave = calcularLlamadaFuncion(antesLlave);
                ResultadoComplejidad totalOEAntesLlave = oeAntesLlave.sumar(oeLlamadaAntesLlave);

                bloqueActual.oeCuerpo = bloqueActual.oeCuerpo.sumar(totalOEAntesLlave);
                System.out.println("DEBUG: Procesando codigo antes de } en IF: " + antesLlave + " (" + totalOEAntesLlave + " OEs)");
                System.out.println("DEBUG: Total IF actualizado: " + bloqueActual.oeCuerpo);
            }
            
            bloqueActual.cuerpoIfCerrado = true;
            enCuerpo = false;
            System.out.println("DEBUG: Cerrando cuerpo IF, esperando posible ELSE. IF tiene: " + bloqueActual.oeCuerpo + " OEs");

            // CORRECCIÓN PRINCIPAL: Verificar si la línea contiene "else"
            String despuesLlave = ln.substring(ln.indexOf("}") + 1).trim();
            if (despuesLlave.startsWith("else")) {
                bloqueActual.cuerpoIfCerrado = false;
                bloqueActual.enElse = true;
                enCuerpo = true;
                System.out.println("DEBUG: Iniciando bloque ELSE en la misma línea");
                
                // NUEVA CORRECCIÓN: Procesar contenido después de "else"
                String contenidoDespuesElse = despuesLlave.substring(4).trim(); // Quitar "else"
                if (contenidoDespuesElse.startsWith("{")) {
                    contenidoDespuesElse = contenidoDespuesElse.substring(1).trim(); // Quitar "{"
                }
                
                if (!contenidoDespuesElse.isEmpty()) {
                    ResultadoComplejidad oeContenidoElse = calcularOEExcluyendoLlamadas(contenidoDespuesElse);
                    ResultadoComplejidad oeLlamadaElse = calcularLlamadaFuncion(contenidoDespuesElse);
                    ResultadoComplejidad totalOEContenidoElse = oeContenidoElse.sumar(oeLlamadaElse);
                    
                    bloqueActual.oeCuerpoElse = bloqueActual.oeCuerpoElse.sumar(totalOEContenidoElse);
                    System.out.println("DEBUG: Procesando contenido después de ELSE en la misma línea: " + contenidoDespuesElse + " (" + totalOEContenidoElse + " OEs)");
                    System.out.println("DEBUG: Total ELSE actualizado: " + bloqueActual.oeCuerpoElse);
                }
            }
        }
        // Cerrar bloque ELSE del IF
        else if (bloqueActual.tipo == TipoBloque.IF && bloqueActual.enElse) {
    // Procesar cualquier código que esté antes de la } en la misma línea
    String antesLlave = "";
    if (ln.contains("}")) {
        antesLlave = ln.substring(0, ln.indexOf("}")).trim();
    }
    
    if (!antesLlave.isEmpty()) {
        ResultadoComplejidad oeAntesLlave = calcularOEExcluyendoLlamadas(antesLlave);
        ResultadoComplejidad oeLlamadaAntesLlave = calcularLlamadaFuncion(antesLlave);
        ResultadoComplejidad totalOEAntesLlave = oeAntesLlave.sumar(oeLlamadaAntesLlave);

        bloqueActual.oeCuerpoElse = bloqueActual.oeCuerpoElse.sumar(totalOEAntesLlave);
        System.out.println("DEBUG: Procesando codigo antes de } en ELSE: " + antesLlave + " (" + totalOEAntesLlave + " OEs)");
        System.out.println("DEBUG: Total ELSE actualizado: " + bloqueActual.oeCuerpoElse);
    }

    // Cerrar bloque IF-ELSE completo
    bloqueActual = pilaBloques.pop();
    ResultadoComplejidad cuerpoMax = ResultadoComplejidad.max(bloqueActual.oeCuerpo, bloqueActual.oeCuerpoElse);
    System.out.println("DEBUG: Cerrando IF-ELSE completo. IF: " + bloqueActual.oeCuerpo + 
                      ", ELSE: " + bloqueActual.oeCuerpoElse + 
                      ", Condición: " + bloqueActual.oeCondicion + 
                      ", Max seleccionado: " + cuerpoMax);

    ResultadoComplejidad totalBloque = bloqueActual.oeCondicion.sumar(cuerpoMax);

    // Verificar si estamos dentro de otro ciclo
    if (!pilaBloques.isEmpty() && 
        (pilaBloques.peek().tipo == TipoBloque.FOR || pilaBloques.peek().tipo == TipoBloque.WHILE)) {
        // Estamos dentro de un ciclo, sumar al cuerpo del ciclo padre
        Bloque cicloPadre = pilaBloques.peek();
        cicloPadre.oeCuerpo = cicloPadre.oeCuerpo.sumar(totalBloque);
        System.out.println("DEBUG: IF-ELSE dentro de ciclo. Sumando " + totalBloque + " al cuerpo del ciclo padre");
    } else if (enFuncion) {
        funcionesUsuario.get(funcionActual).oeTotal = funcionesUsuario.get(funcionActual).oeTotal.sumar(totalBloque);
        System.out.println("DEBUG: Sumando " + totalBloque + " OEs a función " + funcionActual);
    } else {
        totalOE = totalOE.sumar(totalBloque);
        System.out.println("DEBUG: Sumando al total: " + totalBloque + ", Total actual: " + totalOE);
    }
    enCuerpo = !pilaBloques.isEmpty();
}
    }
    continue;
}
            
            // --------------------------------
            // Deteccion de estructuras
            // --------------------------------
            if (ln.startsWith("if") && ln.contains("(")) {
                ResultadoComplejidad oeCondicion = calcularOE(ln);
                bloqueActual = new Bloque(TipoBloque.IF, oeCondicion);
                pilaBloques.push(bloqueActual);
                enCuerpo = true;
                continue;
            }
            
            // Deteccion de FOR
            if (ln.startsWith("for") && ln.contains("(")) {
                CicloInfo infoCiclo = analizarFor(ln);
                bloqueActual = new Bloque(TipoBloque.FOR, infoCiclo);
                pilaBloques.push(bloqueActual);
                enCuerpo = true;
                System.out.println("DEBUG: Detectado ciclo FOR con rango: " + infoCiclo.rango);
                continue;
            }
            
            // Deteccion de WHILE
            if (ln.startsWith("while") && ln.contains("(")) {
                CicloInfo infoCiclo = analizarWhile(ln);
                bloqueActual = new Bloque(TipoBloque.WHILE, infoCiclo);
                pilaBloques.push(bloqueActual);
                enCuerpo = true;
                System.out.println("DEBUG: Detectado ciclo WHILE con rango: " + infoCiclo.rango);
                continue;
            }
            
            // Deteccion de else
            if (ln.startsWith("else")) {
                if (!pilaBloques.isEmpty()) {
                    bloqueActual = pilaBloques.peek();
                    if (bloqueActual.tipo == TipoBloque.IF && bloqueActual.cuerpoIfCerrado) {
                        // Reactivar bloque if para el else
                        bloqueActual.cuerpoIfCerrado = false;
                        bloqueActual.enElse = true;
                        enCuerpo = true;
                        System.out.println("DEBUG: Iniciando bloque ELSE");

                        // CORRECCIÓN: Procesar contenido después de "else" en línea separada
                        String contenidoDespuesElse = ln.substring(4).trim(); // Quitar "else"
                        if (contenidoDespuesElse.startsWith("{")) {
                            contenidoDespuesElse = contenidoDespuesElse.substring(1).trim(); // Quitar "{"
                        }

                        if (!contenidoDespuesElse.isEmpty()) {
                            // AQUÍ ESTABA EL PROBLEMA - No se estaba calculando correctamente
                            ResultadoComplejidad oeContenidoElse = calcularOEExcluyendoLlamadas(contenidoDespuesElse);
                            ResultadoComplejidad oeLlamadaElse = calcularLlamadaFuncion(contenidoDespuesElse);
                            ResultadoComplejidad totalOEContenidoElse = oeContenidoElse.sumar(oeLlamadaElse);

                            bloqueActual.oeCuerpoElse = bloqueActual.oeCuerpoElse.sumar(totalOEContenidoElse);
                            System.out.println("DEBUG: Procesando contenido después de ELSE: " + contenidoDespuesElse + " (" + totalOEContenidoElse + " OEs)");
                            System.out.println("DEBUG: Total ELSE actualizado: " + bloqueActual.oeCuerpoElse);
                        }
                    }
                }
                continue;
            }

            // --------------------------------
            // Manejo de operaciones
            // --------------------------------
            // Primero calcular llamadas a funcion
            ResultadoComplejidad oeLlamadaFuncion = calcularLlamadaFuncion(ln);
            
            // Luego calcular OE normales, excluyendo las operaciones dentro de llamadas a funcion
            ResultadoComplejidad oeLinea = calcularOEExcluyendoLlamadas(ln);
            
            ResultadoComplejidad totalOELinea = oeLinea.sumar(oeLlamadaFuncion);
            
            if (!oeLlamadaFuncion.esVacio()) {
                System.out.println("DEBUG: Linea con llamada a funcion. OE normales: " + oeLinea + ", OE llamada: " + oeLlamadaFuncion);
            }
            
            if (enCuerpo) {
                if (enCuerpo) {
                    if (bloqueActual.enElse) {
                        bloqueActual.oeCuerpoElse = bloqueActual.oeCuerpoElse.sumar(totalOELinea);
                        System.out.println("DEBUG: Sumando " + totalOELinea + " OEs al ELSE. Total ELSE: " + bloqueActual.oeCuerpoElse);
                    } else {
                        bloqueActual.oeCuerpo = bloqueActual.oeCuerpo.sumar(totalOELinea);
                        System.out.println("DEBUG: Sumando " + totalOELinea + " OEs al cuerpo. Total: " + bloqueActual.oeCuerpo);
                    }
                }
            } else if (enFuncion) {
                funcionesUsuario.get(funcionActual).oeTotal = funcionesUsuario.get(funcionActual).oeTotal.sumar(totalOELinea);
                System.out.println("DEBUG: Sumando " + totalOELinea + " OEs a funcion " + funcionActual + ". Total funcion: " + funcionesUsuario.get(funcionActual).oeTotal);
            } else {
                totalOE = totalOE.sumar(totalOELinea);
                System.out.println("DEBUG: Sumando " + totalOELinea + " OEs fuera de bloques. Total: " + totalOE);
            }
        }
        
        // Sumar bloques pendientes
        while (!pilaBloques.isEmpty()) {
            bloqueActual = pilaBloques.pop();
            if (bloqueActual.tipo == TipoBloque.IF) {
                // Solo sumar el mayor entre if y else
                ResultadoComplejidad cuerpoMax = ResultadoComplejidad.max(bloqueActual.oeCuerpo, bloqueActual.oeCuerpoElse);
                ResultadoComplejidad totalBloque = bloqueActual.oeCondicion.sumar(cuerpoMax);
                
                if (enFuncion) {
                    funcionesUsuario.get(funcionActual).oeTotal = funcionesUsuario.get(funcionActual).oeTotal.sumar(totalBloque);
                } else {
                    totalOE = totalOE.sumar(totalBloque);
                }
            } else if (bloqueActual.tipo == TipoBloque.FOR || bloqueActual.tipo == TipoBloque.WHILE) {
                ResultadoComplejidad totalCiclo = calcularComplejidadCiclo(bloqueActual);
                if (enFuncion) {
                    funcionesUsuario.get(funcionActual).oeTotal = funcionesUsuario.get(funcionActual).oeTotal.sumar(totalCiclo);
                } else {
                    totalOE = totalOE.sumar(totalCiclo);
                }
            }
        }
        
        System.out.println("\n=== RESUMEN DE FUNCIONES ===");
        for (Map.Entry<String, Funcion> entry : funcionesUsuario.entrySet()) {
            System.out.println("Funcion " + entry.getKey() + ": " + entry.getValue().oeTotal);
        }
        System.out.println("============================\n");
        
        System.out.println("\n=== DEBUG FINAL ===");
System.out.println("Estado de la pila de bloques: " + pilaBloques.size());
for (int i = 0; i < pilaBloques.size(); i++) {
    Bloque b = pilaBloques.get(i);
    System.out.println("Bloque " + i + ": " + b.tipo + 
                      ", IF: " + b.oeCuerpo + 
                      ", ELSE: " + b.oeCuerpoElse + 
                      ", enElse: " + b.enElse + 
                      ", ifCerrado: " + b.cuerpoIfCerrado);
}
System.out.println("===================\n");
        
        System.out.println("Complejidad total: " + totalOE);
    }


    
    
    // Metodo para analizar ciclo FOR
    private static CicloInfo analizarFor(String ln) {
        // Extraer contenido entre parentesis
        int inicio = ln.indexOf('(');
        int fin = ln.lastIndexOf(')');
        if (inicio == -1 || fin == -1) return new CicloInfo();
        
        String contenido = ln.substring(inicio + 1, fin);
        String[] partes = contenido.split(";");
        
        if (partes.length != 3) return new CicloInfo();
        
        String inicializacion = partes[0].trim();
        String condicion = partes[1].trim();
        String incremento = partes[2].trim();
        
        CicloInfo info = new CicloInfo();
        info.inicializacion = calcularOE(inicializacion);
        info.condicion = calcularOE(condicion);
        info.incremento = calcularOE(incremento);
        info.rango = determinarRangoCiclo(inicializacion, condicion, incremento);
        
        System.out.println("DEBUG: FOR - Init: " + info.inicializacion + ", Cond: " + info.condicion + ", Inc: " + info.incremento + ", Rango: " + info.rango);
        
        return info;
    }
    
    // Metodo para analizar ciclo WHILE
    private static CicloInfo analizarWhile(String ln) {
        // Extraer contenido entre parentesis
        int inicio = ln.indexOf('(');
        int fin = ln.lastIndexOf(')');
        if (inicio == -1 || fin == -1) return new CicloInfo();
        
        String condicion = ln.substring(inicio + 1, fin).trim();
        
        CicloInfo info = new CicloInfo();
        info.inicializacion = new ResultadoComplejidad(); // 0 para while (se asume que se inicializa antes)
        info.condicion = calcularOE(condicion);
        info.incremento = new ResultadoComplejidad(); // Se calculara en el cuerpo
        info.rango = determinarRangoWhile(condicion);
        
        System.out.println("DEBUG: WHILE - Cond: " + info.condicion + ", Rango: " + info.rango);
        
        return info;
    }
    
    // Metodo para determinar el rango de un ciclo FOR
    private static String determinarRangoCiclo(String init, String cond, String inc) {
        // Buscar patron: i = 0; i < n; i++
        // O patron: i = 1; i <= n; i++

        Pattern patternInit = Pattern.compile("(\\w+)\\s*=\\s*(\\d+)");
        Pattern patternCond = Pattern.compile("(\\w+)\\s*(<|<=)\\s*(\\w+|\\d+)");

        Matcher matchInit = patternInit.matcher(init);
        Matcher matchCond = patternCond.matcher(cond);

        if (matchInit.find() && matchCond.find()) {
            String var = matchInit.group(1);
            String valorInicial = matchInit.group(2);
            String operador = matchCond.group(2);
            String limite = matchCond.group(3);

            // Si el limite es un numero
            if (limite.matches("\\d+")) {
                int valorFinal = Integer.parseInt(limite);
                int valorIni = Integer.parseInt(valorInicial);

                if (operador.equals("<")) {
                    // Para i < n: rango = n - valor_inicial
                    return String.valueOf(valorFinal - valorIni);
                } else if (operador.equals("<=")) {
                    // Para i <= n: rango = n - valor_inicial + 1
                    return String.valueOf(valorFinal - valorIni + 1);
                }
            } else {
                // El limite es una variable (n)
                int valorIni = Integer.parseInt(valorInicial);

                if (operador.equals("<")) {
                    // Para i < n: rango = n - valor_inicial
                    if (valorIni == 0) {
                        return limite; // n - 0 = n
                    } else {
                        return limite + "-" + valorIni; // n - valor_inicial
                    }
                } else if (operador.equals("<=")) {
                    // Para i <= n: rango = n - valor_inicial + 1
                    if (valorIni == 0) {
                        return limite + "+1"; // n - 0 + 1 = n + 1
                    } else if (valorIni == 1) {
                        return limite; // n - 1 + 1 = n
                    } else {
                        return limite + "-" + valorIni + "+1"; // n - valor_inicial + 1
                    }
                }
            }
        }

        return "n"; // Por defecto
    }
    // Metodo para determinar el rango de un ciclo WHILE
    private static String determinarRangoWhile(String condicion) {
        // Analizar condiciones tipicas como i < n, i <= n, etc.
        // Para WHILE asumimos que la variable se inicializa en 0 o 1 antes del ciclo
        Pattern pattern = Pattern.compile("(\\w+)\\s*(<|<=)\\s*(\\w+|\\d+)");
        Matcher matcher = pattern.matcher(condicion);

        if (matcher.find()) {
            String operador = matcher.group(2);
            String limite = matcher.group(3);

            if (limite.matches("\\d+")) {
                int valorFinal = Integer.parseInt(limite);
                // Asumimos inicialización en 0 para WHILE
                if (operador.equals("<")) {
                    return String.valueOf(valorFinal); // valorFinal - 0
                } else if (operador.equals("<=")) {
                    return String.valueOf(valorFinal + 1); // valorFinal - 0 + 1
                }
            } else {
                // Variable como n, asumimos inicialización en 0
                if (operador.equals("<")) {
                    return limite; // n - 0 = n
                } else if (operador.equals("<=")) {
                    return limite + "+1"; // n - 0 + 1 = n + 1
                }
            }
        }

        return "n"; // Por defecto
    }

    
    // Metodo para calcular la complejidad total de un ciclo
    // Método corregido para calcular la complejidad total de un ciclo
    // Método corregido para calcular la complejidad total de un ciclo
private static ResultadoComplejidad calcularComplejidadCiclo(Bloque bloque) {
    CicloInfo info = bloque.infoCiclo;
    ResultadoComplejidad resultado = new ResultadoComplejidad();

    // Sumar inicialización (se ejecuta 1 vez)
    resultado = resultado.sumar(info.inicializacion);

    // Sumar comparación inicial (se ejecuta 1 vez antes del ciclo)
    resultado = resultado.sumar(info.condicion);

    // CORRECCIÓN: El cuerpo completo del ciclo debe incluir:
    // - Las instrucciones del cuerpo
    // - El incremento 
    // - La comparación (que se ejecuta en cada iteración)
    ResultadoComplejidad cuerpoCompleto = bloque.oeCuerpo.sumar(info.incremento).sumar(info.condicion);

    // Multiplicar el cuerpo completo por el rango
    String rango = info.rango;
    if (rango.matches("\\d+")) {
        // Rango numérico
        int n = Integer.parseInt(rango);
        resultado = resultado.sumar(cuerpoCompleto.multiplicar(n));
    } else {
        // Rango simbólico - verificar si necesita paréntesis
        int constanteDelCuerpo = cuerpoCompleto.obtenerConstante();
        boolean tieneMultiplesTerminos = cuerpoCompleto.terminos.size() > 1;
        
        if (constanteDelCuerpo != 0 || tieneMultiplesTerminos) {
            // Usar multiplicación por expresión que maneja paréntesis automáticamente
            resultado = resultado.sumar(cuerpoCompleto.multiplicarPorExpresion(rango));
        } else {
            // Solo un término variable, usar multiplicación simple
            resultado = resultado.sumar(cuerpoCompleto.multiplicarPorVariable(rango));
        }
    }

    System.out.println("DEBUG: Ciclo CORREGIDO - Init: " + info.inicializacion + 
                      ", Cond inicial: " + info.condicion + 
                      ", Cuerpo: " + bloque.oeCuerpo +
                      ", Incremento: " + info.incremento +
                      ", Cond por iteración: " + info.condicion +
                      ", Cuerpo completo por iteración: " + cuerpoCompleto + 
                      ", Rango: " + rango + 
                      ", Total: " + resultado);

    return resultado;
}
    
    // Nuevo metodo que calcula OE excluyendo las operaciones dentro de llamadas a funcion
    private static ResultadoComplejidad calcularOEExcluyendoLlamadas(String ln) {
        // Crear una copia de la linea sin las llamadas a funciones de usuario
        String lnSinLlamadas = ln;
        
        // Eliminar llamadas a funciones de usuario
        for (String nombreFuncion : funcionesUsuario.keySet()) {
            Pattern patronLlamada = Pattern.compile("\\b" + nombreFuncion + "\\s*\\([^)]*\\)");
            lnSinLlamadas = patronLlamada.matcher(lnSinLlamadas).replaceAll("VAR");
        }
        
        return calcularOE(lnSinLlamadas);
    }
    
    // Metodo para detectar si una linea es una declaracion de funcion
    private static boolean esDeclaracionFuncion(String ln) {
        // Patron para detectar declaraciones de funcion: tipo nombre(parametros)
        Pattern patronFuncion = Pattern.compile("^(int|void|float|double|char)\\s+([a-zA-Z_]\\w*)\\s*\\(.*\\)\\s*\\{?$");
        return patronFuncion.matcher(ln).matches() && !ln.contains("main");
    }
    
    // Metodo para extraer el nombre de la funcion
    private static String extraerNombreFuncion(String ln) {
        Pattern patronNombre = Pattern.compile("^(int|void|float|double|char)\\s+([a-zA-Z_]\\w*)\\s*\\(");
        Matcher matcher = patronNombre.matcher(ln);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return "";
    }
    
    // Metodo para calcular OEs de llamadas a funciones (Regla 4)
    private static ResultadoComplejidad calcularLlamadaFuncion(String ln) {
        ResultadoComplejidad oeTotal = new ResultadoComplejidad();
        
        // Buscar llamadas a funciones de usuario
        for (String nombreFuncion : funcionesUsuario.keySet()) {
            Pattern patronLlamada = Pattern.compile("\\b" + nombreFuncion + "\\s*\\(([^)]*)\\)");
            Matcher matcher = patronLlamada.matcher(ln);
            
            while (matcher.find()) {
                System.out.println("DEBUG: Detectada llamada a funcion: " + nombreFuncion);
                
                // 1 OE por la llamada a funcion
                oeTotal = oeTotal.sumar(new ResultadoComplejidad(1));
                System.out.println("DEBUG: +1 OE por la llamada a funcion");
                
                // Calcular OEs de los parametros
                String parametros = matcher.group(1).trim();
                if (!parametros.isEmpty()) {
                    ResultadoComplejidad oeParametros = calcularOEParametros(parametros);
                    oeTotal = oeTotal.sumar(oeParametros);
                    System.out.println("DEBUG: +" + oeParametros + " OEs por parametros");
                }
                
                // Sumar el cuerpo de la funcion
                ResultadoComplejidad oeCuerpoFuncion = funcionesUsuario.get(nombreFuncion).oeTotal;
                oeTotal = oeTotal.sumar(oeCuerpoFuncion);
                System.out.println("DEBUG: +" + oeCuerpoFuncion + " OEs del cuerpo de la funcion");
                
                System.out.println("DEBUG: Total llamada a " + nombreFuncion + ": " + oeTotal + " OEs");
            }
        }
        
        return oeTotal;
    }
    
    // Metodo para calcular OEs de los parametros en una llamada
    private static ResultadoComplejidad calcularOEParametros(String parametros) {
        ResultadoComplejidad oe = new ResultadoComplejidad();
        
        // Dividir por comas para obtener cada parametro
        String[] params = parametros.split(",");
        
        for (String param : params) {
            param = param.trim();
            if (!param.isEmpty()) {
                // Calcular OEs del parametro (expresiones, variables, etc.)
                oe = oe.sumar(calcularOE(param));
            }
        }
        
        return oe;
    }
    
    // Metodo mejorado para calcular OE
    private static ResultadoComplejidad calcularOE(String ln) {
        ResultadoComplejidad oe = new ResultadoComplejidad();
        StringBuilder lnLimpia = new StringBuilder();
        boolean enComillas = false;
        
        // Filtro 1: Eliminar contenido entre comillas
        for (int i = 0; i < ln.length(); i++) {
            char c = ln.charAt(i);
            if (c == '"') {
                enComillas = !enComillas;
                continue;
            }
            if (!enComillas) {
                lnLimpia.append(c);
            }
        }
        
        String lnProcesada = lnLimpia.toString();
        
        // Verificar si es una declaracion return
        if (lnProcesada.trim().startsWith("return")) {
            oe = oe.sumar(new ResultadoComplejidad(1)); // +1 OE por el return
            System.out.println("DEBUG: +1 OE por return");
            
            // Buscar expresion despues de return
            String expresionReturn = lnProcesada.replaceFirst("return", "").trim();
            if (!expresionReturn.isEmpty() && !expresionReturn.equals(";")) {
                // Calcular OEs de la expresion del return
                ResultadoComplejidad oeExpresion = calcularOEExpresion(expresionReturn);
                oe = oe.sumar(oeExpresion);
                System.out.println("DEBUG: +" + oeExpresion + " OEs por expresion en return");
            }
            return oe;
        }
        
        // Primero detectar operadores con asignación y reemplazar para evitar contar dos veces
            Matcher mOpAsign = OP_CON_ASIGNACION.matcher(lnProcesada);
            while (mOpAsign.find()) {
                oe = oe.sumar(new ResultadoComplejidad(2));
            }

            // Reemplaza los operadores con asignación por algún marcador para que no sean detectados como "="
            lnProcesada = mOpAsign.replaceAll("OP_ASIG_TEMP");

            // Luego las asignaciones simples (ya no detectará los +=, -=, etc.)
            Matcher mAsign = ASIGNACION_SIMPLE.matcher(lnProcesada);
            while (mAsign.find()) {
                oe = oe.sumar(new ResultadoComplejidad(1));
            }

            // Comparaciones
            Matcher mComp = COMPARACIONES.matcher(lnProcesada);
            while (mComp.find()) {
                oe = oe.sumar(new ResultadoComplejidad(1));
            }

            // Operaciones aritméticas
                Matcher mArit = OPERACIONES_ARIT.matcher(lnProcesada);
                while (mArit.find()) {
                    oe = oe.sumar(new ResultadoComplejidad(1));
                }

                // AGREGAR ESTA NUEVA SECCIÓN:
                // Accesos a arrays [índice] - solo cuando el índice contiene variables (letras)
                Matcher mArray = ACCESO_ARRAY.matcher(lnProcesada);
                while (mArray.find()) {
                    String indiceCompleto = mArray.group();
                    // Extraer solo el contenido entre corchetes
                    String contenidoIndice = indiceCompleto.substring(1, indiceCompleto.length()-1).trim();

                    // Solo contar OE si el índice contiene al menos una letra (variable)
                    if (contenidoIndice.matches(".*[a-zA-Z].*")) {
                        oe = oe.sumar(new ResultadoComplejidad(1));
                        System.out.println("DEBUG: +1 OE por acceso a array con variable: " + indiceCompleto);
                    } else {
                        System.out.println("DEBUG: Ignorando acceso a array con índice numérico: " + indiceCompleto);
                    }
                }

                // Funciones E/S
                oe = oe.sumar(contarFuncionesES(ln));

                return oe;
            }
    
    // Metodo auxiliar para calcular OEs en expresiones
    private static ResultadoComplejidad calcularOEExpresion(String expresion) {
        ResultadoComplejidad oe = new ResultadoComplejidad();
        expresion = expresion.replace(";", "").trim();
        
        // Contar operaciones aritmeticas
        Matcher mArit = OPERACIONES_ARIT.matcher(expresion);
        while (mArit.find()) {
            oe = oe.sumar(new ResultadoComplejidad(1));
        }
        
        // Contar comparaciones
        Matcher mComp = COMPARACIONES.matcher(expresion);
        while (mComp.find()) {
            oe = oe.sumar(new ResultadoComplejidad(1));
        }
        
        return oe;
    }
    
    // Metodo para contar OE en funciones de E/S (corregido)
    private static ResultadoComplejidad contarFuncionesES(String ln) {
        ResultadoComplejidad oe = new ResultadoComplejidad();

        if (ln.contains("printf(") || ln.contains("scanf(")) {
            oe = oe.sumar(new ResultadoComplejidad(1)); // 1 OE por la funcion

            // Eliminar todo el contenido entre comillas
            StringBuilder lnSinComillas = new StringBuilder();
            boolean enComillas = false;
            for (int i = 0; i < ln.length(); i++) {
                char c = ln.charAt(i);
                if (c == '"') {
                    enComillas = !enComillas;
                    continue;
                }
                if (!enComillas) {
                    lnSinComillas.append(c);
                }
            }

            // Extraer argumentos despues de la coma (si existen)
            int start = lnSinComillas.indexOf("(");
            if (start < 0) return oe;
            start++;
            int end = lnSinComillas.lastIndexOf(")");
            if (end < 0 || start >= end) return oe;

            String args = lnSinComillas.substring(start, end).trim();
            int firstComma = args.indexOf(',');
            if (firstComma >= 0) {
                args = args.substring(firstComma + 1);
            } else {
                args = "";
            }
            
            // Eliminar '&' para variables en scanf
            args = args.replaceAll("&", "");

            // Buscar variables reales
            Pattern varPattern = Pattern.compile("\\b[a-zA-Z_]\\w*\\b");
            Matcher varMatcher = varPattern.matcher(args);
            while (varMatcher.find()) {
                String var = varMatcher.group();
                if (!KEYWORDS.contains(var)) {
                    oe = oe.sumar(new ResultadoComplejidad(1)); // +1 OE por cada variable valida
                }
            }
        }
        return oe;
    }

    // Clase para manejar bloques
    enum TipoBloque { IF, FOR, WHILE }
    
    static class Bloque {
        TipoBloque tipo;
        ResultadoComplejidad oeCondicion;
        ResultadoComplejidad oeCuerpo;
        ResultadoComplejidad oeCuerpoElse;
        boolean enElse;
        boolean cuerpoIfCerrado;
        CicloInfo infoCiclo; // Para ciclos

        // Constructor para IF
        Bloque(TipoBloque tipo, ResultadoComplejidad oeCondicion) {
            this.tipo = tipo;
            this.oeCondicion = oeCondicion;
            this.oeCuerpo = new ResultadoComplejidad();
            this.oeCuerpoElse = new ResultadoComplejidad();
            this.enElse = false;
            this.cuerpoIfCerrado = false;
            this.infoCiclo = null;
        }
        
        // Constructor para ciclos (FOR/WHILE)
        Bloque(TipoBloque tipo, CicloInfo infoCiclo) {
            this.tipo = tipo;
            this.oeCondicion = new ResultadoComplejidad();
            this.oeCuerpo = new ResultadoComplejidad();
            this.oeCuerpoElse = new ResultadoComplejidad();
            this.enElse = false;
            this.cuerpoIfCerrado = false;
            this.infoCiclo = infoCiclo;
        }
    }
    
    // Clase para almacenar informacion de funciones
    static class Funcion {
        String nombre;
        ResultadoComplejidad oeTotal;
        
        Funcion(String nombre) {
            this.nombre = nombre;
            this.oeTotal = new ResultadoComplejidad();
        }
    }
    
    // Clase para almacenar informacion de ciclos
    static class CicloInfo {
        ResultadoComplejidad inicializacion;
        ResultadoComplejidad condicion;
        ResultadoComplejidad incremento;
        String rango; // "n", "10", "n-1", etc.
        
        CicloInfo() {
            this.inicializacion = new ResultadoComplejidad();
            this.condicion = new ResultadoComplejidad();
            this.incremento = new ResultadoComplejidad();
            this.rango = "n";
        }
    }
    
    // Clase mejorada para manejar la complejidad con aritmética robusta
static class ResultadoComplejidad {
    private Map<String, Integer> terminos; // Expresión -> Coeficiente
    
    public ResultadoComplejidad() {
        this.terminos = new HashMap<>();
    }
    
    public ResultadoComplejidad(int constante) {
        this.terminos = new HashMap<>();
        if (constante != 0) {
            this.terminos.put("1", constante);
        }
    }
    
    // Constructor privado para crear con términos específicos
    private ResultadoComplejidad(Map<String, Integer> terminos) {
        this.terminos = new HashMap<>(terminos);
    }
    
    public ResultadoComplejidad sumar(ResultadoComplejidad otro) {
        Map<String, Integer> nuevosTerminos = new HashMap<>(this.terminos);
        
        for (Map.Entry<String, Integer> entry : otro.terminos.entrySet()) {
            String termino = entry.getKey();
            int coeficiente = entry.getValue();
            nuevosTerminos.put(termino, nuevosTerminos.getOrDefault(termino, 0) + coeficiente);
        }
        
        // Eliminar términos con coeficiente 0
        nuevosTerminos.entrySet().removeIf(entry -> entry.getValue() == 0);
        
        return new ResultadoComplejidad(nuevosTerminos);
    }
    
    public ResultadoComplejidad multiplicar(int factor) {
        if (factor == 0) return new ResultadoComplejidad();
        if (factor == 1) return new ResultadoComplejidad(this.terminos);
        
        Map<String, Integer> nuevosTerminos = new HashMap<>();
        for (Map.Entry<String, Integer> entry : this.terminos.entrySet()) {
            nuevosTerminos.put(entry.getKey(), entry.getValue() * factor);
        }
        
        return new ResultadoComplejidad(nuevosTerminos);
    }
    
    public ResultadoComplejidad multiplicarPorVariable(String variable) {
        if (this.esVacio()) return new ResultadoComplejidad();
        
        Map<String, Integer> nuevosTerminos = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : this.terminos.entrySet()) {
            String termino = entry.getKey();
            int coeficiente = entry.getValue();
            
            String nuevoTermino = multiplicarTerminos(termino, variable);
            nuevosTerminos.put(nuevoTermino, coeficiente);
        }
        
        return simplificar(new ResultadoComplejidad(nuevosTerminos));
    }
    
    public ResultadoComplejidad multiplicarPorExpresion(String expresion) {
        if (this.esVacio()) return new ResultadoComplejidad();
        
        // Parse de la expresión
        ResultadoComplejidad expresionParsed = parsearExpresion(expresion);
        return this.multiplicarPor(expresionParsed);
    }
    
    private ResultadoComplejidad multiplicarPor(ResultadoComplejidad otro) {
        if (this.esVacio() || otro.esVacio()) return new ResultadoComplejidad();
        
        Map<String, Integer> nuevosTerminos = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry1 : this.terminos.entrySet()) {
            for (Map.Entry<String, Integer> entry2 : otro.terminos.entrySet()) {
                String termino1 = entry1.getKey();
                String termino2 = entry2.getKey();
                int coef1 = entry1.getValue();
                int coef2 = entry2.getValue();
                
                String nuevoTermino = multiplicarTerminos(termino1, termino2);
                int nuevoCoeficiente = coef1 * coef2;
                
                nuevosTerminos.put(nuevoTermino, 
                    nuevosTerminos.getOrDefault(nuevoTermino, 0) + nuevoCoeficiente);
            }
        }
        
        return simplificar(new ResultadoComplejidad(nuevosTerminos));
    }
    
    private String multiplicarTerminos(String termino1, String termino2) {
        // Casos especiales
        if (termino1.equals("1")) return termino2;
        if (termino2.equals("1")) return termino1;
        
        // Parsear variables y exponentes
        Map<String, Integer> vars1 = parsearVariables(termino1);
        Map<String, Integer> vars2 = parsearVariables(termino2);
        
        // Combinar variables
        Map<String, Integer> varsResultado = new HashMap<>(vars1);
        for (Map.Entry<String, Integer> entry : vars2.entrySet()) {
            String var = entry.getKey();
            int exp = entry.getValue();
            varsResultado.put(var, varsResultado.getOrDefault(var, 0) + exp);
        }
        
        return construirTermino(varsResultado);
    }
    
    private Map<String, Integer> parsearVariables(String termino) {
        Map<String, Integer> variables = new HashMap<>();
        
        if (termino.equals("1")) return variables;
        
        // Patrón para detectar variables con exponentes: var^exp o var
        Pattern pattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)(?:\\^(\\d+))?");
        Matcher matcher = pattern.matcher(termino);
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            String exponente = matcher.group(2);
            int exp = exponente != null ? Integer.parseInt(exponente) : 1;
            
            variables.put(variable, variables.getOrDefault(variable, 0) + exp);
        }
        
        return variables;
    }
    
    private String construirTermino(Map<String, Integer> variables) {
        if (variables.isEmpty()) return "1";
        
        StringBuilder sb = new StringBuilder();
        
        // Ordenar variables alfabéticamente para consistencia
        variables.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String var = entry.getKey();
                int exp = entry.getValue();
                
                if (exp == 0) return; // Ignorar variables con exponente 0
                
                if (sb.length() > 0) sb.append("*");
                
                sb.append(var);
                if (exp > 1) {
                    sb.append("^").append(exp);
                }
            });
        
        return sb.length() > 0 ? sb.toString() : "1";
    }
    
    private ResultadoComplejidad parsearExpresion(String expresion) {
        // Simplificar espacios y paréntesis innecesarios
        expresion = expresion.trim().replaceAll("\\s+", "");
        
        // Casos simples
        if (expresion.matches("\\d+")) {
            return new ResultadoComplejidad(Integer.parseInt(expresion));
        }
        
        if (expresion.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            ResultadoComplejidad resultado = new ResultadoComplejidad();
            resultado.terminos.put(expresion, 1);
            return resultado;
        }
        
        // Manejar suma/resta
        return parsearSumaResta(expresion);
    }
    
    private ResultadoComplejidad parsearSumaResta(String expresion) {
        ResultadoComplejidad resultado = new ResultadoComplejidad();
        
        // Dividir por + y - manteniendo el signo
        Pattern pattern = Pattern.compile("([+-]?)([^+-]+)");
        Matcher matcher = pattern.matcher(expresion);
        
        boolean primerTermino = true;
        while (matcher.find()) {
            String signo = matcher.group(1);
            String termino = matcher.group(2).trim();
            
            if (primerTermino && signo.isEmpty()) {
                signo = "+";
            }
            primerTermino = false;
            
            ResultadoComplejidad terminoParsed = parsearTermino(termino);
            
            if (signo.equals("-")) {
                terminoParsed = terminoParsed.multiplicar(-1);
            }
            
            resultado = resultado.sumar(terminoParsed);
        }
        
        return resultado;
    }
    
    private ResultadoComplejidad parsearTermino(String termino) {
        termino = termino.trim();
        
        // Número simple
        if (termino.matches("\\d+")) {
            return new ResultadoComplejidad(Integer.parseInt(termino));
        }
        
        // Variable simple
        if (termino.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            ResultadoComplejidad resultado = new ResultadoComplejidad();
            resultado.terminos.put(termino, 1);
            return resultado;
        }
        
        // Producto (número * variable, variable * variable, etc.)
        if (termino.contains("*")) {
            String[] factores = termino.split("\\*");
            ResultadoComplejidad resultado = new ResultadoComplejidad(1);
            
            for (String factor : factores) {
                factor = factor.trim();
                if (factor.matches("\\d+")) {
                    resultado = resultado.multiplicar(Integer.parseInt(factor));
                } else {
                    resultado = resultado.multiplicarPorVariable(factor);
                }
            }
            
            return resultado;
        }
        
        // Paréntesis
        if (termino.startsWith("(") && termino.endsWith(")")) {
            return parsearExpresion(termino.substring(1, termino.length() - 1));
        }
        
        // Caso por defecto: tratar como variable
        ResultadoComplejidad resultado = new ResultadoComplejidad();
        resultado.terminos.put(termino, 1);
        return resultado;
    }
    
    private ResultadoComplejidad simplificar(ResultadoComplejidad comp) {
        // Aquí puedes agregar reglas de simplificación más avanzadas
        // Por ejemplo: n^0 = 1, 0*n = 0, etc.
        
        Map<String, Integer> terminosSimplificados = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : comp.terminos.entrySet()) {
            String termino = entry.getKey();
            int coeficiente = entry.getValue();
            
            if (coeficiente == 0) continue;
            
            // Simplificar términos individuales
            termino = simplificarTermino(termino);
            
            if (!termino.equals("0")) {
                terminosSimplificados.put(termino, 
                    terminosSimplificados.getOrDefault(termino, 0) + coeficiente);
            }
        }
        
        // Eliminar términos con coeficiente 0
        terminosSimplificados.entrySet().removeIf(entry -> entry.getValue() == 0);
        
        return new ResultadoComplejidad(terminosSimplificados);
    }
    
    private String simplificarTermino(String termino) {
        if (termino.equals("1")) return "1";
        
        // Parsear y reconstruir para normalizar
        Map<String, Integer> variables = parsearVariables(termino);
        
        // Eliminar variables con exponente 0
        variables.entrySet().removeIf(entry -> entry.getValue() == 0);
        
        return construirTermino(variables);
    }
    
    public static ResultadoComplejidad max(ResultadoComplejidad a, ResultadoComplejidad b) {
    // Si uno está vacío, retornar el otro
    if (a.esVacio() && b.esVacio()) return new ResultadoComplejidad();
    if (a.esVacio()) return b;
    if (b.esVacio()) return a;
    
    // Comparar por orden de complejidad (Big O)
    int ordenA = calcularOrdenComplejidad(a);
    int ordenB = calcularOrdenComplejidad(b);
    
    System.out.println("DEBUG: Comparando max - A: " + a + " (orden " + ordenA + "), B: " + b + " (orden " + ordenB + ")");
    
    if (ordenA > ordenB) {
        System.out.println("DEBUG: A tiene mayor orden de complejidad");
        return a;
    }
    if (ordenB > ordenA) {
        System.out.println("DEBUG: B tiene mayor orden de complejidad");
        return b;
    }
    
    // Si tienen el mismo orden, comparar coeficientes del término dominante
    String terminoDominanteA = obtenerTerminoDominante(a);
    String terminoDominanteB = obtenerTerminoDominante(b);
    
    int coefA = a.terminos.getOrDefault(terminoDominanteA, 0);
    int coefB = b.terminos.getOrDefault(terminoDominanteB, 0);
    
    System.out.println("DEBUG: Mismo orden. Coef A: " + coefA + ", Coef B: " + coefB);
    
    // Si los coeficientes son iguales, sumar ambas expresiones
    if (coefA == coefB && terminoDominanteA.equals(terminoDominanteB)) {
        System.out.println("DEBUG: Términos dominantes iguales, retornando cualquiera de los dos");
        return a;
    }
    
    ResultadoComplejidad resultado = coefA >= coefB ? a : b;
    System.out.println("DEBUG: Resultado max: " + resultado);
    return resultado;
}
    
    private static int calcularOrdenComplejidad(ResultadoComplejidad comp) {
    if (comp.esVacio()) return 0;
    
    int maxOrden = 0;
    
    for (Map.Entry<String, Integer> entry : comp.terminos.entrySet()) {
        String termino = entry.getKey();
        int coeficiente = entry.getValue();
        
        if (coeficiente == 0) continue;
        
        int orden = calcularOrdenTermino(termino);
        maxOrden = Math.max(maxOrden, orden);
    }
    
    return maxOrden;
}
    
    private static int calcularOrdenTermino(String termino) {
    if (termino.equals("1")) return 0;
    
    int sumaExponentes = 0;
    
    // Contar exponentes explícitos
    Pattern pattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\^(\\d+)");
    Matcher matcher = pattern.matcher(termino);
    
    Set<String> variablesContadas = new HashSet<>();
    
    while (matcher.find()) {
        String variable = matcher.group(1);
        int exponente = Integer.parseInt(matcher.group(2));
        sumaExponentes += exponente;
        variablesContadas.add(variable);
    }
    
    // Contar variables sin exponente explícito (exponente = 1)
    Pattern varPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
    Matcher varMatcher = varPattern.matcher(termino);
    
    while (varMatcher.find()) {
        String variable = varMatcher.group(1);
        if (!variablesContadas.contains(variable)) {
            sumaExponentes += 1;
            variablesContadas.add(variable);
        }
    }
    
    return sumaExponentes;
}
    
    private static String obtenerTerminoDominante(ResultadoComplejidad comp) {
        String terminoDominante = "1";
        int maxOrden = -1;
        
        for (String termino : comp.terminos.keySet()) {
            int orden = calcularOrdenTermino(termino);
            if (orden > maxOrden) {
                maxOrden = orden;
                terminoDominante = termino;
            }
        }
        
        return terminoDominante;
    }
    
    public boolean esVacio() {
        return terminos.isEmpty() || terminos.values().stream().allMatch(coef -> coef == 0);
    }
    
    public int obtenerConstante() {
        return terminos.getOrDefault("1", 0);
    }
    
    @Override
    public String toString() {
        if (esVacio()) return "0";
        
        StringBuilder sb = new StringBuilder();
        
        // Ordenar términos por orden de complejidad (descendente)
        List<Map.Entry<String, Integer>> terminosOrdenados = terminos.entrySet().stream()
            .filter(entry -> entry.getValue() != 0)
            .sorted((e1, e2) -> {
                int orden1 = calcularOrdenTermino(e1.getKey());
                int orden2 = calcularOrdenTermino(e2.getKey());
                if (orden1 != orden2) return Integer.compare(orden2, orden1); // Descendente
                return e1.getKey().compareTo(e2.getKey()); // Alfabético
            })
            .collect(Collectors.toList());
        
        for (int i = 0; i < terminosOrdenados.size(); i++) {
            Map.Entry<String, Integer> entry = terminosOrdenados.get(i);
            String termino = entry.getKey();
            int coeficiente = entry.getValue();
            
            if (i > 0) {
                if (coeficiente > 0) {
                    sb.append(" + ");
                } else {
                    sb.append(" - ");
                    coeficiente = -coeficiente;
                }
            } else if (coeficiente < 0) {
                sb.append("-");
                coeficiente = -coeficiente;
            }
            
            if (termino.equals("1")) {
                sb.append(coeficiente);
            } else if (coeficiente == 1) {
                sb.append(termino);
            } else {
                sb.append(coeficiente).append("*").append(termino);
            }
        }
        
        return sb.toString();
    }
    
    // Método para obtener la representación en Big O
    public String toBigO() {
        if (esVacio()) return "O(1)";
        
        String terminoDominante = obtenerTerminoDominante(this);
        if (terminoDominante.equals("1")) return "O(1)";
        
        return "O(" + terminoDominante + ")";
    }
}
}