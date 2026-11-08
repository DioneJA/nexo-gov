# Testar o NexoGov localmente

## Abrir uma cópia manual isolada

Na raiz do repositório:

```bash
./dev sandbox
```

O comando cria um projeto Compose temporário com PostgreSQL e S3Mock próprios, aplica as migrações em um banco vazio, sem ler `.env` nem usar o volume local persistente, e inicia a interface e a API atuais. Use a URL e as contas fictícias exibidas no final do comando. Para consultar a execução, use `./dev sandbox-status`.

O seed local apresenta `Município Alfa (demonstração)` e `Município Beta (demonstração)`. A conta `servidor` é administradora de organização no Alfa. `servidor.teste` é um servidor sem papel de administrador organizacional. As duas contas e senhas existem somente para este perfil de demonstração local.

## Validar contatos da organização

1. Acesse a URL da área do servidor impressa pelo sandbox e entre como `servidor`.
2. Na página da organização, localize **Contatos da organização**.
3. Adicione um registro de cada tipo: pessoa física, setor da prefeitura e Prefeitura/pessoa jurídica. Salve cada um e confirme que a categoria aparece junto do nome.
4. Pesquise por nome e e-mail. Edite um dos registros e confirme a atualização.
5. Arquive outro contato, marque **Incluir arquivados** e reative o registro.
6. Importe o CSV abaixo. Confirme a mensagem de conclusão e que os registros aparecem na lista:

```csv
type,name,email,phone
PERSON,Ana Pública,ana@example.test,+5571999999999
MUNICIPAL_DEPARTMENT,Financeiro,financeiro@example.test,
LEGAL_ENTITY,Prefeitura Parceira,parceira@example.test,
```

7. Use **Exportar CSV** e abra o arquivo baixado. O arquivo inclui IDs para que uma exportação importada de volta atualize os mesmos registros.
8. Saia da sessão e entre como `servidor.teste`. Confirme que o painel de contatos não está disponível. A API também nega a administração sem o papel de organização; ocultar o painel não é o controle de segurança.

O teste automatizado também dispara duas solicitações simultâneas de arquivamento e confirma que só uma registra a transição e o evento de auditoria.

## Provas automatizadas

```bash
./dev contacts-test
./dev contacts-e2e
./dev verify-fast
./dev verify
```

`contacts-e2e` percorre a mesma manutenção pela interface real, API e PostgreSQL descartáveis. `verify` acrescenta os cenários de navegador Basic e a suite OIDC com provedor simulado.

## Encerrar

```bash
./dev sandbox-stop
```

Esse comando confere a identidade e o tempo de início dos processos antes de sinalizar seus grupos; se um PID tiver sido reutilizado ou não corresponder ao sandbox, a parada é recusada. Ao concluir, remove somente o projeto Compose temporário criado pelo `./dev sandbox` e o banco/arquivos fictícios dele. O ambiente local persistente e qualquer dado real não são usados. O sandbox é uma ajuda de desenvolvimento, não um ambiente de produção, não usa storage produtivo e não prova todos os requisitos do Pregão nem uma POC oficial.
