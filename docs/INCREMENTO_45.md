# Incremento 45 — evidência visual do fluxo de anexos

## Alterações

O E2E `frontend/e2e/protocol-attachments.spec.ts` agora anexa três capturas PNG usando o helper de evidências já existente, todas em viewport e focadas no estado demonstrado:

- `regra-anexo-persistida`: assunto fictício e regra documental salva visível na administração;
- `anexo-cidadao-enviado-listado`: solicitação fictícia aceita e documento de abertura listado no portal;
- `anexo-setor-consultado-baixado`: mesmo arquivo consultado no setor e download concluído.

As capturas acontecem depois dos asserts de sucesso da API/UI. O helper troca o campo de chave do comprovante para `password`, aplica máscara e verifica a assinatura PNG; a tela pública não renderiza QR nestes estados. Login é concluído antes da captura e cabeçalhos de requisição não fazem parte da UI. O arquivo, assunto, protocolo e pessoa vêm dos fixtures fictícios locais. Vídeo contínuo permanece desligado.

### Correção após revisão P2: verificar os pixels visualmente

A revisão apontou que procurar o bearer como sequência de bytes no PNG não prova que os pixels estejam ocultos: PNG comprime/transforma os pixels e pode não conter o texto literal mesmo quando o campo está visível. Removi essa asserção do helper. A proteção explícita permanece: quando o campo contém chave, o helper muda seu tipo para `password` e verifica o atributo; quando o campo existe, seu locator é incluído na lista `mask` passada ao Playwright. Uma checagem estática do exportador confirma essas condições de configuração sem decodificar PNG nem afirmar redaction visual. A inspeção visual de cada screenshot exportado que contenha a chave é o único passo desta política que valida o resultado nos pixels. No pacote `20261108T171019Z-1373500`, revisei as 19 imagens exportadas; a tela cidadã da chave está mascarada como um bloco uniforme, e as demais não mostram credenciais, tokens ou dados reais.

A spec já executava os três passos com API e storage S3Mock reais no ambiente descartável; também corrigi o botão `Atualizar` da spec para usar nome acessível exato, pois o novo botão `Atualizar lista` de outro painel passou a causar ambiguidade. A primeira execução focal falhou somente por essa ambiguidade; o retry passou.

## Allowlist e vínculo com a matriz

O par exato spec/cenário foi incluído em `frontend/playwright.evidence.config.ts` e em `scripts/export-e2e-evidence.py`. O vínculo visual é por ID e pelo conjunto mínimo de labels realmente necessário:

- `TR91-0516`: configuração da regra + upload/listagem pelo cidadão;
- `TR91-0019`: consulta/download pelo setor;
- `ED91-0170` e `TR91-0174`: fluxo visível do cidadão + consulta/download no setor.

`ED91-0484` não recebe captura deste cenário: o requisito combina anexos e prazo automático; screenshots somente de anexos não provam o prazo. `TR91-0517` permanece associado à captura/teste do prazo em cenário próprio. A validação estática também revelou que a policy anterior associava visualmente `TR91-0115` ao cenário de árvore/presença sem referência explícita à spec/cadeia na matriz; removi esse ID da associação visual para não inferir o vínculo. A matriz oficial, seu gerador, status e roteiros não foram editados.

`./dev evidence-export-test` agora confere paridade entre a policy e a lista de cenários do config de evidências; existência única do título na spec; presença de labels em chamadas explícitas do helper; validade/unicidade de labels; existência dos IDs e referências na matriz; e ausência de associação de `ED91-0484` mesmo quando apenas o cenário de anexos tem screenshots. Um segundo caso confirma nenhum screenshot para esse ID quando não há cenário de anexos nem de prazo.

Após a revisão P2, `./dev evidence-export-test` também verifica estaticamente que o helper configura `type=password`, exige o atributo quando há valor e passa o locator à opção `mask`. Essa verificação não é prova dos pixels da captura; os PNGs que contenham a chave ainda exigem inspeção visual após exportação.

## Verificações

- `./dev e2e --grep 'assunto exige documento'` — passou, 1/1, 25,3 s, após fixar o locator ambíguo.
- `./dev evidence-export-test` — passou anteriormente com 13 testes; após a revisão P2 passou com 14 testes, incluindo a checagem estática do helper.
- `npm run build` em `frontend/` — passou; Vite transformou 89 módulos e gerou os três entrypoints.
- `git diff --check` — passou; também verifiquei espaços finais nos documentos novos.
- `./dev matrix-refresh` não foi executado: nenhum gerador ou artefato oficial da matriz mudou. `./dev openapi-check` não se aplica: nenhum contrato foi alterado.
- `./dev evidence` foi executado em 2026-11-08 e terminou aprovado com 8 cenários Playwright, 19 screenshots e manifesto completo de 1.388 requisitos, salvo em `/home/exati/Documents/NexoGov-Evidencias/PE-91-2025/20261108T171019Z-1373500/`. Três screenshots correspondem aos estados de anexos listados nesta spec; os PNGs e o manifesto mantêm permissões privadas.
- Inspecionei visualmente todas as 19 imagens do pacote: a chave do comprovante está mascarada; não identifiquei tokens, credenciais ou dados reais expostos. O runner de evidência não grava vídeo contínuo.
- Após a correção P2, `npm run build` foi executado quando não havia teste ativo no backend e passou (`vue-tsc -b && vite build`, 89 módulos transformados). A checagem estática complementa, mas não substitui a inspeção visual dos pixels exportados.

## Limites e evidência contratual

O cenário cobre somente um PDF fictício obrigatório na abertura e duas rotas visíveis de consulta/download. Não demonstra regra opcional, todos os formatos, cobertura de todo tipo documental, hospedagem operacional, backup/criptografia ou segurança do bucket produtivo. S3Mock é um dublê local da API S3 e não prova a operação real de armazenamento. As imagens são evidência complementar e não alteram status de matriz nem comprovam POC oficial.
