import java.io.Serializable;

/**
 * Protocolo de comunicação unificado do sistema.
 * Utilizado para alertas de sensores, consenso entre brokers e controle de drones.
 */
public class Mensagem implements Serializable {
    private static final long serialVersionUID = 1L;
    public String tipo; // REQUISICAO, RESPOSTA_OK, ALERTA_SENSOR, ATUALIZACAO_DRONE, etc.
    public String missao; // Descrição textual da ocorrência
    public int idRemetente;
    public int portaRemetente;
    public long timestamp; // Timestamp de Lamport para ordenação de eventos
    public int idDrone; // Usado em mensagens de atualização de frota
    public String conteudoExtra; // Campo versátil para informações adicionais
    public int prioridade; // Nível de criticidade (1 a 5)

    public Mensagem(String tipo, int idRemetente, int portaRemetente, long timestamp, int prioridade) {
        this.tipo = tipo;
        this.idRemetente = idRemetente;
        this.portaRemetente = portaRemetente;
        this.timestamp = timestamp;
        this.prioridade = prioridade;
    }
}