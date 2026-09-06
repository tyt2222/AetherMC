# Roteiro de desenvolvimento

O erro comum em Skyblock é começar pelo mapa e pelas crates. Isso cria aparência, mas não cria um ciclo de jogo. A ordem mais segura é esta:

1. **Ciclo principal (MVP)** — ilha, proteção, árvore/água/lava, geradores e retorno em caso de queda. Esta etapa já está implementada no plugin.
2. **Progressão** — economia, loja de venda/compra, upgrades dos geradores, missões e requisitos por nível de ilha.
3. **Social e competição** — coops, convites, permissões, top ilhas e ranking semanal. Defina limites antes de abrir para evitar abuso.
4. **Conteúdo** — farms especiais, minions ou spawners, pesca/minas, eventos e cosméticos. Cada sistema precisa premiar algo do ciclo principal.
5. **Operação** — backups automáticos, logs, proteção contra dupes, limites de entidades/redstone, testes com jogadores e painel de denúncias.
6. **Mapa e apresentação** — spawn, warps, tutorial, lojas e identidade visual. Faça-o após as mecânicas, pois então o mapa apresenta sistemas reais.

## Decisões que você deve definir antes da fase 2

- O servidor será PvE puro ou terá ilhas invadíveis/PvP em arenas?
- Geradores serão vendidos por moeda do jogo, obtidos em missões ou ambos?
- A economia será baseada em vender minérios, colheitas, ou contratos/missões?
- Haverá reset de temporada? Se sim, qual a duração e o que persiste?
- Qual limite de membros, geradores e spawners por ilha? Esses limites são essenciais para evitar lag.
