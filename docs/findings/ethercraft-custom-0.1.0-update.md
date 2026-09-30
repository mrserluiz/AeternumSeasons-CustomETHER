UPDATE > 30/09/2026 - 19:43 - ETHERCRAFT_CUSTOM_V01

OBJETIVO: iniciar implementação independente EtherCraftPortals/EtherCraftDimensions,
com Frost BLUE_ICE + SNOWBALL, preservando o original descompilado.

CONFIRMADO NO CÓDIGO ORIGINAL:
- VanillaPortalIsolation registra antes dos grupos condicionais Frost/Heat.
- Frost registra portais antes de ensureFrostWorld; Heat cria/carrega antes dos listeners específicos.
- features.portals.frost.enabled e features.portals.heat.enabled controlam grupos;
  não impedem todas as instanciações nem o registro de VanillaPortalIsolation.
- Não há registro direto de HeatOverworldPortals na classe principal analisada.

IMPLEMENTADO LOCALMENTE:
- ethercraft-custom/: plugin próprio v0.1.0, Paper 26.2, Java 25.
- Frost com generator/biome provider novos; criação explícita e manifesto próprio.
- Portais Blue Ice com bola de neve, registry persistente, linking manual e teleporte.
- Nenhuma criação automática de mundos/destinos. Original preservado.
- Workflow para build e artifact JAR após publicação autorizada.

VALIDAÇÃO:
- Teste Java da geometria passou; parser de sintaxe passou; diff check passou.
- Build completo/API Paper e servidor real ainda não validados.
- Não existe JAR compilado neste ambiente.
- Publicação da branch rejeitada pela revisão automática por falta de autorização explícita de envio.

PENDENTE:
- Autorizar publicação para executar CI e obter JAR.
- Compilar e validar em servidor isolado; corrigir incompatibilidades encontradas.
- Construção automática segura de destinos, hooks de proteção, Heat e Aether.
- Validar coexistência/migração do Aeternum antes de produção.

<END UPDATE>
