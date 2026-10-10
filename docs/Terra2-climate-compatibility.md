# Clima fixo por mundo e compatibilidade de backups Terra2

## Estado da entrega

Build completa **AeternumSeasons-4.5.1-CLIMATE-BETA**, reconstruída e compilada com Java 25 para Paper 26.2. Publicada junto ao código e aos workflows de build reproduzível.

Validação concluída no [workflow Paper 26.2](https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/38082184916): testes de perfis e IDs, compilação de todas as classes, inicialização do plugin, referência virtual SNOWY_PLAINS com estação WINTER, salvamento e segundo início mantendo o bioma real minecraft:plains. Portais Frost/Heat ficaram desativados no teste isolado. HYDRAXIA, Terra2 e os plugins de terceiros ainda exigem teste conjunto na cópia do servidor do usuário.

O build usa traduções presentes no repositório (en_US e pt_BR). Traduções adicionais já instaladas são carregadas; idiomas ausentes usam inglês como fallback.

## Instalação

Substituir somente o JAR antigo do AeternumSeasons em plugins/ pelo novo JAR, mantendo o Terra2 instalado e todos os arquivos de configuração/backups. Reiniciar o servidor para carregar as classes atualizadas. Não instalar os dois JARs do Aeternum simultaneamente. Não é preciso apagar/recriar mundos.

[Download pelo GitHub Actions](https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/38082184916/artifacts/11680534458) (o ZIP contém um único JAR).

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

## Validação pendente na cópia do servidor

No servidor de cópia com Terra2/HYDRAXIA, verificar: inverno fixo no mundo de teste durante verão do mundo principal; temperatura fria e neve durante tempestade; IDs Terra2 iguais antes/depois de carregar chunks; backup de bioma customizado resolvido; ID ausente sem aplicar PLAINS nem excluir o arquivo; salvar/reiniciar; mundo principal sem mudanças de perfil.

O teste `bash tools/test-climate-compatibility.sh` cobre normalização, recusa de perfis inválidos, resolução de IDs vanilla antigos e personalizados, e falha de paleta sem fallback. O teste python3 tools/test-paper-climate.py verifica a inicialização e o reinício do plugin completo em Paper. Nenhum desses testes substitui o teste conjunto dos seus packs e plugins.
