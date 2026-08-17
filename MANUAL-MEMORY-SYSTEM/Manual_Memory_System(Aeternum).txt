====================================================================
 AETERNUM SEASONS — MANUAL DE CONTINUIDADE / MEMORY SYSTEM
 Projeto: EtherCraft / AeternumSeasons-CustomETHER
 Ambiente: Minecraft Java 26.2
 Atualizado: 16/08/2026
====================================================================
[0]UPDATE
====================================================================
UPDATE > 17/08/26 - 14:XX - M001I

[PROJETO ETHERCRAFT — ENGENHARIA REVERSA DO AETERNUMSEASONS]

STATUS:
DISCOVERY / ARCHITECTURE

OBJETIVO:
Investigar tecnicamente o AeternumSeasons 4.5 para compreender seus
sistemas internos de portais e dimensões e, posteriormente, reproduzir
somente os mecanismos necessários em uma arquitetura própria do EtherCraft.

A investigação deve evitar modificar o JAR original antes que sua
arquitetura esteja compreendida.

Objetivo arquitetural de longo prazo:

AeternumSeasons 4.5
        ↓
engenharia reversa
        ↓
identificação dos mecanismos necessários
        ↓
separação entre funcionalidades úteis e dependências
        ↓
EtherCraft Custom
        ↓
EtherCraftPortals
EtherCraftDimensions
        ↓
portais próprios
dimensões próprias
resource pack próprio
integração com demais sistemas do EtherCraft


==================================================
[1] FONTE DE CÓDIGO
==================================================

O JAR descompilado do AeternumSeasons 4.5 foi disponibilizado no GitHub:

mrserluiz/AeternumSeasons-CustomETHER

O repositório contém o código descompilado e também o sistema de
continuidade/manual de memória utilizado pelo projeto.

O pacote principal analisado é:

Kinkin.aeternum


==================================================
[2] ALVO PRINCIPAL DA INVESTIGAÇÃO
==================================================

Sistema de portais:

Kinkin.aeternum.portal

Classes identificadas:

FrostOverworldPortals
HeatOverworldPortals
HeatNetherPortals
PortalBuildProtection
PortalFrameClassifier
VanillaPortalIsolation


Objetivo:

descobrir:

1. detecção da estrutura;
2. validação do frame;
3. material do frame;
4. criação do portal;
5. ativação;
6. identificação;
7. linking;
8. persistência;
9. destino;
10. teleporte;
11. isolamento do comportamento vanilla;
12. dependências externas;
13. pontos seguros para futura substituição.


==================================================
[3] FROSTOVERWORLDPORTALS — DESCOBERTAS
==================================================

STATUS:
CONFIRMED ✓

Frost possui uma implementação própria:

FrostPortalShape

A classe possui mecanismos equivalentes a:

detect()
detectWithOrientation()
isValidFrameX()
isValidFrameZ()
buildAt()


==================================================
[4] FRAME DO FROST
==================================================

STATUS:
CONFIRMED ✓

O frame do Frost utiliza:

minecraft:GLOWSTONE

A validação da estrutura verifica explicitamente GLOWSTONE.

A estrutura possui:

width  = 2
height = 3

O frame é construído com GLOWSTONE.

O interior é preenchido com AIR antes da ativação.


==================================================
[5] PORTAL ATIVO DO FROST
==================================================

STATUS:
CONFIRMED ✓

GLOWSTONE não é o bloco interno do portal.

GLOWSTONE:
    estrutura/frame

NETHER_PORTAL:
    interior ativo

O método de ativação cria BlockData de:

Material.NETHER_PORTAL

e configura seu Axis de acordo com a orientação detectada.


==================================================
[6] ATIVAÇÃO DO FROST
==================================================

STATUS:
CONFIRMED ✓

Frost possui comportamento próprio de ativação.

A interação com:

FLINT_AND_STEEL
FIRE_CHARGE

é interceptada quando existe uma estrutura Frost válida.

O evento é cancelado para impedir que o comportamento vanilla simplesmente
assuma o controle.

Frost também possui tratamento de:

ProjectileHitEvent

e reconhece:

Snowball

Uma Snowball atingindo uma estrutura Frost válida pode disparar:

lightPortal(shape)


Conclusão confirmada:

Frost possui mecanismo próprio de ativação e não depende simplesmente
do acendimento vanilla do Nether Portal.


==================================================
[7] ORIENTAÇÃO
==================================================

STATUS:
CONFIRMED ✓

Frost pode existir em:

Axis.X
Axis.Z

A orientação é utilizada tanto na construção quanto na criação do
BlockData de NETHER_PORTAL.


==================================================
[8] PORTALKEY
==================================================

STATUS:
CONFIRMED ✓

O Aeternum possui um identificador próprio para portais.

PortalKey contém informações equivalentes a:

worldName
x
y
z
axis

O PortalKey representa a localização/orientação normalizada do portal.


==================================================
[9] LINKING DO FROST
==================================================

STATUS:
CONFIRMED ✓

Frost possui um mapa:

Map<PortalKey, PortalKey>

denominado:

portalConnections

O sistema mantém vínculos entre:

Portal A → Portal B
Portal B → Portal A


==================================================
[10] PERSISTÊNCIA DOS PORTAIS
==================================================

STATUS:
CONFIRMED ✓

Os vínculos do Frost são persistidos em:

frost_portal_links.yml

O sistema carrega os links durante sua inicialização.

O sistema salva os vínculos durante alterações relevantes e no encerramento.

Portanto o linking não depende somente do estado temporário da memória.


==================================================
[11] RESOLUÇÃO DO DESTINO
==================================================

STATUS:
CONFIRMED ✓

O fluxo geral identificado é:

portalKey(source)
        ↓
findLinkedPortal()
        ↓
link válido?
   ├── SIM
   │    ↓
   destino
   │
   └── NÃO
        ↓
findNearbyPortal()
        ↓
encontrou?
   ├── SIM
   │    ↓
   registerPortalLink()
   │
   └── NÃO
        ↓
findOrCreatePortal()
        ↓
construção do portal
        ↓
lightPortal()
        ↓
registerPortalLink()


==================================================
[12] PROCURA DE PORTAIS
==================================================

STATUS:
CONFIRMED ✓

O sistema procura portais próximos dentro de um raio de aproximadamente:

32 blocos

A procura considera:

NETHER_PORTAL
+
estrutura/frame compatível

e utiliza distância X/Z para encontrar o candidato mais próximo.


==================================================
[13] CRIAÇÃO AUTOMÁTICA
==================================================

STATUS:
CONFIRMED ✓

Quando não existe um portal de destino adequado, o sistema pode:

1. procurar um local seguro;
2. construir o frame;
3. criar o interior;
4. ativar o portal;
5. registrar o vínculo;
6. utilizar o portal como destino.


==================================================
[14] PORTALFRAMECLASSIFIER
==================================================

STATUS:
CONFIRMED ✓

PortalFrameClassifier possui uma função diferente de FrostPortalShape.

Ele classifica portais já existentes pelo material do frame.

Tipos identificados:

OBSIDIAN
    → VANILLA

GLOWSTONE
    → FROST

NETHER_WART_BLOCK
    → HEAT

combinação de materiais
    → CUSTOM_MIXED

nenhum material reconhecido
    → UNKNOWN


Conclusão:

PortalFrameClassifier não substitui FrostPortalShape.

Existem dois níveis:

1. detecção/validação específica para criação do portal;
2. classificação genérica de um portal já existente.


==================================================
[15] VANILLAPORTALISOLATION
==================================================

STATUS:
CONFIRMED ✓

O Aeternum possui uma camada específica para evitar interferência
entre portais customizados e o sistema vanilla.

VanillaPortalIsolation trabalha com eventos relacionados a portal,
incluindo:

PlayerPortalEvent
EntityPortalEvent

e utiliza PortalFrameClassifier para identificar portais vanilla.

A lógica não consiste simplesmente em desligar todos os portais vanilla.

Ela pode separar o destino vanilla de portais customizados e,
quando necessário, criar um portal vanilla separado.


==================================================
[16] HEATOVERWORLDPORTALS
==================================================

STATUS:
CONFIRMED ✓

Heat possui arquitetura semelhante ao Frost.

Heat utiliza:

NETHER_WART_BLOCK

como frame.

O interior ativo continua sendo:

NETHER_PORTAL

Heat possui estrutura equivalente de:

detecção
validação
construção
ativação
linking
teleporte


==================================================
[17] HEAT — DIFERENÇA DE ATIVAÇÃO
==================================================

STATUS:
CONFIRMED ✓

Heat utiliza mecanismos de ativação diferentes do Frost.

Foram identificadas verificações envolvendo:

FLINT_AND_STEEL
FIRE_CHARGE

Portanto a definição do portal não é apenas:

frame + NETHER_PORTAL.

Cada dimensão possui regras específicas de ativação.


==================================================
[18] HEATNETHERPORTALS
==================================================

STATUS:
CONFIRMED ✓

Existe uma segunda implementação:

HeatNetherPortals

Ela controla a outra rota do sistema Heat.

Também possui persistência própria de links:

heat_nether_links.yml

Seu PortalKey possui informações adicionais, incluindo um identificador
de tipo/kind.


==================================================
[19] CONCLUSÃO DA ARQUITETURA DE PORTAIS
==================================================

STATUS:
DISCOVERY ✓

A arquitetura do Aeternum pode ser representada aproximadamente como:

                 PORTAL SYSTEM
                       │
          ┌────────────┼────────────┐
          ↓            ↓            ↓
        FROST         HEAT        VANILLA
          │            │
     GLOWSTONE     WART BLOCK
          │            │
     SNOWBALL      FLINT/FIRE
          │            │
 aeternum_frost  aeternum_heat


Com uma camada transversal:

PortalFrameClassifier
        ↓
classificação

VanillaPortalIsolation
        ↓
isolamento vanilla

PortalBuildProtection
        ↓
local seguro/proteções

PortalKey
        ↓
identificação

portalConnections
        ↓
linking/persistência


==================================================
[20] TROCA GLOWSTONE → BLUE ICE
==================================================

STATUS:
CONFIRMED / IMPLEMENTATION PLAN

É tecnicamente possível substituir o frame Frost:

GLOWSTONE
    ↓
BLUE_ICE

Porém não deve ser feita como uma simples substituição global de string
ou constante.

Os pontos relacionados incluem:

FrostPortalShape
    ↓
validação do frame

FrostPortalShape.buildAt()
    ↓
construção do frame

PortalFrameClassifier
    ↓
identificação de Frost ativo

isGlowstonePortal()/lógicas equivalentes
    ↓
detecção do frame

Portanto a alteração precisa ser coerente em todo o fluxo.


==================================================
[21] DESCOBERTA — DIMENSÕES
==================================================

STATUS:
CONFIRMED ✓

O Aeternum cria seus mundos utilizando mecanismos normais da API Bukkit/Paper.

Para Frost:

WorldCreator
    ↓
nome: aeternum_frost
    ↓
Environment.NORMAL
    ↓
FrostWorldGenerator
    ↓
createWorld()


Para Heat:

WorldCreator
    ↓
nome: aeternum_heat
    ↓
Environment.NETHER
    ↓
HeatWorldGenerator
    ↓
createWorld()


Conclusão:

aeternum_frost e aeternum_heat não dependem necessariamente de mundos
pré-existentes para sua criação.


==================================================
[22] FROSTWORLDGENERATOR
==================================================

STATUS:
CONFIRMED ✓

FrostWorldGenerator estende:

ChunkGenerator

e utiliza:

FrostOnlyBiomeProvider


A geração mantém grande parte dos mecanismos normais de geração
do Minecraft, enquanto o BiomeProvider controla os biomas utilizados.


==================================================
[23] FROSTONlYBIOMEPROVIDER
==================================================

STATUS:
CONFIRMED ✓

FrostOnlyBiomeProvider trabalha com biomas frios.

Foram identificados biomas como:

SNOWY_PLAINS
SNOWY_TAIGA
ICE_SPIKES
FROZEN_RIVER
FROZEN_OCEAN
DEEP_FROZEN_OCEAN
FROZEN_PEAKS
JAGGED_PEAKS
SNOWY_SLOPES


O provider seleciona os biomas com base em seed/coordenadas.

Conclusão:

O Frost Realm do Aeternum é estruturalmente um mundo customizado
através da API de geração, utilizando biomas frios.


==================================================
[24] HEATWORLDGENERATOR
==================================================

STATUS:
CONFIRMED ✓

HeatWorldGenerator utiliza:

HeatBiomeProvider

e também:

HeatRuinsPopulator


A arquitetura é:

HeatWorldGenerator
       │
       ├── HeatBiomeProvider
       │
       └── HeatRuinsPopulator


Portanto Heat possui uma camada adicional para estruturas/população
de chunks.


==================================================
[25] CONCLUSÃO SOBRE CRIAÇÃO DE DIMENSÕES PRÓPRIAS
==================================================

STATUS:
CONFIRMED — VIABILITY

A investigação confirma que é viável arquiteturalmente criar uma
implementação própria baseada na API de mundo:

EtherCraftDimensions
        ↓
WorldCreator
        ↓
EtherFrostGenerator
        ↓
EtherFrostBiomeProvider
        ↓
ethercraft_frost


O sistema próprio poderá eventualmente substituir:

aeternum_frost

por um mundo controlado pelo EtherCraft.


IMPORTANTE:

Isso confirma a viabilidade de criar mundos customizados através da
API utilizada pelo Aeternum.

Ainda NÃO significa que todas as propriedades internas de uma
Minecraft DimensionType podem ser reproduzidas somente através da
API Bukkit/Paper.

Essa parte ainda precisa ser investigada se for necessária.


==================================================
[26] ARQUITETURA FUTURA PROPOSTA
==================================================

DECISION / PROPOSAL

Não limitar o projeto à simples modificação:

GLOWSTONE → BLUE_ICE

A arquitetura desejada passa a ser:

EtherCraft
│
├── EtherCraftPortals
│
├── EtherCraftDimensions
│
├── EtherCraftFrost
│
├── EtherCraftHeat
│
└── EtherTexture


EtherCraftPortals deverá futuramente controlar:

- definição do frame;
- detecção;
- validação;
- ativação;
- linking;
- persistência;
- criação do destino;
- teleporte;
- isolamento vanilla.


EtherCraftDimensions deverá futuramente controlar:

- criação/carregamento;
- WorldCreator;
- ChunkGenerator;
- BiomeProvider;
- populators;
- configuração específica dos mundos.


==================================================
[27] POSSÍVEL DEFINIÇÃO DE PORTAL
==================================================

DESIGN PROPOSAL

Um portal futuro poderia possuir uma definição semelhante a:

PortalDefinition

id:
FROST

frame:
BLUE_ICE

activation:
SNOWBALL

interior:
NETHER_PORTAL

width:
2

height:
3

destination:
ethercraft_frost


Isso permitiria futuramente transformar Frost/Heat/Aether em
configurações do mesmo motor em vez de duplicar toda a lógica.


==================================================
[28] ESTADO ATUAL DO PROJETO
==================================================

CONFIRMED:

[✓] AeternumSeasons 4.5 está sendo analisado por engenharia reversa.

[✓] Código descompilado está disponível no GitHub.

[✓] Frost possui FrostPortalShape próprio.

[✓] Frost utiliza GLOWSTONE como frame.

[✓] Frost utiliza NETHER_PORTAL como interior ativo.

[✓] Frost suporta Axis.X e Axis.Z.

[✓] Frost possui ativação própria.

[✓] Snowball participa da ativação do Frost.

[✓] Frost possui PortalKey.

[✓] Frost possui mapa próprio de vínculos.

[✓] Frost persiste vínculos em frost_portal_links.yml.

[✓] Frost procura portais próximos.

[✓] Frost pode criar automaticamente o destino.

[✓] PortalFrameClassifier reconhece Vanilla/Frost/Heat.

[✓] VanillaPortalIsolation existe para separar o sistema vanilla.

[✓] Heat possui implementação própria.

[✓] Heat utiliza NETHER_WART_BLOCK.

[✓] Heat possui HeatOverworldPortals.

[✓] Heat possui HeatNetherPortals.

[✓] Heat possui persistência própria de links.

[✓] Frost é criado via WorldCreator.

[✓] Heat é criado via WorldCreator.

[✓] Frost utiliza ChunkGenerator.

[✓] Frost utiliza BiomeProvider.

[✓] Heat utiliza BiomeProvider.

[✓] Heat possui BlockPopulator para ruínas.

[✓] É viável arquiteturalmente criar EtherCraftDimensions.

[✓] É viável arquiteturalmente criar EtherCraftPortals.


==================================================
[29] HIPÓTESES / AINDA NÃO CONFIRMADO
==================================================

[?] Se todas as propriedades desejadas de uma DimensionType podem ser
controladas pela API Bukkit/Paper utilizada.

[?] Se o Aeternum utiliza alguma configuração adicional de mundo fora
dos generators/providers já identificados.

[?] Ordem completa de inicialização dos sistemas no
AeternumSeasonsPlugin.

[?] Todas as dependências entre criação de mundos, portais e isolamento
vanilla.

[?] Se é possível desligar seletivamente os sistemas de portal/dimensão
do Aeternum sem efeitos colaterais.


==================================================
[30] PRÓXIMO PASSO
==================================================

Não modificar o Aeternum ainda.

Próxima investigação:

AeternumSeasonsPlugin

Objetivo:

mapear a ordem de inicialização:

Plugin startup
    ↓
world creation
    ↓
FrostWorldGenerator
    ↓
HeatWorldGenerator
    ↓
portal systems
    ↓
VanillaPortalIsolation
    ↓
listeners/services
    ↓
shutdown/save


Também verificar:

- quem instancia FrostOverworldPortals;
- quem instancia HeatOverworldPortals;
- quem instancia HeatNetherPortals;
- quem registra VanillaPortalIsolation;
- quando os mundos são criados;
- quais sistemas dependem dos mundos;
- quais listeners são registrados;
- quais sistemas podem ser substituídos pelo EtherCraft.


==================================================
[31] OBJETIVO FINAL
==================================================

O objetivo não é necessariamente manter o AeternumSeasons como
dependência permanente.

O objetivo é utilizar a engenharia reversa para compreender seus
mecanismos e posteriormente construir uma arquitetura controlável:

AeternumSeasons
       ↓
engenharia reversa
       ↓
entendimento
       ↓
seleção de funcionalidades
       ↓
EtherCraft Custom
       ↓
EtherCraftPortals
EtherCraftDimensions
EtherCraftFrost
EtherCraftHeat
EtherTexture
       ↓
sistemas próprios


Princípio:

NÃO copiar cegamente.

COMPREENDER → ISOLAR → REIMPLEMENTAR → TESTAR.


<END UPDATE>


====================================================================
[1] OBJETIVO PRINCIPAL
====================================================================

OBJETIVO:

Investigar o funcionamento interno do AeternumSeasons e reproduzir
somente os mecanismos necessários para o EtherCraft, evitando modificar
o Aeternum original sempre que isso não for necessário.

O projeto possui duas frentes principais:

1. JAVA / SERVIDOR
   Descobrir como o Aeternum cria, identifica, atualiza e remove
   seus objetos/sistemas no mundo.

2. RESOURCE PACK / CLIENTE
   Descobrir como o Resource Pack transforma os dados enviados pelo
   servidor em modelos/texturas personalizados.


====================================================================
[2] REGRA FUNDAMENTAL DO PROJETO
====================================================================

NÃO assumir a implementação.

Sempre separar:

CONFIRMED
= comportamento comprovado por código, Resource Pack ou teste.

OBSERVED
= comportamento observado no jogo/console, mas ainda sem
confirmação da implementação.

HYPOTHESIS
= explicação provável ainda não confirmada.

UNKNOWN
= ainda não sabemos.

Não transformar HYPOTHESIS em FACT sem evidência.


====================================================================
[3] AMBIENTE ATUAL
====================================================================

Minecraft:
Java Edition 26.2

Servidor:
Paper

Plugin investigado:
AeternumSeasons 4.5

Repositório de investigação:
mrserluiz/AeternumSeasons-CustomETHER

Resource Pack oficial:
resourcepacks/Aeternum-Foods-26.x

Package principal do plugin:
Kinkin.aeternum


====================================================================
[4] DESCOBERTA — CUSTOM MODEL DATA DE ITENS
====================================================================

STATUS:
CONFIRMED ✓

O Aeternum cria itens vanilla com componentes personalizados.

Exemplo Tomato:

ITEM:
minecraft:beetroot_seeds

CUSTOM_MODEL_DATA:
minecraft:custom_model_data
└── floats[0] = 2301.0

IDENTIDADE AETERNUM:
aeternumseasons:food_id = tomato


Exemplo Onion:

ITEM:
minecraft:wheat_seeds

CUSTOM_MODEL_DATA:
minecraft:custom_model_data
└── floats[0] = 2302.0

IDENTIDADE AETERNUM:
aeternumseasons:food_id = onion


IMPORTANTE:

Para a renderização do item, não precisamos de:

- lore
- custom_name
- food_id
- outros custom_data

O Resource Pack consegue utilizar somente:

ITEM BASE + CustomModelData


====================================================================
[5] SISTEMA DE ITEM MODEL — TESTE CONFIRMADO
====================================================================

STATUS:
CONFIRMED ✓

Foi criado um sistema próprio no EtherTexture.

Exemplo:

minecraft:beetroot_seeds
+
CustomModelData.floats[0] = 2301
↓
assets/minecraft/items/beetroot_seeds.json
↓
range_dispatch
↓
ether:item/tomato
↓
tomato.json
↓
tomato.png


Arquivo:

assets/minecraft/items/beetroot_seeds.json

Funcionamento confirmado:

2301 → Tomato
2302+ → modelo vanilla fallback


Mecanismo:

minecraft:range_dispatch

property:
minecraft:custom_model_data

index:
0


IMPORTANTE:

range_dispatch usa threshold.

Portanto:

threshold 2301

significa:

valor >= 2301

Para isolar 2301, foi utilizada uma segunda entrada em 2302
retornando ao modelo vanilla.


====================================================================
[6] MODELO 2D DO ITEM
====================================================================

STATUS:
CONFIRMED ✓

Modelo:

assets/ether/models/item/tomato.json

Conteúdo:

{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "ether:item/tomato"
  }
}


Textura:

assets/ether/textures/item/tomato.png


RESULTADO:

Tomato renderiza corretamente como item 2D.

IMPORTANTE:

Este sistema funciona sem:

- modificar Aeternum
- modificar o item
- plugin intermediário
- interceptação de eventos


====================================================================
[7] REUTILIZAÇÃO DE CUSTOM MODEL DATA
====================================================================

STATUS:
CONFIRMED / DESIGN DECISION ✓

CustomModelData NÃO precisa ser globalmente único.

O contexto relevante é:

ITEM BASE + CMD

Exemplo:

beetroot_seeds + 2301 → Tomato

wheat_seeds + 2302 → Onion

O mesmo CMD pode eventualmente ser reutilizado em outro item base.

Exemplo possível:

apple + 2301 → custom apple


O controle visual pertence aos respectivos:

assets/minecraft/items/<item>.json


====================================================================
[8] DESCOBERTA — PLANTAÇÃO NO MUNDO
====================================================================

STATUS:
CONFIRMED ✓

A plantação visual do Aeternum NÃO utiliza:

- ArmorStand
- ItemDisplay
- BlockDisplay

A observação em modo espectador não mostrou entidades visuais.

A análise do Resource Pack oficial confirmou que o sistema utiliza:

minecraft:tripwire


O arquivo importante é:

assets/minecraft/blockstates/tripwire.json


O Resource Pack possui variantes do tripwire que apontam para
modelos específicos de culturas.


====================================================================
[9] SISTEMA DE RENDERIZAÇÃO DAS PLANTAÇÕES
====================================================================

STATUS:
CONFIRMED ✓

Fluxo confirmado:

AETERNUM
↓
minecraft:tripwire
↓
Block States do tripwire
↓
assets/minecraft/blockstates/tripwire.json
↓
modelo da cultura
↓
textura


Exemplo confirmado:

tripwire state:
powered=true
disarmed=true
north=false
south=false
east=false
west=false

pode apontar para:

aeternum:block/crop/tomato_stage_0


Outro estado pode apontar para:

aeternum:block/crop/onion_stage_0


Portanto:

A PLANTA NÃO É UMA ENTITY.

É UM BLOCK STATE DO TRIPWIRE
RENDERIZADO PELO RESOURCE PACK.


====================================================================
[10] ESTÁGIOS DAS PLANTAÇÕES
====================================================================

STATUS:
CONFIRMED ✓

O Resource Pack possui modelos por estágio.

Exemplo Tomato:

aeternum:block/crop/tomato_stage_0
aeternum:block/crop/tomato_stage_1
aeternum:block/crop/tomato_stage_2
aeternum:block/crop/tomato_stage_3


Exemplo Onion:

aeternum:block/crop/onion_stage_0
aeternum:block/crop/onion_stage_1
aeternum:block/crop/onion_stage_2
aeternum:block/crop/onion_stage_3


Isso indica que o Block State do tripwire também funciona como
mecanismo de seleção do estágio visual.


====================================================================
[11] MODELO DAS PLANTAÇÕES
====================================================================

STATUS:
CONFIRMED ✓

Os modelos das plantações utilizam o conceito vanilla de:

minecraft:block/cross


Exemplo conceitual:

{
  "parent": "minecraft:block/cross",
  "ambientocclusion": false,
  "textures": {
    "cross": "aeternum:block/crop/tomato_stage_0"
  }
}


Portanto a planta é renderizada como geometria cruzada 2D,
sem necessidade de entidade 3D.


====================================================================
[12] JAVA — CÓDIGO JÁ IDENTIFICADO
====================================================================

PACOTE:

Kinkin.aeternum.farming


Classes identificadas:

CropGrowthService
SeasonalCropGrowthListener
SeasonalCropConfig


SeasonalCropGrowthListener trabalha diretamente com:

Block block = e.getBlock();

e:

e.getNewState().getType()

Também verifica:

e.getNewState().getBlockData() instanceof Ageable


CropGrowthService possui:

evaluate(Block b)

e começa avaliando:

Material m = b.getType();


CONCLUSÃO:

A lógica de crescimento do Aeternum trabalha diretamente com
Blocks / BlockData.

Ainda NÃO foi comprovado nesta investigação qual classe
é responsável por colocar o tripwire que representa visualmente
a plantação.


====================================================================
[13] IMPORTANTE — STRING / TRIPWIRE
====================================================================

OBSERVED:

No mundo, a plantação visual parece ser uma linha/string.

Ao usar PICK BLOCK / botão do meio sobre a plantação,
o jogador recebe:

minecraft:beetroot_seeds

Isso indica que a string visual não necessariamente contém
o item da cultura.

HIPÓTESE:

O Aeternum pode usar:

minecraft:tripwire

como marcador visual da plantação.

O plugin pode manter a identidade da cultura separadamente
da informação do bloco.

Possíveis mecanismos ainda NÃO CONFIRMADOS:

A)
posição X/Y/Z → CropData interno

B)
PersistentDataContainer

C)
estrutura interna do plugin

D)
algum sistema próprio de plantação

E)
outro mecanismo associado ao bloco


NÃO assumir nenhum deles sem código/teste.


====================================================================
[14] RESOURCE PACK OFICIAL
====================================================================

STATUS:
CONFIRMED ✓

Resource Pack oficial foi adicionado ao repositório:

resourcepacks/Aeternum-Foods-26.x


O pack contém:

assets/aeternum/
assets/minecraft/


Área especialmente importante:

assets/minecraft/blockstates/tripwire.json


Também existem:

assets/aeternum/models/block/crop/


e texturas correspondentes.


====================================================================
[15] DESCOBERTA CRÍTICA
====================================================================

O arquivo:

assets/minecraft/blockstates/tripwire.json

é atualmente a peça mais importante para entender a
renderização das plantações.

Ele contém estados específicos do tripwire que apontam para:

aeternum:block/crop/<crop>_stage_<n>


Portanto o Resource Pack já revelou:

COMO O CLIENTE RENDERIZA.


O próximo objetivo é descobrir:

COMO O SERVIDOR/AETERNUM PRODUZ ESSES ESTADOS.


====================================================================
[16] QUESTÃO EM ABERTO
====================================================================

QUESTION Q001:

Como o Aeternum coloca/configura o minecraft:tripwire
quando uma cultura é plantada?

Precisamos encontrar no Java:

- criação do tripwire
- alteração do BlockData
- alteração de:
  powered
  disarmed
  north
  south
  east
  west
  attached

- clique/interação com o tripwire
- remoção do tripwire
- associação entre posição e CropData
- associação entre CropData e food_id


====================================================================
[17] PRÓXIMA INVESTIGAÇÃO JAVA
====================================================================

CURRENT_TARGET:

Descobrir quem cria e controla o tripwire da plantação.

Pesquisar no código:

Material.STRING
Material.TRIPWIRE
TRIPWIRE
Tripwire
TripwireData
BlockData
setBlockData
setType
BlockPlaceEvent
BlockBreakEvent
PlayerInteractEvent
PlayerInteractEntityEvent
food_id
CropData
crop
plant
seed


Também investigar:

AeternumSeasonsPlugin.java

para descobrir quais listeners/serviços são registrados.


====================================================================
[18] ORDEM RECOMENDADA DE INVESTIGAÇÃO
====================================================================

1.
Encontrar referências a Material.TRIPWIRE / TRIPWIRE.

2.
Encontrar código que cria ou modifica TripwireData.

3.
Encontrar código que escuta interação com o tripwire.

4.
Encontrar como a posição do tripwire é associada à cultura.

5.
Encontrar como o estágio da cultura altera o BlockData.

6.
Comparar os estados gerados pelo Java com
assets/minecraft/blockstates/tripwire.json.

7.
Confirmar:

SERVER STATE
↓
RESOURCE PACK STATE
↓
MODEL
↓
TEXTURE


====================================================================
[19] OBJETIVO DO ETHERCRAFT
====================================================================

OBJETIVO FUTURO:

Reproduzir o sistema de plantação usando nossa própria arquitetura,
sem depender do Aeternum para a renderização.

Possível arquitetura:

EtherCraft
↓
plantação
↓
minecraft:tripwire
↓
BlockData controlado pelo nosso sistema
↓
EtherTexture
↓
blockstates/tripwire.json
↓
modelo próprio
↓
textura própria


IMPORTANTE:

Ainda NÃO implementar.

Primeiro compreender completamente o mecanismo Aeternum.


====================================================================
[20] O QUE JÁ FOI DESCARTADO
====================================================================

DESCARTADO:

ArmorStand como mecanismo principal da plantação.

ItemDisplay como mecanismo principal.

BlockDisplay como mecanismo principal.

CustomModelData como mecanismo principal da plantação no mundo.

Esses mecanismos não foram observados na plantação investigada.


====================================================================
[21] SEPARAÇÃO DOS SISTEMAS
====================================================================

ITEMS:

Aeternum item
↓
CustomModelData.floats[0]
↓
assets/minecraft/items/<base_item>.json
↓
ether:item/<model>
↓
texture


PLANTS:

Aeternum
↓
minecraft:tripwire
↓
Tripwire Block States
↓
assets/minecraft/blockstates/tripwire.json
↓
aeternum:block/crop/<crop>_stage_<n>
↓
texture


NÃO CONFUNDIR OS DOIS SISTEMAS.


====================================================================
[22] REGRAS PARA CONTINUAÇÃO EM NOVO CHAT
====================================================================

Ao iniciar uma nova conversa:

1.
Ler este arquivo primeiro.

2.
Não repetir investigações já marcadas como CONFIRMED.

3.
Consultar o GitHub para validar os arquivos atuais.

4.
Distinguir sempre:
   CONFIRMED
   OBSERVED
   HYPOTHESIS
   UNKNOWN

5.
Continuar a partir de CURRENT_TARGET.

6.
Não modificar Aeternum enquanto a arquitetura não estiver
completamente compreendida.

7.
Sempre sugerir o próximo passo após cada investigação.


====================================================================
[24] CURRENT STATE
====================================================================

CONFIRMED:

[✓] Minecraft Java 26.2

[✓] AeternumSeasons 4.5

[✓] CustomModelData moderno usa floats[0]

[✓] beetroot_seeds + CMD 2301 → Tomato

[✓] wheat_seeds + CMD 2302 → Onion

[✓] EtherTexture consegue substituir visual de item

[✓] Plantação não utiliza Display Entity

[✓] Plantação utiliza minecraft:tripwire

[✓] Resource Pack oficial possui blockstates/tripwire.json

[✓] tripwire states selecionam modelos das culturas

[✓] culturas possuem múltiplos estágios

[✓] modelos utilizam block/cross


UNKNOWN:

[?] Qual classe cria o tripwire?

[?] Como o tripwire recebe seus estados?

[?] Onde o Aeternum guarda a identidade da cultura?

[?] Como a posição é associada ao CropData?

[?] Como o estágio altera o BlockData?

[?] Qual evento detecta a interação com a plantação?


====================================================================
[25] CURRENT_TARGET
====================================================================

M001-AETERNUM-TRIPWIRE

OBJETIVO:

Encontrar no código Java o mecanismo responsável por:

plantar cultura
↓
criar/configurar tripwire
↓
associar cultura
↓
alterar estágio
↓
remover cultura


====================================================================
[26] NEXT STEP
====================================================================

Pesquisar no repositório:

TRIPWIRE
TripwireData
Material.STRING
Material.TRIPWIRE
setBlockData
BlockData
PlayerInteractEvent
BlockBreakEvent
CropData
food_id


Depois cruzar cada resultado com:

assets/minecraft/blockstates/tripwire.json


====================================================================
END OF MANUAL
====================================================================
