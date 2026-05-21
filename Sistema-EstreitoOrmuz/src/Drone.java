import java.io.Serializable;
import java.net.InetAddress;

/**
 * Representa a entidade Drone no sistema.
 * Armazena o estado, localização de rede e informações de controle.
 */
public class Drone implements Serializable {
    public int id;
    public int idBase; // Identifica a qual base/setor o drone pertence originalmente
    public String status = "INATIVO"; // Estados: INATIVO, DISPONIVEL, OCUPADO
    public String missao = "Nenhuma";
    public int idSetorUsando = -1; // ID do Broker que detém o uso do drone no momento
    public int porta;
    public InetAddress ip;
    public long ultimaVezVisto; // Timestamp para controle de timeout (detecção de falhas)

    public Drone(int id, int idBase, int porta, InetAddress ip) {
        this.id = id;
        this.idBase = idBase;
        this.porta = porta;
        this.ip = ip;
        this.ultimaVezVisto = System.currentTimeMillis();
    }
}