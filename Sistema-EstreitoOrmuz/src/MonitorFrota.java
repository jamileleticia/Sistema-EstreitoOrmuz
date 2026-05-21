import java.net.*;
import java.util.*;

/**
 * Console centralizado para visualização do estado de todos os drones em tempo real.
 */
public class MonitorFrota {
    private static Map<Integer, String[]> dadosDrones = new TreeMap<>();

    public static void main(String[] args) {
        // Inicializa a tabela com 8 drones inativos
        for (int i = 1; i <= 8; i++) {
            dadosDrones.put(i, new String[]{"INATIVO", "Nenhum", "Nenhuma"});
        }

        try (DatagramSocket socket = new DatagramSocket(9999)) {
            byte[] buffer = new byte[1024];

            limparTerminal();
            desenharTabela();

            while (true) {
                // Recebe atualizações de status via UDP dos drones ou brokers
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength());

                String[] partes = msg.split(";");
                int id = Integer.parseInt(partes[0]);
                String status = partes[1];
                String setor = partes[2].equals("-1") ? "Nenhum" : "Setor " + partes[2];
                String missao = partes[3];

                dadosDrones.put(id, new String[]{status, setor, missao});

                limparTerminal();
                desenharTabela();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Usa códigos ANSI para limpar o terminal e evitar scroll infinito.
     */
    private static void limparTerminal() {
        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();
    }

    /**
     * Renderiza a tabela de monitoramento formatada no console.
     */
    private static void desenharTabela() {
        StringBuilder sb = new StringBuilder();
        sb.append("======================================================================\n");
        sb.append("                  MONITORAMENTO - ESTREITO DE ORMUZ                       \n");
        sb.append("======================================================================\n");
        sb.append(String.format("| %-8s | %-12s | %-12s | %-25s |\n", "DRONE", "STATUS", "SETOR", "MISSÃO ATUAL"));
        sb.append("----------------------------------------------------------------------\n");

        for (Map.Entry<Integer, String[]> entry : dadosDrones.entrySet()) {
            String[] info = entry.getValue();
            String status = info[0];

            String cor;
            if (status.equals("OCUPADO")) cor = "\u001B[31m"; // Vermelho
            else if (status.equals("DISPONIVEL")) cor = "\u001B[32m"; // Verde
            else cor = "\u001B[37m"; // Branco/Cinza

            String reset = "\u001B[0m";

            sb.append(String.format("| Drone %-2d | %s%-12s%s | %-12s | %-25s |\n",
                    entry.getKey(), cor, status, reset, info[1], info[2]));
        }
        sb.append("======================================================================\n");
        sb.append("Última atualização: ").append(new Date()).append("\n");

        System.out.print(sb.toString());
        System.out.flush();
    }
}