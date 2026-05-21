/**
 * Implementação de Relógio Lógico de Lamport para ordenação de eventos em sistemas distribuídos.
 */
public class RelogioLamport {
    private long valor = 0;

    /**
     * Incrementa o relógio local (evento interno ou envio de mensagem).
     */
    public synchronized long tique() { return ++valor; }

    public synchronized long pegarValor() { return valor; }

    /**
     * Ajusta o relógio local com base no timestamp recebido em uma mensagem (evento de recepção).
     */
    public synchronized void atualizar(long valorRecebido) {
        valor = Math.max(valor, valorRecebido) + 1;
    }
}