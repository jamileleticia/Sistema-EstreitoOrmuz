import java.io.*;
import java.net.*;

/**
 * Simula o software embarcado no Drone.
 */
public class NoDrone {
    private int id, porta;
    private String status = "INATIVO";
    private String missaoAtual = "Nenhuma";
    private int idSetorUsando = -1;

    public NoDrone(int id, int porta) {
        this.id = id; this.porta = porta;
    }

    public void iniciar() {
        System.out.println("\u001B[37m[DRONE " + id + "] Sistema carregando na porta " + porta + "...\u001B[0m");
        new Thread(this::escutarComandos).start();
        new Thread(this::anunciarPeriodico).start();

        try { Thread.sleep(3000); } catch (Exception e) {}
        this.status = "DISPONIVEL";
        System.out.println("\u001B[32m[DRONE " + id + "] Sistema pronto. DISPONÍVEL.\u001B[0m");
    }

    /**
     * Escuta ordens TCP vindas dos Brokers.
     */
    private void escutarComandos() {
        try (ServerSocket server = new ServerSocket(porta)) {
            while (true) {
                try (Socket s = server.accept();
                     ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

                    Mensagem msg = (Mensagem) in.readObject();
                    if ("ORDEM_DECOLO".equals(msg.tipo)) {
                        // Protocolo de rádio: envia confirmação (ACK) antes de decolar
                        System.out.println("\u001B[33m[ACK 📩] Confirmando decolagem para Setor " + msg.idRemetente + "...\u001B[0m");
                        out.writeObject(new Mensagem("ORDEM_RECEBIDA", id, porta, 0, 0));
                        out.flush();

                        executarMissao(msg, s.getInetAddress());
                    }
                } catch (Exception e) {}
            }
        } catch (IOException e) {}
    }

    /**
     * Simula a execução de uma missão de monitoramento.
     */
    private void executarMissao(Mensagem msg, InetAddress ipBroker) {
        this.status = "OCUPADO";
        this.missaoAtual = msg.missao;
        this.idSetorUsando = msg.idRemetente;
        System.out.println("\u001B[31m[DRONE " + id + "] 🚀 DECOLANDO: Missão '" + missaoAtual + "' para Setor " + idSetorUsando + "\u001B[0m");
        forçarAnuncio();

        // Simula o tempo de voo/monitoramento
        try { Thread.sleep(10000); } catch (InterruptedException e) {}

        this.status = "DISPONIVEL";
        String missaoFinalizada = this.missaoAtual;
        this.missaoAtual = "Nenhuma";
        this.idSetorUsando = -1;
        System.out.println("\u001B[32m[DRONE " + id + "] ✅ Missão concluída. Reportando ao Broker...\u001B[0m");
        forçarAnuncio();

        // Informa ao Broker que a missão terminou para liberar a exclusão mútua
        try (Socket s = new Socket(ipBroker, msg.portaRemetente)) {
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            Mensagem report = new Mensagem("MISSAO_CONCLUIDA", id, porta, 0, 0);
            report.missao = missaoFinalizada;
            out.writeObject(report);
        } catch (Exception e) {}
    }

    private void anunciarPeriodico() {
        while (true) { forçarAnuncio(); try { Thread.sleep(5000); } catch (Exception e) {} }
    }

    /**
     * Envia sinais de vida (Heartbeat) para o Monitor (UDP 9999) e Brokers (UDP 8888).
     */
    private void forçarAnuncio() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            // Formato para o Monitor visual
            String dadosMonitor = id + ";" + status + ";" + idSetorUsando + ";" + missaoAtual;
            socket.send(new DatagramPacket(dadosMonitor.getBytes(), dadosMonitor.length(), InetAddress.getByName("255.255.255.255"), 9999));
            // Formato para os Brokers
            String anuncioBroker = "DRONE_ALIVE:" + id + ":" + porta + ":" + status;
            socket.send(new DatagramPacket(anuncioBroker.getBytes(), anuncioBroker.length(), InetAddress.getByName("255.255.255.255"), 8888));
        } catch (Exception e) {}
    }

    public static void main(String[] args) {
        if (args.length < 2) return;
        new NoDrone(Integer.parseInt(args[0]), Integer.parseInt(args[1])).iniciar();
    }
}