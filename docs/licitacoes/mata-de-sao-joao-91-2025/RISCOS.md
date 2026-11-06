# Riscos e bloqueios

| Risco | Impacto | Estado/mitigação |
|---|---|---|
| O certame aparece adjudicado e contratado na página oficial, com vigência 2025–2027. | Oportunidade de contratar este lote específico pode não existir; não confundir produto-alvo com proposta válida. | Confirmado na página oficial em 06/11/2026; verificar novos certames e situação contratual antes de qualquer ação comercial. |
| O Termo de Referência (69 páginas) é digitalização sem texto pesquisável. | OCR pode perder negações, siglas e limites técnicos; requisito incompleto afetaria POC e arquitetura. | OCR de apoio preservado; 398 parágrafos candidatos marcados `BLOQUEADO` até revisão visual; lote completo ainda pode ser maior. |
| Quase todas as especificações estão sem implementação e sem evidência no novo produto. | Não há base para afirmar aderência ao edital ou aprovação na POC. | Matriz explícita; só um fluxo de protocolo inicial implementado. |
| Identidade local usa HTTP Basic e usuário em memória. | Inadequado para produção, falta ciclo de sessão/MFA/SSO, gestão de usuários real e políticas granulares. | Ativado apenas pelo perfil `local`; produção não está pronta nem configurada. |
| Abertura pública ainda não implementa captcha, throttling, e-mail, upload/análise de malware ou gestão de consentimento/retenção. | Abuso do portal e risco a dados pessoais/arquivos. | Não usar dados reais; necessário definir proteção e política com órgão contratante. |
| O POC tem duração de 90 minutos, prorrogável, e cobertura ampla. | Um roteiro sem ensaio integral e evidências por requisito pode ser desclassificado. | O recorte atual cobre só protocolo; os outros cenários estão pendentes/bloqueados. |
| Dependências do Core resolvem localmente por Maven. | Clone isolado do NexoGov não compila sem obter a versão Core. | Bootstrap instala Core independente; publicar pacote versionado antes de distribuir a terceiros. |
| Atos com ICP-Brasil, Tramita GOV.BR, e-TCM, Sisobra e login Gov.br dependem de certificados, homologações, contratos e credenciais externas. | Integrações reais não podem ser provadas localmente. | Nenhuma credencial corporativa ou segredo foi importado; adaptadores e homologações ainda não existem. |
| A árvore NexoLar estava com 551 entradas locais modificadas/não rastreadas e divergente do remoto. | Qualquer alteração poderia tocar trabalho privado/local ou estado de origem. | NexoLar somente leitura; nada foi copiado/modificado/commitado nele. |
