# Incremento 50 — revalidação de fluxos e transparência da matriz

## Escopo

Reexecutei as verificações de ponta a ponta já existentes, gerei novo pacote de capturas dos cenários aprovados e conferi as imagens exportadas. A mudança no harness torna visível, em `./dev matrix`, quantas linhas ainda usam o critério provisório `Pendente de decomposição:`. A skill de rastreabilidade agora registra duas regras aprendidas na auditoria: revisão visual deve ser comprovada por candidato/ID, e uma quebra de parágrafo no OCR deve ser reconciliada com o PDF antes de tratar os itens como requisitos separados.

Esta revalidação não implementa novos módulos de produto e não promoveu nenhum requisito na matriz. O Pregão 91/2025 já foi adjudicado e homologado, com contrato publicado; este produto não participou da POC oficial. Os resultados abaixo são evidência interna dos fluxos atuais.

## Verificações

- `./dev verify`: aprovado. NEXO Core **11/11** testes; API **65/65** testes com PostgreSQL Testcontainers; build Vue/Quasar **89 módulos**; matriz íntegra com **1.388** linhas; Playwright Basic **14/14** cenários; Playwright OIDC **7/7** cenários.
- `./dev evidence-export-test`: aprovado, **14/14** verificações do exportador.
- `./dev evidence`: aprovado em **8/8** cenários; exportou **22 PNGs** para `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T185611Z-1871850/`.
- `./dev doctor`: dependências locais, Docker/Compose, Chrome, checkout do Core e existência da configuração local verificados. Nenhum valor de `.env` foi exibido.
- `./dev frontend-auth-bundle-test`: compilou 89 módulos e conferiu os **2 módulos públicos** contra helpers locais de Basic/Axios e campos internos do servidor.
- Requisições GET a `http://localhost:9000/`, `http://localhost:9000/servidor/` e `http://localhost:8081/actuator/health` responderam HTTP 200 durante a validação manual. Isso confirma disponibilidade local naquele momento, não a operação de produção.
- Os cenários E2E usaram projetos Compose descartáveis e foram encerrados pelo harness. Os volumes locais persistentes não foram tocados.

## Evidências e limites

O manifesto do pacote E2E relaciona visualmente **23 de 1.388 requisitos**, dos quais **9** aparecem como `VALIDADO` na matriz. **1.345** linhas não têm cenário E2E matriciado e **5** não recebem mídia por privacidade. Essas imagens mostram subfluxos de administração de acesso, processo/workflow, protocolo, anexos, leitura e catálogo de assunto; não substituem os testes de backend e não provam sozinhas os critérios completos.

Revisei visualmente a folha das 22 imagens e, em tamanho integral, a captura pública que mostra o campo de acompanhamento. A chave fica coberta por máscara escura; não aparecem QR nem credenciais. As imagens e os manifestos estão fora do Git, com diretório privado e arquivos `0600`. Vídeo contínuo e PDFs com token/QR não foram exportados. O ambiente de teste usa dados fictícios e S3Mock; isso não prova segurança do storage produtivo, operação, backup ou hospedagem.

Estado atual da matriz após `./dev matrix`: **10 `VALIDADO`**, **45 `EM_DESENVOLVIMENTO`** e **1.333 `NAO_INICIADO`**. O novo contador do harness discrimina **713 critérios TR91** ainda marcados `Pendente de decomposição:`; as cinco outras linhas TR91 não iniciadas já têm critérios próprios. Portanto, o gate estrutural da matriz não significa que cada critério esteja operacionalmente detalhado, e não há aderência integral demonstrada.

A auditoria da fonte oficial encontrou uma possível quebra indevida no parágrafo de documentos complementares da página 52 do PDF do Termo de Referência: `TR91-0556` e `TR91-0557` parecem derivar do mesmo parágrafo oficial. Os IDs devem ser preservados e reconciliados no gerador/registro de revisão antes de corrigir a matriz. A skill proíbe inferir revisão por cutoff fixo e requer ledger por ID, hash do documento, página e decisão; o ledger e a reconciliação desse parágrafo permanecem trabalho pendente.

O projeto local persistente continua com divergência de checksum Flyway em V2; nenhum `repair`, reset ou migração foi executado nele. O portal atualmente aberto em `localhost:9000` usa um projeto isolado descartável com dados fictícios. Se esse processo encerrar, não use o banco persistente como atalho: o bloqueio está registrado em `docs/INCREMENTO_49.md` e requer reconciliação deliberada antes de retomar aquele volume.

## Próxima prioridade

Uma próxima fatia funcional delimitada é o diretório de contatos organizacionais com CRUD e importação/exportação CSV, cobrindo em etapas os itens `TR91-0135` a `TR91-0137`. A auditoria identificou que o organograma e o diretório de servidores existentes não fornecem esse módulo de contatos. Tramitação multi-setor (`ED91-0090`/`TR91-0098`) e prazos múltiplos por documento (`ED91-0108` a `ED91-0115` e `TR91-0116` a `TR91-0123`) continuam pendentes, com escopo mais amplo.

## Artefatos operacionais

- `scripts/check-matrix.py` agora informa o total de critérios ainda pendentes de decomposição por fonte.
- `dev` descreve a saída adicional do comando `./dev matrix`; nenhuma nova infraestrutura de teste foi necessária porque `./dev verify`, `./dev evidence`, `./dev doctor` e `./dev frontend-auth-bundle-test` já reproduzem as verificações deste incremento.
- `.agents/skills/nexogov-tender-traceability/SKILL.md` registra a revisão de fonte por candidato e preservação de IDs em casos de parágrafo dividido pelo OCR.
- A matriz CSV e os seus relatórios derivados não mudaram porque não houve implementação nem alteração confirmada de status neste incremento.
