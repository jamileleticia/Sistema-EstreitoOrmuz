import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.net.InetAddress;

/**
 * Mantém o estado global da frota compartilhada de drones dentro de cada Broker.
 */
public class GerenciadorFrota {
    private Map<Integer, Drone> frota = new ConcurrentHashMap<>();
    private int meuId;
    private Random random = new Random();

    public GerenciadorFrota(int meuId) { this.meuId = meuId; }

    /**
     * Registra novos drones ou atualiza o status de drones conhecidos.
     */
    public void registrarOuAtualizarDrone(int id, int porta, String status, InetAddress ip) {
        Drone d = frota.get(id);
        if (d == null) {
            // Lógica para definir a base de origem (estratégia de distribuição)
            int base = ((id - 1) % 4) + 1;
            d = new Drone(id, base, porta, ip);
            frota.put(id, d);
        } else {
            d.status = status;
            if (ip != null) d.ip = ip;
            if (porta > 0) d.porta = porta;
        }
        d.ultimaVezVisto = System.currentTimeMillis();
    }

    /**
     * Identifica drones que pararam de enviar sinal (timeout de 15s).
     * @return Lista de drones que foram marcados como inativos.
     */
    public List<Drone> limparDronesInativos() {
        List<Drone> removidos = new ArrayList<>();
        long agora = System.currentTimeMillis();
        for (Drone d : frota.values()) {
            if (!d.status.equals("INATIVO") && (agora - d.ultimaVezVisto > 15000)) {
                d.status = "INATIVO";
                d.idSetorUsando = -1;
                d.missao = "Nenhuma";
                removidos.add(d);
            }
        }
        return removidos;
    }

    /**
     * Seleciona um drone livre, priorizando drones que pertencem à base do setor atual.
     */
    public Drone selecionarDroneParaUso() {
        List<Drone> disponiveis = frota.values().stream()
                .filter(d -> "DISPONIVEL".equals(d.status))
                .collect(Collectors.toList());

        if (disponiveis.isEmpty()) return null;

        // Tenta pegar primeiro da própria base para otimizar logística
        List<Drone> locais = disponiveis.stream().filter(d -> d.idBase == meuId).collect(Collectors.toList());
        if (!locais.isEmpty()) return locais.get(random.nextInt(locais.size()));

        // Se não houver locais, pega qualquer um disponível na frota compartilhada
        return disponiveis.get(random.nextInt(disponiveis.size()));
    }

    public Map<Integer, Drone> getFrota() { return frota; }
}