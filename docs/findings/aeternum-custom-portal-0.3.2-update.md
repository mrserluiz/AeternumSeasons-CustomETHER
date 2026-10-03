# AeternumCustomPortal 0.3.2 — destruição e reload — 03/10/2026

Pedido: frames não devem ser indestrutíveis; mudanças de mundos/tipos no portal-types.yaml precisam ser efetivas no reload, incluindo Heat.

## Comportamento corrigido

- Removidos cancelamentos de quebra/colocação/explosão/pistões e filtragem de blocos de explosões. Eventos agora observados em MONITOR sem alterar o evento; cancelamentos normais das proteções são respeitados.
- No próximo tick, apenas estruturas afetadas são verificadas. Se inválidas, o registro e os dois sentidos do vínculo são removidos; o interior NETHER_PORTAL desaparece. O parceiro não é apagado. Física continua preservando apenas interiores de estruturas intactas.
- Startup e WorldLoad reconciliam estruturas quebradas/obsoletas. Mudanças ocorridas enquanto o mundo está descarregado são reconciliadas quando ele carregar.
- /acp reload valida o YAML completo, reconcilia frames desativados, removidos, com material antigo ou mundo não autorizado e descarta vínculos obsoletos. Mudança de origens/destino/habilitação invalida todos os pares antigos daquele tipo, inclusive com parceiro descarregado. Falha de persistência restaura mapas de registros/vínculos e as definições anteriores. YAML inválido mantém os tipos anteriores.
- O comando também recarrega config.yml e reaplica o bridge; informa o caminho real de portal-types.yaml e lista item/modo/material, origens, destino e situação carregado/não carregado.
- Ativação de estrutura compatível sem autorização informa tipo desativado, mundo de origem não autorizado ou falta de permissão. Interação negada pelo servidor/proteção informa motivo genérico e não ignora a negação. Candidatos desativados ou de outros mundos não impedem um candidato válido posterior.
- Registro antigo na mesma posição não engole mais a ativação quando o tipo/material mudou ou o interior precisa ser reaceso.

## Heat

O padrão segue desativado conforme o YAML solicitado anteriormente. Para habilitar, editar plugins/AeternumCustomPortal/portal-types.yaml: enabled: true, source-worlds: [world] ou o nome/chave real, destination-world: aeternum_heat, frame-block: NETHER_WART_BLOCK, activation.item: FLINT_AND_STEEL, activation.mode: INTERACT. Recarregar com /acp reload e clicar no frame completo 4x5. Reload configura regras; não acende frames sozinho e não carrega dimensões. Confirmar o destino em /acp worlds. A pasta antiga EtherCraftCustom não é a configuração ativa depois da migração.

## Validação

Testes novos cobrem remoção de um endpoint sem apagar o parceiro/par não relacionado, descarte de vínculo por rota inválida, remoção de tipos desativados, idempotência com pares intactos e mudanças de origem/destino/habilitação versus item de ativação.

Nenhum teste de servidor Paper real foi executado. Validar em staging: quebra autorizada/desautorizada em região/claim, explosão/pistão, retorno sobrevivente, Heat após enabled/origens/destino alterados e reload inválido. Sem bypass das proteções externas.

Build final: BUILD SUCCESS, 21 testes aprovados, zero falhas/erros/skips.
Commit: 69875569a6f477f92133f6d94ee971984ddf5366.
Run: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37130220744.
Download: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37130220744/artifacts/11276691408.
Artefato: AeternumCustomPortal-0.3.2-Paper26.2, contém aeternum-custom-portal-0.3.2.jar; expira 01/01/2027.
