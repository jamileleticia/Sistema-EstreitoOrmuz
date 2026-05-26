# Problema 2: Desbloqueio do Estreito de Ormuz - Coordenação Distribuída de Drones

**Autor:** Jamile Letícia Carneiro da Silva  
**Tutor:** Prof. Dr. José Amancio Macedo Santos  
**Disciplina:** TEC502 - Sistemas Distribuídos  
**Instituição:** UEFS - Universidade Estadual de Feira de Santana  

---

## 📝 Descrição do Projeto
Este sistema foi desenvolvido para coordenar uma frota compartilhada de drones autônomos no Estreito de Ormuz, utilizando uma arquitetura **Peer-to-Peer (P2P) descentralizada**. A solução elimina pontos únicos de falha ao distribuir a inteligência entre múltiplos gerenciadores de setor (Brokers), que cooperam via protocolos de consenso para atender ocorrências críticas.

Diferente de modelos centralizados, este projeto implementa a exclusão mútua distribuída através do algoritmo de **Ricart-Agrawala** adaptado para falhas, utilizando **Relógios Lógicos de Lamport** para a ordenação de eventos e priorização de missões.

---

## 📁 Estrutura de Diretórios
O projeto é composto por um conjunto de classes Java que implementam a lógica de rede e os scripts de automação para ambiente Docker:

*   **Arquivos Java (Lógica de Sistema):**
    *   `NoBroker.java`: Nó gerenciador de setor (inteligência de consenso e despacho).
    *   `NoDrone.java`: Emulador do software embarcado no drone.
    *   `NoSensor.java`: Emulador de sensores marítimos e detecção de ocorrências.
    *   `MonitorFrota.java`: Console visual para monitoramento do estado global da frota.
    *   `GerenciadorConsenso.java`: Implementação do algoritmo de Ricart-Agrawala com prioridades.
    *   `GerenciadorFrota.java`: Gestão local do estado dos drones e detecção de inatividade.
    *   `RelogioLamport.java`: Gerenciamento do tempo lógico para ordenação de mensagens.
    *   `Mensagem.java`: Protocolo unificado de comunicação (TCP/UDP).
    *   `ServicoRede.java`: Abstração de operações de socket e broadcast.
*   **Scripts de Automação (`.sh`):**
    *   `inicializador.sh`: Script genérico para rodar qualquer componente via Docker.
    *   `rodar_drones.sh`: Automatiza a subida de 8 instâncias de drones.
    *   `rodar_sensores.sh`: Automatiza a subida de 8 sensores distribuídos entre os brokers.

---

### 🧩 Descrição dos Componentes

*   **Brokers de Setor:** Atuam como os coordenadores da malha. Recebem alertas de sensores, negociam a exclusão mútua com vizinhos e enviam ordens de decolagem.
*   **Drones:** Unidades de execução que reportam *heartbeats* (sinais de vida) constantes e executam missões quando recebem ordens confirmadas via TCP.
*   **Sensores Marítimos:** Dispositivos que geram alertas aleatórios com diferentes níveis de prioridade (1 a 5) e os encaminham ao broker responsável pelo seu setor.
*   **Monitor Visual:** Console centralizado que escuta sinais UDP (porta 9999) para renderizar a tabela de status de todos os drones em tempo real.

---

## ⚙️ Arquitetura de Comunicação
*   **TCP (Portas 7071-7074):** Comunicação entre Brokers para negociação de consenso e recebimento de alertas de sensores.
*   **TCP (Portas 8081-8088):** Envio de ordens críticas de decolagem dos Brokers para os Drones (com confirmação via ACK).
*   **UDP Broadcast (Porta 8888):** Descoberta dinâmica de Brokers e Drones na rede (*Service Discovery*) e batimentos de vida (*Heartbeats*).
*   **UDP Broadcast (Porta 9999):** Atualização do Monitor visual de frota.

---

## 🚀 Como Executar
O sistema utiliza uma imagem Docker pré-configurada. Para garantir a descoberta dinâmica, a execução deve seguir a ordem abaixo:

### 1. Preparação do Ambiente
Baixe a imagem oficial e conceda permissão de execução aos scripts:
```bash
docker pull jamileleticia/sistema-estreitoormuz:latest
chmod +x inicializador.sh rodar_drones.sh rodar_sensores.sh
```

### 2. Iniciar o Monitor de Frota
Em um terminal dedicado, inicie o painel de visualização:
```bash
./inicializador.sh MonitorFrota
```

### 3. Iniciar os Brokers (Setores 1 a 4)
Abra quatro terminais (um para cada broker). O sistema suporta a entrada e saída dinâmica de setores:
```bash
./inicializador.sh NoBroker 1 7071
./inicializador.sh NoBroker 2 7072
./inicializador.sh NoBroker 3 7073
./inicializador.sh NoBroker 4 7074
```

### 4. Iniciar a Frota de Drones
Utilize o script de automação para subir 8 drones simultaneamente:
```bash
./rodar_drones.sh
```

### 5. Iniciar os Sensores
Inicie os sensores que gerarão a carga de trabalho para os brokers:
```bash
./rodar_sensores.sh
```

---

## 🤖 Lógica de Consenso e Resiliência
O sistema opera sob regras estritas de coordenação distribuída:
1.  **Exclusão Mútua:** Um broker só despacha um drone após receber "OK" de todos os vizinhos ativos, garantindo que nenhum drone receba ordens conflitantes.
2.  **Priorização:** Requisições de sensores com prioridade 5 (ex: Bloqueio de Rota) têm precedência sobre prioridades menores, mesmo que tenham ocorrido depois no tempo lógico.
3.  **Tolerância a Falhas:**
    *   Se um **Broker** cai, os vizinhos o removem da lista de pendências, impedindo o travamento do sistema (*deadlock*).
    *   Se um **Drone** cai durante a missão, o Broker detecta a perda de conexão e re-enfileira a missão para um drone reserva.
    *   **Reativação:** Drones e Brokers podem ser reiniciados e são reintegrados à malha automaticamente via anúncios UDP.

---

## 🛠️ Configurações do Docker
*   **Imagem Base:** `jamileleticia/sistema-estreitoormuz:latest` (Java 17).
*   **Rede:** Utiliza `network_mode: host` para viabilizar a comunicação via Broadcast UDP entre os contêineres e a malha física do laboratório.
*   **Interatividade:** O `inicializador.sh` utiliza as flags `-it` para permitir a visualização de logs e interação via terminal.
