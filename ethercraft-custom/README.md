# AeternumCustomPortal 0.3.1

Addon independente de portais para Paper 26.2 / Java 25. AeternumSeasons é opcional: o addon usa mundos já carregados por ele ou por outro gerenciador. Não cria, carrega, renomeia nem migra dimensões. O módulo permanece na pasta `ethercraft-custom` para preservar o histórico do repositório.

## Instalação e atualização

1. Pare o servidor e faça backup das configurações e registros de portais.
2. Remova o JAR `ethercraft-custom-*.jar` antigo. Mantenha o AeternumSeasons instalado e suas features Frost/Heat habilitadas para ele carregar os mundos.
3. Instale `aeternum-custom-portal-0.3.1.jar` e reinicie.
4. Configure `plugins/AeternumCustomPortal/portal-types.yaml`; use `/aeternumportal worlds` para conferir nomes, chaves, UUIDs e caminhos reais.
5. Execute `/aeternumportal reload`.

Na primeira instalação, se a pasta nova não existir, o addon copia `config.yml`, `portal-types.yaml` e `portals.yml` de `plugins/EtherCraftCustom`, sem apagar a origem. Destinos e fontes antigos são preservados: ajuste o destino de Frost para `aeternum_frost` e Heat para `aeternum_heat`. Os mundos `ethercraft_*` antigos permanecem intactos. Não importa vínculos de portais do Aeternum; reconstrua/ative os novos frames e use select/link se necessário. Remoção do addon e alterações no Aeternum exigem reinício completo; PlugMan/hot unload não é suportado.

## Configuração e uso

O YAML padrão habilita Frost: BLUE_ICE + bola de neve, fonte `world`, destino `aeternum_frost`. Heat e Aether são exemplos desabilitados. Habilite Heat e preencha `source-worlds: [world]` para usar NETHER_WART_BLOCK + clique com FLINT_AND_STEEL, destino `aeternum_heat`. Aether exige um mundo já carregado definido pelo administrador.

Cada seção em `portals` define um tipo. `enabled: false` bloqueia ativação e transporte. `source-worlds: []` não autoriza fontes. Materiais devem existir na API Bukkit. PROJECTILE aceita SNOWBALL e EGG; INTERACT aceita itens válidos na mão principal. Frame completo 4×5 incluindo cantos, interior 2×3, eixos X/Z. OBSIDIAN é reservado ao Nether vanilla.

Nomes exatos e chaves completas (`namespace:key`) são aceitos. A chave real de um mundo não é inferida do nome: consulte `/aeternumportal worlds`. Os registros de portais usam o UUID fornecido pelo Paper. Nenhuma busca em pastas antigas, manifesto de mundo ou leitura direta de uid.dat é feita.

Com `auto-return-portal: true` (padrão, inclusive em configs antigas), a primeira travessia reutiliza um portal registrado compatível sem vínculo ou cria o portal de retorno próximo ao spawn do destino, vinculando os dois sentidos. A construção procura até 8 blocos ao redor do spawn, acima do terreno, aceita somente ar nos 24 blocos alterados (frame/interior e quatro pisos de saída), respeita a borda e consulta proteções. Não escava/substitui blocos existentes. Sem área/proteção permitida, bloqueia a travessia com motivo. Com `auto-return-portal: false`, portais sem vínculo levam a uma posição segura próxima ao spawn do destino configurado. No destino, um portal do mesmo tipo retorna à única fonte; com várias fontes, é necessário vincular o par. O retorno automático inclui o frame e quatro blocos de piso para saída segura, usando o material do tipo. É necessário manter uma saída segura também na origem. Sem destino carregado ou saída segura, o teleporte é bloqueado. Para conectar dois portais específicos, olhe para a origem, use `select`, vá ao destino e use `link`. Vínculos inválidos não caem silenciosamente no spawn.

Comandos: `/aeternumportal status|worlds|types|reload|select|link|unlink|remove`. Aliases `/acp` e `/ethercraft`. Os comandos antigos create/visit foram removidos. Permissões: `aeternumcustomportal.admin`, `.portal.activate` (OP) e `.portal.use` (todos); os nós antigos `ethercraft.*` são aliases positivos de compatibilidade. Revise negações explícitas no LuckPerms para os novos nós.

## Convivência com Aeternum e proteções

`aeternum.take-over-portals: true` remove apenas os listeners conhecidos `Kinkin.aeternum.portal.FrostOverworldPortals`, `HeatOverworldPortals`, `HeatNetherPortals` e `VanillaPortalIsolation`. A reconciliação ocorre no início e a cada segundo, cobrindo re-registro após reload. Geração, estações, mobs e outros listeners ficam ativos. Integração limitada às classes auditadas do Aeternum 4.5; uma versão com outras classes requer nova adaptação. Não desabilite a tomada de controle se quiser que o YAML seja a única autoridade de portais.

Interiores customizados sem registro são bloqueados no evento de portal para evitar rotas Nether vanilla. Portais antigos não são apagados. Portais registrados desativados continuam protegidos contra quebra até `/aeternumportal remove`. Nether vanilla com frame OBSIDIAN continua disponível.

WorldGuard 7 e GriefPrevention são consultados na ativação/remoção; falhas de integração bloqueiam operações. Outros plugins de proteção configurados exigem integração ou opt-in explícito. Teleporte usa evento Bukkit cancelável para permitir proteções externas.

## Build e validação

`mvn --batch-mode --file ethercraft-custom/pom.xml clean verify`

Workflow `.github/workflows/ethercraft-custom.yml` usa Java 25 e publica o artefato `AeternumCustomPortal-0.3.1-Paper26.2` contendo o JAR. Testes cobrem geometria, autorização, conflitos, nomes/chaves, ida/volta sem vínculo e falhas nas proteções. A execução real com Paper/Aeternum/WorldGuard deve ser validada no servidor: criar um portal Frost, testar ida/volta, desativá-lo e conferir que o portal antigo do Aeternum não assume a rota.
