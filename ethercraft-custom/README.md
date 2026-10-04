# AeternumCustomPortal 0.5.1

Addon de portais para Paper 26.2 / Java 25. Usa mundos já carregados pelo AeternumSeasons ou outro gerenciador; não cria/carrega dimensões nem depende de caminhos antigos de saves. O módulo fica em `ethercraft-custom` para manter seu histórico.

## Instalação e atualização

Pare o servidor, substitua o JAR anterior por `aeternum-custom-portal-0.5.1.jar` e reinicie. Não mantenha o EtherCraftCustom antigo instalado. Mantenha features Frost/Heat do Aeternum habilitadas para ele carregar os mundos.

A configuração ativa está em **plugins/AeternumCustomPortal/portal-types.yaml**; `config.yml` controla integração, retorno automático e posicionamento. Configurações personalizadas não são sobrescritas. Na primeira migração EtherCraftCustom → AeternumCustomPortal, os três YAMLs antigos são copiados somente se a pasta nova não existe; ajuste os destinos antigos para aeternum_frost/aeternum_heat.

Na atualização para 0.3.3, registros schema 1/2 são copiados para `portals-before-0.3.3.yml`, os vínculos antigos são descartados uma vez e o arquivo passa a schema 3. Frames permanecem: na próxima travessia os pares são buscados/criados pelas coordenadas correspondentes. Isso corrige os pares antigos criados junto ao spawn. Vínculos manuais anteriores também precisam ser refeitos. Pares schema 3 permanecem após reinício.

## Configuração ao vivo

`/acp reload` lê novamente portal-types.yaml e config.yml, mostra a versão/arquivo ativo e lista origens, habilitação, item/modo/material e destino carregado/não carregado. YAML inválido é informado e não substitui definições ativas. Mudanças de origens/destino/habilitação descartam pares antigos do tipo, incluindo parceiros descarregados. Material antigo, tipo desativado e mundo agora proibido apagam o interior e o registro carregado; o frame permanece editável. Mundos descarregados são reconciliados quando carregam.

Para aplicar Heat diretamente no arquivo ativo, execute **no mundo de origem** `/acp enable heat`. Salva enabled:true e, se source-worlds estiver vazio, adiciona o mundo atual como origem. Não muda fontes já configuradas. `/acp disable heat` salva e aplica a desativação. Falha de validação/reconciliação restaura o arquivo anterior. No console, enable mantém fontes vazias; configure-as explicitamente.

O YAML padrão habilita Frost BLUE_ICE + SNOWBALL/PROJECTILE, origem world, destino aeternum_frost. Heat continua desabilitado por padrão, NETHER_WART_BLOCK + FLINT_AND_STEEL/INTERACT, destino aeternum_heat. Aether é exemplo desabilitado e precisa de mundo existente. `enabled:false` bloqueia ativação/viagem; `source-worlds:[]` não autoriza origens. Nomes exatos e chaves namespace:key são aceitos; `/acp worlds` lista as identidades e os caminhos reais da API. Material deve existir no Bukkit. PROJECTILE aceita SNOWBALL/EGG; INTERACT usa a mão principal. Frame 4×5 com cantos opcionais na origem, interior 2×3, eixos X/Z; OBSIDIAN reservado ao Nether vanilla.

## Portal horizontal de musgo e água (0.4.0)

Adicione este tipo abaixo de `portals:` no arquivo **já existente** `plugins/AeternumCustomPortal/portal-types.yaml`. O addon preserva seus arquivos; atualizar o JAR não adiciona exemplos automaticamente. Substitua `SET_LOADED_WORLD_NAME` pelo nome/chave exato de um mundo carregado, ajuste as origens e então habilite/recarregue. O exemplo distribuído vem desativado, pois o novo destino ainda não foi definido.

```yaml
  twilight:
    enabled: false
    shape: HORIZONTAL_POOL
    frame-block: MOSS_BLOCK
    activation:
      item: DIAMOND
      mode: DROP_ITEM
    source-worlds: [world]
    destination-world: SET_LOADED_WORLD_NAME
```

Monte um quadrado horizontal 4×4 no chão: 12 blocos de musgo na borda, incluindo os quatro cantos, e uma piscina 2×2 de quatro fontes de água no centro. O fundo sob as quatro águas precisa ser sólido e seguro. Cada um dos 12 musgos deve ter uma flor de um bloco por cima; podem ser iguais ou variadas. Aceitas: dandelion, poppy, blue orchid, allium, azure bluet, tulipas, oxeye daisy, cornflower, lily of the valley, torchflower e eyeblossoms. Flores de dois blocos, wither rose, grama e moss carpet não contam.

Depois de habilitar com `enabled: true` e `/acp reload`, jogue um diamante na água com Q. Somente uma unidade é consumida após ativação bem-sucedida; item incorreto, falta de permissão, destino não carregado, tipo desativado ou proteção negada não consomem o catalisador. O item lançado é acompanhado por até cinco segundos, sem alterar seu tempo de coleta; se for coletado/mesclado antes de alcançar a água, jogue novamente. A ativação produz um raio visual sem dano/fogo. A água permanece água; partículas de portal marcam a piscina ativa. Isso é uma mecânica do addon inspirada no Twilight Forest, não instala o mod nem cria o mundo dele.

Entrar na água leva ao destino. Na primeira viagem o addon gera outra piscina com musgo e seis variedades de flores, preservando o fundo existente, nas coordenadas correspondentes, salva o par nos dois sentidos e posiciona o jogador sobre a borda segura. O retorno não exige outro diamante. Quebrar musgo, flor, fundo ou alterar as fontes de água invalida/desregistra o portal e desfaz o vínculo; a água restante permanece. Reparar a estrutura exige ativar novamente. Portais ativos persistem após reinício; `/acp disable twilight` ou remover o registro interrompe as partículas/viagem.

`activation.mode: INTERACT` permite alternativamente clicar na estrutura com o item da mão principal e também consome um catalisador após sucesso. `DROP_ITEM` só é aceito em HORIZONTAL_POOL; PROJECTILE fica reservado aos portais verticais. Sem `shape`, os tipos anteriores continuam VERTICAL. O plano do retorno horizontal verifica 76 posições (28 edições e 48 espaços livres), respeitando borda/proteções e rollback; usa somente chunks já carregados para as partículas.

## Viagem e geração automática

O jogador constrói e ativa somente o portal de origem. Na primeira travessia, o addon calcula X/Z do destino como **coordenada × escala da origem ÷ escala do destino**, com `World.getCoordinateScale()`. Escalas iguais preservam coordenadas; não há busca/teleporte pelo spawn. X/Z são limitados à borda, reservando espaço para o frame. A busca examina os solos com espaço livre em toda a altura utilizável, priorizando a altura correspondente da origem.

Primeiro busca o portal registrado compatível e livre mais próximo do ponto correspondente (16 blocos por padrão em ambiente Nether, 128 nos outros; `portal-placement.search-radius` pode substituir). Se não houver, procura/cria o frame com mesmo material/eixo num raio horizontal de 8 (`creation-radius`, máximo 16). Acende e salva o vínculo nos dois sentidos. O retorno leva ao portal exato de origem; vários portais não convergem arbitrariamente para o primeiro portal perto do spawn.

A chegada prefere saída lateral segura, aceitando vegetação baixa/neve passável. Se não houver, pode chegar dentro do portal intacto, apoiado no frame inferior, sem exigir uma plataforma lateral na origem. Líquidos, fogo e outros blocos perigosos continuam excluídos. Cooldown impede retorno imediato.

A criação exige apoio sólido existente e espaço livre acima. Preserva os pisos de saída e o fundo da piscina. O plano verifica 32 posições no vertical e 76 no horizontal, com proteção e rollback. A opção `allow-terrain-clearing` só permite limpar vegetação substituível; não escava paredes/tetos sólidos. A moldura é encaixada somente na superfície de terreno substituível. Líquidos, containers, bedrock, obsidian e registros de outros portais não são substituídos.

WorldGuard/GriefPrevention são consultados para a criação/remoção; falhas bloqueiam a operação. Provedores configurados sem integração mantêm bloqueio por padrão. Nenhum mundo é carregado para satisfazer uma rota. Sem destino/área permitida, há mensagem de erro. Com `auto-return-portal:false`, não cria portais e só usa um destino existente nas coordenadas correspondentes; não há fallback ao spawn.

## Quebra, integração e comandos

Frames são destrutíveis normalmente, respeitando cancelamentos de proteção. Quebra, explosão, colocação e pistão que invalidem a estrutura removem interior/registro/vínculo no tick seguinte; preservam o parceiro. Limpeza também ocorre ao iniciar/carregar mundo e no reload.

`aeternum.take-over-portals:true` retira apenas os quatro listeners conhecidos do Aeternum 4.5: FrostOverworldPortals, HeatOverworldPortals, HeatNetherPortals e VanillaPortalIsolation em Kinkin.aeternum.portal. Reconcilia a cada segundo para cobrir re-registros; mantém geração, estações, mobs e outros sistemas. Hook limitado às classes auditadas; instalação/remoção exige reinício completo, sem hot unload. Não importa vínculos do Aeternum. Portais customizados sem registro não recebem rota Nether vanilla.

Comandos `/acp` (aliases aeternumportal/ethercraft): status, worlds, types, reload, enable <tipo>, disable <tipo>, select, link, unlink, remove. Select/link são opcionais para definir um par manual. Permissões aeternumcustomportal.admin, .portal.activate (OP), .portal.use (todos). Os nós ethercraft.* são aliases positivos; negações antigas do LuckPerms precisam ser revisadas nos novos nós.

## Build e validação

`mvn --batch-mode --file ethercraft-custom/pom.xml clean verify`

Workflow usa Java 25 e publica AeternumCustomPortal-0.5.1-Paper26.2. Testes incluem leitura/edição/releitura do YAML real em disco, escalas/coordenadas positivas/negativas, borda, rejeição de portais distantes do ponto correspondente, terreno, geometria, limpeza, autorização, geometria/validade da piscina, plano de retorno horizontal e compatibilidade de YAML antigo. Não foi executado um servidor Paper real; validar a build com o Aeternum/WorldGuard instalados, incluindo viagem, retorno, quebra/reativação e reload. O fluxo de criação e posicionamento corresponde à intenção do Nether, mas o addon mantém formatos fixos (vertical 4×5 ou piscina horizontal 4×4) e vínculos um-a-um; não reproduz toda a implementação interna do Vanilla.


## Diagnósticos e idiomas (0.5.0)

Os diagnósticos de ativação, autorização e construção de retorno ficam desligados por padrão.
`debug.enabled: true` habilita o modo de testes; somente operadores recebem os detalhes no chat,
mesmo que outro jogador possua a permissão de administrador. `debug.log-to-console` controla
os diagnósticos no console durante os testes. Falhas administrativas continuam registradas no console.
Mensagens repetidas têm intervalo de três segundos por jogador e categoria.

`messages.player-feedback` aceita `ACTION_BAR` (padrão, aviso breve fora do chat), `OFF`
(silêncio durante a jogabilidade) ou `CHAT`. Respostas a comandos permanecem no chat.
As novas opções são adicionadas ao `config.yml` existente sem sobrescrever valores configurados.

Qualquer jogador com `aeternumcustomportal.language` (padrão: todos) pode usar:

- `/acp languages`: listar os idiomas.
- `/acp language pt_BR`: salvar a própria preferência; aceita também `lang`.
- `/acp language auto`: remover a preferência individual e usar a regra do servidor.

Idiomas incluídos: `en_US`, `es_ES`, `id_ID`, `it_IT`, `fr_FR`, `de_DE`, `pt_BR`,
`ru_RU`, `pl_PL`, `vi_VN`, `tr_TR`. O padrão é `language.default: pt_BR`.
Com `language.use-client-locale: true`, o idioma do cliente é usado se suportado;
a escolha explícita do jogador sempre tem prioridade. Idiomas de cliente não suportados
usam o padrão do servidor. Preferências ficam em `player-languages.yml`, por UUID.

Traduções podem ser editadas em `languages/<locale>.yml`; preserve os placeholders `{0}` etc.
`/acp reload` reaplica configurações de portal e traduções. Mensagens ausentes nas traduções
personalizadas usam a tradução incluída para aquele idioma. YAML inválido ou placeholders
incompatíveis bloqueiam o reload e preservam o catálogo de idiomas em memória.
Os nomes técnicos de materiais, mundos, tipos de portal e comandos permanecem intactos.


## Cantos e geração no solo (0.5.1)

Nos portais verticais feitos por jogadores, somente os dez blocos da moldura fora dos cantos
precisam usar o material configurado. Os quatro cantos podem estar vazios ou usar outros blocos.
O formato continua 4×5. A piscina horizontal mantém todos os doze blocos de borda e suas flores.
Portais verticais gerados pelo addon continuam completos, incluindo os quatro cantos.

A geração busca apoio sólido existente em toda a altura utilizável do destino, dentro do raio
configurado e próximo às coordenadas correspondentes. Não cria uma plataforma no ar.
O chão das saídas verticais e o fundo das piscinas são preservados. Piscinas são encaixadas
na camada do solo: toda a área 4×4 deve ter solo sólido e uma camada de apoio sólida abaixo.
São reservados três blocos de ar acima da água e três acima das flores na borda.
As saídas verticais também reservam três blocos de ar acima do chão.

O addon altera somente os blocos do plano de construção, respeitando as verificações de proteção
e a lista limitada de terreno substituível. Baús e materiais fora dessa lista são preservados;
se não houver local compatível, a viagem é bloqueada sem montar portal suspenso.
O espaço acima deve estar livre: não escava paredes ou tetos sólidos para abrir uma sala.
`portal-placement.allow-terrain-clearing: false` exige ar no espaço acima; com `true`,
pode limpar vegetação substituível, mantendo o apoio e encaixando a estrutura na superfície. Não exige WorldEdit.
