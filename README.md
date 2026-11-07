# Lembra

App Android de lembretes, em português, simples e fiável: os lembretes tocam a horas, mesmo com o telemóvel em silêncio. Não tem contas nem publicidade, e os dados ficam só no telemóvel. Requer Android 8.0 ou superior.

## Funcionalidades

- **Escrita natural em português**: "Pagar renda dia 8 todos os meses às 10h #finanças" cria o lembrete com data, hora, repetição e categoria.
- **Data e hora, recorrência e adiar**: repete todos os dias, em dias úteis, todas as semanas (nos dias que escolher), todos os meses, todos os anos ou a cada N horas, dias, semanas ou meses. Pode terminar numa data ou ao fim de N vezes. Para adiar há opções rápidas (10 min, 1 h, esta noite, amanhã…) ou uma data e hora à escolha.
- **Despertador**: toca com som de alarme mesmo em silêncio, em ecrã inteiro, com volume próprio que vai aumentando. Só pára quando marca como feito ou adia.
- **Nada fica esquecido**: ao desbloquear o telemóvel aparece o ecrã "Por tratar".
- **Lembretes por localização**: avisa ao chegar ou ao sair de um local guardado. Usa só o Android, sem serviços Google.
- **Checklist dentro de cada lembrete**: os itens podem ser reordenados e é possível colar uma lista de texto.
- **Fotos e anexos**: tirar uma foto ou anexar ficheiros.
- **Calendário mensal** com filtro por categoria.
- **Aviso de lembretes coincidentes** e **modo insistente** para os importantes.
- **Categorias**: criar, mudar nome e cor, reordenar.
- **Estatísticas e histórico**: concluídos, adiamentos, taxa de cumprimento e os lembretes mais adiados.
- **Widget**, **tema claro, escuro ou automático** e **cópia de segurança** em JSON.
- **Verificação de fiabilidade**: confirma notificações, alarmes exatos e bateria, com dicas para Samsung e Xiaomi.

## Estrutura

```
AndroidManifest.xml
res/                     recursos (cores, temas, ícones, widget, som de alarme)
src/pt/lembra/core/      lógica pura e testável (recorrência, intérprete de frases, conflitos…)
src/pt/lembra/data/      SQLite, preferências, anexos
src/pt/lembra/alarm/     alarmes, notificações, despertador, localização
src/pt/lembra/ui/        ecrãs e widget
test/                    testes da lógica (JVM)
tools/zipalign.py        alinhamento do APK
build.sh                 compilação sem Gradle
```

A app usa só o framework Android, sem bibliotecas externas.

## Compilar localmente

Requisitos: JDK 17 ou superior, Python 3 e o Android SDK (build-tools 35 e platform android-35).

```bash
export ANDROID_HOME=~/Android/Sdk
export KS_PASS='a-sua-password'
cp /caminho/seguro/release.jks .   # a chave de assinatura (não está no repositório)
./build.sh                          # gera build/Lembra.apk
```

## Compilar no GitHub (Actions)

O workflow `.github/workflows/build.yml` compila o APK a cada push para `main`. Com uma tag `v*` (por exemplo `v2.3.2`), cria também uma Release com o APK.

Antes da primeira compilação, crie dois *secrets* em **Settings → Secrets and variables → Actions**:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | o ficheiro `release.jks` em base64 (`base64 -w0 release.jks`) |
| `KS_PASS` | a password da chave |

**Importante:** use sempre a mesma chave `release.jks`. Um APK assinado com outra chave não instala por cima da versão anterior: seria preciso desinstalar a app e perderiam-se os lembretes. A chave **não** é publicada no repositório (ver `.gitignore`).

## Testes

```bash
mkdir -p build/test
javac -d build/test src/pt/lembra/core/*.java test/*.java
for t in ParserTest OccTest CatTest LocTest CheckTest; do java -cp build/test $t; done
```

## Dados

Os dados ficam numa base de dados SQLite privada da app e mantêm-se nas atualizações. A cópia de segurança (Definições → Exportar) guarda lembretes, checklists, categorias e locais, mas não os ficheiros anexados.
