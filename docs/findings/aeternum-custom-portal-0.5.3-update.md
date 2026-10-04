# AeternumCustomPortal 0.5.3

Auditoria local do Aeternum 4.5: SeasonalFloraController.processChunk remove pequenas flores no outono/inverno
por purgeBlockRespectingShape, que usa setType(replaceWith,false); climate.yml define replace_with:AIR.
A isenção isProtectedByPlayer consulta playerPlaced quando protectPlayerPlaced está habilitado.
Flores criadas com setType/setBlockData não disparam BlockPlaceEvent e não recebem marca automaticamente.
Esse mecanismo corresponde ao relato de desaparecimento sem drops, mas ainda exige confirmação no servidor.

O addon acessa somente os métodos/flags auditados do listener nativo, sem modificar o JAR original.
Marca somente flores de piscinas ativas/válidas, imediatamente na geração/ativação, ao carregar chunks
ou periodicamente após recriação/reload do controller. A integração não depende de takeover dos portais.
Libera apenas marcas próprias ao remover/inativar o portal; marcas existentes do jogador são preservadas.
Eventos reais de quebra/colocação continuam normais. Não restaura flores apagadas nem impede quebrá-las.
Nenhuma regra sazonal, configuração global ou planta fora do portal é desativada/alterada.

Opção ACP: aeternum.protect-portal-flowers:true, adicionada automaticamente a configs antigos.
Pré-requisito no Aeternum: seasonal_flora.protect_player_placed:true (padrão no arquivo analisado).
Hook incompatível ou opção nativa false gera aviso no console; nenhuma configuração do Aeternum é alterada.

Build commit: 43f2b111a81ed8a53f3c8c010872e9ffedcd1187
CI: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37224371928
Java 25 / Paper 26.2.build.129-stable; Maven clean verify: BUILD SUCCESS; 63 testes, zero falhas/erros/skips.
Testes da integração usam um controlador isolado com o contrato privado auditado, não um servidor Paper real.
Testes: marcação nativa, planta externa não protegida, liberação somente de marcas próprias,
transferência para edição real do jogador, reaplicação após reset/recriação, config nativa desabilitada,
falha explícita em versão incompatível. Teste necessário no servidor: manter flores após múltiplos scans sazonais,
quebrar flor e confirmar desativação; reiniciar/recarregar Aeternum; manter flora externa obedecendo às estações.

Artifact: https://github.com/mrserluiz/AeternumSeasons-CustomETHER/actions/runs/37224371928/artifacts/11311152594
JAR: aeternum-custom-portal-0.5.3.jar
JAR SHA256: 5c63525c232207d6d6fe22c6e5178f8f5a9e90970f1d890aed2f04e767c07248
ZIP SHA256: 1dbcc48aa6c93b6ac1fdc2f8392d5d6bbe5124c0a96a744183bb15aa77203fd1
JAR inspecionado: versão 0.5.3, classe da integração e configuração padrão empacotadas.
