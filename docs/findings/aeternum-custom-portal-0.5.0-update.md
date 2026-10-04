# AeternumCustomPortal 0.5.0

- Debug de ativação, autorização e retorno desligado por padrão; no modo de testes, detalhes no chat somente para operadores reais (isOp), sem acesso por mera permissão admin.
- `debug.enabled`, `debug.log-to-console` e `messages.player-feedback` (ACTION_BAR/OFF/CHAT) configuráveis; avisos repetidos limitados por jogador/categoria.
- `/acp language <locale|auto>` e `/acp languages` acessíveis aos jogadores com `aeternumcustomportal.language` (default true); comandos administrativos continuam protegidos.
- Preferências por UUID persistidas atomicamente em player-languages.yml, com escolha explícita acima do idioma do cliente/padrão.
- 11 catálogos editáveis em languages/*.yml; reload validado, fallback de chaves ausentes e placeholders preservados.
- Novas configurações adicionadas a instalações existentes sem sobrescrever seus valores. Tipos de portal/destinos existentes preservados.

## Verificação

GitHub commit: ef7e10eb859794339c065eef311a81856ebfa8a5

CI: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37211210568

Java 25 / Paper API 26.2.build.129-stable, Maven clean verify: BUILD SUCCESS, 49 testes, zero falhas/erros/skips. Novos testes: 11 idiomas completos e placeholders equivalentes; prioridade/fallback de idioma; overrides; rejeição de tradução inválida; persistência e independência entre jogadores; recuperação após falha de gravação; restrição de diagnósticos.

Artifact: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37211210568/artifacts/11306981114

JAR: aeternum-custom-portal-0.5.0.jar

JAR SHA256: 6628fa1ead853855e5a57bc745b0380505de94901bccc6dd5d1a8b8629707f73

ZIP SHA256: f4095513af2c62e64760942206eafdc117764f2f8e75c4726fe7fb0bd89e157f

O JAR foi inspecionado: versão correta, 11 idiomas empacotados e comando sem bloqueio global de administração. Não foi iniciado servidor Paper real nesta validação. Confirmar no servidor: ativação/viagem sem debug no chat, jogador sem OP trocando idioma, persistência após reinício, mensagens de testes restritas a OP e reload de traduções.

Nenhuma alteração no Modrinth durante esta atualização; publicação manual pelo autor.
