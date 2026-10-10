# Clima fixo por mundo e compatibilidade de backups Terra2

## Estado da entrega

Correção de código-fonte para o AeternumSeasons. Foram incluídos testes isolados de perfis e resolução de IDs, executados pelo workflow `Climate compatibility core tests`. **Ainda não há JAR corrigido nem validação no servidor:** o repositório principal é descompilado e não contém o JAR do Aeternum atualmente instalado nem um build completo reproduzível. O módulo `ethercraft-custom` da outra branch compila somente o addon de portais; ele não substitui estas classes do plugin principal. É necessário obter o JAR instalado para compilar e testar uma atualização compatível.

## Configuração

Adicionar ao `plugins/AeternumSeasons/climate.yml` **após instalar a versão corrigida**:

```yaml
world_climate:
  profiles:
    aeternum_frost:
      enabled: true
      season: WINTER
      climate-biome: minecraft:snowy_plains
```

Substitua `aeternum_frost` pelo nome exato do mundo que usa HYDRAXIA. Outros mundos podem receber perfis independentes com `SPRING`, `SUMMER`, `AUTUMN` ou `WINTER` e um bioma vanilla de referência. Só mundos explicitamente configurados recebem um perfil. Biomas inválidos e estações inválidas são recusados. O nome `aeternum_frost` mantém o comportamento legado quando não há perfil.

## Comportamento

- O calendário exibido por mundo e os cálculos de temperatura usam a estação fixa.
- A temperatura e classificação de neve usam o bioma de referência somente dentro do Aeternum. O mundo e os IDs do Terra2 continuam intactos.
- Neve no chão e nas folhas segue a estação do mundo e as condições existentes de tempestade/proteção/orçamento. Não é tempestade permanente.
- Perfis bloqueiam a alteração sazonal de biomas, o FrostBiomeFixer e a restauração de backups nesses mundos. Os backups são mantidos.
- O pacote de biomas enviado pelo Minecraft permanece real: o perfil não transforma a chuva visual de um bioma quente em neve no cliente. Hydraxia mantém sua própria configuração de precipitação. Agricultura/fauna e calendário de chuvas não recebem novos mapeamentos de biomas neste patch.
- Backups novos usam IDs completos; os antigos com nomes vanilla continuam legíveis. A paleta inteira é resolvida pelo registro antes de qualquer célula ser aplicada. IDs indisponíveis recusam a restauração inteira e preservam o backup, sem substituição por PLAINS. Avisos de IDs ausentes são emitidos uma vez por ID, com limite de 128 avisos por instância.

## Validação pendente no Paper 26.2

Com o JAR instalado, compilar todas as classes alteradas e seus helpers contra suas dependências e a API do Paper 26.2; gerar JAR atualizado. No servidor de cópia, verificar: inverno fixo no mundo de teste durante verão do mundo principal; temperatura fria e neve durante tempestade; IDs Terra2 iguais antes/depois de carregar chunks; backup de bioma customizado resolvido; ID ausente sem aplicar PLAINS nem excluir o arquivo; salvar/reiniciar; mundo principal sem mudanças de perfil.

O teste `bash tools/test-climate-compatibility.sh` cobre normalização, recusa de perfis inválidos, resolução de IDs vanilla antigos e personalizados, e falha de paleta sem fallback. Ele não substitui a compilação das classes Paper nem os testes de integração.
