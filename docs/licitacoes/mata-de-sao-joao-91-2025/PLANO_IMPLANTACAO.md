# Plano de implantação e operação

## Prova técnica local (ambiente descartável)

1. Executar `./dev doctor` para conferir Java, Maven, Node/npm, Docker e o checkout independente do NEXO Core; preparar `.env` apenas para uso local.
2. Executar `./dev setup` para instalar dependências, executar as verificações iniciais e produzir os builds locais.
3. Executar `./dev start` para iniciar PostgreSQL local e API; em outro terminal, executar `./dev web` para abrir a interface.
4. Demonstrar criação, encaminhamento, recebimento, despacho, consulta por QR/token e conclusão com dados fictícios; o E2E descartável pode ser executado com `./dev e2e`.
5. Conferir eventos, numeração por organização, isolamento entre organizações, PDF e resultados de Testcontainers.

Esta sequência é apenas desenvolvimento técnico local. Não inicia implantação contratual nem constitui etapa do Pregão 91/2025, que já foi adjudicado e contratado. Não inclui provisionamento de ambiente, domínio público, TLS, backup externo, monitoramento, migração de dados reais, capacitação, suporte ou aceite de órgão contratante.

## Implantação contratual prevista no edital

O edital §13.1 prevê implantação e treinamento em até 30 dias corridos após a Ordem de Serviço e suporte continuado até o fim da vigência. O Termo de Referência §38 detalha migração/implantação e §40 define suporte. Antes da execução real será necessário planejar inventário, responsáveis por dados, mapeamento, amostra, reconciliação, corte, retorno/rollback, treinamento por perfil, aceite formal, monitoramento e passagem ao suporte.

## Checklist de entrada em operação (ainda pendente)

- Identificar organização, responsáveis, bases legais, operadores, encarregado e prazos de retenção.
- Contratar/configurar infraestrutura isolada, TLS, domínios, criptografia, storage privado e segregação de segredos.
- Definir RPO/RTO, backups cifrados, restauração ensaiada, observabilidade, escalonamento e página de status.
- Homologar integrações necessárias e obter credenciais por canal oficial.
- Definir o provedor municipal de identidade, seus endpoints issuer/JWKS, audience, governança de cadastro/suspensão e procedimento auditável para provisionar a primeira conta administradora; sem esses dados a API de produção permanece fechada.
- Mapear dados/arquivos, sanear, validar amostra e reconciliar contagens sem sobrescrever origem.
- Treinar administradores, servidores e suporte; registrar presença e avaliação.
- Executar testes de carga, segurança independente e POC com gravação/evidência para cada requisito.
- Obter aceite do órgão e cronograma operacional aprovado.

Nenhuma carga de dados de órgão foi realizada. Nenhum deploy foi executado.
