# Plano de implantação e operação

## Prova técnica local (ambiente descartável)

1. Instalar Java 21, Maven, Node/npm e Docker Compose; preparar `.env` local.
2. Executar `scripts/bootstrap-local.sh`: instalar NEXO Core 0.1.0, rodar testes da API e produzir build web.
3. Iniciar Postgres isolado e API com `scripts/start-local.sh`; executar a interface em outro terminal.
4. Demonstrar criação, encaminhamento, recebimento, despacho, consulta por QR/token e conclusão usando dados fictícios.
5. Conferir os eventos, números por organização, consultas entre organizações, PDF e testes Testcontainers.

Esta sequência é apenas desenvolvimento técnico. Não inclui provisionamento de ambiente, domínio público, TLS, backup externo, monitoramento, migração de dados reais, capacitação, suporte ou aceite do contratante.

## Implantação contratual prevista no edital

O edital §13.1 prevê implantação e treinamento em até 30 dias corridos após a Ordem de Serviço e suporte continuado até o fim da vigência. O Termo de Referência §38 detalha migração/implantação e §40 define suporte. Antes da execução real será necessário planejar inventário, responsáveis por dados, mapeamento, amostra, reconciliação, corte, retorno/rollback, treinamento por perfil, aceite formal, monitoramento e passagem ao suporte.

## Checklist de entrada em operação (ainda pendente)

- Identificar organização, responsáveis, bases legais, operadores, encarregado e prazos de retenção.
- Contratar/configurar infraestrutura isolada, TLS, domínios, criptografia, storage privado e segregação de segredos.
- Definir RPO/RTO, backups cifrados, restauração ensaiada, observabilidade, escalonamento e página de status.
- Homologar integrações necessárias e obter credenciais por canal oficial.
- Mapear dados/arquivos, sanear, validar amostra e reconciliar contagens sem sobrescrever origem.
- Treinar administradores, servidores e suporte; registrar presença e avaliação.
- Executar testes de carga, segurança independente e POC com gravação/evidência para cada requisito.
- Obter aceite do órgão e cronograma operacional aprovado.

Nenhuma carga de dados de órgão foi realizada. Nenhum deploy foi executado.
