# Incremento 35 — configuração de campos por assunto e exclusão no navegador

**Execução:** 07/11/2026
**Escopo:** tornar revisável a configuração administrativa de campos de identificação por assunto, testar o CRUD real e corrigir a exclusão bloqueada por CORS. Não altera dados, repositórios ou ambientes do NexoLar.

## Fonte e critério

- `TR91-0524`: Termo de Referência, §4, PDF p. 50, conferida visualmente na página escaneada. O requisito combina configurar campos de identificação por assunto com acompanhamento e interação das pessoas pela Central.
- `ED91-0488`: Edital, §14.1.13, folha impressa/PDF p. 33, conferida visualmente. É uma cláusula combinada que também trata das interações setoriais e do requerente com texto/anexos.
- A Errata I, §1, p. 1, altera a duração da sessão da POC para 90 minutos, prorrogáveis uma vez; não altera esses requisitos funcionais. O registro oficial do certame informa adjudicação/homologação e contrato com a 1DOC; este trabalho não pode ser apresentado como candidatura vencedora ao certame encerrado, nem como POC oficial.
- Auditoria independente das fontes confirmou a transcrição e os limites descritos na matriz/POC. O edital não define tipos de campo, método de autenticação nem quantidade de pessoas; esses pontos permanecem decisões de produto/dados de ensaio.

## Implementação

- V16 persiste definições por organização e assunto com chave estrangeira composta; API autenticada permite listar, criar, editar rótulo/tipo/obrigatoriedade/ordem e remover. A chave é imutável; mudanças exigem ADMIN ativo da organização, usam o `TenantContext` validado e geram auditoria.
- A integração usa desempate determinístico pela chave do campo. O formulário sugere a próxima ordem disponível mesmo quando existem lacunas ou ordens editadas.
- A tela carrega os campos ao expandir o assunto, limpa dados ao trocar organização/sair, mantém a lista durante mutações e diferencia carregamento de gravação. A remoção usa diálogo acessível do próprio produto e continua informando que cadastro/identidade/acesso de participantes não está disponível.
- O E2E revelou um defeito real: o backend aceitava `DELETE`, mas a política CORS não o permitia; o navegador recebia `net::ERR_FAILED` e a API nunca era alcançada. `SecurityConfiguration` agora permite `DELETE`; o teste de integração valida o preflight `OPTIONS` para a origem configurada.
- O teste de API agora inclui GET de assunto de outra organização e confirma 404. As definições e campos de participantes seguem distintos do requerente original e de seu token.

## Rastreabilidade, harness e skills

- `TR91-0524` e `ED91-0488` permanecem `EM_DESENVOLVIMENTO`. O cenário parcial da POC distingue configuração implementada das etapas faltantes: cadastro/vínculo, identidade/credenciais, autorização individual, acompanhamento/interação isolados e POC oficial.
- Scripts fonte e artefatos gerados registram `./dev participant-fields-test` e `./dev participant-fields-e2e`; o primeiro cobre persistência, validação, autorização, cross-tenant e CORS, e o segundo cobre criar, editar, recarregar e remover pela API real.
- `./dev`, `README.md` e `AGENTS.md` documentam o comando E2E focado. A skill de fatia vertical registra a necessidade de provar preflight CORS para novos métodos, manter estados de leitura/gravação separados e usar o mesmo diálogo de confirmação no E2E; a skill visual registra o padrão acessível.
- `./dev matrix-refresh` regenera a matriz, o relatório e a POC a partir das fontes. As cláusulas e evidências permanecem parciais; nenhuma linha foi elevada a `VALIDADO` por esta implementação.
- Revisão da interface: hierarquia e rótulos claros, lista preservada durante mutações, controles desativados enquanto grava, foco visível no diálogo e confirmação por Enter. A revisão foi feita no código e no E2E; não houve avaliação manual de contraste em tema escuro nem de leitor de tela nesta fatia. Não foi identificado desvio dos tokens/identidade NEXO.

## Verificações

- `npm run build`: passou (`vue-tsc -b` e Vite).
- `./dev participant-fields-test`: passou **1 teste**, sem falhas/erros; inclui PostgreSQL Testcontainers, autorização/isolation e preflight CORS de DELETE.
- `./dev participant-fields-e2e`: passou **1/1 cenário** em 21,0 s contra API real, PostgreSQL e S3Mock descartáveis; provou criação, alteração, recarga, foco no diálogo e remoção confirmada com Enter.
- `./dev matrix-refresh`: passou; 1.388 requisitos: 39 `EM_DESENVOLVIMENTO`, 8 `VALIDADO` em critérios delimitados e 1.341 `NAO_INICIADO`.
- `./dev verify-fast`: passou; NEXO Core **11/11**, backend **42/42**, build de produção da interface e verificação da matriz.
- `./dev matrix`: passou, sem divergências; a matriz contém 1.388 linhas.
- `git diff --check`: passou no checkout compartilhado.

## Limites

- A definição de campos não registra pessoas nem cria vínculo com protocolo. Não há credenciais individuais, entrega de convite, autorização individual, acompanhamento ou interação para participantes adicionais; `TR91-0524` e a cláusula combinada `ED91-0488` continuam incompletos.
- Testcontainers, S3Mock e Playwright local não comprovam identidade municipal, infraestrutura/armazenamento de produção, operação, treinamento, SLA ou resultado da comissão. A POC oficial não foi realizada pelo NexoGov.
- O teste de navegação cobre somente os fluxos declarados acima. Nenhum deploy, publicação, commit ou alteração no NexoLar foi realizado.

## Arquivos principais

- Backend/configuração: `backend/src/main/java/br/com/nexo/gov/config/SecurityConfiguration.java`, `ProtocolSubjectParticipantFieldService.java`, `ProtocolSubjectParticipantFieldController.java`, `backend/src/main/resources/db/migration/V16__protocol_subject_participant_identity_fields.sql`.
- Interface/prova: `frontend/src/staff/StaffApp.vue`, `frontend/e2e/protocol-participant-fields.spec.ts`.
- Harness/registro: `dev`, `README.md`, `AGENTS.md`, `.agents/skills/nexogov-vertical-slice/SKILL.md`, `.agents/skills/nexogov-design-system/SKILL.md`, scripts de matriz e documentos derivados em `docs/licitacoes/mata-de-sao-joao-91-2025/`.
