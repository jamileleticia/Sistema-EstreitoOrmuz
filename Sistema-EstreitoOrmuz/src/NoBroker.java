import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Nó principal do sistema. Gerencia um setor, coordena consenso e despacha drones.
 */
public class NoBroker {
    public static final String RESET = "\u001B[0m", VERMELHO = "\u001B[31m", VERDE = "\u001B[32m",
            AMARELO = "\u001B[33m", AZUL = "\u001B[34m", ROXO = "\u001B[35m", CIANO = "\u001B[36m";

    private int meuId, portaTCP;
    private GerenciadorFrota frota;
    private GerenciadorConsenso consenso;
    private ServicoRede rede;
    private Map<Integer, InetSocketAddress> vizinhos = new ConcurrentHashMap<>();
    private Queue<String[]> filaOcorrencias = new ConcurrentLinkedQueue<>();
    private String missaoEmAndamento = "Nenhuma";

    public NoBroker(int id, int porta) {
        this.meuId = id; this.portaTCP = porta;
        this.frota = new GerenciadorFrota(id);
        this.consenso = new GerenciadorConsenso(id);
        this.rede = new ServicoRede(id, porta);
    }

    /**
     * Inicia as threads de serviço do Broker.
     */
    public void iniciar() {
        System.out.println(CIANO + "[SETOR " + meuId + "] Broker online na porta " + portaTCP + RESET);
        new Thread(this::executarServidorTCP).start(); // Recebe conexões de vizinhos e sensores
        new Thread(this::escutarDescobertaUDP).start(); // Ouve outros brokers e drones na rede
        new Thread(this::anunciarPeriodico).start(); // Anuncia sua existência
        new Thread(this::verificarVizinhosAtivos).start(); // Detecta falhas de outros brokers
        new Thread(this::monitorarSaudeFrota).start(); // Detecta se drones pararam de reportar
    }

    /**
     * Verifica periodicamente se os drones da frota estão ativos.
     * Caso um drone em missão falhe, aciona lógica de replanejamento.
     */
    private void monitorarSaudeFrota() {
        while (true) {
            try {
                Thread.sleep(5000);
                List<Drone> inativos = frota.limparDronesInativos();
                for (Drone d : inativos) {
                    if (d.idSetorUsando == meuId) {
                        System.err.println(VERMELHO + "[FALHA ⚠️] Drone " + d.id + " parou de enviar sinal durante missão!" + RESET);
                        tratarFalhaDrone(d, consenso.getPrioridadeAtual(), missaoEmAndamento);
                    } else {
                        reportarFalhaAoMonitor(d.id);
                        notificarMudancaFrota(d);
                    }
                }
            } catch (Exception e) {}
        }
    }

    /**
     * Servidor UDP para descoberta dinâmica de componentes na rede.
     */
    private void escutarDescobertaUDP() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(8888));
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p);
                String msgStr = new String(p.getData(), 0, p.getLength()).trim();
                InetAddress ipOrigem = p.getAddress();

                if (msgStr.startsWith("BROKER_ALIVE")) {
                    String[] partes = msgStr.split(":");
                    registrarVizinho(Integer.parseInt(partes[1]), ipOrigem.getHostAddress(), Integer.parseInt(partes[2]));
                }
                else if (msgStr.startsWith("DRONE_ALIVE")) {
                    String[] partes = msgStr.split(":");
                    frota.registrarOuAtualizarDrone(Integer.parseInt(partes[1]), Integer.parseInt(partes[2]), partes[3], ipOrigem);
                }
            }
        } catch (Exception e) {}
    }

    private synchronized void registrarVizinho(int id, String ip, int porta) {
        if (id != meuId && !vizinhos.containsKey(id)) {
            vizinhos.put(id, new InetSocketAddress(ip, porta));
            System.out.println(AZUL + "[REDE 🌐] Setor " + id + " identificado na malha." + RESET);
            // Se eu estava esperando permissão, incluo este novo vizinho na lista de pendências
            if (consenso.getEstado().equals("QUERENDO")) {
                consenso.getRespostasOkPendentes().add(id);
                enviarParaVizinho(id, new Mensagem("REQUISICAO", meuId, portaTCP, consenso.getTimestampRequisicao(), consenso.getPrioridadeAtual()));
            }
        }
    }

    /**
     * Inicia o processo de obtenção de exclusão mútua para realizar uma missão.
     */
    private void solicitarAcessoDistribuido(int prioridade, String nomeMissao) {
        synchronized (consenso) {
            // Se já estiver processando algo, coloca na fila local
            if (!consenso.getEstado().equals("LIBERADO")) {
                filaOcorrencias.add(new String[]{String.valueOf(prioridade), nomeMissao});
                return;
            }
            this.missaoEmAndamento = nomeMissao;
            consenso.setEstado("QUERENDO");
            consenso.setPrioridadeAtual(prioridade);
            consenso.setTimestampRequisicao(consenso.getRelogio().tique());
            consenso.getRespostasOkPendentes().clear();
            consenso.getRespostasOkPendentes().addAll(vizinhos.keySet());
        }

        System.out.println(AMARELO + "[CONSENSO 🤝] Solicitando permissão para: '" + nomeMissao + "' (Prio: " + prioridade + ")" + RESET);
        if (vizinhos.isEmpty()) {
            new Thread(this::tentarReservarRecurso).start();
        } else {
            Mensagem req = new Mensagem("REQUISICAO", meuId, portaTCP, consenso.getTimestampRequisicao(), prioridade);
            req.missao = nomeMissao;
            for (Integer idVizu : vizinhos.keySet()) enviarParaVizinho(idVizu, req);
        }
    }

    /**
     * Tenta selecionar um drone e despachá-lo após receber todos os "OKs" dos vizinhos.
     */
    private void tentarReservarRecurso() {
        synchronized (this) {
            consenso.setEstado("OCUPADO");
            Drone d = frota.selecionarDroneParaUso();
            if (d != null) {
                d.status = "OCUPADO";
                d.idSetorUsando = meuId;
                notificarMudancaFrota(d); // Informa aos outros que este drone está em uso
                System.out.println(VERDE + "[DRONE 🚁] >>> OPERANDO: Drone " + d.id + " em missão '" + missaoEmAndamento + "'" + RESET);
                despacharDroneFisico(d, consenso.getPrioridadeAtual(), missaoEmAndamento);
            } else {
                // Se não há drones, volta a missão para a fila local
                System.out.println(VERMELHO + "[ALERTA ❌] Frota esgotada! " + meuId + " aguardando drone liberar..." + RESET);
                filaOcorrencias.add(new String[]{String.valueOf(consenso.getPrioridadeAtual()), missaoEmAndamento});
            }
            // Após tentar usar o recurso, libera as respostas adiadas para os vizinhos
            List<Mensagem> fila = consenso.liberarEObterFila();
            for (Mensagem m : fila) {
                enviarParaVizinho(m.idRemetente, new Mensagem("RESPOSTA_OK", meuId, portaTCP, consenso.getRelogio().tique(), 0));
            }
        }
    }

    /**
     * Conecta diretamente ao drone via TCP para enviar a ordem de decolagem.
     */
    private void despacharDroneFisico(Drone d, int prio, String missao) {
        new Thread(() -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(d.ip, d.porta), 3000);
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(s.getInputStream());

                Mensagem ordem = new Mensagem("ORDEM_DECOLO", meuId, portaTCP, 0, prio);
                ordem.missao = missao;
                out.writeObject(ordem);
                out.flush();

                Mensagem resposta = (Mensagem) in.readObject();
                if ("ORDEM_RECEBIDA".equals(resposta.tipo)) {
                    System.out.println(AMARELO + "[RADIO 📡] ACK recebido do Drone " + d.id + ". Decolagem confirmada!" + RESET);
                    try {
                        // Mantém conexão aberta para monitorar vida do drone via Socket
                        while (s.getInputStream().read() != -1) { }
                    } catch (IOException e) {
                        tratarFalhaDrone(d, prio, missao);
                    }
                }
            } catch (Exception e) {
                tratarFalhaDrone(d, prio, missao);
            }
        }).start();
    }

    /**
     * Lógica de replanejamento: quando um drone falha, a missão é re-enfileirada para outro drone.
     */
    private synchronized void tratarFalhaDrone(Drone d, int prio, String missao) {
        if (d.status.equals("INATIVO") && d.idSetorUsando == -1) return;

        System.err.println(VERMELHO + "[STATUS] MORTO - Drone " + d.id + " falhou!" + RESET);
        System.out.println(AMARELO + "[REPLANEJAR] Missão '" + missao + "' voltando para a fila local." + RESET);

        d.status = "INATIVO";
        d.idSetorUsando = -1;
        d.missao = "Nenhuma";

        notificarMudancaFrota(d);
        reportarFalhaAoMonitor(d.id);

        filaOcorrencias.add(new String[]{String.valueOf(prio), missao});
        new Thread(this::verificarFilaInterna).start();
    }

    /**
     * Envia informações de falha para o Monitor visual.
     */
    private void reportarFalhaAoMonitor(int droneId) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String dados = droneId + ";INATIVO;-1;Nenhuma";
            byte[] b = dados.getBytes();
            DatagramPacket p = new DatagramPacket(b, b.length, InetAddress.getByName("255.255.255.255"), 9999);
            socket.send(p);
        } catch (Exception e) {}
    }

    /**
     * Processador de mensagens TCP recebidas de outros brokers, sensores ou drones.
     */
    private void processarEntrada(Socket s) {
        try (ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            Mensagem msg = (Mensagem) in.readObject();
            consenso.getRelogio().atualizar(msg.timestamp);

            switch (msg.tipo) {
                case "ALERTA_SENSOR":
                    System.out.println(ROXO + "[OCORRÊNCIA 🚨] " + msg.missao + " Detectada! (Prio: " + msg.prioridade + ")" + RESET);
                    solicitarAcessoDistribuido(msg.prioridade, msg.missao);
                    break;
                case "REQUISICAO":
                    if (consenso.deveResponderOkAgora(msg)) {
                        enviarParaVizinho(msg.idRemetente, new Mensagem("RESPOSTA_OK", meuId, portaTCP, consenso.getRelogio().tique(), 0));
                    } else {
                        consenso.adicionarAFila(msg);
                    }
                    break;
                case "RESPOSTA_OK":
                    consenso.getRespostasOkPendentes().remove(msg.idRemetente);
                    if (consenso.getRespostasOkPendentes().isEmpty() && consenso.getEstado().equals("QUERENDO")) {
                        new Thread(this::tentarReservarRecurso).start();
                    }
                    break;
                case "ATUALIZACAO_DRONE":
                    frota.registrarOuAtualizarDrone(msg.idDrone, 0, msg.conteudoExtra, null);
                    if (msg.conteudoExtra.equals("DISPONIVEL")) verificarFilaInterna();
                    break;
                case "MISSAO_CONCLUIDA":
                    System.out.println(CIANO + "[OPERACIONAL ✅] Missão '" + msg.missao + "' finalizada por Drone " + msg.idRemetente + "." + RESET);
                    consenso.setEstado("LIBERADO");
                    verificarFilaInterna();
                    break;
            }
        } catch (Exception e) {}
    }

    private synchronized void verificarFilaInterna() {
        if (!filaOcorrencias.isEmpty() && consenso.getEstado().equals("LIBERADO")) {
            String[] prox = filaOcorrencias.poll();
            solicitarAcessoDistribuido(Integer.parseInt(prox[0]), prox[1]);
        }
    }

    /**
     * Notifica os outros brokers sobre a mudança de estado de um drone (Ex: Ocupado/Disponível).
     */
    private void notificarMudancaFrota(Drone d) {
        Mensagem m = new Mensagem("ATUALIZACAO_DRONE", meuId, portaTCP, consenso.getRelogio().tique(), 0);
        m.idDrone = d.id; m.conteudoExtra = d.status;
        for (Integer idVizu : vizinhos.keySet()) enviarParaVizinho(idVizu, m);
    }

    private void executarServidorTCP() {
        try (ServerSocket servidor = new ServerSocket(portaTCP)) {
            while (true) { Socket s = servidor.accept(); new Thread(() -> processarEntrada(s)).start(); }
        } catch (IOException e) {}
    }

    private void enviarParaVizinho(int id, Mensagem m) {
        try { InetSocketAddress a = vizinhos.get(id); if (a != null) rede.enviarMensagemTCP(a, m); } catch (Exception e) { removerVizinho(id); }
    }

    private void anunciarPeriodico() {
        while (true) { rede.anunciarPresencaUDP(); try { Thread.sleep(5000); } catch (Exception e) {} }
    }

    private void verificarVizinhosAtivos() {
        while (true) {
            try {
                Thread.sleep(5000);
                for (Integer id : new ArrayList<>(vizinhos.keySet())) {
                    try (Socket s = new Socket()) { s.connect(vizinhos.get(id), 500); }
                    catch (IOException e) { removerVizinho(id); }
                }
            } catch (Exception e) {}
        }
    }

    /**
     * Remove broker falho e verifica se isso libera o consenso pendente.
     */
    private void removerVizinho(int id) {
        if (vizinhos.containsKey(id)) {
            vizinhos.remove(id);
            System.err.println(VERMELHO + "[REDE ⚠️] Setor " + id + " falhou!" + RESET);
            if (consenso.getEstado().equals("QUERENDO")) {
                consenso.getRespostasOkPendentes().remove(id);
                if (consenso.getRespostasOkPendentes().isEmpty()) new Thread(this::tentarReservarRecurso).start();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) return;
        new NoBroker(Integer.parseInt(args[0]), Integer.parseInt(args[1])).iniciar();
    }
}