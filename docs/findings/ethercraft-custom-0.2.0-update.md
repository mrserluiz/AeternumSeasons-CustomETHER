UPDATE > 30/09/2026 - 19:57 - ETHERCRAFT_CUSTOM_V02

SOLICITAÇÃO: tipos de portal editáveis em YAML; bloco da estrutura, item de ativação
configuráveis e criação de novos tipos sem alterar Java.

IMPLEMENTADO LOCALMENTE:
- Versão 0.2.0; portal-types.yaml com Frost e exemplos Heat/Aether desativados.
- Por tipo: enabled, frame-block, activation.item/mode, source-worlds, destination-world.
- INTERACT: clique direito com item na mão principal; PROJECTILE: SNOWBALL/EGG.
- Tipos novos podem apontar para qualquer destino já carregado explicitamente configurado.
- /ethercraft reload recarrega tipos com validação integral e preservação do estado anterior em erro.
- /ethercraft types lista as definições.
- Validação de materiais, modos, IDs, origens, rotas e ativações ambíguas.
- Vinculação/teleporte exigem mesmo tipo e rota autorizada pela definição atual.
- Persistência schema 2 guarda ID/bloco original; leitura compatível com registros v0.1.
- Mudança do bloco não altera estruturas existentes; remover/reconstruir/reativar.
- Nenhuma criação automática de dimensão ou portal de destino.

VALIDAÇÃO:
- 19 verificações Java de regras/rotas passaram.
- Geometria validada; YAML padrão lido; sintaxe de 10 arquivos Java validada.
- Diff check sem erros; testes JUnit adicionados para CI/Maven.
- Build completo e Paper real ainda pendentes. Sem JAR compilado.

PUBLICAÇÃO:
- Nenhuma nova tentativa de push, pois a autorização solicitada após rejeição
  automática anterior continua pendente.

PENDENTE:
- Compilar/testar API Paper e servidor isolado.
- Construção automática segura, hooks de proteção e geradores Heat/Aether.

<END UPDATE>
