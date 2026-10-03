# AeternumCustomPortal 0.3.3 — coordenadas e erro da origem — 03/10/2026

## Evidência do usuário

Imagem recebida recuperada pelo ID do arquivo: repete "Tipo heat desativado" e "Retorno não criado: Prepare uma saída segura ao lado do portal de origem para permitir a volta". A segunda mensagem vem de uma precondição indevidamente restritiva no ensureReturnPortal da 0.3.2. A imagem confirma que o tipo Heat ativo em memória estava desabilitado; não demonstra qual arquivo foi editado pelo usuário nem que o parser de reload falhou.

## Mudanças

- Removida a precondição de saída lateral pronta na origem. Saída aceita espaço passável seguro (vegetação baixa/neve passável) e pode colocar jogador dentro de um portal intacto, sobre seu frame inferior, se os lados não estiverem disponíveis. Usa cooldown da API após chegada para evitar retorno imediato.
- X/Z de destino = origem × World.getCoordinateScale(origem) / World.getCoordinateScale(destino); limitado à borda com margem. Y usa a origem como referência dentro da altura lógica. Sem dependência do spawn no fluxo de portal.
- Busca o portal registrado compatível livre mais próximo das coordenadas correspondentes, no raio 16 por padrão em ambiente Nether e 128 nos demais, em vez de usar o primeiro portal disponível em qualquer lugar da dimensão.
- Se não houver destino, gera o mesmo frame/material/eixo perto do ponto calculado, com pisos de saída. Busca vertical ±16 e horizontal 8 (configurável até 16). Prioriza espaço livre; fallback pode limpar lista restrita de terreno. Consultas de colocação/quebra do WorldGuard/GP cobrem os 32 blocos do plano, inclusive os oito espaços de cabeça/pés. Líquidos, containers, bedrock, obsidian e outros portais registrados são excluídos. Snapshots permitem rollback ao falhar a construção.
- Limpeza de terreno pode afetar construções não protegidas feitas com os materiais naturais listados. Desativar portal-placement.allow-terrain-clearing para exigir espaço livre. Não afirmar preservação automática de toda construção de pedra/gelo fora de proteção.
- Schema de portals.yml passa a 3. Schema 1/2 faz backup portals-before-0.3.3.yml e descarta todos os vínculos antigos uma vez, preservando frames. Isso também afeta pares manuais antigos, que precisam ser refeitos; necessário porque o schema antigo não distinguia pares manuais de pares automáticos criados no spawn. Pares schema 3 persistem após reinício.
- auto-return-portal:false impede construção e permite apenas portal existente nas coordenadas correspondentes. Nenhum fallback ao spawn.
- Parser PortalTypeYaml agora é compartilhado pelo reload, edição por comando e testes de disco. /acp reload mostra versão e arquivo ativo. Valida config.yml antes de aplicar o reload de tipos, evitando falha silenciosa de parse.
- /acp enable heat salva enabled:true no YAML ativo, adiciona a origem atual somente se a lista estiver vazia e aplica reload. Executar no mundo de origem. /acp disable heat persiste/aplica desativação. Falha de validação/reconciliação restaura o YAML anterior. Fontes personalizadas não são substituídas. Console precisa de fontes explícitas.
- Comando sem subcomando mostra ajuda legível com versão; tab completion cobre comandos/tipos.

## Validação e limites

Testes novos: leitura/alteração/releitura do YAML real em disco; Heat false→true, origem vazia→mundo atual, rota alterada por edição externa; desativação preservando fonte; edição inválida sem sobrescrita; escalas 1:1, 1:8, 8:1 e customizadas, coordenadas negativas, margem da borda e exclusão de destino distante; terreno permitido e materiais excluídos.

Não foi executado um servidor Paper real. Validar com os plugins/arquivos efetivamente instalados: /acp status, enable heat e reload, ativação, primeira viagem, retorno com neve/grama, quebra/reativação, migração de vínculos e negação em região/claim. A nova implementação segue a intenção de criação automática e posicionamento por escala do Nether, mas mantém frame fixo 4×5 e vínculo um-a-um; não é reprodução integral do algoritmo Vanilla. Não cria/carrega/renomeia mundos.

Referências primárias consultadas: Paper API 26.2 World.getCoordinateScale, World.getLogicalHeight e Entity.setPortalCooldown, https://jd.papermc.io/paper/26.2/.

CI: BUILD SUCCESS; 31 testes aprovados, zero falhas/erros/skips, incluindo os três testes reais de YAML em disco.
Commit: 65b27b0b99f153e098560bf6fe1e14ddf1c34bdd.
Run: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37132232007.
Download: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37132232007/artifacts/11277701360.
Artefato: AeternumCustomPortal-0.3.3-Paper26.2; contém aeternum-custom-portal-0.3.3.jar; expira em 01/01/2027.
