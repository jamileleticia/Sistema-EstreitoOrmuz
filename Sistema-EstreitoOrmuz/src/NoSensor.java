import java.io.*;
import java.net.*;
import java.util.Random;

/**
 * Simula um sensor marítimo que detecta ocorrências e envia alertas ao seu Broker local.
 */
public class NoSensor {
    private int id, brokerId, portaBroker;
    private String ipBrokerDetectado = null;
    private String[] tipos = {"Bloqueio Parcial", "Falha Sinalização", "Objeto Não Identificado", "Risco Ambiental"};

    public NoSensor(int id, int brokerId, int portaBroker) {
        this.id = id;
        this.brokerId = brokerId;
        this.portaBroker = portaBroker;
    }

    /**
     * Ciclo de vida do sensor: descobre o broker e gera alertas aleatórios.
     */
    public void iniciar() {
        System.out.println("\u001B[36m[SENSOR " + id + "] Escutando rede para achar Broker " + brokerId + "...\u001B[0m");
        descobrirBroker();

        Random r = new Random();
        while (true) {
            try {
                // Simula intervalo entre detecções de ocorrências
                Thread.sleep(15000 + r.nextInt(15000));
                int prio = r.nextInt(5) + 1; // Prioridade de 1 a 5
                String tipo = tipos[r.nextInt(tipos.length)];

                // Tenta enviar o alerta via TCP para o Broker responsável pelo seu setor
                try (Socket s = new Socket(ipBrokerDetectado, portaBroker)) {
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    Mensagem msg = new Mensagem("ALERTA_SENSOR", id, 0, 0, prio);
                    msg.missao = tipo;
                    out.writeObject(msg);
                    System.out.println("\u001B[33m[SENSOR " + id + "] ⚠️ Alerta '" + tipo + "' enviado ao Broker " + brokerId + "\u001B[0m");
                }
            } catch (Exception e) {
                // Se o Broker cair, volta para a fase de descoberta
                System.err.println("[SENSOR " + id + "] Conexão perdida com Broker. Re-escutando rede...");
                ipBrokerDetectado = null;
                descobrirBroker();
            }
        }
    }

    /**
     * Escuta broadcasts UDP na porta 8888 para identificar o IP do seu Broker.
     */
    private void descobrirBroker() {
        try (DatagramSocket ds = new DatagramSocket(null)) {
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(8888));
            byte[] buf = new byte[1024];
            while (ipBrokerDetectado == null) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ds.receive(p);
                String msg = new String(p.getData(), 0, p.getLength()).trim();

                // Verifica se a mensagem de "batimento" pertence ao broker deste sensor
                if (msg.startsWith("BROKER_ALIVE")) {
                    String[] partes = msg.split(":");
                    if (Integer.parseInt(partes[1]) == brokerId) {
                        ipBrokerDetectado = p.getAddress().getHostAddress();
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void main(String[] args) {
        new NoSensor(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2])).iniciar();
    }
}