UPDATE > 03/10/2026 - 10:29 - ETHERCRAFT_CUSTOM_V021

PROBLEMA CONFIRMADO PELO USUÁRIO:
Ativação bloqueada: integração de proteção pendente (WorldGuard).
A versão 0.2.0 bloqueava por presença do plugin, sem consultar a região.

CORREÇÃO:
- WorldGuard 7 opcional via softdepend; API provided, sem bundling no JAR.
- Consulta testBuild(BUILD/BLOCK_PLACE) nos 20 pontos do frame/interior.
- Bypass de sessão do WorldGuard respeitado, sem bypass próprio baseado em OP.
- /remove verifica BLOCK_BREAK nos 6 pontos internos antes de limpar blocos/registro.
- GriefPrevention opcional: consultas públicas allowBuild/allowBreak por localização.
- Verificação completa antes de persistir/alterar blocos.
- Falha de API não concede acesso. WorldGuard/GP consultados mesmo com config antigo.
- Outros providers sem integração continuam sob o bloqueio anterior.
- Dados/YAMLs existentes preservados, sem migração de mundo.

TESTES:
5 novos testes de autorização: todas as células, recusa em borda, múltiplos providers,
falha da API e ausência de providers. Build e testes completos serão conferidos no CI.
Teste dentro de Paper/WorldGuard real ainda depende do servidor de teste do usuário.

<END UPDATE>
