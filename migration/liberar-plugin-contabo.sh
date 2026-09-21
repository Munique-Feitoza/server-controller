#!/usr/bin/env bash
# migration/liberar-plugin-contabo.sh
# Devolve a instalação de plugin aos sites da Contabo (94.72.113.139).
#
# Contexto: o DISALLOW_FILE_MODS foi aplicado em toda a rede em 08/08/2026, na
# resposta ao incidente — ele fecha o `update.php?action=upload-theme`, que foi
# o vetor A da invasão. Tirar reabre esse caminho específico. É decisão da dona.
#
# O que este script NÃO reabre: o editor de arquivos do wp-admin. O
# DISALLOW_FILE_MODS desligava os dois de uma vez, e o editor é o pior dos dois
# (executa PHP direto pelo painel). Instalar plugin não depende dele, então o
# script recoloca DISALLOW_FILE_EDIT em separado.
#
# Segurança da operação:
#   · backup de cada wp-config.php antes de tocar
#   · `php -l` depois de cada edição — wp-config quebrado derruba o site inteiro
#   · restaura o original na hora se a sintaxe falhar
#   · a linha é COMENTADA, não apagada; voltar é descomentar
#
# Uso (do notebook):
#   ./liberar-plugin-contabo.sh --listar     # só mostra quem seria alterado
#   ./liberar-plugin-contabo.sh --aplicar
#   ./liberar-plugin-contabo.sh --reverter

set -uo pipefail
readonly IP="${IP:-94.72.113.139}"
readonly CHAVE="${CHAVE:-$HOME/.ssh/id_ed25519}"
MODO="${1:---listar}"

sshc() { ssh -i "$CHAVE" -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=60 "root@$IP" "$@" 2>/dev/null; }

case "$MODO" in
--listar)
  sshc 'bash -s' <<'REMOTO'
tot=0; bloq=0
for cfg in /home/*/public_html/wp-config.php; do
  [ -f "$cfg" ] || continue
  tot=$((tot+1))
  grep -qE "^\s*define\s*\(\s*'DISALLOW_FILE_MODS'\s*,\s*true\s*\)" "$cfg" 2>/dev/null && bloq=$((bloq+1))
done
echo "  sites WordPress        : $tot"
echo "  bloqueiam plugin hoje  : $bloq"
REMOTO
  ;;

--aplicar)
  sshc 'bash -s' <<'REMOTO'
set -u
BKP=/root/backup-wpconfig-$(date +%Y%m%d-%H%M)
install -d -m 700 "$BKP"
echo "  backups em: $BKP"

ok=0; pulei=0; revertidos=0
for cfg in /home/*/public_html/wp-config.php; do
  [ -f "$cfg" ] || continue
  site=$(basename "$(dirname "$(dirname "$cfg")")")
  grep -qE "^\s*define\s*\(\s*'DISALLOW_FILE_MODS'\s*,\s*true\s*\)" "$cfg" || { pulei=$((pulei+1)); continue; }

  cp -a "$cfg" "$BKP/${site}.wp-config.php"
  sed -i -E "s|^(\s*)(define\s*\(\s*'DISALLOW_FILE_MODS'\s*,\s*true\s*\)\s*;)|\1// [winup] liberado a pedido para instalar plugin — \2|" "$cfg"
  grep -q "DISALLOW_FILE_EDIT" "$cfg" || \
    sed -i "s|^// \[winup\]|define( 'DISALLOW_FILE_EDIT', true );\n// [winup]|" "$cfg"

  if php -l "$cfg" >/dev/null 2>&1; then
    ok=$((ok+1))
  else
    cp -a "$BKP/${site}.wp-config.php" "$cfg"
    revertidos=$((revertidos+1))
    echo "  ⚠ $site — sintaxe quebrou, RESTAURADO"
  fi
done
echo "  liberados : $ok"
echo "  já livres : $pulei"
echo "  revertidos: $revertidos"

echo
echo "  ── conferência ──"
a=0; b=0; e=0
for cfg in /home/*/public_html/wp-config.php; do
  [ -f "$cfg" ] || continue
  grep -qE "^\s*define\s*\(\s*'DISALLOW_FILE_MODS'\s*,\s*true\s*\)" "$cfg" && a=$((a+1))
  grep -qE "^\s*define\s*\(\s*'DISALLOW_FILE_EDIT'\s*,\s*true\s*\)" "$cfg" && b=$((b+1))
  php -l "$cfg" >/dev/null 2>&1 || e=$((e+1))
done
echo "  ainda bloqueiam plugin : $a"
echo "  com editor bloqueado   : $b"
echo "  wp-config com erro PHP : $e"
REMOTO
  ;;

--reverter)
  sshc 'bash -s' <<'REMOTO'
n=0
for cfg in /home/*/public_html/wp-config.php; do
  [ -f "$cfg" ] || continue
  if grep -q "\[winup\] liberado a pedido" "$cfg"; then
    sed -i -E "s|^\s*// \[winup\][^—]*— (define.*DISALLOW_FILE_MODS.*)|\1|" "$cfg"
    php -l "$cfg" >/dev/null 2>&1 && n=$((n+1))
  fi
done
echo "  bloqueio devolvido a $n site(s)"
REMOTO
  ;;
*) echo "use --listar | --aplicar | --reverter"; exit 1 ;;
esac
