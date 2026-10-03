# AeternumCustomPortal 0.4.0 — portal horizontal — 03/10/2026

Pedido: portal inspirado no Twilight Forest, com musgo, flores, água e ativação por diamante. Usuário selecionou novo mundo já carregado, mas não forneceu seu nome; não criar dimensão nem supor frost/heat. Exemplo twilight vem desativado com destination-world: SET_LOADED_WORLD_NAME. Atualização preserva portal-types.yaml: usuário deve adicionar a seção do README ao arquivo ativo, preencher o destino, ajustar origens e habilitar/recarregar.

## Implementação

- shape opcional VERTICAL preserva configurações anteriores. HORIZONTAL_POOL usa Axis.Y persistido no schema 3 existente: 4x4 total, rim de 12 blocos incluindo cantos, centro 2x2 de quatro fontes de água, fundo sólido seguro e uma flor baixa por rim. Sem END_PORTAL/teleporte vanilla e sem substituir água por outro bloco.
- Flores baixas variadas permitidas; wither rose, plantas de dois blocos, grama/moss carpet não contam. Retorno alterna seis variedades.
- DROP_ITEM acompanha apenas item lançado por jogador, por até cinco segundos; ativação acontece quando chega na água. Somente uma unidade é consumida após registro/persistência bem-sucedidos. Negação, erro, tipo desativado ou portal já ativo não consomem. Item coletado/mesclado antes do impacto pode exigir outro lançamento. INTERACT opcional usa mão principal e consome uma unidade na piscina.
- Raio apenas visual na ativação; partículas sobre piscinas registradas e válidas. Partículas não carregam chunks.
- Entrada na água via PlayerMoveEvent segue a mesma rota, autorização, coordenadas/escalas e cooldown dos verticais. Gera/vincula retorno automático de mesma forma em mundo já carregado. Chegada na borda segura, fora da água. Retorno gerado não exige diamante.
- Plano horizontal: 32 edições (rim, flores, água e fundo) + 20 espaços de cabeça/passagem. Checks de WorldGuard/GP para colocação/quebra e materiais reais, snapshots/rollback. Preserva o fluxo vertical.
- Quebra de rim, flor/fundo, alteração das fontes, baldes/fluxo/pistões/explosões, reload e carregamento de mundo reconciliam registros/vínculos. Desativar/remover preserva água e interrompe viagem/partículas. Reparar exige reativar. Pares schema 3 existentes mantidos.

## Validação/publicação

CI Java 25 / Paper API 26.2: BUILD SUCCESS, 38 testes, zero falhas/erros/skips. Testes novos cobrem cobertura da piscina/rim/cantos, plano sem posições sobrepostas, altura de fundo/flores/passagem, invalidação ao remover cada célula obrigatória, variedades permitidas, defaults VERTICAL, HORIZONTAL_POOL/DROP_ITEM no parser compartilhado em disco, enable/reload e rejeição de schema/combinações inválidas. YAML padrão validado junto aos tipos anteriores.

Não foi executado servidor Paper real. Testar em servidor: lançamento de diamante, consumo de uma unidade/pilha, falta de permissão, proteção negada, preservação da água, entrada/retorno automático pelas coordenadas, quebra e reativação, disable/reload, reinício e chunks descarregados. Não instala mod Twilight Forest nem gera mundo.

Commit de build: a187861405b4d742d5c8aa1bf5116ded3a6b47f0.
Run: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37138799849.
Download: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37138799849/artifacts/11279511618.
Artefato: AeternumCustomPortal-0.4.0-Paper26.2, contém aeternum-custom-portal-0.4.0.jar, expira 01/01/2027.
SHA-256 do ZIP: 63350e573e21f02f29b1f7801b4cd7d6e978d6bbfa6a64a2ec2a1e705599ecc8.
Publicado na branch codex/ethercraft-custom-v0.2.0; main preservada.
