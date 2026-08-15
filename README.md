            ⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳
 ▂▃▅▇█▓▒░۩۞۩ ᴀᴇᴛᴇʀɴᴜᴍꜱᴇᴀꜱᴏɴꜱ ᴇᴛʜᴇʀᴄʀᴀꜰᴛ ۩۞۩░▒▓█▇▅▃▂
            ⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳-⨳    
   
> **Projeto de engenharia reversa e estudo técnico do AeternumSeasons 4.5 para desenvolvimento de sistemas próprios do servidor Minecraft EtherCraft.**

---

## ⚠️ IMPORTANTE — LEIA ANTES DE ANALISAR O REPOSITÓRIO

Este repositório contém código **descompilado** a partir do plugin `AeternumSeasons-4.5.jar`.

O objetivo principal é **estudar a implementação interna do plugin**, entender sua arquitetura e identificar os mecanismos necessários para posteriormente desenvolver uma implementação própria/customizada para o servidor **EtherCraft**.

### Não assumir comportamento sem evidência

Durante a análise:

* Não presumir que uma classe funciona como seu nome sugere.
* Não assumir que uma constante é necessariamente o ponto responsável por determinada funcionalidade.
* Não inventar APIs, métodos ou comportamentos.
* Diferenciar claramente:

  * comportamento confirmado pelo código;
  * inferência técnica;
  * hipótese;
  * informação ainda desconhecida.
* Quando uma conclusão depender de outra classe, analisar essa classe antes de concluir.
* Preservar os nomes e estruturas encontrados no código descompilado sempre que possível.

---

# 🎮 Projeto EtherCraft

**EtherCraft** é um servidor Minecraft baseado em **Paper**, com suporte para jogadores Java e Bedrock através de:

* Geyser
* Floodgate

O projeto utiliza e desenvolve sistemas próprios envolvendo:

* Java/Paper
* Skript
* Resource Packs
* modelos 3D
* sistemas de dimensões
* portais
* agricultura customizada
* entidades/modelos visuais
* sistemas próprios do servidor

O objetivo deste repositório é auxiliar especificamente no estudo do **AeternumSeasons**.

---

# 📦 Plugin analisado

Arquivo original:

```text
AeternumSeasons-4.5.jar
```

Plugin:

```text
AeternumSeasons
```

Classe principal identificada:

```text
Kinkin.aeternum.AeternumSeasonsPlugin
```

O plugin possui aproximadamente 268 classes/arquivos identificados na análise inicial.

---

# 🧬 Estrutura identificada

O pacote principal encontrado é:

```text
Kinkin.aeternum
```

Entre os principais sistemas encontrados:

```text
Kinkin.aeternum
├── calendar
├── command
├── compat
├── crafting
├── dimension
├── events
├── farming
├── fauna
├── food
├── frost
├── heat
├── hud
├── items
├── lang
├── portal
├── temperature
├── util
├── weather
└── world
```

---

# 🚪 Sistema de Portais

Este é o foco principal da primeira fase da engenharia reversa.

Foi identificado o pacote:

```text
Kinkin.aeternum.portal
```

Com as seguintes classes:

```text
FrostOverworldPortals
HeatNetherPortals
HeatOverworldPortals
PortalBuildProtection
PortalFrameClassifier
VanillaPortalIsolation
```

## Frost

A classe prioritária para análise é:

```text
Kinkin.aeternum.portal.FrostOverworldPortals
```

Durante a análise inicial do JAR foram identificadas referências a funcionalidades relacionadas a:

```text
detectPortal
lightPortal
findLinkedPortal
findOrCreatePortal
findNearbyPortal
portalTeleportLocation
registerPortalLink
removePortalLink
portalKey
```

Também foram encontradas referências a:

```text
Material.NETHER_PORTAL
Material.GLOWSTONE
Material.FLINT_AND_STEEL
Material.FIRE_CHARGE
```

e ao mundo:

```text
aeternum_frost
```

Esses nomes são observações da análise inicial e devem ser confirmados através do código descompilado.

---

# 🎯 Objetivos da engenharia reversa

Queremos descobrir exatamente:

### 1. Detecção

Como o plugin identifica que uma estrutura pode ser um portal?

### 2. Estrutura

Quais blocos formam o frame?

### 3. Classificação

Como `PortalFrameClassifier` determina se o frame é válido?

### 4. Criação

Como o portal é criado?

### 5. Ativação

Como o portal é aceso?

### 6. Material

Qual material representa o portal?

### 7. Destino

Como o plugin determina para qual dimensão/mundo o jogador será enviado?

### 8. Linking

Como dois portais são associados?

### 9. Teleporte

Como o jogador é efetivamente teleportado?

### 10. Proteção

Como o plugin impede que o comportamento vanilla de portais interfira?

---

# 🧠 Fluxo que deve ser investigado

O fluxo esperado a ser confirmado pelo código é aproximadamente:

```text
Evento/interação
       ↓
detecção da estrutura
       ↓
PortalFrameClassifier
       ↓
validação
       ↓
FrostOverworldPortals
       ↓
criação/acendimento
       ↓
registro/linking
       ↓
localização do portal de destino
       ↓
teleporte
```

**IMPORTANTE:** este fluxo é uma hipótese de trabalho e não deve ser tratado como confirmado até que as classes correspondentes sejam analisadas.

---

# 🔍 Classes prioritárias

A ordem inicial recomendada é:

```text
1. FrostOverworldPortals
2. PortalFrameClassifier
3. VanillaPortalIsolation
4. HeatOverworldPortals
5. HeatNetherPortals
6. PortalBuildProtection
```

Depois disso, seguir para as classes chamadas diretamente por essas classes.

---

# ❄️ Frost Realm

Foi encontrada referência ao mundo:

```text
aeternum_frost
```

Precisamos descobrir se:

* o nome é fixo;
* vem de configuração;
* é registrado durante o carregamento;
* é criado dinamicamente;
* existe uma classe específica responsável pela dimensão.

Também devemos investigar o pacote:

```text
Kinkin.aeternum.frost
```

e:

```text
Kinkin.aeternum.dimension
```

---

# 🔥 Heat Realm

Após entender o Frost, comparar com:

```text
HeatOverworldPortals
HeatNetherPortals
```

O objetivo é determinar:

* o que é compartilhado;
* o que é específico;
* como os diferentes portais são diferenciados;
* se existe uma classe/base comum;
* como os destinos são definidos.

---

# 🧱 PortalFrameClassifier

Esta classe deve ser investigada para descobrir:

* quais blocos são aceitos;
* tamanho mínimo/máximo do frame;
* formato permitido;
* orientação;
* validações;
* condições especiais;
* diferenças entre Frost e Heat.

Não alterar o código antes de compreender essas regras.

---

# 🚫 VanillaPortalIsolation

Investigar especificamente:

* eventos interceptados;
* prevenção de criação de portal vanilla;
* cancelamento de eventos;
* bloqueio de comportamento padrão;
* diferenças entre portais do Aeternum e Nether/End vanilla.

---

# 🛡️ PortalBuildProtection

Investigar:

* proteção contra quebra;
* proteção contra colocação;
* proteção de frames;
* interação com WorldGuard;
* interação com outras proteções.

---

# 🧩 Compatibilidade

O plugin possui referências a integrações opcionais, incluindo:

```text
WorldGuard
GPFlags
CustomCrops
WorldEdit
Citizens
PlaceholderAPI
```

Não assumir que todas são obrigatórias.

Determinar através do código:

* quais são realmente utilizadas;
* onde são utilizadas;
* se são dependências obrigatórias ou opcionais;
* quais sistemas podem ser removidos na implementação própria.

---

# 🏗️ Objetivo final

A engenharia reversa não tem como objetivo necessariamente manter o AeternumSeasons completo.

O objetivo final é entender os sistemas necessários para criar uma implementação própria para o EtherCraft.

Arquitetura pretendida:

```text
AeternumSeasons 4.5
        │
        ▼
Engenharia reversa
        │
        ▼
Entendimento da arquitetura
        │
        ▼
Identificação dos sistemas necessários
        │
        ├── Portais
        ├── Dimensões
        ├── Frost
        ├── Heat
        └── outros sistemas necessários
        │
        ▼
Implementação própria
        │
        ▼
EtherCraft Custom
```

---

# 🎨 Integração futura com sistemas visuais

O EtherCraft possui sistemas próprios de Resource Pack e modelos 3D.

Existe interesse futuro em integrar sistemas como:

* modelos 3D;
* entidades visuais;
* Armor Stands;
* Resource Pack Java;
* Resource Pack Bedrock;
* Geyser;
* sistemas visuais próprios.

Esses sistemas não devem ser misturados à engenharia reversa dos portais prematuramente.

Primeiro entender o funcionamento original.

---

# 🧪 Metodologia

Para cada classe importante:

## Passo 1 — Identificar responsabilidade

Determinar o que a classe realmente faz.

## Passo 2 — Identificar chamadas

Mapear:

```text
Classe A
 ↓
Classe B
 ↓
Classe C
```

## Passo 3 — Identificar eventos

Procurar:

```text
Listener
EventHandler
Bukkit Events
Player Events
Block Events
Entity Events
World Events
```

## Passo 4 — Identificar estado

Procurar:

```text
Map
Set
List
HashMap
UUID
Location
World
PersistentDataContainer
```

Especialmente estruturas usadas para manter vínculos entre portais.

## Passo 5 — Identificar configuração

Determinar se valores são:

```text
hardcoded
```

ou vêm de:

```text
config.yml
```

ou outros arquivos.

## Passo 6 — Documentar

Cada descoberta importante deve ser documentada.

---

# 📚 Regras para futuras análises

Ao analisar este repositório:

### Confirmado

Usar quando o comportamento estiver diretamente demonstrado pelo código.

### Inferência

Usar quando o comportamento for altamente provável, mas não diretamente confirmado.

### Hipótese

Usar quando houver mais de uma interpretação possível.

### Desconhecido

Usar quando for necessário analisar outra classe/arquivo.

---

# 🔧 Modificação

**Não modificar o código original durante a primeira fase.**

Primeiro:

```text
entender
 ↓
documentar
 ↓
mapear dependências
 ↓
identificar pontos de alteração
```

Somente depois:

```text
modificar
 ↓
compilar
 ↓
testar
```

---

# 🗂️ Estrutura recomendada do repositório

```text
AeternumSeasons-Reverse/
│
├── README.md
│
├── src/
│   └── Kinkin/
│       └── aeternum/
│           ├── portal/
│           ├── dimension/
│           ├── frost/
│           ├── heat/
│           └── ...
│
├── resources/
│   ├── plugin.yml
│   └── ...
│
└── docs/
    ├── architecture/
    ├── portals/
    ├── dimensions/
    └── findings/
```

---

# 🤖 INSTRUÇÕES PARA IA — CONTEXTO DO PROJETO

Se uma IA estiver analisando este repositório, deve considerar este README como o contexto inicial do projeto.

## Prioridade

A prioridade atual é:

```text
FrostOverworldPortals
```

Depois:

```text
PortalFrameClassifier
VanillaPortalIsolation
HeatOverworldPortals
HeatNetherPortals
PortalBuildProtection
```

## A IA deve:

1. Ler o README antes de iniciar a análise.
2. Identificar a classe solicitada.
3. Ler as classes chamadas diretamente quando necessário.
4. Não inventar comportamento.
5. Diferenciar fato, inferência e hipótese.
6. Citar o nome dos métodos/classes responsáveis pela conclusão.
7. Explicar o fluxo de execução.
8. Identificar pontos de alteração possíveis.
9. Não modificar o código sem solicitação explícita.
10. Não assumir que o objetivo é manter o AeternumSeasons original.
11. Priorizar o desenvolvimento de uma implementação controlável pelo EtherCraft.

## Se uma informação não puder ser determinada

Não tentar preencher a lacuna com suposição.

Responder:

```text
Não é possível determinar isso somente com esta classe.
Precisamos analisar: <classe/arquivo>.
```

e explicar por quê.

---

# 🚀 Próximo passo

O primeiro arquivo a ser analisado é:

```text
src/Kinkin/aeternum/portal/FrostOverworldPortals.java
```

A análise deve começar identificando:

```text
- classe
- interfaces
- imports
- campos
- construtor
- listeners
- métodos
- materiais
- mundos
- localização
- criação do portal
- linking
- teleporte
- dependências
```

Depois disso, seguir o fluxo para as classes relacionadas.

---

# 📌 Estado atual

**Fase:** Engenharia reversa

**Plugin:** AeternumSeasons 4.5

**Foco:** Sistema de portais

**Primeira classe:** `FrostOverworldPortals`

**Objetivo:** compreender completamente o funcionamento antes de implementar uma versão própria para EtherCraft.

**Modificação do código:** ainda não iniciada.

---

## EtherCraft

Este repositório faz parte do projeto maior **EtherCraft**, um servidor Minecraft customizado.

O resultado esperado não é simplesmente uma cópia do AeternumSeasons.

O objetivo é obter conhecimento técnico suficiente para desenvolver sistemas próprios, controláveis e integrados à arquitetura do EtherCraft.
