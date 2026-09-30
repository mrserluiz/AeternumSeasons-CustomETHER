# EtherCraft Custom 0.2.0 — Portais configuráveis

Plugin independente do Aeternum, com Frost próprio e motor de portais definido em YAML.
O código descompilado original permanece intacto. Versão experimental para Paper 26.2 e Java 25.

## Editar e adicionar tipos de portal

Arquivo no servidor: `plugins/EtherCraftCustom/portal-types.yaml`.
O arquivo padrão inclui Frost ativo e exemplos Heat/Aether desativados.

Exemplo completo com um tipo adicional:

```yaml
schema: 1
portals:
  frost:
    enabled: true
    frame-block: BLUE_ICE
    activation:
      item: SNOWBALL
      mode: PROJECTILE
    source-worlds: [world]
    destination-world: ethercraft_frost

  cristal:
    enabled: true
    frame-block: AMETHYST_BLOCK
    activation:
      item: AMETHYST_SHARD
      mode: INTERACT
    source-worlds: [world]
    destination-world: mundo_cristal
```

Para adicionar outro tipo, copie uma seção dentro de `portals`, atribua um ID novo
(como `cristal`) e edite bloco, item, modo e mundos. Use o nome exato dos mundos.
`source-worlds` pode conter várias origens; o destino é um mundo por tipo.
Até 64 tipos. Os IDs aceitam letras minúsculas, números, `_` e `-`, começando com letra.

| Campo | Função |
|---|---|
| `enabled` | Habilita o tipo; desativar bloqueia novas ativações e viagens |
| `frame-block` | Material sólido que forma toda a moldura |
| `activation.item` | Material do item usado para ativar |
| `activation.mode: INTERACT` | Clique direito no frame com o item na mão principal |
| `activation.mode: PROJECTILE` | Lance SNOWBALL ou EGG contra o frame |
| `source-worlds` | Lista explícita de mundos de origem autorizados |
| `destination-world` | Nome do mundo de destino; precisa estar carregado |

Os nomes dos blocos/itens são os de `Bukkit Material`, em inglês; também é aceito
formato como `minecraft:blue_ice`. `INTERACT` aceita itens válidos em geral e
não consome o item nem desgasta ferramentas. Em `PROJECTILE`, o arremesso segue
o consumo normal do jogo. OBSIDIAN fica reservado aos portais vanilla do Nether.

Execute `/ethercraft reload` após editar. O reload altera somente `portal-types.yaml`.
Para alterações em `config.yml`, reinicie. Uma definição inválida rejeita o reload
inteiro e mantém os tipos anteriores; o erro identifica o campo correspondente.
`/ethercraft types` lista bloco, item, modo e destino de cada tipo.

Combinações idênticas de bloco + item + modo não podem se sobrepor nos mesmos
mundos. Com o mesmo frame e itens diferentes, o item usado na primeira ativação
define o tipo daquele portal. Cada par vinculado precisa ter o mesmo tipo.

## Estrutura e vínculo

Todos os tipos usam frame completo de **4×5**, com os quatro cantos, interior
**2×3** vazio e eixos X/Z. O interior ativo é NETHER_PORTAL.

1. Configure os mundos de origem no YAML e carregue o destino.
2. Construa o frame, ative com o item definido e olhe para a moldura ativa.
3. Execute `/ethercraft select` para selecionar a origem.
4. Vá ao destino, construa/ative outro frame do mesmo tipo e olhe para ele.
5. Execute `/ethercraft link`. O vínculo é persistente e vale nos dois sentidos.
6. Prepare piso sólido e espaço livre de dois blocos ao lado dos dois frames.

O motor verifica a rota declarada no YAML durante o vínculo e durante cada viagem.
Uma alteração de destino/origens pode invalidar vínculos antigos, que permanecem
salvos até a intervenção do administrador.

Para trocar o bloco de um portal já construído: execute `/ethercraft remove`,
altere o YAML, recarregue, reconstrua e ative novamente. Editar o YAML não transforma
blocos existentes. Registros guardam o ID e o bloco original para evitar mudanças
silenciosas. Remover/desativar um tipo impede viagens, mas os frames cadastrados
permanecem protegidos até serem removidos pelo administrador.

## Frost próprio

`/ethercraft create` cria/carrega apenas o mundo Frost configurado em `config.yml`,
com manifesto de propriedade. `/ethercraft visit` leva o administrador ao spawn.
Use o mesmo nome no `destination-world` do tipo Frost.

O Frost usa geração vanilla e nove biomas frios em regiões determinísticas.
Não há criação/carregamento automático ao habilitar o plugin nem durante teleporte.
Após reiniciar, carregue os mundos antes de usar os portais.

Novos tipos podem levar a mundos já criados/carregados por outro sistema. Os exemplos
Heat/Aether definem portais; os generators dessas dimensões ainda estão pendentes.

## Comandos e permissões

`/ethercraft status|types|reload|create|visit|select|link|unlink|remove`

- `unlink`: desfaz o vínculo nos dois sentidos.
- `remove`: desfaz o vínculo, apaga o registro, limpa o interior e libera o frame.
- `ethercraft.admin`: administração (OP por padrão).
- `ethercraft.portal.activate`: ativação (OP por padrão).
- `ethercraft.portal.use`: viagem (todos por padrão).

`portal-types.yaml` é a configuração editável de tipos.
`portals.yml` é o cadastro persistente de localizações e vínculos; não é o arquivo de tipos.
Registros v0.1/schema 1 são lidos como Frost/BLUE_ICE e passam a schema 2 na próxima gravação.
Na primeira criação do arquivo de tipos, origens autorizadas e nome Frost de um
`config.yml` v0.1 são transferidos; arquivos de tipos já existentes são preservados.

## Compilar

Java 25 e Maven 3.9+:

```sh
mvn --batch-mode --file ethercraft-custom/pom.xml clean verify
```

Saída: `ethercraft-custom/target/ethercraft-custom-0.2.0.jar`.
O workflow GitHub Actions configura build e artifact após publicação da branch.
Referência: https://docs.papermc.io/paper/dev/project-setup/

## Verificação e limites

- Passaram 19 verificações Java das regras de definição/rota: ida/volta, origens,
  desativação, duplicidade, sobreposição e IDs inválidos.
- YAML padrão lido e validado estruturalmente; parser Java passou em todos os arquivos.
- Geometria 4×5/2×3 validada; `git diff --check` sem erros.
- Há testes JUnit no projeto para execução pelo Maven.
- Build completo/API Paper, testes JUnit e servidor real ainda não executados.
  Ambiente local disponível: Java 17, sem Maven; sem JAR compilado.
- Entidades não jogadoras ficam bloqueadas nesses portais.
- Construção automática de destinos, geradores Heat/Aether e hooks de proteção pendentes.
- Ativação bloqueia quando uma proteção listada no config está habilitada.
  `allow-unintegrated-protections` permite opt-in para testes isolados; coexistência não validada.
- `/visit` é exploração administrativa e não constrói plataforma de spawn.
- Não migra mundos/links do Aeternum. Não há suporte Folia nem 26.3 validado.

## Descoberta sobre o original

Em `AeternumSeasonsPlugin.onEnable`, `VanillaPortalIsolation.register()` ocorre
antes dos grupos Frost/Heat. Frost registra portais antes de `ensureFrostWorld`;
Heat cria/carrega antes dos listeners específicos. As flags Frost/Heat desligam
os respectivos grupos, mas não todas as instanciações nem VanillaPortalIsolation.
Coexistência precisa de teste separado antes de uso em produção.
