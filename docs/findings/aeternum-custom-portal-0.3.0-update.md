# AeternumCustomPortal 0.3.0 — 03/10/2026

Pedido: renomear EtherCraftCustom e controlar portais como addon independente, usando os mundos Aeternum já criados.

## Confirmado pelo código

- Identidade do plugin/JAR alterada para AeternumCustomPortal / aeternum-custom-portal-0.3.0.jar; comando aeternumportal, aliases acp/ethercraft.
- DimensionService e FrostGenerator removidos: não há mais criação/carregamento de mundos, manifesto próprio, montagem de caminhos ou leitura de uid.dat.
- LoadedWorlds.resolve usa nomes/chaves dos mundos carregados e rejeita referência ambígua; o registro preserva UUIDs do Paper. Comando worlds exibe getWorldPath da API.
- YAML padrão do usuário aplicado: Frost BLUE_ICE/SNOWBALL/PROJECTILE, [world], aeternum_frost; Heat NETHER_WART_BLOCK/FLINT_AND_STEEL/INTERACT desabilitado, aeternum_heat; Aether permanece exemplo desabilitado.
- Tipos desativados não ativam nem transportam. Reload inválido preserva as definições anteriores. Mundo destino ausente bloqueia ativação; ausência/invalidade após ativação bloqueia transporte.
- Sem vínculo, portal leva ao spawn seguro do destino carregado. Retorno sem vínculo requer uma única origem. Com várias fontes, select/link escolhe o par. Não há construção de plataforma/portal de destino.
- AeternumPortalBridge remove exclusivamente quatro classes de listeners de portal auditadas do Aeternum 4.5. Reconcilia re-registros uma vez por segundo. Não altera configurações ou classes originais, mundos, estações ou mobs. Requer reinício completo na instalação/remoção; não suporta hot unload.
- Portais customizados não registrados com base sem OBSIDIAN são bloqueados no evento vanilla; os blocos antigos não são apagados. Não há importação automática dos vínculos do Aeternum.
- Migração copia os três YAMLs antigos apenas quando a pasta nova não existe. Preserva os arquivos e destinos antigos; o administrador precisa ajustar o YAML migrado para aeternum_frost/aeternum_heat. JAR antigo simultaneamente habilitado bloqueia o novo plugin.
- WorldGuard/GriefPrevention continuam com integração e falhas bloqueiam operações. Permissões novas possuem aliases positivos antigos; negações antigas devem ser revisadas no LuckPerms.

## Validação

CI commit: 2d41a1c959abd5baccbd6c33c498b03bc2d501bf.
Run: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37128095230.
Resultado: BUILD SUCCESS, 14 testes, zero falhas/erros/skips.
Artefato: AeternumCustomPortal-0.3.0-Paper26.2, contém aeternum-custom-portal-0.3.0.jar.
Download: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37128095230/artifacts/11275831063.
ZIP SHA-256: bd2bde78bb49236b8806d6c76f7916f947384cbd3ff14e1c5cdffae814a926b2.
Expiração do artefato: 01/01/2027.

Testes de PortalTypeSpec ampliados para nomes/chaves de dimensões, transporte sem vínculo, retorno ambíguo, alias de rota para o mesmo mundo e tipos desabilitados/origens vazias. Testes anteriores de geometria e proteção mantidos.

Não houve execução num servidor Paper real. Permanecem para teste em staging: tomada de controle dos listeners da versão efetivamente instalada, proteção WorldGuard/GP em claims/regiões, saída no spawn e ida/volta, reload/reativação e coexistência com Nether vanilla. O hook depende dos nomes de classe auditados, não de uma API oficial de cooperação do Aeternum.
