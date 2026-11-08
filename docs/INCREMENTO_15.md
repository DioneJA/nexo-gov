# Incremento 15 — cadastro inicial de setores com isolamento e auditoria

## Entrega

- Adicionada a capacidade de administrador ativo criar setor somente na organização aberta pelo filtro de tenant do servidor.
- O backend valida permissão independentemente da interface. A consulta de capacidade é apenas informativa; `POST /api/v1/staff/departments` revalida autorização.
- Nome é obrigatório, limitado a 180 caracteres e único sem distinção de maiúsculas/minúsculas dentro da organização. Organizações diferentes podem usar o mesmo nome.
- A criação do setor e o evento de auditoria são atômicos. A autorização consulta um vínculo explícito de papel no nível da organização, não o papel `ADMIN` em qualquer setor. A tabela de auditoria rejeita atualização, exclusão e truncamento por triggers no PostgreSQL.
- A interface de servidor mostra o formulário apenas quando a API informa permissão, e o E2E executa cadastro e fluxo de protocolo.
- O contrato OpenAPI documenta capacidade, criação, respostas e conflito de nome.
- A migração detecta nomes duplicados sem distinção de caixa antes de criar o índice. Em banco com conflito, para com a lista de organização/nomes/IDs e orienta reconciliação manual, sem editar dados existentes.

## Limites

Este incremento não entrega organograma hierárquico nem dinâmico, subsetores, contatos, cargos, cadastro de servidores, múltiplas lotações, papéis granulares ou gestão organizacional completa. Portanto, os requisitos relacionados permanecem `EM_DESENVOLVIMENTO`.

## Validação executada

- Backend: `mvn test` — 5 testes aprovados, zero falhas/erros; integração em PostgreSQL 16.4/Testcontainers. Um teste dedicado confirma que migração com nomes antigos duplicados para sem alterar os dois registros e retorna orientação de reconciliação.
- Frontend: `npm run build` — sucesso.
- Navegador: `npm run e2e` — 1 teste Playwright/Chrome aprovado, incluindo cadastro de setor e fluxo de protocolo.
- Matriz: 1.388 linhas; 646 fontes de edital/errata conferidas no documento e 742 requisitos candidatos do TR conferidos visualmente. Após este incremento: 739 `BLOQUEADO`, 17 `EM_DESENVOLVIMENTO`, 632 `NAO_INICIADO` e 0 `VALIDADO`.
- Geração e verificação da matriz: `build-edital-matrix.py`, `extend-matrix-from-tr-ocr.py`, `render-traceability.py` e `check-matrix.py` executados com sucesso.

## Evidência

O teste automatizado e seus limites estão descritos em `licitacoes/mata-de-sao-joao-91-2025/evidencias/POC-PROTOCOLO-E2E.md`. As linhas `ED91-0009`, `ED91-0087`, `ED91-0103`, `ED91-0104`, `TR91-0015`, `TR91-0111` e `TR91-0112` apontam para a evidência parcial. Nenhuma linha foi marcada `VALIDADO`.
