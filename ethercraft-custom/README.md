# AeternumCustomPortal 0.3.3

Addon de portais para Paper 26.2 / Java 25. Usa mundos já carregados pelo AeternumSeasons ou outro gerenciador; não cria/carrega dimensões nem depende de caminhos antigos de saves. O módulo fica em `ethercraft-custom` para manter seu histórico.

## Instalação e atualização

Pare o servidor, substitua o JAR anterior por `aeternum-custom-portal-0.3.3.jar` e reinicie. Não mantenha o EtherCraftCustom antigo instalado. Mantenha features Frost/Heat do Aeternum habilitadas para ele carregar os mundos.

A configuração ativa está em **plugins/AeternumCustomPortal/portal-types.yaml**; `config.yml` controla integração, retorno automático e posicionamento. Configurações personalizadas não são sobrescritas. Na primeira migração EtherCraftCustom → AeternumCustomPortal, os três YAMLs antigos são copiados somente se a pasta nova não existe; ajuste os destinos antigos para aeternum_frost/aeternum_heat.

Na atualização para 0.3.3, registros schema 1/2 são copiados para `portals-before-0.3.3.yml`, os vínculos antigos são descartados uma vez e o arquivo passa a schema 3. Frames permanecem: na próxima travessia os pares são buscados/criados pelas coordenadas correspondentes. Isso corrige os pares antigos criados junto ao spawn. Vínculos manuais anteriores também precisam ser refeitos. Pares schema 3 permanecem após reinício.

## Configuração ao vivo

`/acp reload` lê novamente portal-types.yaml e config.yml, mostra a versão/arquivo ativo e lista origens, habilitação, item/modo/material e destino carregado/não carregado. YAML inválido é informado e não substitui definições ativas. Mudanças de origens/destino/habilitação descartam pares antigos do tipo, incluindo parceiros descarregados. Material antigo, tipo desativado e mundo agora proibido apagam o interior e o registro carregado; o frame permanece editável. Mundos descarregados são reconciliados quando carregam.

Para aplicar Heat diretamente no arquivo ativo, execute **no mundo de origem** `/acp enable heat`. Salva enabled:true e, se source-worlds estiver vazio, adiciona o mundo atual como origem. Não muda fontes já configuradas. `/acp disable heat` salva e aplica a desativação. Falha de validação/reconciliação restaura o arquivo anterior. No console, enable mantém fontes vazias; configure-as explicitamente.

O YAML padrão habilita Frost BLUE_ICE + SNOWBALL/PROJECTILE, origem world, destino aeternum_frost. Heat continua desabilitado por padrão, NETHER_WART_BLOCK + FLINT_AND_STEEL/INTERACT, destino aeternum_heat. Aether é exemplo desabilitado e precisa de mundo existente. `enabled:false` bloqueia ativação/viagem; `source-worlds:[]` não autoriza origens. Nomes exatos e chaves namespace:key são aceitos; `/acp worlds` lista as identidades e os caminhos reais da API. Material deve existir no Bukkit. PROJECTILE aceita SNOWBALL/EGG; INTERACT usa a mão principal. Frame completo 4×5 incluindo cantos, interior 2×3, eixos X/Z; OBSIDIAN reservado ao Nether vanilla.

## Viagem e geração automática

O jogador constrói e ativa somente o portal de origem. Na primeira travessia, o addon calcula X/Z do destino como **coordenada × escala da origem ÷ escala do destino**, com `World.getCoordinateScale()`. Escalas iguais preservam coordenadas; não há busca/teleporte pelo spawn. X/Z são limitados à borda, reservando espaço para o frame. Y começa na altura da origem, limitado à altura lógica da dimensão, e a busca examina ±16 blocos verticalmente.

Primeiro busca o portal registrado compatível e livre mais próximo do ponto correspondente (16 blocos por padrão em ambiente Nether, 128 nos outros; `portal-placement.search-radius` pode substituir). Se não houver, procura/cria o frame com mesmo material/eixo num raio horizontal de 8 (`creation-radius`, máximo 16). Acende e salva o vínculo nos dois sentidos. O retorno leva ao portal exato de origem; vários portais não convergem arbitrariamente para o primeiro portal perto do spawn.

A chegada prefere saída lateral segura, aceitando vegetação baixa/neve passável. Se não houver, pode chegar dentro do portal intacto, apoiado no frame inferior, sem exigir uma plataforma lateral na origem. Líquidos, fogo e outros blocos perigosos continuam excluídos. Cooldown impede retorno imediato.

A criação preferencial usa espaços livres e prepara quatro pisos de saída. Se não houver, `allow-terrain-clearing:true` permite limpar uma lista limitada de terreno (pedra, terra, netherrack, neve, gelo e afins), com checks de quebra/colocação para todos os 32 blocos do plano e snapshots para rollback. Não substitui líquidos, containers, bedrock, obsidian nem registros de outros portais. Construções sem proteção feitas com os materiais de terreno listados também podem ser afetadas por esse fallback, como na construção de um portal; desative a limpeza para exigir espaço livre.

WorldGuard/GriefPrevention são consultados para a criação/remoção; falhas bloqueiam a operação. Provedores configurados sem integração mantêm bloqueio por padrão. Nenhum mundo é carregado para satisfazer uma rota. Sem destino/área permitida, há mensagem de erro. Com `auto-return-portal:false`, não cria portais e só usa um destino existente nas coordenadas correspondentes; não há fallback ao spawn.

## Quebra, integração e comandos

Frames são destrutíveis normalmente, respeitando cancelamentos de proteção. Quebra, explosão, colocação e pistão que invalidem a estrutura removem interior/registro/vínculo no tick seguinte; preservam o parceiro. Limpeza também ocorre ao iniciar/carregar mundo e no reload.

`aeternum.take-over-portals:true` retira apenas os quatro listeners conhecidos do Aeternum 4.5: FrostOverworldPortals, HeatOverworldPortals, HeatNetherPortals e VanillaPortalIsolation em Kinkin.aeternum.portal. Reconcilia a cada segundo para cobrir re-registros; mantém geração, estações, mobs e outros sistemas. Hook limitado às classes auditadas; instalação/remoção exige reinício completo, sem hot unload. Não importa vínculos do Aeternum. Portais customizados sem registro não recebem rota Nether vanilla.

Comandos `/acp` (aliases aeternumportal/ethercraft): status, worlds, types, reload, enable <tipo>, disable <tipo>, select, link, unlink, remove. Select/link são opcionais para definir um par manual. Permissões aeternumcustomportal.admin, .portal.activate (OP), .portal.use (todos). Os nós ethercraft.* são aliases positivos; negações antigas do LuckPerms precisam ser revisadas nos novos nós.

## Build e validação

`mvn --batch-mode --file ethercraft-custom/pom.xml clean verify`

Workflow usa Java 25 e publica AeternumCustomPortal-0.3.3-Paper26.2. Testes incluem leitura/edição/releitura do YAML real em disco, escalas/coordenadas positivas/negativas, borda, rejeição de portais distantes do ponto correspondente, terreno, geometria, limpeza e autorização. Não foi executado um servidor Paper real; validar a build com o Aeternum/WorldGuard instalados, incluindo viagem, retorno, quebra/reativação e reload. O fluxo de criação e posicionamento corresponde à intenção do Nether, mas o addon mantém frame fixo 4×5 e vínculos um-a-um; não reproduz toda a implementação interna do Vanilla.
