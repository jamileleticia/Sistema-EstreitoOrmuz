import java.io.*;
import java.net.*;

/**
 * Utilitário para abstrair operações de rede TCP e UDP.
 */
public class ServicoRede {
    private int meuId;
    private int portaTCP;

    public ServicoRede(int meuId, int portaTCP) {
        this.meuId = meuId;
        this.portaTCP = portaTCP;
    }

    /**
     * Envia uma mensagem serializada via Socket TCP.
     */
    public void enviarMensagemTCP(InetSocketAddress destino, Mensagem msg) throws IOException {
        if (destino == null) return;
        try (Socket s = new Socket()) {
            s.connect(destino, 1000); // Timeout de conexão de 1s
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            out.writeObject(msg);
        }
    }

    /**
     * Emite um sinal via broadcast UDP para anunciar a presença do Broker na malha.
     */
    public void anunciarPresencaUDP() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String anuncio = "BROKER_ALIVE:" + meuId + ":" + portaTCP;
            byte[] b = anuncio.getBytes();
            DatagramPacket p = new DatagramPacket(b, b.length, InetAddress.getByName("255.255.255.255"), 8888);
            socket.send(p);
        } catch (Exception e) {}
    }
}