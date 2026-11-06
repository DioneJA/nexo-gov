# Incremento 07 — operação, POC e integração governamental

**Data:** 06/11/2026  
**Escopo:** conferência visual do Termo de Referência, PDF pp.33–40 (§§36–43), cláusula LGPD iniciada na p.36 e atualizações de riscos e rastreabilidade.

## Mudanças

- Conferidos os requisitos de relatório de auditoria, georreferenciamento, classificação documental e implantação (§§36–38), além das cláusulas de confidencialidade, propriedade e portabilidade dos dados e LGPD (§39).
- A cláusula LGPD foi unida à continuação da p.37; o trecho continua bloqueado para avaliação legal e operacional, embora sua transcrição esteja conferida.
- Separados os seis serviços de suporte a–f, cobertura de navegador, relatório mensal de chamados e horário de atendimento (§40).
- Registradas as condições da POC pública, equipamentos, possibilidade remota, requisitos integrais e desclassificação em caso de reprovação (§41). O prazo de 90 minutos prorrogáveis continua regido pela Errata I.
- Classificada a integração Tramita GOV.BR como dependência externa com adesão, homologação, envio/recebimento de ofícios e processos e recibos NRE/IDT (§42).
- Separados requisitos de análise e exportação de painéis, métricas de usuários/setores, eficiência, engajamento, qualidade e inatividade (§43). O texto facultativo “poderá disponibilizar painéis” está como `PREFERENCIAL`, não obrigatório.
- A matriz distingue `INTEGRACAO_EXTERNA`, `OPERACAO_CONTRATUAL`, `EXTERNO/CONTRATUAL`, `POC` e `SOFTWARE`; fonte conferida continua separada de validação de produto.
- Atualizados os riscos contratuais de equipe presencial, migração real, portabilidade, propriedade do código, LGPD e dependências de homologação externa.

## Estado e verificação

- `python3 scripts/extend-matrix-from-tr-ocr.py` — sucesso: 1.341 linhas; 695 requisitos candidatos do TR, com 414 transcrições conferidas visualmente e 281 pendentes. Todos os 695 permanecem `BLOQUEADO` para aceite, testes e evidência.
- As 414 transcrições visualmente conferidas alcançam as páginas 1–40; páginas 41–69 seguem pendentes de cotejo.
- `python3 scripts/check-matrix.py` — sucesso: 695 `BLOQUEADO`, 10 `EM_DESENVOLVIMENTO`, 636 `NAO_INICIADO`, zero `VALIDADO`.
- `python3 scripts/render-traceability.py` — sucesso; matriz-resumo e roteiro individual da POC regenerados.
- `git diff --check` — sem problemas de whitespace.
- Este incremento documental não executa integração real Tramita GOV.BR, adesão, suporte contratual, migração de bases ou teste de POC oficial.

## Arquivos

- `scripts/extend-matrix-from-tr-ocr.py`
- `scripts/render-traceability.py`
- `docs/licitacoes/mata-de-sao-joao-91-2025/REQUISITOS.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.csv`
- `docs/licitacoes/mata-de-sao-joao-91-2025/MATRIZ_ADERENCIA.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/POC.md`
- `docs/licitacoes/mata-de-sao-joao-91-2025/RISCOS.md`
