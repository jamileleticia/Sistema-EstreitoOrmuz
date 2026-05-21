import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementa a lógica do algoritmo de Ricart-Agrawala para Exclusão Mútua Distribuída.
 * Garante que apenas um setor acesse a frota compartilhada respeitando prioridades.
 */
public class GerenciadorConsenso {
    private int meuId;
    private String estado = "LIBERADO"; // Estados: LIBERADO, QUERENDO, OCUPADO
    private RelogioLamport relogio = new RelogioLamport();
    private long timestampRequisicao; // Timestamp de quando a permissão foi solicitada
    private int prioridadeAtual;

    private Set<Integer> respostasOkPendentes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private List<Mensagem> filaEsperaRicart = new ArrayList<>();

    public GerenciadorConsenso(int meuId) { this.meuId = meuId; }

    /**
     * Lógica central de decisão de Ricart-Agrawala com prioridades.
     * Define se este processo deve responder "OK" imediatamente a um vizinho ou adiar a resposta.
     */
    public synchronized boolean deveResponderOkAgora(Mensagem msgIn) {
        if (estado.equals("LIBERADO")) return true;
        if (estado.equals("OCUPADO")) return false;

        if (estado.equals("QUERENDO")) {
            // Regra 1: Prioridade da ocorrência (conforme requisito do PDF)
            if (msgIn.prioridade > this.prioridadeAtual) return true;
            if (msgIn.prioridade < this.prioridadeAtual) return false;

            // Regra 2: Empate em prioridade usa o Timestamp Lamport (quem pediu antes)
            if (msgIn.timestamp < this.timestampRequisicao) return true;
            if (msgIn.timestamp > this.timestampRequisicao) return false;

            // Regra 3: Desempate final pelo ID do processo
            return msgIn.idRemetente < meuId;
        }
        return false;
    }

    public synchronized void adicionarAFila(Mensagem msg) { filaEsperaRicart.add(msg); }

    /**
     * Libera o recurso e retorna a lista de mensagens cujas respostas foram adiadas.
     */
    public synchronized List<Mensagem> liberarEObterFila() {
        this.estado = "LIBERADO";
        List<Mensagem> paraResponder = new ArrayList<>(filaEsperaRicart);
        filaEsperaRicart.clear();
        return paraResponder;
    }

    public synchronized String getEstado() { return estado; }
    public synchronized void setEstado(String estado) { this.estado = estado; }
    public RelogioLamport getRelogio() { return relogio; }
    public long getTimestampRequisicao() { return timestampRequisicao; }
    public void setTimestampRequisicao(long ts) { this.timestampRequisicao = ts; }
    public int getPrioridadeAtual() { return prioridadeAtual; }
    public void setPrioridadeAtual(int p) { this.prioridadeAtual = p; }
    public Set<Integer> getRespostasOkPendentes() { return respostasOkPendentes; }
}