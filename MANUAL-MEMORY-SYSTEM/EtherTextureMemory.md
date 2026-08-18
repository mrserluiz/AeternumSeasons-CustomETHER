================================
UPDATES SITE:
Exemplo:
UPDATE > 00/00/00 - 00:00 - M000A
[conteúdo]
<END UPDATE>
=============================

UPDATE > 18/08/2026 - 18:48 - M002A

#PADRÃO DE CONTINUIDADE E ESTADO DA MEMÓRIA

## STATUS

MEMORY STRUCTURE / ESTABLISHED ✓

---

## STATUS

### CONFIRMED

- Minecraft Java 26.2 é o ambiente atual.
- `minecraft:custom_model_data` utiliza `floats[0]` nos casos investigados.
- CustomModelData pode ser utilizado como ponte entre o ItemStack
  original e uma representação visual própria.
- A adição do CustomModelData não impediu o funcionamento do item
  proveniente do Theosis.
- O EtherTexture pode atender itens provenientes de diferentes
  plugins.
- O Resource Pack é responsável pela renderização final.
- O EtherTexture deve permanecer independente da lógica dos plugins
  de origem.

---

### DISCOVERED

- AeternumSeasons fornece identidades próprias através de
  `minecraft:custom_data`.
- Exemplo:

  `aeternumseasons:food_id = tomato`

- Theosis fornece identidades próprias através de
  `minecraft:custom_data`.
- Exemplo:

  `theosis:gem_id = RUBY`

- Essas identidades podem ser utilizadas pelo EtherTexture para
  determinar qual CustomModelData deve ser aplicado.
- O Resource Pack pode organizar modelos e texturas em subpastas
  dentro do namespace `ether`.
- O sistema visual não precisa conhecer a lógica interna completa
  do plugin de origem para modificar sua representação visual.

---

### DISCARDED

Até o momento:

- Não foi adotada a substituição completa do ItemStack como método
  padrão de alteração visual.
- Não foi adotada a remoção ou reconstrução dos componentes originais
  do item.
- Não foi adotada uma arquitetura em que cada plugin possua seu
  próprio sistema visual isolado.
- `food_id`, `gem_id`, lore e demais componentes não são utilizados
  diretamente pelo Minecraft para renderização.
- A identidade do plugin não deve ser confundida com a identidade
  visual do Resource Pack.

---

### DECISIONS

1. O EtherTexture pertence ao EtherCraft.
2. AeternumSeasons, Theosis e futuros plugins são fontes de identidade.
3. O Resource Pack central pertence ao EtherCraft.
4. CustomModelData será utilizado como ponte visual.
5. A alteração padrão deve ser não destrutiva.
6. O sistema deve preservar a lógica e os componentes originais
   sempre que possível.
7. O Registry deverá centralizar a relação:

   `identidade → CustomModelData`

8. Os próximos updates deverão separar explicitamente:
   - CONFIRMED
   - DISCOVERED
   - DISCARDED
   - DECISIONS
   - CURRENT STATE
   - NEXT TARGET

---

## CURRENT STATE

O EtherTexture possui uma arquitetura inicial validada.

Fluxo atual:

Plugin
  ↓
Identidade
  ↓
Scanner
  ↓
CustomModelData
  ↓
Resource Pack
  ↓
Modelo
  ↓
Textura

O sistema já foi validado utilizando itens provenientes de
AeternumSeasons e Theosis.

O desenvolvimento atual ainda utiliza Skript para parte da lógica
de identificação e aplicação do CustomModelData.

A arquitetura definitiva do Registry e a implementação central
do sistema ainda não foram finalizadas.

---

## NEXT TARGET

1. Consolidar o Registry do EtherTexture.
2. Definir formalmente o formato de identificação dos itens.
3. Separar identificação, registro e aplicação de CMD.
4. Expandir o scanner para containers.
5. Catalogar os CMDs existentes.
6. Definir uma política permanente para faixas de CMD.
7. Continuar estruturando o Resource Pack central do EtherCraft.
8. Avaliar posteriormente uma implementação Java própria para o
   EtherTexture.

---

## CONTINUITY RULE

Cada novo update do EtherTexture deve registrar claramente:

CONFIRMED
    ↓
o que foi comprovado

DISCOVERED
    ↓
o que foi descoberto durante a investigação

DISCARDED
    ↓
o que foi testado ou considerado e não será utilizado

DECISIONS
    ↓
decisões arquiteturais tomadas

CURRENT STATE
    ↓
estado real do projeto naquele momento

NEXT TARGET
    ↓
próximo objetivo de investigação ou implementação

Esta estrutura existe para permitir que uma nova instância possa
retomar o projeto sem depender da memória da conversa anterior.

===================================================================
# ETHER TEXTURE — MEMORY SYSTEM

> Memória técnica permanente do sistema visual EtherTexture.
>
> Projeto: EtherCraft
> Sistema: EtherTexture
> Primeira memória: M001A
>
> Este arquivo é independente da investigação interna dos plugins
> que fornecem os itens. Plugins como AeternumSeasons e Theosis
> são fontes de identidade para o EtherTexture, não proprietários
> do sistema visual.

---

# M001A — ARQUITETURA BASE DO ETHERTEXTURE

## DATA

18/08/2026

## STATUS

ARCHITECTURE / FOUNDATION ✓

---

# 1. VISÃO DO PROJETO

O EtherTexture é um sistema visual do EtherCraft destinado a
controlar a representação gráfica dos itens no Minecraft Java.

O sistema NÃO pertence exclusivamente ao AeternumSeasons.

AeternumSeasons foi apenas um dos primeiros plugins utilizados
para investigar e validar o mecanismo.

Theosis foi posteriormente utilizado para validar que o mesmo
mecanismo também funciona com itens provenientes de outro plugin.

O objetivo final é permitir que o EtherCraft possua uma camada
visual própria e independente da lógica dos plugins que fornecem
os itens.

---

# 2. PRINCÍPIO FUNDAMENTAL

O EtherTexture não deve substituir a lógica do item.

O plugin de origem continua sendo responsável por:

- identidade do item;
- funcionamento;
- atributos;
- receitas;
- interações;
- lore;
- custom_name;
- custom_data;
- eventos;
- mecânicas próprias.

O EtherTexture é responsável somente pela representação visual.

PIPELINE:

Plugin de origem
    ↓
Identidade do item
    ↓
EtherTexture
    ↓
CustomModelData
    ↓
Minecraft Resource Pack
    ↓
Modelo
    ↓
Textura

---

# 3. CUSTOMMODEL DATA COMO PONTE VISUAL

O mecanismo principal utilizado pelo EtherTexture é:

minecraft:custom_model_data

No Minecraft Java 26.2, os casos investigados utilizam:

minecraft:custom_model_data
└── floats
    └── [0]

Exemplo:

2301.0

O EtherTexture utiliza esse valor como identificador visual.

---

# 4. EXEMPLO AETERNUMSEASONS

Item:

minecraft:beetroot_seeds

Identidade:

aeternumseasons:food_id = tomato

CustomModelData:

2301.0

Resultado:

minecraft:beetroot_seeds
+
CMD 2301
    ↓
Tomato

O item continua sendo:

minecraft:beetroot_seeds

Somente sua representação visual é modificada.

---

# 5. EXEMPLO THEOSIS

Item:

minecraft:echo_shard

Identidade:

theosis:gem_id = RUBY

CustomModelData:

2315.0

Resultado:

minecraft:echo_shard
+
CMD 2315
    ↓
Ruby

Foi confirmado em servidor real que a adição do
CustomModelData não impediu o funcionamento original do item
do Theosis.

STATUS:

CONFIRMED ✓

---

# 6. SEPARAÇÃO ENTRE IDENTIDADE E RENDERIZAÇÃO

Esta é uma decisão fundamental da arquitetura.

A identidade fornecida pelo plugin NÃO é diretamente utilizada
pelo Minecraft para renderização.

Exemplo:

Aeternum:

aeternumseasons:food_id = tomato

Theosis:

theosis:gem_id = RUBY

Essas identidades são interpretadas pelo EtherTexture.

O EtherTexture transforma:

IDENTIDADE
    ↓
CMD

O Minecraft então utiliza:

CMD
    ↓
MODELO

Portanto:

Plugin → EtherTexture → CMD → Resource Pack

---

# 7. REGISTRY

O EtherTexture deverá possuir um Registry central.

Responsabilidade:

mapear identidades conhecidas para CustomModelData.

Exemplo:

AeternumSeasons
└── food_id
    ├── tomato → 2301
    └── onion → 2302

Theosis
└── gem_id
    └── RUBY → 2315

Futuros plugins:

Plugin X
└── item_id
    └── ITEM_A → CMD

O scanner não deve possuir lógica específica para cada item.

Ele consulta o Registry.

---

# 8. ARQUITETURA DO SCANNER

O scanner recebe um ItemStack.

Fluxo:

ItemStack
    ↓
identificar identidade
    ↓
consultar Registry
    ↓
obter CMD
    ↓
verificar CMD atual
    ↓
aplicar somente se necessário
    ↓
retornar ItemStack

Proteção:

Se o item já possuir o CMD correto, nenhuma alteração deve
ser realizada.

Conceito:

if CMD atual == CMD registrado
    ↓
não alterar

---

# 9. ALTERAÇÃO DO ITEM

A operação visual é:

set custom model data floats of {_item} to {_cmd}

Essa operação:

- não troca o item base;
- não remove o custom_data;
- não remove lore;
- não altera custom_name;
- não altera a identidade original.

Ela somente adiciona/modifica:

minecraft:custom_model_data
└── floats[0]

---

# 10. RESOURCE PACK

O Resource Pack é a camada de renderização.

Estrutura base:

assets/
└── ether/
    ├── models/
    │   └── item/
    │
    └── textures/
        └── item/

O namespace utilizado pelo EtherTexture é:

ether

---

# 11. ORGANIZAÇÃO DO RESOURCE PACK

Subpastas são permitidas e recomendadas.

Estrutura:

assets/
└── ether/
    ├── models/
    │   └── item/
    │       ├── foods/
    │       ├── drinks/
    │       ├── gems/
    │       ├── weapons/
    │       ├── armor/
    │       ├── tools/
    │       ├── materials/
    │       ├── magic/
    │       └── miscellaneous/
    │
    └── textures/
        └── item/
            ├── foods/
            ├── drinks/
            ├── gems/
            ├── weapons/
            ├── armor/
            ├── tools/
            ├── materials/
            ├── magic/
            └── miscellaneous/

A organização física dos arquivos não altera o funcionamento do
CustomModelData.

---

# 12. MODELO 2D

Exemplo:

assets/ether/models/item/foods/tomato.json

Conteúdo:

{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "ether:item/foods/tomato"
  }
}

Textura:

assets/ether/textures/item/foods/tomato.png

Referência:

ether:item/foods/tomato

---

# 13. RANGE DISPATCH

O Resource Pack pode utilizar:

minecraft:range_dispatch

com:

property:
minecraft:custom_model_data

Isso permite que múltiplos valores de CMD selecionem diferentes
modelos.

Exemplo conceitual:

2301 → ether:item/foods/tomato
2302 → ether:item/foods/onion
2310 → ether:item/drinks/coffee
2311 → ether:item/drinks/herbal_tea
2315 → ether:item/gems/ruby

---

# 14. FAIXA ATUAL DE CMD

Faixa inicial adotada:

2300+

Registros conhecidos:

2301 → Tomato
2302 → Onion

2305 → Vegetable Bread
2306 → Meat Sandwich
2307 → Bread

2310 → Coffee
2311 → Herbal Tea
2312 → Energy Drink
2313 → Hot Chocolate
2314 → Honey Bottle visual

2315 → Ruby

Esta tabela deverá permanecer centralizada para evitar colisões.

---

# 15. CATEGORIAS DO ETHERCRAFT

O sistema não deve ser limitado aos itens de plugins.

Podemos possuir:

foods
drinks
gems
weapons
armor
tools
materials
quest_items
furniture
magic
miscellaneous

Itens próprios do EtherCraft também podem utilizar o sistema.

Exemplo:

minecraft:paper
+
CMD 2401
    ↓
Ether Quest Token

Não é necessário que exista um plugin externo fornecendo a
identidade do item.

---

# 16. RELAÇÃO COM PLUGINS EXTERNOS

O EtherTexture deve ser independente dos plugins de origem.

Exemplo:

AeternumSeasons
    ↓
fornece identidade

Theosis
    ↓
fornece identidade

Outro plugin
    ↓
fornece identidade

EtherTexture
    ↓
unifica a representação visual

Portanto:

AeternumSeasons ≠ EtherTexture

Theosis ≠ EtherTexture

EtherTexture = camada visual do EtherCraft

---

# 17. RESPONSABILIDADES

## Plugin de origem

Responsável pela lógica.

## EtherTexture

Responsável por:

- detectar identidades;
- consultar Registry;
- atribuir CustomModelData;
- manter a conversão visual;
- administrar a camada de apresentação.

## Resource Pack

Responsável por:

- interpretar CustomModelData;
- selecionar modelo;
- carregar textura;
- renderizar o item.

---

# 18. DESCOBERTA IMPORTANTE

O mesmo ItemStack pode continuar funcionalmente pertencendo ao
plugin de origem enquanto recebe uma representação visual
completamente diferente.

Exemplo:

echo_shard
+
theosis:gem_id = RUBY
+
CMD 2315

Continua sendo:

minecraft:echo_shard

mas visualmente pode representar:

Ruby Gem

Isso confirma que o EtherTexture pode funcionar como uma camada
não destrutiva de apresentação.

STATUS:

CONFIRMED ✓

---

# 19. PRINCÍPIO DE NÃO INTERFERÊNCIA

O EtherTexture deve evitar:

- substituir ItemStacks sem necessidade;
- remover componentes;
- reconstruir itens inteiros;
- alterar lore;
- alterar custom_name;
- alterar custom_data;
- alterar atributos;
- interferir diretamente nas mecânicas dos plugins.

Preferência:

ALTERAR SOMENTE

minecraft:custom_model_data

---

# 20. ESTADO ATUAL

CONFIRMADO:

✓ Minecraft Java 26.2
✓ CustomModelData floats[0]
✓ Aeternum Tomato
✓ Aeternum Onion
✓ Theosis Ruby
✓ leitura de identidade via custom NBT
✓ aplicação de CMD via Skript
✓ Resource Pack com modelos customizados
✓ texturas 2D
✓ range_dispatch
✓ subpastas no namespace ether
✓ múltiplos plugins podem alimentar o sistema
✓ arquitetura independente do plugin de origem

---

# 21. O QUE NÃO É OBJETIVO

O EtherTexture não pretende:

- substituir plugins existentes;
- reproduzir a lógica de plugins;
- controlar gameplay;
- substituir sistemas de inventário;
- substituir sistemas de agricultura;
- substituir sistemas de gemas;
- interpretar todas as mecânicas internas dos plugins.

Seu objetivo é:

VISUAL.

---

# 22. VISÃO DE LONGO PRAZO

O EtherTexture deverá se tornar a biblioteca visual central do
EtherCraft.

Em vez de cada plugin possuir obrigatoriamente sua própria
estrutura visual, o servidor poderá utilizar:

PLUGIN
    ↓
IDENTIDADE
    ↓
ETHERTEXTURE REGISTRY
    ↓
CUSTOM MODEL DATA
    ↓
RESOURCE PACK CENTRAL
    ↓
VISUAL DO ETHerCRAFT

Isso permite que diferentes sistemas coexistam dentro de uma
mesma linguagem visual.

---

# 23. PRINCÍPIO DE CONTINUIDADE

A investigação interna dos plugins deve continuar separada.

Exemplo:

AeternumSeasons:
investigar como o plugin cria e controla seus itens.

EtherTexture:
investigar como transformar esses itens em representação visual.

Não misturar:

"como o plugin funciona"

com:

"como o EtherCraft quer representar visualmente o resultado."

A investigação do plugin fornece conhecimento.

O EtherTexture utiliza esse conhecimento através de uma interface
visual independente.

---

# 24. CURRENT TARGET

M001A

Estabelecer a arquitetura base do EtherTexture como sistema
visual independente do EtherCraft.

---

# 25. NEXT TARGET

1. Consolidar Registry.
2. Catalogar todos os CMDs atuais.
3. Mapear AeternumSeasons.
4. Mapear Theosis.
5. Definir faixas de CMD.
6. Organizar Resource Pack por categorias.
7. Criar biblioteca visual central.
8. Expandir suporte para novos plugins.
9. Avaliar itens dentro de shulkers fechadas.
10. Otimizar scanner.
11. Avaliar futura implementação em Java para reduzir dependência
    de polling via Skript.

---

# 26. REGRA PRINCIPAL

O EtherTexture não pertence ao plugin que fornece o item.

O EtherTexture pertence ao EtherCraft.

Plugins externos são apenas fontes de identidade.

A camada visual pertence ao servidor.

PIPELINE OFICIAL:

PLUGIN
    ↓
IDENTIDADE
    ↓
ETHERTEXTURE
    ↓
CUSTOMMODEL DATA
    ↓
RESOURCE PACK
    ↓
MODELO
    ↓
TEXTURA


STATUS GERAL:

FOUNDATION ESTABLISHED ✓

EtherTextureMemory.md
│
├── HISTÓRICO / IDEIA ORIGINAL
│   └── Start ideia [PROJETO ETHERCRAFT — ETAPA: ETHERTEXTURE]
│
└── MEMÓRIA TÉCNICA
    └── M001A — Arquitetura Base do EtherTexture

====================================================================
Start ideia [PROJETO ETHERCRAFT — ETAPA: ETHERTEXTURE]
====================================================================

STATUS:
IMPLEMENTATION / CONFIRMED ✓

OBJETIVO:
Criar uma camada visual independente para itens provenientes de
plugins existentes, utilizando exclusivamente o
minecraft:custom_model_data do ItemStack.

A lógica de gameplay, identidade, componentes e funcionamento
original dos itens não deve ser substituída.

O EtherTexture somente acrescenta/altera a camada visual através
do CustomModelData.


==================================================
[1] AMBIENTE CONFIRMADO
==================================================

Minecraft Java:
26.2

IMPORTANTE:

O projeto utiliza a nomenclatura moderna de versões:

26.2
│
├── 26 = ano 2026
└── 2  = segunda atualização do ano

Não utilizar a nomenclatura antiga 1.20.x / 1.21.x ao descrever
o ambiente atual deste projeto.


==================================================
[2] OBJETIVO DO ETHERTEXTURE
==================================================

Fluxo desejado:

Item original
    ↓
identidade fornecida pelo plugin
    ↓
EtherTexture identifica o item
    ↓
CustomModelData
    ↓
Minecraft Resource Pack
    ↓
modelo/textura customizada


O EtherTexture NÃO deve:

- substituir o item base;
- remover componentes;
- alterar lore;
- alterar custom_name;
- alterar custom_data;
- alterar a lógica do plugin proprietário;
- interceptar eventos de gameplay desnecessariamente.

Sua responsabilidade é visual.


==================================================
[3] DESCOBERTA — CUSTOMMODEL DATA
==================================================

STATUS:
CONFIRMED ✓

O Minecraft Java 26.2 utiliza:

minecraft:custom_model_data

Para os itens analisados pelo AeternumSeasons, o valor relevante
foi encontrado em:

minecraft:custom_model_data
└── floats
    └── [0] = valor

Exemplo:

minecraft:beetroot_seeds
+
CustomModelData floats[0] = 2301.0
+
aeternumseasons:food_id = tomato


O food_id não é utilizado pelo Resource Pack para identificar
a textura diretamente.

A identidade visual é determinada pelo:

CustomModelData


==================================================
[4] PRIMEIRO TESTE — AETERNUM TOMATO
==================================================

STATUS:
CONFIRMED ✓

Item base:

minecraft:beetroot_seeds

CustomModelData:

2301.0

Identidade Aeternum:

aeternumseasons:food_id = tomato


Resultado:

2301
    ↓
Tomato


Foi confirmado que o mesmo item base pode apresentar uma
representação visual diferente através do CustomModelData.


==================================================
[5] AETERNUM — SEGUNDO ITEM
==================================================

STATUS:
CONFIRMED ✓

Item:

minecraft:wheat_seeds

CustomModelData:

2302.0

Identidade:

aeternumseasons:food_id = onion


Resultado:

2302
    ↓
Onion


O ItemStack continua sendo:

minecraft:wheat_seeds


A alteração visual não exige substituição do item base.


==================================================
[6] RESOURCE PACK — ESTRUTURA
==================================================

STATUS:
CONFIRMED ✓

Estrutura base:

EtherTexture/
└── pack.mcmeta
└── pack.png
└── assets/
    └── ether/
        ├── models/
        │   └── item/
        │
        └── textures/
            └── item/


Foi confirmado que subpastas podem ser utilizadas normalmente
dentro do namespace ether.


Exemplo recomendado:

assets/
└── ether/
    ├── models/
    │   └── item/
    │       ├── foods/
    │       │   └── tomato.json
    │       │
    │       ├── drinks/
    │       │   ├── coffee.json
    │       │   └── herbal_tea.json
    │       │
    │       └── gems/
    │           └── ruby.json
    │
    └── textures/
        └── item/
            ├── foods/
            │   └── tomato.png
            │
            ├── drinks/
            │   ├── coffee.png
            │   └── herbal_tea.png
            │
            └── gems/
                └── ruby.png


==================================================
[7] CAMINHO DE TEXTURA
==================================================

STATUS:
CONFIRMED ✓

Arquivo:

assets/ether/textures/item/foods/tomato.png

Referência dentro do modelo:

ether:item/foods/tomato


A extensão .png não é incluída na referência.


==================================================
[8] MODELO 2D
==================================================

STATUS:
CONFIRMED ✓

Para uma textura 2D simples:

{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "ether:item/foods/tomato"
  }
}


O modelo utiliza a textura localizada em:

assets/ether/textures/item/foods/tomato.png


==================================================
[9] MODELO BASE DO ITEM
==================================================

STATUS:
CONFIRMED ✓

O sistema não altera o item base.

Exemplo:

minecraft:beetroot_seeds
+
CMD 2301
        ↓
modelo Tomato


Portanto:

Item lógico:
minecraft:beetroot_seeds

Representação visual:
Tomato


==================================================
[10] RESOURCE PACK — RANGE DISPATCH
==================================================

STATUS:
CONFIRMED ✓

Para múltiplos modelos, o Resource Pack pode utilizar:

minecraft:range_dispatch

com:

property:
minecraft:custom_model_data


Exemplo conceitual:

2305
    ↓
ether:item/vegetable_bread

2306
    ↓
ether:item/meat_sandwich

2307
    ↓
ether:item/vanilla/bread


Também foram identificados:

2310 → coffee
2311 → herbal_tea
2312 → energy_drink
2313 → hot_chocolate
2314 → vanilla/honey_bottle


O Resource Pack é responsável pela associação:

CustomModelData
    ↓
modelo


==================================================
[11] THEOSIS — TESTE DE COMPATIBILIDADE
==================================================

STATUS:
CONFIRMED ✓

Foi testado um item do plugin Theosis:

minecraft:echo_shard

Identidade:

minecraft:custom_data
└── PublicBukkitValues
    └── theosis:gem_id = "RUBY"


Foi adicionado:

minecraft:custom_model_data
└── floats[0] = 2315.0


RESULTADO:

CONFIRMED ✓

O item continuou funcionando normalmente no Theosis.

Conclusão:

Adicionar CustomModelData ao ItemStack não destruiu a identidade
ou funcionalidade conhecida da Ruby Gem.


==================================================
[12] SKBEE — LEITURA DO CUSTOM NBT
==================================================

STATUS:
CONFIRMED ✓

Ambiente utilizado:

Skript 2.15.2
SkBee 3.24.0


A leitura direta do NBT completo não deve ser utilizada para
acessar o valor do Theosis.

Foi confirmado que a forma correta neste ambiente é:

custom nbt of {_item}


E a leitura do identificador:

string tag "PublicBukkitValues;theosis:gem_id" of {_custom}


Resultado confirmado:

Theosis gem_id: RUBY


Portanto:

custom nbt
    ↓
PublicBukkitValues
    ↓
theosis:gem_id
    ↓
RUBY


==================================================
[13] SKRIPT — ALTERAÇÃO DO CMD
==================================================

STATUS:
CONFIRMED ✓

A alteração funcional é realizada através de:

set custom model data floats of {_item} to {_cmd}


Exemplo:

set custom model data floats of {_item} to 2315


Isso adiciona/modifica:

minecraft:custom_model_data
└── floats[0] = 2315.0


O ItemStack original permanece sendo utilizado.


==================================================
[14] PRIMEIRO SCANNER FUNCIONAL
==================================================

STATUS:
CONFIRMED ✓

Foi criado um scanner para Ruby Gem.

Fluxo:

echo_shard
    ↓
custom nbt
    ↓
PublicBukkitValues;theosis:gem_id
    ↓
RUBY
    ↓
CMD 2315


O scanner percorre:

- inventários dos jogadores;
- containers abertos.


Intervalo utilizado durante o teste:

2 segundos


A conversão foi confirmada em servidor real.


==================================================
[15] ARQUITETURA ETHERTEXTURE 1.5
==================================================

STATUS:
IMPLEMENTED / CONFIRMED ✓

A implementação foi expandida de um scanner específico de Ruby
para uma arquitetura de Registry.

Conceito:

PLUGIN
    ↓
NBT KEY
    ↓
IDENTITY VALUE
    ↓
CUSTOM MODEL DATA
    ↓
RESOURCE PACK


Exemplo:

Theosis
└── theosis:gem_id
    └── RUBY
        └── 2315


Outro item:

Theosis
└── theosis:gem_id
    └── SAPPHIRE
        └── 2316


Outro plugin poderá futuramente utilizar:

OutroPlugin
└── item_id
    └── MAGIC_ITEM
        └── 2320


O scanner permanece único.


==================================================
[16] PRINCÍPIO FUNDAMENTAL DO REGISTRY
==================================================

STATUS:
ARCHITECTURE DECISION ✓

O scanner não deve conhecer individualmente cada item.

Ele deve executar:

1. receber ItemStack;
2. identificar registros conhecidos;
3. encontrar o CMD associado;
4. verificar se o CMD já está correto;
5. aplicar somente o CustomModelData;
6. devolver o ItemStack.


Portanto:

Scanner
    ↓
Registry
    ↓
CMD


Não:

Scanner
    ↓
if Ruby
if Sapphire
if Coffee
if Tomato
if ItemA
if ItemB
...


A lógica visual deve ser centralizada no Registry.


==================================================
[17] PROTEÇÃO CONTRA REESCRITA DESNECESSÁRIA
==================================================

STATUS:
CONFIRMED ✓

Antes de alterar o item:

if custom model data floats of {_item} is {_cmd}:
    return {_item}


Isso evita reescrever continuamente o ItemStack que já possui
o CustomModelData correto.


==================================================
[18] LIMITES ATUAIS DO SCANNER
==================================================

STATUS:
PARTIALLY IMPLEMENTED

Atualmente:

✓ Inventários de jogadores
✓ Containers abertos
✓ Baús
✓ Barris
✓ Hoppers
✓ Dispensers
✓ Droppers
✓ Shulkers abertas


Ainda não implementado como etapa independente:

Shulker fechada contendo itens internos.


Motivo:

Uma shulker fechada é um ItemStack cujo conteúdo está armazenado
dentro de seus próprios componentes/NBT.

Ela não deve ser tratada simplesmente como um inventário aberto.


Essa etapa deverá ser implementada separadamente.


==================================================
[19] ORGANIZAÇÃO DO RESOURCE PACK
==================================================

STATUS:
ARCHITECTURE DECISION ✓

Organização recomendada:

ether/
├── models/
│   └── item/
│       ├── foods/
│       ├── drinks/
│       ├── gems/
│       └── ...
│
└── textures/
    └── item/
        ├── foods/
        ├── drinks/
        ├── gems/
        └── ...


Objetivo:

separar organização lógica do projeto da numeração dos CMDs.


O CMD identifica o modelo.

O caminho do modelo identifica o recurso visual.


==================================================
[20] FAIXA DE CUSTOMMODELDATA
==================================================

STATUS:
DESIGN DECISION

Faixa atualmente utilizada pelo projeto:

2300+

Exemplos:

2301 → Tomato
2302 → Onion

2305 → Vegetable Bread
2306 → Meat Sandwich
2307 → Bread

2310 → Coffee
2311 → Herbal Tea
2312 → Energy Drink
2313 → Hot Chocolate
2314 → Vanilla Honey Bottle

2315 → Ruby


A numeração deve ser mantida centralizada para evitar colisões.


==================================================
[21] ARQUITETURA VISUAL FINAL
==================================================

ETHERTEXTURE

                    ┌─────────────────────┐
                    │      ItemStack      │
                    └──────────┬──────────┘
                               │
                               ▼
                       Plugin Identity
                               │
                               ▼
                         EtherTexture
                               │
                               ▼
                            Registry
                               │
                               ▼
                         CustomModelData
                               │
                               ▼
                     Minecraft Resource Pack
                               │
                               ▼
                         Model JSON
                               │
                               ▼
                          Texture PNG


Importante:

O plugin original continua responsável pelo item.

O EtherTexture é somente a camada de apresentação.


==================================================
[22] OBJETIVO FUTURO
==================================================

O sistema deverá suportar itens provenientes de múltiplos plugins:

AeternumSeasons
Theosis
outros plugins
itens customizados próprios do EtherCraft


Exemplo:

AeternumSeasons
└── food_id
    ├── tomato → 2301
    └── onion → 2302

Theosis
└── gem_id
    ├── RUBY → 2315
    └── ...


Futuros plugins:

Plugin X
└── item_id
    └── VALUE → CMD


O Resource Pack continuará independente da lógica de gameplay.


==================================================
[23] HIPÓTESES / DESCOBERTAS IMPORTANTES
==================================================

H011

CLAIM:
CustomModelData pode ser adicionado a itens criados por plugins
sem necessariamente interferir em sua identidade funcional.

STATUS:
CONFIRMED ✓

TESTE:
Theosis Ruby Gem


H012

CLAIM:
O valor visual pode ser controlado exclusivamente através do
CustomModelData floats[0].

STATUS:
CONFIRMED ✓


H013

CLAIM:
O plugin de origem não precisa participar da renderização.

STATUS:
CONFIRMED ✓

A renderização é realizada pelo cliente através do Resource Pack.


H014

CLAIM:
Um scanner genérico pode aplicar CMDs diferentes de acordo com
identidades NBT fornecidas por diferentes plugins.

STATUS:
CONFIRMED / ARCHITECTURE ✓


==================================================
[24] PRÓXIMO TARGET
==================================================

CURRENT_TARGET:
M003I — EtherTexture Registry


NEXT_TARGET:

1. Consolidar Registry de todos os itens atuais.
2. Mapear AeternumSeasons.
3. Mapear Theosis.
4. Definir faixa oficial de CMDs.
5. Organizar modelos por categorias.
6. Organizar texturas por categorias.
7. Implementar mais itens.
8. Avaliar suporte a shulkers fechadas.
9. Avaliar otimização do scanner.
10. Criar documentação própria do EtherTexture.


==================================================
[25] REGRA DE CONTINUIDADE
==================================================

Ao continuar esta investigação:

NÃO modificar desnecessariamente os plugins de origem.

NÃO substituir ItemStacks sem necessidade.

NÃO remover componentes existentes.

NÃO depender de lore ou custom_name para identificar itens.

NÃO usar o food_id ou gem_id diretamente como mecanismo de
renderização.

A identidade do plugin serve para o Registry.

O CustomModelData serve como ponte visual.

O Resource Pack serve como camada de renderização.


PIPELINE OFICIAL:

Plugin
    ↓
Item Identity
    ↓
EtherTexture Registry
    ↓
CustomModelData floats[0]
    ↓
Resource Pack
    ↓
Modelo
    ↓
Textura


STATUS GERAL M003I:

✓ CustomModelData floats confirmado
✓ Aeternum Tomato confirmado
✓ Aeternum Onion confirmado
✓ Theosis Ruby confirmado
✓ Leitura custom nbt confirmada
✓ Alteração de CMD via Skript confirmada
✓ Scanner de inventário confirmado
✓ Scanner de container aberto confirmado
✓ Registry architecture definida
✓ Subpastas do Resource Pack confirmadas

STATUS:
IMPLEMENTATION / EXPANSION
