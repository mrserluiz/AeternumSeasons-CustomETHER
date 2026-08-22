================================
UPDATES SITE:
Exemplo:
UPDATE > 00/00/00 - 00:00 - M000A
[conteúdo]
<END UPDATE>
=============================

# ============================================================
# ETHERCRAFT — ETHER TEXTURE MEMORY
# UPDATE — BEDROCK / GEYSER / RAINBOW
# DATA: 2026-08-22
# ============================================================

## 1. OBJETIVO ATUAL

Projeto EtherTexture responsável por criar uma camada visual customizada
para o servidor EtherCraft, utilizando:

- Java Resource Pack
- Bedrock Resource Pack
- GeyserMC
- Floodgate
- Rainbow
- Geyser Custom Item Mappings
- Skript / SkBee

Objetivo:

Permitir que itens vanilla utilizados por plugins possam receber:

- Ícones personalizados
- Texturas personalizadas
- Modelos 3D
- Diferentes estados visuais
- Custom Model Data (CMD)

sem alterar a mecânica original dos plugins.

A prioridade atual é garantir que os itens funcionem simultaneamente para:

JAVA + BEDROCK.

---

# 2. SISTEMA CMD

Foi estabelecido um registro central de Custom Model Data:

ETHER TEXTURE CMD REGISTRY : https://github.com/mrserluiz/AeternumSeasons-CustomETHER/blob/main/MANUAL-MEMORY-SYSTEM/ETHERTEXTURE_CMD_REGISTRY

Faixa atualmente utilizada para ícones de menus:

2300–2399

Exemplo:

CMD 2330 = Horse Up / ícone de cavalo

A intenção é manter uma organização centralizada para evitar conflitos
futuros entre itens e sistemas.

---

# 3. MENU INSPECTOR

Foi desenvolvido:

EtherTexture_Menu_Inspector.sk

Versões de desenvolvimento:

- v0
- v0.0.9
- v0.1.2
- v0.1.3
- v0.1.4

Objetivo:

Detectar menus/inventários criados dinamicamente por plugins e identificar
os itens presentes nos slots.

O sistema conseguiu detectar corretamente menus físicos e menus virtuais
criados por plugins.

Exemplo detectado:

MENU: Horse Stats

Slots:

10 - EXPERIENCE_BOTTLE
11 - SUGAR
12 - APPLE
13 - RABBIT_FOOT
14 - LIME_DYE
16 - FILLED_MAP
17 - FEATHER

Foi confirmado que menus de plugins podem ser inventários virtuais,
portanto não devemos depender de um bloco físico ou localização.

---

# 4. DESCOBERTA IMPORTANTE — MENUS DINÂMICOS

O menu do PetHorse/Horse Stats é recriado pelo plugin quando o comando
é executado.

Portanto:

O EtherTexture não deve modificar um inventário físico apenas uma vez.

A transformação deve acontecer toda vez que o menu for criado/aberto.

O sistema deve permanecer genérico para futuros plugins.

Arquitetura desejada:

PLUGIN
  ↓
CRIA MENU
  ↓
ETHER TEXTURE DETECTA
  ↓
IDENTIFICA ITEM
  ↓
VERIFICA CMD
  ↓
SE NECESSÁRIO APLICA CMD
  ↓
ITEM VISUAL PERSONALIZADO

A mecânica original do plugin deve permanecer intacta.

---

# 5. TESTE CMD 2330

Foi criado um item de teste:

minecraft:experience_bottle

com:

minecraft:custom_model_data = 2330

Comando:

/minecraft:give MrSerLuiz minecraft:experience_bottle[minecraft:custom_model_data={floats:[2330.0f]}]

Resultado:

JAVA:
OK

No Java o item aparece corretamente com o modelo/ícone:

horseUp

Portanto a cadeia Java está funcionando.

---

# 6. HORSE UP — JAVA

O modelo Java utilizado pelo CMD 2330 aponta para:

ether:item/icons/horseUp

A textura Java correspondente:

horseUp.png

A cadeia Java confirmada:

EXPERIENCE_BOTTLE
  ↓
CMD 2330
  ↓
range_dispatch
  ↓
ether:item/icons/horseUp
  ↓
horseUp.json
  ↓
horseUp.png

Resultado:

JAVA = FUNCIONANDO

---

# 7. RAINBOW

Rainbow está sendo utilizado para gerar automaticamente:

- Bedrock Resource Pack
- Custom Item Mappings
- Estruturas necessárias para Geyser

Rainbow já demonstrou funcionar corretamente para diversos itens
customizados do servidor.

Inclusive já existem itens com:

- Ícones personalizados
- Modelos 3D
- Custom Model Data
- Mappings Bedrock

funcionando para jogadores Java e Bedrock.

Portanto Rainbow é atualmente a referência para a arquitetura Bedrock.

---

# 8. BEDROCK RESOURCE PACK

Diretório atual:

resourcepacks/pack

IMPORTANTE:

O pacote original gerado pelo Rainbow foi restaurado.

Foi anteriormente renomeado para:

EtherTexture-Bedrock

e seu manifest foi alterado.

Depois disso ocorreram problemas de visualização.

Para eliminar a possibilidade de problemas relacionados a:

- manifest
- UUID
- version
- cache do Bedrock
- identidade do resource pack

o pacote foi restaurado ao nome/estrutura original:

pack

O próximo teste deve utilizar novamente o pacote original do Rainbow.

---

# 9. CUSTOM MAPPINGS

Diretório:

resourcepacks/custom_mappings

Arquivo principal:

geyser_item_mappings.json

Formato:

format_version: 2

O Rainbow gerou corretamente um mapping para:

minecraft:experience_bottle
CMD 2330

Mapping atual esperado:

{
  "bedrock_identifier": "ether:item/icons/horseUp",
  "bedrock_options": {
    "icon": "ether.item_icons_horseUp"
  },
  "custom_model_data": 2330,
  "type": "legacy"
}

Portanto:

O mapping EXISTE.

Não é correto afirmar que o Rainbow simplesmente não gerou
o mapping do EXPERIENCE_BOTTLE.

---

# 10. COMPARAÇÃO COM ITEM FUNCIONAL

Itens Rainbow que já funcionam utilizam arquitetura semelhante.

Exemplo:

minecraft:rabbit_stew
CMD 2307

Mapping:

{
  "bedrock_identifier": "ether:item/beef_rice_stew",
  "bedrock_options": {
    "icon": "ether.item_beef_rice_stew"
  },
  "custom_model_data": 2307,
  "type": "legacy"
}

Outro exemplo:

minecraft:bread
CMD 2306

Também utiliza:

type: legacy

Portanto:

EXPERIENCE_BOTTLE + CMD 2330

não deveria ser automaticamente considerado incompatível apenas por ser
EXPERIENCE_BOTTLE.

---

# 11. HORSE UP — BEDROCK

O Rainbow criou referência:

bedrock_identifier:

ether:item/icons/horseUp

icon:

ether.item_icons_horseUp

A textura correspondente está registrada na estrutura Bedrock gerada pelo
Rainbow.

O PNG horseUp.png existe e foi validado como textura 16x16.

Portanto a textura não aparenta estar corrompida.

---

# 12. TESTE REALIZADO — IDENTIFIER ALTERADO

Foi realizado um Teste B.

Mapping original:

ether:item/icons/horseUp

Foi temporariamente alterado para:

ether:item/horseUp

Resultado:

O item desapareceu/não foi renderizado corretamente.

Isso demonstrou que não podemos simplesmente inventar um novo
bedrock_identifier sem criar toda a estrutura Bedrock correspondente.

Conclusão:

bedrock_identifier NÃO é simplesmente o caminho do PNG.

Ele representa uma identidade/item Bedrock que precisa estar corretamente
registrada na arquitetura do pack.

O mapping original:

ether:item/icons/horseUp

deve ser restaurado.

---

# 13. ITEM_TEXTURE.JSON

Durante a investigação foi levantada a hipótese de utilização de:

item_texture.json

Porém foi confirmado que o usuário NÃO possui esse arquivo no estado
atual do pacote.

Portanto:

NÃO criar item_texture.json arbitrariamente.

Devemos respeitar a estrutura real gerada pelo Rainbow.

A estrutura Rainbow existente é a referência.

---

# 14. PROBLEMA ATUAL

Estado confirmado:

JAVA:

EXPERIENCE_BOTTLE + CMD 2330
        ↓
HORSE UP
        ↓
FUNCIONANDO

BEDROCK:

EXPERIENCE_BOTTLE + CMD 2330
        ↓
EXPERIENCE BOTTLE VANILLA
        ↓
NÃO FUNCIONANDO

Mesmo existindo:

- mapping CMD 2330
- bedrock_identifier
- icon
- textura
- pack Rainbow

---

# 15. HIPÓTESES ATUAIS

Hipótese A:
O .RAR utilizado pelo servidor não corresponde exatamente aos arquivos
atuais do GitHub.

Hipótese B:
O Bedrock está utilizando uma versão antiga/cache do resource pack.

Hipótese C:
O manifest foi alterado anteriormente e o Bedrock/Geyser ficou com
identidade/cache diferente.

Hipótese D:
O mapping legacy para EXPERIENCE_BOTTLE + CMD 2330 está sendo ignorado
ou não selecionado pelo Geyser.

Hipótese E:
Existe alguma diferença estrutural entre a forma como Rainbow cria os
itens funcionais e o caso EXPERIENCE_BOTTLE.

Hipótese F:
O bedrock_identifier:

ether:item/icons/horseUp

possui uma definição/estrutura Bedrock que ainda precisa ser identificada
e comparada com um item Rainbow funcional.

---

# 16. ESTADO DO MANIFEST

O pacote Bedrock havia sido renomeado/modificado para:

EtherTexture-Bedrock

Depois disso surgiram problemas.

Foi decidido restaurar o pacote para:

pack

que corresponde à estrutura original gerada pelo Rainbow.

Objetivo:

Eliminar como variável:

- nome do pack
- UUID
- version
- cache
- identidade do resource pack

O próximo teste deve utilizar o pacote original do Rainbow.

---

# 17. REGRA PARA PRÓXIMOS TESTES

NÃO alterar simultaneamente:

- Java resource pack
- Skript
- CMD
- Geyser mapping
- Bedrock pack
- manifest

Cada teste deve alterar uma única variável.

Isso é necessário para identificar exatamente onde está a falha.

---

# 18. PRÓXIMO TESTE

Primeiro:

Restaurar/recompactar o pack original do Rainbow.

Garantir que o servidor está usando o mesmo .RAR que está sendo analisado.

Estrutura:

GitHub
  ↓
resourcepacks/pack
  ↓
RAR
  ↓
SERVIDOR
  ↓
GEYSER
  ↓
BEDROCK

Depois testar:

/minecraft:give MrSerLuiz minecraft:experience_bottle[minecraft:custom_model_data={floats:[2330.0f]}]

Resultado esperado:

BEDROCK = horseUp

Se funcionar:

Problema = manifest/cache/pack utilizado pelo servidor.

Se continuar:

BEDROCK = experience bottle

Então:

manifest/cache será praticamente descartado.

A investigação continuará especificamente no:

experience_bottle
+
CMD 2330
+
geyser_item_mappings.json
+
Bedrock identifier
+
estrutura interna gerada pelo Rainbow.

---

# 19. ARQUITETURA FUTURA DO ETHER TEXTURE

Objetivo final:

PLUGIN MENU
    ↓
ITEM VANILLA
    ↓
ETHER MENU INSPECTOR
    ↓
IDENTIFICA ITEM
    ↓
CONSULTA CMD REGISTRY
    ↓
APLICA CMD
    ↓
JAVA RESOURCE PACK
    ↓
MODELO/ÍCONE JAVA

E paralelamente:

ITEM + CMD
    ↓
GEYSER
    ↓
CUSTOM MAPPING
    ↓
BEDROCK RESOURCE PACK
    ↓
ÍCONE / MODELO / TEXTURA BEDROCK

O sistema deverá funcionar genericamente para futuros menus de plugins.

---

# 20. FUTURA INVESTIGAÇÃO — AETERNUM FOODS BEDROCK

Foi separado para uma investigação posterior:

resourcepacks/Aeternum-Foods-Bedrock

Objetivo futuro:

Descobrir como o pacote aplica:

- texturas de blocos
- modelos de blocos
- blocos colocados no mundo
- representação Bedrock de blocos customizados

Esta investigação NÃO deve interferir no diagnóstico atual do HorseUp.

Prioridade atual:

EXPERIENCE_BOTTLE + CMD 2330 → HORSE UP → BEDROCK

---

# 21. ESTADO ATUAL RESUMIDO

JAVA:
✅ CMD 2330
✅ HorseUp
✅ textura
✅ modelo
✅ item funcionando

MENU INSPECTOR:
✅ detecta menus
✅ detecta itens
✅ detecta slots
✅ aplica CMD
✅ arquitetura dinâmica identificada

RAINBOW:
✅ gera Bedrock pack
✅ gera custom mappings
✅ outros itens funcionando
✅ modelos 3D funcionando

GEYSER:
✅ ponte Java → Bedrock funcionando para outros itens
✅ modelos 3D já comprovadamente funcionando
⚠️ EXPERIENCE_BOTTLE + CMD 2330 ainda não

HORSE UP BEDROCK:
✅ PNG existente
✅ referência de textura existente
✅ mapping existente
⚠️ Bedrock ainda mostra EXPERIENCE_BOTTLE

MANIFEST:
⚠️ foi alterado anteriormente
✅ pacote restaurado para "pack"
⚠️ próximo teste deve usar o pack original/recompactado

---

# 22. CONCLUSÃO

O problema do HorseUp NÃO está mais sendo tratado como um simples
problema de textura.

A investigação comprovou:

1. O CMD 2330 funciona no Java.
2. O modelo HorseUp funciona no Java.
3. Rainbow gerou mapping para EXPERIENCE_BOTTLE + CMD 2330.
4. Outros mappings legacy funcionam no Bedrock.
5. A textura HorseUp existe.
6. Alterar arbitrariamente o bedrock_identifier faz o item desaparecer.
7. Portanto bedrock_identifier depende da estrutura interna do Bedrock pack.
8. O pacote Rainbow original foi restaurado para eliminar problemas de
manifest/cache.
9. O próximo teste deve usar exatamente o RAR que o servidor está
carregando.
10. Se o problema persistir, a investigação deverá comparar diretamente
a estrutura completa de um item Rainbow funcional com:
EXPERIENCE_BOTTLE + CMD 2330.

PRÓXIMO ALVO:

EXPERIENCE_BOTTLE
CMD 2330
        ↓
Geyser Mapping
        ↓
ether:item/icons/horseUp
        ↓
Bedrock Resource Pack
        ↓
HORSE UP

# ============================================================
# FIM DO UPDATE
# ============================================================

UPDATE > 20/08/2026 - 16:02 - M0006A
# ============================================================
# ETHER TEXTURE MEMORY — UPDATE
# VERSÃO DO PROJETO: EtherTexture / v0.1.x
# ============================================================

## [UPDATE] INVESTIGAÇÃO BEDROCK — HORSEUP / CMD 2330

### CONTEXTO

O sistema EtherTexture utiliza:

- Java Resource Pack `EtherTexture`
- Bedrock Resource Pack gerado pelo Rainbow
- GeyserMC como ponte Java → Bedrock
- Rainbow para geração dos mappings e conversão do Resource Pack
- Pasta `custom_mappings` contendo os mappings utilizados pelo Geyser
- CMD Registry centralizado no projeto

O objetivo atual é fazer com que ícones/modelos personalizados criados para menus Java também sejam visualizados corretamente pelos jogadores Bedrock.

---

# 1. HORSEUP — ESTADO JAVA

O primeiro ícone de teste foi:

```text
horseUp

UPDATE > 2026-08-19 - 22:00 - M005A

# UPDATE — EtherTexture Menu Inspector
## Marco: v0.1.4 — CMD Injection comprovada

---

# 1. RESUMO

O sistema EtherTexture avançou de um simples scanner de menus para um sistema capaz de **interceptar uma nova instância de inventário criada por um plugin e modificar os ItemStacks daquela GUI em tempo real**.

O teste foi realizado com o menu:

    PetHorse
    Menu: Horse Stats

O teste utilizou o item:

    EXPERIENCE_BOTTLE

localizado no:

    Slot 10

O CMD utilizado no teste foi:

    2330

O resultado foi confirmado com sucesso.

---

# 2. DESCOBERTA ARQUITETURAL IMPORTANTE

Foi identificado que menus de plugins como o PetHorse não devem ser tratados como inventários persistentes.

O fluxo correto é:

    /horse
       ↓
    PetHorse cria uma nova GUI
       ↓
    PetHorse cria novos ItemStacks
       ↓
    Inventory Open
       ↓
    EtherTexture intercepta o evento
       ↓
    EtherTexture identifica os itens
       ↓
    EtherTexture modifica o ItemStack
       ↓
    ItemStack modificado é devolvido ao slot
       ↓
    jogador recebe a GUI personalizada

Quando o jogador fecha e executa novamente:

    /horse

o plugin pode criar uma nova instância completa do inventário.

Portanto, o EtherTexture deve executar a transformação **novamente a cada abertura do menu**.

Não é necessário persistir o ItemStack modificado.

---

# 3. ERRO DE ARQUITETURA CORRIGIDO

Inicialmente o sistema estava sendo pensado em torno de um slot específico:

    Slot 10
    EXPERIENCE_BOTTLE
    CMD

Isso foi considerado inadequado para o sistema definitivo.

A arquitetura correta deve ser:

    INVENTORY OPEN
          ↓
    detectar menu
          ↓
    percorrer slots
          ↓
    identificar ItemStacks válidos
          ↓
    aplicar regras EtherTexture
          ↓
    substituir ItemStack na instância atual

O slot pode ser utilizado como critério de uma regra específica, mas não deve ser uma dependência estrutural do scanner.

---

# 4. VERSÃO v0.1.3

A v0.1.3 introduziu o conceito de:

    MENU TRANSFORMER

O scanner passou a:

- detectar Inventory Open;
- identificar o inventário;
- percorrer slots;
- ignorar AIR;
- identificar Material;
- ler Custom Model Data;
- aplicar CMD;
- devolver o ItemStack ao slot.

Foi identificado um problema com:

    size of {_inventory}

que retornava:

    <none>

neste ambiente.

Por isso, para o teste controlado do PetHorse, foi utilizada a faixa conhecida:

    slots 0 → 26

---

# 5. v0.1.4

A v0.1.4 foi criada especificamente para comprovar a cadeia:

    ITEM ORIGINAL
       ↓
    SCAN
       ↓
    CMD INJECTION
       ↓
    ITEM MODIFICADO
       ↓
    RETURN

O teste utilizou:

    Item: EXPERIENCE_BOTTLE
    Slot: 10
    CMD: 2330

---

# 6. RESULTADO REAL DO TESTE

Relatório obtido:

    ==================================================
    [EtherTexture] v0.1.4 SCAN MENU
    [EtherTexture] MENU: Horse Stats
    ==================================================

    [EtherTexture] ITEM: EXPERIENCE_BOTTLE
    [EtherTexture] SLOT: 10
    [EtherTexture] CMD:
    [EtherTexture] CMD: <none>

    [EtherTexture] v0.1.4 APPLY CMD
    [EtherTexture] CMD: 2330

    [EtherTexture] v0.1.4 SCAN MENU RETURNED
    [EtherTexture] ITEM: EXPERIENCE_BOTTLE
    [EtherTexture] SLOT: 10
    [EtherTexture] CMD:
    [EtherTexture] 2330

    [EtherTexture] RESULTADO ESPERADO: CMD 2330

    --------------------------------------------------

    [EtherTexture] v0.1.4 SCAN FINALIZADO
    ==================================================

---

# 7. CONCLUSÃO DA PROVA

O teste comprovou:

    CMD BEFORE
    <none>

    ↓

    EtherTexture

    ↓

    CMD AFTER
    2330

Portanto:

    PetHorse ItemStack
          ↓
    Skript
          ↓
    Custom Model Data = 2330
          ↓
    ItemStack modificado
          ↓
    ItemStack recolocado no inventário

A injeção de Custom Model Data em um ItemStack criado dinamicamente por um plugin está FUNCIONANDO.

---

# 8. COMPONENT COUNT

Anteriormente foi observado que alguns itens apresentavam mudanças no número de componentes:

    Item original:
    14 componentes

    Após CMD:
    15 componentes

e o Berrante de Cavalo:

    Original:
    15 componentes

    Após CMD:
    16 componentes

Essa observação continua sendo útil como indicador secundário.

Porém, a contagem de componentes NÃO deve ser considerada a prova principal.

A prova definitiva utilizada agora é:

    CMD ORIGINAL
       ↓
    CMD INJETADO
       ↓
    CMD RETORNADO

No teste v0.1.4:

    <none> → 2330

---

# 9. REGISTRO DE CMD PARA MENUS

Foi definida uma faixa experimental específica para os ícones do menu:

    2330 → 2340

Primeiro CMD validado:

    2330

Planejamento inicial do Horse Stats:

    2330 → EXPERIENCE_BOTTLE → Level
    2331 → SUGAR → Speed
    2332 → APPLE → Health
    2333 → RABBIT_FOOT → Jump Strength
    2334 → LIME_DYE → Status
    2335 → FILLED_MAP → Total Blocks
    2336 → FEATHER → Total Jumps

Esses valores devem ser registrados no:

    ETHERTEXTURE_CMD_REGISTRY

antes de serem reutilizados em outros sistemas.

---

# 10. RELAÇÃO COM O SISTEMA EtherTexture

O fluxo completo planejado agora é:

    Plugin
       ↓
    cria ItemStack
       ↓
    Menu abre
       ↓
    EtherTexture Menu Inspector
       ↓
    identifica ItemStack
       ↓
    EtherTexture Transformer
       ↓
    injeta CMD
       ↓
    Minecraft Item Model
       ↓
    range_dispatch / item model
       ↓
    modelo EtherTexture
       ↓
    textura personalizada

O objetivo final é criar ícones personalizados para menus de plugins sem modificar a mecânica ou o código original desses plugins.

---

# 11. PRINCÍPIO FUNDAMENTAL

O EtherTexture NÃO deve substituir a mecânica do plugin.

Ele deve alterar somente a apresentação visual do ItemStack.

Exemplo:

    PetHorse cria:

    EXPERIENCE_BOTTLE
    nome = Level: 2
    lore = dados do cavalo

O EtherTexture deve transformar:

    EXPERIENCE_BOTTLE
    + dados originais
    + CMD 2330

sem remover:

- nome;
- lore;
- quantidade;
- componentes;
- NBT;
- funcionalidade;
- comportamento esperado pelo plugin.

A intenção é:

    MESMO ITEM
    MESMA MECÂNICA
    NOVA APRESENTAÇÃO VISUAL

---

# 12. ESTADO ATUAL

Status:

    [OK] Inventory Open detectado
    [OK] Menu PetHorse detectado
    [OK] ItemStack detectado
    [OK] Material detectado
    [OK] CMD inexistente identificado
    [OK] CMD 2330 aplicado
    [OK] ItemStack devolvido ao slot
    [OK] CMD 2330 confirmado após transformação
    [OK] Transformação ocorre sobre a instância atual do menu

Ainda NÃO validado:

    [ ] Textura final do CMD 2330
    [ ] Modelo final do CMD 2330
    [ ] Aplicação dos CMD 2331–2336
    [ ] Regras genéricas para múltiplos menus
    [ ] Preservação completa de todos os componentes em casos complexos
    [ ] Sistema definitivo de regras/configuração

---

# 13. PRÓXIMA VERSÃO

Próximo marco:

    EtherTexture Menu Inspector v0.1.5

Objetivo:

    transformar o CMD 2330 em um ícone visual real.

Primeiro:

    EXPERIENCE_BOTTLE
          ↓
        CMD 2330
          ↓
    EtherTexture Item Model
          ↓
    modelo personalizado
          ↓
    textura personalizada

Somente depois da validação visual do 2330 devem ser adicionados:

    2331
    2332
    2333
    2334
    2335
    2336

---

# 14. OBJETIVO DE LONGO PRAZO

Transformar o EtherTexture em um sistema genérico capaz de personalizar visualmente menus de diferentes plugins.

Arquitetura desejada:

    Menu Detector
          ↓
    Menu Identifier
          ↓
    Item Scanner
          ↓
    Item Classifier
          ↓
    EtherTexture Rule
          ↓
    CMD Injector
          ↓
    ItemStack Return

Exemplo futuro:

    PetHorse
    ├── Horse Stats
    ├── Horse Inventory
    └── outros menus

    Outros plugins
    ├── menu A
    ├── menu B
    └── menu C

Todos utilizando o mesmo motor EtherTexture.

---

# 15. MARCO IMPORTANTE

A v0.1.4 representa a primeira prova funcional de:

    PLUGIN → MENU → ITEMSTACK → SKRIPT → CMD → ITEMSTACK

O sistema não depende mais de alterar diretamente o plugin que cria o menu.

O EtherTexture consegue atuar como uma camada visual externa.

Isso permite avançar para a criação de:

    ÍCONES PERSONALIZADOS DE MENUS

sem alterar a mecânica dos plugins.

UPDATE > 19/08/2026 - 20:41 - M004A

# UPDATE — EtherTexture Menu Inspector / Sistema de Ícones de Menus

Projeto: AeternumSeasons-CustomETHER
Área: EtherTexture / Menu Inspector

---

# 1. OBJETIVO ATUAL

Desenvolver um sistema chamado:

EtherTexture_Menu_Inspector.sk

Objetivo:

Investigar menus de plugins Bukkit/Paper, identificar os ItemStacks utilizados como ícones e futuramente aplicar Custom Model Data (CMD) para substituir visualmente esses ícones através do resource pack EtherTexture.

IMPORTANTE:

O objetivo NÃO é substituir a mecânica dos plugins.

A ideia é:

PLUGIN
→ continua criando e controlando seus próprios itens
→ EtherTexture identifica esses itens
→ EtherTexture adiciona apenas dados visuais
→ Resource Pack altera a aparência

A mecânica original deve permanecer intacta.

---

# 2. REGISTRO DE CMD

Foi criado o registro:

ETHERTEXTURE_CMD_REGISTRY

Faixa reservada:

2300–2399 → EtherTexture

A intenção é evitar CMDs aleatórios e manter uma organização centralizada.

Exemplo planejado:

2300 → exemplo/base
2321 → Berrante de Cavalo
...

Para itens de menus, os CMDs também deverão ser registrados antes de serem utilizados definitivamente.

---

# 3. BERRANTE DE CAVALO

Foi desenvolvido um item baseado em:

minecraft:goat_horn

O Skript detecta:

player's tool is goat horn

O item utiliza:

minecraft:item_model = ether:horse_horn

e:

minecraft:custom_model_data

O CMD alterna entre:

1 → chamar cavalo
2 → esconder cavalo

O sistema possui cooldown vanilla do Goat Horn.

O Skript está funcionando corretamente.

A textura passou por problemas de caminho/nome durante o desenvolvimento.

Foi padronizado o nome real do recurso como:

horse_horn

Após a correção dos caminhos do resource pack, a textura passou a funcionar corretamente.

---

# 4. DESCOBERTA SOBRE RANGE_DISPATCH

Foi inicialmente considerado usar CMDs baixos específicos:

1
2
3

Depois foi decidido utilizar a faixa:

2300–2399

O CMD pode ser utilizado dentro da cadeia de modelos de um item específico, porém foi decidido manter um REGISTRO GLOBAL de CMD para evitar confusão futura.

Mesmo que um CMD seja tecnicamente usado somente em um determinado item, ele será tratado como identificador reservado do EtherTexture.

---

# 5. INÍCIO DO MENU INSPECTOR

Foi criado:

EtherTexture_Menu_Inspector.sk

Primeiro objetivo:

Descobrir como plugins de GUI constroem seus menus e quais ItemStacks eles utilizam.

Foi utilizado o PetHorse como primeiro caso real.

---

# 6. PRIMEIRA DESCOBERTA — INVENTORY CLICK

Teste:

on inventory click

Resultado:

BAÚ / INVENTÁRIO NORMAL
→ evento detectado

MENU PET HORSE
→ clique não era detectado

Conclusão inicial:

O problema não era o Inspector inteiro.

O PetHorse possui tratamento próprio de GUI/clique que impede nosso evento de clique de ser útil para a investigação.

---

# 7. SEGUNDA DESCOBERTA — INVENTORY OPEN

Foi testado:

on inventory open

Resultado:

BAÚ
→ detectado

PET HORSE
→ detectado

O PetHorse retornou:

Inventory: inventory of MenuHolder
Nome: Horse Stats

Posteriormente o tipo apareceu como:

chest inventory

Conclusão:

O PetHorse utiliza um inventário Bukkit normal.

Portanto:

não é uma GUI fora do sistema normal de inventários.

O problema está relacionado principalmente ao tratamento do clique.

---

# 8. ESTRATÉGIA ALTERADA

Como o clique não era necessário para nossa finalidade, decidimos investigar os itens no momento em que o menu é aberto.

Estratégia:

on inventory open
→ obter event-inventory
→ percorrer slots
→ identificar ItemStacks
→ analisar Material / Name / Lore / CMD / NBT

Isso funcionou.

---

# 9. PRIMEIRO MAPA DO PET HORSE

Menu:

Horse Stats

Slots válidos identificados:

Slot 10
Material: Bottle o' Enchanting
Nome: Level: 2

Slot 11
Material: Sugar
Nome: Speed: 0.20 (~8.71 b/s)

Slot 12
Material: Apple
Nome: Health: 16.5 -

Slot 13
Material: Rabbit Foot
Nome: Jump Strength: 0.66
(1.19 blocks)

Slot 14
Material: Lime Dye
Nome: Horse is ready

Slot 16
Material: Filled Map
Nome: Total Blocks: 2.1к

Slot 17
Material: Feather
Nome: Total Jumps: 17

Os slots 10–17 possuem os itens reais da GUI.

---

# 10. DESCOBERTA DE NBT

Foi utilizado:

set {_nbt} to nbt of {_item}

O SkBee aceitou essa expressão.

O NBT dos itens reais mostrou principalmente:

minecraft:custom_name
minecraft:lore

Não foi encontrado:

minecraft:custom_model_data

nem:

minecraft:custom_data

nos itens analisados do PetHorse.

Isso é extremamente importante.

Os itens parecem ser ItemStacks comuns, diferenciados principalmente por:

Material
Custom Name
Lore
Slot

---

# 11. IMPLICAÇÃO PARA O ETHERTEXTURE

Isso indica que provavelmente podemos adicionar:

minecraft:custom_model_data

a esses itens sem precisar modificar a mecânica interna do PetHorse.

Exemplo conceitual:

PetHorse:

Slot 10
EXPERIENCE_BOTTLE
Nome: Level: 2

EtherTexture futuramente:

Slot 10
EXPERIENCE_BOTTLE
Nome: Level: 2
CMD: 23XX

O plugin continuaria reconhecendo o mesmo ItemStack/material/nome/lore.

O CMD serviria somente para alterar a aparência através do resource pack.

---

# 12. PROBLEMA ENCONTRADO NOS SLOTS VAZIOS

Ao percorrer:

0–53

o Inspector inicialmente tentou filtrar com:

if {_item} is not air

Isso não foi suficiente.

Também foi testado:

if type of {_item} is not air

Também não resolveu completamente.

O relatório revelou que, após os slots reais, determinados slots aparecem como:

Material: <none>
Name: <none>
NBT: <none>

Exemplo:

Slot 27+
Material: <none>
Name: <none>
NBT: <none>

Isso indica que o Skript está representando determinados slots como um objeto consultável, porém sem ItemStack/material real.

Conclusão:

AIR e <none> não estão sendo tratados da mesma forma pelo ambiente atual.

---

# 13. v0.0.9

A v0.0.9 foi criada como:

VALID ITEM SCANNER

Objetivo:

Percorrer slots e enviar ao console somente os slots considerados válidos.

Ela melhorou o relatório, mas ainda mostrou slots <none> posteriormente.

Portanto a lógica:

if type of {_item} is not air

não é suficiente para definir "ItemStack válido" nesse contexto.

---

# 14. PRINCIPAL DESCOBERTA ATUAL

Os itens reais possuem dados concretos:

Material
Name
Lore
NBT

Os slots falsos possuem:

Material: <none>
Name: <none>
Lore: <none>
NBT: <none>

Portanto precisamos criar uma definição mais precisa de:

VALID ITEM

O próximo teste deve descobrir a forma correta de diferenciar:

ItemStack real

de:

slot inexistente / vazio representado como <none>

---

# 15. PRÓXIMO TESTE PROPOSTO

Foi proposta a investigação:

v0.1.1 — SLOT EXISTENCE TEST

Objetivo:

Testar diretamente:

if {_item} is set

antes de consultar:

type of {_item}
display name
lore
nbt

A finalidade é descobrir se o Skript diferencia corretamente:

VALID
→ ItemStack existente

EMPTY
→ variável/slot inexistente

Ainda NÃO foi confirmado se essa abordagem é a correta.

---

# 16. ESTADO ATUAL DO PROJETO

EtherTexture resource pack
→ funcionando

CMD Registry
→ criado

Berrante de Cavalo
→ funcionando

Skript do Berrante
→ funcionando

Cooldown vanilla
→ funcionando

Menu Inspector
→ funcionando para inventory open

PetHorse menu
→ detectado

PetHorse inventory
→ confirmado como chest inventory

PetHorse MenuHolder
→ identificado como MenuHolder

Horse Stats
→ identificado

Itens reais
→ identificados nos slots 10, 11, 12, 13, 14, 16 e 17

NBT
→ acessível através do SkBee

Custom Model Data dos itens PetHorse
→ atualmente inexistente

Aplicação de CMD
→ AINDA NÃO TESTADA

Alteração de mecânica
→ NUNCA deve ocorrer

---

# 17. PRÓXIMA ETAPA

NÃO aplicar CMD ainda.

Primeiro:

1. Resolver definitivamente a identificação de slots válidos.
2. Criar um scanner limpo.
3. Confirmar que somente os slots 10, 11, 12, 13, 14, 16 e 17 aparecem.
4. Criar relatório estruturado.
5. Identificar uma forma segura de reconhecer cada botão.
6. Somente então testar CMD em UM único item.
7. Reabrir o menu.
8. Verificar se a aparência mudou.
9. Verificar se o PetHorse continua funcionando.
10. Só depois criar o sistema automático de EtherTexture para menus.

---

# 18. PRINCÍPIO FUNDAMENTAL

O EtherTexture_Menu_Inspector deve ser inicialmente:

READ-ONLY

Nenhuma alteração nos ItemStacks deve ocorrer durante a fase de investigação.

Somente depois de conhecer exatamente a estrutura dos menus será permitido adicionar CMD.

Objetivo final:

PLUGIN MECHANICS
        ↓
     intactas
        ↓
ItemStack original
        +
CustomModelData
        ↓
EtherTexture Resource Pack
        ↓
Ícone personalizado

---

# STATUS

🟢 EtherTexture Resource Pack
🟢 CMD Registry
🟢 Berrante de Cavalo
🟢 Skript / cooldown
🟢 Inventory Open Detection
🟢 PetHorse Detection
🟢 MenuHolder Detection
🟢 ItemStack Detection
🟢 NBT Detection

🟡 Slot Validation
🟡 Menu Identification System
🟡 Safe Item Identification

🔴 CMD Injection
🔴 Automatic Menu Skinning

A prioridade atual é resolver o 🟡 SLOT VALIDATION antes de qualquer alteração visual.
UPDATE > 2026-08-19 - 17:36 - M004A

# ============================================================
# ETHERCRAFT — ETHERTEXTURE CMD REGISTRY
# MEMORY UPDATE — BERRANTE DE CAVALO
# ============================================================

STATUS:
CONFIRMED / TESTED IN-GAME

============================================================
1. NOVA REGRA OFICIAL — CMD GLOBAL
============================================================

Foi decidido que o EtherTexture utilizará UM ÚNICO sistema
global de CustomModelData.

Faixa oficial:

2300+

Não haverá distinção entre:

- CMD global
- CMD local
- CMD de estado interno

Todo CMD utilizado pelo EtherCraft deve possuir um significado
único e registrado neste Registry.

Motivo:

Evitar confusão futura entre IDs visuais locais e globais,
facilitando manutenção, debugging e expansão do sistema.

============================================================
2. CMD RESERVADO
============================================================

2300 → EXEMPLO / RESERVED

O CMD 2300 serve como marcador inicial da faixa e não deve ser
utilizado por itens reais.

============================================================
3. BERRANTE DE CAVALO
============================================================

Item base:

minecraft:goat_horn

Item Model:

ether:horse_horn

Custom Data:

ether_item: "horse_toggle"

CMD inicial:

2320

============================================================
4. BERRANTE — ESTADOS
============================================================

2320 → Horse Horn — State 1
      Estado inicial / cavalo oculto
      Ação:
      /horse summon

2321 → Horse Horn — State 2
      Estado / cavalo ativo
      Ação:
      /horse hide

Ciclo:

2320
 ↓
horse summon
 ↓
2321
 ↓
horse hide
 ↓
2320

============================================================
5. SKRIPT — LÓGICA CONFIRMADA
============================================================

O Skript utiliza o próprio cooldown vanilla do Goat Horn.

Fluxo:

RIGHT CLICK
    ↓
GOAT HORN?
    ↓
VANILLA COOLDOWN?
    ├── SIM → STOP
    └── NÃO
          ↓
       verificar CMD
          ↓
     ┌────┴────┐
     ▼         ▼
   2320      2321
     │         │
     ▼         ▼
  SUMMON      HIDE
     │         │
     ▼         ▼
   2321      2320

O cooldown vanilla não é substituído por um sistema manual.

STATUS:

CONFIRMED / FUNCIONANDO

============================================================
6. RESOURCE PACK — ARQUITETURA CONFIRMADA
============================================================

O Berrante utiliza:

minecraft:item_model
    ↓
ether:horse_horn
    ↓
assets/ether/items/horse_horn.json
    ↓
minecraft:range_dispatch
    ↓
minecraft:custom_model_data
    ↓
CMD 2320 / 2321
    ↓
assets/ether/models/item/horse_horn.json
    ↓
texture layer
    ↓
assets/ether/textures/item/tools/miscellanea/horse_horn.png

STATUS:

CONFIRMED / TEXTURA FUNCIONANDO IN-GAME

============================================================
7. DESCOBERTA IMPORTANTE — RANGE DISPATCH
============================================================

Foi confirmado experimentalmente que CustomModelData pode ser
utilizado como controlador de estado visual de um item.

Exemplo:

CMD 2320 → estado visual A
CMD 2321 → estado visual B

O modelo pode ser controlado através de:

minecraft:range_dispatch

Isso permite que um mesmo ItemStack possua diferentes
representações visuais dependendo do CMD atual.

IMPORTANTE:

Os estados continuam utilizando IDs do Registry global.

Não criar CMDs locais fora do Registry.

============================================================
8. FALLBACK VANILLA
============================================================

O sistema precisa preservar o comportamento visual vanilla
quando o item não possui um CMD registrado para o sistema.

Para Goat Horn:

fallback:

minecraft:item/goat_horn

Isso permite que Goat Horns vanilla continuem funcionando
normalmente mesmo após a personalização do modelo.

STATUS:

CONFIRMED / VANILLA GOAT HORNS PRESERVADOS

============================================================
9. LIÇÃO SOBRE RESOURCE LOCATION
============================================================

Referências de textura dentro de modelos Minecraft NÃO devem
utilizar caminhos físicos contendo:

assets/
.png

ERRADO:

assets/ether/textures/item/tools/miscellanea/horse_horn.png

CORRETO:

ether:item/tools/miscellanea/horse_horn

O caminho físico:

assets/ether/textures/item/tools/miscellanea/horse_horn.png

é convertido em Resource Location:

ether:item/tools/miscellanea/horse_horn

============================================================
10. LIÇÃO SOBRE NOMENCLATURA
============================================================

Foi identificado que nomes inconsistentes como:

horn_horse
horse_horn

podem criar referências quebradas difíceis de detectar.

Foi adotada nomenclatura unificada:

horse_horn

Quando possível, o mesmo conceito deve utilizar o mesmo nome
nos arquivos relacionados:

- item model
- model
- texture
- documentação

Evitar duplicações com nomes semelhantes.

============================================================
11. PRINCÍPIO DE PRESERVAÇÃO DA MECÂNICA
============================================================

A camada EtherTexture deve modificar prioritariamente a
representação visual do ItemStack sem substituir sua identidade
ou mecânica original.

Objetivo:

ITEM ORIGINAL
    +
CUSTOM MODEL DATA
    =
MESMA MECÂNICA + NOVO VISUAL

Não reconstruir o ItemStack desnecessariamente.

Preservar:

- Material
- Nome
- Lore
- Enchantments
- Custom Data
- Persistent Data
- Componentes
- Flags
- Dados utilizados pelo plugin

quando o objetivo for apenas personalização visual.

============================================================
12. FUTURA APLICAÇÃO — ÍCONES DE MENUS DE PLUGINS
============================================================

Foi levantada e considerada VIÁVEL a possibilidade de utilizar
EtherTexture para personalizar visualmente itens presentes em
inventários/menus criados por outros plugins.

Conceito:

PLUGIN ABRE MENU
    ↓
ETHER OBSERVA ITEM
    ↓
IDENTIFICA ITEM
    ↓
ITEM POSSUI CMD?
    ├── SIM → preservar
    └── NÃO
          ↓
      identificar função
          ↓
      adicionar CMD EtherTexture
          ↓
      novo ícone visual

OBJETIVO:

Alterar apenas a apresentação visual dos ícones sem quebrar a
mecânica interna do plugin.

IMPORTANTE:

Ainda NÃO implementado.

Antes de desenvolver o sistema, deve ser realizado um teste
experimental com:

on inventory click

para identificar:

- Inventory title
- Inventory type
- Slot
- Material
- Display Name
- Lore
- CustomModelData
- Custom Data
- Persistent Data / PDC
- Componentes disponíveis ao Skript/SkBee

O primeiro teste deve ser SOMENTE DE LEITURA.

Não modificar os ItemStacks até confirmar quais dados podem ser
acessados com segurança.

============================================================
13. PRÓXIMO TESTE RECOMENDADO
============================================================

Criar um Skript experimental de inspeção de menus.

Objetivo:

Ao abrir/interagir com um menu de plugin, identificar o ItemStack
presente no slot e imprimir seus dados para análise.

Nenhuma alteração visual deve ser realizada nessa etapa.

Somente após descobrir como os plugins estruturam seus itens será
criado o sistema de adapters EtherTexture.

============================================================
14. ARQUITETURA ATUAL
============================================================

EtherTexture

├── CMD Registry Global
│   └── 2300+
│
├── Item Models
│
├── Range Dispatch
│
├── CustomModelData
│
├── Textures
│
└── Futuro:
    └── Menu / Plugin Adapters

============================================================
15. STATUS GERAL
============================================================

CMD Registry Global:
CONFIRMED

Faixa 2300+:
CONFIRMED

Berrante de Cavalo:
CONFIRMED

CMD 2320:
CONFIRMED

CMD 2321:
CONFIRMED

Range Dispatch:
CONFIRMED

Item Model:
CONFIRMED

Modelo:
CONFIRMED

Textura:
CONFIRMED

Fallback Vanilla:
CONFIRMED

Cooldown Vanilla:
CONFIRMED

Skript SUMMON/HIDE:
CONFIRMED

Plugin Menu Adapter:
PLANNED / NOT IMPLEMENTED

============================================================
END OF MEMORY UPDATE
============================================================

UPDATE > 18/08/2026 - M003A

# ETHERCRAFT ITEM SYSTEM / ESTADOS VISUAIS

## STATUS

ITEM INTERACTION / STATE SYSTEM / CONFIRMED ✓

---

## CONFIRMED

- Um item vanilla pode ser utilizado como base para um item
  próprio do EtherCraft.

- `minecraft:goat_horn` foi utilizado como base para o
  "Berrante de Cavalo".

- O item vanilla continua funcional após receber:
  - `minecraft:custom_model_data`
  - `minecraft:custom_data`
  - `minecraft:item_model`
  - `minecraft:custom_name`
  - `minecraft:lore`

- O componente vanilla `minecraft:instrument` deve utilizar a
  sintaxe:

  `minecraft:instrument="minecraft:seek_goat_horn"`

- `minecraft:seek_goat_horn` foi confirmado como instrumento
  válido e funcional.

- O uso do `seek_goat_horn` permite manter o comportamento de
  som do próprio Minecraft, sem necessidade de criar uma lógica
  de áudio no Skript.

- O Resource Pack pode utilizar:

  `item_model="ether:horse_horn"`

  para assumir posteriormente a representação visual própria
  do EtherCraft.

- O item pode possuir:

  `custom_data={ether_item:"horse_toggle"}`

  como identidade lógica permanente do item.

- O item pode possuir:

  `custom_model_data={floats:[1.0f]}`

  e o Skript consegue utilizar o CustomModelData como
  identificador do estado.

- Foi confirmado em servidor real que o Skript consegue
  diferenciar o Berrante de Cavalo de outros Goat Horns através
  do CustomModelData.

- Outros Goat Horns não executam a lógica do Berrante.

- O Skript conseguiu executar:

  `horse summon`

  quando o item possuía CMD 1.

- O Skript conseguiu executar:

  `horse hide`

  quando o item possuía CMD 2.

- O sistema de estados CMD 1 ↔ CMD 2 foi confirmado em servidor
  real.

---

## DISCOVERED

### 1. CustomModelData pode representar estado lógico

O CustomModelData não precisa representar somente uma textura.

Ele também pode funcionar como uma máquina de estados visual/lógica.

Exemplo:

CMD 1
    ↓
Cavalo escondido
    ↓
/horse summon
    ↓
CMD 2

CMD 2
    ↓
Cavalo ativo
    ↓
/horse hide
    ↓
CMD 1

Portanto:

`CMD = estado atual do item`

e não necessariamente:

`CMD = aparência obrigatoriamente diferente`

---

### 2. Separação entre identidade e estado

Foi estabelecida uma distinção importante:

`custom_data`
    ↓
identidade do item

`custom_model_data`
    ↓
estado visual/estado operacional

Exemplo:

`ether_item = horse_toggle`

identifica:

"este item pertence ao sistema Berrante de Cavalo"

Enquanto:

`CMD 1`

ou

`CMD 2`

identifica:

"qual estado o Berrante está atualmente?"

---

### 3. O item pode ser nativo e ainda assim pertencer ao EtherCraft

O Berrante de Cavalo não precisa ser um novo Material.

Base:

`minecraft:goat_horn`

Identidade:

`ether_item = horse_toggle`

Estado:

`CMD 1 / CMD 2`

Visual:

`ether:horse_horn`

Comportamento:

Skript + Pet Horse

Isso permite criar itens EtherCraft utilizando itens vanilla
como base, sem criar um novo ItemStack proprietário.

---

### 4. O Resource Pack não precisa participar da lógica

O Skript não deve precisar alterar:

- textura;
- modelo;
- item_model;
- nome;
- lore.

O Skript somente controla:

`estado → ação → novo estado`

O EtherTexture/Resource Pack controla:

`CMD → modelo → textura`

---

### 5. A arquitetura do EtherTexture foi ampliada

A visão inicial era:

Plugin
  ↓
Identidade
  ↓
EtherTexture
  ↓
CMD
  ↓
Resource Pack

Agora existe também:

EtherCraft Item
  ↓
Identidade própria
  ↓
Estado
  ↓
CMD
  ↓
Resource Pack

Portanto o EtherTexture atende dois tipos de origem:

1. itens provenientes de plugins externos;
2. itens próprios do EtherCraft baseados em materiais vanilla.

---

## DISCARDED

- Não será criado um novo sistema de som para o Berrante.

- Não será utilizado comando separado para reproduzir o som.

- Não será necessário substituir o `minecraft:goat_horn`
  por outro Material.

- Não será necessário alterar o ItemStack inteiro para alternar
  entre os estados.

- Não será criada uma textura diferente diretamente pelo Skript.

- Não será criada inicialmente uma lógica manual de cooldown.

- Não será utilizado o `custom_data` como mecanismo de estado.

- O `custom_data` permanecerá como identidade do item.

- O CMD será utilizado como estado.

---

## DECISIONS

### 1. Separação de responsabilidades

Minecraft Vanilla
    ↓
comportamento nativo do Goat Horn
    ↓
som / instrumento / cooldown

Skript
    ↓
lógica do item
    ↓
estado CMD
    ↓
comandos do Pet Horse

EtherTexture
    ↓
representação visual

Resource Pack
    ↓
modelo + textura

---

### 2. Estrutura lógica do Berrante

Identidade:

`ether_item = horse_toggle`

Estado inicial:

`CMD 1`

Ação:

`/horse summon`

Novo estado:

`CMD 2`

Segundo uso:

`/horse hide`

Novo estado:

`CMD 1`

Fluxo:

1
→ summon
→ 2
→ hide
→ 1
→ summon
→ 2
→ ...

---

### 3. O CMD pode ser invisível para o jogador

A troca entre:

`CMD 1`

e

`CMD 2`

não precisa produzir diferença visual.

O Resource Pack pode apontar os dois estados para o mesmo
modelo/textura.

Ou futuramente:

`CMD 1 → Berrante normal`

`CMD 2 → Berrante ornamentado/brilhante`

A lógica permanece a mesma.

---

## CURRENT STATE

O Berrante de Cavalo está funcional.

Item base:

`minecraft:goat_horn`

Componente de instrumento:

`minecraft:instrument="minecraft:seek_goat_horn"`

Identidade:

`ether_item = horse_toggle`

Modelo:

`ether:horse_horn`

Estado inicial:

`CMD 1`

Comportamento:

`CMD 1 → /horse summon → CMD 2`

`CMD 2 → /horse hide → CMD 1`

O sistema foi testado em servidor real.

Resultado:

✓ Cavalim aparece  
✓ Cavalim é escondido  
✓ CMD alterna entre os estados  
✓ Outros Goat Horns não ativam a lógica  
✓ Item vanilla continua funcional  
✓ Som vanilla do Goat Horn pode ser utilizado  
✓ Visual permanece sob responsabilidade do EtherTexture

---

## COOLDOWN

O Goat Horn possui cooldown vanilla.

Objetivo:

O Berrante deve respeitar a recarga nativa do Minecraft.

Não deve ser criado um timer paralelo no Skript se o cooldown
vanilla puder ser consultado diretamente.

Testes realizados:

`player's tool is on cooldown`

→ inválido neste ambiente.

`cooldown of player's tool is greater than 0`

→ inválido neste ambiente.

O mecanismo correto para consultar o cooldown no ambiente atual
ainda está em investigação.

STATUS:

`PENDING`

REGRA:

Não implementar cooldown manual enquanto a possibilidade de
reutilizar o cooldown vanilla não estiver descartada.

---

## ARCHITECTURE UPDATE

O EtherTexture não deve ser entendido somente como:

"modificador visual de itens de plugins".

A arquitetura correta passa a ser:

                    ETHERCRAFT
                        │
          ┌─────────────┴─────────────┐
          │                           │
     EXTERNAL ITEMS              OWN ITEMS
          │                           │
 Aeternum / Theosis             Vanilla Base
          │                           │
          └─────────────┬─────────────┘
                        ↓
                   IDENTIDADE
                        ↓
                  ETHER SYSTEM
                        ↓
               CUSTOM MODEL DATA
                        ↓
                 RESOURCE PACK
                        ↓
                     VISUAL

Para itens interativos:

IDENTIDADE
    ↓
ESTADO
    ↓
AÇÃO
    ↓
NOVO ESTADO
    ↓
CUSTOM MODEL DATA
    ↓
VISUAL

---

## NEXT TARGET

1. Encontrar a expressão correta do Skript/SkBee para detectar
   o cooldown vanilla do Goat Horn.

2. Fazer o Berrante respeitar completamente a recarga nativa.

3. Confirmar que o cooldown funciona igualmente nos estados
   CMD 1 e CMD 2.

4. Criar a definição visual do:

   `ether:horse_horn`

5. Registrar formalmente o Berrante no Registry do EtherTexture.

6. Definir uma convenção para itens EtherCraft próprios:

   `ether_item = <id>`

7. Definir uma convenção para estados:

   `CMD 1`
   `CMD 2`
   `CMD 3`
   ...

8. Avaliar se o Registry deverá futuramente possuir também
   definição de estados e ações.

9. Continuar expandindo o EtherTexture como sistema visual
   central do EtherCraft.

---

## CONTINUITY NOTE

O Berrante de Cavalo representa a primeira validação de que o
EtherTexture pode trabalhar em conjunto com itens interativos
próprios do EtherCraft sem assumir a responsabilidade pela
mecânica do item.

O princípio estabelecido é:

IDENTIDADE
    ≠
ESTADO
    ≠
VISUAL
    ≠
MECÂNICA

Cada camada possui uma responsabilidade própria.

Esta separação deve ser preservada nas próximas implementações.

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
