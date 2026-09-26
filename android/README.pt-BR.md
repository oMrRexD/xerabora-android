# xeRAbora para Android

*English: [README.md](README.md).*

O cliente do xeRAbora rodando inteiro no celular, sem PC: o PS2 com o OPL-RA (ou o RiptOPL) acha o celular
na rede Wi-Fi, e o celular conversa com o RetroAchievements, desbloqueia as conquistas e mostra a mesma página
do cliente de PC.

## Instalar e usar

1. Baixe o APK da [última release](https://github.com/oMrRexD/xerabora-android/releases/latest) e instale. Na primeira vez o Android pede pra
   liberar a instalação pelo navegador.
2. Abra o app. Ele pede permissão de notificação e pra rodar sem otimização de bateria. Aceite as duas: é o
   que mantém o cliente vivo com a tela apagada no meio do jogo.
3. Na aba **AJUSTES** da página (**SETTINGS** com a página em inglês), entre com o login do RetroAchievements
   e a Web API key, como no PC. A página abre no idioma do celular (português, espanhol ou inglês); o menu
   do cabeçalho troca.
4. No PS2, com o celular **no mesmo Wi-Fi** do console: **RA: test PC connection** tem que mostrar o IP do
   celular. Depois, **RA: check game support**, e aí o jogo.

A notificação fixa mostra o que o console está fazendo: inativo, encontrado, o jogo aberto com ícone,
conquistas, pontos e tempo de sessão. Cada conquista vira uma notificação própria, e o teste de conexão, o
"check game support" e a abertura de um jogo aparecem como pop-up. **Sair** na notificação, ou **SAIR/QUIT**
na página, encerra. O botão voltar pergunta: **Minimizar** deixa rodando, **Sair** desliga, e **Enviar log**
manda o fim do `xerabora.log` pra qualquer app (útil pra relatar problema sem PC).

Com a tela ligada na hora do *test PC connection* e ao abrir o jogo, tudo funciona em qualquer celular.
A descoberta é por broadcast, e o app segura um *multicast lock* pra recebê-lo com a tela apagada, mas
tem Android que filtra do mesmo jeito.

## Atualizações

- **O app avisa sozinho.** Ao abrir (no máximo a cada 6 h), ele consulta a última release e oferece
  **Instalar**. Na primeira vez o Android pede pra liberar "instalar apps desconhecidos" pro xeRAbora.
  O login fica salvo.
- **O fork acompanha o oficial sozinho.** Todo dia o workflow `sync-upstream` faz o merge do
  `hacan359/xerabora`, e o `android` gera e publica o APK novo. A tag é `android-<versão>-r<build>`, e o
  build é o número de commits, então só sobe.

## Como o port funciona

Nenhum arquivo do upstream é modificado. Tudo que é Android é arquivo novo, então o merge diário nunca conflita:

| Pasta / arquivo | O que é |
|---|---|
| `app/src/main/cpp/CMakeLists.txt` | Lê as listas `SRC` e `RC_SRC` do `client/Makefile`, então arquivo novo no upstream entra sozinho. Compila o `main.c` como `xerabora_main`. |
| `http_android.c` | `http.h` via `HttpURLConnection` (TLS do sistema, no lugar da libcurl). |
| `sound_android.c` | `sound.h` via `SoundPool` (no lugar do paplay/aplay). Os `.wav` de `sounds/` substituem os padrões, como no PC. |
| `hooks.c` | `-Wl,--wrap`: o cliente não fecha 15 s sem página (quem manda é o serviço) e, em vez de abrir navegador, avisa o app. |
| `status.c` | `-Wl,--wrap` nas chamadas que o cliente já faz pra encher a página (console, jogo, status, conquista, push) e no `console_serve`: alimenta as notificações. Lê também as respostas `RAA1` do "RA: check game support". |
| `net_guard.c` | Envio pra página espera no máximo 250 ms. O Android congela o processo da tela em segundo plano, e o `send()` bloqueante do `webui.c` travava o cliente inteiro (sem telemetria, sem descoberta, sem conquistas). |
| `jni_bridge.c` | `HOME` no diretório do app, stdout pro logcat, chama o `main`. |
| `StatusNotifier.java` | A notificação fixa (console, jogo, progresso, tempo), a de cada conquista e os pop-ups (PS2 conectado, jogo reconhecido, jogo aberto). |
| `EngineService.java` | Serviço em primeiro plano, processo `:engine` próprio (o C tem estado estático; cada início é um processo novo), wake lock, Wi-Fi lock e multicast lock. |
| `MainActivity.java` | A página do cliente num WebView em `127.0.0.1:18280`, que conta como "esta máquina", então login e QUIT funcionam. |
| `UpdateChecker.java` | O atualizador. |
| `tools/fake_ps2.py` | Imita a descoberta do PS2 pra testar o celular sem ligar o console. |
| `tools/make_icons.py` | Refaz o ícone a partir do `docs/icon.png`. |

**Se o build quebrar depois de um merge,** o GitHub manda e-mail com o workflow `android` falhando. Os pontos
de contato com o upstream são poucos:

- as atribuições `NOME := ...` do `client/Makefile`;
- as APIs de `http.h` e `sound.h`;
- as funções interceptadas (`webui_page_gone`, `webui_open_browser`, `webui_set_console`, `webui_set_game`,
  `webui_set_game_title`, `webui_set_status`, `webui_note_unlock`, `webui_push`, `console_serve`), que precisam
  continuar sendo chamadas de outros arquivos que não o que as define. Elas são declaradas com os tipos do
  próprio header, então uma assinatura mudada quebra o build em vez de chamar errado;
- o formato da resposta `RAA1`, que faz parte do `protocol/PROTOCOL.md`.

## Compilar

Precisa do JDK 21, do Android SDK (platform 36), do NDK 28.2.13676358 e do CMake 3.22.1.

```
git submodule update --init third_party/rcheevos
cd android
./gradlew assembleDebug
```

O APK sai em `android/app/build/outputs/apk/debug/`. Pra ver o log: `adb logcat -s xerabora`.
O `xerabora.log` completo fica em `files/.config/xerabora/` no armazenamento do app.

Os secrets do repositório usados pelo CI: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD` (a assinatura; sem eles não sai release) e `SYNC_TOKEN`
(o merge diário).
