# CLAUDE.md: lively-eyes

Mod de Minecraft "Olhos Vivos" (NeoForge 1.21.1, só cliente) que acha os olhos na skin de cada jogador e anima
expressões neles. Pedido dele em 24/11/2026: "cria um mod de minecraft que detecta onde os olhos do player
estão e cria expressões como por exemplo o olho piscando". Alvo: a instância MC:INFINITO do Prism (pasta
`instances\Horizonte Infinito`). O `CLAUDE.md` da raiz vale aqui também; o `README.md` explica o mod para ele.

## Decisões

- "Onde os olhos estão" foi lido como **na skin** (os pixels do rosto), não rastreio de rosto por webcam.
- Expressões da 1.0.0 escolhidas por mim (ele deu o piscar como exemplo): piscar, olhar para os lados, careta de
  dor, dormindo, cansado e piscadela na vírgula. Na 1.1.0 ele pediu raiva, choro e bochecha vermelha "e mais
  alguns": escolhi surpresa, feliz e frio, e um menu na tecla ponto no lugar de uma tecla por expressão.
- Tudo é calculado no cliente de quem olha, então não há rede: piscadela e menu só aparecem para quem aperta; as
  reações vêm do que o jogo já mostra de cada jogador (vida, golpe, fogo, queda, item em uso, congelamento,
  posição).
- Não abrir o jogo por conta própria (regra dele para os mods de Minecraft). A detecção se confere pela folha de
  prévia (abaixo); a tela do jogo em si **ainda não foi vista** (até a 1.1.0).

## Como funciona

- `logic/` é Java puro, sem Minecraft, e tem os testes:
  - `FaceAnalyzer`: lê o rosto 8x8 (base em (8,8), camada do chapéu em (40,8) por cima). Tom de pele = maior
    família de cor das linhas 3 a 7; tons de cabelo = famílias das duas primeiras linhas; cabelo = o que tem tom
    de cabelo e encosta no topo, descendo só pelas colunas de fora (0/7, e 1/6 ao lado de cabelo). Depois testa
    toda faixa de 1 a 3 linhas entre as linhas 1 e 6: em cada metade, os pixels que diferem da pele (os fracos ao
    lado dos fortes saem), um bloco de cada lado, com uma coluna livre no meio. Nota = simetria espelhada, contraste,
    branco do olho, altura perto da linha 4, menos castigo por fileira só de cor de cabelo (sobrancelha, franja) e
    por cortar um olho pela metade. Nota mínima 2,4. No fim, puxa para o olho os pixels ao lado (colunas 1 e 2, e
    6 e 5) que ficaram fracos, aos pares: é a íris escura em pele escura (Makena, Zuri).
  - `FacePainter`: fechado = linhas de cima com o tom de pele e a de baixo com o tom de pele escurecido pela
    metade (linha do olho); meio fechado = metade de cima; olhar = íris escorrega para uma ponta da fileira,
    mantendo a ordem das cores. Arregalado (WIDE) = fileira do branco do olho em cima; feliz (HAPPY) = olho vira
    pele e a linha do olho sobe uma fileira (o "^"). Sobrancelha de raiva: pixel de dentro logo acima do olho, os
    de fora uma fileira acima. Bochecha: a fileira logo abaixo do olho, misturada com rosa ou azul. Lágrima: desce
    pela coluna da íris mais perto do meio, um pixel a cada 170 ms, com rastro fraco, e volta ao topo. Sobrancelha,
    bochecha, branco de cima e a linha do feliz só pintam pixel parecido com a pele (`SKIN_NEAR` 70), nunca em
    cima de cabelo, barba ou máscara. Pinta a base e, onde o chapéu cobre (alfa >= 200), o chapéu também.
  - `FaceState`: os dois olhos mais sobrancelha, bochecha e quadro da lágrima. `EyeLayout.normalize` tira o que
    aquele rosto não mostra (e reduz o quadro da lágrima ao espaço abaixo do olho) antes de virar código de
    textura, para estados iguais na tela dividirem uma textura só.
  - `ExpressionController`: relógio em ms por jogador (semente pelo UUID, então cada um pisca num ritmo). Piscada
    = 35 ms meio, 100 ms fechado, 40 ms meio; 15% de piscada dupla. Humor (`Mood`) pela ordem: menu, surpresa,
    raiva, choro, feliz, vergonha, frio; morte, cama e o começo do dano vêm antes de tudo. Surpresa e feliz não
    piscam; choro e frio piscam o dobro; vergonha desvia o olhar de quem está na frente.
  - `GazeMath`: lado do alvo em relação ao rosto. Yaw 0 olha para +Z, a direita do personagem é forward x up.
    **A metade esquerda da textura é o olho direito do personagem** (gaze -1 = íris para a esquerda da textura =
    para a direita dele); vale em todos os arquivos e nos textos do /olhos.
- `client/SkinFaces`: na thread de desenho, lê a skin de volta da placa (`NativeImage.downloadTexture`) uma vez
  por textura, só 64x64 (HD fica de fora), e cria uma `DynamicTexture` por expressão na primeira vez que ela
  aparece (`lively_eyes:face/<n>/<código>`). Guarda até 64 skins; limpa ao sair do mundo e ao recarregar
  recursos. Textura que ainda não subiu para a placa é tentada de novo a cada 1 s.
- `client/Expressions`: um estado por jogador a até 48 blocos da câmera. A cada tick: alvo do olhar (ser vivo mais
  perto, até 8 blocos, dentro de 75° do nariz; no F5 de frente, a câmera); raiva = `swinging` com ser vivo a até
  4,5 blocos na frente (segura 1,5 s); vergonha = outro jogador a até 1,8 bloco com os dois a menos de 40° um do
  outro (segura 0,8 s); surpresa = em chamas ou caindo mais de 0,7 bloco por tick sem elytra, voo ou água (segura
  0,7 s). Na hora de desenhar: choro com vida <= 4 e cansado com <= 6 (fora do criativo), feliz com item de comer
  ou beber em uso, frio com `getTicksFrozen() > 0`. Erro = desliga as expressões até fechar o jogo, com o erro no
  log.
- `client/ExpressionMenuScreen`: menu da tecla ponto (6 botões, não pausa o jogo); a expressão escolhida fica 3 s
  no seu rosto.
- `mixin/PlayerRendererMixin`: no RETURN de `getTextureLocation(AbstractClientPlayer)` troca a skin pela cópia
  pintada. O Skin Layers 3D desenha a camada 3D no mesmo buffer, então pega a cópia também; o braço em primeira
  pessoa lê a skin por outro caminho e fica com a original (os braços são iguais).
- `client/EyesCommand`: `/olhos` (comando só do cliente).

## Compatibilidade conferida (sem abrir o jogo, lendo os jars da instância em 24/11/2026)

- **ETF 7.2.4** também injeta no RETURN do mesmo método, para skins com recursos dele. O mixin só mexe quando o
  valor que chega é a própria `getSkin().texture()`; se outro mod já trocou, fica quieto.
- **Skin Layers 3D 1.11.3**: injeta a malha no `ModelPart.render`, com o mesmo `VertexConsumer` (logo, a nossa
  textura).
- **Iris**: é só textura de entidade; nada especial.
- Aether e Eternal Starlight mexem em `getSkin`/`renderToBuffer`, não no mesmo ponto.
- O `feitio` troca o corpo do jogador inteiro e tem o rosto próprio que pisca; ele não está na instância. Se um
  dia estiver, os dois brigam pelo desenho do jogador: decidir com ele.

## Como compilar, testar e conferir

```powershell
.\gradlew.bat build            # testes (26) + jar em build\libs
.\gradlew.bat preview          # build\preview\faces.png: cada skin, os olhos achados e cada expressão
.\gradlew.bat debugFace -Pface=slim/noor   # grade do rosto, máscaras e nota de cada faixa
```

A prévia usa as 18 skins padrão do jar de recursos que o ModDevGradle extrai (assets da Mojang, nunca copiados
para o repositório) e as skins que o Prism guardou em `%APPDATA%\PrismLauncher\assets\skins`. Em 24/11/2026: 21
de 22 rostos achados e conferidos na folha; a que falha é um capuz escuro com mecha vermelha cobrindo o rosto
(fica sem expressão, que é o certo nesse caso).

Instalar: copiar o jar de `build\libs` para `%APPDATA%\PrismLauncher\instances\Horizonte Infinito\minecraft\mods`.

## Armadilhas

- Os limiares da detecção (`EYE_DIFF` 90, `GROW_DIFF` 80, `WEAK_FRACTION` 0,35, colunas do crescimento) foram
  acertados contra sombra de pele e blush das skins padrão (Sunny e Noor têm sombra a ~75 e ~49 do tom de pele;
  a íris da Makena fica a ~94). Mexeu, rodar `build` e olhar a `preview` inteira.
- Cor do olho fechado puxando a íris saía verde (Alex) e roxa (Steve); por isso é a pele escurecida.
- Na primeira versão da 1.1.0 a sobrancelha, o branco da surpresa e a linha do feliz pintavam por cima da franja
  (Sunny). Agora só pintam pele; o custo é que em skin com franja colada no olho a raiva e a surpresa quase não
  aparecem (a franja faz o papel da sobrancelha).
