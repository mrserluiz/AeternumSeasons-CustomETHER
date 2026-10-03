# AeternumCustomPortal 0.3.1 — retorno automático — 03/10/2026

Corrige a ausência de portal de retorno na v0.3.0.

- Primeira travessia de um portal sem vínculo reaproveita um portal registrado compatível e livre no mundo de destino ou cria um novo perto do spawn. Não altera/cria/carrega mundos.
- Vínculo persistido nos dois sentidos: o retorno leva ao portal exato de origem, inclusive quando há múltiplas fontes configuradas.
- Montagem: frame completo 4×5 com material do tipo, interior ativo 2×3 no mesmo eixo e quatro pisos de saída, dois em cada lado.
- Busca num raio horizontal de 8 blocos do spawn, acima do terreno. Todos os 24 blocos alterados devem ser ar, dentro da borda e sem sobreposição de registros; os oito espaços de cabeça/pés laterais também devem estar livres.
- WorldGuard e GriefPrevention consultados para todos os blocos alterados, incluindo frame e pisos. GP consulta tanto o material do frame quanto NETHER_PORTAL. Falhas e negações impedem a montagem. Proteções desconhecidas configuradas mantêm bloqueio anterior.
- Se não houver espaço, destino carregado, proteção permitida ou saída segura na origem, não transporta o jogador e informa o motivo. Construção com erro reverte snapshots de blocos e registros/vínculos. Persistência ocorre antes das alterações de blocos; uma interrupção abrupta nesse intervalo pode deixar registro inválido, bloqueado pelos checks de integridade.
- `auto-return-portal: true` é o padrão, inclusive quando a chave falta num config antigo. Com false, mantém o spawn fallback da 0.3.0. Pares manuais válidos não são alterados. Vínculos quebrados precisam de unlink/remove; não são substituídos silenciosamente.
- Criar/vincular antes de teleportar permite ao evento de teleporte externo cancelar a viagem; o par criado permanece disponível.

Validação: geometria do retorno testa cobertura única dos 24 blocos para proteção/rollback, pisos dos dois lados e separação dos oito espaços de saída. Testes anteriores mantidos. Não foi executado em servidor Paper real; testar primeira travessia, volta ao ponto exato, reinício/reuso sem duplicação e bloqueio em regiões/claims.

CI: BUILD SUCCESS, 16 testes aprovados, zero falhas/erros/skips.
Commit: 19731cf84494f44588971df69e8932d7b2a23167.
Run: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37129222667.
Download: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37129222667/artifacts/11276286969.
Artefato contém aeternum-custom-portal-0.3.1.jar; expira em 01/01/2027.
