# Incremento 28 — revisão factual da matriz e da referência histórica da POC

**Data:** 07/11/2026
**Escopo:** conferir fontes oficiais, corrigir regras dos geradores e artefatos derivados. Nenhum requisito novo foi criado.

## Fontes verificadas

- Edital PE 91/2025, PDF oficial: folha impressa 9 (§9.7.3.1–.3), folha impressa 14 (pentest, §14.1.13) e folhas impressas 25–26 (EAD).
- Termo de Referência oficial, PDF digitalizado: PDF p.4 (item k, Pentest Whitebox), p.30 (replay, avaliação/certificado e progresso do EAD) e p.67 (§18, qualificação técnica). As páginas p.30 e p.67 foram renderizadas e conferidas visualmente; p.4 foi conferida na imagem oficial e no OCR usado para localizar o trecho.
- Errata I oficial, pp.1–2: a duração da POC foi alterada para 90 minutos prorrogáveis; o texto da convocação descreve prazo de 72 horas úteis após agendamento e os contatos usados naquele procedimento.
- [Página oficial do certame](https://www.matadesaojoao.ba.gov.br/site/Licitacao/143681?localConsulta=2), atualizada em 06/11/2026: informa adjudicação e homologação em 06/10/2025 e contrato publicado em 09/10/2025 para 1DOC TECNOLOGIA S.A., com vigência indicada de 09/10/2025 a 09/10/2027. O extrato oficial local informa vigência de 24 meses a contar da publicação no PNCP.

## Decisões e correções

- A natureza de `ED91-X001`–`ED91-X003` passou para `HABILITACAO_EMPRESARIAL`, como estabelecem o §9.7.3 do Edital e o §18 do TR. `TR91-0738`–`TR91-0741` já usavam essa natureza e permanecem nela. As linhas da matriz identificam verificação documental de habilitação, fora da demonstração funcional da POC.
- O Pentest Whitebox trimestral de `ED91-0031` e `TR91-0041` passou a `OPERACAO_CONTRATUAL`; ambos dependem de consultoria certificada, relatórios periódicos ou metodologia alternativa sujeita à validação municipal. `ED91-0031` agora tem roteiro que pede essa evidência operacional e informa que testes locais de código não provam a prestação do serviço. Sua referência cruzada foi corrigida de TR PDF p.3 para p.4, onde o item k aparece no documento oficial; ID, texto e status foram preservados.
- `ED91-0310`–`ED91-0312` e `TR91-0313`–`TR91-0315` estão classificados como `SOFTWARE`: são funções da plataforma EAD para rever aulas, aplicar avaliação/emitir certificado e aferir progresso. A disponibilização de material didático continua em linhas próprias (`ED91-0309` e `TR91-0312`); mantém-se explícita a diferença de natureza da matriz, sem tornar obrigatório o formato preferencial de vídeo.
- A POC de 2025 foi reescrita como referência histórica, não convocação atual. As condições da Errata permanecem documentadas, mas a POC do NexoGov não foi executada e o pacote local não contém laudo do fornecedor contratado. Nenhum resultado da sessão do adjudicatário é atribuído ao NexoGov. Para outro certame, o roteiro manda conferir edital e convocação atuais.
- `REQUISITOS.md` reconcilia um único escopo: 646 linhas ED91 + 742 TR91 = 1.388; `ED91`: 21 em desenvolvimento, 2 validados em escopo restrito, 623 não iniciados; `TR91`: 10, 2, 730; total: 31, 4, 1.353. A natureza `PREFERENCIAL` é reportada separadamente do subtotal de obrigações sem implementação exclusiva em software, que inclui POC e proposta comercial.
- `PLANO_IMPLANTACAO.md` foi alinhado a `./dev doctor`, `./dev setup`, `./dev start`, `./dev web` e `./dev e2e`, conferidos em `./dev help`. A sequência está marcada como validação local e separada da implantação contratual.

## Matriz e artefatos

Os geradores atualizados são `scripts/build-edital-matrix.py`, `scripts/extend-matrix-from-tr-ocr.py` e `scripts/render-traceability.py`. A regeneração preservou IDs, origens, transcrições e status, exceto a correção documentada da referência cruzada de `ED91-0031` para TR p.4. A matriz continua com 1.388 linhas, 31 `EM_DESENVOLVIMENTO`, 4 `VALIDADO` em escopo restrito e 1.353 `NAO_INICIADO`.

## Harness e limites

`./dev matrix-refresh`, `./dev matrix` e `git diff --check` passaram. `matrix-refresh` reportou 742/742 linhas do TR com conferência visual e 1.377 IDs numéricos estáveis. Nenhum teste de aplicação foi necessário nesta revisão documental. O harness `./dev` não mudou: os comandos existentes bastam para reproduzir as verificações de matriz; `./dev help` foi usado para atualizar o plano local. Não foram alterados código de produto, UI, `docs/INCREMENTO_27.md`, dados persistentes, NexoLar, nem foram feitos commit ou deploy.
