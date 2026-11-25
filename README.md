# Olhos Vivos (mod de Minecraft)

Mod para Minecraft 1.21.1 com NeoForge que **acha sozinho onde estão os olhos na skin** de cada jogador e dá vida
a eles. Não precisa de skin especial nem de marcar nada: ele olha o rosto da skin, descobre a cor da pele e do
cabelo e procura dois olhos espelhados. Funciona com qualquer jogador que aparecer na sua tela, inclusive você no
F5. Só roda no cliente: não precisa estar no servidor, e quem não tem o mod só vê a skin normal.

## O que os olhos fazem

- **Piscam** sozinhos a cada 2 a 6 segundos, às vezes duas vezes seguidas. Cada jogador pisca no seu ritmo.
- **Olham para os lados:** acompanham quem está perto (até 8 blocos, na frente do rosto) e, sem ninguém por
  perto, de vez em quando dão uma olhada para um lado. No F5 de frente, olham para você.
- **Careta de dor:** fecham com força quando o jogador leva dano.
- **Dormindo:** ficam fechados na cama.
- **Cansado:** com 3 corações, piscam o dobro e, se o olho da skin tiver 2 linhas, ficam meio fechados.
- **Choro:** com 2 corações ou menos, uma lágrima escorre de cada olho, que fica meio fechado.
- **Raiva:** sobrancelha inclinada para o nariz enquanto o jogador bate em alguma coisa com um bicho ou jogador
  na frente (dura um pouco depois do golpe).
- **Vergonha:** bochecha vermelha e olhar desviado quando outro jogador chega bem perto, de frente.
- **Surpresa:** olho arregalado pegando fogo ou despencando de uma altura.
- **Feliz:** olho fechado sorrindo e bochecha corada enquanto come ou bebe.
- **Frio:** bochecha azulada e olho semicerrado congelando na neve fofa.
- **Piscadela:** aperte **vírgula** para dar uma piscadela.
- **Menu de expressões:** aperte **ponto**, clique numa expressão e o seu rosto mostra ela por 3 segundos.

A piscadela e o menu só aparecem para você, porque o mod é só do cliente: cada um vê no próprio jogo as reações
que o jogo mostra para todo mundo (vida, briga, fogo, queda, comer, neve, alguém perto). As teclas mudam em
Opções > Controles > Olhos Vivos.

Olhar para os lados só acontece em skins cujo olho tem o branco e a íris lado a lado (como o Steve). Meio fechado
só acontece em olho de 2 linhas ou mais. Olho de 1 linha só pisca inteiro. Sobrancelha, bochecha e o branco da
surpresa só são pintados em cima de pele: em skin com franja colada no olho, a raiva e a surpresa quase não
aparecem.

## Conferir a sua skin

Digite **/olhos** no chat. Ele diz em que coluna e linha do rosto achou cada olho e o que eles conseguem fazer, ou
por que não achou (rosto coberto por máscara, por exemplo). Skin sem olhos achados fica exatamente como era.

## Ajustar

Na lista de **Mods**, escolha **Olhos Vivos** e clique em **Config**, ou edite `config/lively_eyes-client.toml`
com o jogo aberto. Dá para ligar e desligar cada expressão e mudar o intervalo entre piscadas.

Quando várias reações valem ao mesmo tempo, ganha a primeira desta ordem: surpresa, raiva, choro, feliz,
vergonha, frio. Dano, cama e morte vêm antes de todas.

## Instalar

Copie `lively_eyes-neoforge-1.21.1-<versão>.jar` (em `build\libs` depois de compilar) para a pasta `mods` da
instância. Compilar: `.\gradlew.bat build` na pasta do projeto.

## Limites

- Skin HD (maior que 64x64) fica de fora.
- Skin cujo rosto não tem dois olhos que se espelham (olho tapado, capuz cobrindo metade do rosto) fica sem
  expressão.
- Skins com recursos próprios do ETF (Entity Texture Features) ficam com o que o ETF faz; o mod não mexe nelas.
