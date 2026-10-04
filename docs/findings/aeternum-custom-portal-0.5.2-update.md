# AeternumCustomPortal 0.5.2

Flores geradas montadas somente após borda/água, sem blocos sólidos temporários na altura das flores.
Pré-validação de luz e validação nativa de colocação (Block.canPlace), com física normal.
Locais incompatíveis são revertidos e a busca continua. Não torna flores indestrutíveis/repostas.

Busca ordenada pela distância horizontal; raio máximo 128 configurável, adicionado sem substituir opções existentes.
Lotes de oito colunas por tick e chunks carregados pela API assíncrona do Paper; blocos/proteção/montagem na thread principal.
Saída/desconexão/reload cancelam a pesquisa e callbacks antigos não criam um portal depois do cancelamento.
Busca simultânea da mesma origem reutiliza o par criado pelo primeiro jogador.

Vertical: solo sob os dois blocos centrais da base, volume 4×5 completo, sem exigir três blocos acima do topo
ou terreno plano dos dois lados. Chegada no interior é permitida, apoiada na moldura inferior.
Horizontal: geometria mantida, fundo existente preservado e três blocos de ar acima da água/flores.

Build commit: 02d9e92965d10b3a1624e49fda4d505799fd4f89
CI: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37223396879
Java 25 / Paper 26.2.build.129-stable, Maven clean verify: BUILD SUCCESS; 57 testes, zero falhas/erros/skips.
JAR conferido: versão 0.5.2, PortalSiteSearch.class e max-creation-radius:128 empacotados.
JAR SHA256: d8ce8ed0bf7a68c7b1c3d226aa95b3d7ce111d368a2646e06fd346c20f489a61
Artifact: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37223396879/artifacts/11311171366

Não houve teste em servidor Paper real nesta sessão. Validação necessária: persistência das flores após ticks normais;
viagem/retorno; terreno irregular; destino além do raio antigo; saída/desconexão durante carregamento; dois jogadores;
proteções e reload. Luz insuficiente é uma causa provável identificada no fluxo, não uma conclusão confirmada por logs do servidor.
Mundos já precisam estar carregados. Busca respeita a borda e proteções, sem garantia em vazio/áreas incompatíveis.
Não reposiciona registros anteriores nem faz fallback para spawn.
