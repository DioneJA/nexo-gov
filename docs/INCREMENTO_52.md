# Incremento 52 — recuperação segura do sandbox incompleto

## Ajuste

A revisão do incremento anterior encontrou que a captura de identidade podia falhar antes que o PID fosse gravado. Isso deixava API/Compose sem um estado que `sandbox-stop` conseguisse ler. Agora cada PID é salvo assim que o serviço inicia; captura malsucedida aciona encerramento do Compose e preserva o estado quando um processo ativo não pode ser validado com segurança. A parada aceita estados parciais, nunca sinaliza PID sem tempo de início validado, encerra o projeto Compose identificado e mantém o estado até que qualquer processo não validado deixe de existir.

Estados antigos continuam migráveis apenas quando ambos os PIDs estão presentes e nenhum serviço tem tempo de início registrado; a migração ainda exige validação completa de processo, grupo, comando e porta.

## Prova e limites

- `./dev sandbox-test`: testa com Docker falso que um PID já encerrado permite limpeza completa, que um processo vivo sem identidade validada não recebe sinal, que o Compose do projeto registrado é encerrado e que o estado permanece até a limpeza segura poder ser repetida.
- `./dev sandbox-status`: o sandbox manual de demonstração que já estava aberto continuou com a API `UP`; os dados fictícios não foram reiniciados.
- `bash -n scripts/manual-sandbox.sh scripts/test-manual-sandbox.sh`: valida sintaxe dos dois scripts.
- `git diff --check`: valida espaços e marcadores do diff.
- Nenhuma alteração nos requisitos do edital ou na matriz é necessária: este incremento corrige apenas recuperação do harness local. Por isso, `./dev matrix` é conferido sem regenerar artefatos de rastreabilidade.
- A skill `nexogov-manual-sandbox`, `README.md`, `AGENTS.md`, `docs/TESTAR_LOCAL.md` e o help de `./dev` registram a regra operacional. Nenhum skill funcional ou de design foi alterado porque o ajuste é específico ao ciclo de vida do sandbox.
- O ambiente persistente local continua intocado. O teste usa processo temporário próprio e um executável `docker` falso.
